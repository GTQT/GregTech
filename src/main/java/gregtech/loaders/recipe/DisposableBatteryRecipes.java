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
 *   <li>Chemistry step 2 — alkaline activation to uncharged fluid paste (Chemical Reactor)</li>
 *   <li>Hull step — assemble casing with pre-installed terminals (Assembler)</li>
 *   <li>Charge step — charge the uncharged electrolyte in the Battery Accumulator multiblock</li>
 *   <li>Fill step — fill hull with charged electrolyte and seal (Canner)</li>
 * </ol>
 *
 * <p>The charge step requires the Battery Accumulator multiblock, which converts
 * uncharged electrolyte + EU → charged electrolyte (with 10% loss). Only charged
 * electrolyte can be used in the Canner to produce a functional battery block.
 */
public class DisposableBatteryRecipes {

    public static void init() {
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
    // A0 — Zinc-Manganese Dry Cell Block (LV)
    //
    // Real chemistry: Leclanché / alkaline cell
    //   Anode:   Zn  → Zn²⁺ + 2e⁻
    //   Cathode: 2 MnO₂ + 2e⁻ → Mn₂O₃ + O²⁻
    //   Electrolyte: NaOH(aq) — substitutes KOH; identical alkaline role
    // -------------------------------------------------------------------------
    private static void zincManganeseCellRecipes() {

        // Step 1 — Mixer: grind and blend anode + cathode powders
        // Zinc dust (anode) + Pyrolusite (MnO₂, cathode) → dry electrode mix
        MIXER_RECIPES.recipeBuilder()
                .input(dust, Zinc, 4)
                .input(dust, Pyrolusite, 8)
                .output(dust, ZincManganeseMix, 12)
                .duration(200).EUt(VA[LV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: alkaline activation of the electrode mix
        // Dry mix + NaOH powder + water → electrolyte-saturated fluid paste
        // Output is 1440 mB (= 1 bucket) of ZincManganesePaste fluid per 12 dust
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, ZincManganeseMix, 12)
                .input(dust, SodiumHydroxide, 3)
                .fluidInputs(Water.getFluid(1000))
                .fluidOutputs(ZincManganesePaste.getFluid(1440))
                .duration(200).EUt(VA[LV])
                .buildAndRegister();

        // Step 3 — Assembler: build the structural steel casing with terminals pre-installed
        // Iron frame provides rigid structure; plates form the body; PE seals it;
        // Tin cable terminals are welded in during hull fabrication
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Iron)
                .input(plate, Iron, 4)
                .input(cableGtSingle, Tin, 2)
                .fluidInputs(Polyethylene.getFluid(144))
                .output(MetaItems.ZINC_MANGANESE_CELL_HULL)
                .duration(100).EUt(VA[LV])
                .buildAndRegister();

        // Step 4 — Canner: fill hull with charged electrode paste and seal
        // The Battery Accumulator charges the uncharged paste into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.ZINC_MANGANESE_CELL_HULL.getStackForm())
                .fluidInputs(ChargedZincManganesePaste.getFluid(10000))
                .outputs(MetaTileEntities.ZINC_MANGANESE_CELL.getStackForm())
                .duration(100).EUt(VA[LV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A1 — Lithium-Manganese Battery Block (MV)
    //
    // Real chemistry: CR-series primary lithium cell
    //   Anode:   Li → Li⁺ + e⁻
    //   Cathode: MnO₂ + Li⁺ + e⁻ → LiMnO₂
    //   Electrolyte: non-aqueous organic (represented by Polyethylene seal)
    // -------------------------------------------------------------------------
    private static void lithiumManganeseCellRecipes() {

        // Step 1 — Mixer: blend lithium anode powder with manganese dioxide cathode powder
        // Li dust + Pyrolusite (MnO₂) dust → dry electrode mix; no solvent needed at this stage
        MIXER_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, Pyrolusite, 8)
                .output(dust, LithiumManganeseMix, 10)
                .duration(200).EUt(VA[MV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: disperse electrode mix into non-aqueous electrolyte carrier
        // Dissolving in Propylene Carbonate (represented by Polyethylene fluid) creates a
        // pumpable slurry suitable for cell filling; 1200 mB per 10 dust batch
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, LithiumManganeseMix, 10)
                .fluidInputs(Polyethylene.getFluid(576))
                .fluidOutputs(LithiumManganesePaste.getFluid(1200))
                .duration(200).EUt(VA[MV])
                .buildAndRegister();

        // Step 3 — Assembler: build the steel casing with MV-grade Tin cable terminals
        // Steel frame + plates form the body; extra Tin cables provide 4-ampere output rating;
        // Polyethylene seals the hull seams against electrolyte leakage
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Steel)
                .input(plate, Steel, 4)
                .input(cableGtSingle, Tin, 4)
                .fluidInputs(Polyethylene.getFluid(144))
                .output(MetaItems.LITHIUM_MANGANESE_CELL_HULL)
                .duration(150).EUt(VA[MV])
                .buildAndRegister();

        // Step 4 — Canner: fill hull with charged electrode paste and seal
        // The Battery Accumulator charges the uncharged paste into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.LITHIUM_MANGANESE_CELL_HULL.getStackForm())
                .fluidInputs(ChargedLithiumManganesePaste.getFluid(10000))
                .outputs(MetaTileEntities.LITHIUM_MANGANESE_CELL.getStackForm())
                .duration(150).EUt(VA[MV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A2 — Nickel-Cadmium Battery Block (HV)
    //
    // Real chemistry: sealed NiCd alkaline cell
    //   Anode:   Cd + 2 OH⁻ → Cd(OH)₂ + 2 e⁻
    //   Cathode: 2 NiOOH + 2 H₂O + 2 e⁻ → 2 Ni(OH)₂ + 2 OH⁻
    //   Electrolyte: KOH(aq) — represented here by NaOH(aq) (same alkaline function)
    //
    // Pipeline (6 steps):
    //   Chem 1 — synthesise Ni(OH)₂ cathode active material
    //   Chem 2 — bond Cd anode with Ni(OH)₂ → solid electrode plate pair
    //   Chem 3 — dissolve NaOH in water → alkaline electrolyte fluid (independent of electrodes)
    //   Hull   — assemble casing with electrodes pre-loaded inside
    //   Fill   — inject electrolyte fluid into filled hull and seal
    // -------------------------------------------------------------------------
    private static void nickelCadmiumCellRecipes() {

        // Step 1 — Chemical Reactor: synthesise Ni(OH)₂ cathode powder
        // Nickel precipitates as hydroxide in alkaline solution:
        //   Ni + 2 NaOH + 2 H₂O → Ni(OH)₂↓ + 2 NaOH (catalytic base excess simplified)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Nickel, 2)
                .input(dust, SodiumHydroxide, 2)
                .fluidInputs(Water.getFluid(1000))
                .output(dust, NickelHydroxide, 4)
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: bond cadmium anode with nickel hydroxide cathode layer
        // Represents the electrode manufacturing step where Cd metal and Ni(OH)₂ powder
        // are pressed and sintered into a unified electrode plate pair:
        //   Cd(2) + Ni(OH)₂(4) → NiCd electrode pair(4)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Cadmium, 2)
                .input(dust, NickelHydroxide, 4)
                .output(dust, NickelCadmiumElectrode, 4)
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        // Step 3 — Mixer: prepare the pure alkaline electrolyte fluid (independent of electrodes)
        // NaOH dissolved in water forms the KOH-equivalent alkaline electrolyte carrier.
        // Electrodes remain solid and are loaded separately in the Assembler (Step 4).
        //   NaOH(4) + H₂O(2000) → NickelCadmiumElectrolyte(2400 mB)
        MIXER_RECIPES.recipeBuilder()
                .input(dust, SodiumHydroxide, 4)
                .fluidInputs(Water.getFluid(2000))
                .fluidOutputs(NickelCadmiumElectrolyte.getFluid(2400))
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        // Step 4 — Assembler: build the stainless steel casing with electrodes pre-loaded inside
        // Stainless Steel frame withstands the alkaline environment; doubled copper cables deliver
        // the higher 2 048 EU/t current; NiCd electrode pairs are seated and welded in;
        // Polyethylene seals the hull seams before electrolyte injection
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, StainlessSteel)
                .input(plate, StainlessSteel, 4)
                .input(cableGtDouble, Copper, 4)
                .input(dust, NickelCadmiumElectrode, 4)
                .fluidInputs(Polyethylene.getFluid(288))
                .output(MetaItems.NICKEL_CADMIUM_CELL_HULL)
                .duration(200).EUt(VA[HV])
                .buildAndRegister();

        // Step 5 — Canner: inject charged alkaline electrolyte fluid into electrode-loaded hull and seal
        // The Battery Accumulator charges the uncharged electrolyte into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.NICKEL_CADMIUM_CELL_HULL.getStackForm())
                .fluidInputs(ChargedNickelCadmiumElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.NICKEL_CADMIUM_CELL.getStackForm())
                .duration(200).EUt(VA[HV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A3 — Lead-Acid Battery Block (EV)
    //
    // Real chemistry: lead-acid (flooded) cell
    //   Anode:   Pb → PbSO₄ + 2 e⁻
    //   Cathode: PbO₂ + 4 H⁺ + SO₄²⁻ + 2 e⁻ → PbSO₄ + 2 H₂O
    //   Electrolyte: H₂SO₄(aq) — dilute sulfuric acid
    //
    // Pipeline (5 steps):
    //   Chem 1 — chemical bath: immerse lead plates in sulfuric acid → PbO₂/Pb electrode pair
    //   Chem 2 — mixer: dilute sulfuric acid with water → lead-acid electrolyte fluid
    //   Chem 3 — chemical reactor: form lead oxide coating (forming charge step)
    //   Hull   — assembler: build titanium casing with glass plates and electrodes
    //   Fill   — canner: inject electrolyte into hull and seal
    // -------------------------------------------------------------------------
    private static void leadAcidBatteryRecipes() {

        // Step 1 — Chemical Bath: immerse lead plates in concentrated sulfuric acid
        // This represents the "formation" process where lead is partially oxidised to PbO₂
        // on one plate while the other remains as pure Pb, creating the electrode pair:
        //   Pb(plate, 6) + H₂SO₄(2000 mB) → LeadAcidElectrode(6)
        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(plate, Lead, 6)
                .fluidInputs(SulfuricAcid.getFluid(2000))
                .output(dust, LeadAcidElectrode, 6)
                .duration(400).EUt(VA[MV])
                .buildAndRegister();

        // Step 2 — Mixer: dilute sulfuric acid with water to produce the electrolyte fluid
        // Real lead-acid batteries use ~37% H₂SO₄(aq); excess water lowers specific gravity
        // to operational range (~1.265 g/cm³ at full charge):
        //   H₂SO₄(1500 mB) + H₂O(1500 mB) → LeadAcidElectrolyte(3000 mB)
        MIXER_RECIPES.recipeBuilder()
                .fluidInputs(SulfuricAcid.getFluid(1500))
                .fluidInputs(Water.getFluid(1500))
                .fluidOutputs(LeadAcidElectrolyte.getFluid(3000))
                .duration(200).EUt(VA[MV])
                .buildAndRegister();

        // Step 3 — Chemical Reactor: electrochemical forming charge
        // Passes current through the electrode pair in dilute acid to fully develop
        // the PbO₂ cathode layer; the electrode pair is "activated" and ready for use:
        //   LeadAcidElectrode(6) + H₂SO₄(500 mB) → LeadAcidElectrode(6, formed)
        // (In-game, this is modelled as consuming electrodes + acid → same output,
        //  to represent the non-trivial energy-intensive forming step)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, LeadAcidElectrode, 6)
                .fluidInputs(SulfuricAcid.getFluid(500))
                .output(dust, LeadAcidElectrode, 8)
                .duration(300).EUt(VA[HV])
                .buildAndRegister();

        // Step 4 — Assembler: build the titanium casing with glass separators and electrodes
        // Titanium frame provides the EV-grade structural integrity; glass plates act as
        // separator sheets between electrode pairs; aluminium double cables provide
        // 8 192 EU/t rated output terminals; electrodes are seated inside;
        // Polyethylene seals the hull before electrolyte injection
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Titanium)
                .input(plate, Glass, 4)
                .input(cableGtDouble, Aluminium, 4)
                .input(dust, LeadAcidElectrode, 8)
                .fluidInputs(Polyethylene.getFluid(576))
                .output(MetaItems.LEAD_ACID_BATTERY_HULL)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        // Step 5 — Canner: inject charged dilute sulfuric acid electrolyte into electrode-loaded hull
        // The Battery Accumulator charges the uncharged electrolyte into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.LEAD_ACID_BATTERY_HULL.getStackForm())
                .fluidInputs(ChargedLeadAcidElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.LEAD_ACID_BATTERY.getStackForm())
                .duration(300).EUt(VA[EV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A4 — Vanadium Redox Flow Battery Block (IV)
    //
    // Real chemistry: All-Vanadium Redox Flow Battery (VRFB)
    //   Positive half-cell: VO₂⁺ + 2 H⁺ + e⁻ ⇌ VO²⁺ + H₂O  (V⁵⁺/V⁴⁺)
    //   Negative half-cell: V³⁺ + e⁻ ⇌ V²⁺
    //   Electrolyte: V₂O₅ dissolved in dilute H₂SO₄
    //   Membrane: ion-selective membrane (Nafion-type) separates half-cells
    //
    // Pipeline (7 steps):
    //   Chem 1 — chemical reactor: oxidise vanadium dust to V₂O₅ (vanadium pentoxide)
    //   Chem 2 — chemical reactor: dissolve V₂O₅ in H₂SO₄ → vanadium electrolyte fluid
    //   Chem 3 — chemical reactor: impregnate carbon felt with vanadium catalyst → electrode
    //   Chem 4 — chemical bath: sulphonate PTFE membrane → ion exchange membrane
    //   Hull   — assembler: build titanium casing with electrodes + membranes
    //   Fill   — canner: inject vanadium electrolyte and seal
    // -------------------------------------------------------------------------
    private static void vanadiumFlowCellRecipes() {

        // Step 1 — Chemical Reactor: oxidise vanadium metal to V₂O₅
        // In reality, vanadium is roasted in air to form the pentoxide;
        // here we use oxygen fluid as the oxidant:
        //   V(dust, 2) + O₂(5000 mB) → V₂O₅(dust, 4)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Vanadium, 2)
                .fluidInputs(Oxygen.getFluid(5000))
                .output(dust, VanadiumPentoxide, 4)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: dissolve V₂O₅ in sulfuric acid to produce electrolyte
        // The active vanadium species are dissolved into the acid carrier;
        // output is a coloured fluid representing the mixed V²⁺/V³⁺/V⁴⁺/V⁵⁺ solution:
        //   V₂O₅(dust, 4) + H₂SO₄(3000 mB) → VanadiumElectrolyte(3000 mB)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, VanadiumPentoxide, 4)
                .fluidInputs(SulfuricAcid.getFluid(3000))
                .fluidOutputs(VanadiumElectrolyte.getFluid(3000))
                .duration(400).EUt(VA[EV])
                .buildAndRegister();

        // Step 3 — Chemical Reactor: impregnate graphite felt with vanadium catalyst
        // Carbon felt acts as the electrode substrate; vanadium pentoxide provides
        // catalytic surface sites for the redox reactions:
        //   Graphite(dust, 4) + V₂O₅(dust, 2) → VanadiumFlowElectrode(dust, 6)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Graphite, 4)
                .input(dust, VanadiumPentoxide, 2)
                .output(dust, VanadiumFlowElectrode, 6)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        // Step 4 — Chemical Bath: sulphonate PTFE film to produce ion exchange membrane
        // Nafion-type membranes are perfluorosulphonic acid polymers; here PTFE is treated
        // with sulfuric acid to introduce sulphonate groups for proton conductivity:
        //   PTFE(fluid, 576 mB) + H₂SO₄(1000 mB) → Ion Exchange Membrane(2)
        CHEMICAL_RECIPES.recipeBuilder()
                .fluidInputs(Polytetrafluoroethylene.getFluid(576))
                .fluidInputs(SulfuricAcid.getFluid(1000))
                .output(MetaItems.ION_EXCHANGE_MEMBRANE, 2)
                .duration(400).EUt(VA[HV])
                .buildAndRegister();

        // Step 5 — Assembler: build titanium casing with electrodes and membranes
        // Titanium frame + plates for IV-grade structural integrity;
        // tungsten double cables for high-current output terminals;
        // ion exchange membranes separate the two half-cell compartments;
        // vanadium flow electrodes seated inside; PTFE seals the hull
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Titanium)
                .input(plate, Titanium, 4)
                .input(cableGtDouble, Tungsten, 4)
                .inputs(MetaItems.ION_EXCHANGE_MEMBRANE.getStackForm(2))
                .input(dust, VanadiumFlowElectrode, 6)
                .fluidInputs(Polytetrafluoroethylene.getFluid(576))
                .output(MetaItems.VANADIUM_FLOW_CELL_HULL)
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // Step 6 — Canner: inject charged vanadium electrolyte into the assembled flow cell hull
        // The Battery Accumulator charges the uncharged electrolyte into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.VANADIUM_FLOW_CELL_HULL.getStackForm())
                .fluidInputs(ChargedVanadiumElectrolyte.getFluid(10000))
                .outputs(MetaTileEntities.VANADIUM_FLOW_CELL.getStackForm())
                .duration(400).EUt(VA[IV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A5 — Lithium Iron Phosphate (LFP) Battery Block (LuV)
    //
    // Real chemistry: olivine-structure LiFePO₄ intercalation cathode
    //   Cathode: LiFePO₄ ⇌ FePO₄ + Li⁺ + e⁻
    //   Anode:   graphite intercalation (Li⁺ + e⁻ + C₆ → LiC₆)
    //   Electrolyte: LiPF₆ in organic solvent (simplified to PBI polymer seal)
    //
    // Pipeline (6 steps):
    //   Chem 1 — chemical reactor: iron + phosphoric acid → iron III phosphate
    //   Chem 2 — chemical reactor: lithium + iron III phosphate → LFP cathode powder
    //   Chem 3 — chemical reactor: carbon + iron catalyst → carbon nanotube film
    //   Chem 4 — mixer: LFP cathode powder + CNT film binder activation
    //   Hull   — assembler: build iridium casing with cathode powder + CNT film
    //   Fill   — canner: seal with polybenzimidazole high-temperature polymer
    // -------------------------------------------------------------------------
    private static void lfpBatteryRecipes() {

        // Step 1 — Chemical Reactor: precipitate iron III phosphate from iron and phosphoric acid
        // FePO₄ is the delithiated cathode framework structure:
        //   Fe(dust, 4) + H₃PO₄(2000 mB) → FePO₄(dust, 8) + H₂(fluid, byproduct simplified)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Iron, 4)
                .fluidInputs(PhosphoricAcid.getFluid(2000))
                .output(dust, IronIIIPhosphate, 8)
                .duration(300).EUt(VA[EV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: lithiate iron phosphate to form LFP cathode material
        // Lithium intercalates into the FePO₄ olivine framework:
        //   Li(dust, 4) + FePO₄(dust, 8) → LiFePO₄ cathode powder(dust, 12)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 4)
                .input(dust, IronIIIPhosphate, 8)
                .output(dust, LFPCathodePowder, 12)
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // Step 3 — Chemical Reactor: catalytic CVD growth of carbon nanotubes on substrate
        // Iron nanoparticles catalyse the decomposition of carbon into tubular structures;
        // the result is a thin conductive film used as current collector:
        //   Carbon(dust, 8) + Iron(dustSmall, 2 — catalyst) + H₂(1000 mB carrier)
        //   → CarbonNanotubeFilm(dust, 4)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Carbon, 8)
                .input(dustSmall, Iron, 2)
                .fluidInputs(Hydrogen.getFluid(1000))
                .output(dust, CarbonNanotubeFilm, 4)
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // Step 4 — Assembler: build the iridium casing with cathode and current collectors
        // Iridium frame + plates provide LuV-grade structural integrity;
        // tungsten quadruple cables deliver 131 072 EU/t rated output;
        // LFP cathode powder and CNT film are layered inside the cell;
        // Polybenzimidazole seals the hull at high temperature
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Iridium)
                .input(plate, Iridium, 4)
                .input(cableGtQuadruple, Tungsten, 4)
                .input(dust, LFPCathodePowder, 12)
                .input(dust, CarbonNanotubeFilm, 2)
                .fluidInputs(Polybenzimidazole.getFluid(576))
                .output(MetaItems.LFP_BATTERY_HULL)
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();

        // Step 5 — Canner: final charged electrolyte injection and hermetic seal
        // The Battery Accumulator charges the PBI polymer into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.LFP_BATTERY_HULL.getStackForm())
                .fluidInputs(ChargedPolybenzimidazole.getFluid(10000))
                .outputs(MetaTileEntities.LFP_BATTERY.getStackForm())
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A6 — Lithium Cobalt Oxide (LCO) Battery Block (ZPM)
    //
    // Real chemistry: layered LiCoO₂ intercalation cathode
    //   Cathode: LiCoO₂ → Li₁₋ₓCoO₂ + x Li⁺ + x e⁻
    //   Anode:   graphite intercalation (x Li⁺ + x e⁻ + C₆ → LiₓC₆)
    //   Electrolyte: LiPF₆ in organic solvent; binder: PVDF
    //
    // Pipeline (5 steps):
    //   Chem 1 — chemical reactor: lithium + cobalt oxide → lithium cobalt oxide
    //   Chem 2 — chemical reactor: synthesise PVDF binder fluid
    //   Chem 3 — chemical reactor: coat LiCoO₂ with PVDF binder → electrode slurry
    //   Hull   — assembler: build osmium casing with cathode + CNT collectors
    //   Fill   — canner: inject PVDF-sealed electrolyte and seal
    // -------------------------------------------------------------------------
    private static void lcoBatteryRecipes() {

        // Step 1 — Chemical Reactor: solid-state synthesis of LiCoO₂ cathode powder
        // Lithium reacts with cobalt oxide at high temperature to form the layered structure:
        //   Li(dust, 2) + CoO(dust, 4) → LiCoO₂(dust, 6)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .input(dust, CobaltOxide, 4)
                .output(dust, LithiumCobaltOxide, 6)
                .duration(500).EUt(VA[LuV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: polymerise VDF monomer into PVDF binder fluid
        // In reality PVDF is produced by radical polymerisation of CH₂=CF₂;
        // here simplified as fluorine + polyethylene decomposition route:
        //   Polyethylene(fluid, 576 mB) + Fluorine(fluid, 2000 mB)
        //   → PVDF(fluid, 1000 mB)
        CHEMICAL_RECIPES.recipeBuilder()
                .fluidInputs(Polyethylene.getFluid(576))
                .fluidInputs(Fluorine.getFluid(2000))
                .fluidOutputs(PVDF.getFluid(1000))
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // Step 3 — Assembler: build the osmium casing with cathode, CNT collectors and wiring
        // Osmium frame + plates provide ZPM-grade structural integrity;
        // naquadah quadruple cables deliver the extreme 524 288 EU/t current;
        // LiCoO₂ cathode powder is layered with CNT film current collectors;
        // PVDF binder fluid bonds the electrode layers inside the hull
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Osmium)
                .input(plate, Osmium, 4)
                .input(cableGtQuadruple, Naquadah, 4)
                .input(dust, LithiumCobaltOxide, 12)
                .input(dust, CarbonNanotubeFilm, 4)
                .fluidInputs(PVDF.getFluid(576))
                .output(MetaItems.LCO_BATTERY_HULL)
                .duration(600).EUt(VA[ZPM])
                .buildAndRegister();

        // Step 4 — Canner: inject charged PVDF electrolyte binder and hermetically seal
        // The Battery Accumulator charges the PVDF binder into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.LCO_BATTERY_HULL.getStackForm())
                .fluidInputs(ChargedPVDF.getFluid(10000))
                .outputs(MetaTileEntities.LCO_BATTERY.getStackForm())
                .duration(600).EUt(VA[ZPM])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // A7 — NMC Ternary Lithium Battery Block (UV)
    //
    // Real chemistry: layered Li(NiₓMnᵧCo_z)O₂ (NMC 811/622/532 family)
    //   Cathode: Li(NiMnCo)O₂ → Li₁₋ₓ(NiMnCo)O₂ + x Li⁺ + x e⁻
    //   Anode:   Si/C composite intercalation
    //   Electrolyte: LiPF₆ in organic carbonate solvent
    //
    // Pipeline (6 steps):
    //   Chem 1 — chemical reactor: Ni + Mn + Co oxide → NMC precursor
    //   Chem 2 — chemical reactor: lithiate NMC precursor → NMC cathode powder
    //   Chem 3 — chemical reactor: LiF + PF₅ equivalent → LiPF₆ electrolyte fluid
    //   Hull   — assembler: build darmstadtium casing with cathode + CNT + wiring
    //   Fill   — canner: inject LiPF₆ electrolyte and seal
    // -------------------------------------------------------------------------
    private static void nmcBatteryRecipes() {

        // Step 1 — Chemical Reactor: co-precipitate ternary NMC precursor hydroxide
        // Nickel, manganese and cobalt oxides react to form the mixed transition metal
        // hydroxide precursor in an 8:1:1 ratio (NMC 811 stoichiometry):
        //   Ni(dust, 4) + Mn(dust, 1) + CobaltOxide(dust, 1) + O₂(2000 mB)
        //   → NMCCathodePowder(dust, 6)  (precursor stage)
        LARGE_CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Nickel, 4)
                .input(dust, Manganese, 1)
                .input(dust, CobaltOxide, 1)
                .fluidInputs(Oxygen.getFluid(2000))
                .output(dust, NMCCathodePowder, 6)
                .duration(400).EUt(VA[LuV])
                .buildAndRegister();

        // Step 2 — Chemical Reactor: lithiate NMC precursor at high temperature
        // Lithium intercalates into the layered NMC oxide framework:
        //   NMCCathodePowder(dust, 6) + Li(dust, 4) → NMCCathodePowder(dust, 12)
        // (doubled output represents the fully lithiated, activated cathode material)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, NMCCathodePowder, 6)
                .input(dust, Lithium, 4)
                .output(dust, NMCCathodePowder, 12)
                .duration(500).EUt(VA[ZPM])
                .buildAndRegister();

        // Step 3 — Chemical Reactor: synthesise LiPF₆ electrolyte salt solution
        // Lithium fluoride reacts with phosphorus pentafluoride (simplified as
        // HydrofluoricAcid + PhosphoricAcid route) to form LiPF₆ in solution:
        //   Li(dust, 2) + HF(2000 mB) + H₃PO₄(1000 mB)
        //   → LithiumHexafluorophosphate(3000 mB)
        CHEMICAL_RECIPES.recipeBuilder()
                .input(dust, Lithium, 2)
                .fluidInputs(HydrofluoricAcid.getFluid(2000))
                .fluidInputs(PhosphoricAcid.getFluid(1000))
                .fluidOutputs(LithiumHexafluorophosphate.getFluid(3000))
                .duration(400).EUt(VA[IV])
                .buildAndRegister();

        // Step 4 — Assembler: build the darmstadtium casing with cathode and collectors
        // Darmstadtium frame + plates provide UV-grade structural integrity;
        // europium quadruple cables deliver the extreme 2 097 152 EU/t current;
        // NMC cathode powder and CNT film current collectors are layered inside;
        // PVDF binder bonds the electrode stack
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, Darmstadtium)
                .input(plate, Darmstadtium, 4)
                .input(cableGtQuadruple, Europium, 4)
                .input(dust, NMCCathodePowder, 12)
                .input(dust, CarbonNanotubeFilm, 4)
                .fluidInputs(PVDF.getFluid(576))
                .output(MetaItems.NMC_BATTERY_HULL)
                .duration(800).EUt(VA[UV])
                .buildAndRegister();

        // Step 5 — Canner: inject charged LiPF₆ electrolyte into the sealed hull
        // The Battery Accumulator charges the LiPF₆ electrolyte into an energised form;
        // only charged electrolyte can power a disposable battery block
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.NMC_BATTERY_HULL.getStackForm())
                .fluidInputs(ChargedLithiumHexafluorophosphate.getFluid(10000))
                .outputs(MetaTileEntities.NMC_BATTERY.getStackForm())
                .duration(800).EUt(VA[UV])
                .buildAndRegister();
    }

    // -------------------------------------------------------------------------
    // Battery Accumulator — JEI display recipes
    //
    // These recipes are registered in BATTERY_ACCUMULATOR_RECIPES for JEI display
    // only. They show the EU per bucket (1000 mB) for each electrolyte type,
    // in both charge and discharge directions.
    //
    // The actual processing logic in MetaTileEntityBatteryAccumulator handles
    // per-mB conversion with loss ratio; these recipes serve as reference info.
    // -------------------------------------------------------------------------

    /** Loss ratio used for recipe display (must match the controller default). */
    private static final double DISPLAY_LOSS_RATIO = 0.10;

    private static void batteryAccumulatorRecipes() {
        for (BatteryAccumulatorFluidMapping mapping : BatteryAccumulatorFluidMapping.values()) {
            registerChargeRecipe(mapping);
            registerDischargeRecipe(mapping);
        }
    }

    /**
     * Registers a charge-mode JEI recipe for the given electrolyte mapping.
     * Shows: 1000 mB uncharged → 1000 mB charged, with EU cost including loss.
     */
    private static void registerChargeRecipe(BatteryAccumulatorFluidMapping mapping) {
        long euPerBucket = mapping.getEuPerBucket();
        long euCostPerBucket = (long) (euPerBucket / (1.0 - DISPLAY_LOSS_RATIO));

        // Use the tier voltage as EUt so JEI shows the correct tier
        int tier = getTierForMapping(mapping);
        int euT = GTValues.V[tier];
        int duration = (int) Math.max(1, euCostPerBucket / euT);

        BATTERY_ACCUMULATOR_RECIPES.recipeBuilder()
                .fluidInputs(mapping.getUnchargedFluidStack(1000))
                .fluidOutputs(mapping.getChargedFluidStack(1000))
                .duration(duration)
                .EUt(euT)
                .buildAndRegister();
    }

    /**
     * Registers a discharge-mode JEI recipe for the given electrolyte mapping.
     * Shows: 1000 mB charged → 1000 mB uncharged, with EU output after loss.
     */
    private static void registerDischargeRecipe(BatteryAccumulatorFluidMapping mapping) {
        long euPerBucket = mapping.getEuPerBucket();
        long euOutputPerBucket = (long) (euPerBucket * (1.0 - DISPLAY_LOSS_RATIO));

        int tier = getTierForMapping(mapping);
        int euT = GTValues.V[tier];
        int duration = (int) Math.max(1, euOutputPerBucket / euT);

        BATTERY_ACCUMULATOR_RECIPES.recipeBuilder()
                .fluidInputs(mapping.getChargedFluidStack(1000))
                .fluidOutputs(mapping.getUnchargedFluidStack(1000))
                .duration(duration)
                .EUt(euT)
                .buildAndRegister();
    }

    /**
     * Maps each BatteryAccumulatorFluidMapping to its voltage tier.
     * This determines the EUt shown in JEI recipes.
     */
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
