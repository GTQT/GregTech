package gregtech.integration.tconstruct;

import gregtech.api.GregTechAPI;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialFlags;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import slimeknights.mantle.util.RecipeMatch;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.smeltery.CastingRecipe;

import static gregtech.common.items.MetaItems.*;

/**
 * Registers TiC smeltery casting recipes for GT materials: melting the processed form and casting it on the
 * casting table using the matching GT shape mold. Only recipes whose mold item exists are registered.
 */
public final class CastingHandler {

    private CastingHandler() {}

    /**
     * Called during material registration to add casting recipes for all GT materials that carry a fluid property.
     */
    public static void init() {
        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (!material.hasFluid()) continue;

            if (material.hasFlag(MaterialFlags.GENERATE_SMALL_GEAR))
                register(material, OrePrefix.gearSmall, SHAPE_MOLD_GEAR_SMALL, 144);
            if (material.hasFlag(MaterialFlags.GENERATE_GEAR))
                register(material, OrePrefix.gear, SHAPE_MOLD_GEAR, 576);
            if (material.hasFlag(MaterialFlags.GENERATE_PLATE))
                register(material, OrePrefix.plate, SHAPE_MOLD_PLATE, 144);
            if (material.hasFlag(MaterialFlags.GENERATE_BOLT_SCREW)) {
                register(material, OrePrefix.screw, SHAPE_MOLD_SCREW, 144 / 9);
                register(material, OrePrefix.bolt, SHAPE_MOLD_BOLT, 144 / 8);
            }
            if (material.hasFlag(MaterialFlags.GENERATE_ROD))
                register(material, OrePrefix.stick, SHAPE_MOLD_ROD, 72);
            if (material.hasFlag(MaterialFlags.GENERATE_RING))
                register(material, OrePrefix.ring, SHAPE_MOLD_RING, 36);
            if (material.hasFlag(MaterialFlags.GENERATE_LONG_ROD))
                register(material, OrePrefix.stickLong, SHAPE_MOLD_ROD_LONG, 144);
            if (material.hasFlag(MaterialFlags.GENERATE_ROTOR))
                register(material, OrePrefix.rotor, SHAPE_MOLD_ROTOR, 576);
            if (material.hasFlag(MaterialFlags.GENERATE_ROUND))
                register(material, OrePrefix.round, SHAPE_MOLD_ROUND, 144 / 9);
        }
    }

    /**
     * Registers melting + table-casting recipes for one processed form of the material. Skips the recipe when the
     * item does not exist or the mold is not registered.
     */
    private static void register(Material material, OrePrefix prefix,
                                 MetaItem<?>.MetaValueItem mold, int amount) {
        ItemStack output = OreDictUnifier.get(prefix, material, 1);
        if (output.isEmpty() || mold == null) return;

        FluidStack fluid = material.getFluid(amount);
        if (fluid == null) return;

        RecipeMatch cast = RecipeMatch.ofNBT(mold.getStackForm());
        TinkerRegistry.registerMelting(output, fluid.getFluid(), amount);
        TinkerRegistry.registerTableCasting(new CastingRecipe(output, cast, fluid,
                calcCooldownTime(fluid.getFluid(), amount), false, false));
    }

    private static int calcCooldownTime(Fluid fluid, int amount) {
        int time = 120;
        int temperature = fluid.getTemperature() - 300;
        return time + temperature * amount / 1200;
    }
}
