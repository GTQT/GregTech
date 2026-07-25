package gregtech.loaders.recipe.handlers;

import gregtech.api.GTValues;
import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.MarkerMaterial;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.api.unification.ore.OrePrefix.circuit;
import static gregtech.api.util.MaterialHelper.Plastic;
import static gregtech.common.metatileentities.MetaTileEntities.*;

public class HatchHandlers {

    public static void init() {
        // 总成互转
        for (int i = 0; i < DUAL_IMPORT_HATCH.length; i++) {
            if (DUAL_IMPORT_HATCH[i] != null && DUAL_EXPORT_HATCH[i] != null) {
                ModHandler.addShapedRecipe("item_dual_output_to_input_" + DUAL_IMPORT_HATCH[i].getTier(),
                        DUAL_IMPORT_HATCH[i].getStackForm(),
                        "d", "B", 'B', DUAL_EXPORT_HATCH[i].getStackForm());
                ModHandler.addShapedRecipe("item_dual_input_to_output_" + DUAL_EXPORT_HATCH[i].getTier(),
                        DUAL_EXPORT_HATCH[i].getStackForm(),
                        "d", "B", 'B', DUAL_IMPORT_HATCH[i].getStackForm());
            }
        }

        // 巨型总成互转
        for (int i = 0; i < HUGE_DUAL_IMPORT_HATCH.length; i++) {
            for (int v = 0; v < 4; v++) {
                if (HUGE_DUAL_IMPORT_HATCH[i][v] != null && HUGE_DUAL_EXPORT_HATCH[i][v] != null) {
                    ModHandler.addShapedRecipe(
                            "huge_dual_output_to_input_" + i + "_" + v,
                            HUGE_DUAL_IMPORT_HATCH[i][v].getStackForm(),
                            "d", "B", 'B', HUGE_DUAL_EXPORT_HATCH[i][v].getStackForm());
                    ModHandler.addShapedRecipe(
                            "huge_dual_input_to_output_" + i + "_" + v,
                            HUGE_DUAL_EXPORT_HATCH[i][v].getStackForm(),
                            "d", "B", 'B', HUGE_DUAL_IMPORT_HATCH[i][v].getStackForm());
                }
            }
        }
        // 巨型总线互转
        for (int i = 0; i < HUGE_ITEM_IMPORT_BUS.length; i++) {
            for (int v = 0; v < 4; v++) {
                if (HUGE_ITEM_IMPORT_BUS[i][v] != null && HUGE_ITEM_EXPORT_BUS[i][v] != null) {
                    ModHandler.addShapedRecipe(
                            "huge_item_bus_output_to_input_" + i + "_" + v,
                            HUGE_ITEM_IMPORT_BUS[i][v].getStackForm(),
                            "d", "B", 'B', HUGE_ITEM_EXPORT_BUS[i][v].getStackForm());
                    ModHandler.addShapedRecipe(
                            "huge_item_bus_input_to_output_" + i + "_" + v,
                            HUGE_ITEM_EXPORT_BUS[i][v].getStackForm(),
                            "d", "B", 'B', HUGE_ITEM_IMPORT_BUS[i][v].getStackForm());
                }
            }
        }

        // 普通总成 回环总成 配方
        for (int i = 0; i < 9; i++) {
            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_IMPORT_BUS[i + 1])
                    .input(FLUID_IMPORT_HATCH[i + 1])
                    .input(circuit, MarkerMaterial.create(GTValues.VN[i + 1].toLowerCase()), 4)
                    .fluidInputs(Plastic.get(i).getFluid(L * 4))
                    .output(DUAL_IMPORT_HATCH[i])
                    .duration(100).EUt(VA[ULV + i]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_EXPORT_BUS[i + 1])
                    .input(FLUID_EXPORT_HATCH[i + 1])
                    .input(circuit, MarkerMaterial.create(GTValues.VN[i + 1].toLowerCase()), 4)
                    .fluidInputs(Plastic.get(i).getFluid(L * 4))
                    .output(DUAL_EXPORT_HATCH[i])
                    .duration(100).EUt(VA[ULV + i]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(DUAL_IMPORT_HATCH[i + 1])
                    .input(DUAL_EXPORT_HATCH[i + 1])
                    .input(circuit, MarkerMaterial.create(GTValues.VN[i + 1].toLowerCase()), 4)
                    .fluidInputs(Plastic.get(i).getFluid(L * 4))
                    .output(COMPLEX_DUAL_HATCH[i])
                    .duration(100).EUt(VA[ULV + i]).buildAndRegister();
        }

        // 巨型总成 总线配方
        for (int i = 0; i < HUGE_DUAL_IMPORT_HATCH.length; i++) {
            for (int v = 0; v < 4; v++) {
                // 巨型输入总成: 普通输入总成 + 量子箱 + 量子缸
                ASSEMBLER_RECIPES.recipeBuilder()
                        .input(DUAL_IMPORT_HATCH[i])
                        .input(QUANTUM_CHEST[i])
                        .input(QUANTUM_TANK[i])
                        .input(circuit, MarkerMaterial.create(GTValues.VN[i+v].toLowerCase()), 4)
                        .fluidInputs(Plastic.get(i).getFluid(L * 4))
                        .output(HUGE_DUAL_IMPORT_HATCH[i][v])
                        .duration(20 * SECOND).EUt(VA[LV + i]).buildAndRegister();

                // 巨型输出总成: 普通输出总成 + 量子箱 + 量子缸
                ASSEMBLER_RECIPES.recipeBuilder()
                        .input(DUAL_EXPORT_HATCH[i])
                        .input(QUANTUM_CHEST[i])
                        .input(QUANTUM_TANK[i])
                        .input(circuit, MarkerMaterial.create(GTValues.VN[i+v].toLowerCase()), 4)
                        .fluidInputs(Plastic.get(i).getFluid(L * 4))
                        .output(HUGE_DUAL_EXPORT_HATCH[i][v])
                        .duration(20 * SECOND).EUt(VA[LV + i]).buildAndRegister();

                // 巨型输入总线: 普通输入总线 + 量子箱
                ASSEMBLER_RECIPES.recipeBuilder()
                        .input(ITEM_IMPORT_BUS[i])
                        .input(QUANTUM_CHEST[i])
                        .input(circuit, MarkerMaterial.create(GTValues.VN[i+v].toLowerCase()), 2)
                        .fluidInputs(Plastic.get(i).getFluid(L * 4))
                        .output(HUGE_ITEM_IMPORT_BUS[i][v])
                        .duration(20 * SECOND).EUt(VA[LV + i]).buildAndRegister();

                // 巨型输出总线: 普通输出总线 + 量子箱
                ASSEMBLER_RECIPES.recipeBuilder()
                        .input(ITEM_EXPORT_BUS[i])
                        .input(QUANTUM_CHEST[i])
                        .input(circuit, MarkerMaterial.create(GTValues.VN[i+v].toLowerCase()), 2)
                        .fluidInputs(Plastic.get(i).getFluid(L * 4))
                        .output(HUGE_ITEM_EXPORT_BUS[i][v])
                        .duration(20 * SECOND).EUt(VA[LV + i]).buildAndRegister();
            }
        }
    }
}
