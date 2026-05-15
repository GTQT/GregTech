package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.Mods;
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
 * the immutable {@link BlockPatternTemplate} with other controllers of the same type.
 *
 * This class holds:
 * - The block position cache (formed structure positions)
 * - Pattern match context (runtime matching results)
 * - Global/layer predicate counters
 * - The BlockWorldState used during pattern checking
 * - Formed repetition counts
 * - A ReentrantLock for future async checking support (P2)
 *
 * @see BlockPatternTemplate for the shared immutable template
 */
public class MultiblockState {

    private final BlockPatternTemplate template;

    // --- Per-instance mutable state ---

    protected final BlockWorldState worldState = new BlockWorldState();
    protected final PatternMatchContext matchContext = new PatternMatchContext();
    protected final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount = new HashMap<>();
    protected final Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount = new HashMap<>();

    /** Cache of formed structure block positions -> block info */
    public final Long2ObjectMap<BlockInfo> cache = new Long2ObjectOpenHashMap<>();

    /** The repetitions per aisle along the axis of repetition (filled after successful pattern check) */
    public int[] formedRepetitionCount;

    /** Lock for thread-safe pattern checking (preparation for P2 async checking) */
    private final ReentrantLock lock = new ReentrantLock();

    public MultiblockState(@NotNull BlockPatternTemplate template) {
        this.template = template;
        this.formedRepetitionCount = new int[template.getAisleRepetitions().length];
    }

    /**
     * @return the immutable template this state is bound to
     */
    public BlockPatternTemplate getTemplate() {
        return template;
    }

    /**
     * @return the current pattern error, or null if no error
     */
    public PatternError getError() {
        return worldState.error;
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
    }

