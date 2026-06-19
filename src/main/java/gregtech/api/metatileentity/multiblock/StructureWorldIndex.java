package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.StructurePositionIndex;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureIncrementalFallbackReason;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-world structure dirty index.
 *
 * <p>World callbacks write to this index only: changed chunks update their
 * snapshot revision and affected controllers/pieces are marked dirty. The
 * scheduler later consumes the dirty status and decides which validation path
 * should run on the server thread.
 */
final class StructureWorldIndex {

    /**
     * Minimum server ticks between successive structure re-checks for the same controller.
     * Prevents per-tick full pattern scans when a player rapidly breaks/places blocks.
     * At 20 TPS this means at most one check per 5 ticks (250 ms).
     */
    private static final int RECHECK_COOLDOWN_TICKS = 5;

    /** ChunkPos -> Set of controllers that have blocks in this chunk. */
    private final Map<ChunkPos, Set<MultiblockControllerBase>> chunkIndex = new ConcurrentHashMap<>();

    /** Controller -> Set of block positions (as longs) belonging to this controller's structure. */
    private final Map<MultiblockControllerBase, LongSet> controllerPositions = new ConcurrentHashMap<>();

    /** Controller -> immutable owner index for watched positions. */
    private final Map<MultiblockControllerBase, StructurePositionIndex> controllerPositionIndexes =
            new ConcurrentHashMap<>();

    /** Controller -> MultiPiecePattern for piece-level dirty marking. */
    private final Map<MultiblockControllerBase, MultiPiecePattern> controllerPiecePatterns =
            new ConcurrentHashMap<>();

    /** Controllers that have been notified of a block change and need scheduler attention. */
    private final Set<MultiblockControllerBase> pendingRecheck = ConcurrentHashMap.newKeySet();

    /** Monotonic revision assigned to each changed chunk for async snapshot validation. */
    private final AtomicLong changeRevision = new AtomicLong();
    private final Map<ChunkPos, Long> chunkChangeRevisions = new ConcurrentHashMap<>();

    /**
     * Controllers currently suppressing event-driven recheck notifications.
     * Used by multiblocks that intentionally modify blocks in their own formed
     * structure during a controlled operation.
     */
    private final Set<MultiblockControllerBase> suppressedControllers = ConcurrentHashMap.newKeySet();

    /** Controller -> server tick at which the last block-change event was received. */
    private final Map<MultiblockControllerBase, Long> lastChangedTick = new ConcurrentHashMap<>();

