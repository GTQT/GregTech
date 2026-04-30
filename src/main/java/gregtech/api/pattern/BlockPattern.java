package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Combines a shared immutable {@link BlockPatternTemplate} with a per-instance
 * mutable {@link MultiblockState}. This class preserves the original API surface
 * for backward compatibility while enabling template sharing.
 *
 * <p>For new code, prefer using {@link BlockPatternTemplate} and {@link MultiblockState} directly.
 *
 * @deprecated Use {@link BlockPatternTemplate} + {@link MultiblockState} directly for new code.
 *             This class is retained for backward compatibility during migration.
 */
@Deprecated
public class BlockPattern {

    private final BlockPatternTemplate template;
    private final MultiblockState state;

    /**
     * Direct access to the formed structure cache for backward compatibility.
     * @deprecated Use {@link #getState()}.cache instead
     */
    @Deprecated
    public final Long2ObjectMap<BlockInfo> cache;

    /**
     * The repetitions per aisle along the axis of repetition.
     * @deprecated Use {@link #getState()}.formedRepetitionCount instead
     */
    @Deprecated
    public final int[] formedRepetitionCount;

    /**
     * Aisle repetition ranges.
     * @deprecated Use {@link #getTemplate()}.getAisleRepetitions() instead
     */
    @Deprecated
    public final int[][] aisleRepetitions;

    /**
     * Structure directions.
     * @deprecated Use {@link #getTemplate()}.getStructureDir() instead
     */
    @Deprecated
    public final RelativeDirection[] structureDir;

    public BlockPattern(@NotNull TraceabilityPredicate[][][] predicatesIn, @NotNull RelativeDirection[] structureDir,
                        @NotNull int[][] aisleRepetitions) {
        this.template = new BlockPatternTemplate(predicatesIn, structureDir, aisleRepetitions);
        this.state = template.createState();
        // Expose state fields directly for backward compatibility
        this.cache = state.cache;
        this.formedRepetitionCount = state.formedRepetitionCount;
        this.aisleRepetitions = template.getAisleRepetitions();
        this.structureDir = template.getStructureDir();
    }

    /**
     * Create a BlockPattern from an existing template. The state is freshly created.
     */
    public BlockPattern(@NotNull BlockPatternTemplate template) {
        this.template = template;
        this.state = template.createState();
        this.cache = state.cache;
        this.formedRepetitionCount = state.formedRepetitionCount;
        this.aisleRepetitions = template.getAisleRepetitions();
        this.structureDir = template.getStructureDir();
    }

    // --- Access to internal components ---

    /**
     * @return the immutable template backing this pattern
     */
    public BlockPatternTemplate getTemplate() {
        return template;
    }

    /**
     * @return the mutable per-instance state
     */
    public MultiblockState getState() {
        return state;
    }

    // --- Delegated accessors (preserving original public API) ---

    public int[][] getAisleRepetitions() {
        return template.getAisleRepetitions();
    }

    public RelativeDirection[] getStructureDir() {
        return template.getStructureDir();
    }

    /**
     * The cache of formed structure block positions.
     * Delegates to the internal MultiblockState.
     */
    public Long2ObjectMap<BlockInfo> getCache() {
        return state.cache;
    }

    /**
     * The repetition count after pattern matching.
     * @deprecated Use {@link #getState()}.formedRepetitionCount
     */
    @Deprecated
    public int[] getFormedRepetitionCount() {
        return state.formedRepetitionCount;
    }

    public int getStructureXSize() {
        return template.getStructureXSize();
    }

    public int getStructureYSize() {
        return template.getStructureYSize();
    }

    public int getStructureZSize() {
        return template.getStructureZSize();
    }

    public PatternError getError() {
        return state.getError();
    }

    // --- Delegated operations ---

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip) {
        return state.checkPatternFastAt(world, centerPos, frontFacing, upwardsFacing, allowsFlip);
    }

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip,
                                                  boolean doRandomCheck) {
        return state.checkPatternFastAt(world, centerPos, frontFacing, upwardsFacing, allowsFlip, doRandomCheck);
    }

    public void clearCache() {
        state.clearCache();
    }

    public int[] calculateRepetitionsByTier(int tier) {
        return state.calculateRepetitionsByTier(tier);
    }

    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase) {
        state.autoBuild(player, controllerBase);
    }

    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase, int tier) {
        state.autoBuild(player, controllerBase, tier);
    }

    public Map<BlockPos, BlockInfo> getAllStructureBlocks(World world, BlockPos centerPos,
                                                          EnumFacing frontFacing, EnumFacing upwardsFacing,
                                                          boolean isFlipped) {
        return state.getAllStructureBlocks(world, centerPos, frontFacing, upwardsFacing, isFlipped);
    }

    public BlockInfo[][][] getPreview(int[] repetition) {
        return state.getPreview(repetition);
    }
}
