package gregtech.api.pattern;

import gregtech.api.pattern.element.CompiledStructureElement;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureMatchSessionTest {

    private static final StructureSessionKey<List<String>> VALUES =
            StructureSessionKey.copying("test:values", ArrayList::new);

    @Test
    void forkIsolatesMutableTypedValuesUntilCommit() {
        StructureMatchSession root = new StructureMatchSession();
        root.set(VALUES, new ArrayList<>(Collections.singletonList("root")));

        StructureMatchSession branch = root.fork();
        branch.get(VALUES).add("branch");

        assertEquals(Collections.singletonList("root"), root.get(VALUES));
        branch.commit();
        assertEquals(Arrays.asList("root", "branch"), root.get(VALUES));
    }

    @Test
    void checkpointRestoresTypedValues() {
        StructureMatchSession session = new StructureMatchSession();
        session.set(VALUES, new ArrayList<>(Collections.singletonList("before")));
        StructureMatchSession.Checkpoint checkpoint = session.checkpoint();

        session.get(VALUES).add("after");
        session.restoreTo(checkpoint);

        assertEquals(Collections.singletonList("before"), session.get(VALUES));
    }

    @Test
    void controllerContextIsInheritedByForks() {
        StructureMatchSession session = new StructureMatchSession();
        Object controller = new Object();
        session.setControllerContext(controller);

        StructureMatchSession branch = session.fork();

        assertSame(controller, branch.getControllerContext(Object.class));
        assertNull(branch.getControllerContext(String.class));
    }

    @Test
    void compiledElementPreservesTypedCheckOverride() {
        IStructureElement<String> source = new IStructureElement<String>() {

            @Override
            public boolean check(StructureEvaluationContext<String> context) {
                return "controller".equals(context.getController());
            }

            @Override
            public boolean check(World world, BlockPos pos, PatternMatchContext context) {
                return false;
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
                return new TraceabilityPredicate();
            }
        };
        StructureEvaluationContext<String> context = new StructureEvaluationContext<>();
        context.update("controller", null, new BlockWorldState(),
                StructureEvaluationContext.Operation.MATCH_WORLD);

        CompiledStructureElement<String> compiled = source.compile();

        assertTrue(compiled.check(context));
    }
}
