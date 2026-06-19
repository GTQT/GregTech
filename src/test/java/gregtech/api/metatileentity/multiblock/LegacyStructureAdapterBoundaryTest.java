package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureCheckResult;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyStructureAdapterBoundaryTest {

    private static World world;
    private static final AtomicInteger LEGACY_PREDICATE_CALLS = new AtomicInteger();

    @BeforeAll
    static void bootstrapMinecraft() {
        if (!Bootstrap.isRegistered()) {
            Bootstrap.register();
        }
        world = bareWorld();
    }

    @Test
    void gtqtStyleTemplatePredicateAndCallbackAdaptToTypedRuntime() {
        LEGACY_PREDICATE_CALLS.set(0);
        TemplateLegacyController controller = legacyController(TemplateLegacyController.class,
                "legacy_template_fixture", "template-fixture");
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;

        controller.reinitializeStructurePattern();
        controller.checkStructurePattern();

        assertTrue(controller.isStructureFormed());
        assertEquals(1, controller.legacyCallbackCount);
        assertNotNull(controller.callbackContext);
        assertEquals(1, LEGACY_PREDICATE_CALLS.get());
        assertRuntimeAdapterTrace(controller, "source=createStructureTemplate, pieces=1");
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedPatternOverrideAlsoAdaptsBeforeRuntimeOwnership() {
        LEGACY_PREDICATE_CALLS.set(0);
        PatternLegacyController controller = legacyController(PatternLegacyController.class,
                "legacy_pattern_fixture", "pattern-fixture");
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;

        controller.reinitializeStructurePattern();
        controller.checkStructurePattern();

        assertTrue(controller.isStructureFormed());
        assertEquals(1, controller.legacyCallbackCount);
        assertNotNull(controller.callbackContext);
        assertEquals(1, LEGACY_PREDICATE_CALLS.get());
        assertRuntimeAdapterTrace(controller, "source=createStructureTemplate, pieces=1");
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedProjectionMutationDoesNotChangeCanonicalLifecycle() {
        TemplateLegacyController controller = legacyController(TemplateLegacyController.class,
                "legacy_template_fixture", "template-fixture");
        controller.world = world;
        controller.pos = BlockPos.ORIGIN;
        controller.reinitializeStructurePattern();
        controller.checkStructurePattern();

        assertTrue(controller.isStructureFormed());
        assertNotNull(controller.structurePattern);
        assertNotNull(controller.getMultiblockState());
        assertNotNull(controller.runtimeState);
        int originalRepetition = controller.runtimeState.formedRepetitionCount[0];

        controller.structurePattern.clearCache();
        controller.getMultiblockState().formedRepetitionCount[0] = 99;
        controller.getMultiblockState().clearCache();

        assertTrue(controller.isStructureFormed());
        assertTrue(controller.getStructureRuntime().getLifecycleState().isFormed());
        assertEquals(originalRepetition, controller.runtimeState.formedRepetitionCount[0]);
    }

    private static void assertRuntimeAdapterTrace(@NotNull LegacyController controller,
                                                 @NotNull String expected) {
        StructureRuntime runtime = controller.getStructureRuntime();
        assertNotNull(runtime);
        assertEquals(expected, runtime.getAdapterTrace(), () -> adapterTraceDebug(controller, runtime));
        assertTrue(runtime.describeShape().contains("adapterTrace={" + expected + "}"));
        StructureCheckResult result = runtime.check(StructureOperationRequest.check(
                controller.getWorld(), controller.getPos(), StructureOrientation.fromController(controller),
                false, null, controller));
        assertEquals(expected, result.getDiagnostics().getAdapterTrace());
    }

    private static String adapterTraceDebug(@NotNull LegacyController controller,
                                            @NotNull StructureRuntime runtime) {
        return "shape=" + runtime.describeShape()
                + ", traceSource=" + getField(MultiblockControllerBase.class, controller,
                        "structureAdapterTraceSource")
                + ", definition=" + controller.getStructureDefinition()
                + ", pattern=" + controller.multiPiecePattern;
    }

    private static final class TemplateLegacyController extends LegacyController {

        @NotNull
        @Override
        protected BlockPatternTemplate createStructureTemplate() {
            return buildTemplate();
        }
    }

    @SuppressWarnings("deprecation")
    private static final class PatternLegacyController extends LegacyController {

        @NotNull
        @Override
        protected BlockPattern createStructurePattern() {
            return FactoryBlockPattern.start()
                    .aisle("S")
                    .where('S', new FixturePredicate(fixtureValue).setCenter())
                    .build();
        }
    }

    private abstract static class LegacyController extends MultiblockControllerBase {

        String id;
        protected String fixtureValue;
        World world;
        BlockPos pos = BlockPos.ORIGIN;
        int legacyCallbackCount;
        PatternMatchContext callbackContext;

        protected LegacyController() {
            super(new ResourceLocation("gregtech", "unused_constructor"));
        }

        @NotNull
        protected BlockPatternTemplate buildTemplate() {
            return FactoryBlockPattern.start()
                    .aisle("S")
                    .where('S', new FixturePredicate(fixtureValue).setCenter())
                    .buildTemplate();
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
        public long getOffsetTimer() {
            return 0;
        }

        @Override
        public @NotNull EnumFacing getFrontFacing() {
            return EnumFacing.NORTH;
        }

        @Override
        public String getMetaName() {
            return "gregtech.machine." + id;
        }

        @Override
        protected void formStructure(PatternMatchContext context) {
            legacyCallbackCount++;
            callbackContext = context.copy();
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }
    }

    @NotNull
    private static <T extends LegacyController> T legacyController(
            @NotNull Class<T> type,
            @NotNull String id,
            @NotNull String fixtureValue) {
        try {
            T controller = (T) unsafe().allocateInstance(type);
            controller.id = id;
            controller.fixtureValue = fixtureValue;
            setField(MetaTileEntity.class, controller, "metaTileEntityId",
                    new ResourceLocation("gregtech", id));
            setField(MultiblockControllerBase.class, controller, "multiblockParts", new ArrayList<>());
            setField(MultiblockControllerBase.class, controller, "multiblockAbilities", new HashMap<>());
            setField(MultiblockControllerBase.class, controller, "structureCheckScheduler",
                    new MultiblockStructureCheckScheduler());
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate legacy controller fixture", e);
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

    private static Object getField(@NotNull Class<?> owner,
                                   @NotNull Object target,
                                   @NotNull String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to get field " + name, e);
        }
    }

    private static final class FixturePredicate extends TraceabilityPredicate {

        private FixturePredicate(@NotNull String fixtureValue) {
            super(new FixturePredicateBody(fixtureValue), () -> new BlockInfo[] {
                    new BlockInfo(Blocks.STONE.getDefaultState(), null)
            });
        }
    }

    private static final class FixturePredicateBody implements Predicate<BlockWorldState> {

        private final String fixtureValue;

        private FixturePredicateBody(@NotNull String fixtureValue) {
            this.fixtureValue = fixtureValue;
        }

        @Override
        public boolean test(BlockWorldState state) {
            LEGACY_PREDICATE_CALLS.incrementAndGet();
            state.getMatchContext().set("fixture", fixtureValue);
            Integer calls = state.getMatchContext().getOrPut("legacy-predicate-calls", 0);
            state.getMatchContext().set("legacy-predicate-calls", calls + 1);
            return true;
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

    private static final class BareWorld extends World {

        private BareWorld() {
            super(null, null, null, null, false);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return Blocks.STONE.getDefaultState();
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockPos getSpawnPoint() {
            return BlockPos.ORIGIN;
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
