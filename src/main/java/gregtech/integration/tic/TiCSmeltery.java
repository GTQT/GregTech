package gregtech.integration.tic;

import gregtech.api.GregTechAPI;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.integration.tic.api.SmelteryHelper;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import slimeknights.mantle.util.RecipeMatch;
import slimeknights.tconstruct.library.smeltery.MeltingRecipe;

/**
 * Registers TiC Smeltery melting recipes for GT materials.
 *
 * <p>
 * Processed forms (ingot, nugget, block, gem, dust variants) are always registered. Raw ore and crushed ore
 * registration is controlled by {@link TiCConfigHolder#smelteryOreMelting}. Whether raw ores yield double ingots is
 * controlled by {@link TiCConfigHolder#smelteryOreDoubling}; crushed ore variants always yield 1x regardless of that
 * flag.
 */
public final class TiCSmeltery {

    private static final int VALUE_Ingot = slimeknights.tconstruct.library.materials.Material.VALUE_Ingot;
    private static final int VALUE_Nugget = slimeknights.tconstruct.library.materials.Material.VALUE_Nugget;
    private static final int VALUE_Block = slimeknights.tconstruct.library.materials.Material.VALUE_Block;

    private TiCSmeltery() {}

    /**
     * Called during material registration to add smeltery melting recipes for all GT materials that carry a fluid
     * property.
     */
    public static void register() {
        for (Material gtMaterial : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (!gtMaterial.hasProperty(PropertyKey.FLUID)) continue;

            Fluid fluid = getFluid(gtMaterial);
            if (fluid == null) continue;

            int temp = getMeltingTemperature(gtMaterial);

            // Always: ingot-family
            tryRegister(OrePrefix.ingot, gtMaterial, fluid, VALUE_Ingot, temp);
            tryRegister(OrePrefix.nugget, gtMaterial, fluid, VALUE_Nugget, temp);
            tryRegister(OrePrefix.block, gtMaterial, fluid, VALUE_Block, temp);

            // Always: gem-family
            tryRegister(OrePrefix.gem, gtMaterial, fluid, VALUE_Ingot, temp);
            tryRegister(OrePrefix.gemFlawed, gtMaterial, fluid, VALUE_Ingot / 2, temp);
            tryRegister(OrePrefix.gemFlawless, gtMaterial, fluid, VALUE_Ingot * 2, temp);
            tryRegister(OrePrefix.gemExquisite, gtMaterial, fluid, VALUE_Ingot * 4, temp);

            // Always: dust-family (no doubling — already processed)
            tryRegister(OrePrefix.dust, gtMaterial, fluid, VALUE_Ingot, temp);
            tryRegister(OrePrefix.dustSmall, gtMaterial, fluid, VALUE_Ingot / 4, temp);
            tryRegister(OrePrefix.dustTiny, gtMaterial, fluid, VALUE_Ingot / 9, temp);

            int oreAmount = VALUE_Ingot * 2;
            tryRegister(OrePrefix.ore, gtMaterial, fluid, oreAmount, temp);
            tryRegister(OrePrefix.oreNetherrack, gtMaterial, fluid, oreAmount, temp);
            tryRegister(OrePrefix.oreEndstone, gtMaterial, fluid, oreAmount, temp);

            // Crushed ore — already processed once, always 1x regardless of doubling config
            tryRegister(OrePrefix.crushed, gtMaterial, fluid, VALUE_Ingot, temp);
            tryRegister(OrePrefix.crushedPurified, gtMaterial, fluid, VALUE_Ingot, temp);
            tryRegister(OrePrefix.crushedCentrifuged, gtMaterial, fluid, VALUE_Ingot, temp);
            tryRegister(OrePrefix.dustImpure, gtMaterial, fluid, VALUE_Ingot, temp);

        }
    }

    /**
     * Registers a melting recipe for the given ore prefix + material combination.
     *
     * <p>
     * Uses {@link OreDictUnifier#get(OrePrefix, Material)} to verify the item exists before registering. Uses
     * {@link MeltingRecipe} directly to specify the exact fluid output amount, bypassing TiC's global
     * {@code oreToIngotRatio} setting.
     *
     * <p>
     * External callers should use {@link SmelteryHelper#registerRecipe} rather than calling this method directly.
     */
    public static void tryRegister(OrePrefix prefix, Material material,
                                   Fluid fluid, int amount, int temp) {
        if (OreDictUnifier.get(prefix, material).isEmpty()) return;
        String oreDictName = prefix.name + material.toCamelCaseString();
        new MeltingRecipe(
                RecipeMatch.of(oreDictName, 1),
                new FluidStack(fluid, amount),
                temp).register();
    }

    /**
     * Returns the registered GT fluid for the material, or {@code null} if absent.
     */
    private static Fluid getFluid(Material material) {
        Fluid fluid = material.getFluid();
        return (FluidRegistry.isFluidRegistered(fluid)) ? fluid : null;
    }

    /**
     * Derives a TiC smeltery temperature from the GT blast temperature.
     *
     * <ul>
     * <li>No blast furnace / low-temp materials → 300 (standard smeltery)</li>
     * <li>EBF tier 1 (≥ 1000 K) → 600</li>
     * <li>EBF tier 2 (≥ 2500 K) → 900</li>
     * <li>Extreme materials (≥ 5000 K) → 1200</li>
     * </ul>
     *
     * <p>
     * External callers may use {@link SmelteryHelper#getDefaultTemperature}
     * to query this value without depending on internal classes.
     */
    public static int getMeltingTemperature(Material material) {
        int blastTemp = material.getBlastTemperature();
        if (blastTemp >= 5000) return 1200;
        if (blastTemp >= 2500) return 900;
        if (blastTemp >= 1000) return 600;
        return 300;
    }
}
