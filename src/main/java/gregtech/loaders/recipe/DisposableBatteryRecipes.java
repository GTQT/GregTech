package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.BatteryAccumulatorFluidMapping;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;

/**
 * Crafting recipes for all A-series disposable battery blocks.
 *
 * <p>Each battery follows a 5-step pipeline:
 * <ol>
 *   <li>Chemistry step 1 — produce active electrode dry blend (Mixer)</li>
 *   <li>Chemistry step 2 — alkaline activation to uncharged electrolyte fluid (Chemical Reactor)</li>
 *   <li>Shell step — combine a tiered machine hull with an exchange membrane (Assembler)</li>
 *   <li>Charge step — charge the uncharged electrolyte in the Battery Accumulator multiblock</li>
 *   <li>Fill step — fill the shell with charged electrolyte and seal (Canner)</li>
 * </ol>
 *
 * <p>Three exchange membrane types cover the 8 battery tiers:
 * Proton Exchange Membrane (LV/MV/HV), Ceramic Exchange Membrane (EV/IV/LuV),
 * and Graphene Exchange Membrane (ZPM/UV).
 */
public class DisposableBatteryRecipes {

    public static void init() {
        exchangeMembraneRecipes();
        disposableShellRecipes();
        batteryAccumulatorRecipes();
        zincManganeseCellRecipes();
        lithiumManganeseCellRecipes();
        nickelCadmiumCellRecipes();
        leadAcidBatteryRecipes();
        vanadiumFlowCellRecipes();
        lfpBatteryRecipes();
        lcoBatteryRecipes();
        nmcBatteryRecipes();
    }

    // -------------------------------------------------------------------------
    // Exchange Membrane Crafting Recipes
    // -------------------------------------------------------------------------
    private static void exchangeMembraneRecipes() {

        // Proton Exchange Membrane: sulphonated PTFE (Nafion-type)
        CHEMICAL_RECIPES.recipeBuilder()
                .fluidInputs(Polytetrafluoroethylene.getFluid(576))
                .fluidInputs(SulfuricAcid.getFluid(1000))
                .output(MetaItems.PROTON_EXCHANGE_MEMBRANE, 2)
                .duration(400).EUt(VA[LV])
                .buildAndRegister();

        // Ceramic Exchange Membrane: alumina-reinforced PTFE composite
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Alumina, 4)
                .fluidInputs(Polytetrafluoroethylene.getFluid(576))
                .output(MetaItems.CERAMIC_EXCHANGE_MEMBRANE, 2)
                .duration(500).EUt(VA[EV])
                .buildAndRegister();

