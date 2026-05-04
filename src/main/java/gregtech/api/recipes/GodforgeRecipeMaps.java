package gregtech.api.recipes;

import gregtech.api.gui.GuiTextures;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenProperty;

@ZenClass("mods.gregtech.recipe.GodforgeRecipeMaps")
@ZenRegister
public final class GodforgeRecipeMaps {

    @ZenProperty
    public static final RecipeMap<SimpleRecipeBuilder> GODFORGE_SMELTING_RECIPES = new RecipeMapBuilder<>(
            "godforge_smelting",
            new SimpleRecipeBuilder())
            .itemInputs(1)
            .itemOutputs(1)
            .fluidInputs(0)
            .fluidOutputs(1)
            .uiBuilder(builder -> builder
                    .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW))
            .build();

    @ZenProperty
    public static final RecipeMap<SimpleRecipeBuilder> GODFORGE_PLASMA_RECIPES = new RecipeMapBuilder<>(
            "godforge_plasma",
            new SimpleRecipeBuilder())
            .itemInputs(1)
            .itemOutputs(1)
            .fluidInputs(1)
            .fluidOutputs(1)
            .uiBuilder(builder -> builder
                    .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW))
            .build();

    @ZenProperty
    public static final RecipeMap<SimpleRecipeBuilder> GODFORGE_EXOTIC_MATTER_RECIPES = new RecipeMapBuilder<>(
            "godforge_exotic",
            new SimpleRecipeBuilder())
            .itemInputs(1)
            .itemOutputs(1)
            .fluidInputs(2)
            .fluidOutputs(1)
            .uiBuilder(builder -> builder
                    .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW))
            .build();

    @ZenProperty
    public static final RecipeMap<SimpleRecipeBuilder> GODFORGE_MOLTEN_RECIPES = new RecipeMapBuilder<>(
            "godforge_molten",
            new SimpleRecipeBuilder())
            .itemInputs(6)
            .itemOutputs(6)
            .fluidInputs(1)
            .fluidOutputs(2)
            .uiBuilder(builder -> builder
                    .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW))
            .build();

    @ZenProperty
    public static final RecipeMap<SimpleRecipeBuilder> GODFORGE_UPGRADE_COST_RECIPES = new RecipeMapBuilder<>(
            "godforge_upgrade_costs",
            new SimpleRecipeBuilder())
            .itemInputs(12)
            .itemOutputs(2)
            .fluidInputs(0)
            .fluidOutputs(2)
            .uiBuilder(builder -> builder
                    .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW))
            .build();

    private GodforgeRecipeMaps() {}
}
