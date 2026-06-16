package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.CompiledStructureElement;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureCompiler;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.casing.StructureChannelValues;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void legacyNoSessionCheckRunsThroughSessionBackedExecution() {
        RecordingElement element = new RecordingElement(true);
        MultiblockState state = new MultiblockState(singleCellTemplate(element));

        PatternMatchContext context = state.checkPatternFastAt(
                WORLD, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false);

        assertNotNull(context);
        assertNotNull(element.lastSession());
        assertEquals(1, context.getInt("channel"));
        assertEquals(1, StructureOperationState.fromLegacyContext(context).getParts().size());
    }

    @Test
    void legacyBlockPatternCheckPublishesTypedRuntimeState() {
        RecordingElement element = new RecordingElement(true)
                .withPredicate(new TraceabilityPredicate(
                        worldState -> true, () -> new BlockInfo[] {
                                new BlockInfo(net.minecraft.init.Blocks.STONE.getDefaultState(), null)
                        }));
        BlockPatternTemplate template = new BlockPatternTemplate(singleCellTemplate(element));
        BlockPattern pattern = new BlockPattern(template);

        PatternMatchContext context = pattern.checkPatternFastAt(
                WORLD, BlockPos.ORIGIN, EnumFacing.NORTH, EnumFacing.UP, false, false);

        assertNotNull(context);
        assertEquals(1, context.getInt("channel"));
        assertEquals(1, StructureOperationState.fromLegacyContext(context).getParts().size());
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
    void fullContributionEvaluatorUsesIndependentPieceSessions() {
        RecordingElement firstElement = new RecordingElement(true);
        RecordingElement secondElement = new RecordingElement(true);
        StructurePiece first = new StructurePiece(
                "first", singleCellTemplate(firstElement), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructurePiece second = new StructurePiece(
                "second", singleCellTemplate(secondElement), new Vec3i(0, 0, 1), OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(asList(first, second));
        PieceRuntimes controllerRuntimes = new PieceRuntimes(pattern);
        StructureRuntime runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, controllerRuntimes);

        StructureCheckResult result = runtime.check(checkRequest());

        assertTrue(result.isMatched());
        assertNotNull(firstElement.lastSession());
        assertNotNull(secondElement.lastSession());
        assertFalse(firstElement.lastSession() == secondElement.lastSession());
        assertEquals(2, result.getResultTable().size());
        assertEquals(2, result.copyOperationState().getParts().size());
        assertEquals(1, result.copyContext().getInt("channel"));

        assertTrue(result.publishPieceRuntimes(controllerRuntimes));
        assertNull(controllerRuntimes.get(first).getLastAggregatedContext());
        assertNull(controllerRuntimes.get(second).getLastAggregatedContext());
    }

    @Test
    @SuppressWarnings("deprecation")
    void formedStructureViewMaterializesLegacyProjectionOnlyInBridgeScope() {
        RecordingElement element = new RecordingElement(true);
        StructureRuntime runtime = StructureRuntime.fromDefinition(
                StructureDefinition.fromTemplate(new BlockPatternTemplate(singleCellTemplate(element))));

        StructureCheckResult result = runtime.check(checkRequest());
        FormedStructureView view = FormedStructureView.fromCheckResult(result);

        assertEquals(1, view.getMetadataChannelValue("channel"));
        assertThrows(IllegalStateException.class, view::copyLegacyCallbackContext);

        FormedStructureView.runWithLegacyCallbackProjection(view, result, () -> {
            PatternMatchContext first = view.copyLegacyCallbackContext();
            first.set("channel", 9);
            PatternMatchContext second = view.copyLegacyCallbackContext();

            assertEquals(1, second.getInt("channel"));
        });

        assertThrows(IllegalStateException.class, view::copyLegacyCallbackContext);
    }

    @Test
    void fullContributionEvaluatorKeepsInitialCompatibilityContextOnlyAtResultBoundary() {
        RecordingElement firstElement = new RecordingElement(true);
        RecordingElement secondElement = new RecordingElement(true);
        StructurePiece first = new StructurePiece(
                "first", singleCellTemplate(firstElement), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructurePiece second = new StructurePiece(
                "second", singleCellTemplate(secondElement), new Vec3i(0, 0, 1), OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(asList(first, second));
        StructureRuntime runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));
        PatternMatchContext initialContext = new PatternMatchContext();
        initialContext.set("external", "kept");
        initialContext.set("scratch", "seed");

        StructureCheckResult result = runtime.check(StructureOperationRequest.check(
                WORLD, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false, initialContext, null));

        assertTrue(result.isMatched());
        assertEquals("kept", result.copyContext().get("external"));
        assertEquals("seed", result.copyContext().get("scratch"));
    }

    @Test
    void legacyPredicateTemplateCompilesToElementBeforeLegacyViewProjection() {
        TraceabilityPredicate original = new TraceabilityPredicate(worldState -> true)
                .setCenter();
        PieceTemplate template = new PieceTemplate(
                new TraceabilityPredicate[][][] {
                        {
                                { original }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                });

        IStructureElement<?> element = template.getElements()[0][0][0];
        assertTrue(element instanceof CompiledStructureElement);
        assertTrue(element.usesLegacyPredicateRuntime());
        assertTrue(element.isCenter());

        TraceabilityPredicate projected = template.getBlockMatches()[0][0][0];
        projected.common.clear();
        projected.limited.clear();

        assertTrue(element.toPredicate().isCenter());
        assertTrue(template.getElements()[0][0][0].isCenter());
    }

    @Test
    void directElementLegacyContextMutationDoesNotBecomeMatcherSharedState() {
        LegacyContextWritingElement element = new LegacyContextWritingElement();
        StructurePiece piece = new StructurePiece(
                "direct", singleCellTemplate(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        StructureRuntime runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));

        StructureCheckResult result = runtime.check(checkRequest());

        assertTrue(result.isMatched());
        assertEquals(1, element.contextCopiesSeen);
        assertEquals(1, result.copyContext().getInt("typed_value"));
        assertNull(result.copyContext().get("direct_scratch"));
        assertNull(result.copyContext().get("direct_shared"));
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
    void failedFormationTransactionRollsBackPieceContributionEmissions() {
        StructurePiece piece = new StructurePiece(
                "piece", template(new RecordingElement(true)),
                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructureMatchSession session = new StructureMatchSession();
        PatternMatchContext legacyContext = session.getContext();
        StructureEvaluationContext<Object> context = new StructureEvaluationContext<>();
        BlockWorldState worldState = new BlockWorldState();
        worldState.update(WORLD, BlockPos.ORIGIN, legacyContext,
                session.getGlobalCount(), new HashMap<>(), TraceabilityPredicate.ANY);
        context.update(null, session, worldState, StructureEvaluationContext.Operation.MATCH_WORLD);
        session.beginPieceContribution(piece);

        assertFalse(context.transaction(evaluation -> {
            evaluation.getCollector().recordCount("count");
            evaluation.getCollector().recordChannelValue("channel", 1, true);
            return false;
        }));

        assertTrue(session.finishPieceContribution(piece).isEmpty());
    }

    @Test
    void fullDefinitionCheckPublishesRuntimeOnlyAfterExplicitCommit() {
        RecordingElement element = new RecordingElement(true);
        StructurePiece piece = new StructurePiece(
                "full", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        PieceRuntimes controllerRuntimes = new PieceRuntimes(pattern);
        StructureDefinition<?> definition = StructureDefinition.fromMultiPiecePattern(pattern);
        StructureRuntime runtime = new StructureRuntime(
                definition, null, null, pattern, controllerRuntimes);

        StructureCheckResult result = runtime.check(checkRequest());

        assertTrue(result.isMatched());
        assertFalse(controllerRuntimes.get(piece).isValidated());
        assertTrue(controllerRuntimes.get(piece).isDirty());
        assertTrue(controllerRuntimes.get(piece).getPositions().isEmpty());

        assertTrue(result.publishPieceRuntimes(controllerRuntimes));
        assertTrue(controllerRuntimes.get(piece).isValidated());
        assertFalse(controllerRuntimes.get(piece).isDirty());
        assertNull(controllerRuntimes.get(piece).getLastAggregatedContext());
        assertEquals(1, result.copyContext().getInt("channel"));
    }

    @Test
    void activeGraphRuntimeCheckReturnsCommitReadyResult() {
        RecordingElement element = new RecordingElement(true);
        StructurePiece piece = new StructurePiece(
                "active", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        PieceRuntimes runtimes = new PieceRuntimes(pattern);
        StructureRuntime runtime = new StructureRuntime(null, null, null, pattern, runtimes);

        StructureCheckResult result = runtime.checkActiveGraph(checkRequest());

        assertTrue(result.isMatched());
        assertEquals("v3-typed-pattern", result.getTracePath());
        assertEquals("v3-typed-pattern", result.getDiagnostics().getPath());
        assertEquals("MATCH_WORLD", result.getDiagnostics().getOperation());
        assertEquals(1, result.getDiagnostics().getPieceCount());
        assertFalse(result.getDiagnostics().isSyntheticSinglePiece());
        assertNotNull(result.getMetadata());
        assertEquals(BlockPos.ORIGIN, result.getMetadata().getPieceCenter("active"));
        assertEquals(1, result.getMetadata().getChannelValue("channel"));
        assertEquals(2, result.copyOperationState().getParts().size());
        assertEquals(1, result.copyContext().getInt("channel"));
        assertFalse(runtimes.get(piece).isValidated());
        assertTrue(runtimes.get(piece).isDirty());

        assertTrue(result.publishPieceRuntimes(runtimes));
        assertTrue(runtimes.get(piece).isValidated());
        assertFalse(runtimes.get(piece).isDirty());
    }

    @Test
    void activeGraphFailurePreservesPublishedRuntimeAndReportsTrace() {
        RecordingElement element = new RecordingElement(true);
        StructurePiece piece = new StructurePiece(
                "active", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        PieceRuntimes runtimes = new PieceRuntimes(pattern);
        StructureRuntime runtime = new StructureRuntime(null, null, null, pattern, runtimes);

        StructureCheckResult initial = runtime.checkActiveGraph(checkRequest());
        assertTrue(initial.isMatched());
        assertTrue(initial.publishPieceRuntimes(runtimes));
        PieceRuntime pieceRuntime = runtimes.get(piece);
        LongSet formedPositions = new LongOpenHashSet(pieceRuntime.getPositions());

        pieceRuntime.markDirty();
        element.setMatches(false);
        StructureCheckResult failed = runtime.checkActiveGraph(checkRequest());

        assertFalse(failed.isMatched());
        assertEquals("v3-typed-pattern", failed.getTracePath());
        StructureFailureTrace failureTrace = failed.createFailureTrace(testController());
        assertEquals(StructureFailureTrace.Kind.BLOCK_MISMATCH,
                failureTrace.getKind());
        assertEquals("v3-typed-pattern", failureTrace.getPath());
        assertTrue(pieceRuntime.isValidated());
        assertTrue(pieceRuntime.isDirty());
        assertEquals(formedPositions, pieceRuntime.getPositions());
    }

    @Test
    void fullContributionFoldMatchesActiveGraphOracle() {
        RecordingElement firstElement = new RecordingElement(true);
        RecordingElement secondElement = new RecordingElement(true);
        StructurePiece first = new StructurePiece(
                "first", template(firstElement), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructurePiece inactive = new StructurePiece(
                "inactive", template(new RecordingElement(true)),
                new Vec3i(0, 0, 1), OffsetMode.RELATIVE, () -> false);
        StructurePiece second = new StructurePiece(
                "second", template(secondElement),
                new Vec3i(0, 0, 2), OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(asList(first, inactive, second));
        StructureDefinition<?> definition = StructureDefinition.fromMultiPiecePattern(pattern);
        StructureRuntime fullRuntime = new StructureRuntime(
                definition, null, null, pattern, new PieceRuntimes(pattern));
        StructureRuntime activeRuntime = new StructureRuntime(
                null, null, null, pattern, new PieceRuntimes(pattern));

        StructureCheckResult full = fullRuntime.check(checkRequest());
        StructureCheckResult active = activeRuntime.checkActiveGraph(checkRequest());

        assertTrue(full.isMatched());
        assertTrue(active.isMatched());
        assertContributionResultMatchesOracle(full);
        assertContributionResultMatchesOracle(active);

        StructureResultTable fullTable = full.getResultTable();
        StructureResultTable activeTable = active.getResultTable();
        assertNotNull(fullTable);
        assertNotNull(activeTable);
        assertEquals(3, fullTable.size());
        assertEquals(PieceEvaluationResult.Status.INACTIVE,
                fullTable.get("inactive").getStatus());
        for (int i = 0; i < fullTable.size(); i++) {
            PieceEvaluationResult fullPiece = fullTable.getResults().get(i);
            PieceEvaluationResult activePiece = activeTable.getResults().get(i);
            assertEquals(fullPiece.getPiece().getName(), activePiece.getPiece().getName());
            assertEquals(fullPiece.getStatus(), activePiece.getStatus());
            assertEquals(fullPiece.getResolvedCenter(), activePiece.getResolvedCenter());
            assertEquals(fullPiece.getFormedPositions(), activePiece.getFormedPositions());
            assertEquals(fullPiece.getContribution().getCounts(),
                    activePiece.getContribution().getCounts());
            assertEquals(fullPiece.getContribution().getVariantActiveBlocks(),
                    activePiece.getContribution().getVariantActiveBlocks());
        }

        assertEquals(full.getMetadata().getPieceCenter("first"),
                active.getMetadata().getPieceCenter("first"));
        assertEquals(full.getMetadata().getPieceCenter("second"),
                active.getMetadata().getPieceCenter("second"));
        assertEquals(full.copyContext().getInt("channel"),
                active.copyContext().getInt("channel"));
        assertEquals(full.copyOperationState().getParts().size(),
                active.copyOperationState().getParts().size());
    }

    @Test
    void fullContributionFoldMatchesAggregateValidationFailure() {
        MinimumCountElement element = new MinimumCountElement(5);
        PieceTemplate pieceTemplate = template(element);
        StructurePiece first = new StructurePiece(
                "first", pieceTemplate, Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructurePiece second = new StructurePiece(
                "second", pieceTemplate, new Vec3i(0, 0, 2), OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(asList(first, second));
        StructureDefinition<?> definition = StructureDefinition.fromMultiPiecePattern(pattern);
        StructureRuntime fullRuntime = new StructureRuntime(
                definition, null, null, pattern, new PieceRuntimes(pattern));
        StructureRuntime activeRuntime = new StructureRuntime(
                null, null, null, pattern, new PieceRuntimes(pattern));

        StructureCheckResult full = fullRuntime.check(checkRequest());
        StructureCheckResult active = activeRuntime.checkActiveGraph(checkRequest());

        assertFalse(full.isMatched());
        assertFalse(active.isMatched());
        assertNotNull(full.getResultTable());
        assertNotNull(active.getResultTable());
        assertEquals(2, full.getResultTable().size());
        assertEquals(2, active.getResultTable().size());
        assertNotNull(full.getContributionAggregate());
        assertNotNull(active.getContributionAggregate());
        assertFalse(full.getContributionAggregate().isMatched());
        assertFalse(active.getContributionAggregate().isMatched());
        assertEquals(StructureFailureTrace.Kind.COUNT_LIMIT,
                full.createFailureTrace(testController()).getKind());
        assertEquals(StructureFailureTrace.Kind.COUNT_LIMIT,
                active.createFailureTrace(testController()).getKind());
    }

    @Test
    void fullContributionEvaluatorValidatesGlobalMaxAfterFold() {
        MaximumCountElement element = new MaximumCountElement(1);
        PieceTemplate pieceTemplate = singleCellTemplate(element);
        StructurePiece first = new StructurePiece(
                "first", pieceTemplate, Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructurePiece second = new StructurePiece(
                "second", pieceTemplate, new Vec3i(0, 0, 2), OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(asList(first, second));
        StructureRuntime runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));

        StructureCheckResult result = runtime.check(checkRequest());

        assertFalse(result.isMatched());
        assertNotNull(result.getResultTable());
        assertEquals(2, result.getResultTable().size());
        assertNotNull(result.getContributionAggregate());
        assertFalse(result.getContributionAggregate().isMatched());
        assertEquals(StructureFailureTrace.Kind.COUNT_LIMIT,
                result.createFailureTrace(testController()).getKind());
    }

    @Test
    void defaultFormationCallbackReceivesProjectedLegacyContext() {
        TestController controller = testController();
        PatternMatchContext legacy = new PatternMatchContext();
        legacy.set("channel", 3);
        FormedStructureView view = FormedStructureView.legacy(
                null, new StructureChannelValues(), new StructureOperationState(), legacy, false);

        controller.invokeFormStructure(view);
        legacy.set("channel", 9);

        assertNotNull(controller.legacyCallbackContext);
        assertEquals(3, controller.legacyCallbackContext.getInt("channel"));
    }

    @Test
    void typedFormationCallbackDoesNotInvokeLegacyCallback() {
        TypedCallbackController controller = typedCallbackController();
        PatternMatchContext legacy = new PatternMatchContext();
        legacy.set("channel", 5);
        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                Collections.emptyMap(), Collections.singletonMap("channel", 5));
        FormedStructureView view = FormedStructureView.legacy(
                metadata, new StructureChannelValues(), new StructureOperationState(), legacy, true);

        controller.invokeFormStructure(view);

        assertNotNull(controller.typedCallbackView);
        assertEquals(5, controller.typedCallbackView.getMetadataChannelValue("channel"));
        assertTrue(controller.typedCallbackView.isFlipped());
        assertNull(controller.legacyCallbackContext);
    }

    @Test
    void formedStructureViewExposesTypedStateWithoutLegacyContextLookup() {
        TestPart part = new TestPart();
        BlockPos activePos = new BlockPos(1, 2, 3);
        StructurePieceKey bodyPiece = StructurePieceKey.of("body");
        StructurePieceKey missingPiece = StructurePieceKey.of("missing");
        StructureOperationState state = new StructureOperationState();
        state.parts.add(part);
        state.abilityCounts.put(MultiblockAbility.IMPORT_ITEMS, 2);
        state.variantActiveBlocks.add(activePos);
        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                Collections.singletonMap("body", new int[] {3}),
                Collections.singletonMap("coil", 4),
                Collections.singletonMap("body", BlockPos.ORIGIN));

        FormedStructureView view = FormedStructureView.legacy(
                metadata, new StructureChannelValues(), state, new PatternMatchContext(), false);

        assertEquals(3, view.getPieceRepeat(bodyPiece, 0));
        assertEquals(0, view.getPieceRepeat(missingPiece, 0));
        assertEquals(BlockPos.ORIGIN, view.getPieceCenter(bodyPiece));
        assertEquals(4, view.getMetadataChannelValue("coil"));
        assertEquals(Collections.singleton(part), view.getParts());
        assertEquals(Collections.singletonList(activePos), view.getVariantActiveBlocks());
        assertEquals(2, view.getAbilityCount(MultiblockAbility.IMPORT_ITEMS));
        assertTrue(view.hasAbility(MultiblockAbility.IMPORT_ITEMS));
        assertFalse(view.hasAbility(MultiblockAbility.EXPORT_ITEMS));
    }

    @Test
    void snapshotRequestUsesDefinitionRuntimeTraversal() {
        RecordingElement element = new RecordingElement(true);
        StructureDefinition<?> definition = StructureDefinition.fromTemplate(
                "snapshot", new BlockPatternTemplate(template(element)));
        StructureRuntime runtime = StructureRuntime.fromDefinition(definition);
        StructureOperationRequest request = StructureOperationRequest.snapshotCheck(
                WORLD,
                BlockPos.ORIGIN,
                StructureOrientation.of(
                        EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                null);

        StructureSnapshotResult result = runtime.checkSnapshot(request);

        assertEquals(StructureOperationRequest.Kind.SNAPSHOT_CHECK, request.getKind());
        assertTrue(result.isSupported());
        assertTrue(result.isMatched());
        assertEquals(1, result.getProgressDepth());
        assertEquals("definition", result.getDiagnostics().getPath());
        assertEquals("MATCH_SNAPSHOT", result.getDiagnostics().getOperation());
    }

    @Test
    void runtimeDetectorPublishesTypedAggregateAndDynamicPositionGraph() {
        StructureContributionKey<Integer, Integer> detectorValue =
                StructureContributionKey.uniform(
                        "gregtech:test/runtime_detector_value",
                        (context, value) -> context.set("detector_value", value));
        RecordingElement identityElement = new RecordingElement(true);
        RecordingElement runtimeElement = new RecordingElement(true);
        StructureDefinition<TestController> definition =
                StructureDefinition.<TestController>builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK)
                        .piece("runtime", "R")
                        .where('R', identityElement)
                        .end()
                        .runtimeDetector(context -> {
                            assertEquals(
                                    new BlockPos(2, -1, 3),
                                    context.localPos(
                                            2, -1, 3,
                                            RelativeDirection.RIGHT,
                                            RelativeDirection.UP,
                                            RelativeDirection.BACK));
                            assertTrue(context.match(BlockPos.ORIGIN, runtimeElement));
                            assertTrue(context.match(BlockPos.ORIGIN.east(), runtimeElement));
                            context.emit(detectorValue, 7);
                            return true;
                        })
                        .build();
        StructureRuntime runtime = StructureRuntime.fromDefinition(definition);
        TestController controller = testController();

        StructureCheckResult result = runtime.check(StructureOperationRequest.check(
                WORLD,
                BlockPos.ORIGIN,
                StructureOrientation.of(
                        EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false,
                null,
                controller));

        assertTrue(result.isMatched());
        assertEquals("runtime-detector", result.getTracePath());
        assertEquals(7, result.copyContext().getInt("detector_value"));
        assertEquals(7, result.getContributionAggregate().get(detectorValue));
        assertNotNull(result.getGraphPublication());
        assertEquals(2, result.getGraphPublication().getPositionIndex().watchedPositionCount());
        assertTrue(result.getGraphPublication().getPositionIndex()
                .getAllWatchedPositions().contains(BlockPos.ORIGIN.toLong()));
        assertTrue(result.getGraphPublication().getPositionIndex()
                .getAllWatchedPositions().contains(BlockPos.ORIGIN.east().toLong()));
    }

    @Test
    void runtimeDetectorRejectsSnapshotChecksAndPreservesFailurePosition() {
        BlockPos failurePos = BlockPos.ORIGIN.east(3);
        StructureDefinition<TestController> definition =
                StructureDefinition.<TestController>builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK)
                        .piece("runtime", "R")
                        .where('R', new RecordingElement(true))
                        .end()
                        .runtimeDetector(context ->
                                context.fail(failurePos, "runtime boundary", "air"))
                        .build();
        StructureRuntime runtime = StructureRuntime.fromDefinition(definition);
        TestController controller = testController();
        StructureOrientation orientation = StructureOrientation.of(
                EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false);

        StructureCheckResult failed = runtime.check(StructureOperationRequest.check(
                WORLD, BlockPos.ORIGIN, orientation, false, null, controller));
        StructureSnapshotResult snapshot = runtime.checkSnapshot(
                StructureOperationRequest.snapshotCheck(
                        WORLD, BlockPos.ORIGIN, orientation, controller));

        assertFalse(failed.isMatched());
        assertEquals("runtime-detector", failed.getTracePath());
        assertEquals(failurePos, failed.createFailureTrace(controller).getErrorPos());
        assertFalse(snapshot.isSupported());
    }

    @Test
    void runtimeDetectorPreservesTypedElementPatternError() {
        StructureDefinition<TestController> definition =
                StructureDefinition.<TestController>builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK)
                        .piece("runtime", "R")
                        .where('R', new RecordingElement(true))
                        .end()
                        .runtimeDetector(context ->
                                context.match(
                                        BlockPos.ORIGIN,
                                        new ErrorElement()))
                        .build();
        TestController controller = testController();

        StructureCheckResult result =
                StructureRuntime.fromDefinition(definition).check(
                        StructureOperationRequest.check(
                                WORLD,
                                BlockPos.ORIGIN,
                                StructureOrientation.of(
                                        EnumFacing.NORTH,
                                        EnumFacing.NORTH,
                                        EnumFacing.UP,
                                        false,
                                        false),
                                false,
                                null,
                                controller));

        assertFalse(result.isMatched());
        assertTrue(result.createFailureTrace(controller).getError()
                instanceof PatternStringError);
    }

    @Test
    void runtimeDetectorAppliesStableGlobalAbilityLimits() {
        StructureDefinition<TestController> definition =
                StructureDefinition.<TestController>builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK)
                        .piece("runtime", "R")
                        .where('R', new RecordingElement(true))
                        .end()
                        .globalAbilityLimit(MultiblockAbility.IMPORT_ITEMS, 1, 1)
                        .runtimeDetector(context -> {
                            context.includePosition(BlockPos.ORIGIN);
                            return true;
                        })
                        .build();
        StructureRuntime runtime = StructureRuntime.fromDefinition(definition);
        TestController controller = testController();

        StructureCheckResult result = runtime.check(StructureOperationRequest.check(
                WORLD,
                BlockPos.ORIGIN,
                StructureOrientation.of(
                        EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false,
                null,
                controller));

        assertFalse(result.isMatched());
        assertEquals(1, result.getMissingAbilities().get(MultiblockAbility.IMPORT_ITEMS));
        assertEquals(
                StructureFailureTrace.Kind.MISSING_ABILITY,
                result.createFailureTrace(controller).getKind());
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

    private static PieceTemplate template(IStructureElement<?> element) {
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
                        { element },
                        { element }
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

    private static PieceTemplate singleCellTemplate(IStructureElement<?> element) {
        TraceabilityPredicate predicate = TraceabilityPredicate.ANY;
        return new PieceTemplate(
                new TraceabilityPredicate[][][] {
                        {
                                { predicate }
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
    private static TypedCallbackController typedCallbackController() {
        try {
            return (TypedCallbackController) unsafe().allocateInstance(TypedCallbackController.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate typed callback controller", e);
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

    private static void assertContributionResultMatchesOracle(
            StructureCheckResult oracle) {
        StructureAggregateFolder.Result aggregate = oracle.getContributionAggregate();
        assertNotNull(aggregate);
        assertTrue(aggregate.isMatched(), aggregate.getErrorMessage());
        assertEquals(oracle.copyContext().getInt("channel"),
                aggregate.copyCompatibilityContext().getInt("channel"));
        assertEquals(oracle.copyOperationState().getParts().size(),
                aggregate.copyOperationState().getParts().size());
        assertEquals(oracle.copyOperationState().getVariantActiveBlocks(),
                aggregate.copyOperationState().getVariantActiveBlocks());
        assertEquals(oracle.getMetadata().getPieceCenter("first"),
                aggregate.getMetadata().getPieceCenter("first"));
        assertEquals(oracle.getMetadata().getPieceCenter("second"),
                aggregate.getMetadata().getPieceCenter("second"));
    }

    @SafeVarargs
    private static <T> List<T> asList(T... values) {
        List<T> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    private static final class RecordingElement implements ITypedStructureElement<Object> {

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

        @NotNull
        @Override
        public Set<StructureElementCapability> getCapabilities() {
            return StructureElementCapability.snapshotSafe();
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @Override
        public TraceabilityPredicate toPredicate() {
            return predicate;
        }

        private StructureMatchSession lastSession() {
            return state.lastSession;
        }
    }

    private static final class ErrorElement
            implements ITypedStructureElement<Object> {

        @Override
        public boolean check(
                @NotNull StructureEvaluationContext<Object> context) {
            context.setError(new PatternStringError(
                    "gregtech:test/runtime_detector_error"));
            return false;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
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

    private static final class MinimumCountElement implements ITypedStructureElement<Object> {

        private final int minimum;
        private final TraceabilityPredicate predicate;

        private MinimumCountElement(int minimum) {
            this(minimum, new TraceabilityPredicate());
        }

        private MinimumCountElement(int minimum,
                                    TraceabilityPredicate predicate) {
            this.minimum = minimum;
            this.predicate = predicate;
        }

        private MinimumCountElement withPredicate(TraceabilityPredicate predicate) {
            return new MinimumCountElement(minimum, predicate);
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            StructureMatchCollector collector = context.getCollector();
            collector.declareCount(this, minimum, -1, null, null);
            return collector.recordCount(this);
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @Override
        public TraceabilityPredicate toPredicate() {
            return predicate;
        }
    }

    private static final class MaximumCountElement implements ITypedStructureElement<Object> {

        private final int maximum;

        private MaximumCountElement(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            StructureMatchCollector collector = context.getCollector();
            collector.declareCount(MaximumCountElement.class, 0, maximum, null, null);
            return collector.recordCount(MaximumCountElement.class);
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }
    }

    private static final class LegacyContextWritingElement implements ITypedStructureElement<Object> {

        private int contextCopiesSeen;

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            PatternMatchContext legacyContext = context.getLegacyContext();
            legacyContext.set("direct_scratch", "not-shared");
            contextCopiesSeen++;
            context.getCollector().setValue("typed_value", 1);
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
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
        private PatternMatchContext legacyCallbackContext;

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

        void invokeFormStructure(@NotNull FormedStructureView view) {
            formStructure(view);
        }

        @Override
        protected void formStructure(PatternMatchContext context) {
            legacyCallbackContext = context.copy();
        }
    }

    private static final class TypedCallbackController extends MultiblockControllerBase {

        private FormedStructureView typedCallbackView;
        private PatternMatchContext legacyCallbackContext;

        private TypedCallbackController() {
            super(new ResourceLocation("gregtech", "typed_callback_test_controller"));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return this;
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }

        void invokeFormStructure(@NotNull FormedStructureView view) {
            formStructure(view);
        }

        @Override
        protected void formStructure(@NotNull FormedStructureView formed) {
            typedCallbackView = formed;
        }

        @Override
        protected void formStructure(PatternMatchContext context) {
            legacyCallbackContext = context.copy();
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
