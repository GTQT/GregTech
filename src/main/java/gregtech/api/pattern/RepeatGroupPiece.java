package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCompiler;
import gregtech.api.util.BlockInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Compact representation of a multi-axis repeatable structure piece.
 * Extends {@link StructurePiece} with internal backtracking for repeat axis search.
 *
 * <p>Two-state dispatch:
 * <ul>
 *   <li><b>Formed state</b> (prior != null): O(1) verification via tryCheckAtRepeats(priorReps)</li>
 *   <li><b>Building state</b> (prior == null): recursive backtracking with strategy dispatch</li>
 * </ul>
 *
 * <p>All per-instance state (last successful repeat counts, last aggregated
 * context, last successful position set) lives on the per-controller
 * {@link PieceRuntime}, not on this piece. This is the same state-separation
 * contract that {@link StructurePiece} uses for {@link PieceRuntimeState} — it
 * keeps the compiled piece template stateless and safe to share across
 * controllers of the same multiblock type.
 *
 * <h2>Search strategies</h2>
 * Driven by {@link StructureCompiler.SearchStrategy}:
 * <ul>
 *   <li>{@link StructureCompiler.SearchStrategy#SLIDING_1D} — single 1D sliding window</li>
 *   <li>{@link StructureCompiler.SearchStrategy#INDEPENDENT_1D} — independent 1D per axis (axis-separable)</li>
 *   <li>{@link StructureCompiler.SearchStrategy#NESTED_BACKTRACKING} — nested backtracking (non-tensor)</li>
 * </ul>
 */
public class RepeatGroupPiece extends StructurePiece {

    private final int[] repeatAxes;
    private final int[][] repeatRanges;
    private final int[] stepSizes;
    @Nullable
    private final String[] repeatChannelNames;
    private final int[] centerOffset;
    private final StructureCompiler.SearchStrategy strategy;

    @FunctionalInterface
    interface RepeatOffsetVisitor {

        boolean visit(@NotNull int[] localOffset);
    }

    public RepeatGroupPiece(@NotNull String name, @NotNull BlockPatternTemplate tpl,
                            @NotNull Vec3i offset, @NotNull OffsetMode mode,
                            @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset,
                            @NotNull StructureCompiler.SearchStrategy strategy) {
        this(name, tpl, offset, mode, cond, axes, ranges, steps, channelNames, centerOffset, strategy, true);
    }

    public RepeatGroupPiece(@NotNull String name, @NotNull BlockPatternTemplate tpl,
                            @NotNull Vec3i offset, @NotNull OffsetMode mode,
                            @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset,
                            @NotNull StructureCompiler.SearchStrategy strategy,
                            boolean toolingVisible) {
        // The 6-arg StructurePiece constructor binds a SnapshotChecker closure that
        // receives the per-controller PieceRuntime as its last argument, so the
        // per-instance state (cache / lastSuccessReps / lastAggregatedContext) all
        // lives on the runtime and the piece itself stays stateless.
        super(name, tpl, offset, mode, cond, RepeatGroupPiece::checkOnSnapshotDispatch, toolingVisible);
        this.repeatAxes = axes;
        this.repeatRanges = ranges;
        this.stepSizes = steps;
        this.repeatChannelNames = channelNames;
        this.centerOffset = centerOffset;
        this.strategy = strategy;
    }

    /**
     * New-path constructor taking a canonical {@link PieceTemplate} directly.
     * Uses the new {@link StructurePiece} constructor that holds the
     * {@code PieceTemplate} as the canonical IR and lazily constructs a
     * {@link BlockPatternTemplate} facade only if {@link #getTemplate()}
     * is called.
     */
    public RepeatGroupPiece(@NotNull String name, @NotNull PieceTemplate tpl,
                            @NotNull Vec3i offset, @NotNull OffsetMode mode,
                            @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset,
                            @NotNull StructureCompiler.SearchStrategy strategy) {
        this(name, tpl, offset, mode, cond, axes, ranges, steps, channelNames, centerOffset, strategy, true);
    }

    public RepeatGroupPiece(@NotNull String name, @NotNull PieceTemplate tpl,
                            @NotNull Vec3i offset, @NotNull OffsetMode mode,
                            @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset,
                            @NotNull StructureCompiler.SearchStrategy strategy,
                            boolean toolingVisible) {
        super(name, tpl, offset, mode, cond, RepeatGroupPiece::checkOnSnapshotDispatch, toolingVisible);
        this.repeatAxes = axes;
        this.repeatRanges = ranges;
        this.stepSizes = steps;
        this.repeatChannelNames = channelNames;
        this.centerOffset = centerOffset;
        this.strategy = strategy;
    }

    /**
     * Static snapshot-checker dispatch.
     * <p>The runtime is provided by the per-controller {@link PieceRuntimes} via the
     * {@link StructurePiece.SnapshotChecker#check} contract; it carries the
     * {@link PieceRuntimeState} plus the per-piece search cache. We forward to the
     * instance method on the supplied piece reference.
     */
    private static boolean checkOnSnapshotDispatch(@NotNull IBlockAccess snap,
                                                   @NotNull BlockPos origin,
                                                   @NotNull StructureOrientation orientation,
                                                   @Nullable FormedStructureMetadata prior,
                                                   @NotNull PieceRuntime runtime,
                                                   @NotNull StructureMatchSession session) {
        // The runtime knows its piece by identity via PieceRuntimes.get(piece),
        // but the SnapshotChecker is bound to the piece (not the runtime), so we
        // route the call back to the piece via its instance method.
        // RepeatGroupPiece is the only piece type that overrides checkOnSnapshotImpl;
        // other pieces use the no-op default. Cast is safe because the dispatch
        // closure is only installed on RepeatGroupPiece instances.
        StructurePiece piece = runtime.getPiece();
        if (piece instanceof RepeatGroupPiece repeatGroupPiece) {
            return repeatGroupPiece.checkOnSnapshotImpl(
                    snap, origin, orientation, prior, runtime, session);
        }
        return false;
    }

    /**
     * Instance-level multi-axis snapshot check. Receives the per-controller
     * {@link PieceRuntime} and uses its state cache.
     */
    private boolean checkOnSnapshotImpl(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                        @NotNull StructureOrientation orientation,
                                         @Nullable FormedStructureMetadata prior,
                                         @NotNull PieceRuntime runtime,
                                         @NotNull StructureMatchSession session) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;
        BlockPos pieceCenter = getCenterPos(origin, orientation, prior);
        int[] rejectedReps = null;

        // Formed state: O(1) verification with prior
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeats(
                    snap, pieceCenter, orientation, priorReps, runtime, session)) {
                runtime.cacheFormedReps(priorReps);
                return true;
            }
            rejectedReps = priorReps.clone();
            // Prior failed, fall through to full search
        }

        int[] preferredReps = selectPreferredReps(priorReps, runtime);

        // Building state: dispatch by strategy
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1D(snap, pieceCenter, orientation, runtime, session, preferredReps, rejectedReps);
                break;
            case INDEPENDENT_1D:
                ok = searchIndependent1D(snap, pieceCenter, orientation, runtime, session,
                        preferredReps, rejectedReps);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxes(0, new int[repeatAxes.length],
                        snap, pieceCenter, orientation, runtime, session, preferredReps, rejectedReps);
                break;
        }
        return ok;
    }

    /**
     * Synchronous check using World (main thread).
     * Uses {@link PieceRuntimeState#checkPatternFastAt} for each slice, which supports
     * cache-based fast path and proper World-level block access.
     *
     * @param runtime the per-controller state holder for this piece
     * @return true if the structure matches at some repeat count
     */
    public boolean checkSync(@NotNull World world, @NotNull BlockPos origin,
                              @NotNull StructureOrientation orientation,
                              @Nullable FormedStructureMetadata prior,
                             @NotNull PieceRuntime runtime) {
        StructureMatchSession session = new StructureMatchSession();
        return checkSync(world, origin, orientation, prior, runtime, session)
                && session.validate(false).success;
    }

    public boolean checkSync(@NotNull World world, @NotNull BlockPos origin,
                             @NotNull StructureOrientation orientation,
                             @Nullable FormedStructureMetadata prior,
                             @NotNull PieceRuntime runtime,
                             @NotNull StructureMatchSession session) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;
        BlockPos pieceCenter = getCenterPos(origin, orientation, prior);
        int[] rejectedReps = null;

        // Formed state: O(1) verification with prior
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeatsWorld(
                    world, pieceCenter, orientation, priorReps, runtime, session)) {
                runtime.cacheFormedReps(priorReps);
                return true;
            }
            rejectedReps = priorReps.clone();
        }

        int[] preferredReps = selectPreferredReps(priorReps, runtime);

        // Building state: dispatch by strategy using World-based search
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1DWorld(world, pieceCenter, orientation, runtime, session,
                        preferredReps, rejectedReps);
                break;
            case INDEPENDENT_1D:
                // INDEPENDENT_1D still uses snapshot path for axis line checks
                // (tensor product optimization doesn't benefit from World-level access)
                ok = searchIndependent1D(world, pieceCenter, orientation, runtime, session,
                        preferredReps, rejectedReps);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxesWorld(0, new int[repeatAxes.length],
                        world, pieceCenter, orientation, runtime, session, preferredReps, rejectedReps);
                break;
        }
        return ok;
    }

    // --- World-based search methods (synchronous, main thread) ---

    /**
     * Single 1D sliding window search using World (synchronous path).
     */
    private boolean searchSliding1DWorld(@NotNull World world, @NotNull BlockPos origin,
                                          @NotNull StructureOrientation orientation,
                                          @NotNull PieceRuntime runtime,
                                          @NotNull StructureMatchSession session,
                                          @Nullable int[] preferredReps,
                                          @Nullable int[] rejectedReps) {
        for (int r : repeatCandidates(0, preferredReps)) {
            int[] reps = new int[]{r};
            if (sameReps(reps, rejectedReps)) {
                continue;
            }
            if (tryCheckAtRepeatsWorld(
                    world, origin, orientation, reps, runtime, session)) {
                runtime.cacheFormedReps(reps);
                return true;
            }
        }
        return false;
    }

    /**
     * Nested backtracking search using World (synchronous path).
     */
    private boolean backtrackAxesWorld(int axisIdx, int[] currentReps,
                                        @NotNull World world, @NotNull BlockPos origin,
                                        @NotNull StructureOrientation orientation,
                                        @NotNull PieceRuntime runtime,
                                        @NotNull StructureMatchSession session,
                                        @Nullable int[] preferredReps,
                                        @Nullable int[] rejectedReps) {
        if (axisIdx == repeatAxes.length) {
            if (sameReps(currentReps, rejectedReps)) {
                return false;
            }
            if (tryCheckAtRepeatsWorld(
                    world, origin, orientation, currentReps, runtime, session)) {
                runtime.cacheFormedReps(currentReps);
                return true;
            }
            return false;
        }
        for (int r : repeatCandidates(axisIdx, preferredReps)) {
            currentReps[axisIdx] = r;
            if (backtrackAxesWorld(axisIdx + 1, currentReps,
                    world, origin, orientation, runtime, session, preferredReps, rejectedReps)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try checking the piece at specific repeat counts using World.
     * Delegates to the appropriate slice-checking method based on axis count.
     */
    private boolean tryCheckAtRepeatsWorld(@NotNull World world, @NotNull BlockPos origin,
                                            @NotNull StructureOrientation orientation,
                                            int[] reps,
                                            @NotNull PieceRuntime runtime,
                                            @NotNull StructureMatchSession session) {
        return session.tryFork(candidate -> repeatAxes.length == 1
                ? tryCheckAllSlicesWorld(
                        world, origin, orientation, reps, runtime, candidate)
                : tryCheckAllMultiAxisSlicesWorld(
                        world, origin, orientation, reps, runtime, candidate));
    }

    /**
     * Check all slices of a single-axis repeatable piece using World.
     * Uses exact-orientation checks for every slice.
     */
    private boolean tryCheckAllSlicesWorld(@NotNull World world, @NotNull BlockPos origin,
                                              @NotNull StructureOrientation orientation,
                                              int[] reps,
                                              @NotNull PieceRuntime runtime,
                                              @NotNull StructureMatchSession session) {
        PieceRuntimeState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;

        LongSet allPositions = new LongOpenHashSet();

        if (!visitRepeatOffsets(reps, local -> {
            PatternMatchContext ctx = state.checkPatternAtExact(
                    world, traversal(pieceCenter, orientation, local), session);
            if (ctx == null) {
                runtime.setLastAggregatedContext(null);
                return false;
            }

            Long2ObjectMap<BlockInfo> innerCache = state.cache;
            if (!innerCache.isEmpty()) {
                for (long posLong : innerCache.keySet()) {
                    allPositions.add(posLong);
                }
            }
            return true;
        })) {
            return false;
        }

        runtime.setLastAggregatedContext(session.getContext().copy());
        runtime.publishPositionSet(allPositions);
        return true;
    }

    /**
     * Check all slices of a multi-axis repeatable piece using World.
     * Enumerates the cartesian product of all repeat axes, checking the base piece
     * at each combination of offsets.
     */
    private boolean tryCheckAllMultiAxisSlicesWorld(@NotNull World world, @NotNull BlockPos origin,
                                                       @NotNull StructureOrientation orientation,
                                                       int[] reps,
                                                       @NotNull PieceRuntime runtime,
                                                       @NotNull StructureMatchSession session) {
        PieceRuntimeState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;
        LongSet allPositions = new LongOpenHashSet();

        if (!visitRepeatOffsets(reps, local -> {
            PatternMatchContext ctx = state.checkPatternAtExact(
                    world, traversal(pieceCenter, orientation, local), session);
            if (ctx == null) {
                runtime.setLastAggregatedContext(null);
                return false;
            }

            Long2ObjectMap<BlockInfo> innerCache = state.cache;
            if (!innerCache.isEmpty()) {
                for (long posLong : innerCache.keySet()) {
                    allPositions.add(posLong);
                }
            }
            return true;
        })) {
            return false;
        }

        runtime.setLastAggregatedContext(session.getContext().copy());
        runtime.publishPositionSet(allPositions);
        return true;
    }

    /**
     * Single 1D sliding window search (single axis).
     * Equivalent to the old aisleRepeatable sliding window algorithm.
     */
    private boolean searchSliding1D(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                    @NotNull StructureOrientation orientation,
                                    @NotNull PieceRuntime runtime,
                                    @NotNull StructureMatchSession session,
                                    @Nullable int[] preferredReps,
                                    @Nullable int[] rejectedReps) {
        int[] reps = new int[1];
        for (int r : repeatCandidates(0, preferredReps)) {
            reps[0] = r;
            if (sameReps(reps, rejectedReps)) {
                continue;
            }
            if (tryCheckAtRepeats(
                    snap, origin, orientation, reps, runtime, session)) {
                runtime.cacheFormedReps(reps);
                return true;
            }
        }
        return false;
    }

    /**
     * Independent 1D search per axis (multi-axis axis-separable shape).
     * Each axis is searched independently, then the selected size is fully verified once.
     */
    private boolean searchIndependent1D(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                        @NotNull StructureOrientation orientation,
                                        @NotNull PieceRuntime runtime,
                                        @NotNull StructureMatchSession session,
                                        @Nullable int[] preferredReps,
                                        @Nullable int[] rejectedReps) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = repeatRanges[i][0];
        }
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = searchAxisGreedy(snap, origin, i, reps, orientation, runtime, preferredReps);
            if (reps[i] < 0) return false; // Any axis failure = whole piece failure
        }
        // Final joint verification (safety net for axis boundary mismatches)
        if (!sameReps(reps, rejectedReps)
                && tryCheckAtRepeats(snap, origin, orientation, reps, runtime, session)) {
            runtime.cacheFormedReps(reps);
            return true;
        }
        return backtrackAxes(0, new int[repeatAxes.length],
                snap, origin, orientation, runtime, session, preferredReps, rejectedReps);
    }

    /**
     * Greedy search along a single axis.
     * Returns the repeat count, or -1 if no valid count found.
     */
    private int searchAxisGreedy(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                 int axisIdx, int[] partialReps,
                                 @NotNull StructureOrientation orientation,
                                 @NotNull PieceRuntime runtime,
                                 @Nullable int[] preferredReps) {
        for (int r : repeatCandidates(axisIdx, preferredReps)) {
            partialReps[axisIdx] = r;
            if (tryCheckAxisLine(snap, origin, axisIdx, partialReps, orientation, runtime)) {
                return r;
            }
        }
        return -1;
    }

    /**
     * Probe the boundary slice for a specific axis before attempting a full
     * cartesian repeat verification.
     */
    private boolean tryCheckAxisLine(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                     int axisIdx, int[] partialReps,
                                     @NotNull StructureOrientation orientation,
                                     @NotNull PieceRuntime runtime) {
        // Use the world-space piece center (OffsetMode applied) and fold the per-slice
        // step (cartesian over partialReps, with slice 0 at offset 0) into the cell loop
        // as a template-local offset. setActualRelativeOffset therefore runs exactly once
        // per cell.
        BlockPos pieceCenter = origin;
        int[] local = {0, 0, 0};
        local[repeatAxes[axisIdx]] = stepSizes[axisIdx] * (partialReps[axisIdx] - 1);
        return runtime.getState().checkAxisBoundaryFastAtSnapshot(
                snap, repeatAxes[axisIdx], traversal(pieceCenter, orientation, local));
    }

    /**
     * Nested backtracking search (multi-axis non-tensor).
     * Worst case O(prod(max_i - min_i + 1)), but early termination and greedy ordering
     * make typical cases much faster.
     */
    private boolean backtrackAxes(int axisIdx, int[] currentReps,
                                  @NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                  @NotNull StructureOrientation orientation,
                                  @NotNull PieceRuntime runtime,
                                  @NotNull StructureMatchSession session,
                                  @Nullable int[] preferredReps,
                                  @Nullable int[] rejectedReps) {
        if (axisIdx == repeatAxes.length) {
            if (sameReps(currentReps, rejectedReps)) {
                return false;
            }
            if (tryCheckAtRepeats(
                    snap, origin, orientation, currentReps, runtime, session)) {
                runtime.cacheFormedReps(currentReps);
                return true;
            }
            return false;
        }
        for (int r : repeatCandidates(axisIdx, preferredReps)) {
            currentReps[axisIdx] = r;
            if (backtrackAxes(axisIdx + 1, currentReps,
                    snap, origin, orientation, runtime, session, preferredReps, rejectedReps)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameReps(@NotNull int[] left, @Nullable int[] right) {
        if (right == null || left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private int[] selectPreferredReps(@Nullable int[] priorReps,
                                      @NotNull PieceRuntime runtime) {
        if (isUsablePreferredReps(priorReps)) {
            return priorReps.clone();
        }
        int[] lastReps = runtime.getLastFormedReps();
        return isUsablePreferredReps(lastReps) ? lastReps.clone() : null;
    }

    private boolean isUsablePreferredReps(@Nullable int[] reps) {
        if (reps == null || reps.length != repeatAxes.length) {
            return false;
        }
        for (int i = 0; i < reps.length; i++) {
            if (reps[i] < repeatRanges[i][0] || reps[i] > repeatRanges[i][1]) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    int[] repeatCandidatesForTesting(int axisIdx, @Nullable int[] preferredReps) {
        return repeatCandidates(axisIdx, preferredReps);
    }

    @NotNull
    private int[] repeatCandidates(int axisIdx, @Nullable int[] preferredReps) {
        int min = repeatRanges[axisIdx][0];
        int max = repeatRanges[axisIdx][1];
        int[] candidates = new int[max - min + 1];
        boolean[] added = new boolean[candidates.length];
        int count = 0;

        if (preferredReps != null) {
            int preferred = preferredReps[axisIdx];
            int limit = Math.max(preferred - min, max - preferred);
            for (int delta = 0; delta <= limit; delta++) {
                if (delta == 0) {
                    count = addRepeatCandidate(candidates, added, count, min, max, preferred);
                } else {
                    count = addRepeatCandidate(candidates, added, count, min, max, preferred + delta);
                    count = addRepeatCandidate(candidates, added, count, min, max, preferred - delta);
                }
            }
        }

        for (int r = max; r >= min; r--) {
            count = addRepeatCandidate(candidates, added, count, min, max, r);
        }
        return candidates;
    }

    private static int addRepeatCandidate(@NotNull int[] candidates,
                                          @NotNull boolean[] added,
                                          int count,
                                          int min,
                                          int max,
                                          int value) {
        if (value < min || value > max) {
            return count;
        }
        int index = value - min;
        if (added[index]) {
            return count;
        }
        added[index] = true;
        candidates[count] = value;
        return count + 1;
    }

    /**
     * Try checking the piece at specific repeat counts.
     * For single-axis repetition, checks each slice individually along the repeat axis.
     * For multi-axis repetition, checks all slice positions across the cartesian product
     * of all repeat axes, aggregating MultiblockParts and positions from every slice.
     */
    private boolean tryCheckAtRepeats(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                      @NotNull StructureOrientation orientation,
                                      int[] reps,
                                      @NotNull PieceRuntime runtime,
                                      @NotNull StructureMatchSession session) {
        return session.tryFork(candidate -> repeatAxes.length == 1
                ? tryCheckAllSlices(snap, origin, orientation, reps, runtime, candidate)
                : tryCheckAllMultiAxisSlices(
                        snap, origin, orientation, reps, runtime, candidate));
    }

    /**
     * Check all slices of a multi-axis repeatable piece.
     * Enumerates the cartesian product of all repeat axes, checking the base piece
     * at each combination of offsets. All slices must pass for the check to succeed.
     * Aggregates "MultiblockParts" from all slices into the runtime's lastAggregatedContext.
     */
    private boolean tryCheckAllMultiAxisSlices(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                                  @NotNull StructureOrientation orientation,
                                                  int[] reps,
                                                  @NotNull PieceRuntime runtime,
                                                  @NotNull StructureMatchSession session) {
        PieceRuntimeState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;
        LongSet allPositions = new LongOpenHashSet();

        if (!visitRepeatOffsets(reps, local -> {
            PatternMatchContext ctx = state.checkPatternAtSnapshotExact(
                    snap, traversal(pieceCenter, orientation, local), session);
            if (ctx == null) {
                runtime.setLastAggregatedContext(null);
                return false;
            }

            Long2ObjectMap<BlockInfo> innerCache = state.cache;
            if (!innerCache.isEmpty()) {
                for (long posLong : innerCache.keySet()) {
                    allPositions.add(posLong);
                }
            }
            return true;
        })) {
            return false;
        }

        runtime.setLastAggregatedContext(session.getContext().copy());
        runtime.publishPositionSet(allPositions);
        return true;
    }

    /**
     * Check each slice of a single-axis repeatable piece individually.
     * Each slice is checked at its own offset along the repeat axis.
     * All slices must pass for the check to succeed.
     * Aggregates "MultiblockParts" from all slices into the runtime's lastAggregatedContext.
     */
    private boolean tryCheckAllSlices(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                        @NotNull StructureOrientation orientation,
                                        int[] reps,
                                        @NotNull PieceRuntime runtime,
                                        @NotNull StructureMatchSession session) {
        PieceRuntimeState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;

        LongSet allPositions = new LongOpenHashSet();

        if (!visitRepeatOffsets(reps, local -> {
            PatternMatchContext ctx = state.checkPatternAtSnapshotExact(
                    snap, traversal(pieceCenter, orientation, local), session);
            if (ctx == null) {
                runtime.setLastAggregatedContext(null);
                return false;
            }

            Long2ObjectMap<BlockInfo> innerCache = state.cache;
            if (!innerCache.isEmpty()) {
                for (long posLong : innerCache.keySet()) {
                    allPositions.add(posLong);
                }
            }
            return true;
        })) {
            return false;
        }

        runtime.setLastAggregatedContext(session.getContext().copy());
        runtime.publishPositionSet(allPositions);
        return true;
    }

    /**
     * Collect positions from the inner state cache into this piece's positions set.
     */
    private void collectSlicePositions(@NotNull BlockPos origin, @NotNull BlockPos pieceOrigin,
                                       @NotNull PieceRuntime runtime) {
        Long2ObjectMap<BlockInfo> innerCache = runtime.getState().cache;
        if (!innerCache.isEmpty()) {
            LongSet newPositions = new LongOpenHashSet();
            BlockPos shift = pieceOrigin.subtract(origin);
            for (long posLong : innerCache.keySet()) {
                BlockPos pos = BlockPos.fromLong(posLong).add(shift);
                newPositions.add(pos.toLong());
            }
            runtime.swapPositions(newPositions);
        }
    }

    /**
     * Auto-build all slices of this repeatable piece.
     * Iterates over all repeat axes and builds each slice individually.
     * <p>
     * Each slice is placed by folding the per-slice step (and the piece's base offset)
     * into the cell loop as a template-local offset passed to
     * {@link PieceRuntimeState#autoBuildAt(EntityPlayer, MultiblockControllerBase, BlockPos,
     * int, int, int, Map, boolean)}. {@code setActualRelativeOffset} therefore runs
     * exactly once per cell, so every slice keeps the same orientation — only the
     * per-cell world position shifts along the repeat axis / axes.
     */
    public void autoBuildAtRepeated(@NotNull EntityPlayer player, @NotNull MultiblockControllerBase controller,
                                     @NotNull BlockPos controllerOrigin,
                                     @NotNull StructureOrientation orientation,
                                     @Nullable FormedStructureMetadata prior,
                                     @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                    @NotNull PieceRuntime runtime,
                                    @NotNull AbilityPlacementTracker abilityTracker) {
        autoBuildAtRepeated(player, controller, controllerOrigin, orientation, prior, channelValues,
                skipHatches, runtime, abilityTracker, StructureEvaluationContext.Operation.CREATIVE_BUILD);
    }

    public void autoBuildAtRepeated(@NotNull EntityPlayer player, @NotNull MultiblockControllerBase controller,
                                    @NotNull BlockPos controllerOrigin,
                                    @NotNull StructureOrientation orientation,
                                    @Nullable FormedStructureMetadata prior,
                                    @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                    @NotNull PieceRuntime runtime,
                                    @NotNull AbilityPlacementTracker abilityTracker,
                                     @NotNull StructureEvaluationContext.Operation operation) {
        autoBuildAtRepeatedWithResult(player, controller, controllerOrigin, orientation, prior,
                channelValues, skipHatches, runtime, abilityTracker, operation, ItemStack.EMPTY);
    }

    @NotNull
    public StructureBuildResult autoBuildAtRepeatedWithResult(@NotNull EntityPlayer player,
                                                              @NotNull MultiblockControllerBase controller,
                                                              @NotNull BlockPos controllerOrigin,
                                                              @NotNull StructureOrientation orientation,
                                                              @Nullable FormedStructureMetadata prior,
                                                              @Nullable Map<String, Integer> channelValues,
                                                               boolean skipHatches,
                                                               @NotNull PieceRuntime runtime,
                                                               @NotNull AbilityPlacementTracker abilityTracker,
                                                               @NotNull StructureEvaluationContext.Operation operation) {
        return autoBuildAtRepeatedWithResult(player, controller, controllerOrigin, orientation, prior,
                channelValues, skipHatches, runtime, abilityTracker, operation, ItemStack.EMPTY);
    }

    @NotNull
    public StructureBuildResult autoBuildAtRepeatedWithResult(@NotNull EntityPlayer player,
                                                              @NotNull MultiblockControllerBase controller,
                                                              @NotNull BlockPos controllerOrigin,
                                                              @NotNull StructureOrientation orientation,
                                                              @Nullable FormedStructureMetadata prior,
                                                              @Nullable Map<String, Integer> channelValues,
                                                              boolean skipHatches,
                                                              @NotNull PieceRuntime runtime,
                                                              @NotNull AbilityPlacementTracker abilityTracker,
                                                              @NotNull StructureEvaluationContext.Operation operation,
                                                              @NotNull ItemStack triggerStack) {
        int[] reps = resolveRepetitions(channelValues);
        PieceRuntimeState state = runtime.getState();
        // Cache the actual repeat counts on the runtime so subsequent pieces
        // (notably DynamicOffsetPieces anchored to this one) can read them via
        // FormedStructureMetadata. Without this, the auto-build path cannot
        // resolve the anchor's repeat count and the following piece falls
        // back to its static baseOffset.
        runtime.cacheFormedReps(reps);
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        RepeatIterationResult iteration = iterate(
                RepeatIterationRequest.of(controllerOrigin, orientation, prior, channelValues), reps);

        for (StructureCellTraversal traversal : iteration.getTraversals()) {
            result.merge(state.autoBuildAtWithResult(player, controller,
                    traversal,
                    channelValues, skipHatches, abilityTracker, operation, triggerStack));
        }
        return result.build();
    }

    public void spawnHintsAtRepeated(@NotNull World world,
                                     @NotNull MultiblockControllerBase controller,
                                     @NotNull BlockPos controllerOrigin,
                                     @NotNull StructureOrientation orientation,
                                     @Nullable FormedStructureMetadata prior,
                                     @Nullable Map<String, Integer> channelValues,
                                     @NotNull PieceRuntime runtime,
                                     @NotNull ItemStack triggerStack) {
        spawnHintsAtRepeatedWithResult(
                world, controller, controllerOrigin, orientation, prior,
                channelValues, runtime, triggerStack);
    }

    @NotNull
    public StructureHintResult spawnHintsAtRepeatedWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controller,
            @NotNull BlockPos controllerOrigin,
            @NotNull StructureOrientation orientation,
            @Nullable FormedStructureMetadata prior,
            @Nullable Map<String, Integer> channelValues,
            @NotNull PieceRuntime runtime,
            @NotNull ItemStack triggerStack) {
        int[] reps = resolveRepetitions(channelValues);
        PieceRuntimeState state = runtime.getState();
        runtime.cacheFormedReps(reps);
        StructureHintResult.Builder result = StructureHintResult.builder();
        RepeatIterationResult iteration = iterate(
                RepeatIterationRequest.of(controllerOrigin, orientation, prior, channelValues), reps);

        for (StructureCellTraversal traversal : iteration.getTraversals()) {
            result.merge(state.spawnHintsAtWithResult(
                    world, controller, traversal,
                    channelValues, triggerStack));
        }
        return result.build();
    }

    @NotNull
    private static StructureCellTraversal traversal(@NotNull BlockPos pieceCenter,
                                                    @NotNull StructureOrientation orientation,
                                                    @NotNull int[] localOffset) {
        return StructureCellTraversal.at(pieceCenter, orientation)
                .withLocalOffset(localOffset[0], localOffset[1], localOffset[2]);
    }

    @NotNull
    public RepeatIterationResult iterate(@NotNull RepeatIterationRequest request) {
        return iterate(request, resolveRepetitions(request.getChannelValues()));
    }

    @NotNull
    public RepeatIterationResult iterate(@NotNull RepeatIterationRequest request,
                                         @NotNull int[] repetitions) {
        BlockPos pieceCenter = getCenterPos(
                request.getControllerOrigin(), request.getOrientation(), request.getPrior());
        List<StructureCellTraversal> traversals = new ArrayList<>();
        boolean completed = visitRepeatOffsets(repetitions, local -> {
            traversals.add(traversal(pieceCenter, request.getOrientation(), local));
            return true;
        });
        return completed
                ? RepeatIterationResult.completed(repetitions, traversals)
                : RepeatIterationResult.stopped(repetitions, traversals);
    }

    boolean visitRepeatOffsets(@NotNull int[] reps,
                               @NotNull RepeatOffsetVisitor visitor) {
        if (repeatAxes.length == 0) {
            return visitor.visit(new int[] {0, 0, 0});
        }

        int[] currentIndices = new int[repeatAxes.length];
        boolean hasMore = true;
        while (hasMore) {
            int[] local = {0, 0, 0};
            for (int i = 0; i < repeatAxes.length; i++) {
                local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
            }
            if (!visitor.visit(local)) {
                return false;
            }

            hasMore = false;
            for (int i = 0; i < repeatAxes.length; i++) {
                currentIndices[i]++;
                if (currentIndices[i] < reps[i]) {
                    hasMore = true;
                    break;
                }
                currentIndices[i] = 0;
            }
        }
        return true;
    }

    @NotNull
    private int[] resolveRepetitions(@Nullable Map<String, Integer> channelValues) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = repeatRanges[i][1];
        }
        if (channelValues != null && repeatChannelNames != null) {
            for (int i = 0; i < repeatChannelNames.length && i < repeatAxes.length; i++) {
                String name = repeatChannelNames[i];
                if (name != null && channelValues.containsKey(name)) {
                    int val = channelValues.get(name);
                    reps[i] = PieceRuntimeState.resolveRepetitionValue(
                            val, repeatRanges[i][0], repeatRanges[i][1]);
                }
            }
        }
        return reps;
    }

    /**
     * Get the repeat channel names for this repeatable piece.
     */
    @Nullable
    public String[] getRepeatChannelNames() {
        return repeatChannelNames;
    }

    /**
     * Get the repeat ranges [min, max] for each repeat axis.
     */
    public int[][] getRepeatRanges() {
        return repeatRanges;
    }

    public int[] getRepeatAxes() {
        return repeatAxes;
    }

    public int[] getStepSizes() {
        return stepSizes;
    }

    public StructureCompiler.SearchStrategy getSearchStrategy() {
        return strategy;
    }
}
