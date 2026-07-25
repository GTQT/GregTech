package gregtech.api.recipes;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.FuelRecipeBuilder;
import gregtech.api.recipes.builders.PrimitiveRecipeBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.core.sound.GTSoundEvents;

/**
 * Copyright (C) SymmetricDevs 2025
 * 由 MeowmelMuku 于 2025 修改。
 * 修改内容：添加靶丸。
 * 此文件遵循 GPL-3.0 许可证，详情请见项目根目录的 LICENSE 文件。
 */
public class SCRecipeMaps {

    public static final RecipeMap<PrimitiveRecipeBuilder> HEAT_EXCHANGER_RECIPES;
    public static final RecipeMap<PrimitiveRecipeBuilder> NATURAL_DRAFT_COOLING_TOWER;

    public static final RecipeMap<SimpleRecipeBuilder> SPENT_FUEL_POOL_RECIPES;
    public static final RecipeMap<SimpleRecipeBuilder> GAS_CENTRIFUGE_RECIPES;
    public static final RecipeMap<SimpleRecipeBuilder> DECAY_CHAMBER_RECIPES;
    public static final RecipeMap<FuelRecipeBuilder> RTG_RECIPES;

    static {
        HEAT_EXCHANGER_RECIPES = new RecipeMapBuilder<>("heat_exchanger", new PrimitiveRecipeBuilder())
                .itemInputs(1)
                .itemOutputs(0)
                .fluidInputs(2)
                .fluidOutputs(2)
                .uiBuilder(builder -> builder
                        .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW_MULTIPLE)
                )
                .sound(GTSoundEvents.COOLING)
                .build();

        NATURAL_DRAFT_COOLING_TOWER = new RecipeMapBuilder<>("natural_draft_cooling_tower", new PrimitiveRecipeBuilder())
                .fluidInputs(1)
                .fluidOutputs(1)
                .uiBuilder(builder -> builder
                        .progressBar(GTGuiTextures.PROGRESS_BAR_GAS_COLLECTOR)
                )
                .sound(GTSoundEvents.COOLING)
                .build();

        SPENT_FUEL_POOL_RECIPES = new RecipeMapBuilder<>("spent_fuel_pool", new SimpleRecipeBuilder())
                .itemInputs(1)
                .itemOutputs(1)
                .fluidInputs(1)
                .fluidOutputs(1)
                .uiBuilder(builder -> builder
                        .progressBar(GTGuiTextures.PROGRESS_BAR_BATH)
                )
                .build();

        GAS_CENTRIFUGE_RECIPES = new RecipeMapBuilder<>("gas_centrifuge", new SimpleRecipeBuilder())
                .itemInputs(0)
                .itemOutputs(0)
                .fluidInputs(1)
                .fluidOutputs(2)
                .uiBuilder(builder -> builder
                        .progressBar(GTGuiTextures.PROGRESS_BAR_MIXER)
                )
                .sound(GTSoundEvents.CENTRIFUGE)
                .build();

        DECAY_CHAMBER_RECIPES = new RecipeMapBuilder<>("decay_chamber_recipes", new SimpleRecipeBuilder())
                .itemInputs(1)
                .itemOutputs(1)
                .fluidInputs(1)
                .fluidOutputs(1)
                .uiBuilder(builder -> builder
                        .progressBar(GTGuiTextures.PROGRESS_BAR_HAMMER)
                )
                .sound(GTSoundEvents.SCIENCE)
                .build();

        RTG_RECIPES = new RecipeMapBuilder<>("rtg_recipes", new FuelRecipeBuilder())
                .itemInputs(1)
                .itemOutputs(1)
                .uiBuilder(builder -> builder
                        .progressBar(GTGuiTextures.PROGRESS_BAR_ARROW_MULTIPLE)
                )
                .sound(GTSoundEvents.ARC)
                .build();


    }

    private SCRecipeMaps() {
    }
}
