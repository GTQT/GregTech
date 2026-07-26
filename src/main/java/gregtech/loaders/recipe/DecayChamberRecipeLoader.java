package gregtech.loaders.recipe;

import gregtech.api.unification.material.Material;

import static gregtech.api.GTValues.VA;
import static gregtech.api.recipes.RecipeMaps.DECAY_CHAMBER_RECIPES;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.dust;

public class DecayChamberRecipeLoader {
    public static void load() {
        DECAY_CHAMBER_RECIPES(Thorium, Lead, 3);
        DECAY_CHAMBER_RECIPES(Radium, Lead, 3);
        DECAY_CHAMBER_RECIPES(Polonium, Lead, 4);
        DECAY_CHAMBER_RECIPES(Strontium, Zirconium, 4);
        DECAY_CHAMBER_RECIPES(Promethium, Neodymium, 4);
        DECAY_CHAMBER_RECIPES(Uranium233, Bismuth, 4);
        DECAY_CHAMBER_RECIPES(Uranium234, Radium, 4);
        DECAY_CHAMBER_RECIPES(Uranium235, Lead, 4);
        DECAY_CHAMBER_RECIPES(Uranium238, Radium, 4);
        DECAY_CHAMBER_RECIPES(Plutonium238, Uranium234, 4);
        DECAY_CHAMBER_RECIPES(Plutonium239, Uranium235, 4);
        DECAY_CHAMBER_RECIPES(Plutonium242, Uranium238, 4);
        DECAY_CHAMBER_RECIPES(Iridium, Platinum, 4);
        DECAY_CHAMBER_RECIPES(Neptunium236, Thorium, 4);
        DECAY_CHAMBER_RECIPES(Neptunium, Uranium233, 4);
        DECAY_CHAMBER_RECIPES(Americium241, Neptunium, 4);
        DECAY_CHAMBER_RECIPES(Americium243, Plutonium239, 4);
        DECAY_CHAMBER_RECIPES(Curium243, Plutonium239, 4);
        DECAY_CHAMBER_RECIPES(Curium245, Plutonium241, 4);
        DECAY_CHAMBER_RECIPES(Curium246, Plutonium242, 4);
        DECAY_CHAMBER_RECIPES(Curium247, Americium243, 4);
    }

    private static void DECAY_CHAMBER_RECIPES(Material material1, Material material2, int tier) {
        DECAY_CHAMBER_RECIPES.recipeBuilder()
                .input(dust, material1)
                .output(dust, material2)
                .duration(400 + tier * 200)
                .EUt(VA[tier])
                .buildAndRegister();
    }
}
