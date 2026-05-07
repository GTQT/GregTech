package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

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

    // Auto-generated structure description lines (from DeclarativePatternBuilder)
    @Nullable
    private List<String> structureDescription;

    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions) {
        this(predicatesIn, structureDir, aisleRepetitions, new String[aisleRepetitions.length]);
    }

    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions,
                                @NotNull String[] aisleChannelNames) {
        this(predicatesIn, structureDir, aisleRepetitions, aisleChannelNames, null);
    }

    /**
     * Full constructor with optional external center offset.
     *
     * @param predicatesIn     the 3D predicate array [z][y][x]
     * @param structureDir     the 3 relative directions
     * @param aisleRepetitions the repetition ranges per aisle
     * @param aisleChannelNames channel names per aisle (nullable entries)
     * @param externalCenterOffset optional externally-specified center offset [x,y,z,minZ,maxZ];
     *                             if null, auto-discovers from isCenter predicate
     */
    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions,
                                @NotNull String[] aisleChannelNames,
                                @Nullable int[] externalCenterOffset) {
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

        this.centerOffset = externalCenterOffset != null ? externalCenterOffset : initializeCenterOffsets();
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

    /**
     * Compute the maximum expanded finger length, accounting for repeatable aisles.
     * This is the sum of all max repetition counts across all aisles,
     * representing the worst-case structure length along the finger axis.
     *
     * @return the maximum possible finger length when all aisles are at max repetitions
     */
    public int getMaxExpandedFingerLength() {
        int total = 0;
        for (int[] rep : aisleRepetitions) {
            total += rep[1];
        }
        return total;
    }

    /**
     * Get auto-generated structure description lines for tooltip display.
     * Only available if the template was built via {@link gregtech.api.pattern.casing.DeclarativePatternBuilder}.
     *
     * @return unmodifiable list of tooltip lines, or empty list if not available
     */
    @NotNull
    public List<String> getStructureDescription() {
        return structureDescription != null ? structureDescription : Collections.emptyList();
    }

    /**
     * Set the structure description lines. Called by DeclarativePatternBuilder during build.
     * Should only be called once during template construction.
     *
     * @param description the description lines
     */
    public void setStructureDescription(@NotNull List<String> description) {
        this.structureDescription = Collections.unmodifiableList(description);
    }
}
