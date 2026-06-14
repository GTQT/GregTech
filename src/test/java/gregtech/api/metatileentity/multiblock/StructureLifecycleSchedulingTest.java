package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureLifecycleSchedulingTest {

    @Test
    void runtimeLifecycleStateOwnsControllerProjection() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        TestPart part = new TestPart();
        StructureRuntime runtime = controller.runtime;

        runtime.publishLifecycleState(
                Collections.singletonList(part),
                Collections.emptyMap(),
                null,
                new StructureChannelValues(),
                null);
        controller.projectStructureLifecycle(runtime.getLifecycleState());

        assertTrue(runtime.getLifecycleState().isFormed());
        assertTrue(controller.isStructureFormed());
        assertEquals(Collections.singletonList(part), controller.getMultiblockParts());

        controller.invalidateStructure();

        assertFalse(runtime.getLifecycleState().isFormed());
        assertFalse(controller.isStructureFormed());
        assertTrue(controller.getMultiblockParts().isEmpty());
    }

    @Test
    void commitTokenRejectsLifecycleGenerationChange() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        StructureRuntime runtime = controller.runtime;
        StructureCommitToken token = StructureCommitToken.captureForCheck(controller);

        runtime.publishLifecycleState(
                Collections.emptyList(),
                Collections.emptyMap(),
                null,
                new StructureChannelValues(),
                null);
        controller.projectStructureLifecycle(runtime.getLifecycleState());

        assertEquals("lifecycle-generation", token.staleReason());
    }

    @Test
    void pollingOnlyPolicyDoesNotConsumeWorldDirtyStorage() {
        PollingOnlyPolicy policy = new PollingOnlyPolicy();
        TestController controller = testController(policy);
        BareWorld world = bareWorld();
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;
        controller.firstTick = false;
        MultiblockWorldData worldData = MultiblockWorldData.get(world);
        worldData.registerMultiblock(controller, new LongOpenHashSet(new long[] { BlockPos.ORIGIN.toLong() }));
        assertTrue(worldData.onBlockChanged(BlockPos.ORIGIN, 0));

        new MultiblockStructureCheckScheduler().doStructureCheck(controller);

        MultiblockWorldData.DirtyCheckLease lease = worldData.consumeDirtyCheck(controller, 10);
        assertTrue(lease.isRegistered());
        assertTrue(lease.shouldCheck());
        assertEquals(1, controller.checks);
        assertEquals(1, policy.polls);

        worldData.clear();
        MultiblockWorldData.remove(world);
    }

    private static final class PollingOnlyPolicy implements StructureSchedulerPolicy {

        int polls;

        @Override
        public boolean shouldRunFirstTickCheck(@NotNull MultiblockControllerBase controller) {
            return false;
        }

        @Override
        public boolean allowsEventDriven(@NotNull MultiblockControllerBase controller) {
            return false;
        }

        @Override
        public boolean allowsAsync(@NotNull MultiblockControllerBase controller,
                                   @NotNull AsyncStructureChecker checker) {
            return false;
        }

        @Override
        public int pollingInterval(@NotNull MultiblockControllerBase controller) {
            return 1;
        }

        @Override
        public boolean shouldPollingCheck(@NotNull MultiblockControllerBase controller) {
            polls++;
            return true;
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

        private StructureSchedulerPolicy policy;
        private StructureRuntime runtime;
        private World world;
        private BlockPos pos = BlockPos.ORIGIN;
        private boolean firstTick;
        private int checks;

        private TestController(@NotNull StructureSchedulerPolicy policy) {
            super(new ResourceLocation("gregtech", "lifecycle_scheduling_test"));
            this.policy = policy;
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
        public boolean isFirstTick() {
            return firstTick;
        }

        @Override
        public long getOffsetTimer() {
            return 0;
        }

        @Override
        public @NotNull EnumFacing getFrontFacing() {
            return EnumFacing.NORTH;
        }

        @Override
        public String getMetaName() {
            return "gregtech.machine.lifecycle_scheduling_test";
        }

        @NotNull
        @Override
        protected StructureSchedulerPolicy getStructureSchedulerPolicy() {
            return policy;
        }

        @Override
        public void checkStructurePattern() {
            checks++;
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

    @NotNull
    private static TestController testController(@NotNull StructureSchedulerPolicy policy) {
        try {
            TestController controller = (TestController) unsafe().allocateInstance(TestController.class);
            controller.policy = policy;
            controller.runtime = new StructureRuntime(null, null, null, null, null);
            controller.world = null;
            controller.pos = BlockPos.ORIGIN;
            setField(MultiblockControllerBase.class, controller, "multiblockParts", new ArrayList<>());
            setField(MultiblockControllerBase.class, controller, "multiblockAbilities", new HashMap<>());
            setField(MultiblockControllerBase.class, controller, "structureCheckScheduler",
                    new MultiblockStructureCheckScheduler());
            setField(MultiblockControllerBase.class, controller, "structureRuntime", controller.runtime);
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate test controller", e);
        }
    }

    @NotNull
    private static BareWorld bareWorld() {
        try {
            return (BareWorld) unsafe().allocateInstance(BareWorld.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate bare world", e);
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
    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access Unsafe", e);
        }
    }
}
