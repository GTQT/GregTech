package gregtech.integration.tconstruct.api;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.ToolProperty;
import gregtech.integration.tconstruct.traits.GregtechTraits;

import net.minecraft.enchantment.Enchantment;

import org.jetbrains.annotations.NotNull;
import slimeknights.tconstruct.library.traits.AbstractTrait;
import slimeknights.tconstruct.tools.TinkerTraits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for TiC trait assignment rules and accessors for GTMT's custom traits.
 *
 * <p>
 * Two types of rules can be registered:
 * <ul>
 * <li><b>Composition rules</b> ({@link #registerCompositionTrait}) — triggered when a
 * GT material's direct composition contains a specific material
 * (e.g. contains Silver → Holy).</li>
 * <li><b>Property rules</b> ({@link #registerPropertyTrait}) — triggered by numeric
 * thresholds or flags on a material's {@link ToolProperty}
 * (e.g. blast temp ≥ 2500 K → HeatResistant).</li>
 * </ul>
 *
 * <p>
 * All registrations must occur before GTMT's {@code registerBlocks} phase
 * (your mod's {@code preInit} or {@code init}).
 */
public final class TraitRegistry {

    // -------------------------------------------------------------------------
    // Shared types
    // -------------------------------------------------------------------------

    private static final Map<Material, List<TraitEntry>> COMPOSITION_TRAITS = new LinkedHashMap<>();
    private static final List<PropertyEntry> PROPERTY_TRAITS = new ArrayList<>();

    static {
        // Silver → Holy (bonus damage vs undead)
        registerCompositionTrait(Materials.Silver, TinkerTraits.holy, "head");

        // Precious metals → Moonlit (night speed + damage bonus).
        // GT provides no "precious metal" flag or periodic-table group API;
        // composition checking is the best available approach.
        registerCompositionTrait(Materials.Gold, GregtechTraits.MOONLIT, "head");
        registerCompositionTrait(Materials.Platinum, GregtechTraits.MOONLIT, "head");
        registerCompositionTrait(Materials.Palladium, GregtechTraits.MOONLIT, "head");
        registerCompositionTrait(Materials.Iridium, GregtechTraits.MOONLIT, "head");
    }

    // -------------------------------------------------------------------------
    // Composition-trait registry
    // -------------------------------------------------------------------------

    static {
        // Blast-temperature tiers
        registerPropertyTrait(
                (mat, prop) -> mat.getBlastTemperature() >= 2500,
                GregtechTraits.HEAT_RESISTANT, "head");
        registerPropertyTrait(
                (mat, prop) -> mat.getBlastTemperature() >= 1750 &&
                        mat.getBlastTemperature() < 2500,
                GregtechTraits.CRYOGENIC, "head");

        // Durability threshold (non-unbreakable only)
        registerPropertyTrait(
                (mat, prop) -> !prop.getUnbreakable() && prop.getToolDurability() >= 2000,
                GregtechTraits.ANTI_CORROSION, null);

        // Attack-damage threshold
        registerPropertyTrait(
                (mat, prop) -> prop.getToolAttackDamage() >= 10f,
                GregtechTraits.HEAVY_BLOW, "head");

        // Harvest-level threshold (GT level ≥ 5 = TiC Cobalt tier and above)
        registerPropertyTrait(
                (mat, prop) -> prop.getToolHarvestLevel() >= 5,
                GregtechTraits.PIERCER, "head");

        // GT material flags
        registerPropertyTrait(
                (mat, prop) -> prop.isMagnetic(),
                TinkerTraits.magnetic, null);
        registerPropertyTrait(
                (mat, prop) -> prop.getUnbreakable(),
                GregtechTraits.UNBREAKABLE, null);
    }

    private TraitRegistry() {}

    /**
     * Returns an unmodifiable view of the composition-trait registry. Used internally by {@code TiCMaterials} when
     * assigning traits.
     */
    public static Map<Material, List<TraitEntry>> getCompositionTraits() {
        return Collections.unmodifiableMap(COMPOSITION_TRAITS);
    }

    /**
     * Registers a composition-based trait rule.
     *
     * <p>
     * Any GT material whose direct composition contains {@code component} will have {@code trait} added to {@code slot}
     * on its TiC material.
     *
     * <p>
     * Example — assign a custom "shiny" trait to materials containing Osmium:
     *
     * <pre>
     * {@code
     * TraitRegistry.registerCompositionTrait(Materials.Osmium, MyTraits.SHINY, "head");
     * }
     * </pre>
     *
     * @param component the trigger material (must not be null)
     * @param trait     the trait to add (must not be null)
     * @param slot      the part slot, or {@code null} for all slots
     */
    public static void registerCompositionTrait(Material component, AbstractTrait trait,
                                                @NotNull String slot) {
        if (component == null) throw new IllegalArgumentException("component must not be null");
        if (trait == null) throw new IllegalArgumentException("trait must not be null");
        COMPOSITION_TRAITS.computeIfAbsent(component, k -> new ArrayList<>())
                .add(new TraitEntry(trait, slot));
    }

    // -------------------------------------------------------------------------
    // Property-trait registry
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the property-trait registry. Used internally by {@code TiCMaterials} when
     * assigning traits.
     */
    public static List<PropertyEntry> getPropertyTraits() {
        return Collections.unmodifiableList(PROPERTY_TRAITS);
    }

    /**
     * Registers a property-based trait rule.
     *
     * <p>
     * When GTMT processes a GT material, {@code condition} is evaluated against the material and its
     * {@link ToolProperty}. If it returns {@code true}, {@code trait} is added to {@code slot}.
     *
     * <p>
     * Example — assign a "legendary" trait to materials with GT harvest level ≥ 8:
     *
     * <pre>
     * {@code
     * TraitRegistry.registerPropertyTrait(
     *     (mat, prop) -> prop.getToolHarvestLevel() >= 8,
     *     MyTraits.LEGENDARY, "head");
     * }
     * </pre>
     *
     * @param condition the predicate (must not be null)
     * @param trait     the trait to add (must not be null)
     * @param slot      the part slot, or {@code null} for all slots
     */
    public static void registerPropertyTrait(TraitCondition condition, AbstractTrait trait,
                                             @NotNull String slot) {
        if (condition == null) throw new IllegalArgumentException("condition must not be null");
        if (trait == null) throw new IllegalArgumentException("trait must not be null");
        PROPERTY_TRAITS.add(new PropertyEntry(condition, trait, slot));
    }

    /** Mining speed +30 % in the Nether (blast temp ≥ 2500 K). */
    public static AbstractTrait getHeatResistantTrait() {
        return GregtechTraits.HEAT_RESISTANT;
    }

    /** 15 % chance to negate durability loss (durability ≥ 2000, non-unbreakable). */
    public static AbstractTrait getAntiCorrosionTrait() {
        return GregtechTraits.ANTI_CORROSION;
    }

    // -------------------------------------------------------------------------
    // Trait accessors
    // -------------------------------------------------------------------------

    /** Applies Slowness on hit (blast temp 1750–2499 K). */
    public static AbstractTrait getCryogenicTrait() {
        return GregtechTraits.CRYOGENIC;
    }

    /** Knockback +50 % (attack damage ≥ 10). */
    public static AbstractTrait getHeavyBlowTrait() {
        return GregtechTraits.HEAVY_BLOW;
    }

    /** Bonus damage vs armored targets (GT harvest level ≥ 5). */
    public static AbstractTrait getPiercerTrait() {
        return GregtechTraits.PIERCER;
    }

    /** +50 % mining speed and +3 damage at night (contains a precious metal). */
    public static AbstractTrait getMoonlitTrait() {
        return GregtechTraits.MOONLIT;
    }

    /** Zero durability loss (GT {@code isUnbreakable} flag). */
    public static AbstractTrait getUnbreakableTrait() {
        return GregtechTraits.UNBREAKABLE;
    }

    /**
     * Returns a TiC trait that permanently applies the given vanilla enchantment to a tool. Instances are cached by
     * {@code "<enchantment>_<level>"} so the same combination always returns the same registered trait object.
     *
     * @param enchantment the vanilla enchantment to apply
     * @param level       the enchantment level (≥ 1)
     * @return the cached or newly created trait
     */
    public static AbstractTrait getOrCreateEnchantmentTrait(Enchantment enchantment, int level) {
        return GregtechTraits.getOrCreateEnchantmentTrait(enchantment, level);
    }

    /**
     * Predicate evaluated against a GT material and its {@link ToolProperty} to decide whether a trait should be
     * assigned.
     */
    @FunctionalInterface
    public interface TraitCondition {

        boolean test(Material material, ToolProperty toolProp);
    }

    /** A trait assignment paired with an optional tool-part slot. */
        public static class TraitEntry {

        private final AbstractTrait trait;
            private final String slot;

            public TraitEntry(AbstractTrait trait, @NotNull String slot) {
                this.trait = trait;
                this.slot = slot;
            }

            /** The TiC trait to add. */
            public AbstractTrait getTrait() {
                return trait;
            }

            /** The part slot (e.g. {@code "head"}), or {@code null} to apply to all slots. */
            @NotNull
            public String getSlot() {
                return slot;
            }
        }

    /** A condition paired with the trait to apply when it evaluates to {@code true}. */
        public static class PropertyEntry {

        private final TraitCondition condition;
            private final AbstractTrait trait;
            private final String slot;

            public PropertyEntry(TraitCondition condition, AbstractTrait trait, @NotNull String slot) {
                this.condition = condition;
                this.trait = trait;
                this.slot = slot;
            }

            /** The predicate to evaluate. */
            public TraitCondition getCondition() {
                return condition;
            }

            /** The trait to add when the condition is true. */
            public AbstractTrait getTrait() {
                return trait;
            }

            /** The part slot, or {@code null} for all slots. */
            @NotNull
            public String getSlot() {
                return slot;
            }
        }
}
