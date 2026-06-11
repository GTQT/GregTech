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
        return checkPatternAt(world, centerPos, frontFacing, upwardsFacing, isFlipped,
                xOffset, yOffset, zOffset, null);
    }

    private PatternMatchContext checkPatternAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                               EnumFacing upwardsFacing, boolean isFlipped,
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
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(x, y, z, frontFacing, upwardsFacing,
                                isFlipped, structureDir)
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
        activeContext.setNeededFlip(isFlipped);
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
        PatternMatchContext result = checkPatternAt(
                world, centerPos, frontFacing, upwardsFacing, isFlipped,
                xOffset, yOffset, zOffset, session);
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
        IStructureElement<?>[][][] elements = template.getElements();
        int[][] aisleRepetitions = template.getAisleRepetitions();
        RelativeDirection[] structureDir = template.getStructureDir();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();
        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        World world = player.world;
        BlockWorldState bws = new BlockWorldState();
        int minZ = -centerOffset.maxZ();
        EnumFacing facing = controllerBase.getFrontFacingForStructure();
        Map<TraceabilityPredicate.SimplePredicate, BlockInfo[]> cacheInfos = new HashMap<>();
        Map<TraceabilityPredicate.SimplePredicate, Integer> cacheGlobal = new HashMap<>();
        Map<BlockPos, Object> blocks = new HashMap<>();
        Map<BlockPos, EnumFacing> explicitFrontFacings = new HashMap<>();
        blocks.put(controllerBase.getPos(), controllerBase);

        int[] repetitions = calculateRepetitionsFromChannels(channelValues);

        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            for (r = 0; r < repetitions[c]; r++) {
                Map<TraceabilityPredicate.SimplePredicate, Integer> cacheLayer = new HashMap<>();
                for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                        IStructureElement<?> element = elements[c][b][a];
                        TraceabilityPredicate predicate = element.toPredicate();
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(x + xOffset, y + yOffset,
                                z + zOffset, facing,
                                controllerBase.getUpwardsFacing(),
                                controllerBase.isFlipped(), structureDir)
                                .add(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        bws.update(world, pos, matchContext, globalCount, layerCount, predicate);
                        if (!world.getBlockState(pos).getMaterial().isReplaceable()) {
                            blocks.put(pos, world.getBlockState(pos));
                            if (abilityTracker != null) {
                                abilityTracker.recordWorldTile(pos, world.getTileEntity(pos));
                            }
                            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                                limit.testLimited(bws);
                            }
                        } else {
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
                                if (!cacheInfos.containsKey(limit)) {
                                    cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
                                }
                                infos = cacheInfos.get(limit);
                                matchedPredicate = limit;
                                find = true;
                                break;
                            }
                            if (!find) {
                                for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                                    if (limit.minGlobalCount > 0) {
                                        if (!cacheGlobal.containsKey(limit)) {
                                            cacheGlobal.put(limit, 1);
                                        } else if (cacheGlobal.get(limit) < limit.minGlobalCount &&
                                                (limit.maxGlobalCount == -1 ||
                                                        cacheGlobal.get(limit) < limit.maxGlobalCount)) {
                                            cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    if (!cacheInfos.containsKey(limit)) {
                                        cacheInfos.put(limit,
                                                limit.candidates == null ? null : limit.candidates.get());
                                    }
                                    infos = cacheInfos.get(limit);
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
                                            cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxGlobalCount)
                                        continue;
                                    if (!cacheInfos.containsKey(limit)) {
                                        cacheInfos.put(limit,
                                                limit.candidates == null ? null : limit.candidates.get());
                                    }
                                    if (cacheLayer.containsKey(limit)) {
                                        cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                                    } else {
                                        cacheLayer.put(limit, 1);
                                    }
                                    if (cacheGlobal.containsKey(limit)) {
                                        cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                    } else {
                                        cacheGlobal.put(limit, 1);
                                    }
                                    infos = ArrayUtils.addAll(infos, cacheInfos.get(limit));
                                }
                                for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
                                    if (!cacheInfos.containsKey(common)) {
                                        cacheInfos.put(common,
                                                common.candidates == null ? null : common.candidates.get());
                                    }
                                    infos = ArrayUtils.addAll(infos, cacheInfos.get(common));
                                    if (common.channelName != null &&
                                            (matchedPredicate == null || matchedPredicate.channelName == null)) {
                                        matchedPredicate = common;
                                    }
                                }
                            }

                            if (infos != null) {
                                infos = Arrays.stream(infos)
                                        .filter(info -> info.getBlockState().getBlock() != Blocks.AIR)
                                        .filter(info -> abilityTracker == null || abilityTracker.canPlace(info))
                                        .toArray(BlockInfo[]::new);
                            }
                            List<ItemStack> candidates = Arrays.stream(infos)
                                    .map(MultiblockState::getStackForBlockInfo)
                                    .collect(Collectors.toList());
                            if (candidates.isEmpty()) continue;

                            // skipHatches mode: replace hatch positions with casing blocks.
                            // Dedicated hatch positions (like muffler) where no non-hatch candidate
                            // can be found will still place the hatch normally.
                            if (skipHatches) {
                                List<BlockInfo> nonHatchInfos = new ArrayList<>();
                                List<ItemStack> nonHatchCandidates = new ArrayList<>();
                                int candidateIdx = 0;
                                for (BlockInfo info : infos) {
                                    if (info.getBlockState().getBlock() == Blocks.AIR) continue;
                                    if (!(info.getTileEntity() instanceof IGregTechTileEntity)) {
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
                                                cacheGlobal.getOrDefault(sp, 0) >= sp.maxGlobalCount) {
                                            continue;
                                        }
                                        if (!cacheInfos.containsKey(sp)) {
                                            cacheInfos.put(sp,
                                                    sp.candidates == null ? null : sp.candidates.get());
                                        }
                                        BlockInfo[] spInfos = cacheInfos.get(sp);
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
                                            if (!cacheInfos.containsKey(sp)) {
                                                cacheInfos.put(sp,
                                                        sp.candidates == null ? null : sp.candidates.get());
                                            }
                                            BlockInfo[] spInfos = cacheInfos.get(sp);
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
                                    if (nonHatchInfos.isEmpty()) {
                                        // No non-hatch candidate found anywhere in this position's predicates.
                                        // This is a dedicated hatch position (e.g. muffler).
                                        // Fall through to place the hatch normally.
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
                                }
                            }

                            ItemStack found = null;
                            BlockInfo matchedInfo = null;
                            int requiredAbilityIndex = findRequiredAbilityCandidate(infos, abilityTracker);
                            if (!player.isCreative()) {
                                if (requiredAbilityIndex >= 0) {
                                    ItemStack requiredStack = candidates.get(requiredAbilityIndex);
                                    for (ItemStack itemStack : player.inventory.mainInventory) {
                                        if (requiredStack.isItemEqual(itemStack) && !itemStack.isEmpty()) {
                                            found = itemStack.copy();
                                            itemStack.setCount(itemStack.getCount() - 1);
                                            matchedInfo = infos[requiredAbilityIndex];
                                            break;
                                        }
                                    }
                                    if (found == null) {
                                        found = tryExtractFromAENetwork(
                                                player, Collections.singletonList(requiredStack));
                                        if (found != null) {
                                            matchedInfo = infos[requiredAbilityIndex];
                                        }
                                    }
                                }
                                int preferredIdx = getPreferredChannelCandidateIndex(
                                        matchedPredicate, infos, channelValues);
                                if (found == null && preferredIdx >= 0 && preferredIdx < candidates.size()) {
                                    ItemStack preferredStack = candidates.get(preferredIdx);
                                    for (ItemStack itemStack : player.inventory.mainInventory) {
                                        if (preferredStack.isItemEqual(itemStack) && !itemStack.isEmpty()) {
                                            found = itemStack.copy();
                                            itemStack.setCount(itemStack.getCount() - 1);
                                            matchedInfo = infos[preferredIdx];
                                            break;
                                        }
                                    }
                                    if (found == null) {
                                        found = tryExtractFromAENetwork(
                                                player, Collections.singletonList(preferredStack));
                                        if (found != null) {
                                            matchedInfo = infos[preferredIdx];
                                        }
                                    }
                                    if (found == null) continue;
                                }
                                if (found == null) {
                                    for (int i = 0; i < candidates.size(); i++) {
                                        ItemStack candidate = candidates.get(i);
                                        for (ItemStack itemStack : player.inventory.mainInventory) {
                                            if (candidate.isItemEqual(itemStack) && !itemStack.isEmpty()) {
                                                found = itemStack.copy();
                                                itemStack.setCount(itemStack.getCount() - 1);
                                                matchedInfo = infos[i];
                                                break;
                                            }
                                        }
                                        if (found != null) break;
                                    }
                                }
                                if (found == null) {
                                    found = tryExtractFromAENetwork(player, candidates);
                                    if (found != null) {
                                        for (int i = 0; i < candidates.size(); i++) {
                                            if (candidates.get(i).isItemEqual(found)) {
                                                matchedInfo = infos[i];
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (found == null || matchedInfo == null) continue;
                            } else {
                                int preferredIndex = requiredAbilityIndex;
                                if (preferredIndex < 0) {
                                    int channelIndex = getPreferredChannelCandidateIndex(
                                            matchedPredicate, infos, channelValues);
                                    if (channelIndex >= 0) {
                                        preferredIndex = channelIndex;
                                    }
                                }
                                if (preferredIndex >= 0 && preferredIndex < candidates.size()) {
                                    found = candidates.get(preferredIndex).copy();
                                    matchedInfo = infos[preferredIndex];
                                }
                                if (found == null) {
                                    for (int i = candidates.size() - 1; i >= 0; i--) {
                                        found = candidates.get(i).copy();
                                        if (!found.isEmpty()) {
                                            matchedInfo = infos[i];
                                            break;
                                        }
                                        found = null;
                                    }
                                }
                                if (found == null || matchedInfo == null) continue;
                            }

                            IBlockState state = matchedInfo.getBlockState();
                            if (abilityTracker != null) {
                                abilityTracker.record(matchedInfo);
                            }
                            if (matchedInfo instanceof ExplicitFrontFacingBlockInfo explicitInfo) {
                                explicitFrontFacings.put(pos, explicitInfo.getFrontFacing(controllerBase));
                            }
                            blocks.put(pos, state);
                            world.setBlockState(pos, state);
                            if (matchedInfo.getTileEntity() instanceof IGregTechTileEntity igtteInfo) {
                                TileEntity holder = world.getTileEntity(pos);
                                if (holder instanceof IGregTechTileEntity igtte) {
                                    MetaTileEntity sampleMetaTileEntity = igtteInfo.getMetaTileEntity();
                                    if (sampleMetaTileEntity != null) {
                                        MetaTileEntity metaTileEntity = igtte.setMetaTileEntity(
                                                sampleMetaTileEntity, null, found.getTagCompound());
                                        metaTileEntity.onPlacement(player);
                                        blocks.put(pos, metaTileEntity);
                                    }
                                }
                            }
                        }
                    }
                }
                z++;
            }
        }
        EnumFacing[] facings = ArrayUtils.addAll(new EnumFacing[] { controllerBase.getFrontFacing() },
                RelativeDirection.ALL_FACINGS);
        blocks.forEach((pos, block) -> {
            if (block instanceof MetaTileEntity) {
                // Do not reassign the controller's front facing — it was set by the
                // player and must remain stable across multi-slice auto-build calls.
                if (block == controllerBase) return;
                MetaTileEntity metaTileEntity = (MetaTileEntity) block;
                EnumFacing explicitFrontFacing = explicitFrontFacings.get(pos);
                if (explicitFrontFacing != null && metaTileEntity.isValidFrontFacing(explicitFrontFacing)) {
                    metaTileEntity.setFrontFacing(explicitFrontFacing);
                    return;
                }
                boolean find = false;
                for (EnumFacing enumFacing : facings) {
                    if (metaTileEntity.isValidFrontFacing(enumFacing)) {
                        if (!blocks.containsKey(pos.offset(enumFacing))) {
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
        Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        BlockPatternTemplate.CenterOffset centerOffset = template.getCenterOffset();

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

        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();
        RelativeDirection[] structureDir = template.getStructureDir();

        int minZ = -centerOffset.maxZ();
        for (int c = 0, z = minZ, r; c < fingerLength; c++) {
            int repetitions = (formedRepetitionCount != null && c < formedRepetitionCount.length)
                    ? formedRepetitionCount[c]
                    : template.getAisles()[c].minRepeat();

            for (r = 0; r < repetitions; r++) {
                for (int b = 0, y = -centerOffset.y(); b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset.x(); a < palmLength; a++, x++) {
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(
                                        x, y, z, frontFacing, upwardsFacing, isFlipped, structureDir)
                                .add(centerPos);

                        if (pos.equals(centerPos)) continue;

                        if (world != null && world.isBlockLoaded(pos)) {
                            TileEntity tileEntity = world.getTileEntity(pos);
                            IBlockState blockState = world.getBlockState(pos);
                            if (blockState.getBlock() != Blocks.AIR) {
                                blocks.put(pos, new BlockInfo(blockState, tileEntity));
                            }
                        }
                    }
                }
                z++;
            }
        }
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
        IStructureElement<?>[][][] elements = template.getElements();
        RelativeDirection[] structureDir = template.getStructureDir();
        int fingerLength = template.getZLength();
        int thumbLength = template.getYLength();
        int palmLength = template.getXLength();

        Map<TraceabilityPredicate.SimplePredicate, BlockInfo[]> cacheInfos = new HashMap<>();
        Map<TraceabilityPredicate.SimplePredicate, Integer> cacheGlobal = new HashMap<>();
        Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int l = 0, x = 0; l < fingerLength; l++) {
            for (int r = 0; r < repetition[l]; r++) {
                Map<TraceabilityPredicate.SimplePredicate, Integer> cacheLayer = new HashMap<>();
                for (int y = 0; y < thumbLength; y++) {
                    for (int z = 0; z < palmLength; z++) {
                        IStructureElement<?> element = elements[l][y][z];
                        TraceabilityPredicate predicate = element.toPredicate();
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
                                if (cacheGlobal.getOrDefault(limit, 0) < limit.previewCount) {
                                    if (!cacheGlobal.containsKey(limit)) {
                                        cacheGlobal.put(limit, 1);
                                    } else if (cacheGlobal.get(limit) < limit.previewCount) {
                                        cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                    } else {
                                        continue;
                                    }
                                }
                            } else {
                                continue;
                            }
                            if (!cacheInfos.containsKey(limit)) {
                                cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
                            }
                            infos = cacheInfos.get(limit);
                            matchedPredicate = limit;
                            find = true;
                            break;
                        }
                        if (!find) {
                            for (TraceabilityPredicate.SimplePredicate limit : predicate.limited) {
                                if (limit.minGlobalCount == -1 && limit.previewCount == -1) continue;
                                if (cacheGlobal.getOrDefault(limit, 0) < limit.previewCount) {
                                    if (!cacheGlobal.containsKey(limit)) {
                                        cacheGlobal.put(limit, 1);
                                    } else if (cacheGlobal.get(limit) < limit.previewCount) {
                                        cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                    } else {
                                        continue;
                                    }
                                } else if (limit.minGlobalCount > 0) {
                                    if (!cacheGlobal.containsKey(limit)) {
                                        cacheGlobal.put(limit, 1);
                                    } else if (cacheGlobal.get(limit) < limit.minGlobalCount) {
                                        cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                if (!cacheInfos.containsKey(limit)) {
                                    cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
                                }
                                infos = cacheInfos.get(limit);
                                matchedPredicate = limit;
                                find = true;
                                break;
                            }
                        }
                        if (!find) {
                            for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
                                if (common.previewCount > 0) {
                                    if (!cacheGlobal.containsKey(common)) {
                                        cacheGlobal.put(common, 1);
                                    } else if (cacheGlobal.get(common) < common.previewCount) {
                                        cacheGlobal.put(common, cacheGlobal.get(common) + 1);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                if (!cacheInfos.containsKey(common)) {
                                    cacheInfos.put(common, common.candidates == null ? null : common.candidates.get());
                                }
                                infos = cacheInfos.get(common);
                                matchedPredicate = common;
                                find = true;
                                break;
                            }
                        }
                        if (!find) {
                            for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
                                if (common.previewCount == -1) {
                                    if (!cacheInfos.containsKey(common)) {
                                        cacheInfos.put(common,
                                                common.candidates == null ? null : common.candidates.get());
                                    }
                                    infos = cacheInfos.get(common);
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
                                    if (cacheGlobal.getOrDefault(limit, 0) < limit.maxGlobalCount) {
                                        if (!cacheGlobal.containsKey(limit)) {
                                            cacheGlobal.put(limit, 1);
                                        } else {
                                            cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
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

                                if (!cacheInfos.containsKey(limit)) {
                                    cacheInfos.put(limit, limit.candidates == null ? null : limit.candidates.get());
                                }
                                infos = cacheInfos.get(limit);
                                matchedPredicate = limit;
                                break;
                            }
                        }
                        int candidateIdx = getChannelCandidateIndex(matchedPredicate, infos, channelValues);
                        BlockInfo info = infos == null || infos.length == 0 ? BlockInfo.EMPTY : infos[candidateIdx];
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(z, y, x, EnumFacing.SOUTH,
                                EnumFacing.UP, false, structureDir);
                        if (info.getTileEntity() instanceof MetaTileEntityHolder) {
                            MetaTileEntityHolder holder = new MetaTileEntityHolder();
                            holder.setMetaTileEntity(
                                    ((MetaTileEntityHolder) info.getTileEntity()).getMetaTileEntity());
                            holder.getMetaTileEntity().onPlacement();
                            if (info instanceof ExplicitFrontFacingBlockInfo explicitInfo) {
                                info = new ExplicitFrontFacingBlockInfo(
                                        holder.getMetaTileEntity().getBlock().getDefaultState(), holder,
                                        controller -> explicitInfo.getFrontFacing(controller));
                            } else {
                                info = new BlockInfo(holder.getMetaTileEntity().getBlock().getDefaultState(), holder);
                            }
                        }
                        blocks.put(pos, info);
                        minX = Math.min(pos.getX(), minX);
                        minY = Math.min(pos.getY(), minY);
                        minZ = Math.min(pos.getZ(), minZ);
                        maxX = Math.max(pos.getX(), maxX);
                        maxY = Math.max(pos.getY(), maxY);
                        maxZ = Math.max(pos.getZ(), maxZ);
                    }
                }
                x++;
            }
        }
        BlockInfo[][][] result = (BlockInfo[][][]) Array.newInstance(BlockInfo.class, maxX - minX + 1, maxY - minY + 1,
                maxZ - minZ + 1);
        int finalMinX = minX;
        int finalMinY = minY;
        int finalMinZ = minZ;
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
            result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info;
        });
        return result;
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
                        z + zOffset, frontFacing, upwardsFacing,
                        isFlipped, structureDir)
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
        return checkPatternAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing, isFlipped, 0, 0, 0);
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
        return checkPatternAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing, isFlipped,
                xOffset, yOffset, zOffset, null);
    }

    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos, EnumFacing frontFacing,
                                                       EnumFacing upwardsFacing, boolean isFlipped,
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
                                z + zOffset, frontFacing, upwardsFacing,
                                isFlipped, structureDir)
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
        activeContext.setNeededFlip(isFlipped);
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
        return checkPatternAtSnapshot(
                blockAccess, centerPos, frontFacing, upwardsFacing, isFlipped,
                xOffset, yOffset, zOffset, session);
    }
}

