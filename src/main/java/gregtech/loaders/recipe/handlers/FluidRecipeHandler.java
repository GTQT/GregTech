package gregtech.loaders.recipe.handlers;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.CoolantProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.common.ConfigHolder;

import static gregtech.api.unification.material.Materials.*;

public class FluidRecipeHandler {

    public static void runRecipeGeneration() {
        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (material.hasProperty(PropertyKey.COOLANT))
                processCoolant(material, material.getProperty(PropertyKey.COOLANT));
        }
    }

    public static void processCooling() {
        // steam exhaust generator fuels 200:1
        RecipeMaps.NATURAL_DRAFT_COOLING_TOWER.recipeBuilder()
                .fluidInputs(SteamExhaust.getFluid(800))
                .fluidOutputs(DistilledWater.getFluid(4))
                .duration(1)
                .buildAndRegister();

        // steam generator fuels 160:1
        RecipeMaps.NATURAL_DRAFT_COOLING_TOWER.recipeBuilder()
                .fluidInputs(Steam.getFluid(640))
                .fluidOutputs(DistilledWater.getFluid(4))
                .duration(2)
                .buildAndRegister();

        // high pressure steam fuels 80:1
        RecipeMaps.NATURAL_DRAFT_COOLING_TOWER.recipeBuilder()
                .fluidInputs(HighPressureSteam.getFluid(320))
                .fluidOutputs(DistilledWater.getFluid(4))
                .duration(5)
                .buildAndRegister();

        // gregtech steam fuels 40:1
        RecipeMaps.NATURAL_DRAFT_COOLING_TOWER.recipeBuilder()
                .fluidInputs(SupercriticalSteam.getFluid(160))
                .fluidOutputs(DistilledWater.getFluid(4))
                .duration(10)
                .buildAndRegister();
    }

    /**
     * 低级配方，Core需要重写
     */
    public static void processCoolant(Material mat, CoolantProperty coolant) {
        int waterAmt = 6;
        double multiplier = ConfigHolder.nuclear.heatExchangerEfficiencyMultiplier;

        // water temp difference * water heat capacity * amount / coolantHeatCapacity * (hotHpTemp - coolantTemp)
        int coolantAmt = (int) Math.ceil(100 * 4168 * waterAmt * multiplier / (coolant.getSpecificHeatCapacity() *
                (coolant.getHotHPCoolant().getFluid().getTemperature() - mat.getFluid().getTemperature())));

        RecipeMaps.HEAT_EXCHANGER_RECIPES.recipeBuilder().duration(1).circuitMeta(1)
                .fluidInputs(coolant.getHotHPCoolant().getFluid(coolantAmt), Materials.Water.getFluid(waterAmt))
                .fluidOutputs(mat.getFluid(coolantAmt), Materials.Steam.getFluid(waterAmt * 160)).buildAndRegister();

        RecipeMaps.HEAT_EXCHANGER_RECIPES.recipeBuilder().duration(1).circuitMeta(1)
                .fluidInputs(coolant.getHotHPCoolant().getFluid(coolantAmt),
                        Materials.DistilledWater.getFluid(waterAmt))
                .fluidOutputs(mat.getFluid(coolantAmt), Materials.Steam.getFluid(waterAmt * 160)).buildAndRegister();
        waterAmt = 600;
        // Slightly more efficient
        coolantAmt = (int) Math.ceil(100 * 4168 * waterAmt * multiplier / (coolant.getSpecificHeatCapacity() *
                (coolant.getHotHPCoolant().getFluid().getTemperature() - mat.getFluid().getTemperature())));

        RecipeMaps.HEAT_EXCHANGER_RECIPES.recipeBuilder().duration(1).circuitMeta(2)
                .fluidInputs(coolant.getHotHPCoolant().getFluid(coolantAmt), Materials.Water.getFluid(waterAmt))
                .fluidOutputs(mat.getFluid(coolantAmt), Materials.Steam.getFluid(waterAmt * 160)).buildAndRegister();

        RecipeMaps.HEAT_EXCHANGER_RECIPES.recipeBuilder().duration(1).circuitMeta(2)
                .fluidInputs(coolant.getHotHPCoolant().getFluid(coolantAmt),
                        Materials.DistilledWater.getFluid(waterAmt))
                .fluidOutputs(mat.getFluid(coolantAmt), Materials.Steam.getFluid(waterAmt * 160)).buildAndRegister();

        // Radiator
        RecipeMaps.HEAT_EXCHANGER_RECIPES.recipeBuilder().duration(10).circuitMeta(3)
                .fluidInputs(coolant.getHotHPCoolant().getFluid(8000)).fluidOutputs(mat.getFluid(8000))
                .buildAndRegister();
    }
}
