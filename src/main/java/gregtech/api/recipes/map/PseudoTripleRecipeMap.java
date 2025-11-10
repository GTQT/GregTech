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

public class PseudoTripleRecipeMap<R extends RecipeBuilder<R>>
        extends PseudoGroupRecipeMapBase<R> {

    protected final RecipeMap<R> leftRecipeMap;
    protected final RecipeMap<R> middleRecipeMap;
    protected final RecipeMap<R> rightRecipeMap;

    public PseudoTripleRecipeMap(String unlocalizedName,
                                 R defaultRecipeBuilder,
                                 RecipeMapUIFunction recipeMapUI,
                                 int maxInputs,
                                 int maxOutputs,
                                 int maxFluidInputs,
                                 int maxFluidOutputs,
                                 RecipeMap<R> leftRecipeMap,
                                 RecipeMap<R> middleRecipeMap,
                                 RecipeMap<R> rightRecipeMap) {
        super(unlocalizedName, defaultRecipeBuilder, recipeMapUI, maxInputs, maxOutputs, maxFluidInputs, maxFluidOutputs);
        this.leftRecipeMap = leftRecipeMap;
        this.middleRecipeMap = middleRecipeMap;
        this.rightRecipeMap = rightRecipeMap;
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

        int circuitValue = getCircuitValue(inputs, 2);

        switch (circuitValue) {
            case MIN_CIRCUIT_VALUE:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, leftRecipeMap);
            case MIN_CIRCUIT_VALUE + 1:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, middleRecipeMap);
            case MIN_CIRCUIT_VALUE + 2:
                return getRecipe(voltage, exactVoltage, inputs, fluidInputs, inputItems, inputFluids, rightRecipeMap);
            default:
                return null;
        }
    }
}
