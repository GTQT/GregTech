package gregtech.api.pattern;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.common.ConfigHolder;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
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
     * Calculate repetitions per aisle based on a global tier.
     *
     * @param tier global tier:
     *             <ul>
     *             <li>{@code tier <= 0} = max scale (all aisles at max)</li>
     *             <li>{@code tier = 1} = min scale (all aisles at min)</li>
     *             <li>{@code tier >= 2} = progressively expand</li>
     *             </ul>
     * @return repetitions array
     */
    public int[] calculateRepetitionsByTier(int tier) {
        int[][] aisleRepetitions = template.getAisleRepetitions();
        int[] repetitions = new int[aisleRepetitions.length];

        if (tier <= 0) {
            for (int i = 0; i < aisleRepetitions.length; i++) {
                repetitions[i] = aisleRepetitions[i][1];
            }
            return repetitions;
        }

        if (tier == 1) {
            for (int i = 0; i < aisleRepetitions.length; i++) {
                repetitions[i] = aisleRepetitions[i][0];
            }
            return repetitions;
        }

        for (int i = 0; i < aisleRepetitions.length; i++) {
            repetitions[i] = aisleRepetitions[i][0];
        }

        int remaining = tier - 1;
        for (int i = 0; i < aisleRepetitions.length && remaining > 0; i++) {
            int min = aisleRepetitions[i][0];
            int max = aisleRepetitions[i][1];
            int increment = Math.min(max - min, remaining);
            repetitions[i] += increment;
            remaining -= increment;
        }

        return repetitions;
    }

    /**
     * Auto-build the structure in the world.
     */
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase) {
        autoBuild(player, controllerBase, 1);
    }

    /**
     * Auto-build the structure in the world at the given tier.
     */
    public void autoBuild(EntityPlayer player, MultiblockControllerBase controllerBase, int tier) {
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
        BlockPos centerPos = controllerBase.getPos();
        Map<TraceabilityPredicate.SimplePredicate, BlockInfo[]> cacheInfos = new HashMap<>();
        Map<TraceabilityPredicate.SimplePredicate, Integer> cacheGlobal = new HashMap<>();
        Map<BlockPos, Object> blocks = new HashMap<>();
        blocks.put(controllerBase.getPos(), controllerBase);

        int[] repetitions = calculateRepetitionsByTier(tier);

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
                            ItemStack found = null;
                            if (!player.isCreative()) {
                                for (ItemStack itemStack : player.inventory.mainInventory) {
                                    if (candidates.stream().anyMatch(candidate -> candidate.isItemEqual(itemStack)) &&
                                            !itemStack.isEmpty() && itemStack.getItem() instanceof ItemBlock) {
                                        found = itemStack.copy();
                                        itemStack.setCount(itemStack.getCount() - 1);
                                        break;
                                    }
                                }
                                if (found == null) continue;
                            } else {
                                for (int i = candidates.size() - 1; i >= 0; i--) {
                                    found = candidates.get(i).copy();
                                    if (!found.isEmpty() && found.getItem() instanceof ItemBlock) {
                                        break;
                                    }
                                    found = null;
                                }
                                if (found == null) continue;
                            }
                            ItemBlock itemBlock = (ItemBlock) found.getItem();
                            IBlockState state = itemBlock.getBlock()
                                    .getStateFromMeta(itemBlock.getMetadata(found.getMetadata()));
                            blocks.put(pos, state);
                            world.setBlockState(pos, state);
                            TileEntity holder = world.getTileEntity(pos);
                            if (holder instanceof IGregTechTileEntity igtte) {
                                MTERegistry registry = GregTechAPI.mteManager
                                        .getRegistry(found.getItem().getRegistryName().getNamespace());
                                MetaTileEntity sampleMetaTileEntity = registry.getObjectById(found.getItemDamage());
                                if (sampleMetaTileEntity != null) {
                                    MetaTileEntity metaTileEntity = igtte.setMetaTileEntity(sampleMetaTileEntity);
                                    metaTileEntity.onPlacement(player);
                                    blocks.put(pos, metaTileEntity);
                                    if (found.getTagCompound() != null) {
                                        metaTileEntity.initFromItemStackData(found.getTagCompound());
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
                                break;
                            }
                        }
                        BlockInfo info = infos == null || infos.length == 0 ? BlockInfo.EMPTY : infos[0];
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(z, y, x, EnumFacing.NORTH,
                                EnumFacing.UP, false, structureDir);
                        // TODO
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
}
