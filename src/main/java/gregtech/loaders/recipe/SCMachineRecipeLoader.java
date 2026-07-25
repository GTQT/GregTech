package gregtech.loaders.recipe;

import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.BlockFissionCasing;
import gregtech.common.blocks.BlockGasCentrifugeCasing;
import gregtech.common.blocks.BlockNuclearCasing;
import gregtech.common.blocks.MetaBlocks;

import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.api.unification.material.SCMaterials.BoronCarbide;
import static gregtech.api.unification.material.SCMaterials.Inconel;

public class SCMachineRecipeLoader {

    public static void init() {
        ASSEMBLER_RECIPES.recipeBuilder().EUt(48).duration(280)
                .input(plateDouble, Inconel)
                .input(plate, Steel, 5)
                .input(frameGt, Steel)
                .outputs(MetaBlocks.FISSION_CASING.getItemVariant(
                        BlockFissionCasing.FissionCasingType.REACTOR_VESSEL, ConfigHolder.recipes.casingsPerCraft))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().EUt(48).duration(280)
                .input(pipeLargeFluid, Inconel)
                .input(frameGt, Steel)
                .outputs(MetaBlocks.FISSION_CASING.getItemVariant(
                        BlockFissionCasing.FissionCasingType.COOLANT_CHANNEL))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().EUt(48).duration(280)
                .input(stick, Zircaloy4, 6)
                .input(ring, Zircaloy4, 1)
                .circuitMeta(1)
                .outputs(MetaBlocks.FISSION_CASING.getItemVariant(
                        BlockFissionCasing.FissionCasingType.FUEL_CHANNEL))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().EUt(48).duration(280)
                .input(stick, Zircaloy4, 3)
                .input(ring, Zircaloy4, 1)
                .circuitMeta(2)
                .outputs(MetaBlocks.FISSION_CASING.getItemVariant(
                        BlockFissionCasing.FissionCasingType.CONTROL_ROD_CHANNEL))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().EUt(48).duration(200)
                .inputs(MetaBlocks.BOILER_CASING.getItemVariant(
                        BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE))
                .input(wireGtSingle, Nichrome, 4)
                .outputs(MetaBlocks.NUCLEAR_CASING.getItemVariant(
                        BlockNuclearCasing.NuclearCasingType.GAS_CENTRIFUGE_HEATER))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().EUt(48).duration(200)
                .input(pipeNormalFluid, Steel)
                .input(pipeTinyFluid, Steel, 3)
                .outputs(MetaBlocks.GAS_CENTRIFUGE_CASING.getItemVariant(
                        BlockGasCentrifugeCasing.GasCentrifugeCasingType.GAS_CENTRIFUGE_COLUMN))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().EUt(64).duration(200)
                .input(frameGt, Titanium)
                .input(stick, BoronCarbide, 8)
                .outputs(MetaBlocks.NUCLEAR_CASING.getItemVariant(
                        BlockNuclearCasing.NuclearCasingType.SPENT_FUEL_CASING, ConfigHolder.recipes.casingsPerCraft))
                .buildAndRegister();
    }
}
