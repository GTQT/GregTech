package gregtech.loaders.recipe;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.common.items.MetaItems;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;

public class RTGFuelRecipeLoader {
    public static void load() {
        RTG(Thorium232, 1,
                Uranium233, Uranium234, Thorium230, Thorium229,
                Strontium, Caesium);
        RTG(Thorium233, 1,
                Uranium233, Protactinium233, Thorium232, Uranium234,
                Strontium, Caesium);
        RTG(Thorium229, 2,
                Uranium233, Thorium230, Materials.Actinium, Radium225,
                Strontium, Caesium);
        RTG(Thorium230, 1,
                Radium226, Thorium229, Uranium234, Protactinium231,
                Strontium, Caesium);

        RTG(Uranium232, 3,
                Thorium228, Uranium233, Plutonium238, Neptunium237,
                Strontium, Caesium);
        RTG(Uranium233, 2,
                Uranium234, Materials.Uranium235, Neptunium237, Materials.Plutonium239,
                Strontium, Caesium);
        RTG(Uranium234, 2,
                Materials.Uranium235, Uranium236, Neptunium237, Plutonium238,
                Strontium, Caesium);
        RTG(Uranium236, 1,
                Neptunium237, Materials.Plutonium239, Materials.Uranium235, Plutonium240,
                Strontium, Caesium);
        RTG(Uranium237, 2,
                Neptunium237, Plutonium238, Materials.Uranium238, Materials.Plutonium239,
                Strontium, Caesium);
        RTG(Uranium239, 1,
                Neptunium239, Materials.Plutonium239, Materials.Uranium238, Plutonium240,
                Strontium, Caesium);

        RTG(Neptunium235, 3,
                Materials.Uranium235, Materials.Plutonium239, Neptunium236, Plutonium238,
                Strontium, Caesium);
        RTG(Neptunium236, 2,
                Plutonium236, Neptunium237, Uranium236, Materials.Plutonium239,
                Strontium, Caesium);
        RTG(Neptunium237, 1,
                Plutonium237, Uranium233, Plutonium238, Americium241,
                Strontium, Caesium);
        RTG(Neptunium238, 2,
                Plutonium238, Neptunium237, Americium241, Materials.Plutonium239,
                Strontium, Caesium);
        RTG(Neptunium239, 3,
                Materials.Plutonium239, Americium243, Neptunium237, Plutonium240,
                Strontium, Caesium);

        RTG(Plutonium236, 3,
                Uranium232, Plutonium238, Neptunium237, Americium241,
                Strontium, Caesium);
        RTG(Plutonium237, 2,
                Neptunium237, Plutonium238, Americium241, Curium242,
                Strontium, Caesium);
        RTG(Plutonium238, 5,
                Uranium234, Materials.Plutonium239, Americium241, Curium242,
                Strontium, Caesium);
        RTG(Plutonium240, 2,
                Uranium236, Materials.Plutonium241, Americium241, Curium244,
                Strontium, Caesium);
        RTG(Plutonium242, 1,
                Materials.Uranium238, Americium243, Curium244, Plutonium244,
                Strontium, Caesium);
        RTG(Plutonium243, 1,
                Americium243, Plutonium244, Curium244, Curium245,
                Strontium, Caesium);
        RTG(Plutonium244, 1,
                Americium243, Curium244, Curium246, Plutonium242,
                Strontium, Caesium);

        RTG(Americium240, 2,
                Plutonium236, Americium241, Curium242, Neptunium237,
                Strontium, Caesium);
        RTG(Americium241, 3,
                Neptunium237, Plutonium238, Americium242, Curium242,
                Strontium, Caesium);
        RTG(Americium242, 1,
                Plutonium238, Americium243, Curium243, Curium242,
                Strontium, Caesium);
        RTG(Americium243, 2,
                Materials.Plutonium239, Curium243, Curium244, Americium242,
                Strontium, Caesium);

        RTG(Curium242, 4,
                Plutonium238, Americium241, Curium243, Curium244,
                Strontium, Caesium);
        RTG(Curium243, 3,
                Americium243, Materials.Plutonium239, Curium244, Curium245,
                Strontium, Caesium);
        RTG(Curium244, 5,
                Plutonium240, Americium241, Curium245, Curium246,
                Strontium, Caesium);
        RTG(Curium245, 2,
                Americium241, Curium246, Materials.Plutonium241, Curium247,
                Strontium, Caesium);
        RTG(Curium246, 2,
                Plutonium242, Curium247, Americium243, Curium248,
                Strontium, Caesium);
        RTG(Curium247, 1,
                Americium243, Curium248, Plutonium243, Curium250,
                Strontium, Caesium);
        RTG(Curium248, 1,
                Plutonium244, Curium250, Californium249, Curium247,
                Strontium, Caesium);
        RTG(Curium250, 2,
                Californium252, Curium248, Plutonium244, Berkelium249,
                Strontium, Caesium);

        RTG(Berkelium249, 3,
                Californium249, Curium248, Americium243, Plutonium244,
                Strontium, Caesium);

        RTG(Californium249, 3,
                Curium248, Berkelium249, Californium252, Plutonium244,
                Strontium, Caesium);
        RTG(Californium252, 5,
                Curium250, Plutonium244, Californium249, Berkelium249,
                Strontium, Caesium);
    }

    private static void RTG(Material material, int fuel, Material material1, Material material2, Material material3, Material material4, Material material5, Material material6) {
        RTG(material, fuel);

        CENTRIFUGE_RECIPES.recipeBuilder()
                .input(fuelPebbleDepleted, material, 9)
                .output(dust, material1)
                .output(dust, material2, 5)
                .output(dust, material3)
                .output(dust, material4)
                .chancedOutput(dust, material5, 1, 2500, 0)
                .chancedOutput(dust, material6, 1, 2500, 0)
                .duration(400 + fuel * 20)
                .EUt(19)
                .buildAndRegister();
    }

    private static void RTG(Material material, int fuel) {
        Material[] materials = {Nitrogen, Helium, Argon, Neon, Krypton};
        BLAST_RECIPES.recipeBuilder()
                .input(dust, material, 16)
                .input(dust, Graphite)
                .notConsumable(MetaItems.SHAPE_MOLD_BALL)
                .fluidInputs(materials[fuel - 1].getFluid(1000))
                .output(fuelPebble, material)
                .duration(400 + fuel * 200)
                .blastFurnaceTemp(1800 * 400 * fuel)
                .EUt(VA[HV])
                .buildAndRegister();

        RTG_RECIPES.recipeBuilder()
                .input(fuelPebble, material)
                .output(fuelPebbleDepleted, material)
                .duration(HOUR * fuel)
                .EUt(V[LV])
                .buildAndRegister();
    }
}
