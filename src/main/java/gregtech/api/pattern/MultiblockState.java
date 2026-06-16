package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Deprecated compatibility facade for legacy multiblock pattern state APIs.
 *
 * <p>The canonical internal matcher/cache implementation now lives in
 * {@link PieceRuntimeState}. This facade owns no canonical lifecycle state: it
 * delegates to a detached backing created for compatibility callers, and
 * mutations made through it are intentionally isolated from controller-owned
 * {@link StructureRuntime} / {@link PieceRuntimes}.
 *
 * @deprecated Use {@link StructureRuntime} and typed operation requests for new
 *             code. Runtime internals should use {@link PieceRuntimeState}.
 * @see PieceRuntimeState
 * @see PieceTemplate for the canonical IR
 */
@Deprecated
@ApiStatus.Obsolete
public class MultiblockState {

    @NotNull
    private final PieceRuntimeState backing;

    /**
     * Direct cache field retained for legacy source/binary compatibility.
     * This is the detached backing cache, never the controller's canonical cache.
     */
    @Deprecated
    public final Long2ObjectMap<BlockInfo> cache;

    /**
     * Direct formed repetition field retained for legacy source compatibility.
     * External assignments are synchronized into the detached backing before
     * delegated operations run.
     */
    @Deprecated
    public int[] formedRepetitionCount;

    public MultiblockState(@NotNull PieceTemplate template) {
        this(new PieceRuntimeState(template));
    }

    private MultiblockState(@NotNull PieceRuntimeState backing) {
        this.backing = backing;
        this.cache = backing.cache;
        this.formedRepetitionCount = backing.formedRepetitionCount;
    }

    @NotNull
    PieceRuntimeState getBackingState() {
        syncToBacking();
        return backing;
    }

    void restoreProjection(@NotNull PieceRuntimeState.Checkpoint checkpoint) {
        backing.restoreTo(checkpoint);
        syncFromBacking();
    }

    private void syncToBacking() {
        if (formedRepetitionCount != backing.formedRepetitionCount && formedRepetitionCount != null) {
            int length = Math.min(formedRepetitionCount.length, backing.formedRepetitionCount.length);
            System.arraycopy(formedRepetitionCount, 0, backing.formedRepetitionCount, 0, length);
            for (int i = length; i < backing.formedRepetitionCount.length; i++) {
                backing.formedRepetitionCount[i] = 0;
            }
        }
        formedRepetitionCount = backing.formedRepetitionCount;
    }

    private void syncFromBacking() {
        formedRepetitionCount = backing.formedRepetitionCount;
    }

    @NotNull
    public PieceTemplate getPieceTemplate() {
        return backing.getPieceTemplate();
    }

    @NotNull
    public BlockPatternTemplate getTemplate() {
        return backing.getTemplate();
    }

