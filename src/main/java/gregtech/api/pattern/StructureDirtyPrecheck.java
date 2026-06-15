package gregtech.api.pattern;

import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detached dirty-piece precheck contract.
 *
 * <p>The plan contains only immutable block-state expectations copied from a
 * committed graph. Snapshot capture happens on the server thread. The returned
 * snapshot contains no {@link World}, controller, or tile-entity references and
 * is therefore safe to compare on a worker thread.
 */
public final class StructureDirtyPrecheck {

    private final long graphGeneration;
    @NotNull
    private final Set<String> dirtyRoots;
    @NotNull
    private final Map<Long, IBlockState> expectedStates;
    @Nullable
    private final BlockPos minCorner;
    @Nullable
    private final BlockPos maxCorner;

    @Nullable
    public static StructureDirtyPrecheck create(
            @NotNull CommittedStructureGraph graph,
            @NotNull Set<String> dirtyRoots) {
        Map<Long, IBlockState> expectedStates = new LinkedHashMap<>();
        PieceRuntimes.Publication runtimePublication = graph.getRuntimePublication();

        for (String root : dirtyRoots) {
            PieceEvaluationResult result = graph.getResultTable().get(root);
            if (result == null) {
                return null;
            }
            if (!result.isActive()) {
                continue;
            }

            PieceRuntime.Publication piecePublication =
                    runtimePublication.get(result.getPiece());
            if (piecePublication == null) {
                return null;
            }
            Map<Long, BlockInfo> cachedBlocks = piecePublication.copyCachedBlocks();
            for (long posLong : result.getWatchedPositions()) {
                BlockInfo cached = cachedBlocks.get(posLong);
                if (cached == null || cached.getBlockState() == null) {
                    return null;
                }
                IBlockState previous = expectedStates.putIfAbsent(
                        posLong, cached.getBlockState());
                if (previous != null && !previous.equals(cached.getBlockState())) {
                    return null;
                }
            }
        }

        if (expectedStates.isEmpty()) {
            return null;
        }
        return new StructureDirtyPrecheck(
                graph.getGeneration(), dirtyRoots, expectedStates);
    }

    private StructureDirtyPrecheck(
            long graphGeneration,
            @NotNull Set<String> dirtyRoots,
            @NotNull Map<Long, IBlockState> expectedStates) {
        this.graphGeneration = graphGeneration;
        this.dirtyRoots = Collections.unmodifiableSet(
                new LinkedHashSet<>(dirtyRoots));
        this.expectedStates = Collections.unmodifiableMap(
                new LinkedHashMap<>(expectedStates));

        BlockPos min = null;
        BlockPos max = null;
        for (long posLong : expectedStates.keySet()) {
            BlockPos pos = BlockPos.fromLong(posLong);
            min = min == null ? pos : new BlockPos(
                    Math.min(min.getX(), pos.getX()),
                    Math.min(min.getY(), pos.getY()),
                    Math.min(min.getZ(), pos.getZ()));
            max = max == null ? pos : new BlockPos(
                    Math.max(max.getX(), pos.getX()),
                    Math.max(max.getY(), pos.getY()),
                    Math.max(max.getZ(), pos.getZ()));
        }
        this.minCorner = min;
        this.maxCorner = max;
    }

    public long getGraphGeneration() {
        return graphGeneration;
    }

    @NotNull
    public Set<String> getDirtyRoots() {
        return dirtyRoots;
    }

    public int getPositionCount() {
        return expectedStates.size();
    }

    @Nullable
    public BlockPos getMinCorner() {
        return minCorner;
    }

    @Nullable
    public BlockPos getMaxCorner() {
        return maxCorner;
    }

    /**
     * Capture the exact expected positions from the live world.
     *
     * @return a detached snapshot, or null when any required position is not loaded
     */
    @Nullable
    public Snapshot capture(@NotNull World world) {
        Map<Long, IBlockState> states = new LinkedHashMap<>();
        for (long posLong : expectedStates.keySet()) {
            BlockPos pos = BlockPos.fromLong(posLong);
            if (!world.isBlockLoaded(pos)) {
                return null;
            }
            states.put(posLong, world.getBlockState(pos));
        }
        return new Snapshot(states);
    }

    @NotNull
    public Result evaluate(@NotNull Snapshot snapshot) {
        if (snapshot.states.size() != expectedStates.size()) {
            return new Result(graphGeneration, dirtyRoots, expectedStates.size(), false);
        }
        for (Map.Entry<Long, IBlockState> entry : expectedStates.entrySet()) {
            if (!entry.getValue().equals(snapshot.states.get(entry.getKey()))) {
                return new Result(graphGeneration, dirtyRoots, expectedStates.size(), false);
            }
        }
        return new Result(graphGeneration, dirtyRoots, expectedStates.size(), true);
    }

    /**
     * Immutable worker-thread input. It intentionally stores block states only.
     */
    public static final class Snapshot {

        @NotNull
        private final Map<Long, IBlockState> states;

        private Snapshot(@NotNull Map<Long, IBlockState> states) {
            this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        }
    }

    /**
     * Immutable precheck signal consumed by the main-thread live confirmation.
     */
    public static final class Result {

        private final long graphGeneration;
        @NotNull
        private final Set<String> dirtyRoots;
        private final int comparedPositions;
        private final boolean matchedBaseline;

        private Result(long graphGeneration,
                       @NotNull Set<String> dirtyRoots,
                       int comparedPositions,
                       boolean matchedBaseline) {
            this.graphGeneration = graphGeneration;
            this.dirtyRoots = Collections.unmodifiableSet(
                    new LinkedHashSet<>(dirtyRoots));
            this.comparedPositions = comparedPositions;
            this.matchedBaseline = matchedBaseline;
        }

        public long getGraphGeneration() {
            return graphGeneration;
        }

        @NotNull
        public Set<String> getDirtyRoots() {
            return dirtyRoots;
        }

        public int getComparedPositions() {
            return comparedPositions;
        }

        public boolean matchedBaseline() {
            return matchedBaseline;
        }
    }
}
