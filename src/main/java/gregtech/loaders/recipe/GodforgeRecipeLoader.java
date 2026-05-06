package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTLog;
import net.minecraftforge.fluids.Fluid;

public class GodforgeRecipeLoader {

    private static final int TICKS = 1;
    private static final int SECONDS = 20;
    private static final int INGOTS = 144;

    private GodforgeRecipeLoader() {}

    public static void init() {
        registerPlasmaRecipes();
        registerUpgradeCostRecipes();
    }

    private static void registerPlasmaRecipes() {
        registerTier0SingleStepPlasmaRecipes();
        registerTier0MultiStepPlasmaRecipes();
        registerTier1SingleStepPlasmaRecipes();
    }

    private static void registerTier0SingleStepPlasmaRecipes() {
        Material[][] recipes = {
                { Materials.Aluminium, Materials.Aluminium },
                { Materials.Iron, Materials.Iron },
                { Materials.Copper, Materials.Copper },
                { Materials.Gold, Materials.Gold },
                { Materials.Silver, Materials.Silver },
                { Materials.Tin, Materials.Tin },
                { Materials.Zinc, Materials.Zinc },
                { Materials.Titanium, Materials.Titanium },
                { Materials.Nickel, Materials.Nickel },
                { Materials.Lead, Materials.Lead },
                { Materials.Tungsten, Materials.Tungsten },
                { Materials.Platinum, Materials.Platinum },
                { Materials.Iridium, Materials.Iridium },
                { Materials.Osmium, Materials.Osmium },
        };

        for (Material[] recipe : recipes) {
            Material material = recipe[0];
            Material plasmaMaterial = recipe[1];
            Fluid plasma = plasmaMaterial.hasFluid() ? plasmaMaterial.getFluid(FluidStorageKeys.PLASMA) : null;
            if (plasma == null) {
                GTLog.logger.warn("Skipping Godforge plasma recipe for {} because {} has no plasma fluid",
                    material.getResourceLocation(), plasmaMaterial.getResourceLocation());
                continue;
            }

            GodforgeRecipeMaps.GODFORGE_PLASMA_RECIPES.recipeBuilder()
                    .input(OrePrefix.dust, material)
                    .fluidOutputs(plasmaMaterial.getFluid(FluidStorageKeys.PLASMA, INGOTS))
                    .duration(10 * TICKS)
                    .EUt(Integer.MAX_VALUE)
                    .buildAndRegister();
        }
    }

    private static void registerTier0MultiStepPlasmaRecipes() {
        // TODO: 当以下材料添加等离子属性后，取消注释
        // Materials.Bismuth, Materials.Boron, Materials.Naquadah, Materials.Plutonium
    }

    private static void registerTier1SingleStepPlasmaRecipes() {
        // TODO: 当以下材料添加等离子属性后，取消注释
        // Materials.Thorium, Materials.Naquadria
    }

    private static void registerUpgradeCostRecipes() {
        // TODO: 当升级系统完善后，注册各模块升级消耗配方
        // 升级配方使用 GodforgeRecipeMaps.GODFORGE_UPGRADE_COST_RECIPES
    }
}
