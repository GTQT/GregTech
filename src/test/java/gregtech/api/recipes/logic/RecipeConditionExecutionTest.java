package gregtech.api.recipes.logic;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.modules.IModuleContainer;
import gregtech.api.modules.IModuleManager;
import gregtech.api.modules.ModuleStage;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.common.ConfigHolder;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeConditionExecutionTest {

    private static GTRecipeCategory TEST_CATEGORY;
    private static RecipeMap<SimpleRecipeBuilder> TEST_RECIPE_MAP;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        if (GregTechAPI.mteManager == null) {
            GregTechAPI.mteManager = MTEManager.getInstance();
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
        TEST_RECIPE_MAP = new RecipeMap<>("test_condition_exec_map", new SimpleRecipeBuilder(),
                r -> null, 2, 2, 2, 2);
        TEST_CATEGORY = TEST_RECIPE_MAP.getPrimaryRecipeCategory();
    }

    private static Recipe createTestRecipe(ItemStack input, ItemStack output, int duration, long eut) {
        return new Recipe(
                Collections.singletonList(new GTRecipeItemInput(input)),
                Collections.singletonList(output),
                ChancedOutputList.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                duration,
                eut,
                false,
                new RecipePropertyStorageImpl(),
                TEST_CATEGORY);
    }

    // ==================== Single-Recipe AbstractRecipeLogic Tests ====================

    @Test
    void singleRecipe_twoPhaseSimulateAndCommitAndComplete() {
        TestMetaTileEntity mte = new TestMetaTileEntity(4);
        mte.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        TestEnergyContainer energy = new TestEnergyContainer(1000L, 1000L, 128L);
        TestSingleRecipeLogic logic = new TestSingleRecipeLogic(mte, energy);

        List<String> events = new ArrayList<>();
        AtomicInteger ticksConsumed = new AtomicInteger();

        logic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "test_cond";
            }

            @Override
            public boolean canStart(@NotNull Recipe recipe, @NotNull IItemHandlerModifiable importInventory,
                                    @NotNull IMultipleTankHandler importFluids) {
                events.add("canStart");
                return true;
            }

            @Override
            public void onRecipeStart(@NotNull RecipeExecutionContext context) {
                events.add("onRecipeStart");
                context.getConditionData(getConditionId()).setString("tag", "started");
                // Verify output list is mutable on start
                context.getItemOutputs().add(new ItemStack(Items.DIAMOND, 1));
            }

            @Override
            public boolean testTick(@NotNull RecipeExecutionContext context) {
                events.add("testTick:" + context.getProgressTime());
                assertEquals("started", context.getConditionData(getConditionId()).getString("tag"));
                return true;
            }

            @Override
            public void consumeTick(@NotNull RecipeExecutionContext context) {
                events.add("consumeTick:" + context.getProgressTime());
                ticksConsumed.incrementAndGet();
            }

            @Override
            public void onRecipeComplete(@NotNull RecipeExecutionContext context) {
                events.add("onRecipeComplete");
                assertEquals("started", context.getConditionData(getConditionId()).getString("tag"));
            }
        });

        Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.GOLD_INGOT, 1), 3, 10L);
        assertTrue(logic.prepareRecipe(recipe));
        assertEquals(1, logic.getProgress());
        assertEquals(3, logic.getMaxProgress());

        // Tick 1: progress 1 -> 2
        logic.tickProgress();
        assertEquals(2, logic.getProgress());
        assertEquals(990L, energy.getEnergyStored());

        // Tick 2: progress 2 -> 3
        logic.tickProgress();
        assertEquals(3, logic.getProgress());
        assertEquals(980L, energy.getEnergyStored());

        // Tick 3: progress 3 -> 4 > maxProgressTime(3) -> completeRecipe()
        logic.tickProgress();
        assertEquals(0, logic.getProgress());
        assertEquals(970L, energy.getEnergyStored());
        assertEquals(3, ticksConsumed.get());

        // Verify original output + bonus output added in onRecipeStart were exported
        assertEquals(1, mte.getExportItems().getStackInSlot(0).getCount());
        assertEquals(Items.GOLD_INGOT, mte.getExportItems().getStackInSlot(0).getItem());
        assertEquals(1, mte.getExportItems().getStackInSlot(1).getCount());
        assertEquals(Items.DIAMOND, mte.getExportItems().getStackInSlot(1).getItem());

        assertTrue(events.contains("canStart"));
        assertTrue(events.contains("onRecipeStart"));
        assertTrue(events.contains("onRecipeComplete"));
        // Verify simulate runs strictly before commit
        assertTrue(events.indexOf("testTick:1") < events.indexOf("consumeTick:1"));
        assertTrue(events.indexOf("consumeTick:3") < events.indexOf("onRecipeComplete"));
    }

    @Test
    void singleRecipe_pausePreventsEnergyAndResourceLeak() {
        TestMetaTileEntity mte = new TestMetaTileEntity(4);
        mte.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        TestEnergyContainer energy = new TestEnergyContainer(500L, 1000L, 128L);
        TestSingleRecipeLogic logic = new TestSingleRecipeLogic(mte, energy);

        AtomicBoolean allowCond1 = new AtomicBoolean(true);
        AtomicBoolean allowCond2 = new AtomicBoolean(false);
        AtomicInteger cond1Consumed = new AtomicInteger();
        AtomicInteger cond2FailedCalls = new AtomicInteger();

        // Condition 1 always passes testTick; must NOT have consumeTick called when condition 2 fails!
        logic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "cond1";
            }

            @Override
            public boolean testTick(@NotNull RecipeExecutionContext context) {
                return allowCond1.get();
            }

            @Override
            public void consumeTick(@NotNull RecipeExecutionContext context) {
                cond1Consumed.incrementAndGet();
            }
        });

        // Condition 2 fails with PAUSE
        logic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "cond2";
            }

            @Override
            public boolean testTick(@NotNull RecipeExecutionContext context) {
                return allowCond2.get();
            }

            @Override
            public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                           boolean energyShortage) {
                assertFalse(energyShortage);
                cond2FailedCalls.incrementAndGet();
                return TickFailureAction.PAUSE;
            }
        });

        Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.GOLD_INGOT, 1), 4, 25L);
        assertTrue(logic.prepareRecipe(recipe));
        assertEquals(1, logic.getProgress());

        // Fail tick -> PAUSE: progress remains 1, no energy drawn, cond1.consumeTick not called
        logic.tickProgress();
        assertEquals(1, logic.getProgress());
        assertEquals(500L, energy.getEnergyStored());
        assertEquals(0, cond1Consumed.get());
        assertEquals(1, cond2FailedCalls.get());
        assertTrue(logic.isActive());

        // Now allow cond2 -> advances normally
        allowCond2.set(true);
        logic.tickProgress();
        assertEquals(2, logic.getProgress());
        assertEquals(475L, energy.getEnergyStored());
        assertEquals(1, cond1Consumed.get());
    }

    @Test
    void singleRecipe_decreaseProgressRespectsResetRecipeOnPowerLoss() {
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        try {
            // Case 1: resetRecipeOnPowerLoss = false -> progressTime resets to 0 without onInvalidate
            ConfigHolder.machines.resetRecipeOnPowerLoss = false;
            TestMetaTileEntity mte1 = new TestMetaTileEntity(4);
            mte1.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            TestEnergyContainer energy1 = new TestEnergyContainer(500L, 1000L, 128L);
            TestSingleRecipeLogic logic1 = new TestSingleRecipeLogic(mte1, energy1);

            AtomicBoolean pass1 = new AtomicBoolean(true);
            AtomicInteger invalidates1 = new AtomicInteger();
            logic1.addRecipeCondition(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "dec_cond";
                }

                @Override
                public boolean testTick(@NotNull RecipeExecutionContext context) {
                    return pass1.get();
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    return TickFailureAction.DECREASE_PROGRESS;
                }

                @Override
                public void onInvalidate(@Nullable RecipeExecutionContext context) {
                    invalidates1.incrementAndGet();
                }
            });

            Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.GOLD_INGOT, 1), 5,
                    10L);
            assertTrue(logic1.prepareRecipe(recipe));
            logic1.tickProgress();
            assertEquals(2, logic1.getProgress());

            pass1.set(false);
            logic1.tickProgress();
            assertEquals(0, logic1.getProgress());
            assertEquals(0, invalidates1.get());
            assertEquals(490L, energy1.getEnergyStored());

            // Case 2: resetRecipeOnPowerLoss = true -> invalidates recipe and invokes onInvalidate
            ConfigHolder.machines.resetRecipeOnPowerLoss = true;
            TestMetaTileEntity mte2 = new TestMetaTileEntity(4);
            mte2.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            TestEnergyContainer energy2 = new TestEnergyContainer(500L, 1000L, 128L);
            TestSingleRecipeLogic logic2 = new TestSingleRecipeLogic(mte2, energy2);

            AtomicBoolean pass2 = new AtomicBoolean(true);
            AtomicInteger invalidates2 = new AtomicInteger();
            logic2.addRecipeCondition(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "dec_cond_reset";
                }

                @Override
                public boolean testTick(@NotNull RecipeExecutionContext context) {
                    return pass2.get();
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    return TickFailureAction.DECREASE_PROGRESS;
                }

                @Override
                public void onInvalidate(@Nullable RecipeExecutionContext context) {
                    assertNotNull(context);
                    invalidates2.incrementAndGet();
                }
            });

            assertTrue(logic2.prepareRecipe(recipe));
            logic2.tickProgress();
            assertEquals(2, logic2.getProgress());

            pass2.set(false);
            logic2.tickProgress();
            assertEquals(0, logic2.getProgress());
            assertFalse(logic2.isActive());
            assertEquals(1, invalidates2.get());
        } finally {
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void singleRecipe_extraDataNbtRoundTripAndCanStartBlock() {
        TestMetaTileEntity mte = new TestMetaTileEntity(4);
        mte.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        TestEnergyContainer energy = new TestEnergyContainer(500L, 1000L, 128L);
        TestSingleRecipeLogic logic = new TestSingleRecipeLogic(mte, energy);

        AtomicBoolean allowStart = new AtomicBoolean(false);
        logic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "persist_cond";
            }

            @Override
            public boolean canStart(@NotNull Recipe recipe, @NotNull IItemHandlerModifiable importInventory,
                                    @NotNull IMultipleTankHandler importFluids) {
                return allowStart.get();
            }

            @Override
            public void onRecipeStart(@NotNull RecipeExecutionContext context) {
                context.getConditionData(getConditionId()).setInteger("magicCost", 42);
            }
        });

        Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.GOLD_INGOT, 1), 4, 10L);
        assertFalse(logic.prepareRecipe(recipe));

        allowStart.set(true);
        assertTrue(logic.prepareRecipe(recipe));
        logic.tickProgress();
        assertEquals(2, logic.getProgress());

        NBTTagCompound saved = logic.serializeNBT();

        TestMetaTileEntity restoredMte = new TestMetaTileEntity(4);
        TestSingleRecipeLogic restored = new TestSingleRecipeLogic(restoredMte, energy);
        restored.deserializeNBT(saved);
        assertEquals(2, restored.getProgress());
        assertEquals(4, restored.getMaxProgress());
        assertEquals(42, restored.getSingleExtraData().getCompoundTag("persist_cond").getInteger("magicCost"));
    }

    // ==================== Multi-Slot CrossRecipeParallelScheduler Tests ====================

    @Test
    void scheduler_pauseIsolatesFailingSlotWhileSiblingProgresses() {
        CrossRecipeParallelScheduler scheduler = new CrossRecipeParallelScheduler(16);
        scheduler.setMaxVoltage(128L);
        scheduler.setTotalPowerBudget(1000L);

        ItemStackHandler outItems = new ItemStackHandler(4);
        FluidTankList outFluids = new FluidTankList(false, new FluidTank(10000));

        Recipe recipeA = createTestRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), 2, 20L);
        Recipe recipeB = createTestRecipe(new ItemStack(Items.COAL), new ItemStack(Items.DIAMOND), 2, 30L);

        RecipeSlot slotA = scheduler.acquireSlot();
        assertNotNull(slotA);
        slotA.startRecipe(recipeA, 2, 20L, Collections.singletonList(new ItemStack(Items.GOLD_INGOT)),
                Collections.emptyList(), 1);
        slotA.getExtraData().setString("slotKey", "A");

        RecipeSlot slotB = scheduler.acquireSlot();
        assertNotNull(slotB);
        slotB.startRecipe(recipeB, 2, 30L, Collections.singletonList(new ItemStack(Items.DIAMOND)),
                Collections.emptyList(), 1);
        slotB.getExtraData().setString("slotKey", "B");

        AtomicBoolean pauseSlotA = new AtomicBoolean(true);
        AtomicInteger slotAConsumed = new AtomicInteger();
        AtomicInteger slotBConsumed = new AtomicInteger();
        List<String> completedSlots = new ArrayList<>();

        List<IRecipeCondition> conditions = Collections.singletonList(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "slot_gate";
            }

            @Override
            public boolean testTick(@NotNull RecipeExecutionContext context) {
                String key = context.getExtraData().getString("slotKey");
                return !"A".equals(key) || !pauseSlotA.get();
            }

            @Override
            public void consumeTick(@NotNull RecipeExecutionContext context) {
                String key = context.getExtraData().getString("slotKey");
                if ("A".equals(key)) slotAConsumed.incrementAndGet();
                if ("B".equals(key)) slotBConsumed.incrementAndGet();
            }

            @Override
            public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                           boolean energyShortage) {
                return TickFailureAction.PAUSE;
            }

            @Override
            public void onRecipeComplete(@NotNull RecipeExecutionContext context) {
                completedSlots.add(context.getExtraData().getString("slotKey"));
            }
        });

        TestEnergyPool energyPool = new TestEnergyPool(500L);

        // Tick 1: A pauses, B runs -> only B's 30 EU/t is drawn!
        int completed1 = scheduler.tickSlots(outItems, outFluids, energyPool, false, false, conditions);
        assertEquals(0, completed1);
        assertEquals(470L, energyPool.stored);
        assertEquals(1, slotA.getProgressTime());
        assertEquals(2, slotB.getProgressTime());
        assertEquals(0, slotAConsumed.get());
        assertEquals(1, slotBConsumed.get());

        // Tick 2: A still paused, B completes (progress 2 -> 3 > 2)!
        int completed2 = scheduler.tickSlots(outItems, outFluids, energyPool, false, false, conditions);
        assertEquals(1, completed2);
        assertEquals(440L, energyPool.stored);
        assertEquals(1, slotA.getProgressTime());
        assertEquals(1, scheduler.getActiveSlotCount());
        assertEquals(Collections.singletonList("B"), completedSlots);
        assertEquals(Items.DIAMOND, outItems.getStackInSlot(0).getItem());

        // Unpause A and run 2 ticks to complete A
        pauseSlotA.set(false);
        assertEquals(0, scheduler.tickSlots(outItems, outFluids, energyPool, false, false, conditions));
        assertEquals(420L, energyPool.stored);
        assertEquals(2, slotA.getProgressTime());

        assertEquals(1, scheduler.tickSlots(outItems, outFluids, energyPool, false, false, conditions));
        assertEquals(400L, energyPool.stored);
        assertEquals(0, scheduler.getActiveSlotCount());
        assertEquals(2, slotAConsumed.get());
        assertEquals(2, completedSlots.size());
        assertTrue(completedSlots.contains("A"));
    }

    @Test
    void scheduler_decreaseProgressRespectsResetConfigAndInvalidate() {
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        try {
            ItemStackHandler outItems = new ItemStackHandler(4);
            FluidTankList outFluids = new FluidTankList(false, new FluidTank(10000));
            Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), 5, 10L);

            // Case 1: resetRecipeOnPowerLoss = false -> resets progressTime to 0, keeps slot active
            ConfigHolder.machines.resetRecipeOnPowerLoss = false;
            CrossRecipeParallelScheduler scheduler1 = new CrossRecipeParallelScheduler(8);
            RecipeSlot slot1 = scheduler1.acquireSlot();
            assertNotNull(slot1);
            slot1.startRecipe(recipe, 5, 10L, Collections.emptyList(), Collections.emptyList(), 1);

            AtomicInteger invalidated1 = new AtomicInteger();
            List<IRecipeCondition> conds1 = Collections.singletonList(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "fail_dec";
                }

                @Override
                public boolean testTick(@NotNull RecipeExecutionContext context) {
                    return false;
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    return TickFailureAction.DECREASE_PROGRESS;
                }

                @Override
                public void onInvalidate(@Nullable RecipeExecutionContext context) {
                    invalidated1.incrementAndGet();
                }
            });

            slot1.tick(); // advance to 2 first
            assertEquals(2, slot1.getProgressTime());
            scheduler1.tickSlots(outItems, outFluids, new TestEnergyPool(1000L), false, false, conds1);
            assertEquals(0, slot1.getProgressTime());
            assertEquals(1, scheduler1.getActiveSlotCount());
            assertEquals(0, invalidated1.get());

            // Case 2: resetRecipeOnPowerLoss = true -> removes slot and calls onInvalidate
            ConfigHolder.machines.resetRecipeOnPowerLoss = true;
            CrossRecipeParallelScheduler scheduler2 = new CrossRecipeParallelScheduler(8);
            RecipeSlot slot2 = scheduler2.acquireSlot();
            assertNotNull(slot2);
            slot2.startRecipe(recipe, 5, 10L, Collections.emptyList(), Collections.emptyList(), 1);
            slot2.getExtraData().setString("marker", "drop_me");

            AtomicInteger invalidated2 = new AtomicInteger();
            List<IRecipeCondition> conds2 = Collections.singletonList(new IRecipeCondition() {

                @Override
                public @NotNull String getConditionId() {
                    return "fail_reset";
                }

                @Override
                public boolean testTick(@NotNull RecipeExecutionContext context) {
                    return false;
                }

                @Override
                public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                               boolean energyShortage) {
                    return TickFailureAction.DECREASE_PROGRESS;
                }

                @Override
                public void onInvalidate(@Nullable RecipeExecutionContext context) {
                    assertNotNull(context);
                    assertEquals("drop_me", context.getExtraData().getString("marker"));
                    invalidated2.incrementAndGet();
                }
            });

            scheduler2.tickSlots(outItems, outFluids, new TestEnergyPool(1000L), false, false, conds2);
            assertEquals(0, scheduler2.getActiveSlotCount());
            assertEquals(1, invalidated2.get());
        } finally {
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void scheduler_extraDataAndCompletedSlotSurviveNbtRoundTrip() {
        CrossRecipeParallelScheduler scheduler = new CrossRecipeParallelScheduler(16);
        scheduler.setMaxVoltage(128L);
        scheduler.setTotalPowerBudget(1000L);

        // Full output inventory so completed slot cannot commit yet
        ItemStackHandler blockedOutItems = new ItemStackHandler(1);
        blockedOutItems.setStackInSlot(0, new ItemStack(Items.EMERALD, 64));
        FluidTankList outFluids = new FluidTankList(false, new FluidTank(10000));

        Recipe recipeDone = createTestRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), 1, 10L);
        Recipe recipeRunning = createTestRecipe(new ItemStack(Items.COAL), new ItemStack(Items.DIAMOND), 5, 20L);

        RecipeSlot completedSlot = scheduler.acquireSlot();
        assertNotNull(completedSlot);
        completedSlot.startRecipe(recipeDone, 1, 10L, Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 2)),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 250)), 2, 2, "done_recipe");
        completedSlot.getExtraData().setString("tag", "blocked_completed");

        RecipeSlot runningSlot = scheduler.acquireSlot();
        assertNotNull(runningSlot);
        runningSlot.startRecipe(recipeRunning, 5, 20L, Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                Collections.emptyList(), 1, 1, "running_recipe");
        runningSlot.getExtraData().setInteger("retentionTicks", 7);

        TestEnergyPool energyPool = new TestEnergyPool(1000L);
        // Tick once: completedSlot reaches COMPLETED and is retained because output is blocked!
        int completed = scheduler.tickSlots(blockedOutItems, outFluids, energyPool, false, false,
                Collections.emptyList());
        assertEquals(0, completed);
        assertEquals(2, scheduler.getActiveSlotCount());
        assertTrue(completedSlot.isCompleted());
        assertTrue(runningSlot.isRunning());
        assertEquals(2, runningSlot.getProgressTime());

        // Round-trip serializeNBT -> deserializeNBT
        NBTTagCompound tag = scheduler.serializeNBT();
        CrossRecipeParallelScheduler restored = new CrossRecipeParallelScheduler(16);
        restored.deserializeNBT(tag);

        assertEquals(2, restored.getActiveSlotCount());
        RecipeSlot restoredCompleted = restored.getActiveSlots().get(0);
        RecipeSlot restoredRunning = restored.getActiveSlots().get(1);

        assertTrue(restoredCompleted.isCompleted());
        assertEquals("blocked_completed", restoredCompleted.getExtraData().getString("tag"));
        assertEquals("done_recipe", restoredCompleted.getRecipeDisplayName());

        assertTrue(restoredRunning.isRunning());
        assertEquals(2, restoredRunning.getProgressTime());
        assertEquals(7, restoredRunning.getExtraData().getInteger("retentionTicks"));

        // Clear output inventory, attach condition, and tick restored scheduler:
        // restored COMPLETED slot must commit outputs and fire onRecipeComplete!
        blockedOutItems.setStackInSlot(0, ItemStack.EMPTY);
        List<String> completedTags = new ArrayList<>();
        List<IRecipeCondition> conditions = Collections.singletonList(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "restore_cond";
            }

            @Override
            public void onRecipeComplete(@NotNull RecipeExecutionContext context) {
                completedTags.add(context.getExtraData().getString("tag"));
            }
        });

        int committed = restored.tickSlots(blockedOutItems, outFluids, energyPool, false, false, conditions);
        assertEquals(2, committed);
        assertEquals(Collections.singletonList("blocked_completed"), completedTags);
        assertEquals(Items.GOLD_INGOT, blockedOutItems.getStackInSlot(0).getItem());
        assertEquals(2, blockedOutItems.getStackInSlot(0).getCount());
        assertEquals(250, outFluids.getTankAt(0).getFluidAmount());
        assertEquals(1, restored.getActiveSlotCount());
        assertEquals(3, restoredRunning.getProgressTime());
    }

    @Test
    void scheduler_invalidateAllNotifiesConditions() {
        CrossRecipeParallelScheduler scheduler = new CrossRecipeParallelScheduler(8);
        Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), 5, 10L);
        RecipeSlot slot = scheduler.acquireSlot();
        assertNotNull(slot);
        slot.startRecipe(recipe, 5, 10L, Collections.emptyList(), Collections.emptyList(), 1);
        slot.getExtraData().setString("k", "v");

        List<String> invalidated = new ArrayList<>();
        scheduler.invalidateAll(Collections.singletonList(new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "inv_cond";
            }

            @Override
            public void onInvalidate(@Nullable RecipeExecutionContext context) {
                if (context != null) {
                    invalidated.add(context.getExtraData().getString("k"));
                }
            }
        }));

        assertEquals(0, scheduler.getActiveSlotCount());
        assertEquals(Collections.singletonList("v"), invalidated);
    }

    @Test
    void scheduler_sharedFinitePoolReservationsPreventOvercommitAndLeak() {
        CrossRecipeParallelScheduler scheduler = new CrossRecipeParallelScheduler(16);
        scheduler.setMaxVoltage(128L);
        scheduler.setTotalPowerBudget(1000L);

        ItemStackHandler outItems = new ItemStackHandler(4);
        FluidTankList outFluids = new FluidTankList(false, new FluidTank(10000));

        Recipe recipe = createTestRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), 2, 10L);

        RecipeSlot slotBlockedByGate = scheduler.acquireSlot();
        assertNotNull(slotBlockedByGate);
        slotBlockedByGate.startRecipe(recipe, 2, 10L, Collections.singletonList(new ItemStack(Items.GOLD_INGOT)),
                Collections.emptyList(), 1);
        slotBlockedByGate.getExtraData().setBoolean("gatePass", false);

        RecipeSlot slot1 = scheduler.acquireSlot();
        assertNotNull(slot1);
        slot1.startRecipe(recipe, 2, 10L, Collections.singletonList(new ItemStack(Items.GOLD_INGOT)),
                Collections.emptyList(), 1);
        slot1.getExtraData().setBoolean("gatePass", true);

        RecipeSlot slot2 = scheduler.acquireSlot();
        assertNotNull(slot2);
        slot2.startRecipe(recipe, 2, 10L, Collections.singletonList(new ItemStack(Items.GOLD_INGOT)),
                Collections.emptyList(), 1);
        slot2.getExtraData().setBoolean("gatePass", true);

        AtomicInteger pool = new AtomicInteger(120);
        AtomicInteger reserved = new AtomicInteger(0);
        AtomicInteger reserveCalls = new AtomicInteger(0);
        TestEnergyPool energyPool = new TestEnergyPool(1000L);

        IRecipeCondition poolCondition = new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "finite_pool";
            }

            @Override
            public void beginTick() {
                reserved.set(0);
            }

            @Override
            public boolean testTick(@NotNull RecipeExecutionContext context) {
                return pool.get() >= reserved.get() + 100;
            }

            @Override
            public void reserveTick(@NotNull RecipeExecutionContext context) {
                reserved.addAndGet(100);
                reserveCalls.incrementAndGet();
            }

            @Override
            public void consumeTick(@NotNull RecipeExecutionContext context) {
                int remaining = pool.addAndGet(-100);
                assertTrue(remaining >= 0, "Shared finite pool must never drain below 0");
            }

            @Override
            public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                           boolean energyShortage) {
                return TickFailureAction.PAUSE;
            }
        };

        IRecipeCondition gateCondition = new IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "secondary_gate";
            }

            @Override
            public boolean testTick(@NotNull RecipeExecutionContext context) {
                return context.getExtraData().getBoolean("gatePass");
            }

            @Override
            public @NotNull TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context,
                                                           boolean energyShortage) {
                return TickFailureAction.PAUSE;
            }
        };

        List<IRecipeCondition> conditions = java.util.Arrays.asList(poolCondition, gateCondition);

        // Tick 1:
        // - slotBlockedByGate passes poolCondition (0 + 100 <= 120) but fails gateCondition -> must NOT reserve!
        // - slot1 passes both -> reserves 100 and consumes 100 (pool: 120 -> 20), progresses 1 -> 2
        // - slot2 fails poolCondition (100 + 100 > 120) -> pauses at 1 without freeloading
        int completed1 = scheduler.tickSlots(outItems, outFluids, energyPool, false, false, conditions);
        assertEquals(0, completed1);
        assertEquals(20, pool.get());
        assertEquals(1, reserveCalls.get(), "Failed gate slot must not leak a reservation");
        assertEquals(1, slotBlockedByGate.getProgressTime());
        assertEquals(2, slot1.getProgressTime());
        assertEquals(1, slot2.getProgressTime());
        assertEquals(990L, energyPool.stored);

        // Refill pool to 200 and tick again: slot1 completes (2 -> 3 > 2), slot2 advances (1 -> 2)
        pool.set(200);
        int completed2 = scheduler.tickSlots(outItems, outFluids, energyPool, false, false, conditions);
        assertEquals(1, completed2);
        assertEquals(0, pool.get());
        assertEquals(2, scheduler.getActiveSlotCount());
        assertEquals(2, slot2.getProgressTime());
        assertEquals(Items.GOLD_INGOT, outItems.getStackInSlot(0).getItem());
    }

    // ==================== Minimal Headless Test Doubles ====================

    private static final class TestEnergyPool implements CrossRecipeParallelScheduler.EnergyDrawer {

        long stored;

        TestEnergyPool(long stored) {
            this.stored = stored;
        }

        @Override
        public boolean drawEnergy(long amount, boolean simulate) {
            if (stored < amount) return false;
            if (!simulate) stored -= amount;
            return true;
        }
    }

    private static final class TestEnergyContainer implements IEnergyContainer {

        private long stored;
        private final long capacity;
        private final long maxVoltage;

        TestEnergyContainer(long stored, long capacity, long maxVoltage) {
            this.stored = stored;
            this.capacity = capacity;
            this.maxVoltage = maxVoltage;
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
            return 2L;
        }

        @Override
        public long getInputVoltage() {
            return maxVoltage;
        }
    }

    private static final class TestMetaTileEntity extends MetaTileEntity {

        TestMetaTileEntity(int slots) {
            super(new ResourceLocation("gregtech", "test_condition_mte"));
            this.importItems = new ItemStackHandler(slots);
            this.exportItems = new ItemStackHandler(slots);
            this.importFluids = new FluidTankList(false, new FluidTank(10000));
            this.exportFluids = new FluidTankList(false, new FluidTank(10000));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestMetaTileEntity(4);
        }
    }

    private static final class TestSingleRecipeLogic extends AbstractRecipeLogic {

        private final IEnergyContainer energyContainer;

        TestSingleRecipeLogic(MetaTileEntity mte, IEnergyContainer energyContainer) {
            super(mte, TEST_RECIPE_MAP);
            this.energyContainer = energyContainer;
            setAllowOverclocking(false);
        }

        @Override
        protected IEnergyContainer getEnergyContainer() {
            return energyContainer;
        }

        @Override
        public long getMaximumOverclockVoltage() {
            return energyContainer.getInputVoltage();
        }

        @Override
        protected boolean hasEnoughPower(long eut, int duration) {
            return energyContainer.getEnergyStored() >= eut;
        }

        void tickProgress() {
            updateRecipeProgress();
        }

        NBTTagCompound getSingleExtraData() {
            return singleRecipeExtraData;
        }
    }
}
