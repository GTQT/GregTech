package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTLog;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Controllers whose structure AABB is too large to snapshot safely.
     * These are handed back to the main thread for direct polling instead.
     */
    private final Queue<MultiblockControllerBase> oversizedQueue = new ConcurrentLinkedQueue<>();

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

            // Compute precise snapshot region from template AABB
            BlockStateSnapshot snapshot = captureSnapshotForController(world, controller, pos);

            if (snapshot == null) {
                // Structure AABB exceeds volume cap — route to oversized queue for main-thread fallback
                pendingControllers.remove(controller);
                oversizedQueue.offer(controller);
                continue;
            }

            // Create task and enqueue for async processing
            SnapshotTask task = new SnapshotTask(
                    controller,
                    snapshot,
                    pos.toImmutable(),
                    controller.getFrontFacing(),
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
     * Also handles oversized controllers that could not be snapshotted.
     */
    public void processResults() {
        if (!running.get()) return;

        // Process oversized controllers: perform a direct main-thread structure check.
        // These controllers were removed from pendingControllers in prepareSnapshots(),
        // so re-register them for async after a successful main-thread form, or fall back to polling.
        MultiblockControllerBase oversized;
        while ((oversized = oversizedQueue.poll()) != null) {
            if (oversized.getWorld() == null || oversized.isStructureFormed()) continue;
            if (gregtech.common.ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug("[AsyncStructureCheck] Oversized AABB fallback for {}", oversized.getMetaName());
            }
            oversized.checkStructurePattern();
        }

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
     * Capture a precise snapshot for the controller using the structure definition's world-space AABB.
     *
     * <p>Uses {@link StructureDefinition#computeWorldAABB} to determine the exact bounding box
     * of the structure in world coordinates, avoiding the wasteful symmetric cubic approximation.
     *
     * <p>Returns {@code null} if the computed AABB volume exceeds {@link #MAX_SNAPSHOT_VOLUME},
     * signalling the caller to route the controller to main-thread fallback instead.
     */
    @Nullable
    private BlockStateSnapshot captureSnapshotForController(World world, MultiblockControllerBase controller,
                                                            BlockPos pos) {
        StructureDefinition definition = controller.getStructureDefinition();
        if (definition != null) {
            BlockPos[] aabb = definition.computeWorldAABB(
                    pos,
                    controller.getFrontFacingForStructure(),
                    controller.getUpwardsFacing(),
                    controller.isFlipped(),
                    SNAPSHOT_MARGIN);
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
                return null;
            }

            return BlockStateSnapshot.captureRegion(world, minCorner, maxCorner);
        }
        // Legacy path: legacy multiblocks (those that override createStructureTemplate instead of
        // createStructureDefinition) have no SD; fall back to a symmetric cubic snapshot.
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
     *
     * <p>Two check paths:
     * <ul>
     *   <li><b>StructureDefinition path</b> (preferred): when the controller exposes a
     *       {@link StructureDefinition}, iterate each piece in its compiled
     *       {@link MultiPiecePattern} and call {@link StructurePiece#checkOnSnapshot} with
     *       prior {@link FormedStructureMetadata} for O(1) verification of formed structures.
     *       This handles both single-piece and multi-piece definitions uniformly.</li>
     *   <li><b>Legacy path</b>: when only a single {@link BlockPatternTemplate} is available
     *       (legacy multiblocks that still override {@code createStructureTemplate}), use the
     *       original temporary-state approach.</li>
     * </ul>
     */
    private boolean performAsyncCheck(@NotNull SnapshotTask task) {
        // Preferred path: route everything through StructureDefinition
        StructureDefinition definition = task.controller.getStructureDefinition();
        if (definition != null) {
            MultiPiecePattern multiPiece = definition.getCompiledPattern();
            FormedStructureMetadata prior = task.controller.getFormedMetadata();
            // Allocate a transient per-check PieceRuntimes to avoid data races with
            // the main thread (which holds the controller's owned PieceRuntimes and
            // may be mutating it concurrently). The async thread only ever reads
            // its own runtimes; the main thread reads its own.
            PieceRuntimes asyncRuntimes = new PieceRuntimes(multiPiece);
            for (StructurePiece piece : multiPiece.getPieceList()) {
                if (piece.isConditional() && !piece.isActive()) continue;

                BlockPos pieceOrigin = piece.getCenterPos(
                        task.centerPos, task.frontFacing, task.upwardsFacing);

                if (!piece.checkOnSnapshot(task.snapshot, pieceOrigin,
                        task.frontFacing, task.upwardsFacing, task.allowsFlip, prior,
                        asyncRuntimes.get(piece))) {
                    return false;
                }
            }
            return true;
        }

        // Legacy path: single template with temporary state
        BlockPatternTemplate template = task.controller.getPatternTemplate();
        if (template == null) return false;

        // Create a temporary state from the shared template to avoid data race (M1 fix)
        MultiblockState tempState = template.createState();

        // Use prior-aware snapshot check for O(1) verification of formed structures
        FormedStructureMetadata prior = task.controller.getFormedMetadata();
        PatternMatchContext context = tempState.checkOnSnapshotWithPrior(
                task.snapshot, task.centerPos, task.frontFacing, task.upwardsFacing, task.allowsFlip, prior);

        return context != null;
    }

    /**
     * Remove all controllers from a specific world.
     * Called when a world is unloaded.
     */
    public void clearWorld(@NotNull World world) {
        pendingControllers.removeIf(c -> c.getWorld() == world);
        inFlight.removeIf(c -> c.getWorld() == world);
        oversizedQueue.removeIf(c -> c.getWorld() == world);
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
