package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCompiler;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * <p>Search strategies (selected at compile time by {@link StructureCompiler}):
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

    /** Inner MultiblockState for single-slice checking (not pre-expanded) */
    private final MultiblockState innerState;

    /** Last successful repeat counts (for prior acceleration) */
    @Nullable
    private int[] lastFormedReps;

    /** Aggregated PatternMatchContext from the last successful check */
    @Nullable
    private PatternMatchContext lastAggregatedContext;

    public RepeatGroupPiece(@NotNull String name, @NotNull BlockPatternTemplate tpl,
                            @NotNull Vec3i offset, @NotNull OffsetMode mode,
                            @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset,
                            @NotNull StructureCompiler.SearchStrategy strategy) {
        super(name, tpl, offset, mode, cond);
        this.repeatAxes = axes;
        this.repeatRanges = ranges;
        this.stepSizes = steps;
        this.repeatChannelNames = channelNames;
        this.centerOffset = centerOffset;
        this.strategy = strategy;
        this.innerState = tpl.createState();

        // Bind snapshot checker to this piece's multi-axis implementation
        super.bindSnapshotChecker(this::checkOnSnapshotImpl);
    }

    /**
     * Multi-axis snapshot check entry point.
     * Bound as the snapshot checker closure at construction time.
     */
    private boolean checkOnSnapshotImpl(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                        @NotNull EnumFacing front, @NotNull EnumFacing up,
                                        boolean flipped,
                                        @Nullable FormedStructureMetadata prior) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;

        // Formed state: O(1) verification with prior
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeats(snap, origin, front, up, flipped, priorReps)) {
                this.lastFormedReps = priorReps.clone();
                return true;
            }
            // Prior failed, fall through to full search
        }

        // Building state: dispatch by strategy
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1D(snap, origin, front, up, flipped);
                break;
            case INDEPENDENT_1D:
                ok = searchIndependent1D(snap, origin, front, up, flipped);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxes(0, new int[repeatAxes.length], snap, origin, front, up, flipped);
                break;
        }
        return ok;
    }

    /**
     * Synchronous check using World (main thread).
     * Uses {@link MultiblockState#checkPatternFastAt} for each slice, which supports
     * cache-based fast path and proper World-level block access.
     *
     * @return true if the structure matches at some repeat count
     */
    public boolean checkSync(@NotNull World world, @NotNull BlockPos origin,
                             @NotNull EnumFacing front, @NotNull EnumFacing up,
                             boolean flipped,
                             @Nullable FormedStructureMetadata prior) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;

        // Formed state: O(1) verification with prior
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeatsWorld(world, origin, front, up, flipped, priorReps)) {
                this.lastFormedReps = priorReps.clone();
                return true;
            }
        }

        // Building state: dispatch by strategy using World-based search
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1DWorld(world, origin, front, up, flipped);
                break;
            case INDEPENDENT_1D:
                // INDEPENDENT_1D still uses snapshot path for axis line checks
                // (tensor product optimization doesn't benefit from World-level access)
                ok = searchIndependent1D(world, origin, front, up, flipped);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxesWorld(0, new int[repeatAxes.length], world, origin, front, up, flipped);
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
                                          boolean flipped) {
        int min = repeatRanges[0][0], max = repeatRanges[0][1];
        for (int r = max; r >= min; r--) {
            int[] reps = new int[]{r};
            if (tryCheckAtRepeatsWorld(world, origin, front, up, flipped, reps)) {
                this.lastFormedReps = reps.clone();
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
                                        boolean flipped) {
        if (axisIdx == repeatAxes.length) {
            if (tryCheckAtRepeatsWorld(world, origin, front, up, flipped, currentReps)) {
                this.lastFormedReps = currentReps.clone();
                return true;
            }
            return false;
        }
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        for (int r = max; r >= min; r--) {
            currentReps[axisIdx] = r;
            if (backtrackAxesWorld(axisIdx + 1, currentReps, world, origin, front, up, flipped)) {
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
                                            boolean flipped, int[] reps) {
        if (repeatAxes.length == 1) {
            return tryCheckAllSlicesWorld(world, origin, front, up, flipped, reps);
        }
        return tryCheckAllMultiAxisSlicesWorld(world, origin, front, up, flipped, reps);
    }

    /**
     * Check all slices of a single-axis repeatable piece using World.
     * Uses {@link MultiblockState#checkPatternFastAt} for cache-accelerated checks.
     */
    private boolean tryCheckAllSlicesWorld(@NotNull World world, @NotNull BlockPos origin,
                                             @NotNull EnumFacing front, @NotNull EnumFacing up,
                                             boolean flipped, int[] reps) {
        int axis = repeatAxes[0];
        int stepSize = stepSizes[0];
        int count = reps[0];
        Vec3i baseOffset = super.getOffset();
        RelativeDirection[] structDir = innerState.getTemplate().getStructureDir();

        LongSet allPositions = new LongOpenHashSet();
        PatternMatchContext aggregated = new PatternMatchContext();
        Set<IMultiblockPart> allParts = aggregated.getOrCreate("MultiblockParts", HashSet::new);

        for (int r = 0; r < count; r++) {
            int[] local = {0, 0, 0};
            local[axis] = stepSize * r;
            BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                    local[0], local[1], local[2], front, up, flipped, structDir);
            BlockPos sliceOrigin = origin.add(
                    baseOffset.getX() + worldOffset.getX(),
                    baseOffset.getY() + worldOffset.getY(),
                    baseOffset.getZ() + worldOffset.getZ());

            PatternMatchContext ctx = innerState.checkPatternFastAt(
                    world, sliceOrigin, front, up, flipped);
            if (ctx == null) {
                this.lastAggregatedContext = null;
                return false;
            }

            Set<IMultiblockPart> sliceParts = ctx.getOrCreate("MultiblockParts", HashSet::new);
            allParts.addAll(sliceParts);

            Long2ObjectMap<BlockInfo> innerCache = innerState.cache;
            if (!innerCache.isEmpty()) {
                BlockPos shift = sliceOrigin.subtract(origin);
                for (long posLong : innerCache.keySet()) {
                    BlockPos pos = BlockPos.fromLong(posLong).add(shift);
                    allPositions.add(pos.toLong());
                }
            }
        }

        this.lastAggregatedContext = aggregated;
        super.swapPositions(allPositions);
        return true;
    }

    /**
     * Check all slices of a multi-axis repeatable piece using World.
     * Enumerates the cartesian product of all repeat axes, checking the base piece
     * at each combination of offsets. Uses {@link MultiblockState#checkPatternFastAt}
     * for cache-accelerated checks.
     */
    private boolean tryCheckAllMultiAxisSlicesWorld(@NotNull World world, @NotNull BlockPos origin,
                                                      @NotNull EnumFacing front, @NotNull EnumFacing up,
                                                      boolean flipped, int[] reps) {
        Vec3i baseOffset = super.getOffset();
        RelativeDirection[] structDir = innerState.getTemplate().getStructureDir();
        LongSet allPositions = new LongOpenHashSet();
        PatternMatchContext aggregated = new PatternMatchContext();
        Set<IMultiblockPart> allParts = aggregated.getOrCreate("MultiblockParts", HashSet::new);

        int[] currentIndices = new int[repeatAxes.length];
        boolean hasMore = true;

        while (hasMore) {
            int[] local = {0, 0, 0};
            for (int i = 0; i < repeatAxes.length; i++) {
                local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
            }
            BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                    local[0], local[1], local[2], front, up, flipped, structDir);
            BlockPos sliceOrigin = origin.add(
                    baseOffset.getX() + worldOffset.getX(),
                    baseOffset.getY() + worldOffset.getY(),
                    baseOffset.getZ() + worldOffset.getZ());

            PatternMatchContext ctx = innerState.checkPatternFastAt(
                    world, sliceOrigin, front, up, flipped);
            if (ctx == null) {
                this.lastAggregatedContext = null;
                return false;
            }

            Set<IMultiblockPart> sliceParts = ctx.getOrCreate("MultiblockParts", HashSet::new);
            allParts.addAll(sliceParts);

            Long2ObjectMap<BlockInfo> innerCache = innerState.cache;
            if (!innerCache.isEmpty()) {
                BlockPos shift = sliceOrigin.subtract(origin);
                for (long posLong : innerCache.keySet()) {
                    BlockPos pos = BlockPos.fromLong(posLong).add(shift);
                    allPositions.add(pos.toLong());
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

        this.lastAggregatedContext = aggregated;
        super.swapPositions(allPositions);
        return true;
    }

    /**
     * Single 1D sliding window search (single axis).
     * Equivalent to the old aisleRepeatable sliding window algorithm.
     */
    private boolean searchSliding1D(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                    @NotNull EnumFacing front, @NotNull EnumFacing up,
                                    boolean flipped) {
        int min = repeatRanges[0][0], max = repeatRanges[0][1];
        int[] reps = new int[]{min};
        // Greedy: try max first
        for (int r = max; r >= min; r--) {
            reps[0] = r;
            if (tryCheckAtRepeats(snap, origin, front, up, flipped, reps)) {
                this.lastFormedReps = reps.clone();
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
                                        boolean flipped) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = searchAxisGreedy(snap, origin, i, reps, front, up, flipped);
            if (reps[i] < 0) return false; // Any axis failure = whole piece failure
        }
        // Final joint verification (safety net for axis boundary mismatches)
        if (tryCheckAtRepeats(snap, origin, front, up, flipped, reps)) {
            this.lastFormedReps = reps.clone();
            return true;
        }
        return false;
    }

    /**
     * Greedy search along a single axis.
     * Returns the repeat count, or -1 if no valid count found.
     */
    private int searchAxisGreedy(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                 int axisIdx, int[] partialReps,
                                 @NotNull EnumFacing front, @NotNull EnumFacing up,
                                 boolean flipped) {
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        for (int r = max; r >= min; r--) {
            partialReps[axisIdx] = r;
            if (tryCheckAxisLine(snap, origin, axisIdx, partialReps, front, up, flipped)) {
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
                                     boolean flipped) {
        BlockPos pieceOrigin = computePieceOrigin(origin, partialReps, front, up, flipped);
        // Delegate to MultiblockState's 1D slice check
        return innerState.checkAxisLineFastAtSnapshot(
                snap, pieceOrigin, repeatAxes[axisIdx], front, up, flipped);
    }

    /**
     * Nested backtracking search (multi-axis non-tensor).
     * Worst case O(prod(max_i - min_i + 1)), but early termination and greedy ordering
     * make typical cases much faster.
     */
    private boolean backtrackAxes(int axisIdx, int[] currentReps,
                                  @NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                  @NotNull EnumFacing front, @NotNull EnumFacing up,
                                  boolean flipped) {
        if (axisIdx == repeatAxes.length) {
            if (tryCheckAtRepeats(snap, origin, front, up, flipped, currentReps)) {
                this.lastFormedReps = currentReps.clone();
                return true;
            }
            return false;
        }
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        // Greedy: try max first (players most likely build the largest structure)
        for (int r = max; r >= min; r--) {
            currentReps[axisIdx] = r;
            if (backtrackAxes(axisIdx + 1, currentReps, snap, origin, front, up, flipped)) {
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
                                      boolean flipped, int[] reps) {
        if (repeatAxes.length == 1) {
            // Single-axis: check each slice along the repeat axis
            return tryCheckAllSlices(snap, origin, front, up, flipped, reps);
        }

        // Multi-axis: check all slice positions across all repeat axes
        return tryCheckAllMultiAxisSlices(snap, origin, front, up, flipped, reps);
    }

    /**
     * Check all slices of a multi-axis repeatable piece.
     * Enumerates the cartesian product of all repeat axes, checking the base piece
     * at each combination of offsets. All slices must pass for the check to succeed.
     * Aggregates "MultiblockParts" from all slices into lastAggregatedContext.
     */
    private boolean tryCheckAllMultiAxisSlices(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                                 @NotNull EnumFacing front, @NotNull EnumFacing up,
                                                 boolean flipped, int[] reps) {
        Vec3i baseOffset = super.getOffset();
        RelativeDirection[] structDir = innerState.getTemplate().getStructureDir();
        LongSet allPositions = new LongOpenHashSet();

        // Aggregate context from all slices
        PatternMatchContext aggregated = new PatternMatchContext();
        Set<IMultiblockPart> allParts = aggregated.getOrCreate("MultiblockParts", HashSet::new);

        // Enumerate all combinations of repeat counts (cartesian product)
        // Each axis i ranges from 0 to reps[i]-1
        int[] currentIndices = new int[repeatAxes.length];
        boolean hasMore = true;

        while (hasMore) {
            // Compute offset for this combination
            int[] local = {0, 0, 0};
            for (int i = 0; i < repeatAxes.length; i++) {
                local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
            }
            BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                    local[0], local[1], local[2], front, up, flipped, structDir);
            BlockPos sliceOrigin = origin.add(
                    baseOffset.getX() + worldOffset.getX(),
                    baseOffset.getY() + worldOffset.getY(),
                    baseOffset.getZ() + worldOffset.getZ());

            // Check this slice
            PatternMatchContext ctx = innerState.checkPatternFastAtSnapshot(
                    snap, sliceOrigin, front, up, flipped);
            if (ctx == null) {
                this.lastAggregatedContext = null;
                return false;
            }

            // Merge MultiblockParts from this slice
            Set<IMultiblockPart> sliceParts = ctx.getOrCreate("MultiblockParts", HashSet::new);
            allParts.addAll(sliceParts);

            // Collect positions from this slice
            Long2ObjectMap<BlockInfo> innerCache = innerState.cache;
            if (!innerCache.isEmpty()) {
                BlockPos shift = sliceOrigin.subtract(origin);
                for (long posLong : innerCache.keySet()) {
                    BlockPos pos = BlockPos.fromLong(posLong).add(shift);
                    allPositions.add(pos.toLong());
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

        this.lastAggregatedContext = aggregated;
        super.swapPositions(allPositions);
        return true;
    }

    /**
     * Check each slice of a single-axis repeatable piece individually.
     * Each slice is checked at its own offset along the repeat axis.
     * All slices must pass for the check to succeed.
     * Aggregates "MultiblockParts" from all slices into lastAggregatedContext.
     */
    private boolean tryCheckAllSlices(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                       @NotNull EnumFacing front, @NotNull EnumFacing up,
                                       boolean flipped, int[] reps) {
        int axis = repeatAxes[0];
        int stepSize = stepSizes[0];
        int count = reps[0];
        Vec3i baseOffset = super.getOffset();
        RelativeDirection[] structDir = innerState.getTemplate().getStructureDir();

        LongSet allPositions = new LongOpenHashSet();

        // Aggregate context from all slices
        PatternMatchContext aggregated = new PatternMatchContext();
        Set<IMultiblockPart> allParts = aggregated.getOrCreate("MultiblockParts", HashSet::new);

        for (int r = 0; r < count; r++) {
            int[] local = {0, 0, 0};
            local[axis] = stepSize * r;
            BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                    local[0], local[1], local[2], front, up, flipped, structDir);
            BlockPos sliceOrigin = origin.add(
                    baseOffset.getX() + worldOffset.getX(),
                    baseOffset.getY() + worldOffset.getY(),
                    baseOffset.getZ() + worldOffset.getZ());

            PatternMatchContext ctx = innerState.checkPatternFastAtSnapshot(
                    snap, sliceOrigin, front, up, flipped);
            if (ctx == null) {
                this.lastAggregatedContext = null;
                return false;
            }

            // Merge MultiblockParts from this slice into the aggregated context
            Set<IMultiblockPart> sliceParts = ctx.getOrCreate("MultiblockParts", HashSet::new);
            allParts.addAll(sliceParts);

            // Collect positions from this slice
            Long2ObjectMap<BlockInfo> innerCache = innerState.cache;
            if (!innerCache.isEmpty()) {
                BlockPos shift = sliceOrigin.subtract(origin);
                for (long posLong : innerCache.keySet()) {
                    BlockPos pos = BlockPos.fromLong(posLong).add(shift);
                    allPositions.add(pos.toLong());
                }
            }
        }

        this.lastAggregatedContext = aggregated;
        super.swapPositions(allPositions);
        return true;
    }

    /**
     * Collect positions from the inner state cache into this piece's positions set.
     */
    private void collectSlicePositions(@NotNull BlockPos origin, @NotNull BlockPos pieceOrigin) {
        Long2ObjectMap<BlockInfo> innerCache = innerState.cache;
        if (!innerCache.isEmpty()) {
            LongSet newPositions = new LongOpenHashSet();
            BlockPos shift = pieceOrigin.subtract(origin);
            for (long posLong : innerCache.keySet()) {
                BlockPos pos = BlockPos.fromLong(posLong).add(shift);
                newPositions.add(pos.toLong());
            }
            super.swapPositions(newPositions);
        }
    }

    /**
     * Compute the piece origin (center position) for given repeat counts.
     */
    @NotNull
    private BlockPos computePieceOrigin(@NotNull BlockPos controllerOrigin, int[] reps,
                                         @NotNull EnumFacing front, @NotNull EnumFacing up,
                                         boolean flipped) {
        int[] local = {0, 0, 0};
        for (int i = 0; i < repeatAxes.length; i++) {
            local[repeatAxes[i]] += stepSizes[i] * (reps[i] - 1);
        }
        RelativeDirection[] structDir = innerState.getTemplate().getStructureDir();
        BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                local[0], local[1], local[2], front, up, flipped, structDir);
        Vec3i offset = super.getOffset();
        return controllerOrigin.add(
                offset.getX() + worldOffset.getX(),
                offset.getY() + worldOffset.getY(),
                offset.getZ() + worldOffset.getZ());
    }

    /**
     * Auto-build all slices of this repeatable piece.
     * Iterates over all repeat axes and builds each slice individually.
     */
    public void autoBuildAtRepeated(@NotNull EntityPlayer player, @NotNull MultiblockControllerBase controller,
                                    @NotNull BlockPos controllerOrigin, @NotNull EnumFacing front,
                                    @NotNull EnumFacing up, boolean flipped,
                                    @Nullable Map<String, Integer> channelValues, boolean skipHatches) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = repeatRanges[i][0];
        }
        if (channelValues != null && repeatChannelNames != null) {
            for (int i = 0; i < repeatChannelNames.length && i < repeatAxes.length; i++) {
                String name = repeatChannelNames[i];
                if (name != null && channelValues.containsKey(name)) {
                    int val = channelValues.get(name);
                    reps[i] = Math.max(repeatRanges[i][0], Math.min(repeatRanges[i][1], val));
                }
            }
        }

        RelativeDirection[] structDir = innerState.getTemplate().getStructureDir();
        Vec3i baseOffset = super.getOffset();

        if (repeatAxes.length == 1) {
            int axis = repeatAxes[0];
            int stepSize = stepSizes[0];
            int count = reps[0];
            for (int r = 0; r < count; r++) {
                int[] local = {0, 0, 0};
                local[axis] = stepSize * r;
                BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                        local[0], local[1], local[2], front, up, flipped, structDir);
                BlockPos sliceOrigin = controllerOrigin.add(
                        baseOffset.getX() + worldOffset.getX(),
                        baseOffset.getY() + worldOffset.getY(),
                        baseOffset.getZ() + worldOffset.getZ());
                innerState.autoBuildAt(player, controller, sliceOrigin, channelValues, skipHatches);
            }
        } else {
            int[] currentIndices = new int[repeatAxes.length];
            boolean hasMore = true;
            while (hasMore) {
                int[] local = {0, 0, 0};
                for (int i = 0; i < repeatAxes.length; i++) {
                    local[repeatAxes[i]] += stepSizes[i] * currentIndices[i];
                }
                BlockPos worldOffset = RelativeDirection.setActualRelativeOffset(
                        local[0], local[1], local[2], front, up, flipped, structDir);
                BlockPos sliceOrigin = controllerOrigin.add(
                        baseOffset.getX() + worldOffset.getX(),
                        baseOffset.getY() + worldOffset.getY(),
                        baseOffset.getZ() + worldOffset.getZ());
                innerState.autoBuildAt(player, controller, sliceOrigin, channelValues, skipHatches);

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

    @Override
    public void cacheFormedReps(int[] reps) {
        this.lastFormedReps = reps.clone();
    }

    @Override
    @Nullable
    public int[] getLastFormedReps() {
        return lastFormedReps;
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

    /**
     * Get the aggregated PatternMatchContext from the last successful check.
     * Contains merged "MultiblockParts" from all repeated slices.
     */
    @Nullable
    public PatternMatchContext getLastAggregatedContext() {
        return lastAggregatedContext;
    }

    @Override
    public void reset() {
        super.reset();
        this.innerState.clearCache();
        this.lastAggregatedContext = null;
    }
}
