package gregtech.loaders.recipe.handlers;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;

public class PipeOpticalRecipeHandler {
    public static void register() {
        pipeOptical.addProcessingHandler(PropertyKey.OPTICAL_CABLE, PipeOpticalRecipeHandler::processPipeOptical);
    }

    private static void processPipeOptical(OrePrefix orePrefix, Material material, OpticalCableProperties opticalCableProperties) {
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(wireFine, material, 8)
                .input(foil, Silver, 8)
                .input(plate, Aluminium, 1)
                .fluidInputs(Polyethylene.getFluid(L))
                .output(orePrefix, material, 2)
                .duration(5 * SECOND).EUt(VA[MV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(wireFine, material, 8)
                .input(foil, Silver, 8)
                .input(plate, Aluminium, 1)
                .fluidInputs(Epoxy.getFluid(L))
                .output(orePrefix, material, 8)
                .duration(5 * SECOND).EUt(VA[MV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(wireFine, material, 8)
                .input(foil, Silver, 8)
                .input(plate, Aluminium, 1)
                .fluidInputs(Polybenzimidazole.getFluid(L))
                .output(orePrefix, material, 32)
                .duration(5 * SECOND).EUt(VA[MV]).buildAndRegister();
    }
}
