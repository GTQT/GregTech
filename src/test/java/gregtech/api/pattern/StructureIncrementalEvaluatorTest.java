package gregtech.api.pattern;

import gregtech.api.capability.IControllable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.impl.BlockElement;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
    void externalDependencyChangeRechecksDeclaredRoot() {
        AtomicInteger externalState = new AtomicInteger(1);
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_incremental_external",
                controller -> externalState.get(),
                java.util.Objects::equals);
        CountingElement independent = new CountingElement(true);
        DependentElement dependent = new DependentElement(
                true, StructureDependency.external(key, PieceDependencyAspect.CONTROLLER_STATE));
        StructureRuntime runtime = runtime(pattern(
                piece("independent", independent),
                piece("dependent", dependent)));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());

        externalState.set(2);
        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertEquals(1, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(1, independent.calls());
        assertEquals(2, dependent.calls());
    }

    @Test
    void externalDependencySnapshotFailureFallsBackWithDiagnostics() {
        AtomicInteger externalState = new AtomicInteger(1);
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_runtime_snapshot_failure",
                controller -> {
                    int state = externalState.get();
                    if (state < 0) {
                        throw new IllegalStateException("snapshot boom");
                    }
                    return state;
                },
                java.util.Objects::equals);
        CountingElement independent = new CountingElement(true);
        DependentElement dependent = new DependentElement(
                true, StructureDependency.external(key, PieceDependencyAspect.CONTROLLER_STATE));
        StructureRuntime runtime = runtime(pattern(
                piece("independent", independent),
                piece("dependent", dependent)));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());

        externalState.set(-1);
        StructureCheckResult fallback = runtime.checkIncremental(checkRequest());

        assertTrue(fallback.isMatched());
        assertFalse(fallback.usedIncrementalEvaluator());
        assertEquals("definition-fallback", fallback.getTracePath());
        String actual = fallback.createFailureTrace(diagnosticsController()).getActual();
        assertTrue(actual.contains("UNKNOWN_EXTERNAL_DEPENDENCY"));
        assertTrue(actual.contains("snapshot boom"));
        assertEquals(2, independent.calls());
        assertEquals(2, dependent.calls());
    }

    @Test
    void externalDependencyComparisonFailureFallsBackWithDiagnostics() {
        AtomicInteger externalState = new AtomicInteger(1);
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_runtime_compare_failure",
                controller -> externalState.get(),
                (left, right) -> {
                    if (!java.util.Objects.equals(left, right)) {
                        throw new IllegalStateException("compare boom");
                    }
                    return true;
                });
        CountingElement independent = new CountingElement(true);
        DependentElement dependent = new DependentElement(
                true, StructureDependency.external(key, PieceDependencyAspect.CONTROLLER_STATE));
        StructureRuntime runtime = runtime(pattern(
                piece("independent", independent),
                piece("dependent", dependent)));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());

        externalState.set(2);
        StructureCheckResult fallback = runtime.checkIncremental(checkRequest());

        assertTrue(fallback.isMatched());
        assertFalse(fallback.usedIncrementalEvaluator());
        assertEquals("definition-fallback", fallback.getTracePath());
        String actual = fallback.createFailureTrace(diagnosticsController()).getActual();
        assertTrue(actual.contains("UNKNOWN_EXTERNAL_DEPENDENCY"));
        assertTrue(actual.contains("compare boom"));
        assertEquals(2, independent.calls());
        assertEquals(2, dependent.calls());
    }

    @Test
    void multipleDirtyRootsReuseCleanPieces() {
        CountingElement first = new CountingElement(true);
        CountingElement second = new CountingElement(true);
        CountingElement third = new CountingElement(true);
        CountingElement fourth = new CountingElement(true);
        StructureRuntime runtime = runtime(pattern(
                piece("first", first),
                piece("second", second),
                piece("third", third),
                piece("fourth", fourth)));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());
        assertTrue(runtime.addDirtyRoots(Arrays.asList("first", "third")));

        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertEquals(2, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(2, first.calls());
        assertEquals(1, second.calls());
        assertEquals(2, third.calls());
        assertEquals(1, fourth.calls());
    }

    @Test
    void repeatGroupDirtyRootReusesIndependentCleanPiece() {
        CountingElement repeatElement = new CountingElement(true);
        CountingElement cleanElement = new CountingElement(true);
        RepeatGroupPiece repeat = repeatPiece("repeat", repeatElement);
        StructurePiece clean = piece("clean", cleanElement);
        StructureRuntime runtime = runtime(pattern(repeat, clean));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());
        assertTrue(runtime.addDirtyRoot("repeat"));

        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertEquals(1, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(2, repeatElement.calls());
        assertEquals(1, cleanElement.calls());
    }

    @Test
    void incrementalDirtyRootCacheProbeReusesBaselineContribution() {
        net.minecraft.init.Bootstrap.register();
        LoadedBareWorld world = loadedBareWorld(Blocks.STONE.getDefaultState());
        CountingBlockElement element =
                new CountingBlockElement(Blocks.STONE.getDefaultState(), 7);
        StructureRuntime runtime = runtime(pattern(new StructurePiece(
                "dirty", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null)));
        StructureOperationRequest request = StructureOperationRequest.check(
                world, BlockPos.ORIGIN, ORIENTATION, false, null, null);
        StructureCheckResult full = runtime.check(request);
        runtime.publishCommittedGraph(full.getGraphPublication());
        assertTrue(runtime.addDirtyRoot("dirty"));

        StructureCheckResult incremental = runtime.checkIncremental(request);

        assertTrue(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertEquals(1, element.calls());
        assertEquals(1, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(1, incremental.getIncrementalCheckResult().getCacheProbeAttempts());
        assertEquals(1, incremental.getIncrementalCheckResult().getCacheProbeHits());
        assertEquals(0, incremental.getIncrementalCheckResult().getCacheProbeMisses());
        assertEquals(7, incremental.copyContext().getInt("channel"));
        assertSame(full.getGraphPublication().getAggregate(),
                incremental.getContributionAggregate());
    }

    @Test
    void incrementalDirtyRootCacheProbeMissFallsBackToFullPieceCheck() {
        net.minecraft.init.Bootstrap.register();
        LoadedBareWorld world = loadedBareWorld(Blocks.STONE.getDefaultState());
        CountingBlockElement element =
                new CountingBlockElement(Blocks.STONE.getDefaultState(), 7);
        StructureRuntime runtime = runtime(pattern(new StructurePiece(
                "dirty", template(element), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null)));
        StructureOperationRequest request = StructureOperationRequest.check(
                world, BlockPos.ORIGIN, ORIENTATION, false, null, null);
        StructureCheckResult full = runtime.check(request);
        runtime.publishCommittedGraph(full.getGraphPublication());
        world.state = Blocks.AIR.getDefaultState();
        assertTrue(runtime.addDirtyRoot("dirty"));

        StructureCheckResult incremental = runtime.checkIncremental(request);

        assertFalse(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertEquals(2, element.calls());
        assertEquals(1, incremental.getIncrementalCheckResult().getCacheProbeAttempts());
        assertEquals(0, incremental.getIncrementalCheckResult().getCacheProbeHits());
        assertEquals(1, incremental.getIncrementalCheckResult().getCacheProbeMisses());
    }

    @Test
    void realMachineStyleMatrixDoesNotReadIndependentCleanPiece() {
        AtomicInteger externalState = new AtomicInteger(1);
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_matrix_external",
                controller -> externalState.get(),
                java.util.Objects::equals);
        WorldReadingElement fixed = new WorldReadingElement(true);
        WorldReadingElement repeat = new WorldReadingElement(true);
        WorldReadingElement dynamic = new WorldReadingElement(true);
        WorldReadingDependentElement external = new WorldReadingDependentElement(
                true, StructureDependency.external(key, PieceDependencyAspect.CONTROLLER_STATE));
        WorldReadingElement clean = new WorldReadingElement(true);
        RepeatGroupPiece repeatPiece = repeatPiece("repeat", repeat);
        DynamicOffsetPiece dynamicPiece = new DynamicOffsetPiece(
                "dynamic", template(dynamic), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, null, "repeat", new int[] {0, 0, 1});
        StructureRuntime runtime = runtime(pattern(
                piece("fixed", fixed),
                repeatPiece,
                dynamicPiece,
                piece("external", external),
                piece("clean", clean)));
        StructureCheckResult full = runtime.check(checkRequest());
        runtime.publishCommittedGraph(full.getGraphPublication());

        externalState.set(2);
        assertTrue(runtime.addDirtyRoots(Arrays.asList("fixed", "repeat")));
        StructureCheckResult incremental = runtime.checkIncremental(checkRequest());

        assertTrue(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertEquals(2, fixed.worldReads());
        assertEquals(2, repeat.worldReads());
        assertEquals(2, external.worldReads());
        assertEquals(1, clean.worldReads());
        assertTrue(dynamic.worldReads() <= 2);
    }

    @Test
    void realMachineExternalStateMatrixRechecksOnlyDeclaredRoots() {
        MachineStateController controller = machineStateController();
        WorldReadingDependentElement modeElement = new WorldReadingDependentElement(
                true, StructureExternalDependencies.controllerMode());
        WorldReadingDependentElement configElement = new WorldReadingDependentElement(
                true, StructureExternalDependencies.configuration());
        WorldReadingDependentElement upgradeElement = new WorldReadingDependentElement(
                true, StructureExternalDependencies.upgrades());
        WorldReadingDependentElement channelElement = new WorldReadingDependentElement(
                true, StructureExternalDependencies.channelValues());
        WorldReadingElement cleanElement = new WorldReadingElement(true);
        StructureRuntime runtime = runtime(pattern(
                piece("mode", modeElement),
                piece("config", configElement),
                piece("upgrade", upgradeElement),
                piece("channel", channelElement),
                piece("clean", cleanElement)));
        StructureOperationRequest request = checkRequest(controller);
        StructureCheckResult full = runtime.check(request);
        runtime.publishCommittedGraph(full.getGraphPublication());

        controller.workingEnabled = false;
        controller.configRecipeMap = "distillery";
        controller.configThrottle = 60;
        controller.upgradeTier = 2;
        controller.channelTier = 3;
        StructureCheckResult incremental = runtime.checkIncremental(request);

        assertTrue(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertEquals(4, incremental.getIncrementalCheckResult().getRecheckedPieces());
        assertEquals(2, modeElement.worldReads());
        assertEquals(2, configElement.worldReads());
        assertEquals(2, upgradeElement.worldReads());
        assertEquals(2, channelElement.worldReads());
        assertEquals(1, cleanElement.worldReads());
    }

    @Test
    void detachedDirtyPrecheckIsStateOnlyAndLiveConfirmRemainsIncremental() {
        net.minecraft.init.Bootstrap.register();
        LoadedBareWorld world = loadedBareWorld(Blocks.STONE.getDefaultState());
        StructureRuntime runtime = runtime(pattern(new StructurePiece(
                "dirty", template(new BlockElement(Blocks.STONE.getDefaultState())),
                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null)));
        StructureOperationRequest request = StructureOperationRequest.check(
                world, BlockPos.ORIGIN, ORIENTATION, false, null, null);
        StructureCheckResult full = runtime.check(request);
        runtime.publishCommittedGraph(full.getGraphPublication());
        assertTrue(runtime.addDirtyRoot("dirty"));
        assertEquals(1, full.getResultTable().get("dirty")
                .getWatchedPositions().size());

        StructureDirtyPrecheck precheck = runtime.createDirtyPrecheck(null);
        assertNotNull(precheck);
        assertEquals(1, precheck.getPositionCount());
        StructureDirtyPrecheck.Snapshot snapshot = precheck.capture(world);
        assertNotNull(snapshot);
        StructureDirtyPrecheck.Result precheckResult = precheck.evaluate(snapshot);
        assertTrue(precheckResult.matchedBaseline());

        StructureCheckResult incremental =
                runtime.checkIncremental(request, precheckResult);

        assertTrue(incremental.isMatched());
        assertTrue(incremental.usedIncrementalEvaluator());
        assertTrue(incremental.getIncrementalCheckResult()
                .wasSnapshotPrecheckAsynchronous());
        assertEquals(1, incremental.getIncrementalCheckResult()
                .getSnapshotPrecheckPositions());
        assertEquals(1, incremental.getIncrementalCheckResult()
                .getRecheckedPieces());
    }

    @Test
    void detachedDirtyPrecheckDetectsChangedBlockState() {
        net.minecraft.init.Bootstrap.register();
        LoadedBareWorld world = loadedBareWorld(Blocks.STONE.getDefaultState());
        StructureRuntime runtime = runtime(pattern(new StructurePiece(
                "dirty", template(new BlockElement(Blocks.STONE.getDefaultState())),
                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null)));
        StructureCheckResult full = runtime.check(StructureOperationRequest.check(
                world, BlockPos.ORIGIN, ORIENTATION, false, null, null));
        runtime.publishCommittedGraph(full.getGraphPublication());
        assertTrue(runtime.addDirtyRoot("dirty"));

        StructureDirtyPrecheck precheck = runtime.createDirtyPrecheck(null);
        assertNotNull(precheck);
        world.state = Blocks.AIR.getDefaultState();
        StructureDirtyPrecheck.Snapshot snapshot = precheck.capture(world);

        assertNotNull(snapshot);
        assertFalse(precheck.evaluate(snapshot).matchedBaseline());
    }

    @Test
    void shadowComparatorDetectsTypedResultDifference() {
        AtomicInteger value = new AtomicInteger(1);
        StructureRuntime firstRuntime = runtime(pattern(
                piece("piece", new ChannelEmittingElement(true, value))));
        StructureCheckResult first = firstRuntime.check(checkRequest());
        value.set(2);
        StructureRuntime secondRuntime = runtime(pattern(
                piece("piece", new ChannelEmittingElement(true, value))));
        StructureCheckResult second = secondRuntime.check(checkRequest());

        assertNotNull(StructureShadowValidator.compare(first, second));
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
    private static StructureOperationRequest checkRequest(@NotNull MultiblockControllerBase controller) {
        return StructureOperationRequest.check(
                WORLD, BlockPos.ORIGIN, ORIENTATION, false, null, controller);
    }

    @NotNull
    private static DiagnosticsController diagnosticsController() {
        try {
            DiagnosticsController controller =
                    (DiagnosticsController) unsafe().allocateInstance(DiagnosticsController.class);
            setField(MetaTileEntity.class, controller,
                    "metaTileEntityId", new ResourceLocation("gregtech", "diagnostics_controller"));
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate diagnostics controller", e);
        }
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

    private static void setField(@NotNull Class<?> owner,
                                 @NotNull Object target,
                                 @NotNull String name,
                                 @NotNull Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to set field " + name, e);
        }
    }

    private static class CountingElement implements ITypedStructureElement<Object> {

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
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }
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

    private static final class ChannelEmittingElement extends CountingElement {

        @NotNull
        private final AtomicInteger value;

        private ChannelEmittingElement(boolean matches, @NotNull AtomicInteger value) {
            super(matches);
            this.value = value;
        }

        @Override
        protected void afterCall(@NotNull StructureEvaluationContext<Object> context) {
            int current = value.get();
            context.getCollector().emit(TEST_VALUE, current);
            context.getCollector().recordChannelValue("channel", current, true);
        }
    }

    private static final class CountingBlockElement extends BlockElement {

        private final AtomicInteger calls = new AtomicInteger();
        private final int channelValue;

        private CountingBlockElement(@NotNull IBlockState state,
                                     int channelValue) {
            super(state);
            this.channelValue = channelValue;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            calls.incrementAndGet();
            boolean matched = super.check(context);
            if (matched) {
                context.getCollector().recordChannelValue("channel", channelValue, true);
            }
            return matched;
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class DependentElement extends CountingElement {

        @NotNull
        private final Set<StructureDependency> dependencies;

        private DependentElement(boolean matches, @NotNull StructureDependency... dependencies) {
            super(matches);
            this.dependencies = Collections.unmodifiableSet(
                    new LinkedHashSet<>(Arrays.asList(dependencies)));
        }

        @NotNull
        @Override
        public Set<StructureDependency> getDependencies() {
            return dependencies;
        }
    }

    private static class WorldReadingElement extends CountingElement {

        private final AtomicInteger worldReads = new AtomicInteger();

        private WorldReadingElement(boolean matches) {
            super(matches);
        }

        @Override
        protected void afterCall(@NotNull StructureEvaluationContext<Object> context) {
            worldReads.incrementAndGet();
            context.getBlockAccess();
        }

        int worldReads() {
            return worldReads.get();
        }
    }

    private static final class WorldReadingDependentElement extends WorldReadingElement {

        @NotNull
        private final Set<StructureDependency> dependencies;

        private WorldReadingDependentElement(boolean matches, @NotNull StructureDependency... dependencies) {
            super(matches);
            this.dependencies = Collections.unmodifiableSet(
                    new LinkedHashSet<>(Arrays.asList(dependencies)));
        }

        @NotNull
        @Override
        public Set<StructureDependency> getDependencies() {
            return dependencies;
        }
    }

    private static final class MachineStateController extends MultiblockControllerBase implements IControllable {

        private boolean workingEnabled = true;
        private String mode = "normal";
        private String configRecipeMap = "assembler";
        private int configThrottle = 100;
        private int upgradeTier = 1;
        private int channelTier = 1;

        private MachineStateController() {
            super(new ResourceLocation("gregtech", "machine_state_matrix_test"));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return this;
        }

        @Override
        public boolean isWorkingEnabled() {
            return workingEnabled;
        }

        @Override
        public void setWorkingEnabled(boolean isWorkingAllowed) {
            workingEnabled = isWorkingAllowed;
        }

        @Override
        protected Object getStructureControllerModeValue() {
            return mode;
        }

        @Override
        protected Object getStructureConfigDependencyValue() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("recipeMap", configRecipeMap);
            values.put("throttle", configThrottle);
            return values;
        }

        @Override
        protected Object getStructureUpgradeDependencyValue() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("tier", upgradeTier);
            return values;
        }

        @Override
        protected Object getStructureChannelDependencyValue() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("coil", channelTier);
            return values;
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }
    }

    private static final class DiagnosticsController extends MultiblockControllerBase {

        private DiagnosticsController() {
            super(new ResourceLocation("gregtech", "diagnostics_controller"));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return this;
        }

        @Override
        public World getWorld() {
            return WORLD;
        }

        @Override
        public BlockPos getPos() {
            return BlockPos.ORIGIN;
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }
    }

    @NotNull
    private static MachineStateController machineStateController() {
        try {
            MachineStateController controller = (MachineStateController) unsafe()
                    .allocateInstance(MachineStateController.class);
            controller.workingEnabled = true;
            controller.mode = "normal";
            controller.configRecipeMap = "assembler";
            controller.configThrottle = 100;
            controller.upgradeTier = 1;
            controller.channelTier = 1;
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate machine state controller", e);
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

    private static LoadedBareWorld loadedBareWorld(@NotNull IBlockState state) {
        try {
            LoadedBareWorld world = (LoadedBareWorld) unsafe()
                    .allocateInstance(LoadedBareWorld.class);
            world.state = state;
            return world;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate loaded bare test world", e);
        }
    }

    private static final class LoadedBareWorld extends World {

        private IBlockState state;

        private LoadedBareWorld() {
            super(null, null, null, null, false);
        }

        @Override
        public boolean isBlockLoaded(@NotNull BlockPos pos) {
            return true;
        }

        @NotNull
        @Override
        public IBlockState getBlockState(@NotNull BlockPos pos) {
            return state;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return true;
        }
    }
}
