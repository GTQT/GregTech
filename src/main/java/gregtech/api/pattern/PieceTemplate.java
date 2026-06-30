package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.RelativeDirection;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Immutable structure intermediate representation (IR) for one structure piece.
 * Pure data carrier — no factory methods, no mutation, no I/O. The
 * {@link StructurePiece} owns one of these and exposes the same queries
 * (dimensions, elements, AABB) used by the structure runtime.
 *
 * <p>Design intent: the new compile path
 * {@link gregtech.api.pattern.element.IStructurePiece} → {@link StructurePiece}
 * produces a {@code StructurePiece} that holds a {@code PieceTemplate}
 * directly.
 *
 * <p>Construction: the primary builder is
 * {@link PieceTemplateCompiler#buildTemplate()}.
 */
public final class PieceTemplate {

    /**
     * Aisle definition record. Describes the repetition range and optional
     * channel name for one aisle of the pattern.
     *
     * @param minRepeat    minimum number of repetitions for this aisle
     * @param maxRepeat    maximum number of repetitions for this aisle
     * @param channelName  optional channel name; {@code null} means the aisle is not channel-controlled
     */
    @Desugar
    public record AisleDef(int minRepeat, int maxRepeat, @Nullable String channelName) {

        /**
         * @return a copy of the {@code [minRepeat, maxRepeat]} pair for callers
         *         that need the int[] shape (e.g. RepetitionDFS / preview builders).
         */
        public int[] toRangeArray() {
            return new int[] { minRepeat, maxRepeat };
        }
    }

    /**
     * Center offset for a template.
     *
     * @param x    controller x offset within the pattern
     * @param y    controller y offset within the pattern
     * @param z    controller z offset within the pattern
     * @param minZ cumulative min aisle count before the center aisle
     * @param maxZ cumulative max aisle count before the center aisle
     */
    @Desugar
    public record CenterOffset(int x, int y, int z, int minZ, int maxZ) {

        // Empty records occasionally confuse older javac + Jabel combinations
        // that walk the body looking for the @Desugar anchor. The no-op getter
        // below makes the body non-empty and avoids the "Must be annotated with
        // @Desugar" error reported against this record when it sits below
        // another @Desugar-annotated record in the same file.
        @SuppressWarnings("unused")
        public boolean isSynthetic() {
            return true;
        }
    }

    private final IStructureElement<?>[][][] elements; // [z][y][x]
    private final AisleDef[] aisles;
    private final RelativeDirection[] structureDir;
    private final int xLength; // x size (char axis)
    private final int yLength; // y size (row/string axis)
    private final int zLength; // z size (aisle axis)
    private final CenterOffset centerOffset;

    // Auto-generated structure description lines (from DeclarativePatternBuilder)
    @NotNull
    private final List<String> structureDescription;

    public PieceTemplate(@NotNull IStructureElement<?>[][][] elements,
                         @NotNull RelativeDirection[] structureDir,
                         @NotNull int[][] aisleRepetitions,
                         @Nullable String[] aisleChannelNames,
                         @Nullable int[] externalCenterOffset,
                         @Nullable List<String> structureDescription) {
        this.elements = elements;
        this.zLength = elements.length;
        this.structureDir = structureDir;
        this.aisles = buildAisles(aisleRepetitions, aisleChannelNames);

        if (this.zLength > 0) {
            this.yLength = elements[0].length;
            if (this.yLength > 0) {
                this.xLength = elements[0][0].length;
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

    private static AisleDef[] buildAisles(@NotNull int[][] aisleRepetitions, @Nullable String[] aisleChannelNames) {
        AisleDef[] result = new AisleDef[aisleRepetitions.length];
        for (int i = 0; i < aisleRepetitions.length; i++) {
            String name = (aisleChannelNames != null && i < aisleChannelNames.length) ? aisleChannelNames[i] : null;
            result[i] = new AisleDef(aisleRepetitions[i][0], aisleRepetitions[i][1], name);
        }
        return result;
    }

    private static CenterOffset unpackCenterOffset(@NotNull int[] external) {
        if (external.length != 5) {
            throw new IllegalArgumentException(
                    "externalCenterOffset must have length 5, got " + external.length);
        }
        return new CenterOffset(external[0], external[1], external[2], external[3], external[4]);
    }

    private CenterOffset initializeCenterOffsets() {
        for (int x = 0; x < this.xLength; x++) {
            for (int y = 0; y < this.yLength; y++) {
                for (int z = 0, minZ = 0, maxZ = 0; z <
                        this.zLength; minZ += aisles[z].minRepeat(), maxZ += aisles[z].maxRepeat(), z++) {
                    IStructureElement<?> element = this.elements[z][y][x];
                    if (element != null && element.isCenter()) {
                        return new CenterOffset(x, y, z, minZ, maxZ);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Didn't find center predicate");
    }

    @NotNull
    public IStructureElement<?>[][][] getElements() {
        return elements;
    }

    @NotNull
    public AisleDef[] getAisles() {
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

    public CenterOffset getCenterOffset() {
        return centerOffset;
    }

    /**
     * Compute the maximum expanded finger length, accounting for repeatable aisles.
     * This is the sum of all max repetition counts across all aisles,
     * representing the worst-case structure length along the finger axis.
     */
    public int getMaxExpandedFingerLength() {
        int total = 0;
        for (AisleDef aisle : aisles) {
            total += aisle.maxRepeat();
        }
        return total;
    }

    /**
     * Compute the precise world-space AABB for this structure template given the controller state.
     * Returns a pair of BlockPos: [min corner, max corner] in world coordinates.
     */
    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos centerPos,
                                       @NotNull StructureOrientation orientation,
                                       int margin) {
        int maxFingerLen = getMaxExpandedFingerLength();
        CenterOffset co = this.centerOffset;

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
                            lx, ly, lz,
                            orientation.getStructureFront(), orientation.getUp(),
                            orientation.isFlipped(), structureDir);
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
    public List<String> getStructureDescription() {
        return structureDescription;
    }
}
