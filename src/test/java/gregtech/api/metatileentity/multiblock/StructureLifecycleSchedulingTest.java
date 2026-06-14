package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.StructureCheckResult;
import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureExternalDependencies;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void externalDependencySnapshotEnqueuesDirtyRootsForScheduler() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        BareWorld world = bareWorld();
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;
        controller.firstTick = false;
        controller.modeValue = "before";
        StructurePiece root = new StructurePiece(
                "root",
                template(new DependentElement(StructureExternalDependencies.controllerMode())),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null);
        StructurePiece clean = new StructurePiece(
                "clean", template(new MatchingElement()), new Vec3i(0, 0, 1),
                OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Arrays.asList(root, clean));
        controller.runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));
        setField(MultiblockControllerBase.class, controller, "structureRuntime", controller.runtime);

        StructureCheckResult result = controller.runtime.check(StructureOperationRequest.check(
                world, BlockPos.ORIGIN,
                StructureOrientation.fromController(controller),
                false, null, controller));
        assertTrue(result.isMatched());
        assertNotNull(result.getGraphPublication());
        controller.runtime.publishLifecycleState(
                Collections.emptyList(), Collections.emptyMap(), result.getMetadata(),
                result.copyChannelValues(), result.getGraphPublication());
        controller.projectStructureLifecycle(controller.runtime.getLifecycleState());
        MultiblockWorldData.get(world).registerMultiblock(
                controller, result.getGraphPublication().getPositionIndex(), pattern);

        controller.modeValue = "after";
        assertTrue(controller.enqueueChangedStructureExternalDependencies());
        MultiblockWorldData.DirtyCheckLease lease =
                MultiblockWorldData.get(world).consumeDirtyCheck(controller, 10);

        assertTrue(lease.shouldCheckIncremental());
        MultiblockWorldData.get(world).clear();
        MultiblockWorldData.remove(world);
    }

    @Test
    void configDependencySnapshotEnqueuesDirtyRootsForScheduler() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        BareWorld world = bareWorld();
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;
        controller.firstTick = false;
        controller.configValue = "before";
        StructurePiece root = new StructurePiece(
                "root",
                template(new DependentElement(StructureExternalDependencies.configuration())),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null);
        StructurePiece clean = new StructurePiece(
                "clean", template(new MatchingElement()), new Vec3i(0, 0, 1),
                OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Arrays.asList(root, clean));
        controller.runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));
        setField(MultiblockControllerBase.class, controller, "structureRuntime", controller.runtime);

        StructureCheckResult result = controller.runtime.check(StructureOperationRequest.check(
                world, BlockPos.ORIGIN,
                StructureOrientation.fromController(controller),
                false, null, controller));
        assertTrue(result.isMatched());
        assertNotNull(result.getGraphPublication());
        controller.runtime.publishLifecycleState(
                Collections.emptyList(), Collections.emptyMap(), result.getMetadata(),
                result.copyChannelValues(), result.getGraphPublication());
        controller.projectStructureLifecycle(controller.runtime.getLifecycleState());
        MultiblockWorldData.get(world).registerMultiblock(
                controller, result.getGraphPublication().getPositionIndex(), pattern);

        controller.configValue = "after";
        controller.fireConfigChanged();
        MultiblockWorldData.DirtyCheckLease lease =
                MultiblockWorldData.get(world).consumeDirtyCheck(controller, 10);

        assertTrue(lease.shouldCheckIncremental());
        MultiblockWorldData.get(world).clear();
        MultiblockWorldData.remove(world);
    }

    @Test
    void upgradeDependencySnapshotEnqueuesDirtyRootsForScheduler() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        BareWorld world = bareWorld();
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;
        controller.firstTick = false;
        controller.upgradeValue = "before";
        StructurePiece root = new StructurePiece(
                "root",
                template(new DependentElement(StructureExternalDependencies.upgrades())),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null);
        StructurePiece clean = new StructurePiece(
                "clean", template(new MatchingElement()), new Vec3i(0, 0, 1),
                OffsetMode.RELATIVE, null);
        MultiPiecePattern pattern = new MultiPiecePattern(Arrays.asList(root, clean));
        controller.runtime = new StructureRuntime(
                StructureDefinition.fromMultiPiecePattern(pattern),
                null, null, pattern, new PieceRuntimes(pattern));
        setField(MultiblockControllerBase.class, controller, "structureRuntime", controller.runtime);

        StructureCheckResult result = controller.runtime.check(StructureOperationRequest.check(
                world, BlockPos.ORIGIN,
                StructureOrientation.fromController(controller),
                false, null, controller));
        assertTrue(result.isMatched());
        assertNotNull(result.getGraphPublication());
        controller.runtime.publishLifecycleState(
                Collections.emptyList(), Collections.emptyMap(), result.getMetadata(),
                result.copyChannelValues(), result.getGraphPublication());
        controller.projectStructureLifecycle(controller.runtime.getLifecycleState());
        MultiblockWorldData.get(world).registerMultiblock(
                controller, result.getGraphPublication().getPositionIndex(), pattern);

        controller.upgradeValue = "after";
        controller.fireUpgradesChanged();
        MultiblockWorldData.DirtyCheckLease lease =
                MultiblockWorldData.get(world).consumeDirtyCheck(controller, 10);

        assertTrue(lease.shouldCheckIncremental());
        MultiblockWorldData.get(world).clear();
        MultiblockWorldData.remove(world);
    }

    @Test
    void defaultAsyncPolicyRejectsFormedControllersForDirtyPrecheck() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        controller.world = bareWorld();
        controller.pos = BlockPos.ORIGIN;
        controller.firstTick = false;
        controller.runtime.publishLifecycleState(
                Collections.emptyList(),
                Collections.emptyMap(),
                null,
                new StructureChannelValues(),
                null);
        controller.projectStructureLifecycle(controller.runtime.getLifecycleState());

        assertFalse(StructureSchedulerPolicy.defaultPolicy()
                .allowsAsync(controller, AsyncStructureChecker.getInstance()));
        MultiblockWorldData.remove(controller.world);
    }

    @Test
    void asyncDirtyPrecheckTokenRejectsAlreadyFormedControllers() {
        TestController controller = testController(StructureSchedulerPolicy.defaultPolicy());
        controller.world = bareWorld();
        controller.pos = BlockPos.ORIGIN;
        controller.firstTick = false;
        controller.runtime.publishLifecycleState(
                Collections.emptyList(),
                Collections.emptyMap(),
                null,
                new StructureChannelValues(),
                null);
        controller.projectStructureLifecycle(controller.runtime.getLifecycleState());

        StructureCommitToken token = StructureCommitToken.captureForAsyncPrecheck(controller, null);

        assertEquals("already-formed", token.staleReason());
        MultiblockWorldData.remove(controller.world);
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
        private String modeValue;
        private String configValue;
        private String upgradeValue;

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

        @Override
        protected Object getStructureControllerModeValue() {
            return modeValue;
        }

        @Override
        protected Object getStructureConfigDependencyValue() {
            return configValue;
        }

        @Override
        protected Object getStructureUpgradeDependencyValue() {
            return upgradeValue;
        }

        private void fireConfigChanged() {
            notifyStructureConfigChanged();
        }

        private void fireUpgradesChanged() {
            notifyStructureUpgradesChanged();
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

        @Override
        public long getTotalWorldTime() {
            return 0L;
        }
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

    private static class MatchingElement implements IStructureElement<Object> {

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
            this.dependencies = Collections.unmodifiableSet(
                    new java.util.LinkedHashSet<>(Arrays.asList(dependencies)));
        }

        @NotNull
        @Override
        public Set<StructureDependency> getDependencies() {
            return dependencies;
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
