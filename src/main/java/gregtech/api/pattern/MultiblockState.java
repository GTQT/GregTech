package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.ExplicitFrontFacingBlockInfo;
import gregtech.api.util.Mods;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.RelativeDirection;
import gregtech.common.ConfigHolder;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.PlayerWirelessGridHelper;
import appeng.me.helpers.BaseActionSource;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * Returns the current match context for this state. The context is refreshed
     * whenever a full pattern check succeeds.
     */
    @NotNull
    public PatternMatchContext getMatchContext() {
        return matchContext;
    }

    /**
     * Fast pattern check using cache, then full check if needed.
     */
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip) {
        return checkPatternFastAt(world, centerPos, frontFacing, upwardsFacing, allowsFlip, true, 0, 0, 0);
    }

    /**
     * Fast pattern check using cache, then full check if needed.
     *
     * @param doRandomCheck if true and cache is large (>512), use random sampling instead of full scan
     */
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip,
                                                  boolean doRandomCheck) {
        return checkPatternFastAt(world, centerPos, frontFacing, upwardsFacing, allowsFlip, doRandomCheck,
                0, 0, 0);
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
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip,
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

        PatternMatchContext pmc = checkPatternAt(world, centerPos, frontFacing, upwardsFacing, false,
                xOffset, yOffset, zOffset);
        if (allowsFlip) {
            if (pmc != null) {
                return pmc;
            }
            Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities = missingAbilities;
            PatternError unflippedError = worldState.error;
            pmc = checkPatternAt(world, centerPos, frontFacing, upwardsFacing, true,
                    xOffset, yOffset, zOffset);
            if (pmc == null && missingAbilities.isEmpty() && !unflippedMissingAbilities.isEmpty()) {
                missingAbilities = unflippedMissingAbilities;
                worldState.setError(unflippedError);
            }
        }
        if (pmc == null) clearCache();
        return pmc;
    }

    private PatternMatchContext checkPatternAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                               EnumFacing upwardsFacing, boolean isFlipped,
                                               int xOffset, int yOffset, int zOffset) {
        return checkPatternAt(world, centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset, yOffset, zOffset, null);
    }

    private PatternMatchContext checkPatternAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                               EnumFacing upwardsFacing, boolean isFlipped,
                                               int xOffset, int yOffset, int zOffset,
                                               @Nullable StructureMatchSession session) {
        return checkPatternAt(world, centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset, yOffset, zOffset, session);
    }

    private PatternMatchContext checkPatternAt(World world, BlockPos centerPos,
                                               @NotNull StructureOrientation orientation,
                                               int xOffset, int yOffset, int zOffset,
                                               @Nullable StructureMatchSession session) {
        IStructureElement<?>[][][] elements = template.getElements();
        int[][] aisleRepetitions = template.getAisleRepetitions();
        RelativeDirection[] structureDir = template.getStructureDir();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

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
        cache.clear();
        this.cachedXOffset = xOffset;
        this.cachedYOffset = yOffset;
        this.cachedZOffset = zOffset;
        this.cacheOffsetsRecorded = true;

        // Checking aisles
        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset.minZ()); r++) {
                // Checking single slice
                this.layerCount.clear();

                for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                        IStructureElement<?> element = elements[c][b][a];
                        TraceabilityPredicate predicate = element.toPredicate();
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(
                                x, y, z,
                                orientation.getStructureFront(), orientation.getUp(),
                                orientation.isFlipped(), structureDir)
                                .add(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        worldState.update(world, pos, activeContext, activeGlobalCount, layerCount, predicate);
                        TileEntity tileEntity = worldState.getTileEntity();
                        if (predicate != TraceabilityPredicate.ANY) {
                            if (tileEntity instanceof IGregTechTileEntity) {
                                if (((IGregTechTileEntity) tileEntity).isValid()) {
                                    cache.put(pos.toLong(),
                                            new BlockInfo(worldState.getBlockState(), tileEntity, predicate));
                                } else {
                                    cache.put(pos.toLong(),
                                            new BlockInfo(worldState.getBlockState(), null, predicate));
                                }
                            } else {
                                cache.put(pos.toLong(),
                                        new BlockInfo(worldState.getBlockState(), tileEntity, predicate));
                            }
                        }
                        if (!checkElement(element, session, StructureEvaluationContext.Operation.MATCH_WORLD)) {
                            recordMissingFixedAbility(predicate);
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {
                                    r = c = 0;
                                    z = minZ++;
                                    if (session == null) {
                                        activeContext.reset();
                                        activeGlobalCount.clear();
                                    } else {
                                        session.restore(initialCheckpoint);
                                    }
                                    findFirstAisle = false;
                                }
                            } else {
                                z++;
                            }
                            continue loop;
                        }
                    }
                }
                findFirstAisle = true;
                z++;

                // Check layer-local matcher predicate
                for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : layerCount.entrySet()) {
                    if (entry.getValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new TraceabilityPredicate.SinglePredicateError(entry.getKey(), 3));
                        return null;
                    }
                }
                validRepetitions++;
            }
            // Repetitions out of range
            if (r < aisleRepetitions[c][0]) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return null;
            }

            // finished checking the aisle, so store the repetitions
            formedRepetitionCount[c] = validRepetitions;
        }

        if (session == null && failForMissingGlobalPredicates(activeGlobalCount)) return null;

        worldState.setError(null);
        activeContext.setNeededFlip(orientation.isFlipped());
        return activeContext;
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
        typedElement.collectRequirements(evaluationContext);
        return typedElement.check(evaluationContext);
    }

    @FunctionalInterface
    private interface FixedStructureCellVisitor {

        void visit(@NotNull FixedStructureCell cell,
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
        IStructureElement<?>[][][] elements = template.getElements();
        RelativeDirection[] structureDir = template.getStructureDir();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        int z = -centerOffset.maxZ();
        for (int c = 0; c < fingerLength; c++) {
            int repetitionCount = c < repetitions.length ? repetitions[c] : 0;
            for (int r = 0; r < repetitionCount; r++) {
                Map<TraceabilityPredicate.SimplePredicate, Integer> layerCounts = new HashMap<>();
                for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                        IStructureElement<?> element = elements[c][b][a];
                        TraceabilityPredicate predicate = element.toPredicate();
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(
                                x + xOffset, y + yOffset, z + zOffset,
                                orientation.getStructureFront(), orientation.getUp(),
                                orientation.isFlipped(), structureDir)
                                .add(centerPos);
                        visitor.visit(new FixedStructureCell(
                                c, r, x, y, z, pos, element, predicate), layerCounts);
                    }
                }
                z++;
            }
        }
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
        world.setBlockState(context.getPos(), matchedInfo.getBlockState());
        return true;
    }

    private static final class BuildCandidate {

        @NotNull
        private final ItemStack found;
        @NotNull
        private final BlockInfo matchedInfo;

        private BuildCandidate(@NotNull ItemStack found, @NotNull BlockInfo matchedInfo) {
            this.found = found;
            this.matchedInfo = matchedInfo;
        }
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

    @Nullable
    private static BuildCandidate selectBuildCandidate(
            @NotNull EntityPlayer player,
            @NotNull BlockInfo[] infos,
            @NotNull List<ItemStack> candidates,
            @Nullable TraceabilityPredicate.SimplePredicate matchedPredicate,
            @Nullable Map<String, Integer> channelValues,
            @Nullable AbilityPlacementTracker abilityTracker) {
        int requiredAbilityIndex = findRequiredAbilityCandidate(infos, abilityTracker);
        if (!player.isCreative()) {
            if (requiredAbilityIndex >= 0) {
                BuildCandidate required = selectSurvivalCandidate(
                        player, infos, Collections.singletonList(candidates.get(requiredAbilityIndex)),
                        requiredAbilityIndex);
                if (required != null) {
                    return required;
                }
            }
            int preferredIdx = getPreferredChannelCandidateIndex(matchedPredicate, infos, channelValues);
            if (preferredIdx >= 0 && preferredIdx < candidates.size()) {
                BuildCandidate preferred = selectSurvivalCandidate(
                        player, infos, Collections.singletonList(candidates.get(preferredIdx)), preferredIdx);
                return preferred;
            }
            BuildCandidate inventoryCandidate = selectSurvivalCandidate(player, infos, candidates, -1);
            if (inventoryCandidate != null) {
                return inventoryCandidate;
            }
            ItemStack found = tryExtractFromAENetwork(player, candidates);
            if (found != null) {
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).isItemEqual(found)) {
                        return new BuildCandidate(found, infos[i]);
                    }
                }
            }
            return null;
        }

        int preferredIndex = requiredAbilityIndex;
        if (preferredIndex < 0) {
            int channelIndex = getPreferredChannelCandidateIndex(matchedPredicate, infos, channelValues);
            if (channelIndex >= 0) {
                preferredIndex = channelIndex;
            }
        }
        if (preferredIndex >= 0 && preferredIndex < candidates.size()) {
            return new BuildCandidate(candidates.get(preferredIndex).copy(), infos[preferredIndex]);
        }
        for (int i = candidates.size() - 1; i >= 0; i--) {
            ItemStack found = candidates.get(i).copy();
            if (!found.isEmpty()) {
                return new BuildCandidate(found, infos[i]);
            }
        }
        return null;
    }

    @Nullable
    private static BuildCandidate selectSurvivalCandidate(@NotNull EntityPlayer player,
                                                          @NotNull BlockInfo[] infos,
                                                          @NotNull List<ItemStack> candidates,
                                                          int fixedCandidateIndex) {
        if (fixedCandidateIndex >= 0) {
            ItemStack found = extractCandidateFromInventory(player, candidates.get(0));
            if (found != null) {
                return new BuildCandidate(found, infos[fixedCandidateIndex]);
            }
            found = tryExtractFromAENetwork(player, candidates);
            return found == null ? null : new BuildCandidate(found, infos[fixedCandidateIndex]);
        }

        for (int i = 0; i < candidates.size(); i++) {
            ItemStack found = extractCandidateFromInventory(player, candidates.get(i));
            if (found != null) {
                return new BuildCandidate(found, infos[i]);
            }
        }
        return null;
    }

    @Nullable
    private static ItemStack extractCandidateFromInventory(@NotNull EntityPlayer player,
                                                           @NotNull ItemStack candidate) {
        for (ItemStack itemStack : player.inventory.mainInventory) {
            if (candidate.isItemEqual(itemStack) && !itemStack.isEmpty()) {
                ItemStack found = itemStack.copy();
                itemStack.setCount(itemStack.getCount() - 1);
                return found;
            }
        }
        return null;
    }

    private void autoBuildCell(@NotNull FixedStructureCell cell,
                               @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> cacheLayer,
                               @NotNull EntityPlayer player,
                               @NotNull MultiblockControllerBase controllerBase,
                               @Nullable Map<String, Integer> channelValues,
                               boolean skipHatches,
                               @Nullable AbilityPlacementTracker abilityTracker,
                               @NotNull BuildTraversalState buildState,
                               @NotNull StructureEvaluationContext.Operation operation) {
        World world = player.world;
        TraceabilityPredicate predicate = cell.predicate;
        BlockPos pos = cell.worldPos;
        buildState.result.recordVisitedCell();

        updateOperationCellContext(buildState.evaluationContext, buildState.worldState,
                world, pos, predicate, controllerBase, operation);
        if (!world.getBlockState(pos).getMaterial().isReplaceable()) {
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
                if (common.channelName != null &&
                        (matchedPredicate == null || matchedPredicate.channelName == null)) {
                    matchedPredicate = common;
                }
            }
        }

        int availableCandidateCount = 0;
        if (infos == null) {
            infos = new BlockInfo[0];
        } else {
            availableCandidateCount = (int) Arrays.stream(infos)
                    .filter(info -> info.getBlockState().getBlock() != Blocks.AIR)
                    .count();
            infos = Arrays.stream(infos)
                    .filter(info -> info.getBlockState().getBlock() != Blocks.AIR)
                    .filter(info -> abilityTracker == null || abilityTracker.canPlace(info))
                    .toArray(BlockInfo[]::new);
        }
        List<ItemStack> candidates = Arrays.stream(infos)
                .map(MultiblockState::getStackForBlockInfo)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
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
                            .map(info -> {
                                IBlockState blockState = info.getBlockState();
                                return new ItemStack(
                                        Item.getItemFromBlock(blockState.getBlock()),
                                        1, blockState.getBlock().damageDropped(blockState));
                            }).collect(Collectors.toList());
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

        BuildCandidate buildCandidate = selectBuildCandidate(
                player, infos, candidates, matchedPredicate, channelValues, abilityTracker);
        if (buildCandidate == null) {
            buildState.result.recordUnavailableItemCell();
            return;
        }
        ItemStack found = buildCandidate.found;
        BlockInfo matchedInfo = buildCandidate.matchedInfo;

        if (!placeBuildCandidate(matchedInfo, buildState.evaluationContext)) {
            buildState.result.recordPlacementFailureCell();
            return;
        }
        IBlockState state = matchedInfo.getBlockState();
        buildState.result.recordPlacedCell();
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

    /**
     * Check exactly one orientation. This does not try the opposite flip state.
     */
    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull EnumFacing frontFacing,
                                                   @NotNull EnumFacing upwardsFacing,
                                                   boolean isFlipped) {
        return checkPatternAtExact(world, centerPos, frontFacing, upwardsFacing, isFlipped, 0, 0, 0);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation) {
        return checkPatternAtExact(world, centerPos, orientation, 0, 0, 0);
    }

    /**
     * Exact-orientation check with a template-local offset applied to every cell.
     */
    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull EnumFacing frontFacing,
                                                   @NotNull EnumFacing upwardsFacing,
                                                   boolean isFlipped,
                                                   int xOffset, int yOffset, int zOffset) {
        return checkPatternAtExact(world, centerPos, frontFacing, upwardsFacing, isFlipped,
                xOffset, yOffset, zOffset, null);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation,
                                                   int xOffset, int yOffset, int zOffset) {
        return checkPatternAtExact(world, centerPos, orientation,
                xOffset, yOffset, zOffset, null);
    }

    /**
     * Exact-orientation check participating in a larger transactional match.
     */
    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull EnumFacing frontFacing,
                                                   @NotNull EnumFacing upwardsFacing,
                                                   boolean isFlipped,
                                                   int xOffset, int yOffset, int zOffset,
                                                   @Nullable StructureMatchSession session) {
        return checkPatternAtExact(
                world,
                centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset, yOffset, zOffset, session);
    }

    @Nullable
    public PatternMatchContext checkPatternAtExact(@NotNull World world, @NotNull BlockPos centerPos,
                                                   @NotNull StructureOrientation orientation,
                                                   int xOffset, int yOffset, int zOffset,
                                                   @Nullable StructureMatchSession session) {
        PatternMatchContext result = checkPatternAt(
                world, centerPos, orientation, xOffset, yOffset, zOffset, session);
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
                xOffset, yOffset, zOffset, channelValues, skipHatches, abilityTracker, operation);
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
        World world = player.world;
        BuildTraversalState buildState = new BuildTraversalState();
        buildState.result.recordAttemptedTraversal();
        buildState.blocks.put(controllerBase.getPos(), controllerBase);
        int[] repetitions = calculateRepetitionsFromChannels(channelValues);

        visitFixedStructureCells(repetitions, centerPos, orientation, xOffset, yOffset, zOffset,
                (cell, layerCounts) -> autoBuildCell(
                        cell, layerCounts, player, controllerBase, channelValues,
                        skipHatches, abilityTracker, buildState, operation));
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
        spawnHintsAt(world, controllerBase, centerPos, orientation, 0, 0, 0, channelValues, triggerStack);
    }

    @SuppressWarnings("unchecked")
    public void spawnHintsAt(@NotNull World world,
                             @NotNull MultiblockControllerBase controllerBase,
                             @NotNull BlockPos centerPos,
                             @NotNull StructureOrientation orientation,
                             int xOffset, int yOffset, int zOffset,
                             @Nullable Map<String, Integer> channelValues,
                             @NotNull ItemStack triggerStack) {
        BuildTraversalState hintState = new BuildTraversalState();
        int[] repetitions = calculateRepetitionsFromChannels(channelValues);
        visitFixedStructureCells(repetitions, centerPos, orientation, xOffset, yOffset, zOffset, (cell, layerCounts) -> {
            updateOperationCellContext(hintState.evaluationContext, hintState.worldState,
                    world, cell.worldPos, cell.predicate, controllerBase,
                    StructureEvaluationContext.Operation.HINT);
            IStructureElement<Object> typedElement = (IStructureElement<Object>) cell.element;
            if (typedElement.spawnHint(world, cell.worldPos, triggerStack)) {
                return;
            }
            typedElement.spawnHint(hintState.evaluationContext);
        });
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
        int candidateIdx = getChannelCandidateIndex(matchedPredicate, infos, channelValues);
        BlockInfo info = infos == null || infos.length == 0 ? BlockInfo.EMPTY : infos[candidateIdx];
        return copyPreviewTileEntity(info);
    }

    /**
     * 尝试从玩家的 AE2 无线终端网络中提取物品。
     * 仅在服务端生效，需要 AE2 已加载且玩家拥有已连接的无线终端。
     *
     * @param player     玩家
     * @param candidates 候选物品列表
     * @return 提取到的 ItemStack，失败返回 null
     */
    @Nullable
    private static ItemStack tryExtractFromAENetwork(EntityPlayer player, List<ItemStack> candidates) {
        if (!Mods.AppliedEnergistics2.isModLoaded()) return null;
        if (player.world.isRemote) return null;

        try {
            IStorageGrid storageGrid = PlayerWirelessGridHelper.getStorageGrid(player);
            if (storageGrid == null) return null;

            IItemStorageChannel channel = AEApi.instance().storage()
                    .getStorageChannel(IItemStorageChannel.class);
            IMEMonitor<IAEItemStack> monitor = storageGrid.getInventory(channel);
            if (monitor == null) return null;

            for (ItemStack candidate : candidates) {
                if (candidate.isEmpty()) continue;

                IAEItemStack request = channel.createStack(candidate);
                request.setStackSize(1);
                IAEItemStack extracted = monitor.extractItems(request, Actionable.MODULATE,
                        new BaseActionSource());
                if (extracted != null && extracted.getStackSize() > 0) {
                    return candidate.copy();
                }
            }
        } catch (Exception ignored) {
            // 无线终端可能超出范围、没电或网络不可用
        }
        return null;
    }

    private static int findRequiredAbilityCandidate(
            BlockInfo[] infos, @Nullable AbilityPlacementTracker abilityTracker) {
        if (abilityTracker == null) return -1;
        for (int i = infos.length - 1; i >= 0; i--) {
            if (abilityTracker.isStillRequired(infos[i])) {
                return i;
            }
        }
        return -1;
    }

    @NotNull
    private static ItemStack getStackForBlockInfo(@NotNull BlockInfo info) {
        IBlockState blockState = info.getBlockState();
        MetaTileEntity metaTileEntity = info.getTileEntity() instanceof IGregTechTileEntity ?
                ((IGregTechTileEntity) info.getTileEntity()).getMetaTileEntity() : null;
        if (metaTileEntity != null) {
            return metaTileEntity.getStackForm();
        }
        return new ItemStack(Item.getItemFromBlock(blockState.getBlock()), 1,
                blockState.getBlock().damageDropped(blockState));
    }

    /**
     * Get all structure blocks (from cache or by calculating positions).
     */
    public Map<BlockPos, BlockInfo> getAllStructureBlocks(World world, BlockPos centerPos,
                                                          EnumFacing frontFacing, EnumFacing upwardsFacing,
                                                          boolean isFlipped) {
        return getAllStructureBlocks(world, centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false));
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

    /**
     * Determine the candidate index based on channel values.
     * If the predicate has a channel name and channelValues specifies a tier,
     * use that tier (1-based) as the index. Otherwise use 0 (default).
     */
    private static int getChannelCandidateIndex(@Nullable TraceabilityPredicate.SimplePredicate predicate,
                                                 @Nullable BlockInfo[] infos,
                                                 @Nullable Map<String, Integer> channelValues) {
        int preferredIndex = getPreferredChannelCandidateIndex(predicate, infos, channelValues);
        if (preferredIndex >= 0) return preferredIndex;
        return 0;
    }

    private static int getPreferredChannelCandidateIndex(@Nullable TraceabilityPredicate.SimplePredicate predicate,
                                                         @Nullable BlockInfo[] infos,
                                                         @Nullable Map<String, Integer> channelValues) {
        if (predicate == null || infos == null || infos.length == 0) return -1;
        if (channelValues == null || predicate.channelName == null) return -1;
        Integer cv = channelValues.get(predicate.channelName);
        if (cv == null || cv <= 0) return -1;
        int idx = cv - 1;
        return idx < infos.length ? idx : -1;
    }

    /**
     * Perform pattern checking against a snapshot (IBlockAccess) instead of a live World.
     * Used by the async structure checker (P2) for thread-safe pattern matching.
     *
     * <p>This is a simplified version that only checks IBlockState matches (no TileEntity checks).
     * If this returns non-null, a confirmatory check should be done on the main thread.
     *
     * @param blockAccess    the snapshot to check against
     * @param centerPos      the center position of the pattern
     * @param frontFacing    the front facing direction
     * @param upwardsFacing  the upwards facing direction
     * @param allowsFlip     whether flipping is allowed
     * @return the match context if the pattern matches, or null
     */
    public PatternMatchContext checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                          BlockPos centerPos, EnumFacing frontFacing,
                                                          EnumFacing upwardsFacing, boolean allowsFlip) {
        return checkPatternFastAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing, allowsFlip,
                0, 0, 0);
    }

    /**
     * Snapshot variant of {@link #checkPatternFastAt(World, BlockPos, EnumFacing, EnumFacing,
     * boolean, boolean, int, int, int)}. See that method for the cell-offset contract.
     */
    public PatternMatchContext checkPatternFastAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                          BlockPos centerPos, EnumFacing frontFacing,
                                                          EnumFacing upwardsFacing, boolean allowsFlip,
                                                          int xOffset, int yOffset, int zOffset) {
        // For snapshot checks, we skip the cache fast-path and do a full pattern check
        PatternMatchContext pmc = checkPatternAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing,
                false, xOffset, yOffset, zOffset);
        if (allowsFlip) {
            if (pmc != null) {
                return pmc;
            }
            Map<MultiblockAbility<?>, Integer> unflippedMissingAbilities = missingAbilities;
            PatternError unflippedError = worldState.error;
            pmc = checkPatternAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing,
                    true, xOffset, yOffset, zOffset);
            if (pmc == null && missingAbilities.isEmpty() && !unflippedMissingAbilities.isEmpty()) {
                missingAbilities = unflippedMissingAbilities;
                worldState.setError(unflippedError);
            }
        }
        return pmc;
    }

    /**
     * Perform pattern checking against a snapshot with prior metadata for acceleration.
     * If prior is available, uses the known repeat counts as an initial guess for O(1) verification.
     * Falls back to full search if prior verification fails.
     *
     * @param snap          the snapshot to check against
     * @param centerPos     the center position of the pattern
     * @param frontFacing   the front facing direction
     * @param upwardsFacing the upwards facing direction
     * @param allowsFlip    whether flipping is allowed
     * @param prior         prior formed metadata (null = no prior, do full search)
     * @return the match context if the pattern matches, or null
     */
    @Nullable
    public PatternMatchContext checkOnSnapshotWithPrior(@NotNull net.minecraft.world.IBlockAccess snap,
                                                         @NotNull BlockPos centerPos,
                                                         @NotNull EnumFacing frontFacing,
                                                         @NotNull EnumFacing upwardsFacing,
                                                         boolean allowsFlip,
                                                         @Nullable FormedStructureMetadata prior) {
        if (prior == null) {
            // No prior: delegate to standard snapshot check
            return checkPatternFastAtSnapshot(snap, centerPos, frontFacing, upwardsFacing, allowsFlip);
        }

        // Fast path: verify cached positions against snapshot
        // If the cache is non-empty and all positions still match, we're done in O(cache_size)
        if (!cache.isEmpty()) {
            if (verifyCacheAgainstSnapshot(snap)) {
                return matchContext;
            }
        }

        // Cache miss or verification failed: full search
        return checkPatternFastAtSnapshot(snap, centerPos, frontFacing, upwardsFacing, allowsFlip);
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

    /**
     * 1D slice verification along a specific axis (tensor product piece optimization).
     * Only checks the cells along the specified axis direction, not the entire base piece.
     * Used by RepeatGroupPiece for INDEPENDENT_1D search strategy.
     *
     * @param snap          the snapshot to check against
     * @param pieceOrigin   the center position for this piece
     * @param axis          the axis to check along (0=X, 1=Y, 2=Z)
     * @param frontFacing   the front facing direction
     * @param upwardsFacing the upwards facing direction
     * @param isFlipped     whether the structure is flipped
     * @return true if the 1D slice matches
     */
    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                                @NotNull BlockPos pieceOrigin,
                                                int axis,
                                                @NotNull EnumFacing frontFacing,
                                                @NotNull EnumFacing upwardsFacing,
                                                boolean isFlipped) {
        return checkAxisLineFastAtSnapshot(snap, pieceOrigin, axis, frontFacing, upwardsFacing, isFlipped,
                0, 0, 0);
    }

    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                               @NotNull BlockPos pieceOrigin,
                                               int axis,
                                               @NotNull StructureOrientation orientation) {
        return checkAxisLineFastAtSnapshot(snap, pieceOrigin, axis, orientation, 0, 0, 0);
    }

    /**
     * 1D slice verification along a specific axis (tensor product piece optimization),
     * with an additional template-local cell offset folded into the per-cell transformation.
     * <p>
     * Mirrors the contract of
     * {@link #checkPatternFastAt(World, BlockPos, EnumFacing, EnumFacing, boolean, boolean,
     * int, int, int)}: the offsets are added to every cell's (x, y, z) before
     * {@link RelativeDirection#setActualRelativeOffset} runs, so the transformation happens
     * exactly once per cell. The {@code pieceOrigin} here is the world-space center of the
     * piece (typically {@link StructurePiece#getCenterPos}), and the offsets encode the
     * template-local slice step the caller wants to verify.
     */
    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                                @NotNull BlockPos pieceOrigin,
                                                int axis,
                                                @NotNull EnumFacing frontFacing,
                                                @NotNull EnumFacing upwardsFacing,
                                                boolean isFlipped,
                                                int xOffset, int yOffset, int zOffset) {
        return checkAxisLineFastAtSnapshot(
                snap,
                pieceOrigin,
                axis,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset,
                yOffset,
                zOffset);
    }

    public boolean checkAxisLineFastAtSnapshot(@NotNull net.minecraft.world.IBlockAccess snap,
                                               @NotNull BlockPos pieceOrigin,
                                               int axis,
                                               @NotNull StructureOrientation orientation,
                                               int xOffset, int yOffset, int zOffset) {
        // For tensor product pieces, all cells are identical,
        // so we only need to check one "line" along the axis.
        // This is a simplified check that verifies the outermost slice.
        IStructureElement<?>[][][] elements = template.getElements();
        RelativeDirection[] structureDir = template.getStructureDir();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        // Check the first slice (z=0) as a representative sample
        // For tensor products, if one slice matches, all slices match
        this.matchContext.reset();
        this.globalCount.clear();
        this.layerCount.clear();
        this.missingAbilities = Collections.emptyMap();

        int z = -centerOffset.maxZ(); // Start at the first aisle
        for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
            for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                IStructureElement<?> element = elements[0][b][a];
                TraceabilityPredicate predicate = element.toPredicate();
                BlockPos pos = RelativeDirection.setActualRelativeOffset(x + xOffset, y + yOffset,
                        z + zOffset,
                        orientation.getStructureFront(), orientation.getUp(),
                        orientation.isFlipped(), structureDir)
                        .add(pieceOrigin.getX(), pieceOrigin.getY(), pieceOrigin.getZ());
                worldState.updateFromBlockAccess(snap, pos, matchContext, globalCount, layerCount, predicate);
                if (!checkElement(element, null, StructureEvaluationContext.Operation.MATCH_SNAPSHOT)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Internal pattern check against a snapshot.
     * Simplified version that uses IBlockAccess instead of World.
     */
    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos, EnumFacing frontFacing,
                                                       EnumFacing upwardsFacing, boolean isFlipped) {
        return checkPatternAtSnapshot(blockAccess, centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                0, 0, 0, null);
    }

    /**
     * Snapshot variant of {@link #checkPatternAt} that accepts template-local cell offsets.
     * The offsets are added to every cell's (x, y, z) before
     * {@link RelativeDirection#setActualRelativeOffset} runs, so the transformation happens
     * exactly once per cell.
     */
    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos, EnumFacing frontFacing,
                                                       EnumFacing upwardsFacing, boolean isFlipped,
                                                       int xOffset, int yOffset, int zOffset) {
        return checkPatternAtSnapshot(blockAccess, centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset, yOffset, zOffset, null);
    }

    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos, EnumFacing frontFacing,
                                                       EnumFacing upwardsFacing, boolean isFlipped,
                                                       int xOffset, int yOffset, int zOffset,
                                                       @Nullable StructureMatchSession session) {
        return checkPatternAtSnapshot(blockAccess, centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset, yOffset, zOffset, session);
    }

    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos,
                                                       @NotNull StructureOrientation orientation,
                                                       int xOffset, int yOffset, int zOffset,
                                                       @Nullable StructureMatchSession session) {
        IStructureElement<?>[][][] elements = template.getElements();
        int[][] aisleRepetitions = template.getAisleRepetitions();
        RelativeDirection[] structureDir = template.getStructureDir();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

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

        // Checking aisles
        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset.minZ()); r++) {
                // Checking single slice
                this.layerCount.clear();

                for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                        IStructureElement<?> element = elements[c][b][a];
                        TraceabilityPredicate predicate = element.toPredicate();
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(x + xOffset, y + yOffset,
                                z + zOffset,
                                orientation.getStructureFront(), orientation.getUp(),
                                orientation.isFlipped(), structureDir)
                                .add(centerPos.getX(), centerPos.getY(), centerPos.getZ());

                        // Use snapshot-aware update
                        worldState.updateFromBlockAccess(blockAccess, pos, activeContext, activeGlobalCount, layerCount,
                                predicate);

                        if (!checkElement(element, session, StructureEvaluationContext.Operation.MATCH_SNAPSHOT)) {
                            recordMissingFixedAbility(predicate);
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {
                                    r = c = 0;
                                    z = minZ++;
                                    if (session == null) {
                                        activeContext.reset();
                                        activeGlobalCount.clear();
                                    } else {
                                        session.restore(initialCheckpoint);
                                    }
                                    findFirstAisle = false;
                                }
                            } else {
                                z++;
                            }
                            continue loop;
                        }
                    }
                }
                findFirstAisle = true;
                z++;

                // Check layer-local matcher predicate
                for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : layerCount.entrySet()) {
                    if (entry.getValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new TraceabilityPredicate.SinglePredicateError(entry.getKey(), 3));
                        return null;
                    }
                }
                validRepetitions++;
            }
            // Repetitions out of range
            if (r < aisleRepetitions[c][0]) {
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

    /**
     * Snapshot check for exactly one orientation.
     */
    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull EnumFacing frontFacing,
            @NotNull EnumFacing upwardsFacing,
            boolean isFlipped,
            int xOffset, int yOffset, int zOffset) {
        return checkPatternAtSnapshotExact(blockAccess, centerPos, frontFacing, upwardsFacing, isFlipped,
                xOffset, yOffset, zOffset, null);
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

    /**
     * Snapshot check participating in a larger transactional match.
     */
    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull EnumFacing frontFacing,
            @NotNull EnumFacing upwardsFacing,
            boolean isFlipped,
            int xOffset, int yOffset, int zOffset,
            @Nullable StructureMatchSession session) {
        return checkPatternAtSnapshotExact(
                blockAccess,
                centerPos,
                StructureOrientation.of(frontFacing, frontFacing, upwardsFacing, isFlipped, false),
                xOffset, yOffset, zOffset, session);
    }

    @Nullable
    public PatternMatchContext checkPatternAtSnapshotExact(
            @NotNull net.minecraft.world.IBlockAccess blockAccess,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            int xOffset, int yOffset, int zOffset,
            @Nullable StructureMatchSession session) {
        return checkPatternAtSnapshot(blockAccess, centerPos, orientation,
                xOffset, yOffset, zOffset, session);
    }
}

