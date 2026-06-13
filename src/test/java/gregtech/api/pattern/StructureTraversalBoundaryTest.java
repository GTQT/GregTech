package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureCompiler;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureTraversalBoundaryTest {

    private static final World WORLD = bareWorld();

    @Test
    void liveAndSnapshotFixedTraversalVisitSameCellsForFlippedOrientation() {
        RecordingElement element = new RecordingElement(true);
        MultiblockState liveState = new MultiblockState(template(element));
        MultiblockState snapshotState = new MultiblockState(template(element));
        StructureOrientation orientation = StructureOrientation.of(
                EnumFacing.WEST, EnumFacing.WEST, EnumFacing.UP, true, true);
        StructureCellTraversal traversal = StructureCellTraversal.at(new BlockPos(10, 20, 30), orientation)
                .withLocalOffset(2, -1, 3);

        assertTrue(liveState.checkPatternAtExact(WORLD, traversal, new StructureMatchSession()) != null);
        List<BlockPos> livePositions = new ArrayList<>(element.visited);
        element.visited.clear();

        assertTrue(snapshotState.checkPatternAtSnapshotExact(
                WORLD, traversal, new StructureMatchSession()) != null);

        assertEquals(livePositions, element.visited);
        assertEquals(asList(new BlockPos(13, 21, 28), new BlockPos(13, 20, 28)), livePositions);
    }

    @Test
    void repeatGroupLiveAndSnapshotTraversalUseSameSliceCoordinates() {
        RecordingElement element = new RecordingElement(true);
        PieceTemplate template = template(element);
        RepeatGroupPiece livePiece = repeatPiece(template, new int[] {0, 2}, new int[] {2, 3});
        RepeatGroupPiece snapshotPiece = repeatPiece(template, new int[] {0, 2}, new int[] {2, 3});
        PieceRuntime liveRuntime = new PieceRuntime(livePiece);
        PieceRuntime snapshotRuntime = new PieceRuntime(snapshotPiece);
        StructureOrientation orientation = StructureOrientation.of(
                EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, true, true);
        BlockPos origin = new BlockPos(4, 5, 6);
        int[] reps = {2, 2};
        FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                Collections.singletonMap("repeat", reps), Collections.emptyMap(), Collections.emptyMap());

        assertTrue(livePiece.checkSync(
                WORLD, origin, orientation, prior, liveRuntime, new StructureMatchSession()));
        List<BlockPos> livePositions = new ArrayList<>(element.visited);
        element.visited.clear();

        assertTrue(snapshotPiece.checkOnSnapshot(
                WORLD, origin, orientation, prior, snapshotRuntime, new StructureMatchSession()));

        assertEquals(livePositions, element.visited);
        assertEquals(asList(
                new BlockPos(4, 5, 6),
                new BlockPos(4, 4, 6),
                new BlockPos(6, 5, 6),
                new BlockPos(6, 4, 6),
                new BlockPos(4, 5, 9),
                new BlockPos(4, 4, 9),
                new BlockPos(6, 5, 9),
                new BlockPos(6, 4, 9)), livePositions);
    }

    @Test
    void failedMultiPieceCheckRollsBackFormationEffectsAndRuntimeState() {
        RecordingElement firstElement = new RecordingElement(true);
        RecordingElement failingElement = new RecordingElement(false);
        StructurePiece first = new StructurePiece(
                "first", template(firstElement), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructurePiece second = new StructurePiece(
                "second", template(failingElement), new Vec3i(0, 0, 1), OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(asList(first, second));
        PieceRuntimes runtimes = new PieceRuntimes(pattern);
        StructureOrientation orientation = StructureOrientation.of(
                EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.UP, false, false);

        boolean matched = pattern.checkAllPieces(
                WORLD, BlockPos.ORIGIN, orientation, runtimes, null);

        assertFalse(matched);
        assertFalse(runtimes.get(first).isValidated());
        assertFalse(runtimes.get(second).isValidated());
        assertTrue(runtimes.get(first).getPositions().isEmpty());
        assertNull(runtimes.get(first).getLastFormedReps());
    }

    @Test
    void failedFormationTransactionRollsBackCollectorAndContextMutations() {
        StructureMatchSession session = new StructureMatchSession();
        PatternMatchContext legacyContext = session.getContext();
        StructureEvaluationContext<Object> context = new StructureEvaluationContext<>();
        BlockWorldState worldState = new BlockWorldState();
        worldState.update(WORLD, BlockPos.ORIGIN, legacyContext,
                session.getGlobalCount(), new HashMap<>(), TraceabilityPredicate.ANY);
        context.update(null, session, worldState, StructureEvaluationContext.Operation.MATCH_WORLD);

        boolean committed = context.transaction(evaluation -> {
            StructureMatchCollector collector = evaluation.getCollector();
            collector.declareCount("count", 0, 2, null, null);
            collector.recordCount("count");
            collector.addPart(new TestPart());
            collector.recordVariantActiveBlock(BlockPos.ORIGIN);
            collector.recordChannelValue("channel", 1, true);
            collector.setValue("tier", 4);
            evaluation.getLegacyContext().set("custom", "mutated");
            return false;
        });

        assertFalse(committed);
        assertEquals(0, session.copyOperationState().getParts().size());
        assertEquals(0, session.copyOperationState().getVariantActiveBlocks().size());
        assertNull(legacyContext.get("channel"));
        assertNull(legacyContext.get("tier"));
        assertNull(legacyContext.get("custom"));
    }

    @Test
    void dirtyPieceRuntimeCheckReturnsCommitReadyResult() {
        RecordingElement element = new RecordingElement(true);
        StructurePiece piece = new StructurePiece(
                "dirty", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        PieceRuntimes runtimes = new PieceRuntimes(pattern);
        StructureRuntime runtime = new StructureRuntime(null, null, null, pattern, runtimes);

        StructureCheckResult result = runtime.checkDirtyPieces(checkRequest());

        assertTrue(result.isMatched());
        assertEquals("dirty-piece", result.getTracePath());
        assertNotNull(result.getMetadata());
        assertEquals(BlockPos.ORIGIN, result.getMetadata().getPieceCenter("dirty"));
        assertEquals(1, result.getMetadata().getChannelValue("channel"));
        assertEquals(2, result.copyOperationState().getParts().size());
        assertEquals(1, result.copyContext().getInt("channel"));
        assertTrue(runtimes.get(piece).isValidated());
        assertFalse(runtimes.get(piece).isDirty());
    }

    @Test
    void dirtyPieceFailureRollsBackRuntimeAndReportsTrace() {
        RecordingElement element = new RecordingElement(true);
        StructurePiece piece = new StructurePiece(
                "dirty", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        PieceRuntimes runtimes = new PieceRuntimes(pattern);
        StructureRuntime runtime = new StructureRuntime(null, null, null, pattern, runtimes);

        assertTrue(runtime.checkDirtyPieces(checkRequest()).isMatched());
        PieceRuntime pieceRuntime = runtimes.get(piece);
        LongSet formedPositions = new LongOpenHashSet(pieceRuntime.getPositions());

        pieceRuntime.markDirty();
        element.setMatches(false);
        StructureCheckResult failed = runtime.checkDirtyPieces(checkRequest());

        assertFalse(failed.isMatched());
        assertEquals("dirty-piece", failed.getTracePath());
        StructureFailureTrace failureTrace = failed.createFailureTrace(testController());
        assertEquals(StructureFailureTrace.Kind.BLOCK_MISMATCH,
                failureTrace.getKind());
        assertEquals("dirty-piece", failureTrace.getPath());
        assertTrue(pieceRuntime.isValidated());
        assertTrue(pieceRuntime.isDirty());
        assertEquals(formedPositions, pieceRuntime.getPositions());
    }

    private static RepeatGroupPiece repeatPiece(PieceTemplate template, int[] axes, int[] steps) {
        int[][] ranges = new int[axes.length][2];
        for (int i = 0; i < axes.length; i++) {
            ranges[i][0] = 1;
            ranges[i][1] = 3;
        }
        return new RepeatGroupPiece(
                "repeat",
                template,
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null,
                axes,
                ranges,
                steps,
                null,
                new int[] {0, 0, 0},
                StructureCompiler.SearchStrategy.NESTED_BACKTRACKING);
    }

    private static PieceTemplate template(RecordingElement element) {
        TraceabilityPredicate center = TraceabilityPredicate.ANY;
        TraceabilityPredicate other = TraceabilityPredicate.ANY;
        TraceabilityPredicate[][][] predicates = new TraceabilityPredicate[][][] {
                {
                        { center },
                        { other }
                }
        };
        IStructureElement<?>[][][] elements = new IStructureElement<?>[][][] {
                {
                        { element.withPredicate(center) },
                        { element.withPredicate(other) }
                }
        };
        return new PieceTemplate(
                predicates,
                elements,
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
    private static World bareWorld() {
        try {
            return (World) unsafe().allocateInstance(BareWorld.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate bare test world", e);
        }
    }

    @NotNull
    private static StructureOperationRequest checkRequest() {
        return StructureOperationRequest.check(
                WORLD,
                BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false,
                null,
                null);
    }

    @NotNull
    private static TestController testController() {
        try {
            TestController controller = (TestController) unsafe().allocateInstance(TestController.class);
            controller.world = WORLD;
            controller.pos = BlockPos.ORIGIN;
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate test controller", e);
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

    @SafeVarargs
    private static <T> List<T> asList(T... values) {
        List<T> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    private static final class RecordingElement implements IStructureElement<Object> {

        private final List<BlockPos> visited;
        @NotNull
        private final State state;
        private final TraceabilityPredicate predicate;

        private RecordingElement(boolean matches) {
            this(new State(matches), new TraceabilityPredicate());
        }

        private RecordingElement(@NotNull State state,
                                 @NotNull TraceabilityPredicate predicate) {
            this.state = state;
            this.visited = state.visited;
            this.predicate = predicate;
        }

        private RecordingElement withPredicate(@NotNull TraceabilityPredicate predicate) {
            return new RecordingElement(state, predicate);
        }

        private void setMatches(boolean matches) {
            state.matches = matches;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            visited.add(context.getPos());
            state.lastSession = context.getSession();
            if (state.matches) {
                StructureMatchCollector collector = context.getCollector();
                collector.addPart(new TestPart());
                collector.recordChannelValue("channel", 1, true);
            }
            return state.matches;
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

        @Override
        public TraceabilityPredicate toPredicate() {
            return predicate;
        }

        private StructureMatchSession lastSession() {
            return state.lastSession;
        }
    }

    private static final class State {

        @NotNull
        private final List<BlockPos> visited = new ArrayList<>();
        private StructureMatchSession lastSession;
        private boolean matches;

        private State(boolean matches) {
            this.matches = matches;
        }
    }

    private static final class TestPart implements IMultiblockPart {

        @Override
        public boolean isAttachedToMultiBlock() {
            return false;
        }

        @Override
        public void addToMultiBlock(MultiblockControllerBase controllerBase) {}

        @Override
        public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {}
    }

    private static final class TestController extends MultiblockControllerBase {

        private World world;
        private BlockPos pos;

        private TestController() {
            super(new ResourceLocation("gregtech", "dirty_piece_test_controller"));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return this;
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public @NotNull EnumFacing getFrontFacing() {
            return EnumFacing.NORTH;
        }

        @Override
        public String getMetaName() {
            return "gregtech.machine.dirty_piece_test_controller";
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
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
