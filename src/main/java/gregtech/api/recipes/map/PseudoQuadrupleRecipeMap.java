package gregtech.api.recipes.map;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUIFunction;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PseudoQuadrupleRecipeMap<R extends RecipeBuilder<R>>
        extends PseudoGroupRecipeMapBase<R> {

    protected final RecipeMap<R> firstRecipeMap;
    protected final RecipeMap<R> secondRecipeMap;
    protected final RecipeMap<R> thirdRecipeMap;
    protected final RecipeMap<R> fourthRecipeMap;

    public PseudoQuadrupleRecipeMap(String unlocalizedName,
                                    R defaultRecipeBuilder,
                                    RecipeMapUIFunction recipeMapUI,
                                    int maxInputs,
                                    int maxOutputs,
                                    int maxFluidInputs,
                                    int maxFluidOutputs,
                                    RecipeMap<R> firstRecipeMap,
                                    RecipeMap<R> secondRecipeMap,
                                    RecipeMap<R> thirdRecipeMap,
                                    RecipeMap<R> fourthRecipeMap) {
        super(unlocalizedName, defaultRecipeBuilder, recipeMapUI, maxInputs, maxOutputs, maxFluidInputs, maxFluidOutputs);
        this.firstRecipeMap = firstRecipeMap;
        this.secondRecipeMap = secondRecipeMap;
        this.thirdRecipeMap = thirdRecipeMap;
        this.fourthRecipeMap = fourthRecipeMap;
    }

    @Override
    public Recipe findRecipe(long voltage,
                             List<ItemStack> inputs,
                             List<FluidStack> fluidInputs,
                             boolean exactVoltage) {
        List<ItemStack> inputItems = inputs.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<FluidStack> inputFluids = fluidInputs.stream()
                .filter(Objects::nonNull)
                .filter(fluidStack -> fluidStack.amount != 0)
                .collect(Collectors.toList());

        int circuitValue = getCircuitValue(inputs, 3);

        switch (circuitValue) {
            case MIN_CIRCUIT_VALUE:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, firstRecipeMap);
            case MIN_CIRCUIT_VALUE + 1:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, secondRecipeMap);
            case MIN_CIRCUIT_VALUE + 2:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, thirdRecipeMap);
            case MIN_CIRCUIT_VALUE + 3:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, fourthRecipeMap);
            default:
                return null;
        }
    }
}
