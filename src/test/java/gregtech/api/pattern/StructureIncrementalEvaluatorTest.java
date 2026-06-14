package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureIncrementalEvaluatorTest {

    private static final StructureContributionKey<Integer, Integer> TEST_VALUE =
            StructureContributionKey.lastNonNull("gregtech:test_value");
    private static final World WORLD = bareWorld();
    private static final StructureOrientation ORIENTATION = StructureOrientation.of(
            EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false);

    @Test
    void fullCheckPublishesCommittedGraph() {
        StructureRuntime runtime = runtime(pattern(
                piece("a", new CountingElement(true)),
                piece("b", new CountingElement(true))));

        StructureCheckResult result = runtime.check(checkRequest());

        assertTrue(result.isMatched());
        assertNotNull(result.getGraphPublication());
        assertEquals(2, result.getGraphPublication().getResultTable().size());
        assertNotNull(result.getGraphPublication().getRuntimePublication());
    }

    @Test
    void incrementalRechecksDirtyRootAndReusesIndependentPieces() {
        CountingElement firstElement = new CountingElement(true);
        CountingElement secondElement = new CountingElement(true);
        CountingElement thirdElement = new CountingElement(true);
        MultiPiecePattern pattern = pattern(
                piece("first", firstElement),
                piece("second", secondElement),
                piece("third", thirdElement));
        StructureRuntime runtime = runtime(pattern);
        StructureCheckResult full = runtime.check(checkRequest());
        CommittedStructureGraph baseline = full.getGraphPublication();
        assertNotNull(baseline);
        runtime.publishCommittedGraph(baseline);
        assertTrue(runtime.addDirtyRoot("second"));

        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertNotNull(incremental.getGraphPublication());
        assertSame(baseline.getAggregate(), incremental.getContributionAggregate());
        assertEquals(1, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(2, incremental.getIncrementalCheckResult().getReusedPieces());
        assertTrue(incremental.getIncrementalCheckResult().wasSnapshotPrecheckAttempted());
        assertEquals(1, firstElement.calls.get());
        assertEquals(2, secondElement.calls.get());
        assertEquals(1, thirdElement.calls.get());
    }

    @Test
    void dynamicAnchorDirtyRootRechecksDependentPiece() {
        CountingElement anchorElement = new CountingElement(true);
        CountingElement topElement = new CountingElement(true);
        RepeatGroupPiece anchor = repeatPiece("body", anchorElement);
        StructurePiece top = new DynamicOffsetPiece(
                "top", template(topElement), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, null, "body", new int[] {0, 0, 1});
        MultiPiecePattern pattern = pattern(anchor, top);
        StructureRuntime runtime = runtime(pattern);
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());
        assertTrue(runtime.addDirtyRoot("body"));

        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertEquals(1, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(1, incremental.getIncrementalCheckResult().getReusedPieces());
        assertTrue(incremental.getIncrementalCheckResult().getDependencyClosure().contains("body"));
        assertTrue(incremental.getIncrementalCheckResult().getDependencyClosure().contains("top"));
        assertTrue(incremental.getIncrementalCheckResult().getPrunedPieces().contains("top"));
        assertEquals(2, anchorElement.calls.get());
        assertEquals(1, topElement.calls.get());
    }

    @Test
    void changedDependencyAspectRechecksDependentPiece() {
        AtomicInteger emittedValue = new AtomicInteger(1);
        EmittingElement firstElement = new EmittingElement(true, emittedValue);
        CountingElement secondElement = new CountingElement(true);
        StructureCondition<Object> condition = StructureCondition.withDependencies(
                context -> true,
                StructureDependency.piece("first", PieceDependencyAspect.CONTRIBUTION_VALUE));
        StructurePiece first = piece("first", firstElement);
        StructurePiece second = new StructurePiece(
                "second", template(secondElement), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructureRuntime runtime = runtime(pattern(first, second));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());
        emittedValue.set(2);
        assertTrue(runtime.addDirtyRoot("first"));

        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertEquals(2, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertFalse(incremental.getIncrementalCheckResult().getPrunedPieces().contains("second"));
        assertEquals(2, firstElement.calls());
        assertEquals(2, secondElement.calls.get());
    }

    @Test
    void incrementalFailureDoesNotPublishSuccessorGraph() {
        CountingElement firstElement = new CountingElement(true);
        CountingElement secondElement = new CountingElement(true);
        MultiPiecePattern pattern = pattern(
                piece("first", firstElement),
                piece("second", secondElement));
        StructureRuntime runtime = runtime(pattern);
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());
        long baselineGeneration = runtime.getCommittedGraph().getGeneration();
        secondElement.matches = false;
        assertTrue(runtime.addDirtyRoot("second"));

        StructureCheckResult failed = runtime.checkIncremental(checkRequest());

        assertFalse(failed.isMatched());
        assertTrue(failed.usedIncrementalEvaluator());
        assertNull(failed.getGraphPublication());
        assertEquals(baselineGeneration, runtime.getCommittedGraph().getGeneration());
        assertEquals(1, failed.getIncrementalCheckResult().getRecheckedPieces());
    }

    @Test
    void positionIndexReportsOverlappingOwners() {
        StructurePiece first = piece("first", new CountingElement(true));
        StructurePiece second = piece("second", new CountingElement(true));
        MultiPiecePattern pattern = pattern(first, second);
        LongOpenHashSet firstPositions = new LongOpenHashSet();
        LongOpenHashSet secondPositions = new LongOpenHashSet();
        long watched = BlockPos.ORIGIN.toLong();
        firstPositions.add(watched);
        secondPositions.add(watched);

        StructureResultTable table = StructureResultTable.builder(pattern)
                .add(PieceEvaluationResult.activeMatched(
                        first, BlockPos.ORIGIN, null, firstPositions, firstPositions,
                        StructureContribution.empty()))
                .add(PieceEvaluationResult.activeMatched(
                        second, BlockPos.ORIGIN, null, secondPositions, secondPositions,
                        StructureContribution.empty()))
                .build();
        StructurePositionIndex index = StructurePositionIndex.fromResultTable(pattern, table);

        assertEquals(Arrays.asList("first", "second"), index.getOwners(watched));
        assertEquals(2, index.getOwnerBits(watched).cardinality());
    }

    private static StructureRuntime runtime(@NotNull MultiPiecePattern pattern) {
        return new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));
    }

    private static MultiPiecePattern pattern(@NotNull StructurePiece... pieces) {
        return new MultiPiecePattern(Arrays.asList(pieces));
    }

    private static StructurePiece piece(@NotNull String name,
                                        @NotNull CountingElement element) {
        return new StructurePiece(
                name, template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
    }

    private static RepeatGroupPiece repeatPiece(@NotNull String name,
                                                @NotNull CountingElement element) {
        return new RepeatGroupPiece(
                name,
                template(element),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null,
                new int[] {2},
                new int[][] {{1, 1}},
                new int[] {1},
                null,
                new int[] {0, 0, 0},
                gregtech.api.pattern.element.StructureCompiler.SearchStrategy.SLIDING_1D);
    }

    private static PieceTemplate template(@NotNull IStructureElement<?> element) {
        return new PieceTemplate(
                new TraceabilityPredicate[][][] {
                        {
                                { TraceabilityPredicate.ANY }
                        }
                },
                new IStructureElement<?>[][][] {
                        {
                                { element }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);
    }

    @NotNull
    private static StructureOperationRequest checkRequest() {
        return StructureOperationRequest.check(
                WORLD, BlockPos.ORIGIN, ORIENTATION, false, null, null);
    }

    @NotNull
    private static World bareWorld() {
        try {
            return (World) unsafe().allocateInstance(BareWorld.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate bare test world", e);
        }
    }

    @NotNull
    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access Unsafe", e);
        }
    }

    private static class CountingElement implements IStructureElement<Object> {

        private final AtomicInteger calls = new AtomicInteger();
        private boolean matches;

        private CountingElement(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            calls.incrementAndGet();
            afterCall(context);
            return matches;
        }

        protected void afterCall(@NotNull StructureEvaluationContext<Object> context) {}

        int calls() {
            return calls.get();
        }

        @NotNull
        @Override
        public Set<StructureElementCapability> getCapabilities() {
            return StructureElementCapability.snapshotSafe();
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            throw new AssertionError("context-aware check should be used");
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}
    }

    private static final class EmittingElement extends CountingElement {

        @NotNull
        private final AtomicInteger value;

        private EmittingElement(boolean matches, @NotNull AtomicInteger value) {
            super(matches);
            this.value = value;
        }

        @Override
        protected void afterCall(@NotNull StructureEvaluationContext<Object> context) {
            context.getCollector().emit(TEST_VALUE, value.get());
        }
    }

    private static final class BareWorld extends World {

        private BareWorld() {
            super(null, null, null, null, false);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }
    }
}
