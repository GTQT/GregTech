package gregtech.integration.tic.api;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.integration.tic.TiCSmeltery;

import net.minecraftforge.fluids.Fluid;

/**
 * Helper for registering TiC Smeltery melting recipes for GT materials.
 *
 * <p>
 * Recipes registered here use the same OreDict-name derivation as GTMT's auto-generated recipes, so they integrate
 * consistently with the rest of GTMT's smeltery support.
 *
 * <p>
 * {@link #registerRecipe} may be called during {@code init} or {@code postInit}.
 */
public final class SmelteryHelper {

    private SmelteryHelper() {}

    /**
     * Registers a TiC Smeltery melting recipe for the given ore prefix and GT material.
     *
     * <p>
     * The recipe is only registered if the corresponding OreDict entry exists.
     *
     * <p>
     * Example — register a custom ore form for a third-party material:
     *
     * <pre>
     * {@code
     * SmelteryHelper.registerRecipe(
     *     OrePrefix.ore, MyMaterials.VIBRANIUM,
     *     MyMaterials.VIBRANIUM.getFluid(),
     *     Material.VALUE_Ingot,
     *     SmelteryHelper.getDefaultTemperature(MyMaterials.VIBRANIUM));
     * }
     * </pre>
     *
     * @param prefix   the ore prefix (e.g. {@link OrePrefix#ore}, {@link OrePrefix#ingot})
     * @param material the GT material
     * @param fluid    the result fluid
     * @param amount   output fluid amount in mB (use
     *                 {@link slimeknights.tconstruct.library.materials.Material#VALUE_Ingot} etc.)
     * @param temp     required smeltery temperature
     */
    public static void registerRecipe(OrePrefix prefix, Material material,
                                      Fluid fluid, int amount, int temp) {
        TiCSmeltery.tryRegister(prefix, material, fluid, amount, temp);
    }

    /**
     * Returns the TiC smeltery temperature GTMT derives from a GT material's blast temperature. Use this to match the
     * temperature GTMT assigned to the base material's other forms.
     *
     * <ul>
     * <li>No blast furnace / low-temp → 300</li>
     * <li>EBF tier 1 (≥ 1000 K) → 600</li>
     * <li>EBF tier 2 (≥ 2500 K) → 900</li>
     * <li>Extreme (≥ 5000 K) → 1200</li>
     * </ul>
     *
     * @param material the GT material
     * @return TiC smeltery temperature
     */
    public static int getDefaultTemperature(Material material) {
        return TiCSmeltery.getMeltingTemperature(material);
    }
}
