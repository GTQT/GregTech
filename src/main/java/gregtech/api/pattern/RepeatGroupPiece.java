package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCompiler;
import gregtech.api.util.BlockInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * contract that {@link StructurePiece} uses for {@link MultiblockState} — it
 * keeps the compiled piece template stateless and safe to share across
 * controllers of the same multiblock type.
 *
 * <h2>Search strategies</h2>
 * Driven by {@link StructureCompiler.SearchStrategy}:
 * <ul>
 *   <li>{@link StructureCompiler.SearchStrategy#SLIDING_1D} — single 1D sliding window</li>
 *   <li>{@link StructureCompiler.SearchStrategy#INDEPENDENT_1D} — independent 1D per axis (tensor product)</li>
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

    public RepeatGroupPiece(@NotNull String name, @NotNull BlockPatternTemplate tpl,
                            @NotNull Vec3i offset, @NotNull OffsetMode mode,
                            @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset,
                            @NotNull StructureCompiler.SearchStrategy strategy) {
        // The 6-arg StructurePiece constructor binds a SnapshotChecker closure that
        // receives the per-controller PieceRuntime as its last argument, so the
        // per-instance state (cache / lastSuccessReps / lastAggregatedContext) all
        // lives on the runtime and the piece itself stays stateless.
        super(name, tpl, offset, mode, cond, RepeatGroupPiece::checkOnSnapshotDispatch);
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
        super(name, tpl, offset, mode, cond, RepeatGroupPiece::checkOnSnapshotDispatch);
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
     * {@link MultiblockState} plus the per-piece search cache. We forward to the
     * instance method on the supplied piece reference.
     */
    private static boolean checkOnSnapshotDispatch(@NotNull IBlockAccess snap,
                                                   @NotNull BlockPos origin,
                                                   @NotNull EnumFacing front,
                                                   @NotNull EnumFacing up,
                                                   boolean flipped,
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
                    snap, origin, front, up, flipped, prior, runtime, session);
        }
        return false;
    }

    /**
     * Instance-level multi-axis snapshot check. Receives the per-controller
     * {@link PieceRuntime} and uses its state cache.
     */
    private boolean checkOnSnapshotImpl(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                        @NotNull EnumFacing front, @NotNull EnumFacing up,
                                         boolean flipped,
                                         @Nullable FormedStructureMetadata prior,
                                         @NotNull PieceRuntime runtime,
                                         @NotNull StructureMatchSession session) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;
        BlockPos pieceCenter = getCenterPos(origin, front, up, flipped, prior);

        // Formed state: O(1) verification with prior
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeats(
                    snap, pieceCenter, front, up, flipped, priorReps, runtime, session)) {
                runtime.cacheFormedReps(priorReps);
                return true;
            }
            // Prior failed, fall through to full search
        }

        // Building state: dispatch by strategy
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1D(snap, pieceCenter, front, up, flipped, runtime, session);
                break;
            case INDEPENDENT_1D:
                ok = searchIndependent1D(snap, pieceCenter, front, up, flipped, runtime, session);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxes(0, new int[repeatAxes.length],
                        snap, pieceCenter, front, up, flipped, runtime, session);
                break;
        }
        return ok;
    }

    /**
     * Synchronous check using World (main thread).
     * Uses {@link MultiblockState#checkPatternFastAt} for each slice, which supports
     * cache-based fast path and proper World-level block access.
     *
     * @param runtime the per-controller state holder for this piece
     * @return true if the structure matches at some repeat count
     */
    public boolean checkSync(@NotNull World world, @NotNull BlockPos origin,
                             @NotNull EnumFacing front, @NotNull EnumFacing up,
                             boolean flipped,
                             @Nullable FormedStructureMetadata prior,
                             @NotNull PieceRuntime runtime) {
        StructureMatchSession session = new StructureMatchSession();
        return checkSync(world, origin, front, up, flipped, prior, runtime, session)
                && session.validate(false).success;
    }

    public boolean checkSync(@NotNull World world, @NotNull BlockPos origin,
                             @NotNull EnumFacing front, @NotNull EnumFacing up,
                             boolean flipped,
                             @Nullable FormedStructureMetadata prior,
                             @NotNull PieceRuntime runtime,
                             @NotNull StructureMatchSession session) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;
        BlockPos pieceCenter = getCenterPos(origin, front, up, flipped, prior);

        // Formed state: O(1) verification with prior
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeatsWorld(
                    world, pieceCenter, front, up, flipped, priorReps, runtime, session)) {
                runtime.cacheFormedReps(priorReps);
                return true;
            }
        }

        // Building state: dispatch by strategy using World-based search
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1DWorld(world, pieceCenter, front, up, flipped, runtime, session);
                break;
            case INDEPENDENT_1D:
                // INDEPENDENT_1D still uses snapshot path for axis line checks
                // (tensor product optimization doesn't benefit from World-level access)
                ok = searchIndependent1D(world, pieceCenter, front, up, flipped, runtime, session);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxesWorld(0, new int[repeatAxes.length],
                        world, pieceCenter, front, up, flipped, runtime, session);
                break;
        }
        return ok;
    }

    // --- World-based search methods (synchronous, main thread) ---

    /**
     * Single 1D sliding window search using World (synchronous path).
     */
    private boolean searchSliding1DWorld(@NotNull World world, @NotNull BlockPos origin,
                                          @NotNull EnumFacing front, @NotNull EnumFacing up,
                                          boolean flipped,
                                          @NotNull PieceRuntime runtime,
                                          @NotNull StructureMatchSession session) {
        int min = repeatRanges[0][0], max = repeatRanges[0][1];
        for (int r = max; r >= min; r--) {
            int[] reps = new int[]{r};
            if (tryCheckAtRepeatsWorld(
                    world, origin, front, up, flipped, reps, runtime, session)) {
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
                                        @NotNull EnumFacing front, @NotNull EnumFacing up,
                                        boolean flipped,
                                        @NotNull PieceRuntime runtime,
                                        @NotNull StructureMatchSession session) {
        if (axisIdx == repeatAxes.length) {
            if (tryCheckAtRepeatsWorld(
                    world, origin, front, up, flipped, currentReps, runtime, session)) {
                runtime.cacheFormedReps(currentReps);
                return true;
            }
            return false;
        }
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        for (int r = max; r >= min; r--) {
            currentReps[axisIdx] = r;
            if (backtrackAxesWorld(axisIdx + 1, currentReps,
                    world, origin, front, up, flipped, runtime, session)) {
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
                                            @NotNull EnumFacing front, @NotNull EnumFacing up,
                                            boolean flipped, int[] reps,
                                            @NotNull PieceRuntime runtime,
                                            @NotNull StructureMatchSession session) {
        StructureMatchSession candidate = session.fork();
        boolean matched = repeatAxes.length == 1
                ? tryCheckAllSlicesWorld(
                        world, origin, front, up, flipped, reps, runtime, candidate)
                : tryCheckAllMultiAxisSlicesWorld(
                        world, origin, front, up, flipped, reps, runtime, candidate);
        if (matched) {
            candidate.commit();
        }
        return matched;
    }

    /**
     * Check all slices of a single-axis repeatable piece using World.
     * Uses exact-orientation checks for every slice.
     */
    private boolean tryCheckAllSlicesWorld(@NotNull World world, @NotNull BlockPos origin,
                                             @NotNull EnumFacing front, @NotNull EnumFacing up,
                                             boolean flipped, int[] reps,
                                             @NotNull PieceRuntime runtime,
                                             @NotNull StructureMatchSession session) {
        int axis = repeatAxes[0];
        int stepSize = stepSizes[0];
        int count = reps[0];
        MultiblockState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;

        LongSet allPositions = new LongOpenHashSet();

        for (int r = 0; r < count; r++) {
            // Fold the per-slice step into the cell loop as a template-local offset, so
            // setActualRelativeOffset runs exactly once per cell. The cached positions
            // stored by checkPatternAt are already in the controller's frame, so no
            // additional shift is needed when copying them into allPositions.
            int[] local = {0, 0, 0};
            local[axis] = stepSize * r;

            PatternMatchContext ctx = state.checkPatternAtExact(
                    world, pieceCenter, front, up, flipped,
                    local[0], local[1], local[2], session);
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
                                                      @NotNull EnumFacing front, @NotNull EnumFacing up,
                                                      boolean flipped, int[] reps,
                                                      @NotNull PieceRuntime runtime,
                                                      @NotNull StructureMatchSession session) {
        MultiblockState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;
        LongSet allPositions = new LongOpenHashSet();

        int[] currentIndices = new int[repeatAxes.length];
        boolean hasMore = true;

        while (hasMore) {
            // Fold the per-slice step (cartesian product over all repeat axes) into the
            // cell loop as a template-local offset, so setActualRelativeOffset runs exactly
            // once per cell. The cached positions stored by checkPatternAt are already in
            // the controller's frame, so no additional shift is needed when copying them
            // into allPositions.
            int[] local = {0, 0, 0};
            for (int i = 0; i < repeatAxes.length; i++) {
                local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
            }

            PatternMatchContext ctx = state.checkPatternAtExact(
                    world, pieceCenter, front, up, flipped,
                    local[0], local[1], local[2], session);
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

            // Advance to next combination (odometer-style increment)
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

        runtime.setLastAggregatedContext(session.getContext().copy());
        runtime.publishPositionSet(allPositions);
        return true;
    }

    /**
     * Single 1D sliding window search (single axis).
     * Equivalent to the old aisleRepeatable sliding window algorithm.
     */
    private boolean searchSliding1D(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                    @NotNull EnumFacing front, @NotNull EnumFacing up,
                                    boolean flipped,
                                    @NotNull PieceRuntime runtime,
                                    @NotNull StructureMatchSession session) {
        int min = repeatRanges[0][0], max = repeatRanges[0][1];
        int[] reps = new int[]{min};
        // Greedy: try max first
        for (int r = max; r >= min; r--) {
            reps[0] = r;
            if (tryCheckAtRepeats(
                    snap, origin, front, up, flipped, reps, runtime, session)) {
                runtime.cacheFormedReps(reps);
                return true;
            }
        }
        return false;
    }

    /**
     * Independent 1D search per axis (multi-axis tensor product).
     * Each axis is searched independently, ~190x faster than backtracking for tensor products.
     */
    private boolean searchIndependent1D(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                        @NotNull EnumFacing front, @NotNull EnumFacing up,
                                        boolean flipped,
                                        @NotNull PieceRuntime runtime,
                                        @NotNull StructureMatchSession session) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = repeatRanges[i][0];
        }
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = searchAxisGreedy(snap, origin, i, reps, front, up, flipped, runtime);
            if (reps[i] < 0) return false; // Any axis failure = whole piece failure
        }
        // Final joint verification (safety net for axis boundary mismatches)
        if (tryCheckAtRepeats(snap, origin, front, up, flipped, reps, runtime, session)) {
            runtime.cacheFormedReps(reps);
            return true;
        }
        return backtrackAxes(0, new int[repeatAxes.length],
                snap, origin, front, up, flipped, runtime, session);
    }

    /**
     * Greedy search along a single axis.
     * Returns the repeat count, or -1 if no valid count found.
     */
    private int searchAxisGreedy(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                 int axisIdx, int[] partialReps,
                                 @NotNull EnumFacing front, @NotNull EnumFacing up,
                                 boolean flipped,
                                 @NotNull PieceRuntime runtime) {
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        for (int r = max; r >= min; r--) {
            partialReps[axisIdx] = r;
            if (tryCheckAxisLine(snap, origin, axisIdx, partialReps, front, up, flipped, runtime)) {
                return r;
            }
        }
        return -1;
    }

    /**
     * 1D slice verification along a specific axis (tensor product optimization).
     * Only checks the cells along the axisIdx direction, not the entire base piece.
     */
    private boolean tryCheckAxisLine(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                     int axisIdx, int[] partialReps,
                                     @NotNull EnumFacing front, @NotNull EnumFacing up,
                                     boolean flipped,
                                     @NotNull PieceRuntime runtime) {
        // Use the world-space piece center (OffsetMode applied) and fold the per-slice
        // step (cartesian over partialReps, with slice 0 at offset 0) into the cell loop
        // as a template-local offset. setActualRelativeOffset therefore runs exactly once
        // per cell.
        BlockPos pieceCenter = origin;
        int[] local = {0, 0, 0};
        local[repeatAxes[axisIdx]] = stepSizes[axisIdx] * (partialReps[axisIdx] - 1);
        return runtime.getState().checkAxisLineFastAtSnapshot(
                snap, pieceCenter, repeatAxes[axisIdx], front, up, flipped,
                local[0], local[1], local[2]);
    }

    /**
     * Nested backtracking search (multi-axis non-tensor).
     * Worst case O(prod(max_i - min_i + 1)), but early termination and greedy ordering
     * make typical cases much faster.
     */
    private boolean backtrackAxes(int axisIdx, int[] currentReps,
                                  @NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                  @NotNull EnumFacing front, @NotNull EnumFacing up,
                                  boolean flipped,
                                  @NotNull PieceRuntime runtime,
                                  @NotNull StructureMatchSession session) {
        if (axisIdx == repeatAxes.length) {
            if (tryCheckAtRepeats(
                    snap, origin, front, up, flipped, currentReps, runtime, session)) {
                runtime.cacheFormedReps(currentReps);
                return true;
            }
            return false;
        }
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        // Greedy: try max first (players most likely build the largest structure)
        for (int r = max; r >= min; r--) {
            currentReps[axisIdx] = r;
            if (backtrackAxes(axisIdx + 1, currentReps,
                    snap, origin, front, up, flipped, runtime, session)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try checking the piece at specific repeat counts.
     * For single-axis repetition, checks each slice individually along the repeat axis.
     * For multi-axis repetition, checks all slice positions across the cartesian product
     * of all repeat axes, aggregating MultiblockParts and positions from every slice.
     */
    private boolean tryCheckAtRepeats(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                      @NotNull EnumFacing front, @NotNull EnumFacing up,
                                      boolean flipped, int[] reps,
                                      @NotNull PieceRuntime runtime,
                                      @NotNull StructureMatchSession session) {
        StructureMatchSession candidate = session.fork();
        boolean matched = repeatAxes.length == 1
                ? tryCheckAllSlices(snap, origin, front, up, flipped, reps, runtime, candidate)
                : tryCheckAllMultiAxisSlices(
                        snap, origin, front, up, flipped, reps, runtime, candidate);
        if (matched) {
            candidate.commit();
        }
        return matched;
    }

    /**
     * Check all slices of a multi-axis repeatable piece.
     * Enumerates the cartesian product of all repeat axes, checking the base piece
     * at each combination of offsets. All slices must pass for the check to succeed.
     * Aggregates "MultiblockParts" from all slices into the runtime's lastAggregatedContext.
     */
    private boolean tryCheckAllMultiAxisSlices(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                                 @NotNull EnumFacing front, @NotNull EnumFacing up,
                                                 boolean flipped, int[] reps,
                                                 @NotNull PieceRuntime runtime,
                                                 @NotNull StructureMatchSession session) {
        MultiblockState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;
        LongSet allPositions = new LongOpenHashSet();

        // Enumerate all combinations of repeat counts (cartesian product)
        // Each axis i ranges from 0 to reps[i]-1
        int[] currentIndices = new int[repeatAxes.length];
        boolean hasMore = true;

        while (hasMore) {
            // Fold the per-slice step (cartesian product over all repeat axes) into the
            // cell loop as a template-local offset, so setActualRelativeOffset runs exactly
            // once per cell. The cached positions stored by checkPatternAtSnapshot are
            // already in the controller's frame, so no additional shift is needed when
            // copying them into allPositions.
            int[] local = {0, 0, 0};
            for (int i = 0; i < repeatAxes.length; i++) {
                local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
            }

            // Check this slice
            PatternMatchContext ctx = state.checkPatternAtSnapshotExact(
                    snap, pieceCenter, front, up, flipped,
                    local[0], local[1], local[2], session);
            if (ctx == null) {
                runtime.setLastAggregatedContext(null);
                return false;
            }

            // Merge MultiblockParts from this slice
            // Collect positions from this slice
            Long2ObjectMap<BlockInfo> innerCache = state.cache;
            if (!innerCache.isEmpty()) {
                for (long posLong : innerCache.keySet()) {
                    allPositions.add(posLong);
                }
            }

            // Advance to next combination (odometer-style increment)
            hasMore = false;
            for (int i = 0; i < repeatAxes.length; i++) {
                currentIndices[i]++;
                if (currentIndices[i] < reps[i]) {
                    hasMore = true;
                    break;
                }
                // Overflow: reset this axis and carry to next
                currentIndices[i] = 0;
            }
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
                                       @NotNull EnumFacing front, @NotNull EnumFacing up,
                                       boolean flipped, int[] reps,
                                       @NotNull PieceRuntime runtime,
                                       @NotNull StructureMatchSession session) {
        int axis = repeatAxes[0];
        int stepSize = stepSizes[0];
        int count = reps[0];
        MultiblockState state = runtime.getState();
        // Use getCenterPos so the piece's OffsetMode is applied to compute the world-space
        // center; the cell loop's template-local slice step (set in `local` below) is the
        // only thing added to each cell — baseOffset is absorbed by pieceCenter.
        BlockPos pieceCenter = origin;

        LongSet allPositions = new LongOpenHashSet();

        for (int r = 0; r < count; r++) {
            // Fold the per-slice step into the cell loop as a template-local offset, so
            // setActualRelativeOffset runs exactly once per cell. The cached positions
            // stored by checkPatternAtSnapshot are already in the controller's frame, so
            // no additional shift is needed when copying them into allPositions.
            int[] local = {0, 0, 0};
            local[axis] = stepSize * r;

            PatternMatchContext ctx = state.checkPatternAtSnapshotExact(
                    snap, pieceCenter, front, up, flipped,
                    local[0], local[1], local[2], session);
            if (ctx == null) {
                runtime.setLastAggregatedContext(null);
                return false;
            }

            // Merge MultiblockParts from this slice into the aggregated context
            // Collect positions from this slice
            Long2ObjectMap<BlockInfo> innerCache = state.cache;
            if (!innerCache.isEmpty()) {
                for (long posLong : innerCache.keySet()) {
                    allPositions.add(posLong);
                }
            }
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
     * {@link MultiblockState#autoBuildAt(EntityPlayer, MultiblockControllerBase, BlockPos,
     * int, int, int, Map, boolean)}. {@code setActualRelativeOffset} therefore runs
     * exactly once per cell, so every slice keeps the same orientation — only the
     * per-cell world position shifts along the repeat axis / axes.
     */
    public void autoBuildAtRepeated(@NotNull EntityPlayer player, @NotNull MultiblockControllerBase controller,
                                    @NotNull BlockPos controllerOrigin, @NotNull EnumFacing front,
                                    @NotNull EnumFacing up, boolean flipped,
                                    @Nullable FormedStructureMetadata prior,
                                    @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                    @NotNull PieceRuntime runtime,
                                    @NotNull AbilityPlacementTracker abilityTracker) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = repeatRanges[i][1];
        }
        if (channelValues != null && repeatChannelNames != null) {
            for (int i = 0; i < repeatChannelNames.length && i < repeatAxes.length; i++) {
                String name = repeatChannelNames[i];
                if (name != null && channelValues.containsKey(name)) {
                    int val = channelValues.get(name);
                    reps[i] = MultiblockState.resolveRepetitionValue(
                            val, repeatRanges[i][0], repeatRanges[i][1]);
                }
            }
        }

        MultiblockState state = runtime.getState();
        // Use the world-space piece center (OffsetMode applied) and fold only the per-slice
        // step into the cell loop as a template-local offset. setActualRelativeOffset
        // therefore runs exactly once per cell.
        BlockPos pieceCenter = getCenterPos(controllerOrigin, front, up, flipped, prior);
        // Cache the actual repeat counts on the runtime so subsequent pieces
        // (notably DynamicOffsetPieces anchored to this one) can read them via
        // FormedStructureMetadata. Without this, the auto-build path cannot
        // resolve the anchor's repeat count and the following piece falls
        // back to its static baseOffset.
        runtime.cacheFormedReps(reps);

        if (repeatAxes.length == 1) {
            int axis = repeatAxes[0];
            int stepSize = stepSizes[0];
            int count = reps[0];
            for (int r = 0; r < count; r++) {
                int[] local = {0, 0, 0};
                local[axis] = stepSize * r;
                state.autoBuildAt(player, controller, pieceCenter,
                        local[0], local[1], local[2],
                        channelValues, skipHatches, abilityTracker);
            }
        } else {
            int[] currentIndices = new int[repeatAxes.length];
            boolean hasMore = true;
            while (hasMore) {
                int[] local = {0, 0, 0};
                for (int i = 0; i < repeatAxes.length; i++) {
                    local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
                }
                state.autoBuildAt(player, controller, pieceCenter,
                        local[0], local[1], local[2],
                        channelValues, skipHatches, abilityTracker);

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
        }
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
}
