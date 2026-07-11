package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.ExplicitFrontFacingBlockInfo;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.RelativeDirection;
import gregtech.common.ConfigHolder;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Per-piece mutable matcher/cache state for multiblock pattern checking.
 * Each multiblock controller holds its own {@link PieceRuntimeState}, while sharing
 * the immutable canonical {@link PieceTemplate} IR with other controllers of
 * the same type.
 *
 * This class holds:
 * - The block position cache (formed structure positions)
 * - Pattern matching diagnostics and typed operation state
 * - The world state used during pattern checking
 * - Formed repetition counts
 * - A ReentrantLock for future async checking support (P2)
 *
 * <p>This is the canonical per-instance mutable runtime state. It is public
 * only because controller internals live in a sibling package; external
 * callers should use {@link StructureRuntime}
 * and typed operation requests instead.
 *
 * @see PieceTemplate for the canonical IR
 */
@ApiStatus.Internal
public final class PieceRuntimeState {

    private static final StructureOrientation DEFAULT_PREVIEW_ORIENTATION = StructureOrientation.of(
            EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.UP, false, false);

    /**
     * The canonical piece IR.
     */
    private final PieceTemplate template;

    // --- Per-instance mutable state ---

    private final BlockWorldState worldState = new BlockWorldState();
    private final StructureEvaluationContext<Object> evaluationContext = new StructureEvaluationContext<>();
    @NotNull
    private Map<MultiblockAbility<?>, Integer> missingAbilities = Collections.emptyMap();

    /** Cache of formed structure block positions -> block info */
    public final Long2ObjectMap<BlockInfo> cache = new Long2ObjectOpenHashMap<>();

    /**
     * Cell offsets that were used to populate {@link #cache}. The cache fast-path only
     * applies when the new call's offsets match these — otherwise the cache was built
     * for a different slice and the fast-path is skipped so a full re-check runs.
     */
    private int cachedXOffset = 0;
    private int cachedYOffset = 0;
    private int cachedZOffset = 0;
    private boolean cacheOffsetsRecorded = false;

    /** The repetitions per aisle along the axis of repetition (filled after successful pattern check) */
    public int[] formedRepetitionCount;

    /** Lock for thread-safe pattern checking (preparation for P2 async checking) */
    private final ReentrantLock lock = new ReentrantLock();

    public PieceRuntimeState(@NotNull PieceTemplate template) {
        this.template = template;
        this.formedRepetitionCount = new int[template.getAisles().length];
    }

    /**
     * @return the canonical piece IR this state is bound to
     */
    @NotNull
    public PieceTemplate getTemplate() {
        return template;
    }

    /**
     * @return the current pattern error, or null if no error
     */
    public PatternError getError() {
        return worldState.error;
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getMissingAbilities() {
        return missingAbilities;
    }

    /**
     * Acquire the lock for thread-safe operations. Used by P2 async checking.
     */
    public void lock() {
        lock.lock();
    }

    /**
     * Release the lock.
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * Try to acquire the lock without blocking.
     *
     * @return true if the lock was acquired
     */
    public boolean tryLock() {
        return lock.tryLock();
    }

    /**
     * Clear the block position cache.
     */
    public void clearCache() {
        cache.clear();
        cacheOffsetsRecorded = false;
    }

    void clearFormedRepetitionCount() {
        for (int i = 0; i < formedRepetitionCount.length; i++) {
            formedRepetitionCount[i] = 0;
        }
    }

    @NotNull
    Checkpoint checkpoint() {
        return new Checkpoint(this);
    }

    void restoreTo(@NotNull Checkpoint checkpoint) {
        cache.clear();
        cache.putAll(checkpoint.cache);
        cachedXOffset = checkpoint.cachedXOffset;
        cachedYOffset = checkpoint.cachedYOffset;
        cachedZOffset = checkpoint.cachedZOffset;
        cacheOffsetsRecorded = checkpoint.cacheOffsetsRecorded;
        if (formedRepetitionCount.length != checkpoint.formedRepetitionCount.length) {
            formedRepetitionCount = checkpoint.formedRepetitionCount.clone();
        } else {
            System.arraycopy(checkpoint.formedRepetitionCount, 0,
                    formedRepetitionCount, 0, formedRepetitionCount.length);
        }
        missingAbilities = checkpoint.missingAbilities;
    }

    static final class Checkpoint {

        @NotNull
        private final Long2ObjectMap<BlockInfo> cache;
        private final int cachedXOffset;
        private final int cachedYOffset;
        private final int cachedZOffset;
        private final boolean cacheOffsetsRecorded;
        @NotNull
        private final int[] formedRepetitionCount;
        @NotNull
        private final Map<MultiblockAbility<?>, Integer> missingAbilities;

        private Checkpoint(@NotNull PieceRuntimeState state) {
            this.cache = new Long2ObjectOpenHashMap<>(state.cache);
            this.cachedXOffset = state.cachedXOffset;
            this.cachedYOffset = state.cachedYOffset;
            this.cachedZOffset = state.cachedZOffset;
            this.cacheOffsetsRecorded = state.cacheOffsetsRecorded;
            this.formedRepetitionCount = state.formedRepetitionCount.clone();
            this.missingAbilities = state.missingAbilities;
        }

        @NotNull
        Long2ObjectMap<BlockInfo> copyCache() {
            return new Long2ObjectOpenHashMap<>(cache);
        }
    }

    public boolean checkPatternFastAt(World world, BlockPos centerPos,
                                      @NotNull StructureOrientation orientation) {
        return checkPatternFastAt(world, centerPos, orientation, true, 0, 0, 0);
    }

    public boolean checkPatternFastAt(World world, BlockPos centerPos,
                                      @NotNull StructureOrientation orientation,
                                      boolean doRandomCheck) {
        return checkPatternFastAt(world, centerPos, orientation, doRandomCheck, 0, 0, 0);
    }

    /**
     * Fast pattern check using cache, then full check if needed, with an additional
     * template-local cell offset folded into the per-cell transformation.
     * <p>
     * Mirrors the contract of
     * {@link #autoBuildAt(EntityPlayer, MultiblockControllerBase, BlockPos, int, int, int, Map, boolean)}:
     * the offsets are added to every cell's (x, y, z) before
     * {@link RelativeDirection#setActualRelativeOffset} runs, so the transformation happens
     * exactly once per cell. The cache fast-path is unaffected by the offsets because it
     * verifies world state at already-transformed positions and only runs when the previous
     * successful check used the same offsets (i.e. the cache is always invalidated by
     * {@code checkPatternAt} on miss).
     *
     * @param doRandomCheck if true and cache is large (>512), use random sampling instead of full scan
     * @param xOffset       template-local x offset added to every cell before transformation
     * @param yOffset       template-local y offset added to every cell before transformation
     * @param zOffset       template-local z offset added to every cell before transformation
     */
    public boolean checkPatternFastAt(World world, BlockPos centerPos,
                                      @NotNull StructureOrientation orientation,
                                      boolean doRandomCheck,
                                      int xOffset, int yOffset, int zOffset) {
        if (probeCacheAt(world, doRandomCheck, xOffset, yOffset, zOffset)) {
            return !worldState.hasError();
        }

        boolean matched = checkPatternAt(world, centerPos, orientation.withFlipped(false),
                xOffset, yOffset, zOffset, null);
        if (orientation.allowsFlip()) {
            if (matched) {
                return true;
            }
            Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities = missingAbilities;
            PatternError unflippedError = worldState.error;
            matched = checkPatternAt(world, centerPos, orientation.withFlipped(true),
                    xOffset, yOffset, zOffset, null);
            if (!matched && shouldKeepUnflippedFailure(unflippedError, unflippedMissingAbilities,
                    worldState.error, missingAbilities)) {
                missingAbilities = unflippedMissingAbilities;
                worldState.setError(unflippedError);
            }
        }
        if (!matched) clearCache();
        return matched;
    }

    /**
     * Read-only cache probe for typed graph checks that already have a prior
     * piece result to reuse. A hit only means the cached live blocks still match;
     * callers must supply the preserved contribution/context themselves.
     */
    public boolean probeCacheAt(@NotNull World world,
                                boolean doRandomCheck,
                                int xOffset, int yOffset, int zOffset) {
        // Cache fast-path is only valid when the offsets used to build the cache match
        // the current call's offsets. A non-empty cache with mismatched offsets belongs
        // to a different slice and must not be trusted.
        boolean cacheValid = cache.isEmpty() == false
                && cacheOffsetsRecorded
                && xOffset == cachedXOffset
                && yOffset == cachedYOffset
                && zOffset == cachedZOffset;
        if (!cacheValid) {
            return false;
        }
        return probeCacheEntries(world, doRandomCheck);
    }

    private boolean probeCacheEntries(@NotNull World world,
                                      boolean doRandomCheck) {
        if (!doRandomCheck || cache.size() < 512) {
            for (Map.Entry<Long, BlockInfo> entry : cache.entrySet()) {
                if (!probeCacheEntry(world, entry)) {
                    return false;
                }
            }
            return true;
        }

        int cacheSize = cache.size();
        int sampleCount = (int) Math.ceil(cacheSize * ConfigHolder.machines.delayStructureCheckSample);
        Iterator<Map.Entry<Long, BlockInfo>> iterator = cache.entrySet().iterator();
        int step = Math.max(1, cacheSize / sampleCount);

        while (iterator.hasNext() && sampleCount > 0) {
            int skip = ThreadLocalRandom.current().nextInt(step);
            for (int i = 0; i < skip && iterator.hasNext(); i++) {
                iterator.next();
            }

            if (!iterator.hasNext()) break;

            Map.Entry<Long, BlockInfo> entry = iterator.next();
            sampleCount--;

            if (!probeCacheEntry(world, entry)) {
                return false;
            }

            if (iterator.hasNext()) {
                for (int i = 0; i < step - skip - 1 && iterator.hasNext(); i++) {
                    iterator.next();
                }
            }
        }
        return true;
    }

    private boolean probeCacheEntry(@NotNull World world,
                                    @NotNull Map.Entry<Long, BlockInfo> entry) {
        BlockPos pos = BlockPos.fromLong(entry.getKey());
        StructureWorldReadTracker.recordBlockStateRead();
        IBlockState blockState = world.getBlockState(pos);
        if (blockState != entry.getValue().getBlockState()) {
            return false;
        }
        TileEntity cachedTileEntity = entry.getValue().getTileEntity();
        if (cachedTileEntity == null) {
            return true;
        }
        StructureWorldReadTracker.recordTileEntityRead();
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!matchesCachedMetaTileEntity(entry.getValue(), cachedTileEntity, tileEntity)) {
            return false;
        }
        return tileEntity == cachedTileEntity;
    }

