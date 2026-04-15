package gtqt.loaders.recipe.handlers;

import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import gtqt.common.metatileentities.GTQTMetaTileEntities;

import static gregtech.api.GTValues.*;
import static gregtech.api.GTValues.VA;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.common.items.MetaItems.*;
import static gregtech.api.unification.ore.OrePrefix.plate;
import static gtqt.common.items.GTQTMetaItems.*;

/**
 * 可编程电路及工具箱的配方注册。
 */
public class ProgrammableCircuit {

    public static void init() {
        // 可编程覆盖板配方
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(ROBOT_ARM_HV)
                .input(CONVEYOR_MODULE_HV)
                .input(OrePrefix.circuit, MarkerMaterials.Tier.HV)
                .circuitMeta(7)
                .fluidInputs(Materials.Tin.getFluid(L))
                .output(COVER_PROGRAMMABLE_CIRCUIT)
                .EUt(VA[HV]).duration(20)
                .buildAndRegister();

        // 可编程工具箱配方
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(ROBOT_ARM_MV)
                .input(OrePrefix.circuit, MarkerMaterials.Tier.MV)
                .input(plate, Materials.Steel, 4)
                .circuitMeta(8)
                .fluidInputs(Materials.Tin.getFluid(L))
                .output(PROGRAMMING_TOOLKIT)
                .EUt(VA[MV]).duration(200)
                .buildAndRegister();

        // 可编程提供器配方
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(ROBOT_ARM_HV)
                .input(CONVEYOR_MODULE_HV, 2)
                .input(OrePrefix.circuit, MarkerMaterials.Tier.HV, 2)
                .input(plate, Materials.Aluminium, 4)
                .circuitMeta(9)
                .fluidInputs(Materials.Tin.getFluid(L * 2))
                .outputs(GTQTMetaTileEntities.PROGRAMMING_PROVIDER.getStackForm())
                .EUt(VA[HV]).duration(400)
                .buildAndRegister();
    }
}
