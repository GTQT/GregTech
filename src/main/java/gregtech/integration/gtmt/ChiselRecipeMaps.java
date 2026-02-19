package gregtech.integration.gtmt;

import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.core.sound.GTSoundEvents;

public class ChiselRecipeMaps {

    public static final RecipeMap<SimpleRecipeBuilder> AUTO_CHISEL_RECIPES;

    static {
        AUTO_CHISEL_RECIPES = new RecipeMapBuilder<>(
                "auto_chisel", new SimpleRecipeBuilder())
                .itemInputs(2)
                .itemOutputs(9)
                .sound(GTSoundEvents.FILE_TOOL)
                .build();
    }
}
