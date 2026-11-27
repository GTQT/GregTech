package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
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
    private static Recipe RECIPE_STERILE_REDSTONE;

    @BeforeAll
    static void setUp() {
        MultiblockRecipeLockAndInvalidationTest.bootstrap();
        TEST_MAP = new RecipeMap<>("test_sched_telemetry_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
        RECIPE_IRON = MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                TEST_MAP, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 16L);
        RECIPE_COAL = MultiblockRecipeLockAndInvalidationTest.compileSimpleRecipe(
                TEST_MAP, Items.COAL, Items.DIAMOND, 4, 16L);

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

    private static ICleanroomProvider uncleanSterileProvider() {
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
                return false;
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
