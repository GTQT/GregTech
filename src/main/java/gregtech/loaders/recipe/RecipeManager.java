package gregtech.loaders.recipe;
import gregtech.loaders.recipe.handlers.GeneralCircuitHandler;
import gregtech.loaders.recipe.handlers.FakeToolRecipes;
import gregtech.loaders.recipe.handlers.HatchHandlers;
import gregtech.loaders.recipe.handlers.LoomRecipes;
import gregtech.loaders.recipe.handlers.OnceToolHandler;
import gregtech.loaders.recipe.handlers.ProgrammableCircuit;

public class RecipeManager {
    public static void register() {
        HatchHandlers.init();
        ProgrammableCircuit.init();
        FakeToolRecipes.register();
        GeneralCircuitHandler.init();
        OnceToolHandler.register();
        LoomRecipes.init();
    }
}
