package gregtech.api.capability.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.IDistillationTower;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TestMetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.modules.IModuleContainer;
import gregtech.api.modules.IModuleManager;
import gregtech.api.modules.ModuleStage;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
import gregtech.api.recipes.logic.IRecipeCondition;
import gregtech.api.recipes.logic.RecipeExecutionContext;
import gregtech.api.recipes.logic.RecipeSlot;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.registry.MarkerMaterialRegistry;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistillationParallelAndOperationCapTest {

    private static RecipeMap<SimpleRecipeBuilder> DISTILL_MAP;
    private static RecipeMap<SimpleRecipeBuilder> OP_CAP_MAP;
    private static Recipe DISTILL_RECIPE;
    private static Recipe SINGLE_FLUID_RECIPE;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        if (GregTechAPI.mteManager == null) {
            GregTechAPI.mteManager = MTEManager.getInstance();
        }
        if (GregTechAPI.materialManager == null) {
            GregTechAPI.materialManager = MaterialRegistryManager.getInstance();
        }
        if (GregTechAPI.markerMaterialRegistry == null) {
            GregTechAPI.markerMaterialRegistry = MarkerMaterialRegistry.getInstance();
        }
        if (Materials.Stone == null) {
            MaterialRegistryManager managerInternal = (MaterialRegistryManager) GregTechAPI.materialManager;
            managerInternal.unfreezeRegistries();
            Materials.Stone = new Material.Builder(1, new ResourceLocation("gregtech", "stone")).dust().build();
            Materials.Ash = new Material.Builder(2, new ResourceLocation("gregtech", "ash")).dust().build();
        }
        if (GregTechAPI.moduleManager == null) {
            GregTechAPI.moduleManager = new IModuleManager() {

                @Override
                public boolean isModuleEnabled(@NotNull ResourceLocation id) {
                    return false;
                }

                @Override
                public void registerContainer(@NotNull IModuleContainer container) {}

                @Override
                public @Nullable IModuleContainer getLoadedContainer() {
                    return null;
                }

                @Override
                public @NotNull ModuleStage getStage() {
                    return ModuleStage.FINISHED;
                }

                @Override
                public boolean hasPassedStage(@NotNull ModuleStage stage) {
                    return true;
                }
            };
        }

        DISTILL_MAP = new RecipeMap<>("test_distill_map", new SimpleRecipeBuilder(), r -> null, 1, 1, 1, 2);
        DISTILL_RECIPE = new Recipe(
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 500))),
                Arrays.asList(
                        new FluidStack(FluidRegistry.WATER, 200),
                        new FluidStack(FluidRegistry.LAVA, 100)),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                2,
                16L,
                false,
                new RecipePropertyStorageImpl(),
                DISTILL_MAP.getPrimaryRecipeCategory());
        DISTILL_MAP.compileRecipe(DISTILL_RECIPE);

        SINGLE_FLUID_RECIPE = new Recipe(
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.LAVA, 100))),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100)),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                2,
                16L,
                false,
                new RecipePropertyStorageImpl(),
                DISTILL_MAP.getPrimaryRecipeCategory());
        DISTILL_MAP.compileRecipe(SINGLE_FLUID_RECIPE);

        OP_CAP_MAP = new RecipeMap<>("test_op_cap_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);
    }

    @Test
    void distillationTowerLogicHandlerLimitsFluidOutputParallelPerLayerAndGuardsNull() {
        IDistillationTower dummyTower = new IDistillationTower() {

            @Override
            public List<IMultiblockPart> getMultiblockParts() {
                return Collections.emptyList();
            }

            @Override
            public BlockPos getPos() {
                return BlockPos.ORIGIN;
            }

            @Override
            public void invalidateStructure() {}

            @Override
            public boolean allowSameFluidFillForOutputs() {
                return false;
            }
        };

        DistillationTowerLogicHandler uninitialized = new DistillationTowerLogicHandler(dummyTower);
        List<FluidStack> nonEmpty = Arrays.asList(
                new FluidStack(FluidRegistry.WATER, 200),
                new FluidStack(FluidRegistry.LAVA, 100));

        assertFalse(uninitialized.applyFluidToOutputs(nonEmpty, false));
        assertTrue(uninitialized.applyFluidToOutputs(Collections.emptyList(), false));
        assertEquals(0, uninitialized.limitFluidOutputParallel(nonEmpty, 8));
        assertEquals(8, uninitialized.limitFluidOutputParallel(Collections.emptyList(), 8));

        FluidTank layer0 = new FluidTank(1000);
        FluidTank layer1 = new FluidTank(300);
        ConfiguredDistillationHandler configured = new ConfiguredDistillationHandler(dummyTower, layer0, layer1);

        // Layer 0 accepts 1000 / 200 = 5, Layer 1 accepts 300 / 100 = 3 -> bottleneck is 3
        assertEquals(3, configured.limitFluidOutputParallel(nonEmpty, 8));

        // Pre-fill Layer 1 to 250 mB LAVA (50 mB free < 100 mB) -> limit drops to 0
        layer1.fill(new FluidStack(FluidRegistry.LAVA, 250), true);
        assertEquals(0, configured.limitFluidOutputParallel(nonEmpty, 8));
    }

    @Test
    void distillationTowerLogicHandlerRejectsExcessNonEmptyFluidsWhenOrderedTooShort() {
        IDistillationTower dummyTower = new IDistillationTower() {

            @Override
            public List<IMultiblockPart> getMultiblockParts() {
                return Collections.emptyList();
            }

            @Override
            public BlockPos getPos() {
                return BlockPos.ORIGIN;
            }

            @Override
            public void invalidateStructure() {}

            @Override
            public boolean allowSameFluidFillForOutputs() {
                return false;
            }
        };

        List<FluidStack> nonEmpty = Arrays.asList(
                new FluidStack(FluidRegistry.WATER, 200),
                new FluidStack(FluidRegistry.LAVA, 100));

        ConfiguredDistillationHandler emptyOrdered = new ConfiguredDistillationHandler(dummyTower);
        assertFalse(emptyOrdered.applyFluidToOutputs(nonEmpty, false));
        assertTrue(emptyOrdered.applyFluidToOutputs(Collections.emptyList(), false));
        assertEquals(0, emptyOrdered.limitFluidOutputParallel(nonEmpty, 8));

        FluidTank singleLayer = new FluidTank(1000);
        ConfiguredDistillationHandler shortOrdered = new ConfiguredDistillationHandler(dummyTower, singleLayer);
        assertFalse(shortOrdered.applyFluidToOutputs(nonEmpty, false));
        assertFalse(shortOrdered.applyFluidToOutputs(nonEmpty, true));
        assertEquals(0, singleLayer.getFluidAmount());
        assertEquals(0, shortOrdered.limitFluidOutputParallel(nonEmpty, 8));

        List<FluidStack> withTrailingNull = Arrays.asList(new FluidStack(FluidRegistry.WATER, 200), null);
        assertTrue(shortOrdered.applyFluidToOutputs(withTrailingNull, false));
        assertEquals(5, shortOrdered.limitFluidOutputParallel(withTrailingNull, 8));
    }

    @Test
    void singleRecipeBatchProcessingCapsMultiplierByPerLayerFluidsAndItemOutputs() {
        TestDistilleryController ctrl = new TestDistilleryController(DISTILL_MAP);
        TestDistilleryLogic logic = ctrl.getDistilleryLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(1);
        logic.setBatchEnable(true);
        ctrl.setAdvHatchLogic(true);
        ctrl.importTank.fill(new FluidStack(FluidRegistry.WATER, 5000), true);

        assertTrue(logic.prepareRecipe(DISTILL_RECIPE),
                "Single-recipe batching must cap multiplier by bottleneck layer instead of stalling");
        assertEquals(6, logic.getMaxProgress());
        assertEquals(3500, ctrl.importTank.getFluidAmount());

        for (int t = 0; t < 6; t++) {
            logic.updateWorkable();
        }
        assertEquals(600, ctrl.layer0.getFluidAmount());
        assertEquals(FluidRegistry.WATER, ctrl.layer0.getFluid().getFluid());
        assertEquals(300, ctrl.layer1.getFluidAmount());
        assertEquals(FluidRegistry.LAVA, ctrl.layer1.getFluid().getFluid());

        Recipe itemBatchRecipe = new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.EMERALD, 1))),
                Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 32)),
                ChancedOutputList.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                8,
                16L,
                false,
                new RecipePropertyStorageImpl(),
                OP_CAP_MAP.getPrimaryRecipeCategory());
        OP_CAP_MAP.compileRecipe(itemBatchRecipe);

        TestOpCapController itemCtrl = new TestOpCapController(OP_CAP_MAP, 128L, 1024L, false, Integer.MAX_VALUE);
        for (int slot = 1; slot < itemCtrl.outputItems.getSlots(); slot++) {
            itemCtrl.outputItems.setStackInSlot(slot, new ItemStack(Items.APPLE, 64));
        }
        itemCtrl.inputItems.setStackInSlot(0, new ItemStack(Items.EMERALD, 64));

        TestOpCapLogic itemLogic = itemCtrl.getOpLogic();
        itemLogic.setAllowOverclocking(false);
        itemLogic.setParallelLimit(1);
        itemLogic.setBatchEnable(true);

        assertTrue(itemLogic.prepareRecipe(itemBatchRecipe),
                "Single-recipe batching must cap multiplier by free item output space");
        assertEquals(16, itemLogic.getMaxProgress());
        assertEquals(62, itemCtrl.inputItems.getStackInSlot(0).getCount());

        for (int t = 0; t < 16; t++) {
            itemLogic.updateWorkable();
        }
        assertEquals(Items.GOLD_INGOT, itemCtrl.outputItems.getStackInSlot(0).getItem());
        assertEquals(64, itemCtrl.outputItems.getStackInSlot(0).getCount());
    }

    @Test
    void schedulerRoutesFluidOutputsThroughCustomFluidOutputHandlerPerLayer() {
        FluidTank layer0 = new FluidTank(1000);
        FluidTank layer1 = new FluidTank(300);
        layer1.fill(new FluidStack(FluidRegistry.LAVA, 250), true); // only 50 mB free on layer 1

        ConfiguredDistillationHandler handler = new ConfiguredDistillationHandler(new IDistillationTower() {

            @Override
            public List<IMultiblockPart> getMultiblockParts() {
                return Collections.emptyList();
            }

            @Override
            public BlockPos getPos() {
                return BlockPos.ORIGIN;
            }

            @Override
            public void invalidateStructure() {}

            @Override
            public boolean allowSameFluidFillForOutputs() {
                return false;
            }
        }, layer0, layer1);

        CrossRecipeParallelScheduler scheduler = new CrossRecipeParallelScheduler(8);
        RecipeSlot slot = scheduler.acquireSlot();
        assertNotNull(slot);
        List<FluidStack> slotFluids = Arrays.asList(
                new FluidStack(FluidRegistry.WATER, 200),
                new FluidStack(FluidRegistry.LAVA, 100));
        slot.startRecipe(DISTILL_RECIPE, 1, 16L, Collections.emptyList(), slotFluids, 1);

        ItemStackHandler outItems = new ItemStackHandler(4);
        FluidTankList outFluids = new FluidTankList(false, layer0, layer1);
        CrossRecipeParallelScheduler.FluidOutputHandler fluidHandler = (fluids, simulate) -> {
            if (simulate) {
                return handler.applyFluidToOutputs(fluids, false);
            }
            handler.applyFluidToOutputs(fluids, true);
            return true;
        };

        // Tick 1: Layer 1 cannot fit 100 mB LAVA -> slot reaches COMPLETED and stays retained,
        // and Layer 0 MUST receive 0 mB (no partial / cross-layer commit).
        int completed1 = scheduler.tickSlots(outItems, outFluids, (amt, sim) -> true, false, false,
                Collections.emptyList(), fluidHandler);
        assertEquals(0, completed1);
        assertEquals(1, scheduler.getActiveSlotCount());
        assertTrue(slot.isCompleted());
        assertEquals(0, layer0.getFluidAmount());
        assertEquals(250, layer1.getFluidAmount());

        // Clear Layer 1 so all 300 mB are free, tick again -> routes cleanly per layer
        layer1.drain(300, true);
        int completed2 = scheduler.tickSlots(outItems, outFluids, (amt, sim) -> true, false, false,
                Collections.emptyList(), fluidHandler);
        assertEquals(1, completed2);
        assertEquals(0, scheduler.getActiveSlotCount());
        assertEquals(200, layer0.getFluidAmount());
        assertEquals(FluidRegistry.WATER, layer0.getFluid().getFluid());
        assertEquals(100, layer1.getFluidAmount());
        assertEquals(FluidRegistry.LAVA, layer1.getFluid().getFluid());
    }

    @Test
    void distillationMultiblockCapsParallelPerLayerAndFallsBackInNonAdvMode() {
        TestDistilleryController ctrl = new TestDistilleryController(DISTILL_MAP);
        TestDistilleryLogic logic = ctrl.getDistilleryLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(8);

        // 1. advHatchLogic = true: Layer 0 = 1000 mB, Layer 1 = 300 mB -> bottleneck parallel = 3
        ctrl.setAdvHatchLogic(true);
        ctrl.importTank.fill(new FluidStack(FluidRegistry.WATER, 5000), true);

        logic.trySearchNewRecipePublic();
        assertNotNull(logic.getCrossRecipeScheduler());
        assertEquals(1, logic.getCrossRecipeScheduler().getActiveSlotCount());
        assertEquals(3, logic.getCrossRecipeScheduler().getTotalParallelCount(),
                "Layer 1 (300 mB) must cap DISTILL_RECIPE (100 mB/op) to parallel 3 even when parallelLimit=8");

        for (int t = 0; t < 4; t++) {
            logic.updateWorkable();
        }
        assertFalse(logic.getCrossRecipeScheduler().hasActiveSlots());
        assertEquals(600, ctrl.layer0.getFluidAmount());
        assertEquals(FluidRegistry.WATER, ctrl.layer0.getFluid().getFluid());
        assertEquals(300, ctrl.layer1.getFluidAmount());
        assertEquals(FluidRegistry.LAVA, ctrl.layer1.getFluid().getFluid());

        // 2. Switch to non-adv mode + invalidate handler (orderedFluidOutputs == null):
        //    must fall back to standard exportTank without NPE.
        logic.invalidate();
        ctrl.setAdvHatchLogic(false);
        ctrl.handler.invalidate();
        ctrl.importTank.drain(10000, true);
        ctrl.importTank.fill(new FluidStack(FluidRegistry.LAVA, 100), true);

        logic.trySearchNewRecipePublic();
        assertTrue(logic.isActive() || (logic.getCrossRecipeScheduler() != null &&
                logic.getCrossRecipeScheduler().hasActiveSlots()));
        for (int t = 0; t < 4; t++) {
            logic.updateWorkable();
        }
        assertEquals(100, ctrl.standardExportTank.getFluidAmount());
        assertEquals(FluidRegistry.WATER, ctrl.standardExportTank.getFluid().getFluid());
    }

    @Test
    void operationCapBlocksSubTickAndBatchInSingleAndSchedulerAndPersistsSingleTotalOps() {
        Recipe fastRecipe = new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1))),
                Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 1)),
                ChancedOutputList.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                1,
                16L,
                false,
                new RecipePropertyStorageImpl(),
                OP_CAP_MAP.getPrimaryRecipeCategory());
        OP_CAP_MAP.compileRecipe(fastRecipe);

        Recipe mediumRecipe = new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.COAL, 1))),
                Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                ChancedOutputList.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                4,
                16L,
                false,
                new RecipePropertyStorageImpl(),
                OP_CAP_MAP.getPrimaryRecipeCategory());
        OP_CAP_MAP.compileRecipe(mediumRecipe);

        // Case 1: Single-recipe capped (parallelLimit = 1, high voltage + batch enabled, canBatch = false, maxOps = 1)
        AtomicInteger cappedStartOps = new AtomicInteger();
        AtomicInteger cappedCompleteOps = new AtomicInteger();
        TestOpCapController cappedSingleCtrl = new TestOpCapController(OP_CAP_MAP, 32768L, 1_000_000L, true, 1);
        TestOpCapLogic cappedSingleLogic = cappedSingleCtrl.getOpLogic();
        cappedSingleLogic.setAllowOverclocking(true);
        cappedSingleLogic.setBatchEnable(true);
        cappedSingleLogic.setParallelLimit(1);
        cappedSingleLogic.addRecipeCondition(new RecordingCondition("cap_single", cappedStartOps, cappedCompleteOps));
        cappedSingleCtrl.inputItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));

        assertTrue(cappedSingleLogic.prepareRecipe(fastRecipe));
        assertEquals(1, cappedStartOps.get(), "maxOps=1 and canBatch=false must clamp start totalOperations to 1");
        assertEquals(63, cappedSingleCtrl.inputItems.getStackInSlot(0).getCount(), "Must consume strictly 1 input");
        cappedSingleLogic.updateWorkable();
        assertEquals(1, cappedCompleteOps.get());
        assertEquals(1, cappedSingleCtrl.outputItems.getStackInSlot(0).getCount());

        // Case 2: Single-recipe uncapped (sub-tick OC + batch) + NBT round-trip of SingleTotalOperations
        // mediumRecipe: EUt=16, duration=4, maxVoltage=2048 -> 3 OCs (EV): duration=1, subTickParallel=2.
        // Input 16 COAL -> batch 8x -> maxProgressTime=8, totalOperations=16.
        AtomicInteger uncappedStartOps = new AtomicInteger();
        AtomicInteger uncappedCompleteOps = new AtomicInteger();
        TestOpCapController uncappedCtrl = new TestOpCapController(OP_CAP_MAP, 2048L, 1_000_000L, false,
                Integer.MAX_VALUE);
        TestOpCapLogic uncappedLogic = uncappedCtrl.getOpLogic();
        uncappedLogic.setAllowOverclocking(true);
        uncappedLogic.setBatchEnable(true);
        uncappedLogic.setParallelLimit(1);
        uncappedLogic.addRecipeCondition(new RecordingCondition("uncap_single", uncappedStartOps, uncappedCompleteOps));
        uncappedCtrl.inputItems.setStackInSlot(0, new ItemStack(Items.COAL, 16));

        assertTrue(uncappedLogic.prepareRecipe(mediumRecipe));
        assertEquals(16, uncappedStartOps.get(), "Sub-tick 2x * batch 8x must report totalOperations=16 on start");
        assertEquals(8, uncappedLogic.getMaxProgress());

        NBTTagCompound saved = uncappedLogic.serializeNBT();

        AtomicInteger restoredStartOps = new AtomicInteger();
        AtomicInteger restoredCompleteOps = new AtomicInteger();
        TestOpCapController restoredCtrl = new TestOpCapController(OP_CAP_MAP, 2048L, 1_000_000L, false,
                Integer.MAX_VALUE);
        TestOpCapLogic restoredLogic = restoredCtrl.getOpLogic();
        restoredLogic.addRecipeCondition(new RecordingCondition("uncap_single", restoredStartOps, restoredCompleteOps));
        restoredLogic.deserializeNBT(saved);

        for (int t = 0; t < 10; t++) {
            restoredLogic.updateWorkable();
        }
        assertEquals(16, restoredCompleteOps.get(),
                "Persisted SingleTotalOperations must survive NBT round-trip and match start count at completion");
        assertEquals(16, restoredCtrl.outputItems.getStackInSlot(0).getCount());

        // Case 3: Scheduler capped (parallelLimit = 8, high voltage + batch enabled, canBatch = false, maxOps = 1)
        AtomicInteger schedStartOps = new AtomicInteger();
        AtomicInteger schedCompleteOps = new AtomicInteger();
        TestOpCapController schedCtrl = new TestOpCapController(OP_CAP_MAP, 32768L, 262144L, true, 1);
        TestOpCapLogic schedLogic = schedCtrl.getOpLogic();
        schedLogic.setAllowOverclocking(true);
        schedLogic.setBatchEnable(true);
        schedLogic.setParallelLimit(8);
        schedLogic.addRecipeCondition(new RecordingCondition("cap_sched", schedStartOps, schedCompleteOps));
        schedCtrl.inputItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));

        schedLogic.trySearchNewRecipePublic();
        CrossRecipeParallelScheduler scheduler = schedLogic.getCrossRecipeScheduler();
        assertNotNull(scheduler);
        assertEquals(1, scheduler.getActiveSlotCount());
        RecipeSlot slot = scheduler.getActiveSlots().get(0);
        assertEquals(1, slot.getParallelCount());
        assertEquals(1, slot.getTotalOperations());
        assertEquals(1, schedStartOps.get());
        assertEquals(63, schedCtrl.inputItems.getStackInSlot(0).getCount(), "Scheduler cap must consume strictly 1");

        schedLogic.updateWorkable();
        assertEquals(1, schedCompleteOps.get());
        assertEquals(1, schedCtrl.outputItems.getStackInSlot(0).getCount());
    }

    // ==================== Headless Fixtures ====================

    private static final class ConfiguredDistillationHandler extends DistillationTowerLogicHandler {

        ConfiguredDistillationHandler(IDistillationTower tower, FluidTank... layers) {
            super(tower);
            setLayerCount(layers.length);
            List<IFluidHandler> ordered = Arrays.<IFluidHandler>asList(layers);
            setOrderedFluidOutputs(ordered);
            setFluidTanks(new FluidTankList(false, layers));
        }
    }

    private static final class TestDistilleryController extends RecipeMapMultiblockController
            implements IDistillationTower {

        final FluidTank importTank = new FluidTank(10000);
        final FluidTank layer0 = new FluidTank(1000);
        final FluidTank layer1 = new FluidTank(300);
        final FluidTank standardExportTank = new FluidTank(10000);
        final FluidTankList importFluidList = new FluidTankList(false, importTank);
        final FluidTankList standardExportList = new FluidTankList(false, standardExportTank);
        final ConfiguredDistillationHandler handler;
        final ItemStackHandler dummyItems = new ItemStackHandler(4);
        final TestEnergyContainer energy = new TestEnergyContainer(100_000L, 100_000L, 128L);
        private boolean advHatchLogic = true;

        TestDistilleryController(RecipeMap<?> map) {
            super(new ResourceLocation("gregtech", "test_distillery_controller"), map);
            new TestMetaTileEntityHolder(this);
            this.handler = new ConfiguredDistillationHandler(this, layer0, layer1);
            this.recipeMapWorkable = new TestDistilleryLogic(this);
        }

        void setAdvHatchLogic(boolean advHatchLogic) {
            this.advHatchLogic = advHatchLogic;
        }

        TestDistilleryLogic getDistilleryLogic() {
            return (TestDistilleryLogic) recipeMapWorkable;
        }

        @Override
        public boolean allowSameFluidFillForOutputs() {
            return !advHatchLogic;
        }

        @Override
        public int getFluidOutputLimit() {
            return advHatchLogic ? handler.getLayerCount() : super.getFluidOutputLimit();
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isStructureFormed() {
            return true;
        }

        @Override
        public List<IMultiblockPart> getMultiblockParts() {
            return Collections.emptyList();
        }

        @Override
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            return Collections.emptyList();
        }

        @Override
        public IEnergyContainer getEnergyContainer() {
            return energy;
        }

        @Override
        public IItemHandlerModifiable getInputInventory() {
            return dummyItems;
        }

        @Override
        public IItemHandlerModifiable getOutputInventory() {
            return dummyItems;
        }

        @Override
        public IMultipleTankHandler getInputFluidInventory() {
            return importFluidList;
        }

        @Override
        public IMultipleTankHandler getOutputFluidInventory() {
            return standardExportList;
        }

        @Override
        protected StructureDefinition<?> createStructureDefinition() {
            return null;
        }

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity te) {
            return new TestDistilleryController(recipeMap);
        }

        @Override
        public boolean hasMaintenanceMechanics() {
            return false;
        }
    }

    private static final class TestDistilleryLogic extends MultiblockRecipeLogic {

        private final TestDistilleryController controller;

        TestDistilleryLogic(TestDistilleryController controller) {
            super(controller);
            this.controller = controller;
        }

        @Override
        protected boolean checkOutputFluidSpace(@NotNull List<FluidStack> fluids,
                                                @NotNull IMultipleTankHandler exportFluids) {
            return controller.advHatchLogic ?
                    controller.handler.applyFluidToOutputs(fluids, false) :
                    super.checkOutputFluidSpace(fluids, exportFluids);
        }

        @Override
        protected void commitFluidOutputs(@NotNull List<FluidStack> fluids,
                                          @NotNull IMultipleTankHandler exportFluids) {
            if (controller.advHatchLogic) {
                controller.handler.applyFluidToOutputs(fluids, true);
            } else {
                super.commitFluidOutputs(fluids, exportFluids);
            }
        }

        @Override
        protected int limitFluidOutputParallel(@NotNull Recipe recipe,
                                               @NotNull IMultipleTankHandler exportFluids,
                                               int maxParallel,
                                               boolean voidFluids) {
            if (!controller.advHatchLogic) {
                return super.limitFluidOutputParallel(recipe, exportFluids, maxParallel, voidFluids);
            }
            return voidFluids ? maxParallel :
                    controller.handler.limitFluidOutputParallel(recipe.getAllFluidOutputs(), maxParallel);
        }

        @Override
        protected IMultipleTankHandler getOutputTank() {
            return controller.advHatchLogic ? controller.handler.getFluidTanks() : super.getOutputTank();
        }

        @Override
        public long getMaxVoltage() {
            return 128L;
        }

        @Override
        public long getMaximumOverclockVoltage() {
            return 128L;
        }

        @Override
        protected long getMaxParallelVoltage() {
            return 128L;
        }

        @Override
        protected long getTotalPowerBudget() {
            return 1024L;
        }

        @Override
        protected boolean hasEnoughPower(long eut, int duration) {
            return true;
        }

        void trySearchNewRecipePublic() {
            trySearchNewRecipe();
        }
    }

    private static final class TestOpCapController extends RecipeMapMultiblockController {

        final ItemStackHandler inputItems = new ItemStackHandler(4);
        final ItemStackHandler outputItems = new ItemStackHandler(8);
        final FluidTankList emptyFluids = new FluidTankList(false);
        final TestEnergyContainer energy;

        TestOpCapController(RecipeMap<?> map, long voltage, long powerBudget, boolean capOperations, int maxOps) {
            super(new ResourceLocation("gregtech", "test_op_cap_controller"), map);
            new TestMetaTileEntityHolder(this);
            this.energy = new TestEnergyContainer(10_000_000L, 10_000_000L, voltage);
            this.recipeMapWorkable = new TestOpCapLogic(this, voltage, powerBudget, capOperations, maxOps);
        }

        TestOpCapLogic getOpLogic() {
            return (TestOpCapLogic) recipeMapWorkable;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isStructureFormed() {
            return true;
        }

        @Override
        public List<IMultiblockPart> getMultiblockParts() {
            return Collections.emptyList();
        }

        @Override
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            return Collections.emptyList();
        }

        @Override
        public IEnergyContainer getEnergyContainer() {
            return energy;
        }

        @Override
        public IItemHandlerModifiable getInputInventory() {
            return inputItems;
        }

        @Override
        public IItemHandlerModifiable getOutputInventory() {
            return outputItems;
        }

        @Override
        public IMultipleTankHandler getInputFluidInventory() {
            return emptyFluids;
        }

        @Override
        public IMultipleTankHandler getOutputFluidInventory() {
            return emptyFluids;
        }

        @Override
        protected StructureDefinition<?> createStructureDefinition() {
            return null;
        }

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity te) {
            return new TestOpCapController(recipeMap, 128L, 1024L, false, Integer.MAX_VALUE);
        }

        @Override
        public boolean hasMaintenanceMechanics() {
            return false;
        }
    }

    private static final class TestOpCapLogic extends MultiblockRecipeLogic {

        private final long maxVoltage;
        private final long totalPowerBudget;
        private final boolean capOperations;
        private final int maxOps;

        TestOpCapLogic(RecipeMapMultiblockController controller, long maxVoltage, long totalPowerBudget,
                       boolean capOperations, int maxOps) {
            super(controller);
            this.maxVoltage = maxVoltage;
            this.totalPowerBudget = totalPowerBudget;
            this.capOperations = capOperations;
            this.maxOps = maxOps;
        }

        @Override
        protected boolean canBatchRecipe(@NotNull Recipe recipe) {
            return !capOperations && super.canBatchRecipe(recipe);
        }

        @Override
        protected int getMaxRecipeOperations(@NotNull Recipe recipe) {
            return capOperations ? maxOps : super.getMaxRecipeOperations(recipe);
        }

        @Override
        public long getMaxVoltage() {
            return maxVoltage;
        }

        @Override
        public long getMaximumOverclockVoltage() {
            return maxVoltage;
        }

        @Override
        protected long getMaxParallelVoltage() {
            return maxVoltage;
        }

        @Override
        protected long getTotalPowerBudget() {
            return totalPowerBudget;
        }

        @Override
        protected boolean hasEnoughPower(long eut, int duration) {
            return true;
        }

        void trySearchNewRecipePublic() {
            trySearchNewRecipe();
        }
    }

    private static final class RecordingCondition implements IRecipeCondition {

        private final String id;
        private final AtomicInteger startOps;
        private final AtomicInteger completeOps;

        RecordingCondition(String id, AtomicInteger startOps, AtomicInteger completeOps) {
            this.id = id;
            this.startOps = startOps;
            this.completeOps = completeOps;
        }

        @Override
        public @NotNull String getConditionId() {
            return id;
        }

        @Override
        public void onRecipeStart(@NotNull RecipeExecutionContext context) {
            startOps.set(context.getTotalOperations());
        }

        @Override
        public void onRecipeComplete(@NotNull RecipeExecutionContext context) {
            completeOps.set(context.getTotalOperations());
        }
    }

    private static final class TestEnergyContainer implements IEnergyContainer {

        private long stored;
        private final long capacity;
        private final long voltage;

        TestEnergyContainer(long stored, long capacity, long voltage) {
            this.stored = stored;
            this.capacity = capacity;
            this.voltage = voltage;
        }

        @Override
        public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean inputsEnergy(EnumFacing side) {
            return true;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            long prev = stored;
            stored = Math.max(0L, Math.min(capacity, stored + differenceAmount));
            return stored - prev;
        }

        @Override
        public long getEnergyStored() {
            return stored;
        }

        @Override
        public long getEnergyCapacity() {
            return capacity;
        }

        @Override
        public long getInputAmperage() {
            return 8L;
        }

        @Override
        public long getInputVoltage() {
            return voltage;
        }
    }
}
