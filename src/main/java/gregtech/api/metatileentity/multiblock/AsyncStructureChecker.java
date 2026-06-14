package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureSnapshotResult;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTLog;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages asynchronous structure checking for unformed multiblock controllers.
 * Instead of checking patterns on the main thread every 20 ticks, unformed controllers
 * are registered here and checked on a separate daemon thread.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Unformed controllers register via {@link #registerForAsyncCheck}</li>
 *   <li>Main thread periodically calls {@link #prepareSnapshots()} to capture block state snapshots</li>
 *   <li>Async thread picks up snapshot tasks and performs pattern matching</li>
 *   <li>If pattern matches, result is queued for main-thread callback via {@link #processResults()}</li>
 * </ol>
 *
 * <p>Thread safety: All world access happens on the main thread (snapshot capture).
 * The async thread only reads from immutable snapshots.
 */
public class AsyncStructureChecker {

    private static final AsyncStructureChecker INSTANCE = new AsyncStructureChecker();

    /** Singleton accessor */
    public static AsyncStructureChecker getInstance() {
        return INSTANCE;
    }

    /** Controllers waiting for async structure check */
    private final Set<MultiblockControllerBase> pendingControllers = ConcurrentHashMap.newKeySet();

    /** Snapshot tasks ready for async processing */
    private final Queue<SnapshotTask> snapshotQueue = new ConcurrentLinkedQueue<>();

    /** Results from async checks that need main-thread processing */
    private final Queue<AsyncCheckResult> resultQueue = new ConcurrentLinkedQueue<>();

    /** Controller -> registration generation currently being processed. */
    private final Map<MultiblockControllerBase, Long> inFlight = new ConcurrentHashMap<>();

    /** Registration generations prevent an old task surviving unregister/re-register. */
    private final Map<MultiblockControllerBase, Long> registrationGenerations =
            new ConcurrentHashMap<>();
    private final AtomicLong nextRegistrationGeneration = new AtomicLong();

    /**
     * Controllers whose structure AABB is too large to snapshot safely.
     * These are handed back to the main thread for direct polling instead.
     */
    private final Queue<AsyncCheckToken> oversizedQueue = new ConcurrentLinkedQueue<>();

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Tick counter for staggering snapshot preparation */
    private int tickCounter = 0;

    /** Maximum snapshots prepared per tick to avoid lag spikes */
    private static final int MAX_SNAPSHOTS_PER_TICK = 4;

    /** Extra margin added to structure AABB for snapshot capture */
    private static final int SNAPSHOT_MARGIN = 2;

    /**
     * Volume threshold above which snapshot capture is skipped and the controller
     * is flagged for immediate main-thread fallback instead.
     * 100^3 = 1,000,000 — structures larger than this are too expensive to snapshot per-tick.
     */
    private static final int MAX_SNAPSHOT_VOLUME = 100 * 100 * 100;

    /** Interval between async check cycles (ms) */
    private static final long CHECK_INTERVAL_MS = 250;

    private AsyncStructureChecker() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GT-Multiblock-AsyncCheck");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /**
     * Start the async checking system.
     * Called during server startup.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduledTask = executor.scheduleAtFixedRate(
                    this::asyncCheckLoop, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the async checking system.
     * Called during server shutdown.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
                scheduledTask = null;
            }
            pendingControllers.clear();
            snapshotQueue.clear();
            resultQueue.clear();
            inFlight.clear();
            registrationGenerations.clear();
            oversizedQueue.clear();
        }
    }

    /**
     * Register an unformed controller for async structure checking.
     * The controller will be checked periodically on the async thread.
     *
     * @param controller the unformed multiblock controller
     */
    public void registerForAsyncCheck(@NotNull MultiblockControllerBase controller) {
        if (running.get() && pendingControllers.add(controller)) {
            registrationGenerations.put(
                    controller, nextRegistrationGeneration.incrementAndGet());
        }
    }

    /**
     * Unregister a controller from async checking.
     * Called when the controller forms, is removed, or the world unloads.
     *
     * @param controller the controller to unregister
     */
    public void unregister(@NotNull MultiblockControllerBase controller) {
        pendingControllers.remove(controller);
        inFlight.remove(controller);
        registrationGenerations.remove(controller);
    }

    /**
     * Called from the main thread every tick.
     * Prepares block state snapshots for a subset of pending controllers.
     * Uses staggering to spread load across ticks.
     */
    public void prepareSnapshots() {
        if (!running.get()) return;
        tickCounter++;

        int prepared = 0;
        for (MultiblockControllerBase controller : pendingControllers) {
            if (prepared >= MAX_SNAPSHOTS_PER_TICK) break;
            if (inFlight.containsKey(controller)) continue;

            // Staggering: only process controllers whose hash aligns with this tick
            if ((controller.hashCode() + tickCounter) % 4 != 0) continue;

            Long registrationGeneration = registrationGenerations.get(controller);
            if (registrationGeneration == null) continue;

            World world = controller.getWorld();
            if (world == null || world.isRemote) continue;

            // Verify the controller is still valid and unformed
            if (controller.isStructureFormed()) {
                unregister(controller);
                continue;
            }

            BlockPos pos = controller.getPos();
            if (pos == null) continue;
            StructureDefinition<?> definition = controller.getStructureDefinition();
            long runtimeGeneration = controller.getStructureRuntimeGeneration();
            StructureOrientation orientation = StructureOrientation.fromController(controller);

            // Compute precise snapshot region from template AABB
            SnapshotCapture capture = captureSnapshotForController(
                    world, controller, definition, pos,
                    orientation);
            AsyncCheckToken token = new AsyncCheckToken(
                    controller, registrationGeneration, runtimeGeneration,
                    world, pos.toImmutable(), orientation, definition,
                    capture.changeSnapshot);

            if (capture.oversized) {
                // Structure AABB exceeds volume cap — route to oversized queue for main-thread fallback
                inFlight.put(controller, registrationGeneration);
                oversizedQueue.offer(token);
                prepared++;
                continue;
            }
            if (capture.snapshot == null || !isCurrent(token)) continue;

            // Create task and enqueue for async processing
            SnapshotTask task = new SnapshotTask(token, capture.snapshot);

            inFlight.put(controller, registrationGeneration);
            snapshotQueue.offer(task);
            prepared++;
        }
    }

    /**
     * Called from the main thread every tick.
     * Processes results from async checks that matched.
     * Also handles oversized controllers that could not be snapshotted.
     */
    public void processResults() {
        if (!running.get()) return;

        // Process oversized controllers with the same generation/orientation
        // checks as snapshot results before falling back to a live check.
        AsyncCheckToken oversized;
        while ((oversized = oversizedQueue.poll()) != null) {
            complete(oversized);
            String staleReason = staleReason(oversized);
            if (staleReason != null) {
                traceStale(oversized, staleReason);
                continue;
            }
            MultiblockControllerBase controller = oversized.controller;
            if (gregtech.common.ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug("[AsyncStructureCheck] Oversized AABB fallback for {}",
                        controller.getMetaName());
            }
            controller.checkStructurePattern();
        }

        AsyncCheckResult result;
        while ((result = resultQueue.poll()) != null) {
            AsyncCheckToken token = result.token;
            MultiblockControllerBase controller = token.controller;
            complete(token);

            String staleReason = result.staleReason == null
                    ? staleReason(token)
                    : result.staleReason;
            if (staleReason != null) {
                traceStale(token, staleReason);
                continue;
            }

            if (result.matched) {
                // Pattern matched in snapshot — do a confirmatory check on main thread
                // This handles the rare case where world changed between snapshot and now
                if (gregtech.common.ConfigHolder.machines.debugStructureCheck) {
                    GTLog.logger.debug("[AsyncStructureCheck] Async match found for {}, performing main-thread confirm",
                            controller.getMetaName());
                }
                controller.checkStructurePattern();

                if (controller.isStructureFormed()) {
                    unregister(controller);
                }
            }
            // If not matched, controller stays in pendingControllers for next cycle
        }
    }

    private void complete(@NotNull AsyncCheckToken token) {
        inFlight.remove(token.controller, token.registrationGeneration);
    }

    private boolean isCurrent(@NotNull AsyncCheckToken token) {
        return staleReason(token) == null;
    }

    @Nullable
    private String staleReason(@NotNull AsyncCheckToken token) {
        Long currentRegistration = registrationGenerations.get(token.controller);
        if (currentRegistration == null
                || currentRegistration != token.registrationGeneration) {
            return "registration-generation";
        }
        if (!pendingControllers.contains(token.controller)) {
            return "not-pending";
        }
        if (token.controller.getWorld() != token.world) {
            return "world";
        }
        if (token.controller.isStructureFormed()) {
            return "already-formed";
        }
        if (!Objects.equals(token.controller.getPos(), token.centerPos)) {
            return "controller-position";
        }
        if (token.controller.getStructureRuntimeGeneration() != token.runtimeGeneration) {
            return "runtime-generation";
        }
        if (!token.orientation.matchesControllerForCheck(token.controller)) {
            return "orientation";
        }
        if (token.changeSnapshot != null
                && !MultiblockWorldData.get(token.world)
                        .isChangeSnapshotCurrent(token.changeSnapshot)) {
            return "snapshot-version";
        }
        return null;
    }

    private static void traceStale(@NotNull AsyncCheckToken token,
                                   @NotNull String reason) {
        StructureTrace.debug(
                token.controller,
                "async-stale-rejected",
                "reason=" + reason
                        + ", registrationGeneration=" + token.registrationGeneration
                        + ", runtimeGeneration=" + token.runtimeGeneration);
    }

    /**
     * Capture a precise snapshot for the controller using the structure definition's world-space AABB.
     *
     * <p>Uses {@link StructureDefinition#computeWorldAABB} to determine the exact bounding box
     * of the structure in world coordinates, avoiding the wasteful symmetric cubic approximation.
     *
     * <p>The result distinguishes a successful stable capture, an oversized
     * region that requires live fallback, and a region that changed while it
     * was being captured.
     */
    @NotNull
    private SnapshotCapture captureSnapshotForController(
            World world,
            MultiblockControllerBase controller,
            StructureDefinition<?> definition,
            BlockPos pos,
            StructureOrientation orientation) {
        BlockPos[] aabb = definition.computeWorldAABB(
                pos,
                orientation.withFlipped(false),
                SNAPSHOT_MARGIN);
        if (orientation.allowsFlip()) {
            BlockPos[] flippedAabb = definition.computeWorldAABB(
                    pos,
                    orientation.withFlipped(true),
                    SNAPSHOT_MARGIN);
            aabb = unionAABB(aabb, flippedAabb);
        }
        BlockPos minCorner = aabb[0];
        BlockPos maxCorner = aabb[1];

        // Guard against absurdly large AABBs (e.g. bad template data or very large repeatable aisles)
        long dx = (long) maxCorner.getX() - minCorner.getX() + 1;
        long dy = (long) maxCorner.getY() - minCorner.getY() + 1;
        long dz = (long) maxCorner.getZ() - minCorner.getZ() + 1;
        long volume = dx * dy * dz;

        if (volume > MAX_SNAPSHOT_VOLUME) {
            if (gregtech.common.ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug(
                        "[AsyncStructureCheck] Snapshot AABB too large ({} blocks) for {}, routing to main-thread fallback",
                        volume, controller.getMetaName());
            }
            return SnapshotCapture.oversized();
        }

        MultiblockWorldData worldData = MultiblockWorldData.get(world);
        MultiblockWorldData.ChangeSnapshot before =
                worldData.captureChangeSnapshot(minCorner, maxCorner);
        BlockStateSnapshot snapshot =
                BlockStateSnapshot.captureRegion(world, minCorner, maxCorner);
        MultiblockWorldData.ChangeSnapshot after =
                worldData.captureChangeSnapshot(minCorner, maxCorner);
        if (!worldData.isChangeSnapshotCurrent(before)
                || !worldData.isChangeSnapshotCurrent(after)) {
            return SnapshotCapture.changedDuringCapture();
        }
        return SnapshotCapture.success(snapshot, after);
    }

    /**
     * Async check loop running on the dedicated thread.
     * Picks up snapshot tasks and performs pattern matching.
     */
    private void asyncCheckLoop() {
        try {
            SnapshotTask task;
            while ((task = snapshotQueue.poll()) != null) {
                if (!running.get()) return;
                String staleReason = staleReason(task.token);
                if (staleReason != null) {
                    resultQueue.offer(AsyncCheckResult.stale(task.token, staleReason));
                    continue;
                }

                try {
                    boolean matched = performAsyncCheck(task);
                    resultQueue.offer(AsyncCheckResult.completed(task.token, matched));
                } catch (RuntimeException e) {
                    GTLog.logger.error(
                            "Error checking async structure snapshot for {}",
                            task.token.controller.getMetaName(), e);
                    resultQueue.offer(AsyncCheckResult.completed(task.token, false));
                }
            }
        } catch (Exception e) {
            // Catch all exceptions to prevent the scheduled task from dying
            gregtech.api.util.GTLog.logger.error("Error in async structure check", e);
        }
    }

    /**
     * Perform pattern matching against a snapshot (runs on async thread).
     * Only returns whether the pattern matched; the main thread will do a confirmatory check.
     *
     * <p>All controllers expose a {@link StructureDefinition}; legacy templates
     * are adapted before this checker sees them.
     */
    private boolean performAsyncCheck(@NotNull SnapshotTask task) {
        StructureRuntime runtime = StructureRuntime.fromDefinition(task.token.definition);
        StructureSnapshotResult result = runtime.checkSnapshot(
                StructureOperationRequest.snapshotCheck(
                        task.snapshot, task.token.centerPos, task.token.orientation,
                        task.token.controller));
        return result.isMatched();
    }

    private static BlockPos[] unionAABB(@NotNull BlockPos[] first, @NotNull BlockPos[] second) {
        return new BlockPos[] {
                new BlockPos(
                        Math.min(first[0].getX(), second[0].getX()),
                        Math.min(first[0].getY(), second[0].getY()),
                        Math.min(first[0].getZ(), second[0].getZ())),
                new BlockPos(
                        Math.max(first[1].getX(), second[1].getX()),
                        Math.max(first[1].getY(), second[1].getY()),
                        Math.max(first[1].getZ(), second[1].getZ()))
        };
    }

    /**
     * Remove all controllers from a specific world.
     * Called when a world is unloaded.
     */
    public void clearWorld(@NotNull World world) {
        pendingControllers.removeIf(c -> c.getWorld() == world);
        inFlight.keySet().removeIf(c -> c.getWorld() == world);
        registrationGenerations.keySet().removeIf(c -> c.getWorld() == world);
        snapshotQueue.removeIf(task -> task.token.world == world);
        resultQueue.removeIf(result -> result.token.world == world);
        oversizedQueue.removeIf(token -> token.world == world);
    }

    /**
     * @return true if async checking is active
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return the number of controllers pending async check
     */
    public int getPendingCount() {
        return pendingControllers.size();
    }

    // --- Internal data classes ---

    private static class AsyncCheckToken {

        final MultiblockControllerBase controller;
        final long registrationGeneration;
        final long runtimeGeneration;
        final World world;
        final BlockPos centerPos;
        final StructureOrientation orientation;
        final StructureDefinition<?> definition;
        @Nullable
        final MultiblockWorldData.ChangeSnapshot changeSnapshot;

        AsyncCheckToken(MultiblockControllerBase controller,
                        long registrationGeneration,
                        long runtimeGeneration,
                        World world,
                        BlockPos centerPos,
                        StructureOrientation orientation,
                        StructureDefinition<?> definition,
                        @Nullable MultiblockWorldData.ChangeSnapshot changeSnapshot) {
            this.controller = controller;
            this.registrationGeneration = registrationGeneration;
            this.runtimeGeneration = runtimeGeneration;
            this.world = world;
            this.centerPos = centerPos;
            this.orientation = orientation;
            this.definition = definition;
            this.changeSnapshot = changeSnapshot;
        }
    }

    private static class SnapshotCapture {

        @Nullable
        final BlockStateSnapshot snapshot;
        @Nullable
        final MultiblockWorldData.ChangeSnapshot changeSnapshot;
        final boolean oversized;

        private SnapshotCapture(@Nullable BlockStateSnapshot snapshot,
                                @Nullable MultiblockWorldData.ChangeSnapshot changeSnapshot,
                                boolean oversized) {
            this.snapshot = snapshot;
            this.changeSnapshot = changeSnapshot;
            this.oversized = oversized;
        }

        static SnapshotCapture success(
                BlockStateSnapshot snapshot,
                MultiblockWorldData.ChangeSnapshot changeSnapshot) {
            return new SnapshotCapture(snapshot, changeSnapshot, false);
        }

        static SnapshotCapture changedDuringCapture() {
            return new SnapshotCapture(null, null, false);
        }

        static SnapshotCapture oversized() {
            return new SnapshotCapture(null, null, true);
        }
    }

    private static class SnapshotTask {

        final AsyncCheckToken token;
        final BlockStateSnapshot snapshot;

        SnapshotTask(AsyncCheckToken token, BlockStateSnapshot snapshot) {
            this.token = token;
            this.snapshot = snapshot;
        }
    }

    private static class AsyncCheckResult {

        final AsyncCheckToken token;
        final boolean matched;
        @Nullable
        final String staleReason;

        private AsyncCheckResult(AsyncCheckToken token, boolean matched,
                                 @Nullable String staleReason) {
            this.token = token;
            this.matched = matched;
            this.staleReason = staleReason;
        }

        static AsyncCheckResult completed(AsyncCheckToken token, boolean matched) {
            return new AsyncCheckResult(token, matched, null);
        }

        static AsyncCheckResult stale(AsyncCheckToken token, String reason) {
            return new AsyncCheckResult(token, false, reason);
        }
    }
}
