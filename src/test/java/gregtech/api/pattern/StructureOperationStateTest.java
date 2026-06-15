package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StructureOperationStateTest {

    @Test
    void forkedCollectorStateCommitsTransactionally() {
        Object countKey = new Object();
        TestPart part = new TestPart();
        StructureMatchSession root = new StructureMatchSession();
        StructureMatchCollector rootCollector =
                new StructureMatchCollector(root.getOperationState(), root.getContext());
        rootCollector.declareCount(countKey, 1, 1, null, null);

        StructureMatchSession child = root.fork();
        StructureMatchCollector childCollector =
                new StructureMatchCollector(child.getOperationState(), child.getContext());
        assertTrue(childCollector.recordCount(countKey));
        childCollector.addPart(part);

        assertEquals(0, rootCollector.getCount(countKey));
        assertTrue(root.copyOperationState().getParts().isEmpty());

        child.commit();

        assertEquals(1, rootCollector.getCount(countKey));
        assertEquals(Collections.singleton(part), root.copyOperationState().getParts());
        assertTrue(root.validate(false).success);
    }

    @Test
    void checkpointRestoreRollsBackTypedCollectorState() {
        Object countKey = new Object();
        StructureMatchSession session = new StructureMatchSession();
        StructureMatchCollector collector =
                new StructureMatchCollector(session.getOperationState(), session.getContext());
        collector.declareCount(countKey, 0, 1, null, null);
        StructureMatchSession.Checkpoint checkpoint = session.checkpoint();

        assertTrue(collector.recordCount(countKey));
        assertEquals(1, collector.getCount(countKey));

        session.restore(checkpoint);

        assertEquals(0, collector.getCount(countKey));
    }

    @Test
    void compatibilityViewCombinesTypedAndLegacyCollectorData() {
        TestPart legacyPart = new TestPart();
        TestPart typedPart = new TestPart();
        BlockPos legacyPos = new BlockPos(1, 2, 3);
        BlockPos typedPos = new BlockPos(4, 5, 6);

        PatternMatchContext context = new PatternMatchContext();
        context.getOrCreate(StructureOperationState.MULTIBLOCK_PARTS_KEY, HashSet::new)
                .add(legacyPart);
        context.getOrCreate(StructureOperationState.VARIANT_ACTIVE_BLOCKS_KEY, LinkedList::new)
                .add(legacyPos);

        StructureOperationState direct = new StructureOperationState();
        direct.parts.add(typedPart);
        direct.variantActiveBlocks.add(typedPos);
        StructureOperationState combined = direct.copyIncludingLegacy(context);
        combined.applyCompatibilityView(context);

        Set<IMultiblockPart> parts = context.get(StructureOperationState.MULTIBLOCK_PARTS_KEY);
        assertEquals(new HashSet<>(Arrays.asList(legacyPart, typedPart)), parts);
        assertEquals(
                Arrays.asList(typedPos, legacyPos),
                context.get(StructureOperationState.VARIANT_ACTIVE_BLOCKS_KEY));
    }

    @Test
    void sessionValidationIncludesLegacyCollectorRequirements() {
        Object countKey = new Object();
        StructureMatchSession session = new StructureMatchSession();
        StructureMatchCollector legacyCollector =
                new StructureMatchCollector(session.getContext());
        legacyCollector.declareCount(countKey, 1, 1, null, null);

        assertFalse(session.validate(false).success);

        assertTrue(legacyCollector.recordCount(countKey));
        assertTrue(session.validate(false).success);
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