    /**
     * Fast pattern check using cache, then full check if needed.
     */
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip) {
        return checkPatternFastAt(world, centerPos, frontFacing, upwardsFacing, allowsFlip, true);
    }

    /**
     * Fast pattern check using cache, then full check if needed.
     *
     * @param doRandomCheck if true and cache is large (>512), use random sampling instead of full scan
     */
    public PatternMatchContext checkPatternFastAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                                  EnumFacing upwardsFacing, boolean allowsFlip,
                                                  boolean doRandomCheck) {
        if (!cache.isEmpty()) {
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

        PatternMatchContext pmc = checkPatternAt(world, centerPos, frontFacing, upwardsFacing, false);
        if (allowsFlip) {
            if (pmc != null) {
                return pmc;
            }
            pmc = checkPatternAt(world, centerPos, frontFacing, upwardsFacing, true);
        }
        if (pmc == null) clearCache();
        return pmc;
    }

    private PatternMatchContext checkPatternAt(World world, BlockPos centerPos, EnumFacing frontFacing,
                                               EnumFacing upwardsFacing, boolean isFlipped) {
        TraceabilityPredicate[][][] blockMatches = template.getBlockMatches();
        int[][] aisleRepetitions = template.getAisleRepetitions();
        RelativeDirection[] structureDir = template.getStructureDir();
        int[] centerOffset = template.getCenterOffset();
        int fingerLength = template.getFingerLength();
        int thumbLength = template.getThumbLength();
        int palmLength = template.getPalmLength();

        boolean findFirstAisle = false;
        int minZ = -centerOffset[4];

        this.matchContext.reset();
        this.globalCount.clear();
        this.layerCount.clear();
        cache.clear();

        // Checking aisles
        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset[3]); r++) {
                // Checking single slice
                this.layerCount.clear();

                for (int b = 0, y = -centerOffset[1]; b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < palmLength; a++, x++) {
                        TraceabilityPredicate predicate = blockMatches[c][b][a];
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(x, y, z, frontFacing, upwardsFacing,
                                isFlipped, structureDir)
                                .add(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        worldState.update(world, pos, matchContext, globalCount, layerCount, predicate);
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
                        if (!predicate.test(worldState)) {
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {
                                    r = c = 0;
                                    z = minZ++;
                                    matchContext.reset();
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

        // Check count matches amount
        for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : globalCount.entrySet()) {
            if (entry.getValue() < entry.getKey().minGlobalCount) {
                worldState.setError(new TraceabilityPredicate.SinglePredicateError(entry.getKey(), 1));
                return null;
            }
        }

        worldState.setError(null);
        matchContext.setNeededFlip(isFlipped);
        return matchContext;
    }

    /**
     * Calculate repetitions per aisle from channel values.
     * {@code STRUCTURE_HEIGHT} controls the first repeatable aisle,
     * {@code STRUCTURE_LENGTH} controls the second (if exists).
     * Value semantics: 0 = max, 1 = min, 2+ = specific (clamped to [min, max]).
     * If a channel is not set, defaults to max repetition for that aisle.
     *
     * @param channelValues map of channel name -> value (null = all max)
     * @return repetitions array
     */
    /**
     * Calculate aisle repetitions from channel values.
     * Uses aisleChannelNames to match channel names to specific aisles (consistent with repetitionDFS).
     * Aisles without an assigned channel name or without a matching value in channelValues default to min repetition,
     * consistent with the preview path (repetitionDFS generates shapes starting from min).
     */
    private int[] calculateRepetitionsFromChannels(Map<String, Integer> channelValues) {
        int[][] aisleRepetitions = template.getAisleRepetitions();
        String[] aisleChannelNames = template.getAisleChannelNames();
        int[] repetitions = new int[aisleRepetitions.length];

        for (int i = 0; i < aisleRepetitions.length; i++) {
            // Default to min repetition (consistent with preview showing min variant)
            repetitions[i] = aisleRepetitions[i][0];
        }

        if (channelValues == null || channelValues.isEmpty()) {
            return repetitions;
        }

        for (int i = 0; i < aisleRepetitions.length; i++) {
            // Skip non-repeatable aisles
            if (aisleRepetitions[i][0] == aisleRepetitions[i][1]) continue;

            String channelName = (aisleChannelNames != null && i < aisleChannelNames.length)
                    ? aisleChannelNames[i] : null;

            if (channelName != null && channelValues.containsKey(channelName)) {
                int value = channelValues.get(channelName);
                repetitions[i] = Math.min(Math.max(value, aisleRepetitions[i][0]), aisleRepetitions[i][1]);
            }
        }

        return repetitions;
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
            int[][] aisleReps = template.getAisleRepetitions();
            String[] channelNames = template.getAisleChannelNames();
            for (int i = 0; i < aisleReps.length; i++) {
                if (aisleReps[i][0] == aisleReps[i][1]) continue;
                String name = (channelNames != null && i < channelNames.length) ? channelNames[i] : null;
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
     *   <li><b>Structure dimensions</b>: {@code STRUCTURE_HEIGHT} and {@code STRUCTURE_LENGTH}
     *       control aisle repetition counts (0 = max, 1 = min, 2+ = specific).</li>
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
        TraceabilityPredicate[][][] blockMatches = template.getBlockMatches();
        int[][] aisleRepetitions = template.getAisleRepetitions();
        RelativeDirection[] structureDir = template.getStructureDir();
        int[] centerOffset = template.getCenterOffset();
        int fingerLength = template.getFingerLength();
        int thumbLength = template.getThumbLength();
        int palmLength = template.getPalmLength();

        World world = player.world;
        BlockWorldState bws = new BlockWorldState();
        int minZ = -centerOffset[4];
        EnumFacing facing = controllerBase.getFrontFacing().getOpposite();
        Map<TraceabilityPredicate.SimplePredicate, BlockInfo[]> cacheInfos = new HashMap<>();
        Map<TraceabilityPredicate.SimplePredicate, Integer> cacheGlobal = new HashMap<>();
        Map<BlockPos, Object> blocks = new HashMap<>();
        blocks.put(controllerBase.getPos(), controllerBase);

        int[] repetitions = calculateRepetitionsFromChannels(channelValues);

        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            for (r = 0; r < repetitions[c]; r++) {
                Map<TraceabilityPredicate.SimplePredicate, Integer> cacheLayer = new HashMap<>();
                for (int b = 0, y = -centerOffset[1]; b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < palmLength; a++, x++) {
                        TraceabilityPredicate predicate = blockMatches[c][b][a];
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(x, y, z, facing,
                                controllerBase.getUpwardsFacing(),
                                controllerBase.isFlipped(), structureDir)
                                .add(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        bws.update(world, pos, matchContext, globalCount, layerCount, predicate);
                        if (!world.getBlockState(pos).getMaterial().isReplaceable()) {
                            blocks.put(pos, world.getBlockState(pos));
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

                            List<ItemStack> candidates = Arrays.stream(infos)
                                    .filter(info -> info.getBlockState().getBlock() != Blocks.AIR).map(info -> {
                                        IBlockState blockState = info.getBlockState();
                                        MetaTileEntity metaTileEntity = info
                                                .getTileEntity() instanceof IGregTechTileEntity ?
                                                        ((IGregTechTileEntity) info.getTileEntity())
                                                                .getMetaTileEntity() :
                                                        null;
                                        if (metaTileEntity != null) {
                                            return metaTileEntity.getStackForm();
                                        } else {
                                            return new ItemStack(Item.getItemFromBlock(blockState.getBlock()), 1,
                                                    blockState.getBlock().damageDropped(blockState));
                                        }
                                    }).collect(Collectors.toList());
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
                                    matchedPredicate = null;
                                }
                            }

                            ItemStack found = null;
                            BlockInfo matchedInfo = null;
                            if (!player.isCreative()) {
                                int preferredIdx = getChannelCandidateIndex(matchedPredicate, infos, channelValues);
                                if (preferredIdx > 0 && preferredIdx < candidates.size()) {
                                    ItemStack preferredStack = candidates.get(preferredIdx);
                                    for (ItemStack itemStack : player.inventory.mainInventory) {
                                        if (preferredStack.isItemEqual(itemStack) && !itemStack.isEmpty()) {
                                            found = itemStack.copy();
                                            itemStack.setCount(itemStack.getCount() - 1);
                                            matchedInfo = infos[preferredIdx];
                                            break;
                                        }
                                    }
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
                                int preferredIndex = getChannelCandidateIndex(matchedPredicate, infos, channelValues);
                                if (preferredIndex > 0 && preferredIndex < candidates.size()) {
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
                BlockPatternTemplate.FACINGS);
        blocks.forEach((pos, block) -> {
            if (block instanceof MetaTileEntity) {
                MetaTileEntity metaTileEntity = (MetaTileEntity) block;
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
                    for (EnumFacing enumFacing : BlockPatternTemplate.FACINGS) {
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

    /**
     * Get all structure blocks (from cache or by calculating positions).
     */
    public Map<BlockPos, BlockInfo> getAllStructureBlocks(World world, BlockPos centerPos,
                                                          EnumFacing frontFacing, EnumFacing upwardsFacing,
                                                          boolean isFlipped) {
        Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        int[] centerOffset = template.getCenterOffset();

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

        int fingerLength = template.getFingerLength();
        int thumbLength = template.getThumbLength();
        int palmLength = template.getPalmLength();
        RelativeDirection[] structureDir = template.getStructureDir();

        int minZ = -centerOffset[4];
        for (int c = 0, z = minZ, r; c < fingerLength; c++) {
            int repetitions = (formedRepetitionCount != null && c < formedRepetitionCount.length)
                    ? formedRepetitionCount[c]
                    : template.getAisleRepetitions()[c][0];

            for (r = 0; r < repetitions; r++) {
                for (int b = 0, y = -centerOffset[1]; b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < palmLength; a++, x++) {
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
        TraceabilityPredicate[][][] blockMatches = template.getBlockMatches();
        RelativeDirection[] structureDir = template.getStructureDir();
        int fingerLength = template.getFingerLength();
        int thumbLength = template.getThumbLength();
        int palmLength = template.getPalmLength();

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
                        TraceabilityPredicate predicate = blockMatches[l][y][z];
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
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(z, y, x, EnumFacing.NORTH,
                                EnumFacing.UP, false, structureDir);
                        if (info.getTileEntity() instanceof MetaTileEntityHolder) {
                            MetaTileEntityHolder holder = new MetaTileEntityHolder();
                            holder.setMetaTileEntity(
                                    ((MetaTileEntityHolder) info.getTileEntity()).getMetaTileEntity());
                            holder.getMetaTileEntity().onPlacement();
                            info = new BlockInfo(holder.getMetaTileEntity().getBlock().getDefaultState(), holder);
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
                boolean find = false;
                for (EnumFacing enumFacing : BlockPatternTemplate.FACINGS) {
                    if (metaTileEntity.isValidFrontFacing(enumFacing)) {
                        if (!blocks.containsKey(pos.offset(enumFacing))) {
                            metaTileEntity.setFrontFacing(enumFacing);
                            find = true;
                            break;
                        }
                    }
                }
                if (!find) {
                    for (EnumFacing enumFacing : BlockPatternTemplate.FACINGS) {
                        BlockInfo blockInfo = blocks.get(pos.offset(enumFacing));
                        if (blockInfo != null && blockInfo.getBlockState().getBlock() == Blocks.AIR &&
                                metaTileEntity.isValidFrontFacing(enumFacing)) {
                            metaTileEntity.setFrontFacing(enumFacing);
                            break;
                        }
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
        if (predicate == null || infos == null || infos.length == 0) return 0;
        if (channelValues == null || predicate.channelName == null) return 0;
        Integer cv = channelValues.get(predicate.channelName);
        if (cv == null || cv <= 0) return 0;
        int idx = cv - 1;
        return idx < infos.length ? idx : 0;
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
        // For snapshot checks, we skip the cache fast-path and do a full pattern check
        PatternMatchContext pmc = checkPatternAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing, false);
        if (allowsFlip) {
            if (pmc != null) {
                return pmc;
            }
            pmc = checkPatternAtSnapshot(blockAccess, centerPos, frontFacing, upwardsFacing, true);
        }
        return pmc;
    }

    /**
     * Internal pattern check against a snapshot.
     * Simplified version that uses IBlockAccess instead of World.
     */
    private PatternMatchContext checkPatternAtSnapshot(net.minecraft.world.IBlockAccess blockAccess,
                                                       BlockPos centerPos, EnumFacing frontFacing,
                                                       EnumFacing upwardsFacing, boolean isFlipped) {
        TraceabilityPredicate[][][] blockMatches = template.getBlockMatches();
        int[][] aisleRepetitions = template.getAisleRepetitions();
        RelativeDirection[] structureDir = template.getStructureDir();
        int[] centerOffset = template.getCenterOffset();
        int fingerLength = template.getFingerLength();
        int thumbLength = template.getThumbLength();
        int palmLength = template.getPalmLength();

        boolean findFirstAisle = false;
        int minZ = -centerOffset[4];

        this.matchContext.reset();
        this.globalCount.clear();
        this.layerCount.clear();

        // Checking aisles
        for (int c = 0, z = minZ++, r; c < fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset[3]); r++) {
                // Checking single slice
                this.layerCount.clear();

                for (int b = 0, y = -centerOffset[1]; b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < palmLength; a++, x++) {
                        TraceabilityPredicate predicate = blockMatches[c][b][a];
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(x, y, z, frontFacing, upwardsFacing,
                                isFlipped, structureDir)
                                .add(centerPos.getX(), centerPos.getY(), centerPos.getZ());

                        // Use snapshot-aware update
                        worldState.updateFromBlockAccess(blockAccess, pos, matchContext, globalCount, layerCount,
                                predicate);

                        if (!predicate.test(worldState)) {
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {
                                    r = c = 0;
                                    z = minZ++;
                                    matchContext.reset();
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

        // Check count matches amount
        for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : globalCount.entrySet()) {
            if (entry.getValue() < entry.getKey().minGlobalCount) {
                worldState.setError(new TraceabilityPredicate.SinglePredicateError(entry.getKey(), 1));
                return null;
            }
        }

        worldState.setError(null);
        matchContext.setNeededFlip(isFlipped);
        return matchContext;
    }
}