    void registerMultiblock(@NotNull MultiblockControllerBase controller,
                            @NotNull LongSet positions) {
        controllerPositions.put(controller, positions);

        for (long pos : positions) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.fromLong(pos));
            chunkIndex.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet())
                    .add(controller);
        }
    }

    void registerMultiblock(@NotNull MultiblockControllerBase controller,
                            @NotNull LongSet positions,
                            @NotNull MultiPiecePattern piecePattern) {
        registerMultiblock(controller, positions);
        controllerPiecePatterns.put(controller, piecePattern);
    }

    void registerMultiblock(@NotNull MultiblockControllerBase controller,
                            @NotNull StructurePositionIndex positionIndex,
                            @NotNull MultiPiecePattern piecePattern) {
        controllerPositionIndexes.put(controller, positionIndex);
        registerMultiblock(controller, positionIndex.getAllWatchedPositions());
        controllerPiecePatterns.put(controller, piecePattern);
    }

    void unregisterMultiblock(@NotNull MultiblockControllerBase controller) {
        LongSet positions = controllerPositions.remove(controller);
        if (positions != null) {
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
        }

        controllerPiecePatterns.remove(controller);
        controllerPositionIndexes.remove(controller);
        pendingRecheck.remove(controller);
        lastChangedTick.remove(controller);
        suppressedControllers.remove(controller);
    }

    /**
     * Register a block change in this world's dirty index.
     *
     * @return true if at least one registered multiblock owns this position
     */
    boolean markBlockChanged(@NotNull BlockPos pos, long gameTick) {
        chunkChangeRevisions.put(
                new ChunkPos(pos), changeRevision.incrementAndGet());

        Set<MultiblockControllerBase> controllers = chunkIndex.get(new ChunkPos(pos));
        if (controllers == null || controllers.isEmpty()) {
            return false;
        }

        long posLong = pos.toLong();
        boolean affected = false;
        for (MultiblockControllerBase controller : controllers) {
            LongSet positions = controllerPositions.get(controller);
            if (positions == null || !positions.contains(posLong)) {
                continue;
            }

            affected = true;
            if (suppressedControllers.contains(controller)) {
                continue;
            }

            StructurePositionIndex positionIndex = controllerPositionIndexes.get(controller);
            if (positionIndex != null) {
                StructureRuntime runtime = controller.getStructureRuntime();
                if (runtime != null) {
                    runtime.addDirtyRoots(positionIndex.getOwners(posLong));
                }
            } else {
                MultiPiecePattern piecePattern = controllerPiecePatterns.get(controller);
                if (piecePattern != null) {
                    PieceRuntimes runtimes = controller.getPieceRuntimes();
                    if (runtimes != null) {
                        piecePattern.markDirtyByPosition(posLong, runtimes, controller);
                    }
                }
            }

            lastChangedTick.put(controller, gameTick);
            pendingRecheck.add(controller);
        }
        return affected;
    }

    boolean enqueueDirtyRoots(@NotNull MultiblockControllerBase controller,
                              @NotNull Iterable<String> roots,
                              long gameTick) {
        if (!controllerPositions.containsKey(controller)) {
            return false;
        }
        if (suppressedControllers.contains(controller)) {
            return false;
        }
        StructureRuntime runtime = controller.getStructureRuntime();
        if (runtime == null) {
            return false;
        }
        boolean added = runtime.addDirtyRoots(roots);
        if (!added && !runtime.hasPendingDirtyRoots(controller)) {
            return false;
        }
        lastChangedTick.put(controller, gameTick);
        pendingRecheck.add(controller);
        return true;
    }

    @NotNull
    ChangeSnapshot captureChangeSnapshot(@NotNull BlockPos minCorner,
                                         @NotNull BlockPos maxCorner) {
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

    boolean isChangeSnapshotCurrent(@NotNull ChangeSnapshot snapshot) {
        for (Map.Entry<ChunkPos, Long> entry : snapshot.revisions.entrySet()) {
            if (!entry.getValue().equals(
                    chunkChangeRevisions.getOrDefault(entry.getKey(), 0L))) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    DirtyCheckLease consumeDirtyCheck(@NotNull MultiblockControllerBase controller,
                                         long currentTick) {
        if (!controllerPositions.containsKey(controller)) {
            return DirtyCheckLease.unregistered();
        }
        if (!pendingRecheck.contains(controller)) {
            return DirtyCheckLease.clean();
        }

        Long changed = lastChangedTick.get(controller);
        if (changed != null && currentTick - changed < RECHECK_COOLDOWN_TICKS) {
            return DirtyCheckLease.deferred(changed);
        }

        pendingRecheck.remove(controller);
        lastChangedTick.remove(controller);

        StructureRuntime runtime = controller.getStructureRuntime();
        if (runtime != null && runtime.hasPendingDirtyRoots(controller)) {
            StructureIncrementalFallbackReason reason =
                    runtime.getIncrementalFallbackReason(
                            gregtech.api.pattern.StructureOrientation.fromController(controller),
                            controller);
            return reason == null
                    ? DirtyCheckLease.incremental()
                    : DirtyCheckLease.full(reason);
        }

        MultiPiecePattern pattern = controllerPiecePatterns.get(controller);
        PieceRuntimes runtimes = controller.getPieceRuntimes();
        if (pattern != null && runtimes != null && pattern.hasDirtyPieces(runtimes, controller)) {
            return DirtyCheckLease.activeGraph();
        }
        return DirtyCheckLease.full();
    }

    boolean isRegistered(@NotNull MultiblockControllerBase controller) {
        return controllerPositions.containsKey(controller);
    }

    @NotNull
    LongSet getPositions(@NotNull MultiblockControllerBase controller) {
        StructurePositionIndex positionIndex = controllerPositionIndexes.get(controller);
        if (positionIndex != null) {
            return positionIndex.getAllFormedPositions();
        }
        LongSet positions = controllerPositions.get(controller);
        return positions != null ? positions : new LongOpenHashSet();
    }

    void suppressRecheck(@NotNull MultiblockControllerBase controller) {
        suppressedControllers.add(controller);
    }

    void unsuppressRecheck(@NotNull MultiblockControllerBase controller) {
        suppressedControllers.remove(controller);
        pendingRecheck.remove(controller);
        lastChangedTick.remove(controller);
    }

    void clear() {
        chunkIndex.clear();
        controllerPositions.clear();
        controllerPositionIndexes.clear();
        controllerPiecePatterns.clear();
        pendingRecheck.clear();
        lastChangedTick.clear();
        chunkChangeRevisions.clear();
        changeRevision.set(0);
        suppressedControllers.clear();
    }

    static final class ChangeSnapshot {

        private final Map<ChunkPos, Long> revisions;

        private ChangeSnapshot(@NotNull Map<ChunkPos, Long> revisions) {
            this.revisions = Collections.unmodifiableMap(new HashMap<>(revisions));
        }
    }

    static final class DirtyCheckLease {

        enum Action {
            UNREGISTERED,
            CLEAN,
            DEFERRED,
            ACTIVE_GRAPH,
            INCREMENTAL,
            FULL
        }

        @NotNull
        private final Action action;
        private final long lastChangedTick;
        @Nullable
        private final StructureIncrementalFallbackReason fallbackReason;

        private DirtyCheckLease(@NotNull Action action, long lastChangedTick,
                                   @Nullable StructureIncrementalFallbackReason fallbackReason) {
            this.action = action;
            this.lastChangedTick = lastChangedTick;
            this.fallbackReason = fallbackReason;
        }

        @NotNull
        static DirtyCheckLease unregistered() {
            return new DirtyCheckLease(Action.UNREGISTERED, -1, null);
        }

        @NotNull
        static DirtyCheckLease clean() {
            return new DirtyCheckLease(Action.CLEAN, -1, null);
        }

        @NotNull
        static DirtyCheckLease deferred(long lastChangedTick) {
            return new DirtyCheckLease(Action.DEFERRED, lastChangedTick, null);
        }

        @NotNull
        static DirtyCheckLease activeGraph() {
            return new DirtyCheckLease(Action.ACTIVE_GRAPH, -1, null);
        }

        @NotNull
        static DirtyCheckLease incremental() {
            return new DirtyCheckLease(Action.INCREMENTAL, -1, null);
        }

        @NotNull
        static DirtyCheckLease full() {
            return new DirtyCheckLease(Action.FULL, -1, null);
        }

        @NotNull
        static DirtyCheckLease full(@NotNull StructureIncrementalFallbackReason fallbackReason) {
            return new DirtyCheckLease(Action.FULL, -1, fallbackReason);
        }

        boolean isRegistered() {
            return action != Action.UNREGISTERED;
        }

        boolean shouldCheck() {
            return action == Action.ACTIVE_GRAPH || action == Action.FULL || action == Action.INCREMENTAL;
        }

        boolean shouldCheckActiveGraph() {
            return action == Action.ACTIVE_GRAPH;
        }

        boolean shouldCheckIncremental() {
            return action == Action.INCREMENTAL;
        }

        @NotNull
        Action getAction() {
            return action;
        }

        long getLastChangedTick() {
            return lastChangedTick;
        }

        @Nullable
        StructureIncrementalFallbackReason getFallbackReason() {
            return fallbackReason;
        }
    }
}
