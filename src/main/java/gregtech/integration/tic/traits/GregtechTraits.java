package gregtech.integration.tic.traits;

import net.minecraft.enchantment.Enchantment;

import slimeknights.tconstruct.library.traits.AbstractTrait;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all gregtech custom TiC traits.
 *
 * <p>
 * Static traits are auto-registered with {@code TinkerRegistry} on class initialisation. Their display names and
 * descriptions live in the mod's lang files ({@code assets/gregtech/lang/}) under {@code modifier.<id>.name} /
 * {@code modifier.<id>.desc}.
 *
 * <p>
 * Note: undead bonus-damage is handled by TiC's built-in {@code TinkerTraits#holy}; this class only defines traits that
 * have no TiC equivalent.
 *
 * <p>
 * Dynamic enchantment traits are created on demand via {@link #getOrCreateEnchantmentTrait(Enchantment, int)} and their
 * translations are injected at runtime because the set of enchantments is not known at compile time.
 */
public final class GregtechTraits {

    /** Mining speed +30 % in the Nether — blast temperature ≥ 2500 K. */
    public static final AbstractTrait HEAT_RESISTANT = new TraitHeatResistant();

    /** 15 % chance to negate durability loss — non-unbreakable materials with durability ≥ 2000. */
    public static final AbstractTrait ANTI_CORROSION = new TraitAntiCorrosion();

    /** Applies Slowness on hit — blast temperature in [1750, 2500) K (vacuum-freezer processed). */
    public static final AbstractTrait CRYOGENIC = new TraitCryogenic();

    /** Knockback +50 % — materials with attack damage ≥ 10. */
    public static final AbstractTrait HEAVY_BLOW = new TraitHeavyBlow();

    /** Bonus damage vs armored targets — GT harvest level ≥ 5 (Cobalt tier and above). */
    public static final AbstractTrait PIERCER = new TraitPiercer();

    /** +50 % mining speed and +3 damage at night — materials containing Gold, Platinum, Palladium, or Iridium. */
    public static final AbstractTrait MOONLIT = new TraitMoonlit();

    /** Zero durability loss — GT {@code isUnbreakable} flag. */
    public static final AbstractTrait UNBREAKABLE = new TraitUnbreakable();

    private static final Map<String, AbstractTrait> enchantmentTraitCache = new HashMap<>();

    private GregtechTraits() {}

    /**
     * Returns a TiC trait that applies the given vanilla enchantment to tools. Instances are cached by
     * {@code "<enchantment>_<level>"} to avoid duplicate registrations with {@code TinkerRegistry}. Display name and
     * description are provided directly by {@link TraitEnchantment} via its overridden {@code getLocalizedName()} /
     * {@code getLocalizedDesc()} methods.
     */
    public static AbstractTrait getOrCreateEnchantmentTrait(Enchantment enchantment, int level) {
        String id = "gregtech_ench_" + enchantment.getRegistryName().getPath() +
                (level > 1 ? "_" + level : "");
        return enchantmentTraitCache.computeIfAbsent(id, k -> {
            int color = enchantment.type != null ? enchantment.type.ordinal() * 0x112233 + 0x4488BB : 0xFFD700;
            return new TraitEnchantment(id, color, enchantment, level);
        });
    }
}
