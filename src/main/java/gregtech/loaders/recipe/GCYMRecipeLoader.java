package gregtech.loaders.recipe;

import gregtech.loaders.recipe.handlers.GCYMMaterialRecipeHandler;

public final class GCYMRecipeLoader {

    private GCYMRecipeLoader() {}

    public static void init() {
        GCYMMetaTileEntityLoader.init();
        GCYMCasingLoader.init();
        GCYMMixerRecipes.init();
        GCYMMiscRecipes.init();
        ManualABSRecipes.register();
    }
}
