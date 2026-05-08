package gregtech.loaders.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.properties.impl.FogMultiStepProperty;
import gregtech.api.recipes.properties.impl.FogPlasmaTierProperty;
import gregtech.api.recipes.properties.impl.FogUpgradeNameProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTLog;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;

/**
 * Loads recipes for Forge of the Gods modules:
 * - Plasma module: auto-generates dust→plasma recipes from all materials that have plasma fluid
 * - Molten module: generates blast furnace derivative recipes with molten outputs
 * - Exotic module: recipe generation is dynamic (handled in module logic)
 * - Smelting module: uses BLAST_RECIPES and ARC_FURNACE_RECIPES directly
 */
public class GodforgeRecipeLoader {

    private static final int TICKS = 1;
    private static final int SECONDS = 20;
    private static final int INGOTS = 144;

    // Plasma recipe base EUt (effectively free due to wireless energy)
    private static final int PLASMA_RECIPE_EUT = Integer.MAX_VALUE;
    // Plasma recipe base duration
    private static final int PLASMA_RECIPE_DURATION = 10 * TICKS;

    // Collected data for exotic module use
    public static final List<Material> plasmaMaterials = new ArrayList<>();

    private GodforgeRecipeLoader() {}

    public static void init() {
        registerPlasmaRecipes();
        registerMoltenRecipes();
        registerUpgradeCostRecipes();
    }

    // ==================== Plasma Module Recipes (C3) ====================

    /**
     * Automatically generates dust→plasma recipes for ALL materials that have:
     * 1. A plasma fluid registered
     * 2. A dust OrePrefix form
     * This allows modpack developers to simply add plasma to a material and the godforge
     * will automatically pick it up.
     */
    private static void registerPlasmaRecipes() {
        int registered = 0;

        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            // Must have dust form (input)
            if (!material.hasProperty(PropertyKey.DUST)) continue;

            // Must have fluid property first, otherwise getFluid(...) may throw
            if (!material.hasProperty(PropertyKey.FLUID)) continue;

            // Must have plasma fluid (output)
            FluidStack plasmaStack;
            try {
                plasmaStack = material.getPlasma(INGOTS);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (plasmaStack == null) continue;

            // Determine plasma tier based on proton count
            int tier = FogPlasmaTierProperty.getTierForProtons(material.getProtons());
            // Multi-step flag: non-elemental materials (alloys/compounds) require multi-step processing
            boolean multiStep = !material.isElement();

            RecipeBuilder<?> builder = GodforgeRecipeMaps.GODFORGE_PLASMA_RECIPES.recipeBuilder()
                    .input(OrePrefix.dust, material)
                    .fluidOutputs(plasmaStack)
                    .duration(PLASMA_RECIPE_DURATION)
                    .EUt(PLASMA_RECIPE_EUT);
            builder.applyProperty(FogPlasmaTierProperty.getInstance(), tier);
            builder.applyProperty(FogMultiStepProperty.getInstance(), multiStep);
            builder.buildAndRegister();

            plasmaMaterials.add(material);
            registered++;
        }

        GTLog.logger.info("GodforgeRecipeLoader: Registered {} plasma recipes", registered);
    }

    // ==================== Molten Module Recipes (C2) ====================

    /**
     * Generates molten module recipes by scanning BLAST_RECIPES and converting
     * solid outputs to their molten (fluid) equivalents.
     * Only creates recipes for materials that have both ingot/dust and fluid forms.
     */
    private static void registerMoltenRecipes() {
        int registered = 0;

        for (Recipe blastRecipe : RecipeMaps.BLAST_RECIPES.getRecipeList()) {
            // Only consider recipes with item outputs that have fluid forms
            if (blastRecipe.getOutputs().isEmpty()) continue;

            List<FluidStack> moltenOutputs = new ArrayList<>();
            boolean hasValidOutput = false;

            for (var itemOutput : blastRecipe.getOutputs()) {
                Material outputMaterial = getMaterialFromItemStack(itemOutput);
                if (outputMaterial != null && outputMaterial.hasFluid()) {
                    FluidStack molten = outputMaterial.getFluid(INGOTS * itemOutput.getCount());
                    if (molten != null) {
                        moltenOutputs.add(molten);
                        hasValidOutput = true;
                    }
                }
            }

            if (!hasValidOutput || moltenOutputs.isEmpty()) continue;

            var builder = GodforgeRecipeMaps.GODFORGE_MOLTEN_RECIPES.recipeBuilder();

            // Copy inputs from blast recipe
            builder.inputIngredients(blastRecipe.getInputs());
            builder.fluidInputs(blastRecipe.getFluidInputs());

            // Output as molten fluids instead of solid items
            builder.fluidOutputs(moltenOutputs.toArray(new FluidStack[0]));

            // Same duration/EUt as blast recipe but will be overclocked by module
            builder.duration(blastRecipe.getDuration());
            builder.EUt(blastRecipe.getEUt());

            // Propagate temperature requirement from original blast recipe
            int blastTemp = blastRecipe.getProperty(TemperatureProperty.getInstance(), 0);
            if (blastTemp > 0) {
                builder.applyProperty(TemperatureProperty.getInstance(), blastTemp);
            }

            builder.buildAndRegister();
            registered++;
        }

        GTLog.logger.info("GodforgeRecipeLoader: Registered {} molten recipes from blast furnace", registered);
    }

