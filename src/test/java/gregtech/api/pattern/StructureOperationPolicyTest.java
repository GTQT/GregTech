package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static gregtech.api.pattern.StructureEvaluationContext.Operation.CREATIVE_BUILD;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.HINT;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.ITERATE;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.MATCH_SNAPSHOT;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.MATCH_WORLD;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.PREVIEW;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.SURVIVAL_BUILD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureOperationPolicyTest {

    @Test
    void matchOperationsCollectFormationStateWithoutMutatingWorld() {
        assertTrue(MATCH_WORLD.isMatch());
        assertTrue(MATCH_WORLD.readsWorld());
        assertFalse(MATCH_WORLD.readsSnapshot());
        assertTrue(MATCH_WORLD.collectsFormationState());
        assertFalse(MATCH_WORLD.mutatesWorld());

        assertTrue(MATCH_SNAPSHOT.isMatch());
        assertTrue(MATCH_SNAPSHOT.readsWorld());
        assertTrue(MATCH_SNAPSHOT.readsSnapshot());
        assertTrue(MATCH_SNAPSHOT.collectsFormationState());
        assertFalse(MATCH_SNAPSHOT.mutatesWorld());
    }

    @Test
    void buildOperationsMutateWorldWithoutCollectingFormationState() {
        assertTrue(CREATIVE_BUILD.isBuild());
        assertTrue(CREATIVE_BUILD.isCreativeBuild());
        assertTrue(CREATIVE_BUILD.readsWorld());
        assertTrue(CREATIVE_BUILD.mutatesWorld());
        assertTrue(CREATIVE_BUILD.isNonFormationProbe());

        assertTrue(SURVIVAL_BUILD.isBuild());
        assertTrue(SURVIVAL_BUILD.isSurvivalBuild());
        assertTrue(SURVIVAL_BUILD.readsWorld());
        assertTrue(SURVIVAL_BUILD.mutatesWorld());
        assertTrue(SURVIVAL_BUILD.isNonFormationProbe());
    }

    @Test
    void nonCheckToolOperationsDoNotCollectFormationState() {
        assertFalse(PREVIEW.readsWorld());
        assertFalse(PREVIEW.mutatesWorld());
        assertTrue(PREVIEW.isNonFormationProbe());

        assertTrue(HINT.readsWorld());
        assertTrue(HINT.emitsHints());
        assertFalse(HINT.mutatesWorld());
        assertTrue(HINT.isNonFormationProbe());

        assertTrue(ITERATE.readsWorld());
        assertFalse(ITERATE.mutatesWorld());
        assertTrue(ITERATE.isNonFormationProbe());
    }

    @Test
    void requestKindsMapToOperationPolicy() {
        assertSame(MATCH_WORLD, StructureOperationRequest.Kind.CHECK.getEvaluationOperation());
        assertSame(MATCH_SNAPSHOT, StructureOperationRequest.Kind.SNAPSHOT_CHECK.getEvaluationOperation());
        assertSame(PREVIEW, StructureOperationRequest.Kind.PREVIEW.getEvaluationOperation());
        assertSame(HINT, StructureOperationRequest.Kind.HINT.getEvaluationOperation());
        assertSame(CREATIVE_BUILD, StructureOperationRequest.Kind.CREATIVE_BUILD.getEvaluationOperation());
        assertSame(SURVIVAL_BUILD, StructureOperationRequest.Kind.SURVIVAL_BUILD.getEvaluationOperation());
        assertSame(ITERATE, StructureOperationRequest.Kind.ITERATE.getEvaluationOperation());

        assertTrue(StructureOperationRequest.Kind.CREATIVE_BUILD.getEvaluationOperation().isBuild());
        assertTrue(StructureOperationRequest.Kind.SURVIVAL_BUILD.getEvaluationOperation().isBuild());
        assertFalse(StructureOperationRequest.Kind.CHECK.getEvaluationOperation().isBuild());
    }

    @Test
    void formationOperationCollectorCommitsState() {
        StructureMatchSession session = new StructureMatchSession();
        PatternMatchContext legacyContext = new PatternMatchContext();
        StructureEvaluationContext<Object> context = new StructureEvaluationContext<>();
        context.update(null, session, newWorldState(legacyContext),
                MATCH_WORLD);

        StructureMatchCollector collector = context.getCollector();
        collector.declareCount("count", 0, 1, null, null);
        assertTrue(collector.recordCount("count"));
        assertFalse(collector.recordCount("count"));
        collector.recordChannelValue("channel", 1, true);
        collector.setValue("tier", 4);
        collector.recordVariantActiveBlock(BlockPos.ORIGIN);
        collector.addPart(new TestPart());

        assertEquals(1, collector.getCount("count"));
        assertEquals(Integer.valueOf(1), legacyContext.get("channel"));
        assertEquals(Integer.valueOf(4), legacyContext.get("tier"));
        assertEquals(1, session.getOperationState().getVariantActiveBlocks().size());
        assertEquals(1, session.getOperationState().getParts().size());
    }

    @Test
    void nonFormationOperationCollectorDoesNotCommitState() {
        StructureMatchSession session = new StructureMatchSession();
        PatternMatchContext legacyContext = new PatternMatchContext();
        StructureEvaluationContext<Object> context = new StructureEvaluationContext<>();
        context.update(null, session, newWorldState(legacyContext),
                CREATIVE_BUILD);

        StructureMatchCollector collector = context.getCollector();
        collector.declareCount("count", 0, 1, null, null);
        assertTrue(collector.recordCount("count"));
        assertTrue(collector.recordCount("count"));
        collector.recordChannelValue("channel", 1, true);
        collector.setValue("tier", 4);
        collector.recordVariantActiveBlock(BlockPos.ORIGIN);
        collector.addPart(new TestPart());

        assertEquals(0, collector.getCount("count"));
        assertNull(legacyContext.get("channel"));
        assertNull(legacyContext.get("tier"));
        assertTrue(session.getOperationState().getVariantActiveBlocks().isEmpty());
        assertTrue(session.getOperationState().getParts().isEmpty());
    }

    @Test
    void abilityContributorIsCountedOncePerPartIdentity() {
        MultiblockAbility<Object> ability =
                new MultiblockAbility<>("test_contribution_ability", Object.class);
        StructureMatchSession session = new StructureMatchSession();
        PatternMatchContext legacyContext = new PatternMatchContext();
        StructureEvaluationContext<Object> context = new StructureEvaluationContext<>();
        context.update(null, session, newWorldState(legacyContext), MATCH_WORLD);
        StructureMatchCollector collector = context.getCollector();
        TestPart part = new TestPart();
        collector.declareAbility("ability", ability, 0, 1);

        assertTrue(collector.recordAbility("ability", part));
        assertTrue(collector.recordAbility("ability", part));

        assertEquals(1, collector.getAbilityCount("ability"));
        assertEquals(1, session.getOperationState().getAbilityCounts().get(ability));
        assertEquals(1, session.getOperationState().getParts().size());
    }

    private static BlockWorldState newWorldState(PatternMatchContext legacyContext) {
        BlockWorldState worldState = new BlockWorldState();
        worldState.update(null, BlockPos.ORIGIN, legacyContext,
                new HashMap<>(), new HashMap<>(), TraceabilityPredicate.ANY);
        return worldState;
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
}
