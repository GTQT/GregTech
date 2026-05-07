package gregtech.loaders.recipe;

import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntities;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;

/**
 * Crafting recipes for all A-series disposable battery blocks.
 *
 * <p>Each battery follows a 4-step pipeline:
 * <ol>
 *   <li>Chemistry step 1 — produce active electrode dry blend (Mixer)</li>
 *   <li>Chemistry step 2 — alkaline activation to fluid paste (Chemical Reactor)</li>
 *   <li>Hull step — assemble casing with pre-installed terminals (Assembler)</li>
 *   <li>Fill step — fill hull with electrolyte paste and seal (Canner)</li>
 * </ol>
 */
public class DisposableBatteryRecipes {

    public static void init() {
        zincManganeseCellRecipes();
        lithiumManganeseCellRecipes();
        nickelCadmiumCellRecipes();
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

        // Step 4 — Canner: fill hull with electrode paste and seal
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.ZINC_MANGANESE_CELL_HULL.getStackForm())
                .fluidInputs(ZincManganesePaste.getFluid(1440))
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

        // Step 4 — Canner: fill hull with electrode paste and seal
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.LITHIUM_MANGANESE_CELL_HULL.getStackForm())
                .fluidInputs(LithiumManganesePaste.getFluid(1200))
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

        // Step 5 — Canner: inject alkaline electrolyte fluid into electrode-loaded hull and seal
        // Electrolyte wets the electrode surfaces and fills the void space; final crimp seals the cell
        CANNER_RECIPES.recipeBuilder()
                .inputs(MetaItems.NICKEL_CADMIUM_CELL_HULL.getStackForm())
                .fluidInputs(NickelCadmiumElectrolyte.getFluid(2400))
                .outputs(MetaTileEntities.NICKEL_CADMIUM_CELL.getStackForm())
                .duration(200).EUt(VA[HV])
                .buildAndRegister();
    }
}
