package gregtech.api.capability.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TestMetaTileEntityHolder;
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
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.registry.MarkerMaterialRegistry;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.core.unification.material.internal.MaterialRegistryManager;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeDualHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMoldItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiFluidHatch;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockColorChannelTest {

    private static RecipeMap<SimpleRecipeBuilder> TEST_RECIPE_MAP;

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
        TEST_RECIPE_MAP = new RecipeMap<>("test_color_channel_map", new SimpleRecipeBuilder(),
                r -> null, 2, 2, 2, 2);
        // Recipe 1: iron -> gold
        TEST_RECIPE_MAP.compileRecipe(makeRecipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1))),
                Collections.emptyList(),
                new ItemStack(Items.GOLD_INGOT, 1)));
        // Recipe 2: coal -> diamond
        TEST_RECIPE_MAP.compileRecipe(makeRecipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.COAL, 1))),
                Collections.emptyList(),
                new ItemStack(Items.DIAMOND, 1)));
        // Recipe 3: apple + wheat -> golden_apple (tests cross-color item+item isolation)
        TEST_RECIPE_MAP.compileRecipe(makeRecipe(
                Arrays.asList(
                        new GTRecipeItemInput(new ItemStack(Items.APPLE, 1)),
                        new GTRecipeItemInput(new ItemStack(Items.WHEAT, 1))),
                Collections.emptyList(),
                new ItemStack(Items.GOLDEN_APPLE, 1)));
        // Recipe 4: stick + water -> blaze_rod (tests DualHandler / fluid channel isolation)
        TEST_RECIPE_MAP.compileRecipe(makeRecipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.STICK, 1))),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 100))),
                new ItemStack(Items.BLAZE_ROD, 1)));
    }

    private static Recipe makeRecipe(List<GTRecipeInput> inputs, List<GTRecipeInput> fluidInputs, ItemStack output) {
        return new Recipe(
                inputs,
                Collections.singletonList(output),
                ChancedOutputList.empty(),
                fluidInputs,
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                4,
                16L,
                false,
                new RecipePropertyStorageImpl(),
                TEST_RECIPE_MAP.getPrimaryRecipeCategory());
    }

    @Test
    void ownerOfAndColorIndexOfIdentifyAllHatchTypesNotController() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();
        logic.setAllowOverclocking(false);

        MetaTileEntityItemBus itemBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_item_bus"), 1, false);
        MetaTileEntityHugeItemBus hugeItemBus = new MetaTileEntityHugeItemBus(
                new ResourceLocation("gregtech", "test_huge_item_bus"), 1, false, 4);
        MetaTileEntityMoldItemBus moldBus = new MetaTileEntityMoldItemBus(
                new ResourceLocation("gregtech", "test_mold_bus"), 1);
        MetaTileEntityDualHatch dualHatch = new MetaTileEntityDualHatch(
                new ResourceLocation("gregtech", "test_dual_hatch"), 1, false);
        MetaTileEntityHugeDualHatch hugeDualHatch = new MetaTileEntityHugeDualHatch(
                new ResourceLocation("gregtech", "test_huge_dual_hatch"), 1, false, 4);
        MetaTileEntityFluidHatch fluidHatch = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_fluid_hatch"), 1, false);
        MetaTileEntityMultiFluidHatch multiFluidHatch = new MetaTileEntityMultiFluidHatch(
                new ResourceLocation("gregtech", "test_multi_fluid_hatch"), 1, 4, false);

        itemBus.setPaintingColor(EnumDyeColor.WHITE, null);        // 0
        hugeItemBus.setPaintingColor(EnumDyeColor.ORANGE, null);   // 1
        moldBus.setPaintingColor(EnumDyeColor.YELLOW, null);       // 4
        dualHatch.setPaintingColor(EnumDyeColor.BLUE, null);       // 11
        hugeDualHatch.setPaintingColor(EnumDyeColor.PURPLE, null); // 10
        fluidHatch.setPaintingColor(EnumDyeColor.RED, null);       // 14
        multiFluidHatch.setPaintingColor(EnumDyeColor.BLACK, null);// 15

        // Simulate controller also registering itself on NotifiableItemStackHandler / NotifiableFluidTank
        // (historically notifiableEntities.get(0) could resolve to controller!)
        controller.addPart(itemBus);
        controller.addPart(hugeItemBus);
        controller.addPart(moldBus);
        controller.addPart(dualHatch);
        controller.addPart(hugeDualHatch);
        controller.addPart(fluidHatch);
        controller.addPart(multiFluidHatch);

        // Controller color differs intentionally to prove ownerOf does NOT return controller
        controller.setPaintingColor(EnumDyeColor.LIME, null);

        for (IItemHandlerModifiable handler : controller.getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
            assertFalse(logic.ownerOfPublic(handler) == controller,
                    "Item handler must not resolve to controller: " + handler);
        }
        for (IFluidTank tank : controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS)) {
            assertFalse(logic.ownerOfPublic(tank) == controller,
                    "Fluid tank must not resolve to controller: " + tank);
        }

        // Verify ItemHandlerList-wrapped NotifiableItemStackHandlers
        assertSame(itemBus, logic.ownerOfPublic(abilityOf(itemBus, MultiblockAbility.IMPORT_ITEMS).get(0)));
        assertEquals(EnumDyeColor.WHITE.getMetadata(),
                logic.colorIndexOfPublic(abilityOf(itemBus, MultiblockAbility.IMPORT_ITEMS).get(0)));

        assertSame(hugeItemBus, logic.ownerOfPublic(abilityOf(hugeItemBus, MultiblockAbility.IMPORT_ITEMS).get(0)));
        assertEquals(EnumDyeColor.ORANGE.getMetadata(),
                logic.colorIndexOfPublic(abilityOf(hugeItemBus, MultiblockAbility.IMPORT_ITEMS).get(0)));

        assertSame(moldBus, logic.ownerOfPublic(abilityOf(moldBus, MultiblockAbility.IMPORT_ITEMS).get(0)));
        assertEquals(EnumDyeColor.YELLOW.getMetadata(),
                logic.colorIndexOfPublic(abilityOf(moldBus, MultiblockAbility.IMPORT_ITEMS).get(0)));

        // Verify DualHandler (item + fluid)
        Object dualAbility = abilityOf(dualHatch, MultiblockAbility.IMPORT_ITEMS).get(0);
        assertTrue(dualAbility instanceof DualHandler);
        assertSame(dualHatch, logic.ownerOfPublic(dualAbility));
        assertEquals(EnumDyeColor.BLUE.getMetadata(), logic.colorIndexOfPublic(dualAbility));

        Object hugeDualAbility = abilityOf(hugeDualHatch, MultiblockAbility.IMPORT_ITEMS).get(0);
        assertTrue(hugeDualAbility instanceof DualHandler);
        assertSame(hugeDualHatch, logic.ownerOfPublic(hugeDualAbility));
        assertEquals(EnumDyeColor.PURPLE.getMetadata(), logic.colorIndexOfPublic(hugeDualAbility));

        // Verify NotifiableFluidTank (FluidHatch) after controller was added as notifiable entity
        NotifiableFluidTank singleTank = (NotifiableFluidTank) abilityOf(fluidHatch, MultiblockAbility.IMPORT_FLUIDS)
                .get(0);
        // Force controller to front of notifiableEntities to mimic real structure formation order edge case
        singleTank.notifiableEntities.clear();
        singleTank.notifiableEntities.add(controller);
        singleTank.notifiableEntities.add(fluidHatch);
        assertSame(fluidHatch, logic.ownerOfPublic(singleTank));
        assertEquals(EnumDyeColor.RED.getMetadata(), logic.colorIndexOfPublic(singleTank));

        // Verify FluidTankList.ITankEntry (MultiFluidHatch)
        FluidTankList wrappedList = new FluidTankList(false,
                abilityOf(multiFluidHatch, MultiblockAbility.IMPORT_FLUIDS).stream()
                        .map(IFluidTank.class::cast)
                        .toArray(IFluidTank[]::new));
        for (IMultipleTankHandler.ITankEntry entry : wrappedList.getFluidTanks()) {
            assertSame(multiFluidHatch, logic.ownerOfPublic(entry));
            assertEquals(EnumDyeColor.BLACK.getMetadata(), logic.colorIndexOfPublic(entry));
        }

        // Verify getHatchColorsMask includes all 7 painted colors
        short mask = logic.getHatchColorsMaskPublic();
        assertTrue((mask & (1 << 0)) != 0);
        assertTrue((mask & (1 << 1)) != 0);
        assertTrue((mask & (1 << 4)) != 0);
        assertTrue((mask & (1 << 10)) != 0);
        assertTrue((mask & (1 << 11)) != 0);
        assertTrue((mask & (1 << 14)) != 0);
        assertTrue((mask & (1 << 15)) != 0);
    }

    @Test
    void getChannelOrderStartsAtZeroAndWrapsFromHighestChannel() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();
        logic.setAllowOverclocking(false);

        short mask = (short) ((1 << 0) | (1 << 4) | (1 << 14));

        // Initial NO_CHANNEL (-1): starts at lowest channel 0, ends with NO_CHANNEL (-1)
        logic.setLastColorChannel(MultiblockRecipeLogic.NO_CHANNEL);
        assertArrayEquals(new int[] { 0, 4, 14, MultiblockRecipeLogic.NO_CHANNEL }, logic.getChannelOrderPublic(mask));

        // After 0 succeeds -> starts at 4, wraps to 0, ends with NO_CHANNEL
        logic.setLastColorChannel(0);
        assertArrayEquals(new int[] { 4, 14, 0, MultiblockRecipeLogic.NO_CHANNEL }, logic.getChannelOrderPublic(mask));

        // After 14 (highest!) succeeds -> pivot == null -> wraps cleanly to 0 -> 4 -> 14 -> NO_CHANNEL
        // (No skipped channel 0, no phantom 0s!)
        logic.setLastColorChannel(14);
        assertArrayEquals(new int[] { 0, 4, 14, MultiblockRecipeLogic.NO_CHANNEL }, logic.getChannelOrderPublic(mask));
    }

    @Test
    void channelZeroIsNotSkippedInSingleOrDistinctSearch() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();
        logic.setAllowOverclocking(false);

        MetaTileEntityItemBus whiteBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_white_bus"), 1, false);
        whiteBus.setPaintingColor(EnumDyeColor.WHITE, null); // channel 0!
        whiteBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        controller.addPart(whiteBus);

        // With only channel 0 present, channelOrder is [0, -1]; index 0 MUST be checked!
        logic.trySearchNewRecipePublic();
        assertTrue(logic.isActive());
        assertEquals(0, logic.getLastColorChannel());
        assertEquals(1, logic.getProgress());

        // Also test distinct + color channel 0
        controller.setDistinct(true);
        logic.invalidate();
        whiteBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        logic.trySearchNewRecipePublic();
        assertTrue(logic.isActive());
        assertEquals(0, logic.getLastColorChannel());
    }

    @Test
    void colorChannelsIntegrateWithSchedulerWithoutCrossColorMixing() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(8); // enables CrossRecipeParallelScheduler
        logic.setCrossRecipeParallelEnabled(true);

        MetaTileEntityItemBus redBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_sched_red"), 1, false);
        redBus.setPaintingColor(EnumDyeColor.RED, null); // 14

        MetaTileEntityItemBus blueBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_sched_blue"), 1, false);
        blueBus.setPaintingColor(EnumDyeColor.BLUE, null); // 11

        controller.addPart(redBus);
        controller.addPart(blueBus);

        // Case 1: Apple in RED, Wheat in BLUE -> Recipe 3 (Apple + Wheat -> Golden Apple) MUST NOT mix!
        redBus.getImportItems().setStackInSlot(0, new ItemStack(Items.APPLE, 4));
        blueBus.getImportItems().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));

        logic.trySearchNewRecipePublic();
        CrossRecipeParallelScheduler scheduler = logic.getCrossRecipeScheduler();
        assertTrue(scheduler == null || !scheduler.hasActiveSlots(),
                "Items across different color channels must NOT combine into one recipe");

        // Case 2: Iron in RED (recipe 1) and Coal in BLUE (recipe 2) -> BOTH run concurrently in scheduler!
        redBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        blueBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 2));

        logic.trySearchNewRecipePublic();
        scheduler = logic.getCrossRecipeScheduler();
        assertNotNull(scheduler);
        assertEquals(2, scheduler.getActiveSlotCount(),
                "Red and Blue channels should run concurrently in separate scheduler slots");

        // Tick until completion and verify both outputs produced
        for (int t = 0; t < 5; t++) {
            logic.updateWorkable();
        }
        assertFalse(scheduler.hasActiveSlots());
        assertEquals(2, countItem(controller.getOutputInventory(), Items.GOLD_INGOT));
        assertEquals(2, countItem(controller.getOutputInventory(), Items.DIAMOND));
    }

    @Test
    void colorChannelsIsolateDualHandlerFluidsInDistinctSchedulerMode() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        controller.setDistinct(true);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();
        logic.setParallelLimit(4);

        MetaTileEntityDualHatch redDual = new MetaTileEntityDualHatch(
                new ResourceLocation("gregtech", "test_red_dual"), 1, false);
        redDual.setPaintingColor(EnumDyeColor.RED, null);
        redDual.getImportFluids().fill(new FluidStack(FluidRegistry.WATER, 500), true);

        MetaTileEntityItemBus blueBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_blue_bus"), 1, false);
        blueBus.setPaintingColor(EnumDyeColor.BLUE, null);
        blueBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));

        controller.addPart(redDual);
        controller.addPart(blueBus);

        // Blue item bus has stick, Red dual hatch has water -> MUST NOT mix in distinct color-channel mode!
        logic.trySearchNewRecipePublic();
        CrossRecipeParallelScheduler scheduler = logic.getCrossRecipeScheduler();
        assertTrue(scheduler == null || !scheduler.hasActiveSlots(),
                "Blue bus must not borrow fluid from Red DualHandler");

        // Now give Red dual hatch a stick -> Red runs recipe 4!
        redDual.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));
        logic.clearInvalidatedInputs();
        logic.trySearchNewRecipePublic();
        scheduler = logic.getCrossRecipeScheduler();
        assertNotNull(scheduler);
        assertEquals(1, scheduler.getActiveSlotCount());
    }

    @Test
    void setPaintingColorOnPartDoesNotInterruptRunningRecipe() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();
        logic.setAllowOverclocking(false);

        MetaTileEntityItemBus importBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_paint_import"), 1, false);
        MetaTileEntityItemBus exportBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_paint_export"), 1, true);

        importBus.setPaintingColor(EnumDyeColor.RED, null);
        importBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        controller.addPart(importBus);
        controller.addPart(exportBus);

        logic.trySearchNewRecipePublic();
        assertTrue(logic.isActive());
        assertEquals(1, logic.getProgress());

        // Advance progress to 3 ticks (out of 4)
        logic.updateWorkable();
        logic.updateWorkable();
        assertEquals(3, logic.getProgress());

        // Paint export bus -> must NOT interrupt recipe OR reset lastColorChannel
        exportBus.setPaintingColor(EnumDyeColor.BLUE.colorValue, EnumFacing.NORTH);
        assertEquals(3, logic.getProgress());
        assertEquals(EnumDyeColor.RED.getMetadata(), logic.getLastColorChannel());

        // Paint import bus while recipe is running -> must NOT reset progressTime to 0 or 1!
        logic.setInvalidInputsForRecipes(true);
        importBus.setPaintingColor(EnumDyeColor.YELLOW.colorValue, EnumFacing.NORTH);
        assertEquals(3, logic.getProgress(), "Painting import hatch must not interrupt active progressTime");
        assertTrue(logic.isActive());
        assertFalse(logic.isInvalidInputsForRecipes(), "onHatchColorChanged must clear stale input cache");
        assertEquals(MultiblockRecipeLogic.NO_CHANNEL, logic.getLastColorChannel());

        // Recipe completes cleanly on remaining ticks without deadlocking at progressTime = 1
        logic.updateWorkable();
        logic.updateWorkable();
        assertEquals(0, logic.getProgress());
        assertEquals(1, countItem(controller.getOutputInventory(), Items.GOLD_INGOT));
    }

    @Test
    void colorChannelsDeduplicatePublicBusAndAllowConcurrentPaintedChannels() {
        // 1. Combined mode: uncolored public bus holding 2x iron with 2 painted channels & per-slot cap 1
        TestController combinedCtrl = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic combinedLogic = combinedCtrl.getTestLogic();
        combinedLogic.setAllowOverclocking(false);
        combinedLogic.setThreadLimit(4);
        combinedLogic.setParallelLimit(1); // threaded: maxSlots=4, perSlotParallelCap=1, totalParallel=4

        MetaTileEntityItemBus cRed = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dedup_comb_red"), 1, false);
        cRed.setPaintingColor(EnumDyeColor.RED, null);
        MetaTileEntityItemBus cBlue = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dedup_comb_blue"), 1, false);
        cBlue.setPaintingColor(EnumDyeColor.BLUE, null);
        MetaTileEntityItemBus cPublic = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dedup_comb_public"), 1, false);
        cPublic.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));

        combinedCtrl.addPart(cRed);
        combinedCtrl.addPart(cBlue);
        combinedCtrl.addPart(cPublic);

        combinedLogic.trySearchNewRecipePublic();
        CrossRecipeParallelScheduler combinedSched = combinedLogic.getCrossRecipeScheduler();
        assertNotNull(combinedSched);
        assertEquals(1, combinedSched.getActiveSlotCount(),
                "Combined mode must allocate public recipe only once across multiple painted channels");
        assertEquals(1, cPublic.getImportItems().getStackInSlot(0).getCount());

        // Same setup with separate painted inputs for the SAME recipe -> both channels allocate concurrently
        combinedLogic.invalidate();
        cPublic.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
        cRed.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        cBlue.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        combinedLogic.trySearchNewRecipePublic();
        assertEquals(2, combinedSched.getActiveSlotCount(),
                "Combined mode must still allow two painted channels with own inputs to run same recipe");

        // 2. Distinct mode: uncolored public bus holding 2x iron with 2 painted channels & per-slot cap 1
        TestController distinctCtrl = new TestController(TEST_RECIPE_MAP);
        distinctCtrl.setDistinct(true);
        TestMultiblockRecipeLogic distinctLogic = distinctCtrl.getTestLogic();
        distinctLogic.setAllowOverclocking(false);
        distinctLogic.setThreadLimit(4);
        distinctLogic.setParallelLimit(1);

        MetaTileEntityItemBus dRed = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dedup_dist_red"), 1, false);
        dRed.setPaintingColor(EnumDyeColor.RED, null);
        MetaTileEntityItemBus dBlue = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dedup_dist_blue"), 1, false);
        dBlue.setPaintingColor(EnumDyeColor.BLUE, null);
        MetaTileEntityItemBus dPublic = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_dedup_dist_public"), 1, false);
        dPublic.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));

        distinctCtrl.addPart(dRed);
        distinctCtrl.addPart(dBlue);
        distinctCtrl.addPart(dPublic);

        distinctLogic.trySearchNewRecipePublic();
        CrossRecipeParallelScheduler distinctSched = distinctLogic.getCrossRecipeScheduler();
        assertNotNull(distinctSched);
        assertEquals(1, distinctSched.getActiveSlotCount(),
                "Distinct mode must allocate uncolored public bus only once across multiple painted channels");
        assertEquals(1, dPublic.getImportItems().getStackInSlot(0).getCount());

        // Same distinct setup with separate painted inputs for the SAME recipe -> both channels allocate concurrently
        distinctLogic.invalidate();
        dPublic.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
        dRed.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        dBlue.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        distinctLogic.clearInvalidatedInputs();
        distinctLogic.trySearchNewRecipePublic();
        assertEquals(2, distinctSched.getActiveSlotCount(),
                "Distinct mode must still allow two painted channels with own inputs to run same recipe");
    }

    @Test
    void distinctColorChannelsDoNotPrematurelyInvalidatePublicBus() {
        // Case 1: Distinct + scheduler mode (parallelLimit = 4).
        // Public bus has STICK; empty WHITE fluid hatch (channel 0, scanned first) fails,
        // RED fluid hatch (channel 14) has WATER -> must still match and start 1 slot.
        TestController schedCtrl = new TestController(TEST_RECIPE_MAP);
        schedCtrl.setDistinct(true);
        TestMultiblockRecipeLogic schedLogic = schedCtrl.getTestLogic();
        schedLogic.setAllowOverclocking(false);
        schedLogic.setParallelLimit(4);

        MetaTileEntityItemBus schedPublicBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_pub_inval_sched_bus"), 1, false);
        schedPublicBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));

        MetaTileEntityFluidHatch schedWhiteFluid = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_pub_inval_sched_white"), 1, false);
        schedWhiteFluid.setPaintingColor(EnumDyeColor.WHITE, null); // channel 0 (empty)

        MetaTileEntityFluidHatch schedRedFluid = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_pub_inval_sched_red"), 1, false);
        schedRedFluid.setPaintingColor(EnumDyeColor.RED, null); // channel 14 (has water)
        schedRedFluid.getImportFluids().fill(new FluidStack(FluidRegistry.WATER, 500), true);

        schedCtrl.addPart(schedPublicBus);
        schedCtrl.addPart(schedWhiteFluid);
        schedCtrl.addPart(schedRedFluid);
        assertTrue(schedLogic.shouldUseDistinctInputBuses(), "Test fixture must actually enter distinct bus mode");

        schedLogic.trySearchNewRecipePublic();
        CrossRecipeParallelScheduler sched = schedLogic.getCrossRecipeScheduler();
        assertNotNull(sched);
        assertEquals(1, sched.getActiveSlotCount(),
                "Empty WHITE channel must not blacklist public bus before RED channel is checked");
        IItemHandlerModifiable schedRegisteredBus = schedCtrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
        assertFalse(schedLogic.isBusInvalidated(schedRegisteredBus),
                "Matched public bus must not be invalidated");

        // Case 2: Distinct + single-recipe mode (parallelLimit = 1).
        TestController singleCtrl = new TestController(TEST_RECIPE_MAP);
        singleCtrl.setDistinct(true);
        TestMultiblockRecipeLogic singleLogic = singleCtrl.getTestLogic();
        singleLogic.setAllowOverclocking(false);
        singleLogic.setParallelLimit(1);

        MetaTileEntityItemBus singlePublicBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_pub_inval_single_bus"), 1, false);
        singlePublicBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));

        MetaTileEntityFluidHatch singleWhiteFluid = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_pub_inval_single_white"), 1, false);
        singleWhiteFluid.setPaintingColor(EnumDyeColor.WHITE, null);

        MetaTileEntityFluidHatch singleRedFluid = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_pub_inval_single_red"), 1, false);
        singleRedFluid.setPaintingColor(EnumDyeColor.RED, null);
        singleRedFluid.getImportFluids().fill(new FluidStack(FluidRegistry.WATER, 500), true);

        singleCtrl.addPart(singlePublicBus);
        singleCtrl.addPart(singleWhiteFluid);
        singleCtrl.addPart(singleRedFluid);

        singleLogic.trySearchNewRecipePublic();
        assertTrue(singleLogic.isActive(),
                "Non-parallel distinct color search must reach RED channel after empty WHITE channel fails");
        assertEquals(EnumDyeColor.RED.getMetadata(), singleLogic.getLastColorChannel());
        IItemHandlerModifiable singleRegisteredBus = singleCtrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
        assertFalse(singleLogic.isBusInvalidated(singleRegisteredBus));

        // Case 3: When ALL channels fail (no water anywhere), public bus IS invalidated after full scan.
        TestController failCtrl = new TestController(TEST_RECIPE_MAP);
        failCtrl.setDistinct(true);
        TestMultiblockRecipeLogic failLogic = failCtrl.getTestLogic();
        failLogic.setParallelLimit(4);

        MetaTileEntityItemBus failPublicBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_pub_inval_fail_bus"), 1, false);
        failPublicBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));
        MetaTileEntityFluidHatch failWhiteFluid = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_pub_inval_fail_white"), 1, false);
        failWhiteFluid.setPaintingColor(EnumDyeColor.WHITE, null);
        MetaTileEntityFluidHatch failRedFluid = new MetaTileEntityFluidHatch(
                new ResourceLocation("gregtech", "test_pub_inval_fail_red"), 1, false);
        failRedFluid.setPaintingColor(EnumDyeColor.RED, null);

        failCtrl.addPart(failPublicBus);
        failCtrl.addPart(failWhiteFluid);
        failCtrl.addPart(failRedFluid);

        failLogic.trySearchNewRecipePublic();
        IItemHandlerModifiable failRegisteredBus = failCtrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
        assertTrue(failLogic.isBusInvalidated(failRegisteredBus),
                "Public bus should be invalidated once all color channels fail");
    }

    @Test
    void paintedChannelConditionRecoveryAutoStartsWithoutInputTouch() {
        boolean[] conditionReady = { false };
        // 1. Single-slot painted channel: items arrive before condition is ready
        TestController singleCtrl = new TestController(TEST_RECIPE_MAP);
        TestMultiblockRecipeLogic singleLogic = singleCtrl.getTestLogic();
        singleLogic.setAllowOverclocking(false);
        singleLogic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public String getConditionId() {
                return "test:gate";
            }

            @Override
            public boolean canStart(Recipe recipe, IItemHandlerModifiable inv, IMultipleTankHandler fluids) {
                if (!conditionReady[0]) {
                    singleLogic.setWhyFailed("等待外部条件就绪");
                    return false;
                }
                return true;
            }
        });

        MetaTileEntityItemBus redBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_cond_red_single"), 1, false);
        redBus.setPaintingColor(EnumDyeColor.RED, null);
        redBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        singleCtrl.addPart(redBus);

        singleLogic.updateWorkable();
        assertFalse(singleLogic.isActive());
        assertFalse(singleLogic.isInvalidInputsForRecipes(), "Condition rejection must not mark valid inputs invalid");
        assertEquals("等待外部条件就绪", singleLogic.getWhyFailed());
        assertEquals(1, countItem(redBus.getImportItems(), Items.IRON_INGOT));

        conditionReady[0] = true;
        singleLogic.updateWorkable();
        assertTrue(singleLogic.isActive(), "Painted channel must auto-start once condition becomes satisfied");
        assertEquals(0, countItem(redBus.getImportItems(), Items.IRON_INGOT));

        // 2. Distinct parallel scheduler painted channel: bus must not be added to invalidatedInputList on condition miss
        conditionReady[0] = false;
        TestController schedCtrl = new TestController(TEST_RECIPE_MAP);
        schedCtrl.setDistinct(true);
        TestMultiblockRecipeLogic schedLogic = schedCtrl.getTestLogic();
        schedLogic.setAllowOverclocking(false);
        schedLogic.setParallelLimit(4);
        schedLogic.addRecipeCondition(new IRecipeCondition() {

            @Override
            public String getConditionId() {
                return "test:sched_gate";
            }

            @Override
            public boolean canStart(Recipe recipe, IItemHandlerModifiable inv, IMultipleTankHandler fluids) {
                return conditionReady[0];
            }
        });

        MetaTileEntityItemBus schedRedBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_cond_red_sched"), 1, false);
        schedRedBus.setPaintingColor(EnumDyeColor.RED, null);
        schedRedBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        schedCtrl.addPart(schedRedBus);

        schedLogic.updateWorkable();
        IItemHandlerModifiable registeredBus = schedCtrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
        assertFalse(schedLogic.isBusInvalidated(registeredBus));

        conditionReady[0] = true;
        schedLogic.updateWorkable();
        assertNotNull(schedLogic.getCrossRecipeScheduler());
        assertEquals(1, schedLogic.getCrossRecipeScheduler().getActiveSlotCount());
        assertEquals(0, countItem(schedRedBus.getImportItems(), Items.IRON_INGOT));
    }

    @Test
    void notifyConditionInputChangedClearsInvalidationWithoutResettingColorRotation() {
        TestController controller = new TestController(TEST_RECIPE_MAP);
        controller.setDistinct(true);
        TestMultiblockRecipeLogic logic = controller.getTestLogic();

        MetaTileEntityItemBus whiteBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_notify_white"), 1, false);
        whiteBus.setPaintingColor(EnumDyeColor.WHITE, null);
        whiteBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 1));
        controller.addPart(whiteBus);

        logic.setLastColorChannel(EnumDyeColor.WHITE.getMetadata());
        logic.setInvalidInputsForRecipes(true);
        logic.trySearchNewRecipePublic();
        IItemHandlerModifiable registeredBus = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
        assertTrue(logic.isBusInvalidated(registeredBus));

        logic.notifyConditionInputChanged();
        assertFalse(logic.isInvalidInputsForRecipes());
        assertFalse(logic.isBusInvalidated(registeredBus));
        assertEquals(EnumDyeColor.WHITE.getMetadata(), logic.getLastColorChannel(),
                "notifyConditionInputChanged must preserve lastColorChannel rotation");
    }

    @Test
    void distinctPublicBusPairedWithMultiColorFluidsRetriesWhenConditionRecovers() {
        for (int parallelLimit : new int[] { 1, 4 }) {
            boolean[] conditionReady = { false };
            TestController ctrl = new TestController(TEST_RECIPE_MAP);
            ctrl.setDistinct(true);
            TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(parallelLimit);
            logic.addRecipeCondition(new IRecipeCondition() {

                @Override
                public String getConditionId() {
                    return "test:pub_channel_gate";
                }

                @Override
                public boolean canStart(Recipe recipe, IItemHandlerModifiable inv, IMultipleTankHandler fluids) {
                    return conditionReady[0];
                }
            });

            MetaTileEntityItemBus publicBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_pub_retry_bus_" + parallelLimit), 1, false);
            publicBus.getImportItems().setStackInSlot(0, new ItemStack(Items.STICK, 2));
            MetaTileEntityFluidHatch whiteFluid = new MetaTileEntityFluidHatch(
                    new ResourceLocation("gregtech", "test_pub_retry_white_" + parallelLimit), 1, false);
            whiteFluid.setPaintingColor(EnumDyeColor.WHITE, null);
            MetaTileEntityFluidHatch redFluid = new MetaTileEntityFluidHatch(
                    new ResourceLocation("gregtech", "test_pub_retry_red_" + parallelLimit), 1, false);
            redFluid.setPaintingColor(EnumDyeColor.RED, null);
            redFluid.getImportFluids().getTankAt(0).fill(new FluidStack(FluidRegistry.WATER, 500), true);

            ctrl.addPart(publicBus);
            ctrl.addPart(whiteFluid);
            ctrl.addPart(redFluid);
            ctrl.getNotifiedItemInputList().clear();
            ctrl.getNotifiedFluidInputList().clear();

            // WHITE has no fluid, RED matches stick+water but condition is false -> publicBus must NOT be invalidated
            logic.updateWorkable();
            IItemHandlerModifiable registeredBus = ctrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
            assertFalse(logic.isBusInvalidated(registeredBus));

            // Flip condition without touching any hatch -> starts automatically and consumes expected sticks & water
            conditionReady[0] = true;
            logic.updateWorkable();
            assertTrue(logic.isActive());
            int expectedRemainingSticks = parallelLimit == 1 ? 1 : 0;
            int expectedRemainingWater = parallelLimit == 1 ? 400 : 300;
            assertEquals(expectedRemainingSticks, countItem(publicBus.getImportItems(), Items.STICK));
            assertEquals(expectedRemainingWater, redFluid.getImportFluids().getTankAt(0).getFluidAmount());
        }
    }

    private static List<Object> abilityOf(IMultiblockAbilityPart<?> part, MultiblockAbility<?> ability) {
        AbilityInstances instances = new AbilityInstances(ability);
        part.registerAbilities(instances);
        List<Object> list = new ArrayList<>();
        for (Object obj : instances) {
            list.add(obj);
        }
        return list;
    }

    private static int countItem(IItemHandlerModifiable inv, net.minecraft.item.Item item) {
        int total = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) {
                total += s.getCount();
            }
        }
        return total;
    }

    // ==================== Headless Controller & Logic Test Fixture ====================

    private static final class TestController extends RecipeMapMultiblockController {

        private final List<IMultiblockPart> parts = new ArrayList<>();
        private final Map<MultiblockAbility<?>, List<Object>> abilities = new HashMap<>();
        private final ItemStackHandler exportInv = new ItemStackHandler(8);
        private final FluidTankList exportFluidsList = new FluidTankList(false);
        private final TestEnergyContainer energy = new TestEnergyContainer(100_000L, 100_000L, 128L);

        TestController(RecipeMap<?> recipeMap) {
            super(new ResourceLocation("gregtech", "test_color_controller"), recipeMap);
            new TestMetaTileEntityHolder(this);
            this.recipeMapWorkable = new TestMultiblockRecipeLogic(this);
        }

        void addPart(MetaTileEntity partMte) {
            new TestMetaTileEntityHolder(partMte);
            if (partMte instanceof IMultiblockPart part) {
                parts.add(part);
                part.addToMultiBlock(this);
            }
            if (partMte instanceof IMultiblockAbilityPart<?> abilityPart) {
                for (MultiblockAbility<?> ability : abilityPart.getAbilities()) {
                    AbilityInstances instances = new AbilityInstances(ability);
                    abilityPart.registerAbilities(instances);
                    List<Object> list = abilities.computeIfAbsent(ability, k -> new ArrayList<>());
                    for (Object inst : instances) {
                        list.add(inst);
                    }
                }
            }
        }

        TestMultiblockRecipeLogic getTestLogic() {
            return (TestMultiblockRecipeLogic) recipeMapWorkable;
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
            return Collections.unmodifiableList(parts);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            List<Object> list = abilities.get(ability);
            return list == null ? Collections.emptyList() : (List<T>) list;
        }

        @Override
        public IEnergyContainer getEnergyContainer() {
            return energy;
        }

        @Override
        public boolean canBeDistinct() {
            return true;
        }

        @Override
        public IItemHandlerModifiable getInputInventory() {
            return new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        }

        @Override
        public IItemHandlerModifiable getOutputInventory() {
            return exportInv;
        }

        @Override
        public IMultipleTankHandler getOutputFluidInventory() {
            return exportFluidsList;
        }

        @Override
        public IMultipleTankHandler getInputFluidInventory() {
            List<IFluidTank> tanks = getAbilities(MultiblockAbility.IMPORT_FLUIDS);
            return new FluidTankList(false, tanks);
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
        public MetaTileEntity createMetaTileEntity(gregtech.api.metatileentity.interfaces.IGregTechTileEntity te) {
            return new TestController(recipeMap);
        }

        @Override
        public boolean hasMaintenanceMechanics() {
            return false;
        }
    }

    private static final class TestMultiblockRecipeLogic extends MultiblockRecipeLogic {

        private boolean crossRecipeEnabled = false;

        TestMultiblockRecipeLogic(RecipeMapMultiblockController controller) {
            super(controller);
        }

        void setCrossRecipeParallelEnabled(boolean enabled) {
            this.crossRecipeEnabled = enabled;
        }

        @Override
        public boolean isCrossRecipeParallelEnabled() {
            return crossRecipeEnabled;
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
        public long getTotalPowerBudget() {
            return 1024L;
        }

        @Override
        protected boolean hasEnoughPower(long eut, int duration) {
            return true;
        }

        void trySearchNewRecipePublic() {
            trySearchNewRecipe();
        }

        int[] getChannelOrderPublic(short mask) {
            return getChannelOrder(mask);
        }

        short getHatchColorsMaskPublic() {
            return getHatchColorsMask();
        }

        MetaTileEntity ownerOfPublic(Object handler) {
            return ownerOf(handler);
        }

        int colorIndexOfPublic(Object handler) {
            return colorIndexOf(handler);
        }

        int getLastColorChannel() {
            return lastColorChannel;
        }

        void setLastColorChannel(int ch) {
            this.lastColorChannel = ch;
        }

        void setInvalidInputsForRecipes(boolean invalid) {
            this.invalidInputsForRecipes = invalid;
        }

        boolean isInvalidInputsForRecipes() {
            return invalidInputsForRecipes;
        }

        void clearInvalidatedInputs() {
            invalidatedInputList.clear();
        }

        boolean isBusInvalidated(IItemHandlerModifiable bus) {
            return invalidatedInputList.contains(bus);
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
