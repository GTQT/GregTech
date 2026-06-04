package gregtech.api.pattern.element;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified compilation entry point: StructureDefinition → MultiPiecePattern.
 *
 * <p>Each {@link IStructurePiece} produces at most 1 {@link StructurePiece}:
 * <ul>
 *   <li>Fixed piece → 1 × StructurePiece</li>
 *   <li>Repeatable piece → 1 × RepeatGroupPiece (with search strategy auto-selected)</li>
 * </ul>
 *
 * <p>Search strategy selection (tensor product auto-dispatch):
 * <ul>
 *   <li>Single axis → SLIDING_1D</li>
 *   <li>Multi-axis tensor product → INDEPENDENT_1D</li>
 *   <li>Multi-axis non-tensor → NESTED_BACKTRACKING</li>
 * </ul>
 */
public final class StructureCompiler {

    private StructureCompiler() {}

    // --- Search strategy enum ---

    /**
     * Search strategy for repeatable pieces.
     * Selected at compile time based on base shape and axis count.
     */
    public enum SearchStrategy {
        /** Single 1D sliding window (single axis). */
        SLIDING_1D,
        /** Independent 1D per axis (multi-axis tensor product). */
        INDEPENDENT_1D,
        /** Nested backtracking (multi-axis non-tensor). */
        NESTED_BACKTRACKING
    }

    // --- Main compilation entry point ---

    /**
     * Compile a StructureDefinition into a MultiPiecePattern.
     *
     * @param def the structure definition to compile
     * @return the compiled multi-piece pattern
     */
    @NotNull
    public static MultiPiecePattern compile(@NotNull StructureDefinition def) {
        List<StructurePiece> pieces = new ArrayList<>();
        for (StructureDefinition.PieceEntry entry : def.getPieceEntries()) {
            IStructurePiece p = entry.piece;

            // Handle legacy pieces (from pieceFromFactory)
            if (p instanceof StructureDefinition.MutablePiece) {
                StructureDefinition.MutablePiece mp = (StructureDefinition.MutablePiece) p;
                if (mp.legacyTemplate != null) {
                    // The snapshot checker receives the per-controller PieceRuntime as its
                    // last argument; the template is final, so the captured reference to
                    // `mp.legacyTemplate` is fine across controllers.
                    StructurePiece piece = new StructurePiece(p.getName(), mp.legacyTemplate,
                            entry.baseOffset, entry.offsetMode, entry.condition,
                            (snap, origin, front, up, flipped, prior, runtime) ->
                                    runtime.getState().checkPatternFastAtSnapshot(
                                            snap, origin, front, up, flipped) != null);
                    pieces.add(piece);
                    continue;
                }
            }

            PieceTemplate tpl = compilePieceToPieceTemplate(p, def.getStructureDir());

            // StructurePiece.template is typed as BlockPatternTemplate (the legacy facade)
            // for backward compatibility with the public API. Wrap the canonical
            // PieceTemplate as a BlockPatternTemplate facade so the existing
            // StructurePiece constructor signature still accepts it.
            // PieceTemplate is final and does not extend BlockPatternTemplate, so we
            // always go through the BlockPatternTemplate(PieceTemplate) constructor.
            BlockPatternTemplate tplFacade = new BlockPatternTemplate(tpl);

            if (!p.isRepeatable()) {
                // Fixed piece: single StructurePiece holding the canonical PieceTemplate directly
                StructurePiece piece = new StructurePiece(p.getName(), tplFacade,
                        entry.baseOffset, entry.offsetMode, entry.condition,
                        (snap, origin, front, up, flipped, prior, runtime) ->
                                runtime.getState().checkPatternFastAtSnapshot(
                                        snap, origin, front, up, flipped) != null);
                pieces.add(piece);
            } else {
                // Repeatable piece: 1 RepeatGroupPiece with auto-selected search strategy
                boolean tensor = isTensorProduct(p);
                SearchStrategy strategy = pickStrategy(p, tensor);
                RepeatGroupPiece group = new RepeatGroupPiece(
                        p.getName(), tplFacade, entry.baseOffset, entry.offsetMode, entry.condition,
                        p.getRepeatAxes(), p.getRepeatRanges(), p.getStepSizes(),
                        p.getRepeatChannelNames(), p.getCenterOffset(), strategy);
                pieces.add(group);
            }
        }
        return new MultiPiecePattern(pieces);
    }

    // --- AABB computation ---

