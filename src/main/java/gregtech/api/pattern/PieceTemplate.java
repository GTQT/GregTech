package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;
import gregtech.api.pattern.element.CompiledStructureElement;
import gregtech.api.pattern.element.IStructureElement;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Immutable structure intermediate representation (IR) for one structure piece.
 * Pure data carrier — no factory methods, no mutation, no I/O. The
 * {@link StructurePiece} owns one of these and exposes the same queries
 * (dimensions, predicates, AABB) that previously lived on
 * {@code BlockPatternTemplate}.
 *
 * <p>Design intent: the new compile path
 * {@link gregtech.api.pattern.element.IStructurePiece} → {@link StructurePiece}
 * produces a {@code StructurePiece} that holds a {@code PieceTemplate}
 * directly. The legacy {@code BlockPatternTemplate} is retained as a thin
 * facade for backward compatibility with public addons.
 *
 * <p>Construction: the primary builder is
 * {@link PieceTemplateCompiler#buildTemplate()}; the legacy builder
 * {@link FactoryBlockPattern#buildTemplate()} constructs a
 * {@code BlockPatternTemplate} facade over a {@code PieceTemplate}.
 */
public final class PieceTemplate {

    private final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    private final IStructureElement<?>[][][] elements; // [z][y][x]
    private final BlockPatternTemplate.AisleDef[] aisles;
    private final RelativeDirection[] structureDir;
    private final int xLength; // x size (char axis)
    private final int yLength; // y size (row/string axis)
    private final int zLength; // z size (aisle axis)
    private final BlockPatternTemplate.CenterOffset centerOffset;

    // Auto-generated structure description lines (from DeclarativePatternBuilder)
    @NotNull
    private final List<String> structureDescription;

    public PieceTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                         @NotNull RelativeDirection[] structureDir,
                         @NotNull int[][] aisleRepetitions) {
        this(predicatesIn, compileLegacyElements(predicatesIn), structureDir,
                aisleRepetitions, null, null, null);
    }

    public PieceTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                         @NotNull RelativeDirection[] structureDir,
                         @NotNull int[][] aisleRepetitions,
                         @Nullable String[] aisleChannelNames) {
        this(predicatesIn, compileLegacyElements(predicatesIn), structureDir,
                aisleRepetitions, aisleChannelNames, null, null);
    }

    /**
     * Full constructor with optional external center offset and structure description.
     *
     * @param predicatesIn          the 3D predicate array [z][y][x]
     * @param structureDir          the 3 relative directions
     * @param aisleRepetitions      the repetition ranges per aisle
     * @param aisleChannelNames     channel names per aisle (nullable entries)
     * @param externalCenterOffset  optional externally-specified center offset;
     *                              if {@code null}, auto-discovers from the {@code isCenter} predicate
     * @param structureDescription  optional auto-generated description lines for tooltip display;
     *                              if {@code null}, defaults to an empty list
     */
    public PieceTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                         @NotNull RelativeDirection[] structureDir,
                         @NotNull int[][] aisleRepetitions,
                         @Nullable String[] aisleChannelNames,
                         @Nullable int[] externalCenterOffset,
                         @Nullable List<String> structureDescription) {
        this(predicatesIn, compileLegacyElements(predicatesIn), structureDir, aisleRepetitions,
                aisleChannelNames, externalCenterOffset, structureDescription);
    }

    public PieceTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                         @NotNull IStructureElement<?>[][][] elements,
                         @NotNull RelativeDirection[] structureDir,
                         @NotNull int[][] aisleRepetitions,
                         @Nullable String[] aisleChannelNames,
                         @Nullable int[] externalCenterOffset,
                         @Nullable List<String> structureDescription) {
        this.blockMatches = predicatesIn;
        this.elements = elements;
        this.zLength = predicatesIn.length;
        this.structureDir = structureDir;
        this.aisles = buildAisles(aisleRepetitions, aisleChannelNames);

        if (this.zLength > 0) {
            this.yLength = predicatesIn[0].length;
            if (this.yLength > 0) {
                this.xLength = predicatesIn[0][0].length;
            } else {
                this.xLength = 0;
            }
        } else {
            this.yLength = 0;
            this.xLength = 0;
        }

        // Fail-fast: compute center offset right here so that a missing center predicate
        // throws at template-construction time, not at the first check.
        this.centerOffset = externalCenterOffset != null
                ? unpackCenterOffset(externalCenterOffset)
                : initializeCenterOffsets();

        this.structureDescription = structureDescription == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(structureDescription);
    }

    private static BlockPatternTemplate.AisleDef[] buildAisles(@NotNull int[][] aisleRepetitions, @Nullable String[] aisleChannelNames) {
        BlockPatternTemplate.AisleDef[] result = new BlockPatternTemplate.AisleDef[aisleRepetitions.length];
        for (int i = 0; i < aisleRepetitions.length; i++) {
            String name = (aisleChannelNames != null && i < aisleChannelNames.length) ? aisleChannelNames[i] : null;
            result[i] = new BlockPatternTemplate.AisleDef(aisleRepetitions[i][0], aisleRepetitions[i][1], name);
        }
        return result;
    }

    private static BlockPatternTemplate.CenterOffset unpackCenterOffset(@NotNull int[] external) {
        if (external.length != 5) {
            throw new IllegalArgumentException(
                    "externalCenterOffset must have length 5, got " + external.length);
        }
        return new BlockPatternTemplate.CenterOffset(external[0], external[1], external[2], external[3], external[4]);
    }

    private BlockPatternTemplate.CenterOffset initializeCenterOffsets() {
        for (int x = 0; x < this.xLength; x++) {
            for (int y = 0; y < this.yLength; y++) {
                for (int z = 0, minZ = 0, maxZ = 0; z <
                        this.zLength; minZ += aisles[z].minRepeat(), maxZ += aisles[z].maxRepeat(), z++) {
                    TraceabilityPredicate predicate = this.blockMatches[z][y][x];
                    if (predicate.isCenter) {
                        return new BlockPatternTemplate.CenterOffset(x, y, z, minZ, maxZ);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Didn't find center predicate");
    }

    public TraceabilityPredicate[][][] getBlockMatches() {
        return blockMatches;
    }

    @NotNull
    public IStructureElement<?>[][][] getElements() {
        return elements;
    }

    @NotNull
    private static IStructureElement<?>[][][] compileLegacyElements(
            @NotNull TraceabilityPredicate[][][] predicates) {
        IStructureElement<?>[][][] result = new IStructureElement<?>[predicates.length][][];
        for (int z = 0; z < predicates.length; z++) {
            result[z] = new IStructureElement<?>[predicates[z].length][];
            for (int y = 0; y < predicates[z].length; y++) {
                result[z][y] = new IStructureElement<?>[predicates[z][y].length];
                for (int x = 0; x < predicates[z][y].length; x++) {
                    result[z][y][x] = CompiledStructureElement.legacy(predicates[z][y][x]);
                }
            }
        }
        return result;
    }

    @NotNull
    public BlockPatternTemplate.AisleDef[] getAisles() {
        return aisles;
    }

    @NotNull
    public int[][] getAisleRepetitions() {
        int[][] result = new int[aisles.length][2];
        for (int i = 0; i < aisles.length; i++) {
            result[i][0] = aisles[i].minRepeat();
            result[i][1] = aisles[i].maxRepeat();
        }
        return result;
    }

    @NotNull
    public String[] getAisleChannelNames() {
        String[] result = new String[aisles.length];
        for (int i = 0; i < aisles.length; i++) {
            result[i] = aisles[i].channelName();
        }
        return result;
    }

    public RelativeDirection[] getStructureDir() {
        return structureDir;
    }

    public int getXLength() {
        return xLength;
    }

    public int getYLength() {
        return yLength;
    }

    public int getZLength() {
        return zLength;
    }

    public BlockPatternTemplate.CenterOffset getCenterOffset() {
        return centerOffset;
    }

    /**
     * Compute the maximum expanded finger length, accounting for repeatable aisles.
     * This is the sum of all max repetition counts across all aisles,
     * representing the worst-case structure length along the finger axis.
     */
    public int getMaxExpandedFingerLength() {
        int total = 0;
        for (BlockPatternTemplate.AisleDef aisle : aisles) {
            total += aisle.maxRepeat();
        }
        return total;
    }

    /**
     * Walk every non-null, non-{@link TraceabilityPredicate#ANY} cell of this template and
     * invoke {@code consumer} with the pattern-local world position and the predicate
     * occupying that cell.
     */
    public void forEachPredicate(@NotNull EnumFacing front, @NotNull EnumFacing up, boolean flipped,
                                 @NotNull BiConsumer<BlockPos, TraceabilityPredicate> consumer) {
        for (int iz = 0; iz < zLength; iz++) {
            TraceabilityPredicate[][] layer = blockMatches[iz];
            for (int iy = 0; iy < yLength; iy++) {
                TraceabilityPredicate[] row = layer[iy];
                for (int ix = 0; ix < xLength; ix++) {
                    TraceabilityPredicate pred = row[ix];
                    if (pred == null || pred == TraceabilityPredicate.ANY) continue;
                    BlockPos localPos = RelativeDirection.setActualRelativeOffset(
                            ix, iy, iz, front, up, flipped, structureDir);
                    consumer.accept(localPos, pred);
                }
            }
        }
    }

    public void forEachPredicate(@NotNull StructureOrientation orientation,
                                 @NotNull BiConsumer<BlockPos, TraceabilityPredicate> consumer) {
        forEachPredicate(
                orientation.getStructureFront(),
                orientation.getUp(),
                orientation.isFlipped(),
                consumer);
    }

    /**
     * Compute the precise world-space AABB for this structure template given the controller state.
     * Returns a pair of BlockPos: [min corner, max corner] in world coordinates.
     */
    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos centerPos, @NotNull EnumFacing frontFacing,
                                       @NotNull EnumFacing upwardsFacing, boolean isFlipped, int margin) {
        int maxFingerLen = getMaxExpandedFingerLength();
        BlockPatternTemplate.CenterOffset co = this.centerOffset;

        int xMin = -co.x();
        int xMax = xLength - 1 - co.x();
        int yMin = -co.y();
        int yMax = yLength - 1 - co.y();
        int zMin = -co.maxZ();
        int zMax = maxFingerLen - 1 - co.minZ();

        int worldMinX = Integer.MAX_VALUE, worldMinY = Integer.MAX_VALUE, worldMinZ = Integer.MAX_VALUE;
        int worldMaxX = Integer.MIN_VALUE, worldMaxY = Integer.MIN_VALUE, worldMaxZ = Integer.MIN_VALUE;

        for (int xi = 0; xi < 2; xi++) {
            int lx = (xi == 0) ? xMin : xMax;
            for (int yi = 0; yi < 2; yi++) {
                int ly = (yi == 0) ? yMin : yMax;
                for (int zi = 0; zi < 2; zi++) {
                    int lz = (zi == 0) ? zMin : zMax;
                    BlockPos offset = RelativeDirection.setActualRelativeOffset(
                            lx, ly, lz, frontFacing, upwardsFacing, isFlipped, structureDir);
                    worldMinX = Math.min(worldMinX, offset.getX());
                    worldMinY = Math.min(worldMinY, offset.getY());
                    worldMinZ = Math.min(worldMinZ, offset.getZ());
                    worldMaxX = Math.max(worldMaxX, offset.getX());
                    worldMaxY = Math.max(worldMaxY, offset.getY());
                    worldMaxZ = Math.max(worldMaxZ, offset.getZ());
                }
            }
        }

        BlockPos min = centerPos.add(worldMinX - margin, worldMinY - margin, worldMinZ - margin);
        BlockPos max = centerPos.add(worldMaxX + margin, worldMaxY + margin, worldMaxZ + margin);
        return new BlockPos[] { min, max };
    }

    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos centerPos,
                                       @NotNull StructureOrientation orientation,
                                       int margin) {
        return computeWorldAABB(
                centerPos,
                orientation.getStructureFront(),
                orientation.getUp(),
                orientation.isFlipped(),
                margin);
    }

    @NotNull
    public List<String> getStructureDescription() {
        return structureDescription;
    }
}
