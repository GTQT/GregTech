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
import gregtech.api.util.RelativeDirection;
import gregtech.common.ConfigHolder;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
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
 * Per-instance mutable state for multiblock pattern checking.
 * Each multiblock controller holds its own MultiblockState, while sharing
 * the immutable canonical {@link PieceTemplate} IR with other controllers of
 * the same type. (The legacy {@link BlockPatternTemplate} facade is no longer
 * present at this layer — it lives only in the compile-result surface of
 * {@link StructurePiece#getTemplate()} for back-compat.)
 *
 * This class holds:
 * - The block position cache (formed structure positions)
 * - Pattern match context (runtime matching results)
 * - Global/layer predicate counters
 * - The BlockWorldState used during pattern checking
 * - Formed repetition counts
 * - A ReentrantLock for future async checking support (P2)
 *
 * @see PieceTemplate for the canonical IR
 */
public class MultiblockState {

    private static final StructureOrientation DEFAULT_PREVIEW_ORIENTATION = StructureOrientation.of(
            EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.UP, false, false);

    /**
     * The canonical piece IR. The legacy
     * {@link #getTemplate()} accessor returns a {@link BlockPatternTemplate}
     * facade constructed lazily on first call.
     */
    private final PieceTemplate template;

    // --- Per-instance mutable state ---

    protected final BlockWorldState worldState = new BlockWorldState();
    protected final StructureEvaluationContext<Object> evaluationContext = new StructureEvaluationContext<>();
    protected final PatternMatchContext matchContext = new PatternMatchContext();
    protected final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount = new HashMap<>();
    protected final Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount = new HashMap<>();
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

    public MultiblockState(@NotNull PieceTemplate template) {
        this.template = template;
        this.formedRepetitionCount = new int[template.getAisles().length];
    }

    /**
     * @return the canonical piece IR this state is bound to
     */
    @NotNull
    public PieceTemplate getPieceTemplate() {
        return template;
    }

    /**
     * @return the legacy facade view of the piece IR. Lazily constructed on
     *         first call; new code should prefer {@link #getPieceTemplate()}.
     */
    @NotNull
    public BlockPatternTemplate getTemplate() {
        return templateView != null ? templateView : (templateView = new BlockPatternTemplate(template));
    }

    @Nullable
    private BlockPatternTemplate templateView;

    /**
     * @return the current pattern error, or null if no error
     */
    public PatternError getError() {
        return worldState.error;
    }

    /**
     * Returns ability deficits found after a complete legacy pattern scan.
     */
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
        formedRepetitionCount = checkpoint.formedRepetitionCount.clone();
        matchContext.replaceWith(checkpoint.matchContext);
        globalCount.clear();
        globalCount.putAll(checkpoint.globalCount);
        layerCount.clear();
        layerCount.putAll(checkpoint.layerCount);
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
        private final PatternMatchContext matchContext;
        @NotNull
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
        @NotNull
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount;
        @NotNull
        private final Map<MultiblockAbility<?>, Integer> missingAbilities;

        private Checkpoint(@NotNull MultiblockState state) {
            this.cache = new Long2ObjectOpenHashMap<>(state.cache);
            this.cachedXOffset = state.cachedXOffset;
            this.cachedYOffset = state.cachedYOffset;
            this.cachedZOffset = state.cachedZOffset;
            this.cacheOffsetsRecorded = state.cacheOffsetsRecorded;
            this.formedRepetitionCount = state.formedRepetitionCount.clone();
            this.matchContext = state.matchContext.copy();
            this.globalCount = new HashMap<>(state.globalCount);
            this.layerCount = new HashMap<>(state.layerCount);
            this.missingAbilities = state.missingAbilities;
        }
    }

    /**
     * Returns the current match context for this state. The context is refreshed
     * whenever a full pattern check succeeds.
     */
    @NotNull
    public PatternMatchContext getMatchContext() {
        return matchContext;
    }

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos,
                                                  @NotNull StructureOrientation orientation) {
        return checkPatternFastAt(world, centerPos, orientation, true, 0, 0, 0);
    }

    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos,
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
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos,
                                                  @NotNull StructureOrientation orientation,
                                                  boolean doRandomCheck,
                                                  int xOffset, int yOffset, int zOffset) {
        // Cache fast-path is only valid when the offsets used to build the cache match
        // the current call's offsets. A non-empty cache with mismatched offsets belongs
        // to a different slice and must not be trusted — fall through to a full re-check.
        boolean cacheValid = cache.isEmpty() == false
                && cacheOffsetsRecorded
                && xOffset == cachedXOffset
                && yOffset == cachedYOffset
                && zOffset == cachedZOffset;
        if (cacheValid) {
            if (!doRandomCheck || cache.size() < 512) {
                // Small cache: full check
                boolean pass = true;
                for (Map.Entry<Long, BlockInfo> entry : cache.entrySet()) {
                    BlockPos pos = BlockPos.fromLong(entry.getKey());
                    IBlockState blockState = world.getBlockState(pos);
                    if (blockState != entry.getValue().getBlockState()) {
                        pass = false;
                        break;
                    }
                    TileEntity cachedTileEntity = entry.getValue().getTileEntity();
                    if (cachedTileEntity != null) {
                        TileEntity tileEntity = world.getTileEntity(pos);
                        if (tileEntity != cachedTileEntity) {
                            pass = false;
                            break;
                        }
                    }
                }
                if (pass) return worldState.hasError() ? null : matchContext;
            } else {
                // Large cache: random sampling
                int cacheSize = cache.size();
                int sampleCount = (int) Math.ceil(cacheSize * ConfigHolder.machines.delayStructureCheckSample);
                boolean pass = true;

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

                    BlockPos pos = BlockPos.fromLong(entry.getKey());
                    IBlockState blockState = world.getBlockState(pos);
                    if (blockState != entry.getValue().getBlockState()) {
                        pass = false;
                        break;
                    }
                    TileEntity cachedTileEntity = entry.getValue().getTileEntity();
                    if (cachedTileEntity != null) {
                        TileEntity tileEntity = world.getTileEntity(pos);
                        if (tileEntity != cachedTileEntity) {
                            pass = false;
                            break;
                        }
                    }

                    if (iterator.hasNext()) {
                        for (int i = 0; i < step - skip - 1 && iterator.hasNext(); i++) {
                            iterator.next();
                        }
                    }
                }

                if (pass) return worldState.hasError() ? null : matchContext;
            }
        }

        PatternMatchContext pmc = checkPatternAt(world, centerPos, orientation.withFlipped(false),
                xOffset, yOffset, zOffset, null);
        if (orientation.allowsFlip()) {
            if (pmc != null) {
                return pmc;
            }
            Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities = missingAbilities;
            PatternError unflippedError = worldState.error;
            pmc = checkPatternAt(world, centerPos, orientation.withFlipped(true),
                    xOffset, yOffset, zOffset, null);
            if (pmc == null && shouldKeepUnflippedFailure(unflippedError, unflippedMissingAbilities,
                    worldState.error, missingAbilities)) {
                missingAbilities = unflippedMissingAbilities;
                worldState.setError(unflippedError);
            }
        }
        if (pmc == null) clearCache();
        return pmc;
    }

    private PatternMatchContext checkPatternAt(World world, BlockPos centerPos,
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
        return new StructureFailureTrace.Builder("legacy", BlockPos.ORIGIN)
                .orientation(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, flipped)
                .path("legacy-template")
                .operation("CHECK")
                .result("failed")
                .kind(classifyLegacyFailure(error, missingAbilities))
                .progressDepth(error == null ? 0 : 1)
                .missingAbilities(missingAbilities)
                .error(error)
                .build();
    }

    @NotNull
    private static StructureFailureTrace.Kind classifyLegacyFailure(
            @Nullable PatternError error,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (!missingAbilities.isEmpty()) {
            return StructureFailureTrace.Kind.MISSING_ABILITY;
        }
        if (error instanceof TraceabilityPredicate.SinglePredicateError) {
            TraceabilityPredicate.SinglePredicateError single =
                    (TraceabilityPredicate.SinglePredicateError) error;
            if (single.type == 0 || single.type == 2) {
                return StructureFailureTrace.Kind.COUNT_LIMIT;
            }
        }
        return error == null ? StructureFailureTrace.Kind.LEGACY_PATTERN : StructureFailureTrace.Kind.BLOCK_MISMATCH;
    }

    private void recordMissingFixedAbility(@NotNull TraceabilityPredicate predicate) {
        if (!hasFixedAisleLayout()) return;
        MultiblockAbility<?> ability = predicate.getSingleAbility();
        if (ability == null) return;

        Map<MultiblockAbility<?>, Integer> abilities = new HashMap<>(missingAbilities);
        abilities.putIfAbsent(ability, 1);
        missingAbilities = Collections.unmodifiableMap(abilities);
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
                   @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> layerCounts);
    }

    @FunctionalInterface
    private interface FixedStructureCellPredicate {

        boolean visit(@NotNull FixedStructureCell cell,
                      @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> layerCounts);
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
        private final TraceabilityPredicate predicate;

        private FixedStructureCell(int aisleIndex, int repetitionIndex,
                                   int localX, int localY, int localZ,
                                   @NotNull BlockPos worldPos,
                                   @NotNull IStructureElement<?> element,
                                   @NotNull TraceabilityPredicate predicate) {
            this.aisleIndex = aisleIndex;
            this.repetitionIndex = repetitionIndex;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.worldPos = worldPos;
            this.element = element;
            this.predicate = predicate;
        }
    }

    private void visitFixedStructureCells(@NotNull int[] repetitions,
                                          @NotNull BlockPos centerPos,
                                          @NotNull StructureOrientation orientation,
                                          int xOffset, int yOffset, int zOffset,
                                          @NotNull FixedStructureCellVisitor visitor) {
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();

        int z = -centerOffset.maxZ();
        for (int c = 0; c < fingerLength; c++) {
            int repetitionCount = c < repetitions.length ? repetitions[c] : 0;
            for (int r = 0; r < repetitionCount; r++) {
                Map<TraceabilityPredicate.SimplePredicate, Integer> layerCounts = new HashMap<>();
                visitFixedStructureSlice(c, r, z, centerPos, orientation, xOffset, yOffset, zOffset,
                        layerCounts, (cell, counts) -> {
                            visitor.visit(cell, counts);
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
                                             @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> layerCounts,
                                             @NotNull FixedStructureCellPredicate visitor) {
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
            for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                if (!visitor.visit(createFixedStructureCell(
                        aisleIndex, repetitionIndex, a, b, x, y, localZ,
                        centerPos, orientation, xOffset, yOffset, zOffset), layerCounts)) {
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
        TraceabilityPredicate predicate = element.toPredicate();
        BlockPos pos = RelativeDirection.setActualRelativeOffset(
                localX + xOffset, localY + yOffset, localZ + zOffset,
                orientation.getStructureFront(), orientation.getUp(),
                orientation.isFlipped(), template.getStructureDir())
                .add(centerPos);
        return new FixedStructureCell(
                aisleIndex, repetitionIndex, localX, localY, localZ, pos, element, predicate);
    }

    @Nullable
    private PatternMatchContext checkFixedStructureCells(@Nullable World world,
                                                         @Nullable net.minecraft.world.IBlockAccess blockAccess,
                                                         @NotNull BlockPos centerPos,
                                                         @NotNull StructureOrientation orientation,
                                                         int xOffset, int yOffset, int zOffset,
                                                         @Nullable StructureMatchSession session,
                                                         @NotNull StructureEvaluationContext.Operation operation,
                                                         boolean updateCache) {
        int[][] aisleRepetitions = template.getAisleRepetitions();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();

        boolean findFirstAisle = false;
        int minZ = -centerOffset.maxZ();

        PatternMatchContext activeContext = session == null ? this.matchContext : session.getContext();
        Map<TraceabilityPredicate.SimplePredicate, Integer> activeGlobalCount =
                session == null ? this.globalCount : session.getGlobalCount();
        StructureMatchSession.Checkpoint initialCheckpoint = session == null ? null : session.checkpoint();
        if (session == null) {
            activeContext.reset();
            activeGlobalCount.clear();
        }
        this.layerCount.clear();
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
                this.layerCount.clear();

                if (!visitFixedStructureSlice(c, r, z, centerPos, orientation, xOffset, yOffset, zOffset,
                        this.layerCount, (cell, counts) -> checkFixedStructureCell(
                                cell, world, blockAccess, activeContext, activeGlobalCount,
                                counts, session, operation, updateCache))) {
                    if (findFirstAisle) {
                        if (r < aisleRepetitions[c][0]) {
                            r = c = 0;
                            z = minZ++;
                            if (session == null) {
                                activeContext.reset();
                                activeGlobalCount.clear();
                            } else {
                                session.restoreTo(initialCheckpoint);
                            }
                            findFirstAisle = false;
                        }
                    } else {
                        z++;
                    }
                    continue loop;
                }
                findFirstAisle = true;
                z++;

                if (!validateFixedStructureLayerCounts()) {
                    return null;
                }
                validRepetitions++;
            }
            if (validRepetitions < aisleRepetitions[c][0]) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return null;
            }

            formedRepetitionCount[c] = validRepetitions;
        }

        if (session == null && failForMissingGlobalPredicates(activeGlobalCount)) return null;

        worldState.setError(null);
        activeContext.setNeededFlip(orientation.isFlipped());
        return activeContext;
    }

    private boolean checkFixedStructureCell(@NotNull FixedStructureCell cell,
                                            @Nullable World world,
                                            @Nullable net.minecraft.world.IBlockAccess blockAccess,
                                            @NotNull PatternMatchContext activeContext,
                                            @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> activeGlobalCount,
                                            @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> layerCounts,
                                            @Nullable StructureMatchSession session,
                                            @NotNull StructureEvaluationContext.Operation operation,
                                            boolean updateCache) {
        if (operation.readsSnapshot()) {
            if (blockAccess == null) {
                throw new IllegalArgumentException("Snapshot fixed-structure check requires an IBlockAccess");
            }
            worldState.updateFromBlockAccess(blockAccess, cell.worldPos, activeContext, activeGlobalCount,
                    layerCounts, cell.predicate);
        } else {
            if (world == null) {
                throw new IllegalArgumentException("Live fixed-structure check requires a World");
            }
            worldState.update(world, cell.worldPos, activeContext, activeGlobalCount, layerCounts, cell.predicate);
            if (updateCache) {
                recordLiveCacheCell(cell);
            }
        }
        if (!checkElement(cell.element, session, operation)) {
            recordMissingFixedAbility(cell.predicate);
            return false;
        }
        return true;
    }

    private void recordLiveCacheCell(@NotNull FixedStructureCell cell) {
        if (cell.predicate == TraceabilityPredicate.ANY) return;

        TileEntity tileEntity = worldState.getTileEntity();
        if (tileEntity instanceof IGregTechTileEntity && !((IGregTechTileEntity) tileEntity).isValid()) {
            cache.put(cell.worldPos.toLong(), new BlockInfo(worldState.getBlockState(), null, cell.predicate));
            return;
        }
        cache.put(cell.worldPos.toLong(), new BlockInfo(worldState.getBlockState(), tileEntity, cell.predicate));
    }

    private boolean validateFixedStructureLayerCounts() {
        for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : layerCount.entrySet()) {
            if (entry.getValue() < entry.getKey().minLayerCount) {
                worldState.setError(new TraceabilityPredicate.SinglePredicateError(entry.getKey(), 3));
                return false;
            }
        }
        return true;
    }

    private void updateOperationCellContext(@NotNull StructureEvaluationContext<Object> evaluationContext,
                                            @NotNull BlockWorldState operationWorldState,
                                            @NotNull World world,
                                            @NotNull BlockPos pos,
                                            @NotNull TraceabilityPredicate predicate,
                                            @NotNull MultiblockControllerBase controllerBase,
                                            @NotNull StructureEvaluationContext.Operation operation) {
        operationWorldState.update(world, pos, matchContext, globalCount, layerCount, predicate);
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

    private static final class BuildTraversalState {

        @NotNull
        private final BlockWorldState worldState = new BlockWorldState();
        @NotNull
        private final StructureEvaluationContext<Object> evaluationContext = new StructureEvaluationContext<>();
        @NotNull
        private final Map<TraceabilityPredicate.SimplePredicate, BlockInfo[]> cacheInfos = new HashMap<>();
        @NotNull
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> cacheGlobal = new HashMap<>();
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
        private final Map<TraceabilityPredicate.SimplePredicate, BlockInfo[]> cacheInfos = new HashMap<>();
        @NotNull
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> cacheGlobal = new HashMap<>();
        @NotNull
        private final Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        @NotNull
        private final Map<BlockPos, TraceabilityPredicate> predicates = new HashMap<>();
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void record(@NotNull BlockPos pos, @NotNull BlockInfo info,
                            @NotNull TraceabilityPredicate predicate) {
            blocks.put(pos, info);
            if (predicate != TraceabilityPredicate.ANY) {
                predicates.put(pos, predicate);
            }
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
            return new PreviewCells(blocks, predicates, center, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public static final class PreviewCells {

        @NotNull
        private final Map<BlockPos, BlockInfo> blocks;
        @NotNull
        private final Map<BlockPos, TraceabilityPredicate> predicates;
        @NotNull
        private final BlockPos center;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private PreviewCells(@NotNull Map<BlockPos, BlockInfo> blocks,
                             @NotNull Map<BlockPos, TraceabilityPredicate> predicates,
                             @NotNull BlockPos center,
                             int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ) {
            this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks));
            this.predicates = Collections.unmodifiableMap(new HashMap<>(predicates));
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
        public Map<BlockPos, TraceabilityPredicate> getPredicates() {
            return predicates;
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
    }

    private void autoBuildCell(@NotNull FixedStructureCell cell,
                                @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> cacheLayer,
                                @NotNull EntityPlayer player,
                                @NotNull MultiblockControllerBase controllerBase,
                                @Nullable Map<String, Integer> channelValues,
                                boolean skipHatches,
                                @Nullable AbilityPlacementTracker abilityTracker,
                                @NotNull ItemStack triggerStack,
                                @NotNull BuildTraversalState buildState,
                                @NotNull StructureEvaluationContext.Operation operation) {
        World world = player.world;
        TraceabilityPredicate predicate = cell.predicate;
        BlockPos pos = cell.worldPos;
        buildState.result.recordVisitedCell();

        updateOperationCellContext(buildState.evaluationContext, buildState.worldState,
                world, pos, predicate, controllerBase, operation);
        if (buildState.evaluationContext.probe(evaluation -> elementMatches(cell.element, evaluation))) {
            buildState.result.recordExistingCell();
            buildState.blocks.put(pos, world.getBlockState(pos));
            if (abilityTracker != null) {
                abilityTracker.recordWorldTile(pos, world.getTileEntity(pos));
            }
            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                limit.testLimited(buildState.worldState);
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
        TraceabilityPredicate.SimplePredicate matchedPredicate = null;
        for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
            if (limit.minLayerCount > 0) {
                if (!cacheLayer.containsKey(limit)) {
                    cacheLayer.put(limit, 1);
                } else if (cacheLayer.get(limit) < limit.minLayerCount &&
                        (limit.maxLayerCount == -1 ||
                                cacheLayer.get(limit) < limit.maxLayerCount)) {
                    cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                } else {
                    continue;
                }
            } else {
                continue;
            }
            if (!buildState.cacheInfos.containsKey(limit)) {
                buildState.cacheInfos.put(limit,
                        limit.candidates == null ? null : limit.candidates.get());
            }
            infos = buildState.cacheInfos.get(limit);
            matchedPredicate = limit;
            find = true;
            break;
        }
        if (!find) {
            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                if (limit.minGlobalCount > 0) {
                    if (!buildState.cacheGlobal.containsKey(limit)) {
                        buildState.cacheGlobal.put(limit, 1);
                    } else if (buildState.cacheGlobal.get(limit) < limit.minGlobalCount &&
                            (limit.maxGlobalCount == -1 ||
                                    buildState.cacheGlobal.get(limit) < limit.maxGlobalCount)) {
                        buildState.cacheGlobal.put(limit, buildState.cacheGlobal.get(limit) + 1);
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (!buildState.cacheInfos.containsKey(limit)) {
                    buildState.cacheInfos.put(limit,
                            limit.candidates == null ? null : limit.candidates.get());
                }
                infos = buildState.cacheInfos.get(limit);
                matchedPredicate = limit;
                find = true;
                break;
            }
        }
        if (!find) {
            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                if (limit.maxLayerCount != -1 &&
                        cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount)
                    continue;
                if (limit.maxGlobalCount != -1 &&
                        buildState.cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxGlobalCount)
                    continue;
                if (!buildState.cacheInfos.containsKey(limit)) {
                    buildState.cacheInfos.put(limit,
                            limit.candidates == null ? null : limit.candidates.get());
                }
                if (cacheLayer.containsKey(limit)) {
                    cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                } else {
                    cacheLayer.put(limit, 1);
                }
                if (buildState.cacheGlobal.containsKey(limit)) {
                    buildState.cacheGlobal.put(limit, buildState.cacheGlobal.get(limit) + 1);
                } else {
                    buildState.cacheGlobal.put(limit, 1);
                }
                infos = ArrayUtils.addAll(infos, buildState.cacheInfos.get(limit));
            }
            for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
                if (!buildState.cacheInfos.containsKey(common)) {
                    buildState.cacheInfos.put(common,
                            common.candidates == null ? null : common.candidates.get());
                }
                infos = ArrayUtils.addAll(infos, buildState.cacheInfos.get(common));
                if ((common.channelName != null || common.defaultCandidate != null) &&
                        (matchedPredicate == null ||
                                (matchedPredicate.channelName == null && common.channelName != null))) {
                    matchedPredicate = common;
                }
            }
        }

        int availableCandidateCount = StructurePlacementDecision.countPlaceable(infos);
        infos = StructurePlacementDecision.filterPlaceable(infos, abilityTracker);
        if (infos.length == 0 && !find) {
            BlockInfo[] directCandidates = getElementCandidates(cell.element, buildState.evaluationContext);
            availableCandidateCount = StructurePlacementDecision.countPlaceable(directCandidates);
            infos = StructurePlacementDecision.filterPlaceable(directCandidates, abilityTracker);
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

        if (skipHatches) {
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
            boolean keepMatchedPredicate = !nonHatchInfos.isEmpty();
            if (nonHatchInfos.isEmpty()) {
                // All candidates in infos are hatches. Search all predicates
                // (both common and limited) for a non-hatch casing candidate.
                // Skip predicates that have already reached their maxGlobalCount
                // to avoid placing excess blocks (e.g. a second coil when max=1).
                for (TraceabilityPredicate.SimplePredicate sp : predicate.limited) {
                    if (sp.maxGlobalCount != -1 &&
                            buildState.cacheGlobal.getOrDefault(sp, 0) >= sp.maxGlobalCount) {
                        continue;
                    }
                    if (!buildState.cacheInfos.containsKey(sp)) {
                        buildState.cacheInfos.put(sp,
                                sp.candidates == null ? null : sp.candidates.get());
                    }
                    BlockInfo[] spInfos = buildState.cacheInfos.get(sp);
                    if (spInfos != null) {
                        for (BlockInfo info : spInfos) {
                            if (info.getBlockState().getBlock() != Blocks.AIR &&
                                    !(info.getTileEntity() instanceof IGregTechTileEntity)) {
                                nonHatchInfos.add(info);
                            }
                        }
                    }
                    if (!nonHatchInfos.isEmpty()) break;
                }
                if (nonHatchInfos.isEmpty()) {
                    for (TraceabilityPredicate.SimplePredicate sp : predicate.common) {
                        if (!buildState.cacheInfos.containsKey(sp)) {
                            buildState.cacheInfos.put(sp,
                                    sp.candidates == null ? null : sp.candidates.get());
                        }
                        BlockInfo[] spInfos = buildState.cacheInfos.get(sp);
                        if (spInfos != null) {
                            for (BlockInfo info : spInfos) {
                                if (info.getBlockState().getBlock() != Blocks.AIR &&
                                        !(info.getTileEntity() instanceof IGregTechTileEntity)) {
                                    nonHatchInfos.add(info);
                                }
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
                if (!keepMatchedPredicate) {
                    matchedPredicate = null;
                }
                if (hadHatchCandidates) {
                    buildState.result.recordSkippedHatchCell();
                }
            }
        }

        buildState.result.recordPlacementBudget();
        ItemStack representativeRequired = operation.isSurvivalBuild()
                ? StructurePlacementDecision.representativeRequiredStack(
                        infos, candidates, matchedPredicate, channelValues, abilityTracker)
                : ItemStack.EMPTY;

        StructurePlacementDecision.Selection buildCandidate = StructurePlacementDecision.select(
                player, infos, candidates, matchedPredicate, channelValues, abilityTracker, operation);
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

    private boolean hasFixedAisleLayout() {
        for (BlockPatternTemplate.AisleDef aisle : template.getAisles()) {
            if (aisle.minRepeat() != aisle.maxRepeat()) return false;
        }
        return true;
    }

    private boolean failForMissingGlobalPredicates(
            @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> counts) {
        TraceabilityPredicate.SimplePredicate firstMissing = null;
        Map<MultiblockAbility<?>, Integer> abilities = new HashMap<>();

        for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : counts.entrySet()) {
            TraceabilityPredicate.SimplePredicate predicate = entry.getKey();
            int deficit = predicate.minGlobalCount - entry.getValue();
            if (deficit <= 0) continue;

            if (firstMissing == null) {
                firstMissing = predicate;
            }
            if (predicate.ability != null) {
                abilities.merge(predicate.ability, deficit, Integer::sum);
            }
        }

        StructureMatchCollector.Validation collectorValidation = new StructureMatchCollector(matchContext).validate();
        if (!collectorValidation.missingAbilities.isEmpty()) {
            collectorValidation.missingAbilities.forEach(
                    (ability, deficit) -> abilities.merge(ability, deficit, Integer::sum));
        }

        if (firstMissing == null) {
            return failForMissingCollectorRequirements(collectorValidation);
        }

        missingAbilities = abilities.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(abilities);
        worldState.setError(new TraceabilityPredicate.SinglePredicateError(firstMissing, 1));
        return true;
    }

    private boolean failForMissingCollectorRequirements(
            @NotNull StructureMatchCollector.Validation validation) {
        if (validation.success) {
            missingAbilities = Collections.emptyMap();
            return false;
        }

        missingAbilities = validation.missingAbilities.isEmpty()
                ? Collections.emptyMap()
                : validation.missingAbilities;
        worldState.setError(validation.error == null
                ? new PatternStringError("gregtech.multiblock.pattern.error.requirements")
                : validation.error);
        return true;
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
        return checkPatternAtExact(world, centerPos, orientation,
                xOffset, yOffset, zOffset, null);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation,
                                                   int xOffset, int yOffset, int zOffset,
                                                   @Nullable StructureMatchSession session) {
        return checkPatternAtExact(
                world,
                StructureCellTraversal.at(centerPos, orientation).withLocalOffset(xOffset, yOffset, zOffset),
                session);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world,
                                                   @NotNull StructureCellTraversal traversal,
                                                   @Nullable StructureMatchSession session) {
        PatternMatchContext result = checkPatternAt(
                world,
                traversal.getCenterPos(),
                traversal.getOrientation(),
                traversal.getXOffset(),
                traversal.getYOffset(),
                traversal.getZOffset(),
                session);
        if (result == null) clearCache();
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
        BlockPatternTemplate.AisleDef[] aisles = template.getAisles();
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
        autoBuild(player, controllerBase, (Map<String, Integer>) null, false);
    }

    /**
     * Auto-build the structure in the world at the given tier.
     * Converts tier to channelValues internally for backward compatibility.
     *
     * @param tier the repetition tier (0 = min/default, 1 = min, 2+ = specific repetition count)
     * @deprecated Use {@link #autoBuild(EntityPlayer, MultiblockControllerBase, Map, boolean)} with channelValues
     */
    @Deprecated
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase, int tier) {
        Map<String, Integer> channels = new HashMap<>();
        if (tier > 0) {
            BlockPatternTemplate.AisleDef[] aisles = template.getAisles();
            for (int i = 0; i < aisles.length; i++) {
                if (aisles[i].minRepeat() == aisles[i].maxRepeat()) continue;
                String name = aisles[i].channelName();
                if (name != null) {
                    channels.put(name, tier);
                }
            }
        }
        autoBuild(player, controllerBase, channels, false);
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
     * @param skipHatches    if true, skip all hatch placement and only place casing blocks
     */
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase,
                          Map<String, Integer> channelValues, boolean skipHatches) {
        autoBuildAt(player, controllerBase, controllerBase.getPos(), channelValues, skipHatches);
    }

    /**
     * Auto-build the structure in the world at a specified center position.
     * Used by MultiPiecePattern to build individual pieces at their offset positions.
     *
     * @param player         the player performing the build
     * @param controllerBase the multiblock controller (used for facing/flip info)
     * @param centerPos      the center position for this build (controller pos or piece center)
     * @param channelValues  map of channel name -> desired value (null = max size, no tier preference)
     * @param skipHatches    if true, skip all hatch placement and only place casing blocks
     */
    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, Map<String, Integer> channelValues, boolean skipHatches) {
        autoBuildAt(player, controllerBase, centerPos, channelValues, skipHatches, null);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        // Delegate to the offset-aware overload with zero cell offsets. Callers that need to
        // fold a piece-level offset (e.g. RepeatGroupPiece) into the cell loop use the
        // (xOffset, yOffset, zOffset) overload directly so the structureDir rotation is
        // applied exactly once per cell.
        autoBuildAt(player, controllerBase, centerPos, 0, 0, 0, channelValues, skipHatches, abilityTracker);
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
     * @param skipHatches    if true, skip all hatch placement and only place casing blocks
     */
    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches) {
        autoBuildAt(player, controllerBase, centerPos, xOffset, yOffset, zOffset,
                channelValues, skipHatches, null);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        autoBuildAt(player, controllerBase, centerPos,
                StructureOrientation.fromController(controllerBase),
                xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                            BlockPos centerPos, @NotNull StructureOrientation orientation,
                            int xOffset, int yOffset, int zOffset,
                            Map<String, Integer> channelValues, boolean skipHatches,
                            @Nullable AbilityPlacementTracker abilityTracker) {
        autoBuildAt(player, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker,
                StructureEvaluationContext.Operation.CREATIVE_BUILD);
    }

    public void autoBuildAt(EntityPlayer player, MultiblockControllerBase controllerBase,
                             BlockPos centerPos, @NotNull StructureOrientation orientation,
                             int xOffset, int yOffset, int zOffset,
                             Map<String, Integer> channelValues, boolean skipHatches,
                             @Nullable AbilityPlacementTracker abilityTracker,
                             @NotNull StructureEvaluationContext.Operation operation) {
        autoBuildAtWithResult(player, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker,
                operation, ItemStack.EMPTY);
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
        return autoBuildAtWithResult(player, controllerBase, centerPos, orientation,
                xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker,
                operation, ItemStack.EMPTY);
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
        return autoBuildAtWithResult(player, controllerBase,
                StructureCellTraversal.at(centerPos, orientation).withLocalOffset(xOffset, yOffset, zOffset),
                channelValues, skipHatches, abilityTracker, operation, triggerStack);
    }

    @NotNull
    public StructureBuildResult autoBuildAtWithResult(EntityPlayer player,
                                                      MultiblockControllerBase controllerBase,
                                                      @NotNull StructureCellTraversal traversal,
                                                      Map<String, Integer> channelValues,
                                                      boolean skipHatches,
                                                      @Nullable AbilityPlacementTracker abilityTracker,
                                                      @NotNull StructureEvaluationContext.Operation operation) {
        return autoBuildAtWithResult(player, controllerBase, traversal, channelValues,
                skipHatches, abilityTracker, operation, ItemStack.EMPTY);
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
        World world = player.world;
        BuildTraversalState buildState = new BuildTraversalState();
        buildState.result.recordAttemptedTraversal();
        buildState.blocks.put(controllerBase.getPos(), controllerBase);
        int[] repetitions = calculateRepetitionsFromChannels(channelValues);

        visitFixedStructureCells(repetitions, traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(),
                (cell, layerCounts) -> autoBuildCell(
                        cell, layerCounts, player, controllerBase, channelValues,
                        skipHatches, abilityTracker, triggerStack, buildState, operation));
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
                    world, cell.worldPos, cell.predicate, controllerBase,
                    StructureEvaluationContext.Operation.HINT);
            IStructureElement<Object> typedElement = (IStructureElement<Object>) cell.element;
            if (typedElement.spawnHint(world, cell.worldPos, triggerStack)) {
                hintState.result.recordTriggerHandledCell();
                return;
            }
            hintState.result.recordContextFallbackCell();
            typedElement.spawnHint(hintState.evaluationContext);
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
    private static BlockInfo selectPreviewBlockInfo(@NotNull TraceabilityPredicate predicate,
                                                    @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> cacheLayer,
                                                    @NotNull PreviewTraversalState previewState,
                                                    @Nullable Map<String, Integer> channelValues) {
        boolean find = false;
        BlockInfo[] infos = null;
        TraceabilityPredicate.SimplePredicate matchedPredicate = null;
        for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
            if (limit.minLayerCount > 0) {
                if (!cacheLayer.containsKey(limit)) {
                    cacheLayer.put(limit, 1);
                } else if (cacheLayer.get(limit) < limit.minLayerCount) {
                    cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                } else {
                    continue;
                }
                if (previewState.cacheGlobal.getOrDefault(limit, 0) < limit.previewCount) {
                    if (!previewState.cacheGlobal.containsKey(limit)) {
                        previewState.cacheGlobal.put(limit, 1);
                    } else if (previewState.cacheGlobal.get(limit) < limit.previewCount) {
                        previewState.cacheGlobal.put(limit, previewState.cacheGlobal.get(limit) + 1);
                    } else {
                        continue;
                    }
                }
            } else {
                continue;
            }
            if (!previewState.cacheInfos.containsKey(limit)) {
                previewState.cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
            }
            infos = previewState.cacheInfos.get(limit);
            matchedPredicate = limit;
            find = true;
            break;
        }
        if (!find) {
            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                if (limit.minGlobalCount == -1 && limit.previewCount == -1) continue;
                if (previewState.cacheGlobal.getOrDefault(limit, 0) < limit.previewCount) {
                    if (!previewState.cacheGlobal.containsKey(limit)) {
                        previewState.cacheGlobal.put(limit, 1);
                    } else if (previewState.cacheGlobal.get(limit) < limit.previewCount) {
                        previewState.cacheGlobal.put(limit, previewState.cacheGlobal.get(limit) + 1);
                    } else {
                        continue;
                    }
                } else if (limit.minGlobalCount > 0) {
                    if (!previewState.cacheGlobal.containsKey(limit)) {
                        previewState.cacheGlobal.put(limit, 1);
                    } else if (previewState.cacheGlobal.get(limit) < limit.minGlobalCount) {
                        previewState.cacheGlobal.put(limit, previewState.cacheGlobal.get(limit) + 1);
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (!previewState.cacheInfos.containsKey(limit)) {
                    previewState.cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
                }
                infos = previewState.cacheInfos.get(limit);
                matchedPredicate = limit;
                find = true;
                break;
            }
        }
        if (!find) {
            for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
                if (common.previewCount > 0) {
                    if (!previewState.cacheGlobal.containsKey(common)) {
                        previewState.cacheGlobal.put(common, 1);
                    } else if (previewState.cacheGlobal.get(common) < common.previewCount) {
                        previewState.cacheGlobal.put(common, previewState.cacheGlobal.get(common) + 1);
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (!previewState.cacheInfos.containsKey(common)) {
                    previewState.cacheInfos.put(common, common.candidates == null ? null : common.candidates.get());
                }
                infos = previewState.cacheInfos.get(common);
                matchedPredicate = common;
                find = true;
                break;
            }
        }
        if (!find) {
            for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
                if (common.previewCount == -1) {
                    if (!previewState.cacheInfos.containsKey(common)) {
                        previewState.cacheInfos.put(common,
                                common.candidates == null ? null : common.candidates.get());
                    }
                    infos = previewState.cacheInfos.get(common);
                    matchedPredicate = common;
                    find = true;
                    break;
                }
            }
        }
        if (!find) {
            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                if (limit.previewCount != -1) {
                    continue;
                } else if (limit.maxGlobalCount != -1 || limit.maxLayerCount != -1) {
                    if (previewState.cacheGlobal.getOrDefault(limit, 0) < limit.maxGlobalCount) {
                        if (!previewState.cacheGlobal.containsKey(limit)) {
                            previewState.cacheGlobal.put(limit, 1);
                        } else {
                            previewState.cacheGlobal.put(limit, previewState.cacheGlobal.get(limit) + 1);
                        }
                    } else if (cacheLayer.getOrDefault(limit, 0) < limit.maxLayerCount) {
                        if (!cacheLayer.containsKey(limit)) {
                            cacheLayer.put(limit, 1);
                        } else {
                            cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                        }
                    } else {
                        continue;
                    }
                }

                if (!previewState.cacheInfos.containsKey(limit)) {
                    previewState.cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
                }
                infos = previewState.cacheInfos.get(limit);
                matchedPredicate = limit;
                break;
            }
        }
        int candidateIdx = StructurePlacementDecision.getChannelCandidateIndex(matchedPredicate, infos, channelValues);
        BlockInfo info = infos == null || infos.length == 0 ? BlockInfo.EMPTY : infos[candidateIdx];
        return copyPreviewTileEntity(info);
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

        BlockPatternTemplate.AisleDef[] aisles = template.getAisles();
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

    /**
     * Get the preview blocks for JEI display.
     */
    public BlockInfo[][][] getPreview(int[] repetition) {
        return getPreview(repetition, null);
    }

    /**
     * Get the preview blocks for JEI display with channel value support.
     *
     * @param repetition    the aisle repetition counts
     * @param channelValues map of channel name -> desired tier (1-based, null or 0 = auto)
     */
    public BlockInfo[][][] getPreview(int[] repetition, @Nullable Map<String, Integer> channelValues) {
        return createPreviewCells(repetition, channelValues).toBlockArray();
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues) {
        return createPreviewCells(repetition, channelValues, DEFAULT_PREVIEW_ORIENTATION);
    }

    @NotNull
    public PreviewCells createPreviewCells(@NotNull int[] repetition,
                                           @Nullable Map<String, Integer> channelValues,
                                           @NotNull StructureOrientation previewOrientation) {
        PreviewTraversalState previewState = new PreviewTraversalState();
        visitFixedStructureCells(repetition, BlockPos.ORIGIN, previewOrientation, 0, 0, 0, (cell, layerCounts) -> {
            BlockInfo info = selectPreviewBlockInfo(cell.predicate, layerCounts, previewState, channelValues);
            previewState.record(cell.worldPos, info, cell.predicate);
        });
        orientPreviewControllers(previewState.blocks);
        return previewState.toCells(calculatePreviewCenter(repetition, previewOrientation));
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
                            !blocks.containsKey(pos.offset(enumFacing))) {
                        metaTileEntity.setFrontFacing(enumFacing);
                        break;
                    }
                }
            }
        });
    }

    @NotNull
    private BlockPos calculatePreviewCenter(@NotNull int[] repetition,
                                            @NotNull StructureOrientation orientation) {
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int finger = -centerOffset.maxZ();
        for (int i = 0; i < centerOffset.z() && i < repetition.length; i++) {
            finger += repetition[i];
        }
        return RelativeDirection.setActualRelativeOffset(
                0, 0, finger,
                orientation.getStructureFront(), orientation.getUp(),
                orientation.isFlipped(), template.getStructureDir());
    }

    public PatternMatchContext checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                          BlockPos centerPos,
                                                          @NotNull StructureOrientation orientation) {
        return checkPatternFastAtSnapshot(blockAccess, centerPos, orientation, 0, 0, 0);
    }

    public PatternMatchContext checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                          BlockPos centerPos,
                                                          @NotNull StructureOrientation orientation,
                                                          int xOffset, int yOffset, int zOffset) {
        // For snapshot checks, we skip the cache fast-path and do a full pattern check
        PatternMatchContext pmc = checkPatternAtSnapshot(blockAccess, centerPos,
                orientation.withFlipped(false), xOffset, yOffset, zOffset, null);
        if (orientation.allowsFlip()) {
            if (pmc != null) {
                return pmc;
            }
            Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities = missingAbilities;
            PatternError unflippedError = worldState.error;
            pmc = checkPatternAtSnapshot(blockAccess, centerPos,
                    orientation.withFlipped(true), xOffset, yOffset, zOffset, null);
            if (pmc == null && shouldKeepUnflippedFailure(unflippedError, unflippedMissingAbilities,
                    worldState.error, missingAbilities)) {
                missingAbilities = unflippedMissingAbilities;
                worldState.setError(unflippedError);
            }
        }
        return pmc;
    }

    @Nullable
    public PatternMatchContext checkOnSnapshotWithPrior(@NotNull net.minecraft.world.IBlockAccess snap,
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
                return matchContext;
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

    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                               @NotNull BlockPos pieceOrigin,
                                               int axis,
                                               @NotNull StructureOrientation orientation) {
        return checkAxisLineFastAtSnapshot(snap, pieceOrigin, axis, orientation, 0, 0, 0);
    }

    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                               @NotNull BlockPos pieceOrigin,
                                               int axis,
                                               @NotNull StructureOrientation orientation,
                                               int xOffset, int yOffset, int zOffset) {
        return checkAxisLineFastAtSnapshot(
                snap,
                axis,
                StructureCellTraversal.at(pieceOrigin, orientation).withLocalOffset(xOffset, yOffset, zOffset));
    }

    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                               int axis,
                                               @NotNull StructureCellTraversal traversal) {
        // For tensor product pieces, all cells are identical,
        // so we only need to check one "line" along the axis.
        // This is a simplified check that verifies the outermost slice.
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();

        // Check the first slice (z=0) as a representative sample
        // For tensor products, if one slice matches, all slices match
        this.matchContext.reset();
        this.globalCount.clear();
        this.layerCount.clear();
        this.missingAbilities = Collections.emptyMap();

        int z = -centerOffset.maxZ(); // Start at the first aisle
        return visitFixedStructureSlice(0, 0, z, traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(),
                layerCount, (cell, layerCounts) -> {
                    worldState.updateFromBlockAccess(snap, cell.worldPos, matchContext, globalCount,
                            layerCounts, cell.predicate);
                    return checkElement(cell.element, null, StructureEvaluationContext.Operation.MATCH_SNAPSHOT);
                });
    }

    /**
     * Internal pattern check against a snapshot.
     * Simplified version that uses IBlockAccess instead of World.
     */
    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos,
                                                       @NotNull StructureOrientation orientation,
                                                       int xOffset, int yOffset, int zOffset,
                                                       @Nullable StructureMatchSession session) {
        return checkFixedStructureCells(null, blockAccess, centerPos, orientation,
                xOffset, yOffset, zOffset, session,
                StructureEvaluationContext.Operation.MATCH_SNAPSHOT, false);
    }

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset) {
        return checkPatternAtSnapshotExact(blockAccess, centerPos, orientation,
                xOffset, yOffset, zOffset, null);
    }

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
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

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull StructureCellTraversal traversal,
            @Nullable StructureMatchSession session) {
        return checkPatternAtSnapshot(blockAccess, traversal.getCenterPos(), traversal.getOrientation(),
                traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset(), session);
    }
}

