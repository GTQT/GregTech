package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.util.GTLog;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /** Controllers currently being processed (avoid double submission) */
    private final Set<MultiblockControllerBase> inFlight = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Tick counter for staggering snapshot preparation */
    private int tickCounter = 0;

    /** Maximum snapshots prepared per tick to avoid lag spikes */
    private static final int MAX_SNAPSHOTS_PER_TICK = 4;

    /** Fallback snapshot radius when template size cannot be determined */
    private static final int FALLBACK_SNAPSHOT_RADIUS = 32;

    /** Extra margin added to structure AABB for snapshot capture */
    private static final int SNAPSHOT_MARGIN = 2;

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
        }
    }

    /**
     * Register an unformed controller for async structure checking.
     * The controller will be checked periodically on the async thread.
     *
     * @param controller the unformed multiblock controller
     */
    public void registerForAsyncCheck(@NotNull MultiblockControllerBase controller) {
        if (running.get()) {
            pendingControllers.add(controller);
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
            if (inFlight.contains(controller)) continue;

            // Staggering: only process controllers whose hash aligns with this tick
            if ((controller.hashCode() + tickCounter) % 4 != 0) continue;

            World world = controller.getWorld();
            if (world == null || world.isRemote) continue;

            // Verify the controller is still valid and unformed
            if (controller.isStructureFormed()) {
                pendingControllers.remove(controller);
                continue;
            }

            BlockPos pos = controller.getPos();
            if (pos == null) continue;

            // Compute snapshot region from template dimensions instead of fixed radius
            BlockStateSnapshot snapshot = captureSnapshotForController(world, controller, pos);

            // Create task and enqueue for async processing
            SnapshotTask task = new SnapshotTask(
                    controller,
                    snapshot,
                    pos.toImmutable(),
                    controller.getFrontFacing().getOpposite(),
                    controller.getUpwardsFacing(),
                    controller.allowsFlip()
            );

            inFlight.add(controller);
            snapshotQueue.offer(task);
            prepared++;
        }
    }

    /**
     * Called from the main thread every tick.
     * Processes results from async checks that matched.
     */
    public void processResults() {
        if (!running.get()) return;

        AsyncCheckResult result;
        while ((result = resultQueue.poll()) != null) {
            MultiblockControllerBase controller = result.controller;
            inFlight.remove(controller);

            // Double-check the controller is still valid and unformed
            if (controller.getWorld() == null || controller.isStructureFormed()) {
                pendingControllers.remove(controller);
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
                    pendingControllers.remove(controller);
                }
            }
            // If not matched, controller stays in pendingControllers for next cycle
        }
    }

    /**
     * Capture a snapshot region sized to the controller's structure template.
     * Uses the maximum expanded dimensions (accounting for repeatable aisles)
     * and takes the max across all axes as a symmetric radius, since the mapping
     * from pattern coordinates to world coordinates depends on controller facing.
     * Falls back to a fixed radius if template is unavailable.
     */
    private BlockStateSnapshot captureSnapshotForController(World world, MultiblockControllerBase controller,
                                                            BlockPos pos) {
        BlockPatternTemplate template = controller.getPatternTemplate();
        if (template != null) {
            int palmSize = template.getPalmLength();
            int thumbSize = template.getThumbLength();
            int fingerSize = template.getMaxExpandedFingerLength();
            // Use max dimension as a conservative symmetric radius since we don't know
            // which pattern axis maps to which world axis without resolving structureDir + facing
            int radius = Math.max(Math.max(palmSize, thumbSize), fingerSize) + SNAPSHOT_MARGIN;
            BlockPos min = pos.add(-radius, -radius, -radius);
            BlockPos max = pos.add(radius, radius, radius);
            return BlockStateSnapshot.captureRegion(world, min, max);
        }
        return BlockStateSnapshot.capture(world, pos, FALLBACK_SNAPSHOT_RADIUS);
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
                if (!pendingControllers.contains(task.controller)) {
                    inFlight.remove(task.controller);
                    continue;
                }

                boolean matched = performAsyncCheck(task);
                resultQueue.offer(new AsyncCheckResult(task.controller, matched));
            }
        } catch (Exception e) {
            // Catch all exceptions to prevent the scheduled task from dying
            gregtech.api.util.GTLog.logger.error("Error in async structure check", e);
        }
    }

    /**
     * Perform pattern matching against a snapshot (runs on async thread).
     * Uses a temporary MultiblockState to avoid data race with the main thread.
     * Only returns whether the pattern matched; the main thread will do a confirmatory check.
     */
    private boolean performAsyncCheck(@NotNull SnapshotTask task) {
        BlockPatternTemplate template = task.controller.getPatternTemplate();
        if (template == null) return false;

        // Create a temporary state from the shared template to avoid data race (M1 fix)
        MultiblockState tempState = template.createState();

        // Use the snapshot-based check on the temporary state
        PatternMatchContext context = tempState.checkPatternFastAtSnapshot(
                task.snapshot, task.centerPos, task.frontFacing, task.upwardsFacing, task.allowsFlip);

        return context != null;
    }

    /**
     * Remove all controllers from a specific world.
     * Called when a world is unloaded.
     */
    public void clearWorld(@NotNull World world) {
        pendingControllers.removeIf(c -> c.getWorld() == world);
        inFlight.removeIf(c -> c.getWorld() == world);
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

    private static class SnapshotTask {

        final MultiblockControllerBase controller;
        final BlockStateSnapshot snapshot;
        final BlockPos centerPos;
        final EnumFacing frontFacing;
        final EnumFacing upwardsFacing;
        final boolean allowsFlip;

        SnapshotTask(MultiblockControllerBase controller, BlockStateSnapshot snapshot,
                     BlockPos centerPos, EnumFacing frontFacing, EnumFacing upwardsFacing, boolean allowsFlip) {
            this.controller = controller;
            this.snapshot = snapshot;
            this.centerPos = centerPos;
            this.frontFacing = frontFacing;
            this.upwardsFacing = upwardsFacing;
            this.allowsFlip = allowsFlip;
        }
    }

    private static class AsyncCheckResult {

        final MultiblockControllerBase controller;
        final boolean matched;

        AsyncCheckResult(MultiblockControllerBase controller, boolean matched) {
            this.controller = controller;
            this.matched = matched;
        }
    }
}