    @Nullable
    public PatternError getError() {
        return backing.getError();
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getMissingAbilities() {
        return backing.getMissingAbilities();
    }

    public void lock() {
        backing.lock();
    }

    public void unlock() {
        backing.unlock();
    }

    public boolean tryLock() {
        return backing.tryLock();
    }

    public void clearCache() {
        backing.clearCache();
    }

    @NotNull
    public PatternMatchContext getMatchContext() {
        return backing.getMatchContext();
    }

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos,
                                                  @NotNull StructureOrientation orientation) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternFastAt(world, centerPos, orientation);
        syncFromBacking();
        return result;
    }

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos,
                                                  @NotNull StructureOrientation orientation,
                                                  boolean doRandomCheck) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternFastAt(world, centerPos, orientation, doRandomCheck);
        syncFromBacking();
        return result;
    }

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos,
                                                  @NotNull StructureOrientation orientation,
                                                  boolean doRandomCheck,
                                                  int xOffset, int yOffset, int zOffset) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternFastAt(
                world, centerPos, orientation, doRandomCheck, xOffset, yOffset, zOffset);
        syncFromBacking();
        return result;
    }

    public boolean probeCacheAt(@NotNull World world,
                                boolean doRandomCheck,
                                int xOffset, int yOffset, int zOffset) {
        return backing.probeCacheAt(world, doRandomCheck, xOffset, yOffset, zOffset);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation) {
        return checkPatternAtExact(world, centerPos, orientation, 0, 0, 0);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation,
                                                   int xOffset, int yOffset, int zOffset) {
        return checkPatternAtExact(world, centerPos, orientation, xOffset, yOffset, zOffset, null);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation,
                                                   int xOffset, int yOffset, int zOffset,
                                                   @Nullable StructureMatchSession session) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternAtExact(
                world, centerPos, orientation, xOffset, yOffset, zOffset, session);
        syncFromBacking();
        return result;
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world,
                                                   @NotNull StructureCellTraversal traversal,
                                                   @Nullable StructureMatchSession session) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternAtExact(world, traversal, session);
        syncFromBacking();
        return result;
    }

    public static int resolveRepetitionValue(int value, int min, int max) {
        return PieceRuntimeState.resolveRepetitionValue(value, min, max);
    }

    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase) {
        backing.autoBuild(player, controllerBase);
        syncFromBacking();
    }

    @Deprecated
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase, int tier) {
        backing.autoBuild(player, controllerBase, tier);
        syncFromBacking();
    }

    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase,
                          Map<String, Integer> channelValues, boolean skipHatches) {
        backing.autoBuild(player, controllerBase, channelValues, skipHatches);
        syncFromBacking();
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, Map<String, Integer> channelValues, boolean skipHatches) {
        backing.autoBuildAt(player, controllerBase, centerPos, channelValues, skipHatches);
        syncFromBacking();
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        backing.autoBuildAt(player, controllerBase, centerPos, channelValues, skipHatches, abilityTracker);
        syncFromBacking();
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches) {
        backing.autoBuildAt(player, controllerBase, centerPos, xOffset, yOffset, zOffset,
                channelValues, skipHatches);
        syncFromBacking();
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        backing.autoBuildAt(player, controllerBase, centerPos, xOffset, yOffset, zOffset,
                channelValues, skipHatches, abilityTracker);
        syncFromBacking();
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, @NotNull StructureOrientation orientation,
                            int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        backing.autoBuildAt(player, controllerBase, centerPos, orientation, xOffset, yOffset, zOffset,
                channelValues, skipHatches, abilityTracker);
        syncFromBacking();
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, @NotNull StructureOrientation orientation,
                            int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker,
                            @NotNull StructureEvaluationContext.Operation operation) {
        backing.autoBuildAt(player, controllerBase, centerPos, orientation, xOffset, yOffset, zOffset,
                channelValues, skipHatches, abilityTracker, operation);
        syncFromBacking();
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      BlockPos centerPos,
                                                      @NotNull StructureOrientation orientation,
                                                      int xOffset, int yOffset, int zOffset,
                                                      Map<String, Integer> channelValues,
                                                      boolean skipHatches,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation) {
        StructureBuildResult result = backing.autoBuildAtWithResult(player, controllerBase, centerPos,
                orientation, xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker, operation);
        syncFromBacking();
        return result;
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      BlockPos centerPos,
                                                      @NotNull StructureOrientation orientation,
                                                      int xOffset, int yOffset, int zOffset,
                                                      Map<String, Integer> channelValues,
                                                      boolean skipHatches,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation,
                                                      @NotNull ItemStack triggerStack) {
        StructureBuildResult result = backing.autoBuildAtWithResult(player, controllerBase, centerPos,
                orientation, xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker,
                operation, triggerStack);
        syncFromBacking();
        return result;
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      @NotNull StructureCellTraversal traversal,
                                                      Map<String, Integer> channelValues,
                                                      boolean skipHatches,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation) {
        StructureBuildResult result = backing.autoBuildAtWithResult(player, controllerBase, traversal,
                channelValues, skipHatches, abilityTracker, operation);
        syncFromBacking();
        return result;
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      @NotNull StructureCellTraversal traversal,
                                                      Map<String, Integer> channelValues,
                                                      boolean skipHatches,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation,
                                                      @NotNull ItemStack triggerStack) {
        StructureBuildResult result = backing.autoBuildAtWithResult(player, controllerBase, traversal,
                channelValues, skipHatches, abilityTracker, operation, triggerStack);
        syncFromBacking();
        return result;
    }

    public void spawnHintsAt(@NotNull World world,
                             @NotNull MultiblockControllerBase controllerBase,
                             @NotNull BlockPos centerPos,
                             @NotNull StructureOrientation orientation,
                             @Nullable Map<String, Integer> channelValues,
                             @NotNull ItemStack triggerStack) {
        backing.spawnHintsAt(world, controllerBase, centerPos, orientation, channelValues, triggerStack);
    }

    @NotNull
    public StructureHintResult spawnHintsAtWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controllerBase,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            @NotNull ItemStack triggerStack) {
        return backing.spawnHintsAtWithResult(world, controllerBase, centerPos, orientation,
                channelValues, triggerStack);
    }

    public void spawnHintsAt(@NotNull World world,
                             @NotNull MultiblockControllerBase controllerBase,
                             @NotNull BlockPos centerPos,
                             @NotNull StructureOrientation orientation,
                             int xOffset, int yOffset, int zOffset,
                             @Nullable Map<String, Integer> channelValues,
                             @NotNull ItemStack triggerStack) {
        backing.spawnHintsAt(world, controllerBase, centerPos, orientation, xOffset, yOffset, zOffset,
                channelValues, triggerStack);
    }

    @NotNull
    public StructureHintResult spawnHintsAtWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controllerBase,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset,
            @Nullable Map<String, Integer> channelValues,
            @NotNull ItemStack triggerStack) {
        return backing.spawnHintsAtWithResult(world, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, triggerStack);
    }

    @NotNull
    public StructureHintResult spawnHintsAtWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controllerBase,
            @NotNull StructureCellTraversal traversal,
            @Nullable Map<String, Integer> channelValues,
            @NotNull ItemStack triggerStack) {
        return backing.spawnHintsAtWithResult(world, controllerBase, traversal, channelValues, triggerStack);
    }

    public Map<BlockPos, BlockInfo> getAllStructureBlocks(World world, BlockPos centerPos,
                                                          @NotNull StructureOrientation orientation) {
        syncToBacking();
        return backing.getAllStructureBlocks(world, centerPos, orientation);
    }

    public BlockInfo[][][] getPreview(int[] repetition) {
        return backing.getPreview(repetition);
    }

    public BlockInfo[][][] getPreview(int[] repetition, @Nullable Map<String, Integer> channelValues) {
        return backing.getPreview(repetition, channelValues);
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues) {
        return wrap(backing.createPreviewCells(repetition, channelValues));
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues,
                                           @NotNull StructureOrientation previewOrientation) {
        return wrap(backing.createPreviewCells(repetition, channelValues, previewOrientation));
    }

    public PatternMatchContext checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                          BlockPos centerPos,
                                                          @NotNull StructureOrientation orientation) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternFastAtSnapshot(blockAccess, centerPos, orientation);
        syncFromBacking();
        return result;
    }

    public PatternMatchContext checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                          BlockPos centerPos,
                                                          @NotNull StructureOrientation orientation,
                                                          int xOffset, int yOffset, int zOffset) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternFastAtSnapshot(
                blockAccess, centerPos, orientation, xOffset, yOffset, zOffset);
        syncFromBacking();
        return result;
    }

    @Nullable
    public PatternMatchContext checkOnSnapshotWithPrior(@NotNull net.minecraft.world.IBlockAccess snap,
                                                        @NotNull BlockPos centerPos,
                                                        @NotNull StructureOrientation orientation,
                                                        @Nullable FormedStructureMetadata prior) {
        syncToBacking();
        PatternMatchContext result = backing.checkOnSnapshotWithPrior(snap, centerPos, orientation, prior);
        syncFromBacking();
        return result;
    }

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset) {
        return checkPatternAtSnapshotExact(blockAccess, centerPos, orientation, xOffset, yOffset, zOffset, null);
    }

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset,
            @Nullable StructureMatchSession session) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternAtSnapshotExact(
                blockAccess, centerPos, orientation, xOffset, yOffset, zOffset, session);
        syncFromBacking();
        return result;
    }

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull StructureCellTraversal traversal,
            @Nullable StructureMatchSession session) {
        syncToBacking();
        PatternMatchContext result = backing.checkPatternAtSnapshotExact(blockAccess, traversal, session);
        syncFromBacking();
        return result;
    }

    @NotNull
    private static PreviewCells wrap(@NotNull PieceRuntimeState.PreviewCells cells) {
        return new PreviewCells(
                cells.getBlocks(),
                cells.getPredicates(),
                cells.getPreviewEntries(),
                cells.getCenter(),
                cells.minX(), cells.minY(), cells.minZ(),
                cells.maxX(), cells.maxY(), cells.maxZ());
    }

    /**
     * @deprecated Use {@link PieceRuntimeState.PreviewCells}. This alias keeps
     *             legacy source and binary references to
     *             {@code MultiblockState.PreviewCells} available.
     */
    @Deprecated
    public static class PreviewCells extends PieceRuntimeState.PreviewCells {

        private PreviewCells(@NotNull Map<BlockPos, BlockInfo> blocks,
                             @NotNull Map<BlockPos, TraceabilityPredicate> predicates,
                             @NotNull Map<BlockPos, StructureElementPreviewEntry> previewEntries,
                             @NotNull BlockPos center,
                             int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ) {
            super(blocks, predicates, previewEntries, center, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