        // Graphene Exchange Membrane: CNT-reinforced PBI composite
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, CarbonNanotubeFilm, 2)
                .fluidInputs(Polybenzimidazole.getFluid(576))
                .output(MetaItems.GRAPHENE_EXCHANGE_MEMBRANE, 2)
                .duration(600).EUt(VA[LuV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // Disposable Battery Shell Recipes (Assembler)
    //
    // Machine hull + exchange membrane → tiered disposable battery shell
    // -------------------------------------------------------------------------
    private static void disposableShellRecipes() {

        // LV — Proton Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[LV].getStackForm())
                .inputs(MetaItems.PROTON_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_LV)
                .duration(100).EUt(VA[LV])
                .buildAndRegister();

        // MV — Proton Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[MV].getStackForm())
                .inputs(MetaItems.PROTON_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_MV)
                .duration(150).EUt(VA[MV])
                .buildAndRegister();

        // HV — Proton Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[HV].getStackForm())
                .inputs(MetaItems.PROTON_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_HV)
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        // EV — Ceramic Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[EV].getStackForm())
                .inputs(MetaItems.CERAMIC_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_EV)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        // IV — Ceramic Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[IV].getStackForm())
                .inputs(MetaItems.CERAMIC_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_IV)
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // LuV — Ceramic Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[LuV].getStackForm())
                .inputs(MetaItems.CERAMIC_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_LUV)
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();

        // ZPM — Graphene Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[ZPM].getStackForm())
                .inputs(MetaItems.GRAPHENE_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_ZPM)
                .duration(600).EUt(VA[ZPM])
                .buildAndRegister();

        // UV — Graphene Exchange Membrane
        ASSEMBLER_RECIPES.recipeBuilder()
                .inputs(MetaTileEntities.HULL[UV].getStackForm())
                .inputs(MetaItems.GRAPHENE_EXCHANGE_MEMBRANE.getStackForm())
                .output(MetaItems.DISPOSABLE_BATTERY_SHELL_UV)
                .duration(800).EUt(VA[UV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A0 — Zinc-Manganese Dry Cell Block (LV)
    // -------------------------------------------------------------------------
    private static void zincManganeseCellRecipes() {

        MIXER_RECIPES.recipeBuilder()
                .input(dust, Zinc, 4)
                .input(dust, Pyrolusite, 8)
                .output(dust, ZincManganeseMix, 12)
                .duration(200).EUt(VA[LV])
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, ZincManganeseMix, 12)
                .input(dust, SodiumHydroxide, 3)
                .fluidInputs(Water.getFluid(1000))
                .fluidOutputs(ZincManganeseElectrolyte.getFluid(1440))
                .duration(200).EUt(VA[LV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_LV.getStackForm())
                .fluidInputs(ChargedZincManganeseElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.ZINC_MANGANESE_CELL.getStackForm())
                .duration(100).EUt(VA[LV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A1 — Lithium-Manganese Battery Block (MV)
    // -------------------------------------------------------------------------
    private static void lithiumManganeseCellRecipes() {

        MIXER_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, Pyrolusite, 8)
                .output(dust, LithiumManganeseMix, 10)
                .duration(200).EUt(VA[MV])
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, LithiumManganeseMix, 10)
                .input(dust, SodiumHydroxide, 2)
                .fluidInputs(Water.getFluid(1000))
                .fluidOutputs(LithiumManganeseElectrolyte.getFluid(1200))
                .duration(200).EUt(VA[MV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_MV.getStackForm())
                .fluidInputs(ChargedLithiumManganeseElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.LITHIUM_MANGANESE_CELL.getStackForm())
                .duration(150).EUt(VA[MV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A2 — Nickel-Cadmium Battery Block (HV)
    // -------------------------------------------------------------------------
    private static void nickelCadmiumCellRecipes() {

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Nickel, 2)
                .input(dust, SodiumHydroxide, 2)
                .fluidInputs(Water.getFluid(1000))
                .output(dust, NickelHydroxide, 4)
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder()
                .input(dust, Cadmium, 2)
                .input(dust, NickelHydroxide, 4)
                .input(dust, SodiumHydroxide, 2)
                .fluidInputs(Water.getFluid(1000))
                .fluidOutputs(NickelCadmiumElectrolyte.getFluid(2000))
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_HV.getStackForm())
                .fluidInputs(ChargedNickelCadmiumElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.NICKEL_CADMIUM_CELL.getStackForm())
                .duration(200).EUt(VA[HV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A3 — Lead-Acid Battery Block (EV)
    // -------------------------------------------------------------------------
    private static void leadAcidBatteryRecipes() {

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(plate, Lead, 6)
                .fluidInputs(SulfuricAcid.getFluid(2000))
                .output(dust, LeadAcidElectrode, 10)
                .duration(400).EUt(VA[MV])
                .buildAndRegister();

        MIXER_RECIPES.recipeBuilder()
                .input(dust, LeadAcidElectrode, 2)
                .fluidInputs(SulfuricAcid.getFluid(1500))
                .fluidInputs(Water.getFluid(1500))
                .fluidOutputs(LeadAcidElectrolyte.getFluid(3000))
                .duration(200).EUt(VA[MV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_EV.getStackForm())
                .fluidInputs(ChargedLeadAcidElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.LEAD_ACID_BATTERY.getStackForm())
                .duration(300).EUt(VA[EV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A4 — Vanadium Redox Flow Battery Block (IV)
    // -------------------------------------------------------------------------
    private static void vanadiumFlowCellRecipes() {

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Vanadium, 2)
                .fluidInputs(Oxygen.getFluid(5000))
                .output(dust, VanadiumPentoxide, 4)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, VanadiumPentoxide, 4)
                .input(dust, Graphite, 4)
                .fluidInputs(SulfuricAcid.getFluid(3000))
                .fluidOutputs(VanadiumElectrolyte.getFluid(3000))
                .duration(400).EUt(VA[EV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_IV.getStackForm())
                .fluidInputs(ChargedVanadiumElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.VANADIUM_FLOW_CELL.getStackForm())
                .duration(400).EUt(VA[IV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A5 — Lithium Iron Phosphate (LFP) Battery Block (LuV)
    // -------------------------------------------------------------------------
    private static void lfpBatteryRecipes() {

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Iron, 4)
                .fluidInputs(PhosphoricAcid.getFluid(2000))
                .output(dust, IronIIIPhosphate, 8)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 4)
                .input(dust, IronIIIPhosphate, 8)
                .output(dust, LFPCathodePowder, 12)
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // Carbon Nanotube Film via methane catalytic decomposition on iron catalyst
        // CH₄ → C (CNT) + 2 H₂
        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .fluidInputs(Methane.getFluid(2000))
                .input(dust, Iron, 1)
                .output(dust, CarbonNanotubeFilm, 2)
                .fluidOutputs(Hydrogen.getFluid(4000))
                .duration(100).EUt(VA[HV])
                .buildAndRegister();

        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, Boron, 1)
                .input(dust, Carbon, 4)
                .input(dust, LFPCathodePowder, 2)
                .input(dust, CarbonNanotubeFilm, 1)
                .fluidInputs(Oxygen.getFluid(4000))
                .fluidOutputs(LithiumBisoxalatoborate.getFluid(1152))
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_LUV.getStackForm())
                .fluidInputs(ChargedLithiumBisoxalatoborate.getFluid(10000))
                .outputs(MetaTileEntities.LFP_BATTERY.getStackForm())
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A6 — Lithium Cobalt Oxide (LCO) Battery Block (ZPM)
    // -------------------------------------------------------------------------
    private static void lcoBatteryRecipes() {

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, CobaltOxide, 4)
                .output(dust, LithiumCobaltOxide, 6)
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();

        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, Sulfur, 2)
                .input(dust, Carbon, 2)
                .input(dust, LithiumCobaltOxide, 2)
                .input(dust, CarbonNanotubeFilm, 1)
                .fluidInputs(Fluorine.getFluid(6000))
                .fluidInputs(Nitrogen.getFluid(1000))
                .fluidOutputs(LithiumBistriflimide.getFluid(1152))
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_ZPM.getStackForm())
                .fluidInputs(ChargedLithiumBistriflimide.getFluid(10000))
                .outputs(MetaTileEntities.LCO_BATTERY.getStackForm())
                .duration(600).EUt(VA[ZPM])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A7 — NMC Ternary Lithium Battery Block (UV)
    // -------------------------------------------------------------------------
    private static void nmcBatteryRecipes() {

        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Nickel, 4)
                .input(dust, Manganese, 1)
                .input(dust, CobaltOxide, 1)
                .input(dust, Lithium, 4)
                .fluidInputs(Oxygen.getFluid(2000))
                .output(dust, NMCCathodePowder, 12)
                .duration(600).EUt(VA[LuV])
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, NMCCathodePowder, 2)
                .input(dust, CarbonNanotubeFilm, 1)
                .fluidInputs(HydrofluoricAcid.getFluid(2000))
                .fluidInputs(PhosphoricAcid.getFluid(1000))
                .fluidOutputs(LithiumHexafluorophosphate.getFluid(1152))
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.DISPOSABLE_BATTERY_SHELL_UV.getStackForm())
                .fluidInputs(ChargedLithiumHexafluorophosphate.getFluid(10000))
                .outputs(MetaTileEntities.NMC_BATTERY.getStackForm())
                .duration(800).EUt(VA[UV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // Battery Accumulator — JEI display recipes
    // -------------------------------------------------------------------------

    private static final double DISPLAY_LOSS_RATIO = 0.10;

    private static void batteryAccumulatorRecipes() {
        for (BatteryAccumulatorFluidMapping mapping : BatteryAccumulatorFluidMapping.values()) {
            registerChargeRecipe(mapping);
            registerDischargeRecipe(mapping);
        }
    }

    private static void registerChargeRecipe(BatteryAccumulatorFluidMapping mapping) {
        long euPerBucket = mapping.getEuPerBucket();
        long euCostPerBucket = (long) (euPerBucket / (1.0 - DISPLAY_LOSS_RATIO));

        int tier = getTierForMapping(mapping);
        long euT = GTValues.V[tier];
        int duration = (int) Math.max(1, euCostPerBucket / euT);

        BATTERY_ACCUMULATOR_RECIPES.recipeBuilder()
                .fluidInputs(mapping.getUnchargedFluidStack(1000))
                .fluidOutputs(mapping.getChargedFluidStack(1000))
                .duration(duration)
                .EUt(euT)
                .buildAndRegister();
    }

    private static void registerDischargeRecipe(BatteryAccumulatorFluidMapping mapping) {
        long euPerBucket = mapping.getEuPerBucket();
        long euOutputPerBucket = (long) (euPerBucket * (1.0 - DISPLAY_LOSS_RATIO));

        int tier = getTierForMapping(mapping);
        long euT = GTValues.V[tier];
        int duration = (int) Math.max(1, euOutputPerBucket / euT);

        BATTERY_ACCUMULATOR_RECIPES.recipeBuilder()
                .fluidInputs(mapping.getChargedFluidStack(1000))
                .fluidOutputs(mapping.getUnchargedFluidStack(1000))
                .duration(duration)
                .EUt(euT)
                .buildAndRegister();
    }

    private static int getTierForMapping(BatteryAccumulatorFluidMapping mapping) {
        return switch (mapping) {
            case ZINC_MANGANESE -> LV;
            case LITHIUM_MANGANESE -> MV;
            case NICKEL_CADMIUM -> HV;
            case LEAD_ACID -> EV;
            case VANADIUM_FLOW -> IV;
            case LFP -> LuV;
            case LCO -> ZPM;
            case NMC -> UV;
        };
    }
}
