package gregtech.loaders.recipe.chemistry;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;

public class FuelRecipeChains {

    public static void init() {
        // High Octane Gasoline
        LARGE_CHEMICAL_RECIPES.recipeBuilder().EUt(VA[HV]).duration(100)
                .fluidInputs(Naphtha.getFluid(16000))
                .fluidInputs(RefineryGas.getFluid(2000))
                .fluidInputs(Methanol.getFluid(1000))
                .fluidInputs(Acetone.getFluid(1000))
                .circuitMeta(24)
                .fluidOutputs(RawGasoline.getFluid(20000))
                .buildAndRegister();

        CHEMICAL_RECIPES.recipeBuilder().EUt(VA[HV]).duration(10)
                .fluidInputs(RawGasoline.getFluid(10000))
                .fluidInputs(Toluene.getFluid(1000))
                .fluidOutputs(Gasoline.getFluid(11000))
                .buildAndRegister();

        // Nitrous Oxide
        CHEMICAL_RECIPES.recipeBuilder().EUt(VA[LV]).duration(100)
                .fluidInputs(Nitrogen.getFluid(2000))
                .fluidInputs(Oxygen.getFluid(1000))
                .circuitMeta(4)
                .fluidOutputs(NitrousOxide.getFluid(1000))
                .buildAndRegister();

        // Anti-Knock Agent
        LARGE_CHEMICAL_RECIPES.recipeBuilder().EUt(VA[HV]).duration(SECOND)
                .fluidInputs(Butene.getFluid(1000))
                .fluidInputs(Methanol.getFluid(1000))
                .circuitMeta(24)
                .fluidOutputs(MTBEReactionMixtureButene.getFluid(1000))
                .buildAndRegister();

        LARGE_CHEMICAL_RECIPES.recipeBuilder().EUt(VA[HV]).duration(SECOND)
                .fluidInputs(Butane.getFluid(1000))
                .fluidInputs(Methanol.getFluid(1000))
                .circuitMeta(24)
                .fluidOutputs(MTBEReactionMixtureButane.getFluid(1000))
                .buildAndRegister();

        DISTILLATION_RECIPES.recipeBuilder().EUt(VA[MV]).duration(2 * SECOND)
                .fluidInputs(MTBEReactionMixtureButene.getFluid(900))
                .fluidOutputs(AntiKnockAgent.getFluid(400))
                .fluidOutputs(Methanol.getFluid(500))
                .fluidOutputs(Butene.getFluid(400))
                .buildAndRegister();

        DISTILLATION_RECIPES.recipeBuilder().EUt(VA[MV]).duration(2 * SECOND)
                .fluidInputs(MTBEReactionMixtureButane.getFluid(900))
                .fluidOutputs(AntiKnockAgent.getFluid(400))
                .fluidOutputs(Methanol.getFluid(500))
                .fluidOutputs(Butane.getFluid(400))
                .buildAndRegister();

        LARGE_CHEMICAL_RECIPES.recipeBuilder().EUt(VA[EV]).duration(2 * SECOND + 10 * TICK)
                .fluidInputs(Gasoline.getFluid(20000))
                .fluidInputs(Octane.getFluid(2000))
                .fluidInputs(NitrousOxide.getFluid(6000))
                .fluidInputs(Toluene.getFluid(1000))
                .fluidInputs(AntiKnockAgent.getFluid(3000))
                .circuitMeta(24)
                .fluidOutputs(HighOctaneGasoline.getFluid(32000))
                .buildAndRegister();

        // Nitrobenzene
        CHEMICAL_RECIPES.recipeBuilder().EUt(VA[HV]).duration(8 * SECOND)
                .fluidInputs(Benzene.getFluid(5000))
                .fluidInputs(NitrationMixture.getFluid(2000))
                .fluidInputs(DistilledWater.getFluid(2000))
                .fluidOutputs(Nitrobenzene.getFluid(8000))
                .fluidOutputs(DilutedSulfuricAcid.getFluid(1000))
                .buildAndRegister();
    }
}
