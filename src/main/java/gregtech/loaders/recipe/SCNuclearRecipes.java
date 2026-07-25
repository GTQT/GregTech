package gregtech.loaders.recipe;

import gregtech.api.unification.material.SCMaterials;
import gregtech.common.items.MetaItems;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.api.recipes.SCRecipeMaps.GAS_CENTRIFUGE_RECIPES;
import static gregtech.api.unification.material.SCMaterials.*;
import static gregtech.api.unification.ore.SCOrePrefix.*;

/**
 * Copyright (C) SymmetricDevs 2025
 * 由 MeowmelMuku 于 2025 修改。
 * 修改内容：添加高浓缩铀二氧化物产线。
 * 此文件遵循 GPL-3.0 许可证，详情请见项目根目录的 LICENSE 文件。
 */
public class SCNuclearRecipes {

    public static void init() {
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Boron, 2)
                .fluidInputs(Oxygen.getFluid(3000))
                .output(dust, BoronTrioxide, 5)
                .EUt(VA[LV]).duration(200)
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, BoronTrioxide, 10)
                .input(dust, Carbon, 7)
                .output(dust, BoronCarbide, 5)
                .fluidOutputs(CarbonMonoxide.getFluid(6000))
                .EUt(VA[MV]).duration(400)
                .buildAndRegister();

        GAS_CENTRIFUGE_RECIPES.recipeBuilder().duration(800).EUt(VA[HV])
                .fluidInputs(UraniumHexafluoride.getFluid(1000))
                .fluidOutputs(EnrichedUraniumHexafluoride.getFluid(100))
                .fluidOutputs(DepletedUraniumHexafluoride.getFluid(900))
                .buildAndRegister();

        GAS_CENTRIFUGE_RECIPES.recipeBuilder().duration(800).EUt(VA[HV])
                .fluidInputs(EnrichedUraniumHexafluoride.getFluid(1000))
                .fluidOutputs(HighEnrichedUraniumHexafluoride.getFluid(100))
                .fluidOutputs(DepletedUraniumHexafluoride.getFluid(900))
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder().duration(400).EUt(VA[HV])
                .input(dust, FissilePlutoniumDioxide, 1)
                .input(dust, Uraninite, 19)
                .circuitMeta(1)
                .output(dust, LowGradeMOX, 20)
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder().duration(400).EUt(VA[HV])
                .input(dust, FissilePlutoniumDioxide, 1)
                .input(dust, Uraninite, 4)
                .circuitMeta(2)
                .output(dust, HighGradeMOX, 5)
                .buildAndRegister();
        ////////////////////////////////////////////////////////////////
        MIXER_RECIPES.recipeBuilder().duration(400).EUt(VA[EV])
                .input(dust, Uranium235, 1)
                .input(dust, Plutonium244, 19)
                .circuitMeta(1)
                .output(dust, FBR, 20)
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder().duration(400).EUt(VA[EV])
                .input(dust, Uranium235, 1)
                .input(dust, Thorium230, 4)
                .circuitMeta(2)
                .output(dust, THOR, 5)
                .buildAndRegister();

        // Inconel 718
        MIXER_RECIPES.recipeBuilder().duration(200).EUt(VA[EV])
                .input(dust, Nickel, 5)
                .input(dust, Chrome, 2)
                .input(dust, Iron, 2)
                .input(dust, Niobium)
                .input(dust, Molybdenum)
                .circuitMeta(4)
                .output(dust, Inconel, 11)
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .fluidInputs(DepletedUraniumHexafluoride.getFluid(1000))
                .fluidInputs(Water.getFluid(2000))
                .fluidInputs(Hydrogen.getFluid(2000))
                .output(dust, DepletedUraniumDioxide, 1)
                .fluidOutputs(HydrofluoricAcid.getFluid(6000))
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dust, HighEnrichedUraniumDioxide, 1)
                .input(dust, DepletedUraniumDioxide, 19)
                .circuitMeta(1)
                .output(dust, LEU235, 20)
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dust, LowEnrichedUraniumDioxide, 1) // Assuming 20% enrichment
                .input(dust, DepletedUraniumDioxide, 3)
                .circuitMeta(1)
                .output(dust, LEU235, 4)
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dust, HighEnrichedUraniumDioxide, 1)
                .input(dust, DepletedUraniumDioxide, 4)
                .circuitMeta(2)
                .output(dust, HEU235, 5)
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(40).EUt(VA[ULV])
                .input(dust, Plutonium239)
                .fluidInputs(Oxygen.getFluid(2000))
                .output(dust, FissilePlutoniumDioxide, 3)
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(40).EUt(VA[ULV])
                .input(dust, Plutonium241)
                .fluidInputs(Oxygen.getFluid(2000))
                .output(dust, FissilePlutoniumDioxide, 3)
                .buildAndRegister();

        // U/Pu extraction

        ASSEMBLER_RECIPES.recipeBuilder().duration(400).EUt(VA[LV])
                .input(ring, Titanium, 2)
                .input(stick, Titanium, 16)
                .circuitMeta(1)
                .output(MetaItems.ANODE_BASKET)
                .buildAndRegister();

        ELECTROLYZER_RECIPES.recipeBuilder().duration(800).EUt(VA[EV])
                .notConsumable(MetaItems.ANODE_BASKET)
                .notConsumable(Salt.getFluid(1000))
                .input(fuelPelletDepleted, LEU235)
                .output(dustSpentFuel, LEU235)
                .output(dustBredFuel, LEU235)
                .chancedOutput(dustFissionByproduct, LEU235, 633, 0)
                .buildAndRegister();

        ELECTROLYZER_RECIPES.recipeBuilder().duration(800).EUt(VA[EV])
                .notConsumable(MetaItems.ANODE_BASKET)
                .notConsumable(Salt.getFluid(1000))
                .input(fuelPelletDepleted, HEU235)
                .output(dustSpentFuel, HEU235)
                .output(dustBredFuel, HEU235)
                .chancedOutput(dustFissionByproduct, HEU235, 821, 0)
                .buildAndRegister();

        ELECTROLYZER_RECIPES.recipeBuilder().duration(800).EUt(VA[EV])
                .notConsumable(MetaItems.ANODE_BASKET)
                .notConsumable(Salt.getFluid(1000))
                .input(fuelPelletDepleted, LowGradeMOX)
                .output(dustSpentFuel, LowGradeMOX)
                .output(dustBredFuel, LowGradeMOX)
                .chancedOutput(dustFissionByproduct, LowGradeMOX, 565, 0)
                .buildAndRegister();

        ELECTROLYZER_RECIPES.recipeBuilder().duration(800).EUt(VA[EV])
                .notConsumable(MetaItems.ANODE_BASKET)
                .notConsumable(Salt.getFluid(1000))
                .input(fuelPelletDepleted, HighGradeMOX)
                .output(dustSpentFuel, HighGradeMOX)
                .output(dustBredFuel, HighGradeMOX)
                .chancedOutput(dustFissionByproduct, HighGradeMOX, 1141, 0)
                .buildAndRegister();
        /////////////////////////////////////////////////////////////
        ELECTROLYZER_RECIPES.recipeBuilder().duration(800).EUt(VA[EV])
                .notConsumable(MetaItems.ANODE_BASKET)
                .notConsumable(Salt.getFluid(1000))
                .input(fuelPelletDepleted, FBR)
                .output(dustSpentFuel, FBR)
                .output(dustBredFuel, FBR)
                .chancedOutput(dustFissionByproduct, FBR, 1141, 0)
                .buildAndRegister();

        ELECTROLYZER_RECIPES.recipeBuilder().duration(800).EUt(VA[EV])
                .notConsumable(MetaItems.ANODE_BASKET)
                .notConsumable(Salt.getFluid(1000))
                .input(fuelPelletDepleted, THOR)
                .output(dustSpentFuel, THOR)
                .output(dustBredFuel, THOR)
                .chancedOutput(dustFissionByproduct, THOR, 1141, 0)
                .buildAndRegister();
        /////////////////////////////////////////////////////////////
        CHEMICAL_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustSpentFuel, LEU235, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(4000))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(UraniumHexafluoride.getFluid(1000))
                .fluidOutputs(Water.getFluid(2000))
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustSpentFuel, HEU235, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(4000))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(EnrichedUraniumHexafluoride.getFluid(1000))
                .fluidOutputs(Water.getFluid(2000))
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustSpentFuel, LowGradeMOX, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(4000))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(DepletedUraniumHexafluoride.getFluid(1000))
                .fluidOutputs(Water.getFluid(2000))
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustSpentFuel, HighGradeMOX, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(4000))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(DepletedUraniumHexafluoride.getFluid(1000))
                .fluidOutputs(Water.getFluid(2000))
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustBredFuel, LEU235)
                .chancedOutput(dust, Plutonium239, 282, 0)
                .chancedOutput(dust, Plutonium240, 132, 0)
                .chancedOutput(dust, Plutonium241, 84, 0)
                .chancedOutput(dust, Neptunium239, 18, 0)
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustBredFuel, HEU235)
                .chancedOutput(dust, Plutonium239, 235, 0)
                .chancedOutput(dust, Plutonium240, 110, 0)
                .chancedOutput(dust, Plutonium241, 70, 0)
                .chancedOutput(dust, Neptunium239, 15, 0)
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustBredFuel, LowGradeMOX)
                .chancedOutput(dust, Plutonium240, 165, 0)
                .chancedOutput(dust, Plutonium241, 5, 0)
                .chancedOutput(dust, Neptunium239, 15, 0)
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustBredFuel, HighGradeMOX)
                .chancedOutput(dust, Plutonium240, 724, 0)
                .chancedOutput(dust, Plutonium241, 192, 0)
                .chancedOutput(dust, Plutonium242, 59, 0)
                .chancedOutput(dust, Neptunium239, 3, 0)
                .buildAndRegister();
        /////////////////////////////////////////////////////////////////////
        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustBredFuel, FBR)
                .chancedOutput(dust, Uranium235, 700, 0) // 假设 FBR 主要产出 Uranium235
                .chancedOutput(dust, Plutonium244, 200, 0) // 假设 FBR 也会产出一些 Plutonium244
                .chancedOutput(dust, Zirconium, 100, 0) // 假设 FBR 还会产出一些 Zirconium
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustBredFuel, THOR)
                .chancedOutput(dust, Thorium, 1000, 0) // 假设 THOR 主要产出 Thorium232
                .chancedOutput(dust, Uranium233, 500, 0) // 假设 THOR 也会产出一些 Uranium233
                .chancedOutput(dust, Uranium238, 200, 0) // 假设 THOR 还会产出一些 Uranium238
                .buildAndRegister();
        /////////////////////////////////////////////////////////////////////
        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustFissionByproduct, LEU235)
                .chancedOutput(dust, Zirconium, 1645, 0)
                .chancedOutput(dust, Molybdenum, 1169, 0)
                .chancedOutput(dust, Neodymium, 1030, 0)
                .chancedOutput(dust, Lead, 659, 0)
                .chancedOutput(dust, Ruthenium, 609, 0)
                .chancedOutput(dust, Technetium, 297, 0)
                .fluidOutputs(Krypton.getFluid(16), Xenon.getFluid(111), Radon.getFluid(125))
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustFissionByproduct, HEU235)
                .chancedOutput(dust, Zirconium, 1645, 0)
                .chancedOutput(dust, Molybdenum, 1182, 0)
                .chancedOutput(dust, Neodymium, 1031, 0)
                .chancedOutput(dust, Ruthenium, 600, 0)
                .chancedOutput(dust, Technetium, 300, 0)
                .chancedOutput(dust, Yttrium, 211, 0)
                .fluidOutputs(Krypton.getFluid(16), Xenon.getFluid(110), Radon.getFluid(129))
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustFissionByproduct, LowGradeMOX)
                .chancedOutput(dust, Neodymium, 1015, 0)
                .chancedOutput(dust, Molybdenum, 937, 0)
                .chancedOutput(dust, Zirconium, 863, 0)
                .chancedOutput(dust, Palladium, 738, 0)
                .chancedOutput(dust, Bismuth, 300, 0)
                .chancedOutput(dust, Tellurium, 188, 0)
                .fluidOutputs(Krypton.getFluid(6), Xenon.getFluid(126), Radon.getFluid(118))
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustFissionByproduct, HighGradeMOX)
                .chancedOutput(dust, Neodymium, 1020, 0)
                .chancedOutput(dust, Molybdenum, 937, 0)
                .chancedOutput(dust, Zirconium, 863, 0)
                .chancedOutput(dust, Samarium, 319, 0)
                .chancedOutput(dust, Tellurium, 187, 0)
                .chancedOutput(dust, Promethium, 119, 0)
                .fluidOutputs(Krypton.getFluid(6), Xenon.getFluid(126), Radon.getFluid(114))
                .buildAndRegister();
        /////////////////////////////////////////////////////////////////////
        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustFissionByproduct, FBR)
                .chancedOutput(dust, Zirconium, 1645, 0)
                .chancedOutput(dust, Molybdenum, 1169, 0)
                .chancedOutput(dust, Neodymium, 1030, 0)
                .chancedOutput(dust, Palladium, 738, 0)
                .chancedOutput(dust, Samarium, 319, 0)
                .chancedOutput(dust, Tellurium, 188, 0)
                .fluidOutputs(Krypton.getFluid(16), Xenon.getFluid(111), Radon.getFluid(125))
                .buildAndRegister();

        CENTRIFUGE_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(dustFissionByproduct, THOR)
                .chancedOutput(dust, Zirconium, 863, 0)
                .chancedOutput(dust, Molybdenum, 937, 0)
                .chancedOutput(dust, Neodymium, 1015, 0)
                .chancedOutput(dust, Bismuth, 300, 0)
                .chancedOutput(dust, Tellurium, 188, 0)
                .chancedOutput(dust, Yttrium, 211, 0)
                .fluidOutputs(Krypton.getFluid(6), Xenon.getFluid(126), Radon.getFluid(118))
                .buildAndRegister();
        /////////////////////////////////////////////////////////////////////
        // Radon from uranium bearing ores

        CHEMICAL_BATH_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(crushed, Uraninite)
                .fluidInputs(DilutedHydrochloricAcid.getFluid(100))
                .output(crushedPurified, Uraninite)
                .fluidOutputs(RadonRichGasMixture.getFluid(1000))
                .buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder().duration(200).EUt(VA[LV])
                .input(crushed, Pitchblende)
                .fluidInputs(DilutedHydrochloricAcid.getFluid(150))
                .output(crushedPurified, Pitchblende)
                .fluidOutputs(RadonRichGasMixture.getFluid(1500))
                .buildAndRegister();

        DISTILLATION_RECIPES.recipeBuilder().duration(1000).EUt(VHA[HV])
                .fluidInputs(RadonRichGasMixture.getFluid(3000))
                .fluidOutputs(Radon.getFluid(1000))
                .fluidOutputs(Helium.getFluid(2000))
                .buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder().duration(200).EUt(VA[MV])
                .input(plate, Zircaloy4, 4)
                .input(spring, Inconel, 1)
                .input(round, StainlessSteel, 2)
                .output(MetaItems.FUEL_CLADDING)
                .buildAndRegister();

        //////////////////////////////////////////////////////////////////
        // 阶段一：贫铀二氧化物初步处理
        //3uo2 6f =2uo2 uf6
        BLAST_RECIPES.recipeBuilder()
                .input(dust, DepletedUraniumDioxide, 1)
                .output(dust, LowEnrichedUraniumDioxide, 1)
                .blastFurnaceTemp(5400)
                .duration(400).EUt(VA[EV])
                .buildAndRegister();

        // 阶段一：低浓缩二氧化物到六氟化铀
        CHEMICAL_RECIPES.recipeBuilder()
                .duration(200)
                .EUt(VA[EV])
                .input(dust, DepletedUraniumDioxide, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(4000))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(Water.getFluid(2000))
                .fluidOutputs(DepletedUraniumHexafluoride.getFluid(1000))
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .duration(200)
                .EUt(VA[EV])
                .input(dust, LowEnrichedUraniumDioxide, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(4000))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(Water.getFluid(2000))
                .fluidOutputs(UraniumHexafluoride.getFluid(1000))
                .buildAndRegister();

        // 阶段三：高浓缩产物合成
        //UF6 + 3H2O =UO2 + 6HF +O
        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .fluidInputs(HighEnrichedUraniumHexafluoride.getFluid(7000))
                .fluidInputs(Steam.getFluid(3000))
                .output(dust, HighEnrichedUraniumDioxide, 3)
                .fluidOutputs(HydrofluoricAcid.getFluid(6000))
                .fluidOutputs(Oxygen.getFluid(1000))
                .duration(400).EUt(VA[IV])
                .buildAndRegister();
    }
}
