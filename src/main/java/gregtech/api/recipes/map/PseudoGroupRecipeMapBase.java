package gregtech.api.recipes.map;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.recipes.ui.RecipeMapUIFunction;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public abstract class PseudoGroupRecipeMapBase<R extends RecipeBuilder<R>>
        extends RecipeMap<R> {

    /**
     * The minimum configuration value of the integrated circuit.
     */
    protected static final int MIN_CIRCUIT_VALUE = 20;

    public PseudoGroupRecipeMapBase(String unlocalizedName,
                                    R defaultRecipeBuilder,
                                    RecipeMapUIFunction recipeMapUI,
                                    int maxInputs,
                                    int maxOutputs,
                                    int maxFluidInputs,
                                    int maxFluidOutputs) {
        super(unlocalizedName, defaultRecipeBuilder, recipeMapUI, maxInputs, maxOutputs, maxFluidInputs, maxFluidOutputs);
    }

    protected Recipe getRecipe(long voltage,
                               boolean exactVoltage,
                               List<ItemStack> inputs,
                               List<FluidStack> fluidInputs,
                               List<ItemStack> inputItems,
                               List<FluidStack> inputFluids,
                               RecipeMap<R> recipeMap) {
        return recipeMap.find(inputItems, inputFluids, recipe -> {
            if (exactVoltage && recipe.getEUt() != voltage) {
                return false;
            }
            if (recipe.getEUt() > voltage) {
                return false;
            }
            return recipe.matches(false, inputs, fluidInputs);
        });
    }

    protected int getCircuitValue(List<ItemStack> inputs, int size) {
        for (ItemStack input : inputs) {
            if (IntCircuitIngredient.isIntegratedCircuit(input)) {
                // Only circuits with correct configuration will be considered.
                int num = IntCircuitIngredient.getCircuitConfiguration(input);
                if (num >= MIN_CIRCUIT_VALUE && num <= (MIN_CIRCUIT_VALUE + size)) {
                    return num;
                }
            }
        }
        return 0;
    }
}
