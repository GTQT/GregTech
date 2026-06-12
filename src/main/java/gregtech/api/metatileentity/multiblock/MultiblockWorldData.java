package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.MultiPiecePattern;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages event-driven structure checking for formed multiblocks.
 * Instead of periodic polling, formed multiblocks register their block positions
 * and only re-validate when a block change occurs within their structure.
 *
 * Uses a ChunkPos-based index for O(1) lookup of affected multiblocks.
 */
public class MultiblockWorldData {

    // Thread-safe: Forge events may fire from non-main threads (e.g., async chunk loading)
    private static final Map<World, MultiblockWorldData> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Minimum server ticks between successive structure re-checks for the same controller.
     * Prevents per-tick full pattern scans when a player rapidly breaks/places blocks.
     * At 20 TPS this means at most one full check per 5 ticks (250 ms).
     */
    private static final int RECHECK_COOLDOWN_TICKS = 5;

    /** ChunkPos -> Set of controllers that have blocks in this chunk */
    private final Map<ChunkPos, Set<MultiblockControllerBase>> chunkIndex = new ConcurrentHashMap<>();

    /** Controller -> Set of block positions (as longs) belonging to this controller's structure */
    private final Map<MultiblockControllerBase, LongSet> controllerPositions = new ConcurrentHashMap<>();

    /** Controller -> MultiPiecePattern for piece-level dirty marking (P3) */
    private final Map<MultiblockControllerBase, MultiPiecePattern> controllerPiecePatterns = new ConcurrentHashMap<>();

    /** Controllers that have been notified of a block change and need re-validation on next tick */
    private final Set<MultiblockControllerBase> pendingRecheck = ConcurrentHashMap.newKeySet();

    /** Monotonic revision assigned to each changed chunk for async snapshot validation. */
    private final AtomicLong changeRevision = new AtomicLong();
    private final Map<ChunkPos, Long> chunkChangeRevisions = new ConcurrentHashMap<>();

    /**
     * Controllers that are currently suppressing event-driven recheck notifications.
     * Used by multiblocks (e.g., Forge of Gods) that intentionally modify blocks within
     * their own structure (e.g., replacing rings with air for rendering). Block changes
     * during suppression are silently ignored instead of triggering a recheck.
     */
    private final Set<MultiblockControllerBase> suppressedControllers = ConcurrentHashMap.newKeySet();

    /**
     * Controller -> server tick at which the last block-change event was received.
     * Used together with {@link #RECHECK_COOLDOWN_TICKS} to debounce rapid block changes.
     */
    private final Map<MultiblockControllerBase, Long> lastChangedTick = new ConcurrentHashMap<>();

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
     * Register a formed multiblock with multi-piece pattern for piece-level dirty tracking.
     * The combined positions from all active pieces are used for the chunk index.
     *
     * @param controller   the multiblock controller
     * @param positions    the combined set of block positions (as longs) across all pieces
     * @param piecePattern the multi-piece pattern for piece-level dirty marking
     */
    public void registerMultiblock(MultiblockControllerBase controller, LongSet positions,
                                   MultiPiecePattern piecePattern) {
        registerMultiblock(controller, positions);
        controllerPiecePatterns.put(controller, piecePattern);
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

        controllerPiecePatterns.remove(controller);
        pendingRecheck.remove(controller);
        lastChangedTick.remove(controller);
    }

