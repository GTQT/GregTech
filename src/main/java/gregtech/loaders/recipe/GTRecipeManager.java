package gregtech.loaders.recipe;

import gregtech.api.event.MaterialInfoEvent;
import gregtech.common.crafting.DyeableRecipes;
import gregtech.loaders.recipe.handlers.DecompositionRecipeHandler;
import gregtech.loaders.recipe.handlers.FissionReactorRecipeHandler;
import gregtech.loaders.recipe.handlers.FluidRecipeHandler;
import gregtech.loaders.recipe.handlers.NuclearReactorRecipeHandler;
import gregtech.loaders.recipe.handlers.RecipeHandlerList;
import gregtech.loaders.recipe.handlers.ToolRecipeHandler;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class GTRecipeManager {

    private GTRecipeManager() {/**/}

    public static void preLoad() {
        ToolRecipeHandler.initializeMetaItems();
    }

    public static void load() {
        MachineRecipeLoader.init();
        CraftingRecipeLoader.init();
        MetaTileEntityLoader.init();
        MetaTileEntityMachineRecipeLoader.init();
        GodforgeRecipeLoader.init();
        RecipeHandlerList.register();
        ForgeRegistries.RECIPES.register(new DyeableRecipes());
        NuclearMachineRecipeLoader.init();
        NuclearRecipes.init();
        FissionReactorRecipeHandler.register();
        DecayChamberRecipeLoader.load();
        FluidRecipeHandler.processCooling();
        NuclearReactorRecipeHandler.init();
        MultiblockLoader.init();
        ManualABSRecipes.register();
    }

    public static void loadLatest() {
        MinecraftForge.EVENT_BUS.post(new MaterialInfoEvent());
        DecompositionRecipeHandler.runRecipeGeneration();
        RecyclingRecipes.init();

        FluidRecipeHandler.runRecipeGeneration();
    }
}
