package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.logic.CleanroomRecipeCondition;
import gregtech.api.recipes.logic.IRecipeCondition;
import gregtech.api.recipes.logic.RecipeExecutionContext;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.properties.impl.CleanroomProperty;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerTelemetryIsolationTest {

    private static RecipeMap<SimpleRecipeBuilder> TEST_MAP;
    private static Recipe RECIPE_IRON;
    private static Recipe RECIPE_COAL;
    private static Recipe RECIPE_CLAY_30EUT;
    private static Recipe RECIPE_ZERO_EUT;
    private static Recipe RECIPE_STERILE_REDSTONE;

    @BeforeAll
    static void setUp() {
        MultiblockRecipeLockAndInvalidationTest.bootstrap();
        TEST_MAP = new RecipeMap<>("test_sched_telemetry_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
        RECIPE_IRON = MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                TEST_MAP, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 16L);
        RECIPE_COAL = MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                TEST_MAP, Items.COAL, Items.DIAMOND, 4, 16L);
        RECIPE_CLAY_30EUT = MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                TEST_MAP, Items.CLAY_BALL, Items.BRICK, 4, 30L);
        RECIPE_ZERO_EUT = MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                TEST_MAP, Items.FLINT, Items.GUNPOWDER, 4, 0L);

        RecipePropertyStorage sterileProps = new RecipePropertyStorageImpl();
        sterileProps.store(CleanroomProperty.getInstance(), CleanroomType.STERILE_CLEANROOM);
        RECIPE_STERILE_REDSTONE = new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.REDSTONE, 1))),
                Collections.singletonList(new ItemStack(Items.EMERALD, 1)),
                ChancedOutputList.empty(), Collections.emptyList(), Collections.emptyList(),
                ChancedOutputList.empty(), Collections.emptyList(),
                4, 16L, false, sterileProps, TEST_MAP.getPrimaryRecipeCategory());
        TEST_MAP.compileRecipe(RECIPE_STERILE_REDSTONE);
    }

    @Test
    void refillPowerShortageKeepsWorkingSlotActiveAndReportsActualEUt() {
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setThreadLimit(2);
        logic.setParallelLimit(1);

        MetaTileEntityItemBus inBusA = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_sched_tel_in_a"), 1, false);
        MetaTileEntityItemBus inBusB = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_sched_tel_in_b"), 1, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_sched_tel_out_a"), 2, true);
        ctrl.addPart(inBusA);
        ctrl.addPart(inBusB);
        ctrl.addPart(outBus);
        ctrl.setDistinct(true);

        MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
        energy.changeEnergy(-energy.getEnergyStored() + 16L);

        // Start Slot 0 (16 EU/t) with exactly 16 EU available
        inBusA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        logic.updateWorkable();
        assertTrue(logic.isActive());
        assertTrue(logic.isWorking());
        assertEquals(16L, logic.getInfoProviderEUt());

        // Supply 16 EU/t for Slot 0, while adding a second 16 EU/t candidate in Bus B (needs 32 EU/t total)
        inBusB.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        for (int tick = 0; tick < 3; tick++) {
            energy.changeEnergy(-energy.getEnergyStored() + 16L);
            logic.updateWorkable();
            assertTrue(logic.isWorking(), "Running slot must stay in working state when refill slot lacks power");
            assertFalse(logic.isHasNotEnoughEnergy(), "Candidate refill power miss must not flag low power");
            assertEquals(16L, logic.getInfoProviderEUt());
            assertEquals("", logic.getWhyFailed());
            assertEquals(1, inBusB.getImportItems().getStackInSlot(0).getCount());
        }

        // Final tick of Slot 0 consumes 16 EU; giving another 16 EU allows Slot 1 to start and finish cleanly
        energy.changeEnergy(-energy.getEnergyStored() + 32L);
        logic.updateWorkable();
        assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
        assertEquals(0, inBusB.getImportItems().getStackInSlot(0).getCount());
    }

    @Test
    void refillCandidateRejectionPreservesInFlightPausedSlotWhyFailed() {
        boolean prevClean = ConfigHolder.machines.cleanMultiblocks;
        ConfigHolder.machines.cleanMultiblocks = false;
        try {
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            ctrl.setCleanroom(uncleanSterileProvider());
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setCrossRecipeParallelEnabled(true);
            logic.setParallelLimit(2);

            AtomicBoolean specialFluidPresent = new AtomicBoolean(true);
            logic.addRecipeCondition(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "test:special_fluid";
                }

                @Override
                public boolean testTick(@NotNull RecipeExecutionContext context) {
                    return context.getRecipe() != RECIPE_IRON || specialFluidPresent.get();
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    if (!energyShortage) {
                        logic.setWhyFailed("缺少专用流体");
                    }
                    return TickFailureAction.PAUSE;
                }
            });

            MetaTileEntityItemBus inBusA = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sched_pause_in_a"), 1, false);
            MetaTileEntityItemBus inBusB = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sched_pause_in_b"), 1, false);
            MetaTileEntityItemBus inBusC = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sched_pause_in_c"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sched_pause_out"), 2, true);
            ctrl.addPart(inBusA);
            ctrl.addPart(inBusB);
            ctrl.addPart(inBusC);
            ctrl.addPart(outBus);
            ctrl.setDistinct(true);

            // Start Slot 0 (iron, requires special fluid) and Slot 1 (coal, 1 tick left after 3 ticks)
            inBusA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            logic.updateWorkable();
            assertTrue(logic.isActive());

            // Drain special fluid -> Slot 0 pauses; add Cleanroom-blocked candidate in Bus B
            specialFluidPresent.set(false);
            inBusB.getImportItems().setStackInSlot(0, new ItemStack(Items.REDSTONE, 1));
            logic.updateWorkable();

            assertTrue(logic.isPaused());
            assertFalse(logic.isWorking());
            assertFalse(logic.isHasNotEnoughEnergy());
            assertEquals("缺少专用流体", logic.getWhyFailed(),
                    "Candidate cleanroom rejection during refill must not overwrite paused slot whyFailed");

            // Start working sibling Slot 1 in Bus C; while Slot 1 runs, machine works; when Slot 1 finishes, Slot 0's reason returns
            inBusC.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
            logic.updateWorkable();
            assertTrue(logic.isWorking());
            assertEquals(16L, logic.getInfoProviderEUt());
            for (int t = 0; t < 4; t++) {
                logic.updateWorkable();
            }
            assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
            assertTrue(logic.isPaused());
            assertEquals("缺少专用流体", logic.getWhyFailed());
        } finally {
            ConfigHolder.machines.cleanMultiblocks = prevClean;
        }
    }

    @Test
    void idleSchedulerReportsPowerOrConditionRejectionAndClearsWhenInputsRemoved() {
        boolean prevClean = ConfigHolder.machines.cleanMultiblocks;
        ConfigHolder.machines.cleanMultiblocks = false;
        try {
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            ctrl.setCleanroom(uncleanSterileProvider());
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(2);

            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sched_idle_in"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sched_idle_out"), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);

            MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                    (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
            energy.changeEnergy(-energy.getEnergyStored());

            // 1. Idle start blocked by 0 EU sets whyFailed without flagging active low-power, then auto-starts on power
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            logic.updateWorkable();
            assertFalse(logic.isActive());
            assertFalse(logic.isHasNotEnoughEnergy());
            assertEquals("电力不足，无法满足当前配方的运行需求", logic.getWhyFailed());

            // 2. Replace with Cleanroom-blocked item -> reports cleanroom failure; removing item clears stale whyFailed
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.REDSTONE, 1));
            ctrl.addNotifiedInput(inBus.getImportItems());
            logic.updateWorkable();
            assertEquals(CleanroomRecipeCondition.MISSING_CLEANROOM_REASON, logic.getWhyFailed());

            inBus.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
            ctrl.addNotifiedInput(inBus.getImportItems());
            logic.updateWorkable();
            assertEquals("", logic.getWhyFailed());
        } finally {
            ConfigHolder.machines.cleanMultiblocks = prevClean;
        }
    }

    @Test
    void abortTickDoesNotDropConcurrentDistinctBusInputNotification() {
        boolean prevClean = ConfigHolder.machines.cleanMultiblocks;
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.cleanMultiblocks = false;
        ConfigHolder.machines.resetRecipeOnPowerLoss = true;
        try {
            AtomicBoolean clean = new AtomicBoolean(true);
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            ctrl.setCleanroom(sterileProvider(clean));
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setThreadLimit(2);
            logic.setParallelLimit(1);

            MetaTileEntityItemBus busA = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_abort_notif_a"), 1, false);
            MetaTileEntityItemBus busB = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_abort_notif_b"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_abort_notif_out"), 2, true);
            ctrl.addPart(busA);
            ctrl.addPart(busB);
            ctrl.addPart(outBus);
            ctrl.setDistinct(true);

            // Empty search puts Bus B into invalidatedInputList; then start sterile recipe from Bus A
            logic.updateWorkable();
            busA.getImportItems().setStackInSlot(0, new ItemStack(Items.REDSTONE, 1));
            logic.updateWorkable();
            assertTrue(logic.isActive());

            // Same tick: Cleanroom becomes unclean (aborting Slot A) AND Bus B receives 1 item + notification
            clean.set(false);
            busB.getImportItems().setStackInSlot(0, new ItemStack(Items.REDSTONE, 1));
            assertFalse(ctrl.getNotifiedItemInputList().isEmpty());
            logic.updateWorkable();
            assertEquals(CleanroomRecipeCondition.MISSING_CLEANROOM_REASON, logic.getWhyFailed());

            // Next tick processes Bus B's notification while still unclean; restoring Cleanroom must auto-start Bus B
            logic.updateWorkable();
            clean.set(true);
            for (int t = 0; t < 6; t++) {
                logic.updateWorkable();
            }
            assertEquals(0, busB.getImportItems().getStackInSlot(0).getCount());
            assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
        } finally {
            ConfigHolder.machines.cleanMultiblocks = prevClean;
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void energyShortageOnSiblingSlotPreservesPausedSlotWhyFailed() {
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.resetRecipeOnPowerLoss = false;
        try {
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setCrossRecipeParallelEnabled(true);
            logic.setParallelLimit(2);

            AtomicBoolean specialFluidPresent = new AtomicBoolean(true);
            logic.addRecipeCondition(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "test:sibling_special_fluid";
                }

                @Override
                public boolean testTick(@NotNull RecipeExecutionContext context) {
                    return context.getRecipe() != RECIPE_IRON || specialFluidPresent.get();
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    if (!energyShortage) {
                        logic.setWhyFailed("缺少专用流体");
                    }
                    return TickFailureAction.PAUSE;
                }
            });

            MetaTileEntityItemBus busA = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sib_low_a"), 1, false);
            MetaTileEntityItemBus busB = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sib_low_b"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_sib_low_out"), 2, true);
            ctrl.addPart(busA);
            ctrl.addPart(busB);
            ctrl.addPart(outBus);
            ctrl.setDistinct(true);

            busA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            busB.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
            logic.updateWorkable();
            assertEquals(2, logic.getCrossRecipeScheduler().getActiveSlotCount());

            // Drain special fluid (Slot 0 pauses) AND drain energy to 0 (Slot 1 hits low power)
            specialFluidPresent.set(false);
            MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                    (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
            energy.changeEnergy(-energy.getEnergyStored());

            for (int t = 0; t < 2; t++) {
                logic.updateWorkable();
                assertTrue(logic.isHasNotEnoughEnergy());
                assertEquals(0L, logic.getInfoProviderEUt());
                assertEquals("缺少专用流体", logic.getWhyFailed());
            }
        } finally {
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void alternatingOneTickRecipesReportActualConsumedEUtEachTick() {
        RecipeMap<SimpleRecipeBuilder> oneTickMap = new RecipeMap<>(
                "test_sched_one_tick_eut_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
        MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                oneTickMap, Items.IRON_INGOT, Items.GOLD_INGOT, 1, 8L);
        MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                oneTickMap, Items.COAL, Items.DIAMOND, 1, 32L);

        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(oneTickMap);
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(2);

        MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_1t_alt_in"), 1, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_1t_alt_out"), 2, true);
        ctrl.addPart(inBus);
        ctrl.addPart(outBus);

        MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();

        // Prime first 8 EU/t 1-tick recipe
        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        logic.updateWorkable();
        assertTrue(logic.isWorking());

        // Alternate queuing next 32 EU/t / 8 EU/t item while current 1-tick slot executes and completes
        for (int step = 0; step < 8; step++) {
            boolean expect8EUt = (step % 2 == 0);
            inBus.getImportItems().setStackInSlot(0,
                    new ItemStack(expect8EUt ? Items.COAL : Items.IRON_INGOT, 1));
            long before = energy.getEnergyStored();
            logic.updateWorkable();
            long drawn = before - energy.getEnergyStored();
            long expected = expect8EUt ? 8L : 32L;
            assertEquals(expected, drawn);
            assertEquals(expected, logic.getInfoProviderEUt(),
                    "Displayed EU/t must match the slot executed this tick rather than the newly queued refill slot");
        }
    }

    @Test
    void postAbortConditionOrInputChangeClearsPreservedWhyFailed() {
        boolean prevClean = ConfigHolder.machines.cleanMultiblocks;
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.cleanMultiblocks = false;
        ConfigHolder.machines.resetRecipeOnPowerLoss = true;
        try {
            AtomicBoolean clean = new AtomicBoolean(true);
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            ctrl.setCleanroom(sterileProvider(clean));
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(2);

            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_abort_clear_in"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_abort_clear_out"), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);

            // Abort 1 -> cleared via notifyConditionInputChanged()
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.REDSTONE, 1));
            logic.updateWorkable();
            clean.set(false);
            logic.updateWorkable();
            logic.updateWorkable();
            assertEquals(CleanroomRecipeCondition.MISSING_CLEANROOM_REASON, logic.getWhyFailed());
            clean.set(true);
            logic.notifyConditionInputChanged();
            logic.updateWorkable();
            assertEquals("", logic.getWhyFailed());

            // Abort 2 -> cleared via subsequent non-matching input edit and removal
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.REDSTONE, 1));
            logic.updateWorkable();
            clean.set(false);
            logic.updateWorkable();
            logic.updateWorkable();
            assertEquals(CleanroomRecipeCondition.MISSING_CLEANROOM_REASON, logic.getWhyFailed());
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));
            logic.updateWorkable();
            assertEquals("NoneRecipes", logic.getWhyFailed());
            inBus.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
            logic.updateWorkable();
            assertEquals("", logic.getWhyFailed());
        } finally {
            ConfigHolder.machines.cleanMultiblocks = prevClean;
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void energyShortageAbortToIdleClearsHasNotEnoughEnergy() {
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.resetRecipeOnPowerLoss = true;
        try {
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(2);
            logic.addRecipeCondition(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "test:energy_abort";
                }

                @Override
                public boolean handlesEnergyShortage() {
                    return true;
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    return TickFailureAction.DECREASE_PROGRESS;
                }
            });

            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_energy_abort_in"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_energy_abort_out"), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);

            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            logic.updateWorkable();
            assertTrue(logic.isActive());

            MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                    (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
            energy.changeEnergy(-energy.getEnergyStored());
            logic.updateWorkable();
            assertFalse(logic.isActive());
            assertFalse(logic.isHasNotEnoughEnergy(),
                    "Returning to idle after energy-shortage slot destruction must clear hasNotEnoughEnergy");
        } finally {
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void distinctBudgetExhaustionKeepsValidBusActiveAndSetsSilentConditionFallback() {
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setTotalPowerBudget(128L);
        logic.setParallelLimit(8);
        logic.setThreadLimit(2);

        MetaTileEntityItemBus busA = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dist_budget_a"), 1, false);
        MetaTileEntityItemBus busB = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dist_budget_b"), 1, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dist_budget_out"), 2, true);
        ctrl.addPart(busA);
        ctrl.addPart(busB);
        ctrl.addPart(outBus);
        ctrl.setDistinct(true);

        MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
        energy.changeEnergy(-energy.getEnergyStored());

        // Bus A reserves 7 * 16 = 112 EU/t (leaving 16 EU/t < 30 EU/t for Bus B), Phase 2 fails on 0 stored EU
        busA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
        busB.getImportItems().setStackInSlot(0, new ItemStack(Items.CLAY_BALL, 1));
        logic.updateWorkable();
        IItemHandlerModifiable registeredBusB = ctrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(1);
        assertFalse(logic.invalidatedInputList.contains(registeredBusB),
                "Valid recipe blocked only by remaining base power budget must not invalidate the distinct bus");

        // Removing Bus A and restoring power must run Bus B without touching Bus B
        busA.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
        energy.changeEnergy(512L);
        for (int t = 0; t < 5; t++) {
            logic.updateWorkable();
        }
        assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());

        // Silent IRecipeCondition#canStart rejection populates fallback whyFailed
        logic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "test:silent_reject";
            }

            @Override
            public boolean canStart(@NotNull Recipe recipe, @NotNull IItemHandlerModifiable inputs,
                                    @NotNull IMultipleTankHandler fluids) {
                return false;
            }
        });
        busA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        logic.updateWorkable();
        assertEquals("配方的特殊条件不满足（例如温度，算力等额外条件）", logic.getWhyFailed());
    }

    @Test
    void combinedSchedulerSetsInvalidInputsAndReportsLockedOrNoneRecipes() {
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(2);

        MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_comb_inv_in"), 1, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_comb_inv_out"), 2, true);
        ctrl.addPart(inBus);
        ctrl.addPart(outBus);

        logic.updateWorkable();
        assertTrue(logic.invalidInputsForRecipes);
        assertEquals("", logic.getWhyFailed());

        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));
        logic.updateWorkable();
        assertTrue(logic.invalidInputsForRecipes);
        assertEquals("NoneRecipes", logic.getWhyFailed());

        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        for (int t = 0; t < 5; t++) {
            logic.updateWorkable();
        }
        assertEquals(RECIPE_IRON, logic.getPreviousRecipe());
        logic.setRecipeLockEnable(true);
        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        logic.updateWorkable();
        assertTrue(logic.invalidInputsForRecipes);
        assertEquals("无法运行锁定的配方", logic.getWhyFailed());
    }

    @Test
    void staggeredRefillDoesNotDoubleCountPaidRunningSlotEUt() {
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(2);
        logic.setThreadLimit(2);

        MetaTileEntityItemBus busA = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_stag_in_a"), 1, false);
        MetaTileEntityItemBus busB = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_stag_in_b"), 1, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_stag_out"), 2, true);
        ctrl.addPart(busA);
        ctrl.addPart(busB);
        ctrl.addPart(outBus);
        ctrl.setDistinct(true);

        MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
        energy.changeEnergy(-energy.getEnergyStored() + 32L);

        busA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        logic.updateWorkable();
        assertEquals(1, logic.getCrossRecipeScheduler().getActiveSlotCount());

        // Steady 32 EU/t supply: Slot 0 pays 16 EU in tickSlots, leaving 16 EU to immediately admit staggered Slot 1
        energy.changeEnergy(-energy.getEnergyStored() + 32L);
        busB.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        logic.updateWorkable();
        assertEquals(2, logic.getCrossRecipeScheduler().getActiveSlotCount());
        assertEquals(0, busB.getImportItems().getStackInSlot(0).getCount());
        assertTrue(logic.isWorking());
        assertFalse(logic.isHasNotEnoughEnergy());
    }

    @Test
    void lowEnergyTickSkipsZeroEUtRefillCandidate() {
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.resetRecipeOnPowerLoss = false;
        try {
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(TEST_MAP);
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(2);
            logic.setThreadLimit(2);

            MetaTileEntityItemBus busA = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_low_zero_a"), 1, false);
            MetaTileEntityItemBus busB = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_low_zero_b"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_low_zero_out"), 2, true);
            ctrl.addPart(busA);
            ctrl.addPart(busB);
            ctrl.addPart(outBus);
            ctrl.setDistinct(true);

            busA.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            logic.updateWorkable();
            assertEquals(1, logic.getCrossRecipeScheduler().getActiveSlotCount());

            MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer energy =
                    (MultiblockRecipeLockAndInvalidationTest.TestEnergyContainer) ctrl.getEnergyContainer();
            energy.changeEnergy(-energy.getEnergyStored());
            busB.getImportItems().setStackInSlot(0, new ItemStack(Items.FLINT, 1));
            logic.updateWorkable();

            assertEquals(1, logic.getCrossRecipeScheduler().getActiveSlotCount(),
                    "Refill must be skipped while in-flight slots are frozen by low energy");
            assertTrue(logic.isHasNotEnoughEnergy());
            assertFalse(logic.isWorking());
            assertEquals(1, busB.getImportItems().getStackInSlot(0).getCount());
            assertEquals(RECIPE_CLAY_30EUT.getEUt(), 30L);
            assertEquals(RECIPE_ZERO_EUT.getEUt(), 0L);
        } finally {
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    private static ICleanroomProvider uncleanSterileProvider() {
        return sterileProvider(new AtomicBoolean(false));
    }

    private static ICleanroomProvider sterileProvider(AtomicBoolean cleanFlag) {
        return new ICleanroomProvider() {

            @Override
            public boolean checkCleanroomType(@NotNull CleanroomType type) {
                return type == CleanroomType.STERILE_CLEANROOM;
            }

            @Override
            public void setCleanAmount(int amount) {}

            @Override
            public void adjustCleanAmount(int amount) {}

            @Override
            public boolean isClean() {
                return cleanFlag.get();
            }

            @Override
            public boolean drainEnergy(boolean simulate) {
                return true;
            }

            @Override
            public long getEnergyInputPerSecond() {
                return 0;
            }

            @Override
            public int getEnergyTier() {
                return 1;
            }
        };
    }
}
