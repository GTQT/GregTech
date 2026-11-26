package gregtech.api.capability.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TestMetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ParallelLogicType;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.multiblock.RecipeMapSteamMultiblockController;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.modules.IModuleContainer;
import gregtech.api.modules.IModuleManager;
import gregtech.api.modules.ModuleStage;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.logic.CleanroomRecipeCondition;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
import gregtech.api.recipes.map.PseudoPairRecipeMap;
import gregtech.api.recipes.map.PseudoQuadrupleRecipeMap;
import gregtech.api.recipes.map.PseudoTripleRecipeMap;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.properties.impl.CleanroomProperty;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.registry.MarkerMaterialRegistry;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockRecipeLockAndInvalidationTest {

    private static RecipeMap<SimpleRecipeBuilder> TEST_MAP;
    private static Recipe RECIPE_IRON_TO_GOLD;
    private static Recipe RECIPE_COAL_TO_DIAMOND;

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

        TEST_MAP = new RecipeMap<>("test_lock_invalid_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
        RECIPE_IRON_TO_GOLD = compileSimpleRecipe(TEST_MAP, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 16L);
        RECIPE_COAL_TO_DIAMOND = compileSimpleRecipe(TEST_MAP, Items.COAL, Items.DIAMOND, 4, 16L);
    }

    private static Recipe compileSimpleRecipe(RecipeMap<SimpleRecipeBuilder> map, Item input, Item output,
                                              int duration, long eut) {
        Recipe recipe = new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(input, 1))),
                Collections.singletonList(new ItemStack(output, 1)),
                ChancedOutputList.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                ChancedOutputList.empty(),
                Collections.singletonList(new ItemStack(Items.COAL)),
                duration,
                eut,
                false,
                new RecipePropertyStorageImpl(),
                map.getPrimaryRecipeCategory());
        map.compileRecipe(recipe);
        return recipe;
    }

    private static void fillAllSlots(IItemHandlerModifiable inventory, ItemStack stack) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, stack.copy());
        }
    }

    private static void clearAllSlots(IItemHandlerModifiable inventory) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    private static int countItem(IItemHandlerModifiable inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) {
                total += s.getCount();
            }
        }
        return total;
    }

    @Test
    void distinctBusInvalidatedByFullOutputRecoversOnOutputNotification() {
        // 1. Distinct + scheduler mode (parallelLimit = 4): full output invalidates the bus during allocation
        TestController schedCtrl = new TestController(TEST_MAP);
        schedCtrl.setDistinct(true);
        TestMultiblockRecipeLogic schedLogic = schedCtrl.getTestLogic();
        schedLogic.setAllowOverclocking(false);
        schedLogic.setParallelLimit(4);

        MetaTileEntityItemBus schedInBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_inval_sched_in"), 1, false);
        MetaTileEntityItemBus schedOutBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_inval_sched_out"), 1, true);
        schedCtrl.addPart(schedInBus);
        schedCtrl.addPart(schedOutBus);
        // Clear initial addToMultiBlock notifications so subsequent checks mirror steady-state ticks
        schedCtrl.getNotifiedItemInputList().clear();
        schedCtrl.getNotifiedItemOutputList().clear();

        schedInBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        fillAllSlots(schedOutBus.getExportItems(), new ItemStack(Items.EMERALD, 64));
        // Clear output notification from setup; keep input undisturbed
        schedCtrl.getNotifiedItemOutputList().clear();
        schedCtrl.getNotifiedItemInputList().clear();

        schedLogic.updateWorkable();
        assertFalse(schedLogic.isActive(), "Machine must not start while output bus is full");
        assertTrue(schedLogic.invalidatedInputList.contains(schedCtrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0)),
                "Full output during scheduler allocation adds distinct bus to invalidatedInputList");
        assertTrue(schedLogic.isOutputsFull);

        // Next idle tick with no output change: remains blocked
        schedLogic.updateWorkable();
        assertFalse(schedLogic.isActive());

        // Clear export bus (triggers NotifiableItemStackHandler output notification ONLY) without touching input bus
        clearAllSlots(schedOutBus.getExportItems());
        assertTrue(schedCtrl.getNotifiedItemInputList().isEmpty(), "Test requires no input notification");
        assertFalse(schedCtrl.getNotifiedItemOutputList().isEmpty(), "Clearing export bus must notify outputs");

        schedLogic.updateWorkable();
        assertTrue(schedLogic.isActive(),
                "Output notification must reset invalidatedInputList and unblock distinct recipe search");
        assertTrue(schedLogic.invalidatedInputList.isEmpty());

        // 2. Distinct + single-recipe mode (parallelLimit = 1): even if a single distinct bus was invalidated,
        //    output notification resets invalidatedInputList without needing an input change.
        TestController singleCtrl = new TestController(TEST_MAP);
        singleCtrl.setDistinct(true);
        TestMultiblockRecipeLogic singleLogic = singleCtrl.getTestLogic();
        singleLogic.setAllowOverclocking(false);
        singleLogic.setParallelLimit(1);

        MetaTileEntityItemBus singleInBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_inval_single_in"), 1, false);
        MetaTileEntityItemBus singleOutBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_inval_single_out"), 1, true);
        singleCtrl.addPart(singleInBus);
        singleCtrl.addPart(singleOutBus);
        singleCtrl.getNotifiedItemInputList().clear();
        singleCtrl.getNotifiedItemOutputList().clear();

        singleInBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        fillAllSlots(singleOutBus.getExportItems(), new ItemStack(Items.EMERALD, 64));
        singleCtrl.getNotifiedItemInputList().clear();
        singleCtrl.getNotifiedItemOutputList().clear();

        singleLogic.updateWorkable();
        assertFalse(singleLogic.isActive());
        IItemHandlerModifiable registeredSingleBus = singleCtrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
        singleLogic.invalidatedInputList.add(registeredSingleBus);

        clearAllSlots(singleOutBus.getExportItems());
        assertTrue(singleCtrl.getNotifiedItemInputList().isEmpty());

        singleLogic.updateWorkable();
        assertTrue(singleLogic.isActive(),
                "Output notification must clear invalidatedInputList in single-recipe distinct mode too");
        assertTrue(singleLogic.invalidatedInputList.isEmpty());
    }

    @Test
    void steamMultiblockResetInvalidatedInputsAndCheckPreviousRecipeDistinct() {
        TestSteamController steamCtrl = new TestSteamController(TEST_MAP);
        SteamMultiblockRecipeLogic steamLogic = steamCtrl.getSteamLogic();

        MetaTileEntityItemBus bus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_steam_in"), 1, false);
        steamCtrl.addImportBus(bus);
        IItemHandlerModifiable importBus = bus.getImportItems();

        // resetInvalidatedInputs clears both boolean flag and distinct invalidatedInputList
        steamLogic.invalidInputsForRecipes = true;
        steamLogic.invalidatedInputList.add(importBus);
        steamLogic.resetInvalidatedInputs();
        assertFalse(steamLogic.invalidInputsForRecipes);
        assertTrue(steamLogic.invalidatedInputList.isEmpty());

        // Foreign recipe not owned by TEST_MAP -> checkPreviousRecipeDistinct must return false
        RecipeMap<SimpleRecipeBuilder> foreignMap = new RecipeMap<>(
                "test_steam_foreign_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);
        Recipe foreignRecipe = compileSimpleRecipe(foreignMap, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 16L);
        importBus.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        steamLogic.previousRecipe = foreignRecipe;
        assertFalse(steamLogic.checkPreviousRecipeDistinct(importBus),
                "checkPreviousRecipeDistinct must reject recipes not owned by the current RecipeMap");

        // Recipe exceeding Steam max voltage (LV = 32 EU/t) -> must return false
        Recipe highVoltageRecipe = compileSimpleRecipe(TEST_MAP, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 128L);
        steamLogic.previousRecipe = highVoltageRecipe;
        assertFalse(steamLogic.checkPreviousRecipeDistinct(importBus),
                "checkPreviousRecipeDistinct must reject recipes exceeding Steam getMaxVoltage()");

        // Valid owned recipe within voltage and matching bus contents -> true
        steamLogic.previousRecipe = RECIPE_IRON_TO_GOLD;
        assertTrue(steamLogic.checkPreviousRecipeDistinct(importBus),
                "checkPreviousRecipeDistinct must accept owned, in-voltage matching recipe");
    }

    @Test
    void recipeLockEnforcedAcrossSingleDistinctColorChannelAndSchedulerModes() {
        // 1. Single non-distinct mode
        TestController singleCtrl = new TestController(TEST_MAP);
        TestMultiblockRecipeLogic singleLogic = singleCtrl.getTestLogic();
        singleLogic.setAllowOverclocking(false);

        MetaTileEntityItemBus singleBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_lock_single_in"), 1, false);
        singleCtrl.addPart(singleBus);
        singleBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        singleLogic.trySearchNewRecipePublic();
        assertTrue(singleLogic.isActive());
        assertSame(RECIPE_IRON_TO_GOLD, singleLogic.previousRecipe);
        for (int t = 0; t < 5; t++) singleLogic.updateWorkable();
        assertFalse(singleLogic.isActive());

        singleLogic.setRecipeLockEnable(true);
        singleBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        singleCtrl.getNotifiedItemInputList().clear();
        singleLogic.trySearchNewRecipePublic();
        assertFalse(singleLogic.isActive(), "Locked machine must refuse to start different recipe (COAL)");
        assertTrue(singleLogic.invalidInputsForRecipes);

        // Unlocking resets input invalidation cache so COAL starts cleanly on updateWorkable()
        singleLogic.setRecipeLockEnable(false);
        assertFalse(singleLogic.invalidInputsForRecipes);
        singleLogic.updateWorkable();
        assertTrue(singleLogic.isActive());
        assertSame(RECIPE_COAL_TO_DIAMOND, singleLogic.previousRecipe);

        // 2. Distinct mode (single & scheduler)
        TestController distinctCtrl = new TestController(TEST_MAP);
        distinctCtrl.setDistinct(true);
        TestMultiblockRecipeLogic distinctLogic = distinctCtrl.getTestLogic();
        distinctLogic.setAllowOverclocking(false);

        MetaTileEntityItemBus coalBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_lock_dist_coal"), 1, false);
        MetaTileEntityItemBus ironBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_lock_dist_iron"), 1, false);
        distinctCtrl.addPart(coalBus);
        distinctCtrl.addPart(ironBus);

        distinctLogic.previousRecipe = RECIPE_IRON_TO_GOLD;
        distinctLogic.setRecipeLockEnable(true);
        coalBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        distinctLogic.trySearchNewRecipePublic();
        assertFalse(distinctLogic.isActive(), "Distinct lock must not start COAL when locked to IRON");

        ironBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        distinctLogic.resetInvalidatedInputs();
        distinctLogic.trySearchNewRecipePublic();
        assertTrue(distinctLogic.isActive());
        assertSame(RECIPE_IRON_TO_GOLD, distinctLogic.getShowRecipes());
        for (int t = 0; t < 5; t++) distinctLogic.updateWorkable();

        // Scheduler distinct with locked recipe: only IRON runs even when COAL is also present
        distinctLogic.setParallelLimit(4);
        coalBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 2));
        ironBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        distinctLogic.resetInvalidatedInputs();
        distinctLogic.trySearchNewRecipePublic();
        assertEquals(2, countItem(coalBus.getImportItems(), Items.COAL),
                "Distinct scheduler lock must leave COAL untouched");
        assertEquals(0, countItem(ironBus.getImportItems(), Items.IRON_INGOT));

        // 3. Color-channel mode (non-scheduler)
        TestController colorCtrl = new TestController(TEST_MAP);
        TestMultiblockRecipeLogic colorLogic = colorCtrl.getTestLogic();
        colorLogic.setAllowOverclocking(false);

        MetaTileEntityItemBus redCoalBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_lock_color_red"), 1, false);
        redCoalBus.setPaintingColor(EnumDyeColor.RED, null);
        MetaTileEntityItemBus blueIronBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_lock_color_blue"), 1, false);
        blueIronBus.setPaintingColor(EnumDyeColor.BLUE, null);
        colorCtrl.addPart(redCoalBus);
        colorCtrl.addPart(blueIronBus);

        colorLogic.previousRecipe = RECIPE_IRON_TO_GOLD;
        colorLogic.setRecipeLockEnable(true);
        redCoalBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        colorLogic.trySearchNewRecipePublic();
        assertFalse(colorLogic.isActive(), "Color-channel lock must reject COAL in RED channel");

        blueIronBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        colorLogic.resetInvalidatedInputs();
        colorLogic.trySearchNewRecipePublic();
        assertTrue(colorLogic.isActive(), "Color-channel lock must match IRON in BLUE channel");
        assertSame(RECIPE_IRON_TO_GOLD, colorLogic.getShowRecipes());
        assertEquals(EnumDyeColor.BLUE.getMetadata(), colorLogic.getLastColorChannel());

        // 4. Cross-recipe parallel scheduler mode
        TestController schedCtrl = new TestController(TEST_MAP);
        TestMultiblockRecipeLogic schedLogic = schedCtrl.getTestLogic();
        schedLogic.setAllowOverclocking(false);
        schedLogic.setParallelLimit(8);
        schedLogic.setCrossRecipeParallelEnabled(true);

        MetaTileEntityItemBus mixedBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_lock_sched_mixed"), 1, false);
        schedCtrl.addPart(mixedBus);
        mixedBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        mixedBus.getImportItems().setStackInSlot(1, new ItemStack(Items.COAL, 2));

        schedLogic.previousRecipe = RECIPE_IRON_TO_GOLD;
        schedLogic.setRecipeLockEnable(true);
        schedLogic.trySearchNewRecipePublic();

        CrossRecipeParallelScheduler scheduler = schedLogic.getCrossRecipeScheduler();
        assertNotNull(scheduler);
        assertEquals(1, scheduler.getActiveSlotCount(),
                "Cross-recipe scheduler under recipe lock must only allocate the locked recipe");
        assertSame(RECIPE_IRON_TO_GOLD, schedLogic.lastCrossRecipe);
        assertSame(RECIPE_IRON_TO_GOLD, schedLogic.previousRecipe);

        for (int t = 0; t < 5; t++) schedLogic.updateWorkable();
        assertEquals(2, countItem(schedCtrl.getOutputInventory(), Items.GOLD_INGOT));
        assertEquals(0, countItem(schedCtrl.getOutputInventory(), Items.DIAMOND));
        assertEquals(2, countItem(mixedBus.getImportItems(), Items.COAL));
    }

    @Test
    void recipeLockAndPseudoRecipeMapsRespectMapOwnership() {
        RecipeMap<SimpleRecipeBuilder> map1 = new RecipeMap<>(
                "test_own_1", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);
        RecipeMap<SimpleRecipeBuilder> map2 = new RecipeMap<>(
                "test_own_2", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);
        RecipeMap<SimpleRecipeBuilder> map3 = new RecipeMap<>(
                "test_own_3", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);
        RecipeMap<SimpleRecipeBuilder> map4 = new RecipeMap<>(
                "test_own_4", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);
        RecipeMap<SimpleRecipeBuilder> foreignMap = new RecipeMap<>(
                "test_own_foreign", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0);

        Recipe r1 = compileSimpleRecipe(map1, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 16L);
        Recipe r2 = compileSimpleRecipe(map2, Items.COAL, Items.DIAMOND, 4, 16L);
        Recipe r3 = compileSimpleRecipe(map3, Items.STICK, Items.BLAZE_ROD, 4, 16L);
        Recipe r4 = compileSimpleRecipe(map4, Items.APPLE, Items.GOLDEN_APPLE, 4, 16L);
        Recipe rForeign = compileSimpleRecipe(foreignMap, Items.IRON_INGOT, Items.GOLD_INGOT, 4, 16L);

        PseudoPairRecipeMap<SimpleRecipeBuilder> pair = new PseudoPairRecipeMap<>(
                "test_pseudo_pair", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0, map1, map2);
        PseudoTripleRecipeMap<SimpleRecipeBuilder> triple = new PseudoTripleRecipeMap<>(
                "test_pseudo_triple", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0, map1, map2, map3);
        PseudoQuadrupleRecipeMap<SimpleRecipeBuilder> quad = new PseudoQuadrupleRecipeMap<>(
                "test_pseudo_quad", new SimpleRecipeBuilder(), r -> null, 2, 2, 0, 0, map1, map2, map3, map4);

        assertTrue(map1.ownsRecipe(r1));
        assertFalse(map1.ownsRecipe(r2));

        assertTrue(pair.ownsRecipe(r1));
        assertTrue(pair.ownsRecipe(r2));
        assertFalse(pair.ownsRecipe(r3));
        assertFalse(pair.ownsRecipe(rForeign));

        assertTrue(triple.ownsRecipe(r1));
        assertTrue(triple.ownsRecipe(r2));
        assertTrue(triple.ownsRecipe(r3));
        assertFalse(triple.ownsRecipe(r4));
        assertFalse(triple.ownsRecipe(rForeign));

        assertTrue(quad.ownsRecipe(r1));
        assertTrue(quad.ownsRecipe(r2));
        assertTrue(quad.ownsRecipe(r3));
        assertTrue(quad.ownsRecipe(r4));
        assertFalse(quad.ownsRecipe(rForeign));

        // Locking previousRecipe from map1 must NOT match when logic's active RecipeMap is switched to map2
        TestController ctrl = new TestController(map1);
        TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_map_switch_in"), 1, false);
        ctrl.addPart(inBus);
        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        logic.previousRecipe = r1;
        logic.setRecipeLockEnable(true);
        assertSame(r1, logic.matchLockedRecipe(map1, 128L, ctrl.getInputInventory(), ctrl.getInputFluidInventory()));

        logic.setRecipeMap(map2);
        assertFalse(logic.checkPreviousRecipe(),
                "checkPreviousRecipe must reject recipe owned by map1 once switched to map2");
        assertNull(logic.matchLockedRecipe(map2, 128L, ctrl.getInputInventory(), ctrl.getInputFluidInventory()),
                "matchLockedRecipe must reject locked recipe from map1 when searching map2");

        logic.trySearchNewRecipePublic();
        assertFalse(logic.isActive(),
                "Switched map must not start foreign locked recipe even with matching inputs");
    }

    @Test
    void parallelSchedulerStopsSterileCleanroomSlotWhenCleanroomBecomesUnclean() {
        boolean prevCleanMulti = ConfigHolder.machines.cleanMultiblocks;
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.cleanMultiblocks = false;
        ConfigHolder.machines.resetRecipeOnPowerLoss = true;
        try {
            RecipeMap<SimpleRecipeBuilder> cleanMap = new RecipeMap<>("test_sched_cleanroom_map",
                    new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
            RecipePropertyStorage props = new RecipePropertyStorageImpl();
            props.store(CleanroomProperty.getInstance(), CleanroomType.STERILE_CLEANROOM);
            Recipe sterileRecipe = new Recipe(
                    Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1))),
                    Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 1)),
                    ChancedOutputList.empty(), Collections.emptyList(), Collections.emptyList(),
                    ChancedOutputList.empty(), Collections.emptyList(),
                    8, 32L, false, props, cleanMap.getPrimaryRecipeCategory());
            cleanMap.compileRecipe(sterileRecipe);

            TestController ctrl = new TestController(cleanMap);
            TestCleanroom cleanroom = new TestCleanroom(CleanroomType.STERILE_CLEANROOM, true);
            ctrl.setCleanroom(cleanroom);
            TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(2);

            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_cleanroom_in"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_cleanroom_out"), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));

            logic.updateWorkable();
            assertTrue(logic.isActive());
            assertEquals(0, countItem(inBus.getImportItems(), Items.IRON_INGOT));

            // Simulate world reload (drops transient RecipeSlot.sourceRecipe, keeps extraData from CleanroomRecipeCondition)
            TestController reloadedCtrl = new TestController(cleanMap);
            TestCleanroom reloadedCleanroom = new TestCleanroom(CleanroomType.STERILE_CLEANROOM, false);
            reloadedCtrl.setCleanroom(reloadedCleanroom);
            reloadedCtrl.addPart(outBus);
            TestMultiblockRecipeLogic reloadedLogic = reloadedCtrl.getTestLogic();
            reloadedLogic.setAllowOverclocking(false);
            reloadedLogic.setParallelLimit(2);
            reloadedLogic.deserializeNBT(logic.serializeNBT());
            long energyAfterStart = reloadedCtrl.getEnergyContainer().getEnergyStored();

            // Cleanroom is unclean after reload -> CleanroomRecipeCondition halts slot with 0 EU drawn and 0 outputs
            for (int tick = 0; tick < 10; tick++) {
                reloadedLogic.updateWorkable();
            }
            assertEquals(energyAfterStart, reloadedCtrl.getEnergyContainer().getEnergyStored());
            assertEquals(0, countItem(outBus.getExportItems(), Items.GOLD_INGOT));
            assertEquals(CleanroomRecipeCondition.MISSING_CLEANROOM_REASON, reloadedLogic.getWhyFailed());

            // Legacy subclasses polling canProgressRecipe() (e.g. PressureSingleRecipeLogic):
            // 1) Idle machine with stale previousRecipe must NOT fail canProgressRecipe() or pollute whyFailed
            TestController legacyCtrl = new TestController(cleanMap);
            TestCleanroom legacyCleanroom = new TestCleanroom(CleanroomType.STERILE_CLEANROOM, false);
            legacyCtrl.setCleanroom(legacyCleanroom);
            TestMultiblockRecipeLogic legacyLogic = legacyCtrl.getTestLogic();
            legacyLogic.previousRecipe = sterileRecipe;
            assertTrue(legacyLogic.canProgressRecipe());
            assertEquals("", legacyLogic.getWhyFailed());

            // 2) Active single-slot recipe (including after NBT reload where previousRecipe == null) re-verifies via IRecipeCondition
            legacyCleanroom.clean = true;
            MetaTileEntityItemBus legacyIn = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_legacy_clean_in"), 1, false);
            legacyCtrl.addPart(legacyIn);
            legacyIn.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            legacyLogic.setAllowOverclocking(false);
            legacyLogic.setParallelLimit(1);
            assertTrue(legacyLogic.prepareRecipe(sterileRecipe));

            TestController legacyReloadedCtrl = new TestController(cleanMap);
            legacyReloadedCtrl.setCleanroom(new TestCleanroom(CleanroomType.STERILE_CLEANROOM, false));
            TestMultiblockRecipeLogic legacyReloadedLogic = legacyReloadedCtrl.getTestLogic();
            legacyReloadedLogic.deserializeNBT(legacyLogic.serializeNBT());
            assertNull(legacyReloadedLogic.previousRecipe);
            assertFalse(legacyReloadedLogic.canProgressRecipe());
            assertEquals(CleanroomRecipeCondition.MISSING_CLEANROOM_REASON, legacyReloadedLogic.getWhyFailed());
        } finally {
            ConfigHolder.machines.cleanMultiblocks = prevCleanMulti;
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void staleCanRecipeProgressDoesNotDestroyNewOrRecoveredRecipe() {
        boolean prevCleanMulti = ConfigHolder.machines.cleanMultiblocks;
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.cleanMultiblocks = false;
        ConfigHolder.machines.resetRecipeOnPowerLoss = true;
        try {
            TestController ctrl = new TestController(TEST_MAP);
            TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(1);

            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_stale_canprog_in"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_stale_canprog_out"), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);

            // Simulate stale canRecipeProgress=false left over before starting a normal recipe
            logic.setCanRecipeProgress(false);
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            assertTrue(logic.prepareRecipe(RECIPE_IRON_TO_GOLD));
            assertTrue(logic.isCanRecipeProgress());

            // Simulate stale canRecipeProgress=false mid-recipe after conditions already recovered
            logic.setCanRecipeProgress(false);
            for (int t = 0; t < 4; t++) {
                logic.updateWorkable();
            }
            assertEquals(1, countItem(outBus.getExportItems(), Items.GOLD_INGOT));
        } finally {
            ConfigHolder.machines.cleanMultiblocks = prevCleanMulti;
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    @Test
    void parallelSchedulerClearsWhyFailedWhenLastSlotResumesAndCompletesOnFinalTick() {
        TestController ctrl = new TestController(TEST_MAP);
        TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        logic.setParallelLimit(4);

        java.util.concurrent.atomic.AtomicBoolean allow = new java.util.concurrent.atomic.AtomicBoolean(true);
        logic.addRecipeCondition(new gregtech.api.recipes.logic.IRecipeCondition() {

            @Override
            public @NotNull String getConditionId() {
                return "test_final_tick_pause";
            }

            @Override
            public boolean testTick(@NotNull gregtech.api.recipes.logic.RecipeExecutionContext context) {
                return allow.get();
            }

            @Override
            public boolean handlesEnergyShortage() {
                return false;
            }

            @Override
            public @NotNull TickFailureAction onTickFailed(
                    @NotNull gregtech.api.recipes.logic.RecipeExecutionContext context, boolean energyShortage) {
                if (!energyShortage) {
                    logic.setWhyFailed("missing_special_fluid");
                }
                return TickFailureAction.PAUSE;
            }
        });

        MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_last_tick_in"), 1, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_last_tick_out"), 1, true);
        ctrl.addPart(inBus);
        ctrl.addPart(outBus);
        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        // Start 4-tick recipe (progress=1) and advance 3 ticks to final tick (progress=4)
        logic.updateWorkable();
        for (int t = 0; t < 3; t++) {
            logic.updateWorkable();
        }
        assertEquals(4, logic.getCrossRecipeScheduler().getActiveSlots().get(0).getProgressTime());

        // Pause on final tick -> whyFailed recorded
        allow.set(false);
        logic.updateWorkable();
        assertTrue(logic.isPaused());
        assertEquals("missing_special_fluid", logic.getWhyFailed());

        // Resume -> completes on that very tick (activeSlots becomes empty) and clears stale whyFailed
        allow.set(true);
        logic.updateWorkable();
        assertEquals(0, logic.getCrossRecipeScheduler().getActiveSlotCount());
        assertEquals("", logic.getWhyFailed());
        assertEquals(1, countItem(outBus.getExportItems(), Items.GOLD_INGOT));
    }

    @Test
    void parallelSchedulerPreservesProgressUnderIntermittentPowerWhenResetDisabled() {
        boolean prevReset = ConfigHolder.machines.resetRecipeOnPowerLoss;
        ConfigHolder.machines.resetRecipeOnPowerLoss = false;
        try {
            TestController ctrl = new TestController(TEST_MAP);
            TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setParallelLimit(32);

            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_intermittent_in"), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_intermittent_out"), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);
            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

            // Start recipe (progress = 1) and advance 1 tick to progress = 2
            logic.updateWorkable();
            logic.updateWorkable();
            assertEquals(2, logic.getCrossRecipeScheduler().getActiveSlots().get(0).getProgressTime());

            // 1-tick power outage must preserve slot progress at 2 and report 0 EU/t across scheduler & display views
            TestEnergyContainer energy = (TestEnergyContainer) ctrl.getEnergyContainer();
            energy.changeEnergy(-energy.getEnergyStored());
            logic.updateWorkable();
            CrossRecipeParallelScheduler scheduler = logic.getCrossRecipeScheduler();
            assertEquals(2, scheduler.getActiveSlots().get(0).getProgressTime());
            assertTrue(scheduler.getActiveSlots().get(0).isPaused());
            assertTrue(logic.isHasNotEnoughEnergy());
            assertFalse(logic.isPaused());
            assertEquals(0L, logic.getRecipeEUt());
            assertEquals(0L, logic.getInfoProviderEUt());
            assertEquals(0L, scheduler.getTotalEnergyConsumption());
            assertEquals(0L, scheduler.getMergedDisplaySlots().get(0).totalEUt);

            // Alternating 1-tick power on / 1-tick power off completes the 4-tick recipe cleanly
            for (int i = 0; i < 10 && logic.isActive(); i++) {
                if (i % 2 == 0) {
                    energy.changeEnergy(16L);
                } else {
                    energy.changeEnergy(-energy.getEnergyStored());
                }
                logic.updateWorkable();
            }
            assertEquals(1, countItem(outBus.getExportItems(), Items.GOLD_INGOT));
        } finally {
            ConfigHolder.machines.resetRecipeOnPowerLoss = prevReset;
        }
    }

    private static final class TestCleanroom implements ICleanroomProvider {

        private final CleanroomType type;
        boolean clean;

        TestCleanroom(CleanroomType type, boolean clean) {
            this.type = type;
            this.clean = clean;
        }

        @Override
        public boolean checkCleanroomType(@NotNull CleanroomType required) {
            return this.type == required;
        }

        @Override
        public void setCleanAmount(int amount) {}

        @Override
        public void adjustCleanAmount(int amount) {}

        @Override
        public boolean isClean() {
            return clean;
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
    }

    // ==================== Headless Test Doubles ====================

    private static final class TestController extends RecipeMapMultiblockController {

        private final List<IMultiblockPart> parts = new ArrayList<>();
        private final Map<MultiblockAbility<?>, List<Object>> abilities = new HashMap<>();
        private final net.minecraftforge.items.ItemStackHandler fallbackExportInv = new net.minecraftforge.items.ItemStackHandler(
                8);
        private final FluidTankList exportFluidsList = new FluidTankList(false);
        private final TestEnergyContainer energy = new TestEnergyContainer(100_000L, 100_000L, 128L);

        TestController(RecipeMap<?> recipeMap) {
            super(new ResourceLocation("gregtech", "test_lock_controller"), recipeMap);
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
            List<IItemHandlerModifiable> exports = getAbilities(MultiblockAbility.EXPORT_ITEMS);
            return exports.isEmpty() ? fallbackExportInv : new ItemHandlerList(exports);
        }

        @Override
        public IMultipleTankHandler getOutputFluidInventory() {
            return exportFluidsList;
        }

        @Override
        public IMultipleTankHandler getInputFluidInventory() {
            return new FluidTankList(false, getAbilities(MultiblockAbility.IMPORT_FLUIDS));
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

        int getLastColorChannel() {
            return lastColorChannel;
        }
    }

    private static final class TestSteamController extends RecipeMapSteamMultiblockController {

        private final List<IItemHandlerModifiable> importBuses = new ArrayList<>();
        private final FluidTankList steamTanks = new FluidTankList(true, new FluidTank(64000));
        private final FluidTankList emptyFluids = new FluidTankList(false);
        private final SteamMultiblockRecipeLogic directLogic;

        TestSteamController(RecipeMap<?> recipeMap) {
            super(new ResourceLocation("gregtech", "test_steam_controller"), recipeMap, 1.0,
                    ParallelLogicType.CROSS_RECIPE);
            new TestMetaTileEntityHolder(this);
            this.directLogic = new SteamMultiblockRecipeLogic(this, recipeMap, steamTanks, 1.0);
        }

        void addImportBus(MetaTileEntityItemBus bus) {
            new TestMetaTileEntityHolder(bus);
            importBuses.add(bus.getImportItems());
        }

        SteamMultiblockRecipeLogic getSteamLogic() {
            return directLogic;
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
        public boolean canBeDistinct() {
            return true;
        }

        @Override
        public boolean hasMaintenanceMechanics() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            if (ability == MultiblockAbility.IMPORT_ITEMS || ability == MultiblockAbility.STEAM_IMPORT_ITEMS) {
                return (List<T>) importBuses;
            }
            return Collections.emptyList();
        }

        @Override
        public IMultipleTankHandler getSteamFluidTank() {
            return steamTanks;
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
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestSteamController(recipeMap);
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