    /**
     * Compute the maximum AABB for the structure definition.
     * Used by async structure checker for snapshot bounds.
     *
     * <p>Returns a pair of BlockPos: [min corner, max corner] in structure-local
     * coordinates relative to the controller position.
     *
     * @param def the structure definition
     * @return array of two BlockPos: [min, max]
     */
    @NotNull
    public static BlockPos[] computeMaxAABB(@NotNull StructureDefinition def) {
        int minX = 0, minY = 0, minZ = 0;
        int maxX = 0, maxY = 0, maxZ = 0;

        for (StructureDefinition.PieceEntry entry : def.getPieceEntries()) {
            IStructurePiece p = entry.piece;
            Vec3i offset = entry.baseOffset;

            // Get base piece dimensions from the compiled template
            PieceTemplate tpl = compilePieceToPieceTemplate(p, def.getStructureDir());
            int finger = tpl.getZLength();
            int thumb = tpl.getYLength();
            int palm = tpl.getXLength();
            // [x, y, z, minZ, maxZ] — shared record with BlockPatternTemplate for back-compat
            BlockPatternTemplate.CenterOffset center = tpl.getCenterOffset();

            // Compute max expanded dimensions for repeatable pieces
            int maxPalm = palm;
            int maxThumb = thumb;
            int maxFinger = finger;
            if (p.isRepeatable()) {
                int[][] ranges = p.getRepeatRanges();
                int[] axes = p.getRepeatAxes();
                int[] steps = p.getStepSizes();
                for (int i = 0; i < axes.length; i++) {
                    int expansion = steps[i] * (ranges[i][1] - 1);
                    switch (axes[i]) {
                        case 0:
                            maxPalm += expansion;
                            break;
                        case 1:
                            maxThumb += expansion;
                            break;
                        case 2:
                            maxFinger += expansion;
                            break;
                        default:
                            break;
                    }
                }
            }

            // Compute piece AABB relative to controller (structure-local coords)
            int ox = offset.getX(), oy = offset.getY(), oz = offset.getZ();
            int pieceMinX = ox - center.x();
            int pieceMinY = oy - center.y();
            int pieceMinZ = oz - center.maxZ(); // backward extent uses maxZ
            int pieceMaxX = ox + maxPalm - 1 - center.x();
            int pieceMaxY = oy + maxThumb - 1 - center.y();
            int pieceMaxZ = oz + maxFinger - 1 - center.minZ(); // forward extent uses minZ

            minX = Math.min(minX, pieceMinX);
            minY = Math.min(minY, pieceMinY);
            minZ = Math.min(minZ, pieceMinZ);
            maxX = Math.max(maxX, pieceMaxX);
            maxY = Math.max(maxY, pieceMaxY);
            maxZ = Math.max(maxZ, pieceMaxZ);
        }

        return new BlockPos[]{new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)};
    }

    // --- Tensor product detection ---

    /**
     * Detect if a piece's base pattern is a tensor product
     * (all cells use the same symbol/element).
     *
     * <p>A tensor product pattern has uniform structure along all repeat axes,
     * allowing independent 1D search per axis instead of nested backtracking.
     *
     * @param piece the structure piece to check
     * @return true if the pattern is a tensor product
     */
    static boolean isTensorProduct(@NotNull IStructurePiece piece) {
        String[][] pattern = piece.getPattern();
        if (pattern == null || pattern.length == 0) return true;

        // Find the first non-empty cell as the reference marker
        char marker = 0;
        boolean foundMarker = false;
        for (String[] aisle : pattern) {
            for (String row : aisle) {
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c != ' ') {
                        marker = c;
                        foundMarker = true;
                        break;
                    }
                }
                if (foundMarker) break;
            }
            if (foundMarker) break;
        }

        if (!foundMarker) return true;

        // Check if all non-space cells use the same marker
        for (String[] aisle : pattern) {
            for (String row : aisle) {
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c != ' ' && c != marker) return false;
                }
            }
        }

        // Element layer: check that the marker maps to a single-candidate element
        Map<Character, IStructureElement> symbolMap = piece.getSymbolMap();
        IStructureElement elem = symbolMap.get(marker);
        return elem != null && elem.getCandidates().length <= 1;
    }

    // --- Strategy selection ---

    /**
     * Select the search strategy based on axis count and tensor detection.
     *
     * @param p       the structure piece
     * @param isTensor whether the piece is a tensor product
     * @return the selected search strategy
     */
    static SearchStrategy pickStrategy(@NotNull IStructurePiece p, boolean isTensor) {
        if (p.getRepeatAxes().length == 1) return SearchStrategy.SLIDING_1D;
        return isTensor ? SearchStrategy.INDEPENDENT_1D : SearchStrategy.NESTED_BACKTRACKING;
    }

    // --- Piece template compilation ---

    /**
     * Compile an {@link IStructurePiece} into a canonical {@link PieceTemplate}
     * (the new IR). This is the new-path entry point: the resulting
     * {@code PieceTemplate} is wrapped directly in a {@link StructurePiece}
     * without ever constructing a {@link BlockPatternTemplate} facade.
     *
     * <p>If any element in the symbol map is a center element ({@code isCenter() == true}),
     * the template will auto-discover the center offset. Otherwise, the piece's
     * explicit center offset is used.
     *
     * @param piece        the structure piece to compile
     * @param structureDir the structure direction triple [charDir, stringDir, aisleDir]
     * @return the compiled piece IR
     */
    @NotNull
    public static PieceTemplate compilePieceToPieceTemplate(@NotNull IStructurePiece piece,
                                                             @NotNull RelativeDirection[] structureDir) {
        return compilePieceToPieceTemplate(piece, structureDir, null);
    }

    /**
     * Compile a piece into a {@link PieceTemplate}, optionally attaching an
     * auto-generated structure description. The description is propagated
     * through the underlying {@link PieceTemplateCompiler} so the resulting
     * template is fully immutable (no setter is required).
     *
     * @param piece                  the piece to compile
     * @param structureDir           the 3 relative directions
     * @param structureDescription   optional description lines; {@code null}/empty means "no description"
     * @return the compiled piece IR
     */
    @NotNull
    public static PieceTemplate compilePieceToPieceTemplate(@NotNull IStructurePiece piece,
                                                             @NotNull RelativeDirection[] structureDir,
                                                             @Nullable List<String> structureDescription) {
        // Handle legacy pieces with pre-built template
        if (piece instanceof StructureDefinition.MutablePiece) {
            StructureDefinition.MutablePiece mp = (StructureDefinition.MutablePiece) piece;
            if (mp.legacyTemplate != null) {
                return mp.legacyTemplate.getDelegate();
            }
        }

        // Build the piece's template directly via PieceTemplateCompiler,
        // bypassing the public FactoryBlockPattern facade.
        PieceTemplateCompiler compiler = new PieceTemplateCompiler(
                structureDir[0], structureDir[1], structureDir[2]);

        String[][] pattern = piece.getPattern();
        Map<Character, IStructureElement> symbolMap = piece.getSymbolMap();

        // Add aisles to the compiler
        for (String[] aisle : pattern) {
            compiler.aisle(aisle);
        }

        // Add symbol mappings
        for (Map.Entry<Character, IStructureElement> entry : symbolMap.entrySet()) {
            entry.getValue().applyTo(String.valueOf(entry.getKey()), compiler);
        }

        // Determine center offset strategy
        boolean hasCenter = false;
        for (IStructureElement elem : symbolMap.values()) {
            if (elem.isCenter()) {
                hasCenter = true;
                break;
            }
        }

        if (hasCenter) {
            // Auto-discover center from isCenter predicate
            return compiler.buildPieceTemplate();
        } else {
            // Use the piece's explicit center offset
            // Convert {x, y, z} to {x, y, z, minZ, maxZ}
            // For a non-repeatable base piece, minZ = maxZ = z
            int[] co = piece.getCenterOffset();
            int[] templateCenterOffset = new int[]{co[0], co[1], co[2], co[2], co[2]};
            return compiler.buildPieceTemplate(templateCenterOffset, structureDescription);
        }
    }

    /**
     * Legacy compile entry point that returns a {@link BlockPatternTemplate}
     * facade. New code should call {@link #compilePieceToPieceTemplate}
     * instead and use the canonical {@link PieceTemplate} directly.
     *
     * <p>If any element in the symbol map is a center element (isCenter = true),
     * the template will auto-discover the center offset. Otherwise, the piece's
     * explicit center offset is used.
     *
     * @param piece        the structure piece to compile
     * @param structureDir the structure direction triple [charDir, stringDir, aisleDir]
     * @return the compiled block pattern template (facade over a PieceTemplate)
     */
    @NotNull
    public static BlockPatternTemplate compilePieceTemplate(@NotNull IStructurePiece piece,
                                                            @NotNull RelativeDirection[] structureDir) {
        return new BlockPatternTemplate(compilePieceToPieceTemplate(piece, structureDir));
    }

    /**
     * Compile a piece into a template, optionally attaching an auto-generated structure
     * description. The description is propagated through the underlying
     * {@link PieceTemplateCompiler} so the resulting template is fully immutable
     * (no setter is required).
     *
     * @param piece                  the piece to compile
     * @param structureDir           the 3 relative directions
     * @param structureDescription   optional description lines; {@code null}/empty means "no description"
     * @return the compiled template (facade over a PieceTemplate)
     */
    @NotNull
    public static BlockPatternTemplate compilePieceTemplate(@NotNull IStructurePiece piece,
                                                            @NotNull RelativeDirection[] structureDir,
                                                            @Nullable List<String> structureDescription) {
        return new BlockPatternTemplate(
                compilePieceToPieceTemplate(piece, structureDir, structureDescription));
    }
}
