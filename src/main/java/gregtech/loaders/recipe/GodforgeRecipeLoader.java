package gregtech.loaders.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTLog;

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

            GodforgeRecipeMaps.GODFORGE_PLASMA_RECIPES.recipeBuilder()
                    .input(OrePrefix.dust, material)
                    .fluidOutputs(plasmaStack)
                    .duration(PLASMA_RECIPE_DURATION)
                    .EUt(PLASMA_RECIPE_EUT)
                    .buildAndRegister();

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

    // ==================== Upgrade Cost Recipes ====================

    private static void registerUpgradeCostRecipes() {
        // TODO: Register upgrade material costs when upgrade system is fully wired
    }
}
