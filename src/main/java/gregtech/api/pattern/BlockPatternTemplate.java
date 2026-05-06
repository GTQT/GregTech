package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable structural template for multiblock patterns.
 * Contains all static/shared data that does not change between instances of the same machine type.
 * Multiple machines of the same type share a single template, significantly reducing memory usage.
 *
 * @see MultiblockState for per-instance mutable state
 * @see FactoryBlockPattern for the builder that creates templates
 */
public class BlockPatternTemplate {

    static final EnumFacing[] FACINGS = { EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.WEST, EnumFacing.EAST,
            EnumFacing.UP, EnumFacing.DOWN };

    private final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    private final int[][] aisleRepetitions;
    private final String[] aisleChannelNames; // channel name per aisle (null = no channel)
    private final RelativeDirection[] structureDir;
    private final int fingerLength; // z size
    private final int thumbLength; // y size
    private final int palmLength; // x size
    // x, y, z, minZ, maxZ
    private final int[] centerOffset;

    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions) {
        this(predicatesIn, structureDir, aisleRepetitions, new String[aisleRepetitions.length]);
    }

    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions,
                                @NotNull String[] aisleChannelNames) {
        this.blockMatches = predicatesIn;
        this.fingerLength = predicatesIn.length;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;
        this.aisleChannelNames = aisleChannelNames;

        if (this.fingerLength > 0) {
            this.thumbLength = predicatesIn[0].length;
            if (this.thumbLength > 0) {
                this.palmLength = predicatesIn[0][0].length;
            } else {
                this.palmLength = 0;
            }
        } else {
            this.thumbLength = 0;
            this.palmLength = 0;
        }

        this.centerOffset = initializeCenterOffsets();
    }

    private int[] initializeCenterOffsets() {
        for (int x = 0; x < this.palmLength; x++) {
            for (int y = 0; y < this.thumbLength; y++) {
                for (int z = 0, minZ = 0, maxZ = 0; z <
                        this.fingerLength; minZ += aisleRepetitions[z][0], maxZ += aisleRepetitions[z][1], z++) {
                    TraceabilityPredicate predicate = this.blockMatches[z][y][x];
                    if (predicate.isCenter) {
                        return new int[] { x, y, z, minZ, maxZ };
                    }
                }
            }
        }
        throw new IllegalArgumentException("Didn't find center predicate");
    }

    /**
     * Create a new mutable state instance for this template.
     * Each multiblock controller instance should hold its own state.
     *
     * @return a new MultiblockState bound to this template
     */
    public MultiblockState createState() {
        return new MultiblockState(this);
    }

    // --- Accessors for template data (package-private for MultiblockState / BlockPattern access) ---

    public TraceabilityPredicate[][][] getBlockMatches() {
        return blockMatches;
    }

    public int[][] getAisleRepetitions() {
        return aisleRepetitions;
    }

    /**
     * Get the channel name associated with each aisle index.
     * Null entries mean the aisle has no associated channel (repetition is not channel-controlled).
     *
     * @return array of channel names (parallel to aisleRepetitions)
     */
    public String[] getAisleChannelNames() {
        return aisleChannelNames;
    }

    public RelativeDirection[] getStructureDir() {
        return structureDir;
    }

    public int getFingerLength() {
        return fingerLength;
    }

    public int getThumbLength() {
        return thumbLength;
    }

    public int getPalmLength() {
        return palmLength;
    }

    public int[] getCenterOffset() {
        return centerOffset;
    }

    public int getStructureXSize() {
        return palmLength;
    }

    public int getStructureYSize() {
        return thumbLength;
    }

    public int getStructureZSize() {
        return fingerLength;
    }
}