    private static boolean matchesCachedMetaTileEntity(@NotNull BlockInfo cached,
                                                       @NotNull TileEntity cachedTileEntity,
                                                       @Nullable TileEntity tileEntity) {
        if (!(cachedTileEntity instanceof IGregTechTileEntity)) {
            return true;
        }
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            return false;
        }
        Object cachedInfo = cached.getInfo();
        if (!(cachedInfo instanceof CachedMetaTileEntityInfo)) {
            return true;
        }
        MetaTileEntity current = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        return current != null && ((CachedMetaTileEntityInfo) cachedInfo).matches(current);
    }

    private boolean checkPatternAt(World world, BlockPos centerPos,
                                   @NotNull StructureOrientation orientation,
                                   int xOffset, int yOffset, int zOffset,
                                   @Nullable StructureMatchSession session) {
        return checkFixedStructureCells(world, null, centerPos, orientation,
                xOffset, yOffset, zOffset, session,
                StructureEvaluationContext.Operation.MATCH_WORLD, true);
    }

    private static boolean shouldKeepUnflippedFailure(
            @Nullable PatternError unflippedError,
            @NotNull Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities,
            @Nullable PatternError flippedError,
            @NotNull Map<MultiblockAbility<?>, Integer> flippedMissingAbilities) {
        StructureFailureTrace unflipped = failureForSelection(false, unflippedError, unflippedMissingAbilities);
        StructureFailureTrace flipped = failureForSelection(true, flippedError, flippedMissingAbilities);
        return StructureFailureSelection.select(unflipped, flipped) == unflipped;
    }

    @NotNull
    private static StructureFailureTrace failureForSelection(
            boolean flipped,
            @Nullable PatternError error,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        return new StructureFailureTrace.Builder("structure", BlockPos.ORIGIN)
                .orientation(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, flipped)
                .path("piece-template")
                .operation("CHECK")
                .result("failed")
                .kind(classifyFailure(error, missingAbilities))
                .progressDepth(error == null ? 0 : 1)
                .missingAbilities(missingAbilities)
                .error(error)
                .build();
    }

    @NotNull
    private static StructureFailureTrace.Kind classifyFailure(
            @Nullable PatternError error,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (!missingAbilities.isEmpty()) {
            return StructureFailureTrace.Kind.MISSING_ABILITY;
        }
        if (error instanceof CountLimitError) {
            return StructureFailureTrace.Kind.COUNT_LIMIT;
        }
        return error == null ? StructureFailureTrace.Kind.BLOCK_MISMATCH : StructureFailureTrace.Kind.BLOCK_MISMATCH;
    }

    @SuppressWarnings("unchecked")
    private boolean checkElement(@NotNull IStructureElement<?> element,
                                 @Nullable StructureMatchSession session,
                                 @NotNull StructureEvaluationContext.Operation operation) {
        Object controller = session == null ? null : session.getControllerContext();
        evaluationContext.update(controller, session, worldState, operation);
        IStructureElement<Object> typedElement = (IStructureElement<Object>) element;
        return evaluationContext.transaction(typedElement::match);
    }

    @FunctionalInterface
    private interface FixedStructureCellVisitor {

        void visit(@NotNull FixedStructureCell cell,
                   @NotNull Map<StructureElementPreview.CandidateGroup, Integer> layerCounts);
    }

    @FunctionalInterface
    private interface FixedStructureCellPredicate {

        boolean visit(@NotNull FixedStructureCell cell);
    }

    @FunctionalInterface
    private interface FixedStructureCellFilter {

        boolean includes(int elementXIndex, int elementYIndex, int aisleIndex);
    }

    private static final class FixedStructureCell {

        private final int aisleIndex;
        private final int repetitionIndex;
        private final int localX;
        private final int localY;
        private final int localZ;
        @NotNull
        private final BlockPos worldPos;
        @NotNull
        private final IStructureElement<?> element;
        @NotNull
        private final StructureElementPreviewEntry previewEntry;

        private FixedStructureCell(int aisleIndex, int repetitionIndex,
                                   int localX, int localY, int localZ,
                                   @NotNull BlockPos worldPos,
                                   @NotNull IStructureElement<?> element,
                                   @NotNull StructureElementPreviewEntry previewEntry) {
            this.aisleIndex = aisleIndex;
            this.repetitionIndex = repetitionIndex;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.worldPos = worldPos;
            this.element = element;
            this.previewEntry = previewEntry;
        }
    }

    private void visitFixedStructureCells(@NotNull int[] repetitions,
                                          @NotNull BlockPos centerPos,
                                          @NotNull StructureOrientation orientation,
                                          int xOffset, int yOffset, int zOffset,
                                          @NotNull FixedStructureCellVisitor visitor) {
        PieceTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();

        int z = -centerOffset.maxZ();
        for (int c = 0; c < fingerLength; c++) {
            int repetitionCount = c < repetitions.length ? repetitions[c] : 0;
            for (int r = 0; r < repetitionCount; r++) {
                Map<StructureElementPreview.CandidateGroup, Integer> previewLayerCounts = new HashMap<>();
                visitFixedStructureSlice(c, r, z, centerPos, orientation, xOffset, yOffset, zOffset,
                        cell -> {
                            visitor.visit(cell, previewLayerCounts);
                            return true;
                        });
                z++;
            }
        }
    }

    private boolean visitFixedStructureSlice(int aisleIndex,
                                             int repetitionIndex,
                                             int localZ,
                                             @NotNull BlockPos centerPos,
                                             @NotNull StructureOrientation orientation,
                                             int xOffset, int yOffset, int zOffset,
                                             @NotNull FixedStructureCellPredicate visitor) {
        PieceTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
            for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                if (!visitor.visit(createFixedStructureCell(
                        aisleIndex, repetitionIndex, a, b, x, y, localZ,
                        centerPos, orientation, xOffset, yOffset, zOffset))) {
                    return false;
                }
            }
        }
        return true;
    }

    @NotNull
    private FixedStructureCell createFixedStructureCell(int aisleIndex,
                                                       int repetitionIndex,
                                                       int elementXIndex,
                                                       int elementYIndex,
                                                       int localX, int localY, int localZ,
                                                       @NotNull BlockPos centerPos,
                                                       @NotNull StructureOrientation orientation,
                                                       int xOffset, int yOffset, int zOffset) {
        IStructureElement<?> element = template.getElements()[aisleIndex][elementYIndex][elementXIndex];
        BlockPos pos = RelativeDirection.setActualRelativeOffset(
                localX + xOffset, localY + yOffset, localZ + zOffset,
                orientation.getStructureFront(), orientation.getUp(),
                orientation.isFlipped(), template.getStructureDir())
                .add(centerPos);
        return new FixedStructureCell(
                aisleIndex, repetitionIndex, localX, localY, localZ, pos, element,
                StructureElementPreviewEntry.of(element.getPreview(), previewTooltip(element)));
    }

    private boolean checkFixedStructureCells(@Nullable World world,
                                             @Nullable net.minecraft.world.IBlockAccess blockAccess,
                                             @NotNull BlockPos centerPos,
                                             @NotNull StructureOrientation orientation,
                                             int xOffset, int yOffset, int zOffset,
                                             @Nullable StructureMatchSession session,
                                             @NotNull StructureEvaluationContext.Operation operation,
                                             boolean updateCache) {
        if (session == null) {
            StructureMatchSession internalSession = new StructureMatchSession(
                    Collections.emptyMap(), Collections.emptyList());
            boolean result = checkFixedStructureCells(world, blockAccess, centerPos, orientation,
                    xOffset, yOffset, zOffset, internalSession, operation, updateCache);
            if (!result) {
                return false;
            }
            if (failForMissingInternalSessionRequirements(internalSession)) {
                return false;
            }
            missingAbilities = Collections.emptyMap();
            return true;
        }

        int[][] aisleRepetitions = template.getAisleRepetitions();
        PieceTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();

        boolean findFirstAisle = false;
        int minZ = -centerOffset.maxZ();

        StructureMatchSession.Checkpoint initialCheckpoint = session.checkpoint();
        this.missingAbilities = Collections.emptyMap();
        if (updateCache) {
            cache.clear();
            this.cachedXOffset = xOffset;
            this.cachedYOffset = yOffset;
            this.cachedZOffset = zOffset;
            this.cacheOffsetsRecorded = true;
        }

        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset.minZ()); r++) {
                if (!visitFixedStructureSlice(c, r, z, centerPos, orientation, xOffset, yOffset, zOffset,
                        cell -> checkFixedStructureCell(
                                cell, world, blockAccess,
                                session, operation, updateCache))) {
                    if (findFirstAisle) {
                        if (r < aisleRepetitions[c][0]) {
                            r = c = 0;
                            z = minZ++;
                            session.restoreTo(initialCheckpoint);
                            findFirstAisle = false;
                        }
                    } else {
                        z++;
                    }
                    continue loop;
                }
                findFirstAisle = true;
                z++;

                validRepetitions++;
            }
            if (validRepetitions < aisleRepetitions[c][0]) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return false;
            }

            formedRepetitionCount[c] = validRepetitions;
        }

        worldState.setError(null);
        return true;
    }

    private boolean failForMissingInternalSessionRequirements(@NotNull StructureMatchSession session) {
        Map<MultiblockAbility<?>, Integer> abilities = new HashMap<>();

        StructureMatchCollector.Validation operationValidation =
                StructureMatchCollector.validate(session.getOperationState());
        if (!operationValidation.missingAbilities.isEmpty()) {
            operationValidation.missingAbilities.forEach(
                    (ability, deficit) -> abilities.merge(ability, deficit, Integer::sum));
        }

        if (operationValidation.success) {
            missingAbilities = Collections.emptyMap();
            return false;
        }

        missingAbilities = abilities.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(abilities);
        StructureMatchCollector.Validation failure = operationValidation;
        worldState.setError(failure.error == null
                ? new PatternStringError(failure.errorMessage == null
                        ? "gregtech.multiblock.pattern.error.requirements"
                        : failure.errorMessage)
                : failure.error);
        return true;
    }

    private boolean checkFixedStructureCell(@NotNull FixedStructureCell cell,
                                            @Nullable World world,
                                            @Nullable net.minecraft.world.IBlockAccess blockAccess,
                                            @Nullable StructureMatchSession session,
                                            @NotNull StructureEvaluationContext.Operation operation,
                                            boolean updateCache) {
        if (operation.readsSnapshot()) {
            if (blockAccess == null) {
                throw new IllegalArgumentException("Snapshot fixed-structure check requires an IBlockAccess");
            }
            worldState.updateFromBlockAccess(blockAccess, cell.worldPos, session, cell.previewEntry);
        } else {
            if (world == null) {
                throw new IllegalArgumentException("Live fixed-structure check requires a World");
            }
            worldState.update(world, cell.worldPos, session, cell.previewEntry);
        }
        if (!checkElement(cell.element, session, operation)) {
            return false;
        }
        if (updateCache && !operation.readsSnapshot()) {
            recordLiveCacheCell(cell);
        }
        return true;
    }

    private boolean visitFixedStructureCellsWhere(@NotNull BlockPos centerPos,
                                                  @NotNull StructureOrientation orientation,
                                                  int xOffset, int yOffset, int zOffset,
                                                  @NotNull FixedStructureCellFilter filter,
                                                  @NotNull FixedStructureCellPredicate visitor) {
        PieceTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        for (int c = 0, localZ = -centerOffset.maxZ(); c < fingerLength; c++, localZ++) {
            for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
                for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                    if (!filter.includes(a, b, c)) {
                        continue;
                    }
                    if (!visitor.visit(createFixedStructureCell(
                            c, 0, a, b, x, y, localZ,
                            centerPos, orientation, xOffset, yOffset, zOffset))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void recordLiveCacheCell(@NotNull FixedStructureCell cell) {
        IBlockState blockState = worldState.getCachedBlockState();
        if (blockState == null) return;
        TileEntity tileEntity = blockState.getBlock().hasTileEntity(blockState)
                ? worldState.getTileEntity()
                : null;
        if (tileEntity instanceof IGregTechTileEntity && !((IGregTechTileEntity) tileEntity).isValid()) {
            cache.put(cell.worldPos.toLong(), new BlockInfo(blockState, null));
            return;
        }
        cache.put(cell.worldPos.toLong(), new BlockInfo(
                blockState, tileEntity, createCacheInfo(tileEntity)));
    }

    @Nullable
    private static Object createCacheInfo(@Nullable TileEntity tileEntity) {
        if (tileEntity instanceof IGregTechTileEntity) {
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (metaTileEntity != null) {
                return new CachedMetaTileEntityInfo(metaTileEntity);
            }
        }
        return null;
    }

    private void updateOperationCellContext(@NotNull StructureEvaluationContext<Object> evaluationContext,
                                            @NotNull BlockWorldState operationWorldState,
                                            @NotNull World world,
                                            @NotNull BlockPos pos,
                                            @NotNull MultiblockControllerBase controllerBase,
                                            @NotNull StructureEvaluationContext.Operation operation) {
        operationWorldState.update(world, pos, null);
        evaluationContext.update(controllerBase, null, operationWorldState, operation);
    }

    private boolean placeBuildCandidate(@NotNull BlockInfo matchedInfo,
                                        @NotNull StructureEvaluationContext<Object> context) {
        World world = context.getWorld();
        if (world == null) {
            return false;
        }
        return world.setBlockState(context.getPos(), matchedInfo.getBlockState());
    }

    @SuppressWarnings("unchecked")
    private boolean elementMatches(@NotNull IStructureElement<?> element,
                                   @NotNull StructureEvaluationContext<Object> context) {
        return ((IStructureElement<Object>) element).match(context);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    private BlockInfo[] getElementCandidates(@NotNull IStructureElement<?> element,
                                             @NotNull StructureEvaluationContext<Object> context) {
        BlockInfo[] candidates = ((IStructureElement<Object>) element).getCandidates(context);
        return candidates == null ? new BlockInfo[0] : candidates;
    }

    @SuppressWarnings("unchecked")
    @NotNull
    private StructureElementPreview getElementPreview(@NotNull IStructureElement<?> element,
                                                      @NotNull StructureEvaluationContext<Object> context) {
        return ((IStructureElement<Object>) element).getPreview(context);
    }

    private static final class BuildTraversalState {

        @NotNull
        private final BlockWorldState worldState = new BlockWorldState();
        @NotNull
        private final StructureEvaluationContext<Object> evaluationContext = new StructureEvaluationContext<>();
        @NotNull
        private final Map<StructureElementPreview.CandidateGroup, BlockInfo[]> cacheInfos = new HashMap<>();
        @NotNull
        private final Map<StructureElementPreview.CandidateGroup, Integer> cacheGlobal = new HashMap<>();
        @NotNull
        private final Map<BlockPos, Object> blocks = new HashMap<>();
        @NotNull
        private final Map<BlockPos, EnumFacing> explicitFrontFacings = new HashMap<>();
        @NotNull
        private final StructureBuildResult.Builder result = StructureBuildResult.builder();
    }

    private static final class HintTraversalState {

        @NotNull
        private final BlockWorldState worldState = new BlockWorldState();
        @NotNull
        private final StructureEvaluationContext<Object> evaluationContext = new StructureEvaluationContext<>();
        @NotNull
        private final StructureHintResult.Builder result = StructureHintResult.builder();
    }

    private static final class PreviewTraversalState {

        @NotNull
        private final Map<StructureElementPreview.CandidateGroup, BlockInfo[]> cacheInfos = new HashMap<>();
        @NotNull
        private final Map<StructureElementPreview.CandidateGroup, Integer> cacheGlobal = new HashMap<>();
        @NotNull
        private final Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        @NotNull
        private final Map<BlockPos, StructureElementPreviewEntry> previewEntries = new HashMap<>();
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void record(@NotNull BlockPos pos, @NotNull BlockInfo info,
                            @NotNull StructureElementPreviewEntry previewEntry) {
            blocks.put(pos, info);
            previewEntries.put(pos, previewEntry);
            minX = Math.min(pos.getX(), minX);
            minY = Math.min(pos.getY(), minY);
            minZ = Math.min(pos.getZ(), minZ);
            maxX = Math.max(pos.getX(), maxX);
            maxY = Math.max(pos.getY(), maxY);
            maxZ = Math.max(pos.getZ(), maxZ);
        }

        @NotNull
        private PreviewCells toCells(@NotNull BlockPos center) {
            if (blocks.isEmpty()) {
                return new PreviewCells(Collections.emptyMap(), Collections.emptyMap(),
                        center, 0, 0, 0, 0, 0, 0);
            }
            return new PreviewCells(blocks, previewEntries, center,
                    minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public static class PreviewCells {

        @NotNull
        private final Map<BlockPos, BlockInfo> blocks;
        @NotNull
        private final Map<BlockPos, StructureElementPreviewEntry> previewEntries;
        @NotNull
        private final BlockPos center;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        protected PreviewCells(@NotNull Map<BlockPos, BlockInfo> blocks,
                               @NotNull Map<BlockPos, StructureElementPreviewEntry> previewEntries,
                               @NotNull BlockPos center,
                               int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ) {
            this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks));
            this.previewEntries = Collections.unmodifiableMap(new HashMap<>(previewEntries));
            this.center = center;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        @NotNull
        public Map<BlockPos, BlockInfo> getBlocks() {
            return blocks;
        }

        @NotNull
        public Map<BlockPos, StructureElementPreviewEntry> getPreviewEntries() {
            return previewEntries;
        }

        public boolean isEmpty() {
            for (BlockInfo info : blocks.values()) {
                if (info != null
                        && info != BlockInfo.EMPTY
                        && info.getBlockState() != null
                        && info.getBlockState().getBlock() != Blocks.AIR) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Actual selected preview center in the same coordinate frame as {@link #getBlocks()}.
         */
        @NotNull
        public BlockPos getCenter() {
            return center;
        }

        @NotNull
        public BlockInfo[][][] toBlockArray() {
            if (blocks.isEmpty()) {
                return new BlockInfo[][][]{{{BlockInfo.EMPTY}}};
            }
            BlockInfo[][][] result = (BlockInfo[][][]) Array.newInstance(
                    BlockInfo.class,
                    maxX - minX + 1,
                    maxY - minY + 1,
                    maxZ - minZ + 1);
            blocks.forEach((pos, info) ->
                    result[pos.getX() - minX][pos.getY() - minY][pos.getZ() - minZ] = info);
            return result;
        }

        int minX() {
            return minX;
        }

        int minY() {
            return minY;
        }

        int minZ() {
            return minZ;
        }

        int maxX() {
            return maxX;
        }

        int maxY() {
            return maxY;
        }

        int maxZ() {
            return maxZ;
        }
    }

    private void autoBuildCell(@NotNull FixedStructureCell cell,
                                @NotNull Map<StructureElementPreview.CandidateGroup, Integer> cacheLayer,
                                @NotNull EntityPlayer player,
                                @NotNull MultiblockControllerBase controllerBase,
                                @Nullable Map<String, Integer> channelValues,
                                @Nullable AbilityPlacementTracker abilityTracker,
                                @NotNull ItemStack triggerStack,
                                @NotNull BuildTraversalState buildState,
                                @NotNull StructureEvaluationContext.Operation operation) {
        World world = player.world;
        BlockPos pos = cell.worldPos;
        buildState.result.recordVisitedCell();

        updateOperationCellContext(buildState.evaluationContext, buildState.worldState,
                world, pos, controllerBase, operation);
        if (buildState.evaluationContext.probe(evaluation -> elementMatches(cell.element, evaluation))) {
            buildState.result.recordExistingCell();
            buildState.blocks.put(pos, world.getBlockState(pos));
            if (abilityTracker != null) {
                abilityTracker.recordWorldTile(pos, world.getTileEntity(pos));
            }
            return;
        }
        if (!world.getBlockState(pos).getMaterial().isReplaceable()) {
            buildState.result.recordPlacementBudget();
            buildState.result.recordPlacementFailureCell();
            return;
        }

        boolean find = false;
        BlockInfo[] infos = new BlockInfo[0];
        StructureElementPreview preview = getElementPreview(cell.element, buildState.evaluationContext);
        StructureElementPreview.CandidateGroup matchedGroup = null;
        for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
            int nextCount;
            if (limit.getMinLayerCount() > 0) {
                if (!cacheLayer.containsKey(limit)) {
                    nextCount = 1;
                } else if (cacheLayer.get(limit) < limit.getMinLayerCount() &&
                        (limit.getMaxLayerCount() == -1 ||
                                cacheLayer.get(limit) < limit.getMaxLayerCount())) {
                    nextCount = cacheLayer.get(limit) + 1;
                } else {
                    continue;
                }
            } else {
                continue;
            }
            if (!buildState.cacheInfos.containsKey(limit)) {
                buildState.cacheInfos.put(limit, limit.getCandidates());
            }
            infos = buildState.cacheInfos.get(limit);
            if (!hasPlaceableCandidate(infos, abilityTracker)) {
                continue;
            }
            cacheLayer.put(limit, nextCount);
            matchedGroup = limit;
            find = true;
            break;
        }
        if (!find) {
            for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
                int nextCount;
                if (limit.getMinGlobalCount() > 0) {
                    if (!buildState.cacheGlobal.containsKey(limit)) {
                        nextCount = 1;
                    } else if (buildState.cacheGlobal.get(limit) < limit.getMinGlobalCount() &&
                            (limit.getMaxGlobalCount() == -1 ||
                                    buildState.cacheGlobal.get(limit) < limit.getMaxGlobalCount())) {
                        nextCount = buildState.cacheGlobal.get(limit) + 1;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (!buildState.cacheInfos.containsKey(limit)) {
                    buildState.cacheInfos.put(limit, limit.getCandidates());
                }
                infos = buildState.cacheInfos.get(limit);
                if (!hasPlaceableCandidate(infos, abilityTracker)) {
                    continue;
                }
                buildState.cacheGlobal.put(limit, nextCount);
                matchedGroup = limit;
                find = true;
                break;
            }
        }
        if (!find) {
            for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
                int previewCount = limit.getPreviewCount();
                if (previewCount <= 0) {
                    continue;
                }
                int global = buildState.cacheGlobal.getOrDefault(limit, 0);
                int layer = cacheLayer.getOrDefault(limit, 0);
                if (global >= previewCount ||
                        (limit.getMaxGlobalCount() != -1 && global >= limit.getMaxGlobalCount()) ||
                        (limit.getMaxLayerCount() != -1 && layer >= limit.getMaxLayerCount())) {
                    continue;
                }
                if (!buildState.cacheInfos.containsKey(limit)) {
                    buildState.cacheInfos.put(limit, limit.getCandidates());
                }
                infos = buildState.cacheInfos.get(limit);
                if (!hasPlaceableCandidate(infos, abilityTracker)) {
                    continue;
                }
                buildState.cacheGlobal.put(limit, global + 1);
                cacheLayer.put(limit, layer + 1);
                matchedGroup = limit;
                find = true;
                break;
            }
        }
        if (!find) {
            for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
                if (limit.getMaxLayerCount() != -1 &&
                        cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.getMaxLayerCount())
                    continue;
                if (limit.getMaxGlobalCount() != -1 &&
                        buildState.cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.getMaxGlobalCount())
                    continue;
                if (!buildState.cacheInfos.containsKey(limit)) {
                    buildState.cacheInfos.put(limit, limit.getCandidates());
                }
                if (!hasPlaceableCandidate(buildState.cacheInfos.get(limit), abilityTracker)) {
                    continue;
                }
                cacheLayer.put(limit, cacheLayer.getOrDefault(limit, 0) + 1);
                buildState.cacheGlobal.put(limit, buildState.cacheGlobal.getOrDefault(limit, 0) + 1);
                infos = ArrayUtils.addAll(infos, buildState.cacheInfos.get(limit));
            }
            for (StructureElementPreview.CandidateGroup common : preview.getCommon()) {
                if (!buildState.cacheInfos.containsKey(common)) {
                    buildState.cacheInfos.put(common, common.getCandidates());
                }
                infos = ArrayUtils.addAll(infos, buildState.cacheInfos.get(common));
                if ((common.getChannelName() != null || common.getDefaultCandidate() != null) &&
                        (matchedGroup == null ||
                                (matchedGroup.getChannelName() == null && common.getChannelName() != null))) {
                    matchedGroup = common;
                }
            }
        }

        int availableCandidateCount = StructurePlacementDecision.countPlaceable(infos);
        infos = StructurePlacementDecision.filterPlaceable(infos, abilityTracker);
        if (infos.length == 0 && !find) {
            BlockInfo[] directCandidates = getElementCandidates(cell.element, buildState.evaluationContext);
            availableCandidateCount = StructurePlacementDecision.countPlaceable(directCandidates);
            infos = StructurePlacementDecision.filterPlaceable(directCandidates, abilityTracker);
            matchedGroup = null;
        }
        List<ItemStack> candidates = StructurePlacementDecision.toItemStacks(infos);
        if (candidates.isEmpty()) {
            buildState.result.recordPlacementBudget();
            if (abilityTracker != null && availableCandidateCount > 0) {
                buildState.result.recordAbilityLimitBlockedCell();
            } else {
                buildState.result.recordMissingCandidateCell();
            }
            return;
        }

        if (StructureOperationRequest.isNoHatch(channelValues)) {
            List<BlockInfo> nonHatchInfos = new ArrayList<>();
            List<ItemStack> nonHatchCandidates = new ArrayList<>();
            int candidateIdx = 0;
            boolean hadHatchCandidates = false;
            for (BlockInfo info : infos) {
                if (info.getBlockState().getBlock() == Blocks.AIR) continue;
                if (info.getTileEntity() instanceof IGregTechTileEntity) {
                    hadHatchCandidates = true;
                } else {
                    nonHatchInfos.add(info);
                    nonHatchCandidates.add(candidates.get(candidateIdx));
                }
                candidateIdx++;
            }
            boolean keepMatchedGroup = !nonHatchInfos.isEmpty();
            if (nonHatchInfos.isEmpty()) {
                // All candidates in infos are hatches. Search all candidate groups
                // (both common and limited) for a non-hatch casing candidate.
                for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
                    if (group.getMaxGlobalCount() != -1 &&
                            buildState.cacheGlobal.getOrDefault(group, 0) >= group.getMaxGlobalCount()) {
                        continue;
                    }
                    if (!buildState.cacheInfos.containsKey(group)) {
                        buildState.cacheInfos.put(group, group.getCandidates());
                    }
                    BlockInfo[] groupInfos = buildState.cacheInfos.get(group);
                    for (BlockInfo info : groupInfos) {
                        if (info.getBlockState().getBlock() != Blocks.AIR &&
                                !(info.getTileEntity() instanceof IGregTechTileEntity)) {
                            nonHatchInfos.add(info);
                        }
                    }
                    if (!nonHatchInfos.isEmpty()) break;
                }
                if (nonHatchInfos.isEmpty()) {
                    for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
                        if (!buildState.cacheInfos.containsKey(group)) {
                            buildState.cacheInfos.put(group, group.getCandidates());
                        }
                        BlockInfo[] groupInfos = buildState.cacheInfos.get(group);
                        for (BlockInfo info : groupInfos) {
                            if (info.getBlockState().getBlock() != Blocks.AIR &&
                                    !(info.getTileEntity() instanceof IGregTechTileEntity)) {
                                nonHatchInfos.add(info);
                            }
                        }
                        if (!nonHatchInfos.isEmpty()) break;
                    }
                }
                if (!nonHatchInfos.isEmpty() && nonHatchCandidates.isEmpty()) {
                    nonHatchCandidates = nonHatchInfos.stream()
                            .map(StructurePlacementDecision::getStackForBlockInfo)
                            .collect(Collectors.toList());
                }
            }
            if (!nonHatchInfos.isEmpty()) {
                infos = nonHatchInfos.toArray(new BlockInfo[0]);
                candidates = nonHatchCandidates;
                if (!keepMatchedGroup) {
                    matchedGroup = null;
                }
                if (hadHatchCandidates) {
                    buildState.result.recordSkippedHatchCell();
                }
            }
        }

        buildState.result.recordPlacementBudget();
        ItemStack representativeRequired = operation.isSurvivalBuild()
                ? StructurePlacementDecision.representativeRequiredStack(
                        infos, candidates, matchedGroup, channelValues, abilityTracker)
                : ItemStack.EMPTY;

        StructurePlacementDecision.Selection buildCandidate = StructurePlacementDecision.select(
                player, infos, candidates, matchedGroup, channelValues, abilityTracker, operation);
        if (buildCandidate == null) {
            buildState.result.recordUnavailableItemCell();
            if (!representativeRequired.isEmpty()) {
                buildState.result.recordRequiredItem(representativeRequired);
                buildState.result.recordMissingItem(representativeRequired);
            }
            return;
        }
        ItemStack found = buildCandidate.getRequiredStack();
        BlockInfo matchedInfo = buildCandidate.getMatchedInfo();

        if (operation.isSurvivalBuild()) {
            buildState.result.recordRequiredItem(found);
        }
        IBlockState previousState = world.getBlockState(pos);
        if (!placeBuildCandidate(matchedInfo, buildState.evaluationContext)) {
            buildState.result.recordPlacementFailureCell();
            return;
        }
        if (!buildCandidate.consume(player)) {
            world.setBlockState(pos, previousState);
            buildState.result.recordUnavailableItemCell();
            if (operation.isSurvivalBuild()) {
                buildState.result.recordMissingItem(found);
            }
            return;
        }
        IBlockState state = matchedInfo.getBlockState();
        buildState.result.recordPlacedCell();
        if (buildCandidate.consumesItem()) {
            buildState.result.recordConsumedItem(found);
        }
        if (abilityTracker != null) {
            abilityTracker.record(matchedInfo);
        }
        if (matchedInfo instanceof ExplicitFrontFacingBlockInfo explicitInfo) {
            buildState.explicitFrontFacings.put(pos, explicitInfo.getFrontFacing(controllerBase));
        }
        buildState.blocks.put(pos, state);
        if (matchedInfo.getTileEntity() instanceof IGregTechTileEntity igtteInfo) {
            TileEntity holder = world.getTileEntity(pos);
            if (holder instanceof IGregTechTileEntity igtte) {
                MetaTileEntity sampleMetaTileEntity = igtteInfo.getMetaTileEntity();
                if (sampleMetaTileEntity != null) {
                    MetaTileEntity metaTileEntity = igtte.setMetaTileEntity(
                            sampleMetaTileEntity, null, found.getTagCompound());
                    metaTileEntity.onPlacement(player);
                    buildState.blocks.put(pos, metaTileEntity);
                }
            }
        }
    }

    private static boolean hasPlaceableCandidate(@Nullable BlockInfo[] infos,
                                                 @Nullable AbilityPlacementTracker abilityTracker) {
        if (infos == null) {
            return false;
        }
        for (BlockInfo info : infos) {
            if (info == null || info.getBlockState() == null ||
                    info.getBlockState().getBlock() == Blocks.AIR) {
                continue;
            }
            if (abilityTracker == null || abilityTracker.canPlace(info)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFixedAisleLayout() {
        for (PieceTemplate.AisleDef aisle : template.getAisles()) {
            if (aisle.minRepeat() != aisle.maxRepeat()) return false;
        }
        return true;
    }

    public boolean checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                       @NotNull StructureOrientation orientation) {
        return checkPatternAtExact(world, centerPos, orientation, 0, 0, 0);
    }

    public boolean checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                       @NotNull StructureOrientation orientation,
                                       int xOffset, int yOffset, int zOffset) {
        return checkPatternAtExact(world, centerPos, orientation,
                xOffset, yOffset, zOffset, null);
    }

    public boolean checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                       @NotNull StructureOrientation orientation,
                                       int xOffset, int yOffset, int zOffset,
                                       @Nullable StructureMatchSession session) {
        return checkPatternAtExact(
                world,
                StructureCellTraversal.at(centerPos, orientation).withLocalOffset(xOffset, yOffset, zOffset),
                session);
    }

    public boolean checkPatternAtExact(@NotNull World world,
                                       @NotNull StructureCellTraversal traversal,
                                       @Nullable StructureMatchSession session) {
        boolean result = checkPatternAt(
                world,
                traversal.getCenterPos(),
                traversal.getOrientation(),
                traversal.getXOffset(),
                traversal.getYOffset(),
                traversal.getZOffset(),
                session);
        if (!result) clearCache();
        return result;
    }

    /**
     * Calculate repetitions per aisle from channel values.
     * Channel names assigned via {@code aisleChannelNames} are matched to specific aisles
     * (e.g. {@code STRUCTURE_WIDTH}, {@code STRUCTURE_HEIGHT}, {@code STRUCTURE_LENGTH}
     * each control their corresponding repeatable axis/aisle, in declaration order).
     * Value semantics: 0 = max, 1 = min, 2+ = specific (clamped to [min, max]).
     * If a channel is not set, defaults to max repetition for that aisle.
     *
     * @param channelValues map of channel name -> value (null = all max)
     * @return repetitions array
     */
    /**
     * Calculate aisle repetitions from channel values.
     * Uses aisleChannelNames to match channel names to specific aisles (consistent with repetitionDFS).
     * Aisles without an assigned channel value default to max repetition.
     */
    private int[] calculateRepetitionsFromChannels(Map<String, Integer> channelValues) {
        PieceTemplate.AisleDef[] aisles = template.getAisles();
        int[] repetitions = new int[aisles.length];

        for (int i = 0; i < aisles.length; i++) {
            repetitions[i] = aisles[i].maxRepeat();
        }

        if (channelValues == null || channelValues.isEmpty()) {
            return repetitions;
        }

        for (int i = 0; i < aisles.length; i++) {
            // Skip non-repeatable aisles
            if (aisles[i].minRepeat() == aisles[i].maxRepeat()) continue;

            String channelName = aisles[i].channelName();
            if (channelName != null && channelValues.containsKey(channelName)) {
                int value = channelValues.get(channelName);
                repetitions[i] = resolveRepetitionValue(
                        value, aisles[i].minRepeat(), aisles[i].maxRepeat());
            }
        }

        return repetitions;
    }

    public static int resolveRepetitionValue(int value, int min, int max) {
        if (value <= 0) return max;
        if (value == 1) return min;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Auto-build the structure in the world (default: max size, no channel preferences).
     */
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase) {
        autoBuild(player, controllerBase, null);
    }

    /**
     * Auto-build the structure in the world using channel-based configuration.
     *
     * <p>Channel values control two aspects:
     * <ul>
     *   <li><b>Structure dimensions</b>: {@code STRUCTURE_WIDTH}, {@code STRUCTURE_HEIGHT}
     *       and {@code STRUCTURE_LENGTH} (or any channel names bound via
     *       {@code aisleChannelNames}) control aisle repetition counts
     *       (0 = max, 1 = min, 2+ = specific).</li>
     *   <li><b>Casing tier selection</b>: other channels (e.g. {@code HEATING_COIL})
     *       control which tier of tiered casing is preferred during construction.</li>
     * </ul>
     *
     * @param player         the player performing the build
     * @param controllerBase the multiblock controller
     * @param channelValues  map of channel name -> desired value (null = max size, no tier preference)
     */
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase,
                          @Nullable Map<String, Integer> channelValues) {
        autoBuildAt(player, controllerBase, controllerBase.getPos(), channelValues);
    }

    /**
     * Auto-build the structure in the world at a specified center position.
     * Used by MultiPiecePattern to build individual pieces at their offset positions.
     *
     * @param player         the player performing the build
     * @param controllerBase the multiblock controller (used for facing/flip info)
     * @param centerPos      the center position for this build (controller pos or piece center)
     * @param channelValues  map of channel name -> desired value (null = max size, no tier preference)
     */
    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, @Nullable Map<String, Integer> channelValues) {
        autoBuildAt(player, controllerBase, centerPos, channelValues, null);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, @Nullable Map<String, Integer> channelValues,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        // Delegate to the offset-aware overload with zero cell offsets. Callers that need to
        // fold a piece-level offset (e.g. RepeatGroupPiece) into the cell loop use the
        // (xOffset, yOffset, zOffset) overload directly so the structureDir rotation is
        // applied exactly once per cell.
        autoBuildAt(player, controllerBase, centerPos, 0, 0, 0, channelValues, abilityTracker);
    }

    /**
     * Auto-build the structure in the world at a specified center position, with an
     * additional template-local cell offset folded into the per-cell transformation.
     * <p>
     * The offsets are in template-local coordinates and are added to every cell's (x, y, z)
     * before {@link RelativeDirection#setActualRelativeOffset} runs. The combined vector is
     * transformed exactly once, so the orientation of every placed cell is determined solely
     * by the controller's facing / upward-facing / flipped state and the template's
     * {@code structureDir}. Slice-level callers (e.g. {@code RepeatGroupPiece}) use this to
     * place all slices of a repeatable piece in the same orientation — only the per-cell
     * world position shifts.
     *
     * @param player         the player performing the build
     * @param controllerBase the multiblock controller (used for facing/flip info)
     * @param centerPos      the center position for this build (typically the controller pos)
     * @param xOffset        template-local x offset added to every cell before transformation
     * @param yOffset        template-local y offset added to every cell before transformation
     * @param zOffset        template-local z offset added to every cell before transformation
     * @param channelValues  map of channel name -> desired value (null = max size, no tier preference)
     */
    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, int xOffset, int yOffset, int zOffset,
                            @Nullable Map<String, Integer> channelValues) {
        autoBuildAt(player, controllerBase, centerPos, xOffset, yOffset, zOffset,
                channelValues, null);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, int xOffset, int yOffset, int zOffset,
                            @Nullable Map<String, Integer> channelValues,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        autoBuildAt(player, controllerBase, centerPos,
                StructureOrientation.fromController(controllerBase),
                xOffset, yOffset, zOffset, channelValues, abilityTracker);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, @NotNull StructureOrientation orientation,
                            int xOffset, int yOffset, int zOffset,
                            @Nullable Map<String, Integer> channelValues,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        autoBuildAt(player, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, abilityTracker,
                StructureEvaluationContext.Operation.CREATIVE_BUILD);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                             BlockPos centerPos, @NotNull StructureOrientation orientation,
                             int xOffset, int yOffset, int zOffset,
                             @Nullable Map<String, Integer> channelValues,
                             @Nullable AbilityPlacementTracker abilityTracker,
                             @NotNull StructureEvaluationContext.Operation operation) {
        autoBuildAtWithResult(player, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, abilityTracker,
                operation, ItemStack.EMPTY);
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      BlockPos centerPos,
                                                      @NotNull StructureOrientation orientation,
                                                      int xOffset, int yOffset, int zOffset,
                                                      Map<String, Integer> channelValues,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation) {
        return autoBuildAtWithResult(player, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, abilityTracker,
                operation, ItemStack.EMPTY);
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      BlockPos centerPos,
                                                      @NotNull StructureOrientation orientation,
                                                      int xOffset, int yOffset, int zOffset,
                                                      Map<String, Integer> channelValues,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation,
                                                      @NotNull ItemStack triggerStack) {
        return autoBuildAtWithResult(player, controllerBase,
                StructureCellTraversal.at(centerPos, orientation).withLocalOffset(xOffset, yOffset, zOffset),
                channelValues, abilityTracker, operation, triggerStack);
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      @NotNull StructureCellTraversal traversal,
                                                      Map<String, Integer> channelValues,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation) {
        return autoBuildAtWithResult(player, controllerBase, traversal, channelValues,
                abilityTracker, operation, ItemStack.EMPTY);
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      @NotNull StructureCellTraversal traversal,
                                                      Map<String, Integer> channelValues,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation,
                                                      @NotNull ItemStack triggerStack) {
        World world = player.world;
        BuildTraversalState buildState = new BuildTraversalState();
        buildState.result.recordAttemptedTraversal();
        buildState.blocks.put(controllerBase.getPos(), controllerBase);
        int[] repetitions = calculateRepetitionsFromChannels(channelValues);

        visitFixedStructureCells(repetitions, traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(),
                (cell, layerCounts) -> autoBuildCell(
                        cell, layerCounts, player, controllerBase, channelValues,
                        abilityTracker, triggerStack, buildState, operation));
        EnumFacing[] facings = ArrayUtils.addAll(new EnumFacing[] { controllerBase.getFrontFacing() },
                RelativeDirection.ALL_FACINGS);
        buildState.blocks.forEach((pos, block) -> {
            if (block instanceof MetaTileEntity) {
                // Do not reassign the controller's front facing — it was set by the
                // player and must remain stable across multi-slice auto-build calls.
                if (block == controllerBase) return;
                MetaTileEntity metaTileEntity = (MetaTileEntity) block;
                EnumFacing explicitFrontFacing = buildState.explicitFrontFacings.get(pos);
                if (explicitFrontFacing != null && metaTileEntity.isValidFrontFacing(explicitFrontFacing)) {
                    metaTileEntity.setFrontFacing(explicitFrontFacing);
                    return;
                }
                boolean find = false;
                for (EnumFacing enumFacing : facings) {
                    if (metaTileEntity.isValidFrontFacing(enumFacing)) {
                        if (!buildState.blocks.containsKey(pos.offset(enumFacing))) {
                            metaTileEntity.setFrontFacing(enumFacing);
                            find = true;
                            break;
                        }
                    }
                }
                if (!find) {
                    for (EnumFacing enumFacing : RelativeDirection.ALL_FACINGS) {
                        if (world.isAirBlock(pos.offset(enumFacing)) &&
                                metaTileEntity.isValidFrontFacing(enumFacing)) {
                            metaTileEntity.setFrontFacing(enumFacing);
                            break;
                        }
                    }
                }
            }
        });
        return buildState.result.build();
    }

    @SuppressWarnings("unchecked")
    public void spawnHintsAt(@NotNull World world,
                             @NotNull MultiblockControllerBase controllerBase,
                             @NotNull BlockPos centerPos,
                             @NotNull StructureOrientation orientation,
                             @Nullable Map<String, Integer> channelValues,
                             @NotNull ItemStack triggerStack) {
        spawnHintsAtWithResult(
                world, controllerBase, centerPos, orientation, channelValues, triggerStack);
    }

    @NotNull
    public StructureHintResult spawnHintsAtWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controllerBase,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            @NotNull ItemStack triggerStack) {
        return spawnHintsAtWithResult(
                world, controllerBase, centerPos, orientation,
                0, 0, 0, channelValues, triggerStack);
    }

    @SuppressWarnings("unchecked")
    public void spawnHintsAt(@NotNull World world,
                             @NotNull MultiblockControllerBase controllerBase,
                             @NotNull BlockPos centerPos,
                             @NotNull StructureOrientation orientation,
                             int xOffset, int yOffset, int zOffset,
                             @Nullable Map<String, Integer> channelValues,
                             @NotNull ItemStack triggerStack) {
        spawnHintsAtWithResult(
                world, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, triggerStack);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public StructureHintResult spawnHintsAtWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controllerBase,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset,
            @Nullable Map<String, Integer> channelValues,
            @NotNull ItemStack triggerStack) {
        return spawnHintsAtWithResult(
                world,
                controllerBase,
                StructureCellTraversal.at(centerPos, orientation).withLocalOffset(xOffset, yOffset, zOffset),
                channelValues,
                triggerStack);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public StructureHintResult spawnHintsAtWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controllerBase,
            @NotNull StructureCellTraversal traversal,
            @Nullable Map<String, Integer> channelValues,
            @NotNull ItemStack triggerStack) {
        HintTraversalState hintState = new HintTraversalState();
        hintState.result.recordAttemptedTraversal();
        int[] repetitions = calculateRepetitionsFromChannels(channelValues);
        visitFixedStructureCells(repetitions, traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(), (cell, layerCounts) -> {
            hintState.result.recordVisitedCell();
            updateOperationCellContext(hintState.evaluationContext, hintState.worldState,
                    world, cell.worldPos, controllerBase,
                    StructureEvaluationContext.Operation.HINT);
            IStructureElement<Object> typedElement = (IStructureElement<Object>) cell.element;
            StructureHintRenderResult triggerResult =
                    typedElement.spawnHintWithResult(hintState.evaluationContext, triggerStack);
            if (!triggerResult.skipped()) {
                hintState.result.recordTriggerHandledCell();
                hintState.result.recordRenderOutcome(triggerResult);
                return;
            }
            hintState.result.recordContextFallbackCell();
            hintState.result.recordRenderOutcome(
                    typedElement.spawnHintWithResult(hintState.evaluationContext));
        });
        return hintState.result.build();
    }

    @NotNull
    private static BlockInfo copyPreviewTileEntity(@NotNull BlockInfo info) {
        if (info.getTileEntity() instanceof MetaTileEntityHolder) {
            MetaTileEntityHolder holder = new MetaTileEntityHolder();
            holder.setMetaTileEntity(
                    ((MetaTileEntityHolder) info.getTileEntity()).getMetaTileEntity());
            holder.getMetaTileEntity().onPlacement();
            if (info instanceof ExplicitFrontFacingBlockInfo explicitInfo) {
                return new ExplicitFrontFacingBlockInfo(
                        holder.getMetaTileEntity().getBlock().getDefaultState(), holder,
                        controller -> explicitInfo.getFrontFacing(controller));
            }
            return new BlockInfo(holder.getMetaTileEntity().getBlock().getDefaultState(), holder);
        }
        return info;
    }

    @NotNull
    private static BlockInfo selectPreviewBlockInfo(@NotNull StructureElementPreview preview,
                                                    @NotNull Map<StructureElementPreview.CandidateGroup, Integer> cacheLayer,
                                                    @NotNull PreviewTraversalState previewState,
                                                    @Nullable Map<String, Integer> channelValues,
                                                    @Nullable AbilityPlacementTracker abilityTracker) {
        BlockInfo[] infos = null;
        StructureElementPreview.CandidateGroup matchedGroup = null;
        for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
            if (limit.getMinLayerCount() <= 0) {
                continue;
            }
            infos = getPreviewInfos(previewState, limit);
            if (!hasPreviewCandidate(limit, infos, channelValues, abilityTracker)) {
                continue;
            }
            int layer = cacheLayer.getOrDefault(limit, 0);
            if (layer >= limit.getMinLayerCount()) {
                continue;
            }
            cacheLayer.put(limit, layer + 1);
            int global = previewState.cacheGlobal.getOrDefault(limit, 0);
            if (global < limit.getPreviewCount()) {
                previewState.cacheGlobal.put(limit, global + 1);
            }
            matchedGroup = limit;
            break;
        }
        if (matchedGroup == null) {
            for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
                if (limit.getMinGlobalCount() == -1 && limit.getPreviewCount() == -1) continue;
                infos = getPreviewInfos(previewState, limit);
                if (!hasPreviewCandidate(limit, infos, channelValues, abilityTracker)) {
                    continue;
                }
                int global = previewState.cacheGlobal.getOrDefault(limit, 0);
                if (global < limit.getPreviewCount()) {
                    previewState.cacheGlobal.put(limit, global + 1);
                } else if (limit.getMinGlobalCount() > 0) {
                    if (global < limit.getMinGlobalCount()) {
                        previewState.cacheGlobal.put(limit, global + 1);
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                matchedGroup = limit;
                break;
            }
        }
        if (matchedGroup == null) {
            for (StructureElementPreview.CandidateGroup common : preview.getCommon()) {
                if (common.getPreviewCount() <= 0) {
                    continue;
                }
                infos = getPreviewInfos(previewState, common);
                if (!hasPreviewCandidate(common, infos, channelValues, abilityTracker)) {
                    continue;
                }
                int global = previewState.cacheGlobal.getOrDefault(common, 0);
                if (global >= common.getPreviewCount()) {
                    continue;
                }
                previewState.cacheGlobal.put(common, global + 1);
                matchedGroup = common;
                break;
            }
        }
        if (matchedGroup == null) {
            for (StructureElementPreview.CandidateGroup common : preview.getCommon()) {
                if (common.getPreviewCount() == -1) {
                    infos = getPreviewInfos(previewState, common);
                    if (!hasPreviewCandidate(common, infos, channelValues, abilityTracker)) {
                        continue;
                    }
                    matchedGroup = common;
                    break;
                }
            }
        }
        if (matchedGroup == null) {
            for (StructureElementPreview.CandidateGroup limit : preview.getLimited()) {
                if (limit.getPreviewCount() != -1) {
                    continue;
                }
                infos = getPreviewInfos(previewState, limit);
                if (!hasPreviewCandidate(limit, infos, channelValues, abilityTracker)) {
                    continue;
                }
                if (limit.getMaxGlobalCount() != -1 || limit.getMaxLayerCount() != -1) {
                    int global = previewState.cacheGlobal.getOrDefault(limit, 0);
                    if (limit.getMaxGlobalCount() != -1 && global < limit.getMaxGlobalCount()) {
                        previewState.cacheGlobal.put(limit, global + 1);
                    } else {
                        int layer = cacheLayer.getOrDefault(limit, 0);
                        if (limit.getMaxLayerCount() != -1 && layer < limit.getMaxLayerCount()) {
                            cacheLayer.put(limit, layer + 1);
                        } else {
                            continue;
                        }
                    }
                }

                matchedGroup = limit;
                break;
            }
        }
        if (matchedGroup == null) {
            return BlockInfo.EMPTY;
        }
        int candidateIdx = StructurePlacementDecision.getPlaceableCandidateIndex(
                matchedGroup, infos, channelValues, abilityTracker);
        BlockInfo info = candidateIdx < 0 || infos == null || infos.length == 0
                ? BlockInfo.EMPTY
                : infos[candidateIdx];
        if (StructureOperationRequest.isNoHatch(channelValues)
                && info.getTileEntity() instanceof IGregTechTileEntity) {
            BlockInfo fallback = findNonHatchPreviewCandidate(preview, previewState, abilityTracker);
            if (fallback != null) {
                info = fallback;
            }
        }
        if (abilityTracker != null) {
            abilityTracker.record(info);
        }
        return copyPreviewTileEntity(info);
    }

    @Nullable
    private static BlockInfo findNonHatchPreviewCandidate(
            @NotNull StructureElementPreview preview,
            @NotNull PreviewTraversalState previewState,
            @Nullable AbilityPlacementTracker abilityTracker) {
        for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
            BlockInfo fallback = firstNonHatchCandidate(getPreviewInfos(previewState, group), abilityTracker);
            if (fallback != null) return fallback;
        }
        for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
            BlockInfo fallback = firstNonHatchCandidate(getPreviewInfos(previewState, group), abilityTracker);
            if (fallback != null) return fallback;
        }
        return null;
    }

    @Nullable
    private static BlockInfo firstNonHatchCandidate(@Nullable BlockInfo[] infos,
                                                    @Nullable AbilityPlacementTracker abilityTracker) {
        for (BlockInfo info : StructurePlacementDecision.filterPlaceable(infos, abilityTracker)) {
            if (!(info.getTileEntity() instanceof IGregTechTileEntity)) {
                return info;
            }
        }
        return null;
    }

    @NotNull
    private static BlockInfo[] getPreviewInfos(@NotNull PreviewTraversalState previewState,
                                               @NotNull StructureElementPreview.CandidateGroup group) {
        if (!previewState.cacheInfos.containsKey(group)) {
            previewState.cacheInfos.put(group, group.getCandidates());
        }
        return previewState.cacheInfos.get(group);
    }

    private static boolean hasPreviewCandidate(@Nullable StructureElementPreview.CandidateGroup group,
                                               @Nullable BlockInfo[] infos,
                                               @Nullable Map<String, Integer> channelValues,
                                               @Nullable AbilityPlacementTracker abilityTracker) {
        return StructurePlacementDecision.getPlaceableCandidateIndex(
                group, infos, channelValues, abilityTracker) >= 0;
    }

    /**
     * Get all structure blocks (from cache or by calculating positions).
     */
    public Map<BlockPos, BlockInfo> getAllStructureBlocks(World world, BlockPos centerPos,
                                                          @NotNull StructureOrientation orientation) {
        Map<BlockPos, BlockInfo> blocks = new HashMap<>();

        if (!cache.isEmpty()) {
            cache.forEach((posLong, blockInfo) -> {
                BlockPos pos = BlockPos.fromLong(posLong);
                if (pos.equals(centerPos)) return;
                if (world != null && !world.isBlockLoaded(pos)) {
                    return;
                }
                if (blockInfo.getBlockState().getBlock() != Blocks.AIR) {
                    blocks.put(pos, blockInfo);
                }
            });
            return blocks;
        }

        PieceTemplate.AisleDef[] aisles = template.getAisles();
        int[] repetitions = new int[aisles.length];
        for (int c = 0; c < repetitions.length; c++) {
            repetitions[c] = (formedRepetitionCount != null && c < formedRepetitionCount.length)
                    ? formedRepetitionCount[c]
                    : aisles[c].minRepeat();
        }

        visitFixedStructureCells(repetitions, centerPos, orientation, 0, 0, 0, (cell, layerCounts) -> {
            BlockPos pos = cell.worldPos;
            if (pos.equals(centerPos)) {
                return;
            }

            if (world != null && world.isBlockLoaded(pos)) {
                TileEntity tileEntity = world.getTileEntity(pos);
                IBlockState blockState = world.getBlockState(pos);
                if (blockState.getBlock() != Blocks.AIR) {
                    blocks.put(pos, new BlockInfo(blockState, tileEntity));
                }
            }
        });
        return blocks;
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues) {
        return createPreviewCells(repetition, channelValues, DEFAULT_PREVIEW_ORIENTATION);
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues,
                                           @Nullable AbilityPlacementTracker abilityTracker) {
        return createPreviewCells(repetition, channelValues, DEFAULT_PREVIEW_ORIENTATION, abilityTracker);
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues,
                                           @NotNull StructureOrientation previewOrientation) {
        return createPreviewCells(repetition, channelValues, previewOrientation, null);
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues,
                                           @NotNull StructureOrientation previewOrientation,
                                           @Nullable AbilityPlacementTracker abilityTracker) {
        PreviewTraversalState previewState = new PreviewTraversalState();
        visitFixedStructureCells(repetition, BlockPos.ORIGIN, previewOrientation, 0, 0, 0, (cell, layerCounts) -> {
            StructureElementPreview preview = cell.element.getPreview();
            BlockInfo info = selectPreviewBlockInfo(
                    preview, layerCounts, previewState, channelValues, abilityTracker);
            previewState.record(cell.worldPos, info,
                    StructureElementPreviewEntry.of(preview, previewTooltip(cell.element)));
        });
        orientPreviewControllers(previewState.blocks);
        return previewState.toCells(calculatePreviewCenter(repetition, previewOrientation));
    }

    @NotNull
    private static List<String> previewTooltip(@NotNull IStructureElement<?> element) {
        List<String> tooltip = new ArrayList<>();
        element.addPreviewTooltip(tooltip);
        return tooltip;
    }

    private static void orientPreviewControllers(@NotNull Map<BlockPos, BlockInfo> blocks) {
        blocks.forEach((pos, info) -> {
            if (info.getTileEntity() instanceof MetaTileEntityHolder) {
                MetaTileEntity metaTileEntity = ((MetaTileEntityHolder) info.getTileEntity()).getMetaTileEntity();
                // Try to find a boundary direction (no block in that direction).
                // Boundary directions point outward from the structure, so this is the
                // preferred front-facing for the previewed controller. If the controller
                // is fully enclosed (e.g. sits in the middle of a chamber), we keep its
                // default front-facing: a previous second pass scanned for the first
                // AIR neighbour and used it as the front-facing, but that direction is
                // usually *inward* (toward a chamber wall) rather than outward, which
                // made the projector / JEI preview show the controller facing the
                // inside of the multiblock. The actual structure check at #checkPatternAt
                // uses the real controller's front-facing as a hint, but the preview
                // does not have that information here, so the safe choice is to leave
                // the default in place.
                for (EnumFacing enumFacing : RelativeDirection.ALL_FACINGS) {
                    if (metaTileEntity.isValidFrontFacing(enumFacing) &&
                            !isOccupied(blocks.get(pos.offset(enumFacing)))) {
                        metaTileEntity.setFrontFacing(enumFacing);
                        break;
                    }
                }
            }
        });
    }

    private static boolean isOccupied(@Nullable BlockInfo info) {
        return info != null
                && info != BlockInfo.EMPTY
                && info.getBlockState() != null
                && info.getBlockState().getBlock() != Blocks.AIR;
    }

    @NotNull
    private BlockPos calculatePreviewCenter(@NotNull int[] repetition,
                                            @NotNull StructureOrientation orientation) {
        PieceTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int finger = -centerOffset.maxZ();
        for (int i = 0; i < centerOffset.z() && i < repetition.length; i++) {
            finger += repetition[i];
        }
        return RelativeDirection.setActualRelativeOffset(
                0, 0, finger,
                orientation.getStructureFront(), orientation.getUp(),
                orientation.isFlipped(), template.getStructureDir());
    }

    public boolean checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                              BlockPos centerPos,
                                              @NotNull StructureOrientation orientation) {
        return checkPatternFastAtSnapshot(blockAccess, centerPos, orientation, 0, 0, 0);
    }

    public boolean checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                              BlockPos centerPos,
                                              @NotNull StructureOrientation orientation,
                                              int xOffset, int yOffset, int zOffset) {
        // For snapshot checks, we skip the cache fast-path and do a full pattern check
        boolean matched = checkPatternAtSnapshot(blockAccess, centerPos,
                orientation.withFlipped(false), xOffset, yOffset, zOffset, null);
        if (orientation.allowsFlip()) {
            if (matched) {
                return true;
            }
            Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities = missingAbilities;
            PatternError unflippedError = worldState.error;
            matched = checkPatternAtSnapshot(blockAccess, centerPos,
                    orientation.withFlipped(true), xOffset, yOffset, zOffset, null);
            if (!matched && shouldKeepUnflippedFailure(unflippedError, unflippedMissingAbilities,
                    worldState.error, missingAbilities)) {
                missingAbilities = unflippedMissingAbilities;
                worldState.setError(unflippedError);
            }
        }
        return matched;
    }

    public boolean checkOnSnapshotWithPrior(@NotNull net.minecraft.world.IBlockAccess snap,
                                            @NotNull BlockPos centerPos,
                                            @NotNull StructureOrientation orientation,
                                            @Nullable FormedStructureMetadata prior) {
        if (prior == null) {
            // No prior: delegate to standard snapshot check
            return checkPatternFastAtSnapshot(snap, centerPos, orientation);
        }

        // Fast path: verify cached positions against snapshot
        // If the cache is non-empty and all positions still match, we're done in O(cache_size)
        if (!cache.isEmpty()) {
            if (verifyCacheAgainstSnapshot(snap)) {
                return true;
            }
        }

        // Cache miss or verification failed: full search
        return checkPatternFastAtSnapshot(snap, centerPos, orientation);
    }

    /**
     * Verify that all cached positions still match against the snapshot.
     * This provides O(cache_size) verification for formed structures.
     * For small caches (&lt; 1000), check all positions; for large caches, sample 10%.
     *
     * @param snap the snapshot to verify against
     * @return true if all sampled cached positions still match
     */
    private boolean verifyCacheAgainstSnapshot(@NotNull net.minecraft.world.IBlockAccess snap) {
        int size = cache.size();
        int checksNeeded = size < 1000 ? size : Math.max(100, size / 10);

        int checked = 0;
        for (Long2ObjectMap.Entry<BlockInfo> entry : cache.long2ObjectEntrySet()) {
            if (checked >= checksNeeded) break;

            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            IBlockState snapshotState = snap.getBlockState(pos);
            IBlockState cachedState = entry.getValue().getBlockState();

            if (snapshotState != cachedState) {
                return false;
            }
            checked++;
        }
        return true;
    }

    public boolean checkAxisBoundaryFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                                   int axis,
                                                   @NotNull StructureCellTraversal traversal) {
        return checkAxisBoundaryFastAtSnapshotCore(snap, axis, traversal);
    }

    private boolean checkAxisBoundaryFastAtSnapshotCore(@NotNull net.minecraft.world.IBlockAccess snap,
                                                        int axis,
                                                        @NotNull StructureCellTraversal traversal) {
        int boundaryIndex = axisBoundaryIndex(axis);
        if (boundaryIndex < 0) {
            return false;
        }

        StructureMatchSession session = new StructureMatchSession();
        this.missingAbilities = Collections.emptyMap();

        boolean matched = visitFixedStructureCellsWhere(
                traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(),
                (x, y, z) -> coordinateForAxis(axis, x, y, z) == boundaryIndex,
                cell -> {
                    worldState.updateFromBlockAccess(snap, cell.worldPos, session, cell.previewEntry);
                    return checkElement(cell.element, session, StructureEvaluationContext.Operation.MATCH_SNAPSHOT);
                });
        if (!matched) {
            return false;
        }

        // This method is a branch-pruning probe. Do not commit contexts, counts,
        // or ability requirements; the selected repeat vector is fully verified
        // before it is accepted.
        return true;
    }

    private int axisBoundaryIndex(int axis) {
        switch (axis) {
            case 0:
                return template.getXLength() - 1;
            case 1:
                return template.getYLength() - 1;
            case 2:
                return template.getZLength() - 1;
            default:
                return -1;
        }
    }

    private static int coordinateForAxis(int axis, int x, int y, int z) {
        switch (axis) {
            case 0:
                return x;
            case 1:
                return y;
            case 2:
                return z;
            default:
                return -1;
        }
    }

    /**
     * Internal pattern check against a snapshot.
     * Simplified version that uses IBlockAccess instead of World.
     */
    private boolean checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                           BlockPos centerPos,
                                           @NotNull StructureOrientation orientation,
                                           int xOffset, int yOffset, int zOffset,
                                           @Nullable StructureMatchSession session) {
        return checkFixedStructureCells(null, blockAccess, centerPos, orientation,
                xOffset, yOffset, zOffset, session,
                StructureEvaluationContext.Operation.MATCH_SNAPSHOT, false);
    }

    public boolean checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset) {
        return checkPatternAtSnapshotExact(blockAccess, centerPos, orientation,
                xOffset, yOffset, zOffset, null);
    }

    public boolean checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset,
            @Nullable StructureMatchSession session) {
        return checkPatternAtSnapshotExact(
                blockAccess,
                StructureCellTraversal.at(centerPos, orientation).withLocalOffset(xOffset, yOffset, zOffset),
                session);
    }

    public boolean checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull StructureCellTraversal traversal,
            @Nullable StructureMatchSession session) {
        return checkPatternAtSnapshot(blockAccess, traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(), session);
    }

    private static final class CachedMetaTileEntityInfo {

        @NotNull
        private final MetaTileEntity instance;
        @NotNull
        private final ResourceLocation id;

        private CachedMetaTileEntityInfo(@NotNull MetaTileEntity instance) {
            this.instance = instance;
            this.id = instance.metaTileEntityId;
        }

        private boolean matches(@NotNull MetaTileEntity current) {
            return current == instance && id.equals(current.metaTileEntityId);
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}

