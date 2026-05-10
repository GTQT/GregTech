package gregtech.api.recipes.map;

import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapUI;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PseudoGroupRecipeMapBuilder<B extends RecipeBuilder<B>> {
    private final String unlocalizedName;
    private final B defaultRecipeBuilder;

    private RecipeMap<B>[] recipeMaps = new RecipeMap[0];
    private int itemInputs = 0;
    private int itemOutputs = 0;
    private int fluidInputs = 0;
    private int fluidOutputs = 0;

    public PseudoGroupRecipeMapBuilder(String unlocalizedName, B defaultRecipeBuilder) {
        this.unlocalizedName = unlocalizedName;
        this.defaultRecipeBuilder = defaultRecipeBuilder;
    }

    public PseudoGroupRecipeMapBuilder<B> group(RecipeMap<B>[] recipeMaps) {
        this.recipeMaps = recipeMaps;
        return this;
    }

    public PseudoGroupRecipeMapBuilder<B> itemInputs(int itemInputs) {
        this.itemInputs = itemInputs;
        return this;
    }

    public PseudoGroupRecipeMapBuilder<B> itemOutputs(int itemOutputs) {
        this.itemOutputs = itemOutputs;
        return this;
    }

    public PseudoGroupRecipeMapBuilder<B> fluidInputs(int fluidInputs) {
        this.fluidInputs = fluidInputs;
        return this;
    }

    public PseudoGroupRecipeMapBuilder<B> fluidOutputs(int fluidOutputs) {
        this.fluidOutputs = fluidOutputs;
        return this;
    }

    @NotNull
    public RecipeMap<B> build() {
        RecipeMap<B> actualRecipeMap = null;

        switch (recipeMaps.length) {
            case 2:
                actualRecipeMap = new PseudoPairRecipeMap<>(
                        unlocalizedName, defaultRecipeBuilder, this::buildUI,
                        itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                        recipeMaps[0], recipeMaps[1]
                );
                break;
            case 3:
                actualRecipeMap = new PseudoTripleRecipeMap<>(
                        unlocalizedName, defaultRecipeBuilder, this::buildUI,
                        itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                        recipeMaps[0], recipeMaps[1], recipeMaps[2]
                );
                break;
            case 4:
                actualRecipeMap = new PseudoQuadrupleRecipeMap<>(
                        unlocalizedName, defaultRecipeBuilder, this::buildUI,
                        itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                        recipeMaps[0], recipeMaps[1], recipeMaps[2], recipeMaps[3]
                );
                break;
            default:
                break;
        }

        Objects.requireNonNull(actualRecipeMap, "Recipe map must be created with 2, 3, or 4 recipe maps");

        // Pseudo group recipe map do not generate corresponding UI page.
        actualRecipeMap.getRecipeMapUI().setJEIVisible(false);

        return actualRecipeMap;
    }

    @NotNull
    private RecipeMapUI<?> buildUI(RecipeMap<?> recipeMap) {
        return new RecipeMapUI<>(recipeMap, false, false, false, false);
    }
}
