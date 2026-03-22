package gregtech.loaders.recipe.chemistry;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;

import static gregtech.api.GTValues.SECOND;
import static gregtech.api.GTValues.VA;
import static gregtech.api.recipes.RecipeMaps.VULCANIZING_PRESS_RECIPES;
import static gregtech.api.unification.material.Materials.Sulfur;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.common.items.MetaItems.*;

public class RubberRecipes {

    static Material[] catalyst = { Materials.Zincite, Materials.Magnesia };

    public static void init() {
        registerRecipes(Materials.RawRubber, Materials.Rubber, 0);
        registerRecipes(Materials.RawStyreneButadieneRubber, Materials.StyreneButadieneRubber, 1);
        registerRecipes(Materials.Polydimethylsiloxane, Materials.SiliconeRubber, 1);
    }

    public static void registerRecipes(Material input, Material output, int tier) {
        for (Material cat : catalyst) {
            // Ingot
            VULCANIZING_PRESS_RECIPES.recipeBuilder()
                    .notConsumable(dust, cat)
                    .notConsumable(SHAPE_EXTRUDER_INGOT)
                    .input(dust, input, 4)
                    .input(dust, Sulfur)
                    .output(ingot, output, 4)
                    .EUt(VA[tier])
                    .duration((tier + 1) * 10 * SECOND)
                    .buildAndRegister();

            // Plate
            VULCANIZING_PRESS_RECIPES.recipeBuilder()
                    .notConsumable(dust, cat)
                    .notConsumable(SHAPE_EXTRUDER_PLATE)
                    .input(dust, input, 4)
                    .input(dust, Sulfur)
                    .output(plate, output, 4)
                    .EUt(VA[tier])
                    .duration((tier + 1) * 10 * SECOND)
                    .buildAndRegister();

            // Rod
            VULCANIZING_PRESS_RECIPES.recipeBuilder()
                    .notConsumable(dust, cat)
                    .notConsumable(SHAPE_EXTRUDER_ROD)
                    .input(dust, input, 4)
                    .input(dust, Sulfur)
                    .output(stick, output, 8)
                    .EUt(VA[tier])
                    .duration((tier + 1) * 10 * SECOND)
                    .buildAndRegister();

            // Ring
            VULCANIZING_PRESS_RECIPES.recipeBuilder()
                    .notConsumable(dust, cat)
                    .notConsumable(SHAPE_EXTRUDER_RING)
                    .input(dust, input, 4)
                    .input(dust, Sulfur)
                    .output(ring, output, 16)
                    .EUt(VA[tier])
                    .duration((tier + 1) * 10 * SECOND)
                    .buildAndRegister();

            // Foil
            VULCANIZING_PRESS_RECIPES.recipeBuilder()
                    .notConsumable(dust, cat)
                    .notConsumable(SHAPE_EXTRUDER_FOIL)
                    .input(dust, input, 4)
                    .input(dust, Sulfur)
                    .output(foil, output, 16)
                    .EUt(VA[tier])
                    .duration((tier + 1) * 10 * SECOND)
                    .buildAndRegister();

            // Gear
            VULCANIZING_PRESS_RECIPES.recipeBuilder()
                    .notConsumable(dust, cat)
                    .notConsumable(SHAPE_EXTRUDER_GEAR)
                    .input(dust, input, 4)
                    .input(dust, Sulfur)
                    .output(gear, output, 1)
                    .EUt(VA[tier])
                    .duration((tier + 1) * 10 * SECOND)
                    .buildAndRegister();
        }
    }
}
