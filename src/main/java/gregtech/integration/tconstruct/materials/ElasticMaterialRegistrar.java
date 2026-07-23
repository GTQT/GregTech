package gregtech.integration.tconstruct.materials;

import gregtech.api.GregTechAPI;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.integration.tconstruct.api.ElasticMaterials;

import net.minecraft.block.Block;
import net.minecraftforge.registries.IForgeRegistry;

import slimeknights.tconstruct.library.MaterialIntegration;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.materials.BowStringMaterialStats;
import slimeknights.tconstruct.library.materials.FletchingMaterialStats;
import slimeknights.tconstruct.tools.TinkerTraits;

import static gregtech.api.GTValues.MODID;

/**
 * Registers GT polymer materials as TiC BowString and Fletching part materials.
 *
 * <p>
 * Every GT material carrying the {@code POLYMER} property is registered automatically. Stats are taken from
 * {@link ElasticMaterials}: external mods may call {@link ElasticMaterials#register} before this phase to override
 * them, or to add custom polymer materials that GTMT does not know about. Materials not explicitly registered receive
 * neutral defaults (modifier = 1.0 / accuracy = 1.0).
 */
public final class ElasticMaterialRegistrar {

    private ElasticMaterialRegistrar() {}

    /**
     * Entry point called during {@code registerBlocks}. Must be called after {@link ToolMaterialRegistrar#register} so
     * the shared {@code integrations} list and translation helper are already initialised.
     */
    public static void register(IForgeRegistry<Block> blockRegistry) {
        for (Material gtMaterial : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (!gtMaterial.hasProperty(PropertyKey.POLYMER)) continue;

            ElasticMaterials.Entry entry = ElasticMaterials.getEntries().get(gtMaterial);
            float strMod, fletchAcc, fletchMod;
            if (entry != null) {
                strMod = entry.getStringModifier();
                fletchAcc = entry.getFletchingAccuracy();
                fletchMod = entry.getFletchingModifier();
            } else {
                strMod = 1.0f;
                fletchAcc = 1.0f;
                fletchMod = 1.0f;
            }
            registerElastic(gtMaterial, strMod, fletchAcc, fletchMod, blockRegistry);
        }
    }

    private static void registerElastic(Material gtMaterial,
                                        float stringMod,
                                        float fletchAcc, float fletchMod,
                                        IForgeRegistry<Block> blockRegistry) {
        String identifier = MODID + "." + gtMaterial.getName();
        slimeknights.tconstruct.library.materials.Material ticMaterial = new slimeknights.tconstruct.library.materials.Material(
                identifier, gtMaterial.getMaterialRGB(), true);

        ToolMaterialRegistrar.injectTranslation(identifier, gtMaterial);

        TinkerRegistry.addMaterialStats(ticMaterial, new BowStringMaterialStats(stringMod));
        TinkerRegistry.addMaterialStats(ticMaterial,
                new FletchingMaterialStats(fletchAcc, fletchMod));

        if (stringMod >= 1.1f) {
            ticMaterial.addTrait(TinkerTraits.momentum, "bowstring");
        } else if (stringMod >= 1.05f) {
            ticMaterial.addTrait(TinkerTraits.stiff, "bowstring");
        }
        if (fletchMod >= 1.05f) {
            ticMaterial.addTrait(TinkerTraits.heavy, "fletching");
        }

        // Register whichever GT item forms exist for this material
        String suffix = gtMaterial.toCamelCaseString();
        for (OrePrefix prefix : new OrePrefix[] { OrePrefix.plate, OrePrefix.foil, OrePrefix.ring }) {
            if (!OreDictUnifier.get(prefix, gtMaterial).isEmpty()) {
                ticMaterial.addItem(prefix.name + suffix, 1,
                        slimeknights.tconstruct.library.materials.Material.VALUE_Ingot);
            }
        }

        var fluid = ToolMaterialRegistrar.getFluid(gtMaterial);
        MaterialIntegration integration;
        if (fluid != null) {
            integration = new MaterialIntegration(ticMaterial, fluid, suffix);
            ToolMaterialRegistrar.trackIntegratedFluid(fluid);
        } else {
            integration = new MaterialIntegration(ticMaterial);
            for (OrePrefix prefix : new OrePrefix[] { OrePrefix.plate, OrePrefix.foil }) {
                if (!OreDictUnifier.get(prefix, gtMaterial).isEmpty()) {
                    String rep = prefix.name + suffix;
                    integration = new MaterialIntegration(rep, ticMaterial, null, null);
                    integration.setRepresentativeItem(rep);
                    break;
                }
            }
        }

        TinkerRegistry.integrate(integration);
        integration.preInit();
        integration.registerFluidBlock(blockRegistry);
        ToolMaterialRegistrar.getIntegrations().add(integration);
    }
}
