package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.impl.ChainElement;
import gregtech.api.pattern.element.impl.LegacyElement;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureDependencyCompilerTest {

    private static final World WORLD = bareWorld();

    @Test
    void dynamicOffsetCompilesAnchorEdge() {
        StructurePiece anchor = repeatPiece("body");
        StructurePiece top = new DynamicOffsetPiece(
                "top", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, null, "body", new int[] {0, 0, 1});
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Arrays.asList(anchor, top)));

        assertTrue(plan.isEligible(), plan.describe());
        assertEquals(1, plan.getGraph().getEdges().size());
        PieceDependencyGraph.Edge edge = plan.getGraph().getEdges().get(0);
        assertEquals("body", edge.getSourcePiece());
        assertEquals("top", edge.getTargetPiece());
        assertTrue(edge.getAspects().contains(PieceDependencyAspect.CENTER));
        assertTrue(edge.getAspects().contains(PieceDependencyAspect.REPETITIONS));
        assertEquals("dynamic-anchor", edge.getReason());
    }

    @Test
    void typedConditionDependencyCompilesPieceEdge() {
        StructureCondition<Object> condition = StructureCondition.withDependencies(
                context -> true,
                StructureDependency.piece("core", PieceDependencyAspect.CONTRIBUTION_VALUE));
        StructurePiece core = piece("core");
        StructurePiece conditional = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Arrays.asList(core, conditional)));

        assertTrue(plan.isEligible(), plan.describe());
        assertEquals(1, plan.getGraph().getEdges().size());
        PieceDependencyGraph.Edge edge = plan.getGraph().getEdges().get(0);
        assertEquals("core", edge.getSourcePiece());
        assertEquals("conditional", edge.getTargetPiece());
        assertTrue(edge.getAspects().contains(PieceDependencyAspect.CONTRIBUTION_VALUE));
        assertTrue(edge.getReason().contains("condition"));
    }

    @Test
    void directElementDependenciesCompileEdgesAndExternalRoots() {
        StructureExternalDependencyKey<Integer> external = StructureExternalDependencyKey.create(
                "gregtech:test_direct_external", controller -> 1, Objects::equals);
        StructurePiece source = piece("source");
        StructurePiece target = new StructurePiece(
                "target",
                template(new DependentElement(
                        StructureDependency.piece("source", PieceDependencyAspect.CONTRIBUTION_VALUE),
                        StructureDependency.external(external, PieceDependencyAspect.CONTROLLER_STATE))),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null);

        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Arrays.asList(source, target)));

        assertTrue(plan.isEligible(), plan.describe());
        assertEquals(1, plan.getGraph().getEdges().size());
        PieceDependencyGraph.Edge edge = plan.getGraph().getEdges().get(0);
        assertEquals("source", edge.getSourcePiece());
        assertEquals("target", edge.getTargetPiece());
        assertTrue(edge.getAspects().contains(PieceDependencyAspect.CONTRIBUTION_VALUE));
        assertTrue(edge.getReason().contains("element:"));
        assertTrue(plan.getExternalDependencies().contains(external));
        assertEquals(Collections.singleton("target"), plan.getExternalDependencyRoots(external));
    }

    @Test
    void chainElementDependenciesCompileEdgesAndExternalRoots() {
        StructureExternalDependencyKey<Integer> external = StructureExternalDependencyKey.create(
                "gregtech:test_chain_external", controller -> 1, Objects::equals);
        StructurePiece source = piece("source");
        StructurePiece target = new StructurePiece(
                "target",
                template(new ChainElement(
                        new MatchingElement(),
                        new DependentElement(
                                StructureDependency.piece("source", PieceDependencyAspect.CONTRIBUTION_VALUE),
                                StructureDependency.external(external, PieceDependencyAspect.CONTROLLER_STATE)))),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null);

        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Arrays.asList(source, target)));

        assertTrue(plan.isEligible(), plan.describe());
        assertEquals(1, plan.getGraph().getEdges().size());
        PieceDependencyGraph.Edge edge = plan.getGraph().getEdges().get(0);
        assertEquals("source", edge.getSourcePiece());
        assertEquals("target", edge.getTargetPiece());
        assertTrue(edge.getAspects().contains(PieceDependencyAspect.CONTRIBUTION_VALUE));
        assertTrue(plan.getExternalDependencies().contains(external));
        assertEquals(Collections.singleton("target"), plan.getExternalDependencyRoots(external));
    }

    @Test
    void chainContainingLegacyElementFallsBackAsOpaque() {
        StructurePiece legacyChain = new StructurePiece(
                "legacy_chain",
                template(new ChainElement(new MatchingElement(), new LegacyElement(new TraceabilityPredicate()))),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(legacyChain)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("opaque element"));
    }

    @Test
    void opaqueBooleanSupplierConditionFallsBackDeterministically() {
        StructurePiece core = piece("core");
        StructurePiece conditional = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, () -> true);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Arrays.asList(core, conditional)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("BooleanSupplier"));
    }

    @Test
    void contextualConditionWithoutDependenciesIsOpaque() {
        StructureCondition<Object> condition = context -> true;
        StructurePiece conditional = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(conditional)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("declares no typed dependencies"));
    }

    @Test
    void conditionReturningNullDependenciesFallsBack() {
        StructureCondition<Object> condition = new StructureCondition<Object>() {

            @Override
            public boolean test(StructureActivationContext<Object> context) {
                return true;
            }

            @Override
            public Set<StructureDependency> dependencies() {
                return null;
            }
        };
        StructurePiece conditional = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(conditional)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("returned null dependencies"));
    }

    @Test
    void unknownPieceDependencyHasStableFallbackReason() {
        StructureCondition<Object> condition = StructureCondition.withDependencies(
                context -> true,
                StructureDependency.piece("missing", PieceDependencyAspect.ACTIVATION));
        StructurePiece conditional = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(conditional)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.UNKNOWN_DEPENDENCY,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("missing"));
    }

    @Test
    void futurePieceDependencyIsCycleFallback() {
        StructureCondition<Object> condition = StructureCondition.withDependencies(
                context -> true,
                StructureDependency.piece("later", PieceDependencyAspect.ACTIVATION));
        StructurePiece first = new StructurePiece(
                "first", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructurePiece later = piece("later");
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Arrays.asList(first, later)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.DEPENDENCY_CYCLE,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("same or later"));
    }

    @Test
    void opaqueElementHasStableFallbackReason() {
        StructurePiece legacy = new StructurePiece(
                "legacy", template(new LegacyElement(new TraceabilityPredicate())),
                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(legacy)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("opaque element"));
    }

    @Test
    void directElementWithoutExplicitIncrementalContractFallsBack() {
        IStructureElement<Object> undeclared = new MatchingElement() {

            @Override
            public boolean hasExplicitIncrementalContract() {
                return false;
            }
        };
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(
                        new StructurePiece("undeclared", template(undeclared),
                                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null))));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("without an explicit incremental"));
    }

    @Test
    void explicitContractThrowFallsBackAsOpaqueElement() {
        IStructureElement<Object> element = new MatchingElement() {

            @Override
            public boolean hasExplicitIncrementalContract() {
                throw new IllegalStateException("bad contract");
            }
        };
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(
                        new StructurePiece("bad_contract", template(element),
                                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null))));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("threw while declaring explicit incremental contract"));
    }

    @Test
    void nullIncrementalSupportFallsBackAsOpaqueElement() {
        IStructureElement<Object> element = new MatchingElement() {

            @Override
            public StructureIncrementalSupport getIncrementalSupport() {
                return null;
            }
        };
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(
                        new StructurePiece("null_support", template(element),
                                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null))));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("returned null incremental support"));
    }

    @Test
    void elementReturningNullDependenciesFallsBack() {
        IStructureElement<Object> element = new MatchingElement() {

            @Override
            public Set<StructureDependency> getDependencies() {
                return null;
            }
        };
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(
                        new StructurePiece("null_deps", template(element),
                                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null))));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("returned null dependencies"));
    }

    @Test
    void nullDependencyEntryFallsBackWithDiagnostic() {
        StructurePiece target = new StructurePiece(
                "target", template(new DependentElement((StructureDependency) null)),
                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(target)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.UNKNOWN_DEPENDENCY,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("declares a null dependency"));
    }

    @Test
    void nullExternalDependencyKeyFallsBackWithDiagnostic() {
        StructurePiece target = new StructurePiece(
                "target", template(new DependentElement(nullExternalDependency())),
                Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(target)));

        assertFalse(plan.isEligible());
        assertEquals(StructureIncrementalFallbackReason.UNKNOWN_DEPENDENCY,
                plan.getFallbackReason());
        assertTrue(plan.describeFallback().contains("declares a null external dependency"));
    }

    @Test
    void externalDependencySnapshotReportsChangedKeys() {
        AtomicInteger externalState = new AtomicInteger(1);
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_external", controller -> externalState.get(), Objects::equals);
        StructureCondition<Object> condition = StructureCondition.withDependencies(
                context -> true,
                StructureDependency.external(key, PieceDependencyAspect.CONTROLLER_STATE));
        StructurePiece conditional = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, condition);
        StructureEligibilityPlan plan = StructureDependencyCompiler.compile(
                new MultiPiecePattern(Collections.singletonList(conditional)));

        assertTrue(plan.isEligible(), plan.describe());
        assertTrue(plan.getExternalDependencies().contains(key));
        assertEquals(Collections.singleton("conditional"),
                plan.getExternalDependencyRoots(key));
        StructureExternalDependencySnapshot first =
                plan.snapshotExternalDependencies(null);
        externalState.set(2);
        StructureExternalDependencySnapshot second =
                plan.snapshotExternalDependencies(null);

        assertFalse(second.isEquivalentTo(first));
        assertEquals(Collections.singleton(key), second.changedKeys(first));
        assertEquals(Collections.singleton("conditional"),
                plan.rootsForExternalDependencyChanges(second.changedKeys(first)));
    }

    @Test
    void externalDependencySnapshotRetainsCaptureFailureDiagnostic() {
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_external_snapshot_failure",
                controller -> {
                    throw new IllegalStateException("snapshot boom");
                },
                Objects::equals);

        StructureExternalDependencySnapshot snapshot =
                StructureExternalDependencySnapshot.capture(Collections.singleton(key), null);

        assertTrue(snapshot.hasFailures());
        assertTrue(snapshot.describeFailures().contains("gregtech:test_external_snapshot_failure"));
        assertTrue(snapshot.describeFailures().contains("snapshot boom"));
        assertEquals(Collections.singleton(key),
                snapshot.changedKeys(StructureExternalDependencySnapshot.capture(
                        Collections.emptySet(), null)));
    }

    @Test
    void externalDependencyComparisonFailureMarksChangedKey() {
        AtomicInteger externalState = new AtomicInteger(1);
        StructureExternalDependencyKey<Integer> key = StructureExternalDependencyKey.create(
                "gregtech:test_external_compare_failure",
                controller -> externalState.get(),
                (left, right) -> {
                    throw new IllegalStateException("compare boom");
                });

        StructureExternalDependencySnapshot first =
                StructureExternalDependencySnapshot.capture(Collections.singleton(key), null);
        externalState.set(2);
        StructureExternalDependencySnapshot second =
                StructureExternalDependencySnapshot.capture(Collections.singleton(key), null);

        StructureExternalDependencySnapshot.ChangeSet changes = second.changesFrom(first);
        assertEquals(Collections.singleton(key), changes.getChangedKeys());
        assertTrue(changes.hasFailures());
        assertTrue(changes.describeFailures().contains("compare boom"));
    }

    @Test
    void versionedSnapshotFreezesNestedMutableValues() {
        List<String> list = new ArrayList<>(Collections.singletonList("before"));
        int[] array = new int[] {1};
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("list", list);
        value.put("array", array);

        StructureExternalDependencies.VersionedSnapshot first =
                StructureExternalDependencies.VersionedSnapshot.of(0, value);

        list.add("after");
        array[0] = 2;

        StructureExternalDependencies.VersionedSnapshot second =
                StructureExternalDependencies.VersionedSnapshot.of(0, value);

        assertNotEquals(first, second);
    }

    @Test
    void ineligibleDefinitionCheckFallsBackToActiveGraph() {
        StructurePiece piece = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, () -> true);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        StructureRuntime runtime = new StructureRuntime(
                gregtech.api.pattern.element.StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));

        StructureCheckResult result = runtime.check(StructureOperationRequest.check(
                WORLD, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false, null, null));

        assertTrue(result.isMatched());
        assertTrue(result.usedActiveGraphFallback());
        assertEquals("active-graph-fallback", result.getTracePath());
        assertNotNull(result.getEligibilityPlan());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                result.getEligibilityPlan().getFallbackReason());
    }

    @Test
    void ineligibleIncrementalFallbackKeepsEligibilityDiagnostics() {
        StructurePiece piece = new StructurePiece(
                "conditional", template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, () -> true);
        MultiPiecePattern pattern = new MultiPiecePattern(Collections.singletonList(piece));
        StructureRuntime runtime = new StructureRuntime(
                gregtech.api.pattern.element.StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));

        StructureCheckResult result = runtime.checkIncremental(StructureOperationRequest.check(
                WORLD, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                false, null, null));

        assertTrue(result.isMatched());
        assertTrue(result.usedActiveGraphFallback());
        assertNotNull(result.getEligibilityPlan());
        assertEquals(StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                result.getEligibilityPlan().getFallbackReason());
        assertTrue(result.createFailureTrace(diagnosticsController())
                .getActual()
                .contains("OPAQUE_CONDITION"));
    }

    private static StructurePiece piece(@NotNull String name) {
        return new StructurePiece(
                name, template(new MatchingElement()), Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE, null);
    }

    private static RepeatGroupPiece repeatPiece(@NotNull String name) {
        return new RepeatGroupPiece(
                name,
                template(new MatchingElement()),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null,
                new int[] {2},
                new int[][] {{1, 3}},
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

    private static class MatchingElement implements ITypedStructureElement<Object> {

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return true;
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return true;
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

    private static final class DependentElement extends MatchingElement {

        @NotNull
        private final Set<StructureDependency> dependencies;

        private DependentElement(@NotNull StructureDependency... dependencies) {
            this.dependencies = new java.util.LinkedHashSet<>(Arrays.asList(dependencies));
        }

        @NotNull
        @Override
        public Set<StructureDependency> getDependencies() {
            return Collections.unmodifiableSet(dependencies);
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

    @NotNull
    private static DiagnosticsController diagnosticsController() {
        try {
            DiagnosticsController controller =
                    (DiagnosticsController) unsafe().allocateInstance(DiagnosticsController.class);
            setField(gregtech.api.metatileentity.MetaTileEntity.class, controller,
                    "metaTileEntityId", new ResourceLocation("gregtech", "diagnostics_controller"));
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate diagnostics controller", e);
        }
    }

    private static final class DiagnosticsController
            extends gregtech.api.metatileentity.multiblock.MultiblockControllerBase {

        private DiagnosticsController() {
            super(new ResourceLocation("gregtech", "diagnostics_controller"));
        }

        @Override
        public gregtech.api.metatileentity.MetaTileEntity createMetaTileEntity(
                gregtech.api.metatileentity.interfaces.IGregTechTileEntity tileEntity) {
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
        public ICubeRenderer getBaseTexture(
                gregtech.api.metatileentity.multiblock.IMultiblockPart sourcePart) {
            return null;
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

    @NotNull
    private static StructureDependency nullExternalDependency() {
        try {
            java.lang.reflect.Constructor<StructureDependency> constructor =
                    StructureDependency.class.getDeclaredConstructor(
                            StructureDependency.Kind.class,
                            String.class,
                            StructureExternalDependencyKey.class,
                            Set.class,
                            String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    StructureDependency.Kind.EXTERNAL,
                    null,
                    null,
                    Collections.singleton(PieceDependencyAspect.CONTROLLER_STATE),
                    "bad-test-dependency");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to create null external dependency", e);
        }
    }
}
