package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Combines a shared immutable {@link BlockPatternTemplate} with a deprecated
 * compatibility {@link MultiblockState} projection. This class preserves the
 * original API surface for backward compatibility while enabling template sharing.
 *
 * <p>For new code, prefer {@link StructureRuntime} and typed operation requests.
 *
 * @deprecated Use {@link StructureRuntime} and typed operation requests for new code.
 *             This class is retained for backward compatibility during migration and will be
 *             removed in version 2.10.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2.10")
public class BlockPattern {

    private final BlockPatternTemplate template;
    private final MultiblockState state;
    private final StructureOperationEvaluator evaluator;

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
        this.evaluator = new StructureOperationEvaluator(null, state.getBackingState(), null, null);
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
        this.evaluator = new StructureOperationEvaluator(null, state.getBackingState(), null, null);
        this.cache = state.cache;
        this.formedRepetitionCount = state.formedRepetitionCount;
        this.aisleRepetitions = template.getAisleRepetitions();
        this.structureDir = template.getStructureDir();
    }

    /**
     * Create a BlockPattern from an existing template and state.
     * Used by compatibility callers that already own a detached legacy state projection.
     */
    public BlockPattern(@NotNull BlockPatternTemplate template, @NotNull MultiblockState state) {
        this.template = template;
        this.state = state;
        this.evaluator = new StructureOperationEvaluator(null, state.getBackingState(), null, null);
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
     * @return the detached deprecated compatibility state projection
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
     * Delegates to the detached compatibility MultiblockState.
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
        return template.getXLength();
    }

    public int getStructureYSize() {
        return template.getYLength();
    }

    public int getStructureZSize() {
        return template.getZLength();
    }

    public PatternError getError() {
        return state.getError();
    }

    // --- Delegated operations ---

    /**
     * @deprecated Legacy orientation facade. New runtime code should pass
     *             {@link StructureOrientation} through {@link StructureOperationRequest}.
     */
    @Deprecated
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip) {
        return evaluator.checkSingle(
                world, centerPos, StructureOrientation.legacy(frontFacing, upwardsFacing, false, allowsFlip), true);
    }

    /**
     * @deprecated Legacy orientation facade. New runtime code should pass
     *             {@link StructureOrientation} through {@link StructureOperationRequest}.
     */
    @Deprecated
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip,
                                                  boolean doRandomCheck) {
        return evaluator.checkSingle(
                world, centerPos,
                StructureOrientation.legacy(frontFacing, upwardsFacing, false, allowsFlip),
                doRandomCheck);
    }

    public void clearCache() {
        evaluator.clearSingleCache();
    }

    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase) {
        evaluator.creativeBuildSingle(player, controllerBase, null, false);
    }

    @Deprecated
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase, int tier) {
        evaluator.creativeBuildSingle(player, controllerBase, tier);
    }

    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase,
                          java.util.Map<String, Integer> channelValues, boolean skipHatches) {
        evaluator.creativeBuildSingle(player, controllerBase, channelValues, skipHatches);
    }

    /**
     * @deprecated Legacy orientation facade. New runtime code should use
     *             {@link StructureOperationRequest#iterate(World, BlockPos, StructureOrientation)}.
     */
    @Deprecated
    public Map<BlockPos, BlockInfo> getAllStructureBlocks(World world, BlockPos centerPos,
                                                          EnumFacing frontFacing, EnumFacing upwardsFacing,
                                                          boolean isFlipped) {
        return evaluator.iterateSingle(world, centerPos,
                StructureOrientation.legacy(frontFacing, upwardsFacing, isFlipped, false));
    }

}
