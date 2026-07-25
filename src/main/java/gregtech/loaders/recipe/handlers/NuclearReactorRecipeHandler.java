package gregtech.loaders.recipe.handlers;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;

import static gregtech.api.GTValues.HV;
import static gregtech.api.GTValues.VA;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.common.items.MetaItems.*;

public class NuclearReactorRecipeHandler {

    public static void init() {
        //冷却
        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_COOLANT_10K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_COOLANT_10K)
                .EUt(VA[HV])
                .duration(100)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_COOLANT_30K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_COOLANT_30K)
                .EUt(VA[HV])
                .duration(300)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_COOLANT_60K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_COOLANT_60K)
                .EUt(VA[HV])
                .duration(600)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_SODIUM_POTASSIUM_10K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_SODIUM_POTASSIUM_10K)
                .EUt(VA[HV])
                .duration(100)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_SODIUM_POTASSIUM_30K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_SODIUM_POTASSIUM_30K)
                .EUt(VA[HV])
                .duration(300)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_SODIUM_POTASSIUM_60K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_SODIUM_POTASSIUM_60K)
                .EUt(VA[HV])
                .duration(600)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_HELIUM_10K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_HELIUM_10K)
                .EUt(VA[HV])
                .duration(100)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_HELIUM_30K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_HELIUM_30K)
                .EUt(VA[HV])
                .duration(300)
                .buildAndRegister();

        VACUUM_RECIPES.recipeBuilder()
                .inputNBT(COOLANT_CELL_HELIUM_60K, NBTMatcher.ANY, NBTCondition.ANY)
                .output(COOLANT_CELL_HELIUM_60K)
                .EUt(VA[HV])
                .duration(600)
                .buildAndRegister();
    }
}
