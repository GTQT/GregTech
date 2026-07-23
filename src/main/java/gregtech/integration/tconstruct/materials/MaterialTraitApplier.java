package gregtech.integration.tconstruct.materials;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.ToolProperty;
import gregtech.api.unification.stack.MaterialStack;

import gregtech.integration.tconstruct.api.TraitRegistry;

/**
 * Applies TiC traits to a material using the {@link TraitRegistry} API.
 *
 * <p>
 * Handles three sources of traits:
 * <ol>
 * <li>Composition-based traits (e.g. contains Silver → Holy)</li>
 * <li>Property-based traits (e.g. blast temperature ≥ 2500 → HeatResistant)</li>
 * <li>Enchantment traits (dynamically derived from the GT tool property)</li>
 * </ol>
 */
final class MaterialTraitApplier {

    private MaterialTraitApplier() {}

    /**
     * Assigns TiC traits from the {@link TraitRegistry} registries, then handles per-enchantment traits from the GT
     * tool property.
     */
    static void applyTraits(slimeknights.tconstruct.library.materials.Material ticMaterial,
                            Material gtMaterial, ToolProperty toolProp) {
        // Composition-based traits (e.g. contains Silver → Holy, contains Gold → Moonlit)
        TraitRegistry.getCompositionTraits().forEach((component, entries) -> {
            if (containsMaterial(gtMaterial, component)) {
                for (TraitRegistry.TraitEntry entry : entries) {
                    if (entry.getSlot() != null) ticMaterial.addTrait(entry.getTrait(), entry.getSlot());
                    else ticMaterial.addTrait(entry.getTrait());
                }
            }
        });

        // Property-based traits (e.g. blast temp ≥ 2500 → HeatResistant)
        for (TraitRegistry.PropertyEntry entry : TraitRegistry.getPropertyTraits()) {
            if (entry.getCondition().test(gtMaterial, toolProp)) {
                if (entry.getSlot() != null) ticMaterial.addTrait(entry.getTrait(), entry.getSlot());
                else ticMaterial.addTrait(entry.getTrait());
            }
        }

        // Enchantment traits — dynamic per-enchantment, not suitable for the static registry
        toolProp.getEnchantments().forEach((enchantment, enchLevel) -> {
            int level = enchLevel.getLevel(toolProp.getToolHarvestLevel());
            if (level > 0) {
                ticMaterial.addTrait(
                        TraitRegistry.getOrCreateEnchantmentTrait(enchantment, level), "head");
            }
        });
    }

    /** Check whether a material's direct composition contains the given target material. */
    private static boolean containsMaterial(Material material, Material target) {
        for (MaterialStack stack : material.getMaterialComponents()) {
            if (stack.material == target) return true;
        }
        return false;
    }
}