    /**
     * Attempts to determine the Material corresponding to an ItemStack output from a recipe.
     * Returns null if no matching material is found.
     */
    @org.jetbrains.annotations.Nullable
    private static Material getMaterialFromItemStack(net.minecraft.item.ItemStack stack) {
        var unificationEntry = gregtech.api.unification.OreDictUnifier.getUnificationEntry(stack);
        if (unificationEntry != null && unificationEntry.material != null) {
            return unificationEntry.material;
        }
        return null;
    }

    // ==================== Upgrade Cost Recipes (JEI Display) ====================

    /**
     * Registers fake recipes for display in JEI showing the material cost of key upgrades.
     * These recipes are display-only and not used for actual crafting (materials are inserted via the upgrade GUI).
     */
    private static void registerUpgradeCostRecipes() {
        addUpgradeMaterialCosts();
        addFakeUpgradeCostRecipes();
    }

    /**
     * Defines the material costs for key upgrades that require additional items.
     * Only upgrades that unlock major new functionality have extra material costs.
     */
    private static void addUpgradeMaterialCosts() {
        // START — Unlock basic functionality (T1 materials)
        ForgeOfGodsUpgrade.START.addExtraCost(
                OreDictUnifier.get(OrePrefix.plate, Materials.Darmstadtium, 64),
                OreDictUnifier.get(OrePrefix.frameGt, Materials.Naquadria, 16),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(4),
                MetaItems.SENSOR_UV.getStackForm(4));

        // FDIM — Unlock Melting Core module (T2 materials)
        ForgeOfGodsUpgrade.FDIM.addExtraCost(
                OreDictUnifier.get(OrePrefix.plate, Materials.Neutronium, 32),
                OreDictUnifier.get(OrePrefix.wireGtSingle, Materials.Naquadria, 64),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(8),
                MetaItems.EMITTER_UV.getStackForm(8));

        // GPCI — Unlock Plasma Fabricator module (T3 materials)
        ForgeOfGodsUpgrade.GPCI.addExtraCost(
                OreDictUnifier.get(OrePrefix.plate, Materials.Neutronium, 64),
                OreDictUnifier.get(OrePrefix.frameGt, Materials.Tritanium, 32),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(16),
                MetaItems.ROBOT_ARM_UV.getStackForm(8),
                MetaItems.SENSOR_UV.getStackForm(16));

        // QGPIU — Unlock Exoticizer module (T4 materials)
        ForgeOfGodsUpgrade.QGPIU.addExtraCost(
                OreDictUnifier.get(OrePrefix.block, Materials.Neutronium, 16),
                OreDictUnifier.get(OrePrefix.wireGtQuadruple, Materials.Tritanium, 64),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(32),
                MetaItems.EMITTER_UV.getStackForm(32),
                OreDictUnifier.get(OrePrefix.plate, Materials.Darmstadtium, 64));

        // CD — Unlock second ring (T5 materials)
        ForgeOfGodsUpgrade.CD.addExtraCost(
                OreDictUnifier.get(OrePrefix.block, Materials.Neutronium, 64),
                OreDictUnifier.get(OrePrefix.block, Materials.Tritanium, 32),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(64),
                MetaItems.EMITTER_UV.getStackForm(64),
                MetaItems.SENSOR_UV.getStackForm(64),
                OreDictUnifier.get(OrePrefix.frameGt, Materials.Darmstadtium, 64));

        // EE — Unlock Magmatter & exotic plasmas (T6 materials)
        ForgeOfGodsUpgrade.EE.addExtraCost(
                OreDictUnifier.get(OrePrefix.block, Materials.Neutronium, 64),
                OreDictUnifier.get(OrePrefix.block, Materials.Naquadria, 64),
                OreDictUnifier.get(OrePrefix.block, Materials.Darmstadtium, 64),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(64),
                MetaItems.ROBOT_ARM_UV.getStackForm(64));

        // END — Unlock third ring & graviton shard ejection (T7 materials)
        ForgeOfGodsUpgrade.END.addExtraCost(
                OreDictUnifier.get(OrePrefix.block, Materials.Neutronium, 64),
                OreDictUnifier.get(OrePrefix.block, Materials.Tritanium, 64),
                OreDictUnifier.get(OrePrefix.block, Materials.Naquadria, 64),
                OreDictUnifier.get(OrePrefix.block, Materials.Darmstadtium, 64),
                MetaItems.FIELD_GENERATOR_UV.getStackForm(64),
                MetaItems.EMITTER_UV.getStackForm(64),
                MetaItems.SENSOR_UV.getStackForm(64));
    }

    /**
     * Registers the fake recipes into the GODFORGE_UPGRADE_COST_RECIPES RecipeMap for JEI display.
     * Each recipe shows the material inputs needed and the upgrade name.
     */
    private static void addFakeUpgradeCostRecipes() {
        int registered = 0;

        for (ForgeOfGodsUpgrade upgrade : ForgeOfGodsUpgrade.VALUES) {
            if (!upgrade.hasExtraCost()) continue;

            RecipeBuilder<?> builder = GodforgeRecipeMaps.GODFORGE_UPGRADE_COST_RECIPES.recipeBuilder();

            for (ItemStack cost : upgrade.getExtraCostNoNulls()) {
                builder.inputs(cost);
            }

            // Use 0 EUt / 0 duration since this is just a display recipe
            builder.duration(0)
                    .EUt(0)
                    .applyProperty(FogUpgradeNameProperty.getInstance(), upgrade.getShortNameKey())
                    .buildAndRegister();

            registered++;
        }

        GTLog.logger.info("GodforgeRecipeLoader: Registered {} upgrade cost display recipes", registered);
    }
}
