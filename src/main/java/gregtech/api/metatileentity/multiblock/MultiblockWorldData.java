package gregtech.api.metatileentity.multiblock;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages event-driven structure checking for formed multiblocks.
 * Instead of periodic polling, formed multiblocks register their block positions
 * and only re-validate when a block change occurs within their structure.
 *
 * Uses a ChunkPos-based index for O(1) lookup of affected multiblocks.
 */
public class MultiblockWorldData {

    private static final Map<World, MultiblockWorldData> INSTANCES = new WeakHashMap<>();

    /** ChunkPos -> Set of controllers that have blocks in this chunk */
    private final Map<ChunkPos, Set<MultiblockControllerBase>> chunkIndex = new ConcurrentHashMap<>();

    /** Controller -> Set of block positions (as longs) belonging to this controller's structure */
    private final Map<MultiblockControllerBase, LongSet> controllerPositions = new ConcurrentHashMap<>();

    /** Controllers that have been notified of a block change and need re-validation on next tick */
    private final Set<MultiblockControllerBase> pendingRecheck = ConcurrentHashMap.newKeySet();

    public static MultiblockWorldData get(World world) {
        return INSTANCES.computeIfAbsent(world, w -> new MultiblockWorldData());
    }

    public static void remove(World world) {
        INSTANCES.remove(world);
    }

    /**
     * Register a formed multiblock's block positions for event-driven checking.
     * Called when a multiblock successfully forms.
     *
     * @param controller the multiblock controller
     * @param positions  the set of block positions (as longs) in the structure
     */
    public void registerMultiblock(MultiblockControllerBase controller, LongSet positions) {
        controllerPositions.put(controller, positions);

        for (long pos : positions) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.fromLong(pos));
            chunkIndex.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet())
                    .add(controller);
        }
    }

    /**
     * Unregister a multiblock when it invalidates.
     * Called when a multiblock structure breaks.
     *
     * @param controller the multiblock controller
     */
    public void unregisterMultiblock(MultiblockControllerBase controller) {
        LongSet positions = controllerPositions.remove(controller);
        if (positions == null) return;

        for (long pos : positions) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.fromLong(pos));
            Set<MultiblockControllerBase> controllers = chunkIndex.get(chunkPos);
            if (controllers != null) {
                controllers.remove(controller);
                if (controllers.isEmpty()) {
                    chunkIndex.remove(chunkPos);
                }
            }
        }

        pendingRecheck.remove(controller);
    }

    /**
     * Called when a block changes in the world.
     * Checks if any formed multiblock has this position registered
     * and marks it for re-validation.
     *
     * @param pos the position where a block changed
     */
    public void onBlockChanged(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Set<MultiblockControllerBase> controllers = chunkIndex.get(chunkPos);
        if (controllers == null || controllers.isEmpty()) return;

        long posLong = pos.toLong();
        for (MultiblockControllerBase controller : controllers) {
            LongSet positions = controllerPositions.get(controller);
            if (positions != null && positions.contains(posLong)) {
                pendingRecheck.add(controller);
            }
        }
    }

    /**
     * Check if a controller has a pending re-check due to a block change event.
     *
     * @param controller the controller to check
     * @return true if the controller needs re-validation
     */
    public boolean hasPendingRecheck(MultiblockControllerBase controller) {
        return pendingRecheck.remove(controller);
    }

    /**
     * Check if a controller is registered for event-driven checking.
     *
     * @param controller the controller to check
     * @return true if the controller is registered
     */
    public boolean isRegistered(MultiblockControllerBase controller) {
        return controllerPositions.containsKey(controller);
    }

    /**
     * Get all registered positions for a controller.
     *
     * @param controller the controller
     * @return the set of positions, or empty set if not registered
     */
    public LongSet getPositions(MultiblockControllerBase controller) {
        LongSet positions = controllerPositions.get(controller);
        return positions != null ? positions : new LongOpenHashSet();
    }

    /**
     * Clear all data. Called when a world is unloaded.
     */
    public void clear() {
        chunkIndex.clear();
        controllerPositions.clear();
        pendingRecheck.clear();
    }
}