    /**
     * Called when a block changes in the world.
     * Checks if any formed multiblock has this position registered
     * and marks it for re-validation.
     *
     * For multi-piece controllers, only the affected piece(s) are marked dirty
     * instead of the entire controller (P3 piece-level dirty tracking).
     *
     * @param pos      the position where a block changed
     * @param gameTick the current server tick (from {@code world.getTotalWorldTime()})
     * @return true if at least one registered multiblock was affected by this position
     */
    public boolean onBlockChanged(BlockPos pos, long gameTick) {
        chunkChangeRevisions.put(
                new ChunkPos(pos), changeRevision.incrementAndGet());

        ChunkPos chunkPos = new ChunkPos(pos);
        Set<MultiblockControllerBase> controllers = chunkIndex.get(chunkPos);
        if (controllers == null || controllers.isEmpty()) return false;

        long posLong = pos.toLong();
        boolean affected = false;
        for (MultiblockControllerBase controller : controllers) {
            LongSet positions = controllerPositions.get(controller);
            if (positions != null && positions.contains(posLong)) {
                // Skip controllers that are suppressing recheck (e.g., during ring replacement)
                if (suppressedControllers.contains(controller)) {
                    affected = true;
                    continue;
                }
                // Check if this controller uses multi-piece pattern
                MultiPiecePattern piecePattern = controllerPiecePatterns.get(controller);
                if (piecePattern != null) {
                    // Piece-level dirty marking: only mark the specific piece(s) containing this position
                    // The per-controller PieceRuntimes carries the per-piece position sets and dirty flags
                    piecePattern.markDirtyByPosition(posLong, controller.getPieceRuntimes(), controller);
                }
                // Record the tick of this change; doStructureCheck() will debounce using this value
                lastChangedTick.put(controller, gameTick);
                pendingRecheck.add(controller);
                affected = true;
            }
        }
        return affected;
    }

    /**
     * Capture the change revisions for every chunk touched by a snapshot AABB.
     */
    ChangeSnapshot captureChangeSnapshot(BlockPos minCorner, BlockPos maxCorner) {
        Map<ChunkPos, Long> revisions = new HashMap<>();
        int minChunkX = minCorner.getX() >> 4;
        int maxChunkX = maxCorner.getX() >> 4;
        int minChunkZ = minCorner.getZ() >> 4;
        int maxChunkZ = maxCorner.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                revisions.put(chunk, chunkChangeRevisions.getOrDefault(chunk, 0L));
            }
        }
        return new ChangeSnapshot(revisions);
    }

    boolean isChangeSnapshotCurrent(ChangeSnapshot snapshot) {
        for (Map.Entry<ChunkPos, Long> entry : snapshot.revisions.entrySet()) {
            if (!entry.getValue().equals(
                    chunkChangeRevisions.getOrDefault(entry.getKey(), 0L))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a controller has a pending re-check due to a block change event,
     * applying a cooldown so that rapid consecutive changes collapse into one check.
     *
     * <p>Returns {@code true} (and clears the pending flag) only when both:
     * <ul>
     *   <li>the controller is in the pending set, AND</li>
     *   <li>at least {@link #RECHECK_COOLDOWN_TICKS} ticks have elapsed since the last change</li>
     * </ul>
     *
     * @param controller the controller to check
     * @param currentTick the current server tick (from {@code world.getTotalWorldTime()})
     * @return true if the controller should be re-validated this tick
     */
    public boolean hasPendingRecheck(MultiblockControllerBase controller, long currentTick) {
        if (!pendingRecheck.contains(controller)) return false;

        Long changed = lastChangedTick.get(controller);
        if (changed != null && (currentTick - changed) < RECHECK_COOLDOWN_TICKS) {
            // Still within the cooldown window — defer the check
            return false;
        }

        // Cooldown expired: consume the pending flag and clear the tick record
        pendingRecheck.remove(controller);
        lastChangedTick.remove(controller);
        return true;
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
        controllerPiecePatterns.clear();
        pendingRecheck.clear();
        lastChangedTick.clear();
        chunkChangeRevisions.clear();
        changeRevision.set(0);
        suppressedControllers.clear();
    }

    static final class ChangeSnapshot {

        private final Map<ChunkPos, Long> revisions;

        private ChangeSnapshot(Map<ChunkPos, Long> revisions) {
            this.revisions = Collections.unmodifiableMap(new HashMap<>(revisions));
        }
    }

    /**
     * Suppress event-driven recheck notifications for the given controller.
     * While suppressed, block changes within the controller's registered positions
     * will be silently ignored. Call {@link #unsuppressRecheck} after the modification
     * is complete.
     *
     * @param controller the controller to suppress
     */
    public void suppressRecheck(MultiblockControllerBase controller) {
        suppressedControllers.add(controller);
    }

    /**
     * Re-enable event-driven recheck notifications for the given controller.
     * Also clears any pending recheck that may have been queued before suppression.
     *
     * @param controller the controller to unsuppress
     */
    public void unsuppressRecheck(MultiblockControllerBase controller) {
        suppressedControllers.remove(controller);
        pendingRecheck.remove(controller);
        lastChangedTick.remove(controller);
    }
}
