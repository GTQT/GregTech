package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.DynamicOffsetPiece;
import gregtech.api.pattern.DynamicRepeatGroupPiece;
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
import java.util.HashMap;
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
    public static MultiPiecePattern compile(@NotNull StructureDefinition<?> def) {
        List<StructurePiece> pieces = new ArrayList<>();

        // Track the centerOffset from the first piece that has an isCenter element.
        // Subsequent pieces without isCenter and with default centerOffset {0,0,0}
        // will inherit this reference centerOffset so that their template is aligned
        // with the first piece (e.g. a "body" piece aligns with the "bottom" piece
        // that contains the controller 'S' predicate).
        int[] referenceCenterOffset = null;

        // Resolve the controller center before compiling any piece. Declarative
        // layouts may place the controller in a later named piece.
        for (StructureDefinition.PieceEntry entry : def.getPieceEntries()) {
            IStructurePiece candidate = entry.piece;
            boolean hasCenter = false;
            for (IStructureElement element : candidate.getSymbolMap().values()) {
                if (element.isCenter()) {
                    hasCenter = true;
                    break;
                }
            }
            if (hasCenter) {
                PieceTemplate centerTemplate = compilePieceToPieceTemplate(
                        candidate, def.getStructureDir(), null, null);
                BlockPatternTemplate.CenterOffset center = centerTemplate.getCenterOffset();
                referenceCenterOffset = new int[]{center.x(), center.y(), center.z()};
                break;
            }
        }

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
                             (snap, origin, front, up, flipped, prior, runtime, session) ->
                                     runtime.getState().checkPatternAtSnapshotExact(
                                             snap, origin, front, up, flipped, 0, 0, 0, session) != null);
                    pieces.add(piece);
                    // Record centerOffset from legacy templates that have isCenter
                    if (referenceCenterOffset == null) {
                        BlockPatternTemplate.CenterOffset co = mp.legacyTemplate.getCenterOffset();
                        referenceCenterOffset = new int[]{co.x(), co.y(), co.z()};
                    }
                    continue;
                }
            }

            // Check whether this piece has an isCenter element
            boolean hasCenter = false;
            for (IStructureElement elem : p.getSymbolMap().values()) {
                if (elem.isCenter()) {
                    hasCenter = true;
                    break;
                }
            }

            PieceTemplate tpl = compilePieceToPieceTemplate(p, def.getStructureDir(),
                    null, hasCenter ? null : referenceCenterOffset);

            // Update reference centerOffset from the first piece that has isCenter
            if (hasCenter && referenceCenterOffset == null) {
                BlockPatternTemplate.CenterOffset co = tpl.getCenterOffset();
                referenceCenterOffset = new int[]{co.x(), co.y(), co.z()};
            }

            // StructurePiece.template is typed as BlockPatternTemplate (the legacy facade)
            // for backward compatibility with the public API. Wrap the canonical
            // PieceTemplate as a BlockPatternTemplate facade so the existing
            // StructurePiece constructor signature still accepts it.
            // PieceTemplate is final and does not extend BlockPatternTemplate, so we
            // always go through the BlockPatternTemplate(PieceTemplate) constructor.
            BlockPatternTemplate tplFacade = new BlockPatternTemplate(tpl);

            // Resolve the effective centerOffset for RepeatGroupPiece constructor
            int[] pieceCenterOffset = p.getCenterOffset();
            if (!hasCenter && referenceCenterOffset != null
                    && pieceCenterOffset[0] == 0 && pieceCenterOffset[1] == 0 && pieceCenterOffset[2] == 0) {
                pieceCenterOffset = referenceCenterOffset;
            }

            if (p.isRepeatable()) {
                boolean tensor = isTensorProduct(p);
                SearchStrategy strategy = pickStrategy(p, tensor);
                RepeatGroupPiece group;
                if (entry.anchorPieceName != null) {
                    group = new DynamicRepeatGroupPiece(
                            p.getName(), tpl, entry.baseOffset, entry.offsetMode, entry.condition,
                            p.getRepeatAxes(), p.getRepeatRanges(), p.getStepSizes(),
                            p.getRepeatChannelNames(), pieceCenterOffset, strategy,
                            entry.anchorPieceName, entry.anchorStep);
                } else {
                    group = new RepeatGroupPiece(
                            p.getName(), tpl, entry.baseOffset, entry.offsetMode, entry.condition,
                            p.getRepeatAxes(), p.getRepeatRanges(), p.getStepSizes(),
                            p.getRepeatChannelNames(), pieceCenterOffset, strategy);
                }
                pieces.add(group);
            } else if (entry.anchorPieceName != null) {
                // Dynamic-anchor piece: position is computed at check time from the
                // runtime repeat count of the named anchor piece. Used to place a
                // fixed piece that follows a repeatable body whose extent is only
                // known at runtime (e.g. a "top" piece after a "body" piece).
                DynamicOffsetPiece piece = new DynamicOffsetPiece(
                        p.getName(), tplFacade, entry.baseOffset, entry.offsetMode,
                        entry.condition, entry.anchorPieceName, entry.anchorStep);
                pieces.add(piece);
            } else {
                // Fixed piece: single StructurePiece holding the canonical PieceTemplate directly
                StructurePiece piece = new StructurePiece(p.getName(), tplFacade,
                         entry.baseOffset, entry.offsetMode, entry.condition,
                         (snap, origin, front, up, flipped, prior, runtime, session) ->
                                 runtime.getState().checkPatternAtSnapshotExact(
                                         snap, origin, front, up, flipped, 0, 0, 0, session) != null);
                pieces.add(piece);
            }
        }
        Map<MultiblockAbility<?>, int[]> abilityLimits = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, StructureDefinition.AbilityLimit> entry :
                def.getAbilityLimits().entrySet()) {
            abilityLimits.put(entry.getKey(), new int[]{entry.getValue().min, entry.getValue().max});
        }
        return new MultiPiecePattern(pieces, abilityLimits);
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
    public static BlockPos[] computeMaxAABB(@NotNull StructureDefinition<?> def) {
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

        if (!foundMarker) return false;

        // Every cell must use the same marker. Ignoring spaces here would classify
        // hollow or sparse patterns as tensor products and make axis probes sample
        // a shape that is not actually uniform.
        for (String[] aisle : pattern) {
            for (String row : aisle) {
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c != marker) return false;
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
        return compilePieceToPieceTemplate(piece, structureDir, structureDescription, null);
    }

    /**
     * Compile a piece into a {@link PieceTemplate} with an optional reference
     * center offset for alignment with a preceding isCenter piece.
     *
     * <p>When the piece has no isCenter element and its own centerOffset is the
     * default {@code {0,0,0}}, the {@code referenceCenterOffset} (typically from
     * the first piece that contains the controller 'S' predicate) is used instead.
     * This ensures that all pieces in a multi-piece structure share the same
     * center alignment, preventing cross-piece misalignment in auto-build and
     * structure checking.
     *
     * @param piece                    the piece to compile
     * @param structureDir             the 3 relative directions
     * @param structureDescription     optional description lines; {@code null}/empty means "no description"
     * @param referenceCenterOffset    center offset inherited from the first isCenter piece,
     *                                 or {@code null} to use the piece's own centerOffset
     * @return the compiled piece IR
     */
    @NotNull
    public static PieceTemplate compilePieceToPieceTemplate(@NotNull IStructurePiece piece,
                                                             @NotNull RelativeDirection[] structureDir,
                                                             @Nullable List<String> structureDescription,
                                                             @Nullable int[] referenceCenterOffset) {
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
            compiler.whereElement(entry.getKey(), entry.getValue());
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
            // Use the piece's explicit center offset, falling back to the
            // reference centerOffset when the piece's own offset is the
            // default {0,0,0} and a reference is available. This ensures
            // alignment with the first (isCenter) piece in the structure.
            int[] co = piece.getCenterOffset();
            if (referenceCenterOffset != null
                    && co[0] == 0 && co[1] == 0 && co[2] == 0) {
                co = referenceCenterOffset;
            }
            // Convert {x, y, z} to {x, y, z, minZ, maxZ}
            // For a non-repeatable base piece, minZ = maxZ = z
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
