package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeIterator;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.recipes.ingredients.nbtmatch.NBTTagType;
import gregtech.api.recipes.logic.ParallelLogic;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidNBTMatcherTest {

    @BeforeAll
    static void init() {
        MultiblockRecipeLockAndInvalidationTest.bootstrap();
    }

    private static FluidStack cleanWater(int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("clean", true);
        return new FluidStack(FluidRegistry.WATER, amount, tag);
    }

    private static FluidStack taggedWater(int amount, String key, int value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(key, value);
        return new FluidStack(FluidRegistry.WATER, amount, tag);
    }

    @Test
    void pureFluidLookupOverlappingSpecialNodesIteratorAndRemovalWork() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMap<>(
                "test_fluid_nbt_lookup_map", new SimpleRecipeBuilder(), r -> null, 0, 2, 2, 2);
        Recipe anyHv = map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(new ItemStack(Items.GOLD_INGOT, 1))
                .duration(4).EUt(512).build().getResult();
        Recipe grade1Lv = map.recipeBuilder()
                .fluidInputNBT(taggedWater(1000, "grade", 99), NBTMatcher.RECURSIVE_EQUAL_TO,
                        NBTCondition.create(NBTTagType.INT, "grade", 1))
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(30).build().getResult();
        Recipe plainLv = map.recipeBuilder()
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 1000))
                .outputs(new ItemStack(Items.COAL, 1))
                .duration(4).EUt(30).build().getResult();
        map.compileRecipe(anyHv);
        map.compileRecipe(grade1Lv);

        assertEquals(grade1Lv, grade1Lv.copy());
        assertNotEquals(anyHv, plainLv);
        assertTrue(grade1Lv.hasInputFluid(taggedWater(1000, "grade", 1)));
        assertFalse(grade1Lv.hasInputFluid(taggedWater(1000, "grade", 2)));
        assertFalse(grade1Lv.hasInputFluid(null));

        NBTCondition cleanCond = NBTCondition.create(NBTTagType.BOOLEAN, "clean", true);
        Recipe dualAB = map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(16).build().getResult();
        Recipe dualBA = map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(16).build().getResult();
        assertEquals(dualAB, dualBA);
        assertEquals(dualAB.hashCode(), dualBA.hashCode());

        Recipe cleanPlusPlain = map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 1000))
                .outputs(new ItemStack(Items.COAL, 1))
                .duration(4).EUt(16).build().getResult();
        assertTrue(cleanPlusPlain.hasInputFluid(new FluidStack(FluidRegistry.WATER, 1000)));
        assertSame(grade1Lv, map.findRecipe(32L, Collections.emptyList(),
                Collections.singletonList(taggedWater(1000, "grade", 1))));
        assertNull(map.findRecipe(32L, Collections.emptyList(),
                Collections.singletonList(taggedWater(1000, "grade", 2))));

        RecipeIterator iter = map.findRecipeIterator(Collections.emptyList(),
                Collections.singletonList(taggedWater(2000, "grade", 1)), r -> true);
        assertNotNull(iter);
        List<Recipe> found = new ArrayList<>();
        while (iter.hasNext()) {
            found.add(iter.next());
        }
        assertEquals(2, found.size());
        assertTrue(found.contains(anyHv) && found.contains(grade1Lv));

        assertTrue(map.removeRecipe(grade1Lv));
        assertNull(map.findRecipe(32L, Collections.emptyList(),
                Collections.singletonList(taggedWater(1000, "grade", 1))));
        assertSame(anyHv, map.findRecipe(512L, Collections.emptyList(),
                Collections.singletonList(taggedWater(1000, "grade", 1))));
    }

    @Test
    void orderIndependentMatchesAndParallelMultiplierRespectOverlappingNBTAndNonConsumables() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMap<>(
                "test_fluid_nbt_parallel_map", new SimpleRecipeBuilder(), r -> null, 0, 2, 2, 2);
        NBTCondition cleanCond = NBTCondition.create(NBTTagType.BOOLEAN, "clean", true);
        Recipe dual = map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(16).build().getResult();
        Recipe ncRecipe = map.recipeBuilder()
                .notConsumableFluidNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(new ItemStack(Items.EMERALD, 1))
                .duration(4).EUt(16).build().getResult();
        Recipe plainNcRecipe = map.recipeBuilder()
                .notConsumable(FluidRegistry.WATER, 1000)
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 1000))
                .outputs(new ItemStack(Items.COAL, 1))
                .duration(4).EUt(16).build().getResult();

        List<FluidStack> criticalPlain = new ArrayList<>(
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000)));
        assertFalse(plainNcRecipe.matches(true, Collections.emptyList(), criticalPlain));
        assertEquals(1000, criticalPlain.get(0).amount);
        List<FluidStack> sufficientPlain = new ArrayList<>(
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 2000)));
        assertTrue(plainNcRecipe.matches(true, Collections.emptyList(), sufficientPlain));
        assertEquals(1000, sufficientPlain.get(0).amount);

        for (boolean cleanFirst : new boolean[] { true, false }) {
            List<FluidStack> tanks = new ArrayList<>(Arrays.asList(
                    cleanFirst ? cleanWater(1000) : new FluidStack(FluidRegistry.WATER, 1000),
                    cleanFirst ? new FluidStack(FluidRegistry.WATER, 1000) : cleanWater(1000)));
            assertTrue(dual.matches(true, Collections.emptyList(), tanks));
            assertNull(tanks.get(0));
            assertNull(tanks.get(1));

            List<FluidStack> ncTanks = new ArrayList<>(Arrays.asList(
                    cleanFirst ? cleanWater(1000) : new FluidStack(FluidRegistry.WATER, 1000),
                    cleanFirst ? new FluidStack(FluidRegistry.WATER, 1000) : cleanWater(1000)));
            assertTrue(ncRecipe.matches(true, Collections.emptyList(), ncTanks));
            FluidStack kept = ncTanks.get(cleanFirst ? 0 : 1);
            assertNotNull(kept);
            assertEquals(1000, kept.amount);
            assertNull(ncTanks.get(cleanFirst ? 1 : 0));
        }
        assertFalse(ncRecipe.matches(false, Collections.emptyList(), Collections.singletonList(cleanWater(1000))));

        ItemStackHandler emptyItems = new ItemStackHandler(0);
        assertEquals(1, ParallelLogic.getMaxRecipeMultiplier(dual, emptyItems,
                new FluidTankList(false, new FluidTank(cleanWater(2000), 4000)), 4));
        assertEquals(2, ParallelLogic.getMaxRecipeMultiplier(dual, emptyItems,
                new FluidTankList(false, new FluidTank(new FluidStack(FluidRegistry.WATER, 2000), 4000),
                        new FluidTank(cleanWater(2000), 4000)), 4));

        Recipe anySingle = map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .fluidOutputs(new FluidStack(FluidRegistry.LAVA, 500))
                .duration(4).EUt(16).build().getResult();
        map.compileRecipe(anySingle);
        FluidTank[] split8 = new FluidTank[8];
        for (int i = 0; i < 8; i++) {
            split8[i] = new FluidTank(i < 7 ? taggedWater(250, "batch", i) : new FluidStack(FluidRegistry.WATER, 250),
                    250);
        }
        assertEquals(2, ParallelLogic.getMaxRecipeMultiplier(anySingle, emptyItems, new FluidTankList(false, split8), 4));
        RecipeBuilder<?> appended = ParallelLogic.appendFluidRecipes(map, new FluidTankList(false, split8),
                new FluidTankList(false, new FluidTank(8000)), 4, 128L,
                new MultiblockRecipeLockAndInvalidationTest.TestController(map));
        assertNotNull(appended);
        assertEquals(2, appended.getParallel());
    }

    @Test
    void multiblockExecutesPureFluidNBTMatcherAcrossParallelBatchAndSchedulerModes() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMap<>(
                "test_fluid_nbt_multi_modes_map", new SimpleRecipeBuilder(), r -> null, 0, 2, 2, 2);
        NBTCondition cleanCond = NBTCondition.create(NBTTagType.BOOLEAN, "clean", true);
        map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(16).buildAndRegister();

        assertNotNull(map.findRecipe(32L, Collections.emptyList(), Collections.singletonList(cleanWater(2000))));

        for (int mode = 0; mode < 3; mode++) {
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(map);
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            if (mode == 0) {
                logic.setParallelLimit(4);
            } else if (mode == 1) {
                logic.setBatchEnable(true);
            } else {
                logic.setParallelLimit(4);
                logic.setThreadLimit(2);
            }
            FluidTank plainTank = new FluidTank(new FluidStack(FluidRegistry.WATER, 2000), 4000);
            FluidTank cleanTank = new FluidTank(cleanWater(2000), 4000);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_fnbt_out_" + mode), 1, true);
            ctrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart("test_fnbt_p_" + mode, plainTank, null));
            ctrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart("test_fnbt_c_" + mode, cleanTank, null));
            ctrl.addPart(outBus);

            for (int t = 0; t < 16; t++) {
                logic.updateWorkable();
            }
            assertEquals(0, plainTank.getFluidAmount());
            assertEquals(0, cleanTank.getFluidAmount());
            assertEquals(2, outBus.getExportItems().getStackInSlot(0).getCount());
        }

        RecipeMap<SimpleRecipeBuilder> ncMap = new RecipeMap<>(
                "test_fluid_nbt_nc_multi_map", new SimpleRecipeBuilder(), r -> null, 0, 2, 3, 2);
        ncMap.recipeBuilder()
                .notConsumableFluidNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO, cleanCond)
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(new ItemStack(Items.EMERALD, 1))
                .duration(4).EUt(16).buildAndRegister();
        for (int threads : new int[] { 1, 2 }) {
            MultiblockRecipeLockAndInvalidationTest.TestController ncCtrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(ncMap);
            ncCtrl.getTestLogic().setAllowOverclocking(false);
            ncCtrl.getTestLogic().setThreadLimit(threads);
            FluidTank cleanCatalyst = new FluidTank(cleanWater(1000), 2000);
            FluidTank plainFeed = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 2000);
            MetaTileEntityItemBus ncOut = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_fnbt_nc_out_" + threads), 1, true);
            ncCtrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart(
                    "test_fnbt_nc_c_" + threads, cleanCatalyst, null));
            ncCtrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart(
                    "test_fnbt_nc_p_" + threads, plainFeed, null));
            ncCtrl.addPart(ncOut);
            for (int t = 0; t < 8; t++) {
                ncCtrl.getTestLogic().updateWorkable();
            }
            assertEquals(1000, cleanCatalyst.getFluidAmount());
            assertEquals(0, plainFeed.getFluidAmount());
            assertEquals(1, ncOut.getExportItems().getStackInSlot(0).getCount());
        }

        // Dual non-consumable catalysts + ANY consumable requiring 500 mB + 500 mB intermediate cross-tank split
        RecipeMap<SimpleRecipeBuilder> splitNcMap = new RecipeMap<>(
                "test_fluid_nbt_dual_nc_split_map", new SimpleRecipeBuilder(), r -> null, 0, 2, 3, 2);
        splitNcMap.recipeBuilder()
                .notConsumableFluidNBT(FluidRegistry.WATER, 1000, NBTMatcher.RECURSIVE_EQUAL_TO,
                        NBTCondition.create(NBTTagType.INT, "grade", 1))
                .notConsumableFluidNBT(taggedWater(1000, "grade", 99), NBTMatcher.RECURSIVE_EQUAL_TO,
                        NBTCondition.create(NBTTagType.INT, "grade", 2))
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(16).buildAndRegister();
        for (int threads : new int[] { 1, 2 }) {
            MultiblockRecipeLockAndInvalidationTest.TestController splitCtrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(splitNcMap);
            splitCtrl.getTestLogic().setAllowOverclocking(false);
            splitCtrl.getTestLogic().setThreadLimit(threads);
            FluidTank grade1Tank = new FluidTank(taggedWater(1500, "grade", 1), 2000);
            FluidTank grade2Tank = new FluidTank(taggedWater(1500, "grade", 2), 2000);
            MetaTileEntityItemBus splitOut = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_fnbt_split_out_" + threads), 1, true);
            splitCtrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart(
                    "test_fnbt_split_g1_" + threads, grade1Tank, null));
            splitCtrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart(
                    "test_fnbt_split_g2_" + threads, grade2Tank, null));
            splitCtrl.addPart(splitOut);
            for (int t = 0; t < 8; t++) {
                splitCtrl.getTestLogic().updateWorkable();
            }
            assertEquals(1000, grade1Tank.getFluidAmount());
            assertEquals(1000, grade2Tank.getFluidAmount());
            assertEquals(1, splitOut.getExportItems().getStackInSlot(0).getCount());
        }
    }

    @Test
    void schedulerTwoThreadModeRunsEightDistinctNBTTanksWithAuxiliaryDrain() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMap<>(
                "test_fluid_nbt_8tanks_sched_map", new SimpleRecipeBuilder(), r -> null, 0, 2, 2, 2);
        map.recipeBuilder()
                .fluidInputNBT(FluidRegistry.WATER, 1000, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(new ItemStack(Items.DIAMOND, 1))
                .duration(4).EUt(16).buildAndRegister();

        for (boolean auxFirst : new boolean[] { true, false }) {
            FluidTank[] tanks = new FluidTank[8];
            for (int i = 0; i < 8; i++) {
                int idx = auxFirst ? i : (7 - i);
                FluidStack stack = idx < 7 ? taggedWater(250, "batch", idx) : new FluidStack(FluidRegistry.WATER, 250);
                tanks[i] = new FluidTank(stack, 250);
                if (idx < 4) tanks[i].setCanFill(false);
            }
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(map) {

                        @Override
                        public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
                            IMultipleTankHandler inv = getInputFluidInventory();
                            for (int b = 0; b < 4; b++) {
                                FluidStack req = taggedWater(250, "batch", b);
                                FluidStack d = inv.drain(req, consumeIfSuccess);
                                if (d == null || d.amount < 250) return false;
                            }
                            return true;
                        }
                    };
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setThreadLimit(2);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_fnbt_8t_out_" + auxFirst), 1, true);
            ctrl.addPart(outBus);
            for (int i = 0; i < 8; i++) {
                ctrl.addPart(new MultiblockInputRollbackTest.TestPaintableFluidHatchPart(
                        "test_fnbt_8t_h_" + auxFirst + "_" + i, tanks[i], null));
            }
            for (int t = 0; t < 10; t++) {
                logic.updateWorkable();
            }
            for (FluidTank tank : tanks) {
                assertEquals(0, tank.getFluidAmount());
            }
            assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
        }
    }
}
