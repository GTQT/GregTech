package gregtech.api.util;

import gregtech.api.damagesources.DamageSources;
import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.api.items.armor.IArmorLogic;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.HazardProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * One kind of damage an entity can be exposed to, and the single pipeline that resolves,
 * resists and applies it.
 * <p>
 * <b>Every hazard has exactly the same two entries</b>, both measured in unshielded damage per
 * second before armor:
 * <ul>
 * <li><b>shape entry</b> — a function on the {@link OrePrefix}. It applies to every material the
 * shape is made of: {@code ingotHot} burns no matter which metal it is, and a hot depleted fuel
 * rod glows no matter which fuel it came from. Registered with
 * {@link OrePrefix#setHazardDamage(Hazard, java.util.function.Function)}.</li>
 * <li><b>substance entry</b> — a {@link HazardProperty} on the {@link Material}. It applies in
 * every prefix: every shape of uranium irradiates, every shape of chlorine is toxic. Registered
 * by setting the material's property.</li>
 * </ul>
 * The two entries are added; neither overrides the other. A hot uranium ingot therefore burns
 * and irradiates in the same tick, and a (material, prefix) pair whose sum is zero carries
 * nothing. Negative readings are not a thing: a cold shape registers under {@link #FROST}
 * instead of returning a negative {@link #HEAT}.
 * <p>
 * Everything else is plain data on the enum constant — damage source, tooltip keys, which armor
 * stat resists it and which extra effect comes with it. There are no per-constant overrides, so
 * all hazards travel the same path: sum the two entries, scale by armor, deal damage, wear the
 * chestplate, apply the effect. {@link #ELECTRIC} is the one hazard with no item entries; it is
 * applied directly by sources such as bare cables, through the same {@link #applyTo} pipeline.
 */
public enum Hazard {

    // damage source, item tooltip key, substance property, armor tooltip key, thermal, armor stat, extra effect
    HEAT(DamageSources.getHeatDamage(), "gregtech.material.tooltip.hot", null,
            "gregtech.armor.resistance.heat", true, IArmorLogic::getHeatResistance, HazardEffect.NONE),

    FROST(DamageSources.getFrostDamage(), "gregtech.material.tooltip.cold", PropertyKey.COLD,
            "gregtech.armor.resistance.heat", true, IArmorLogic::getHeatResistance, HazardEffect.NONE),

    RADIATION(DamageSources.getRadioactiveDamage(), "gregtech.material.tooltip.radioactive",
            PropertyKey.RADIOACTIVE, "gregtech.armor.resistance.radiation", false,
            IArmorLogic::getRadiationResistance, Hazard::radiationDebuff),

    POISON(DamageSources.getChemicalDamage(), "gregtech.material.tooltip.toxic", PropertyKey.TOXIC,
            "gregtech.armor.resistance.chemical", false, IArmorLogic::getPoisonResistance,
            Hazard::poisonEffect),

    ELECTRIC(DamageSources.getElectricDamage(), null, null, "gregtech.armor.resistance.electric", false,
            IArmorLogic::getElectricResistance, HazardEffect.NONE);

    /** All hazards, in canonical application order. */
    public static final Hazard[] VALUES = values();

    /**
     * The hazards armor keeps its own resistance value for, in tooltip order.
     * {@link #FROST} is absent because it shares {@link #HEAT}'s value.
     */
    public static final List<Hazard> ARMOR_RESISTED = Collections
            .unmodifiableList(Arrays.asList(HEAT, RADIATION, POISON, ELECTRIC));

    @FunctionalInterface
    public interface ArmorResistance {

        float apply(@NotNull IArmorLogic armor);
    }

    /** An effect applied on top of the damage, such as a poison or radiation debuff. */
    @FunctionalInterface
    public interface HazardEffect {

        void apply(@NotNull EntityLivingBase entity, float intensity, float resisted);

        HazardEffect NONE = (entity, intensity, resisted) -> {};
    }

    private final DamageSource damageSource;
    private final @Nullable String materialTooltipKey;
    private final @Nullable PropertyKey<? extends HazardProperty> materialProperty;
    private final String armorResistanceKey;
    private final boolean thermal;
    private final ArmorResistance armorResistance;
    private final HazardEffect effect;

    Hazard(@NotNull DamageSource damageSource,
           @Nullable String materialTooltipKey,
           @Nullable PropertyKey<? extends HazardProperty> materialProperty,
           @NotNull String armorResistanceKey,
           boolean thermal,
           @NotNull ArmorResistance armorResistance,
           @NotNull HazardEffect effect) {
        this.damageSource = damageSource;
        this.materialTooltipKey = materialTooltipKey;
        this.materialProperty = materialProperty;
        this.armorResistanceKey = armorResistanceKey;
        this.thermal = thermal;
        this.armorResistance = armorResistance;
        this.effect = effect;
    }

    /**
     * The raw damage per second this (material, prefix) pair emits, before armor: the substance
     * entry plus the shape entry. Zero means the pair carries none of this hazard.
     */
    public float intensityOf(@NotNull Material material, @NotNull OrePrefix prefix) {
        return materialIntensity(material) + prefixIntensity(material, prefix);
    }

    /**
     * The substance entry: damage per second this material emits in every one of its shapes.
     * Zero for hazards that are not carried by substances ({@link #HEAT}, {@link #ELECTRIC}).
     */
    public float materialIntensity(@NotNull Material material) {
        if (materialProperty == null || !material.hasProperty(materialProperty)) return 0f;
        return material.getProperty(materialProperty).getHazardDamage();
    }

    /**
     * The shape entry: damage per second this prefix adds for any material. Zero for shapes
     * that do not carry this hazard.
     */
    public float prefixIntensity(@NotNull Material material, @NotNull OrePrefix prefix) {
        return prefix.getHazardDamage(this, material);
    }

    /**
     * Whether this (material, prefix) pair is worth advertising on the item's tooltip.
     * Anything that actually hurts is worth warning about, whichever entry supplied it.
     */
    public boolean warnsOn(@NotNull Material material, @NotNull OrePrefix prefix) {
        return intensityOf(material, prefix) > 0f;
    }

    /** The lang key for this hazard's material tooltip line, or null if it has none. */
    public @Nullable String materialTooltipKey() {
        return materialTooltipKey;
    }

    /** The lang key for this hazard's armor resistance tooltip line. */
    public String armorResistanceKey() {
        return armorResistanceKey;
    }

    /** Heat and cold — the hazards a pair of pincers lets you ignore. */
    public boolean isThermal() {
        return thermal;
    }

    /** The multiplier this armor logic applies to this hazard. 1.0 means no protection. */
    public float resistanceOf(@NotNull IArmorLogic armor) {
        return armorResistance.apply(armor);
    }

    /**
     * The resistance multiplier of the entity's chest armor against this hazard.
     * Returns 1.0 (no reduction) when no GT armor is worn.
     */
    public float armorResistanceOf(@NotNull EntityLivingBase entity) {
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!chest.isEmpty() && chest.getItem() instanceof ArmorMetaItem) {
            ArmorMetaItem<?>.ArmorMetaValueItem meta = ((ArmorMetaItem<?>) chest.getItem()).getItem(chest);
            if (meta != null) return resistanceOf(meta.getArmorLogic());
        }
        return 1.0f;
    }

    /**
     * Applies this hazard at the given strength: armor scaling, damage, chestplate
     * wear, then the hazard's own extra effect.
     *
     * @param intensity raw damage per second, before armor
     */
    public final void applyTo(@NotNull EntityLivingBase entity, float intensity) {
        if (intensity <= 0f || !entity.isEntityAlive()) return;
        float resisted = intensity * armorResistanceOf(entity);
        if (resisted <= 0f) return;

        DamageSource source = damageSource.setDamageBypassesArmor();
        entity.attackEntityFrom(source, resisted);
        EntityDamageUtil.damageArmorForHazard(entity, source, resisted);
        effect.apply(entity, intensity, resisted);
    }

    /**
     * Whether this (material, prefix) pair emits heat or cold at all. Callers that
     * shield against thermal hazards should check this <em>before</em> paying for the
     * shield — a pair of pincers should not wear out on a stack that was never hot.
     */
    public static boolean carriesThermal(@NotNull Material material, @NotNull OrePrefix prefix) {
        return HEAT.intensityOf(material, prefix) > 0f || FROST.intensityOf(material, prefix) > 0f;
    }

    /** Applies every hazard this (material, prefix) pair carries. */
    public static void applyAll(@NotNull EntityLivingBase entity, @NotNull Material material,
                                @NotNull OrePrefix prefix) {
        applyAll(entity, material, prefix, hazard -> true);
    }

    /**
     * Applies every hazard this (material, prefix) pair carries, except those the
     * filter rejects. Use the filter for protections that live outside armor —
     * pincers against heat, a hazmat suit against radiation, and so on.
     */
    public static void applyAll(@NotNull EntityLivingBase entity, @NotNull Material material,
                                @NotNull OrePrefix prefix, @NotNull Predicate<Hazard> filter) {
        boolean applied = false;
        for (Hazard hazard : VALUES) {
            if (!filter.test(hazard)) continue;

            float raw = hazard.intensityOf(material, prefix);
            if (raw <= 0f) continue;

            // Hazards are independent sources. Vanilla's hurt-resistance window would otherwise
            // swallow every hazard after the first one applied in the same tick — a hot uranium
            // ingot would only burn, never irradiate. Clear it between hazards so both land.
            if (applied) entity.hurtResistantTime = 0;

            // Each hazard clears the filter on its own account: shielding against heat does
            // not by itself shield against the cold of the same stack.
            hazard.applyTo(entity, raw);
            applied = true;
        }
    }

    /** Applies every hazard this (material, prefix) pair carries to players near a point. */
    public static void applyAllNearby(@NotNull World world, @NotNull AxisAlignedBB box,
                                      @NotNull Material material, @NotNull OrePrefix prefix) {
        applyAllNearby(world, box, material, prefix, hazard -> true);
    }

    /** As {@link #applyAllNearby}, with a filter — see {@link #applyAll}. */
    public static void applyAllNearby(@NotNull World world, @NotNull AxisAlignedBB box,
                                      @NotNull Material material, @NotNull OrePrefix prefix,
                                      @NotNull Predicate<Hazard> filter) {
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, box)) {
            applyAll(player, material, prefix, filter);
        }
    }

    private static void radiationDebuff(@NotNull EntityLivingBase entity, float intensity, float resisted) {
        // Graded against the unshielded strength, not the damage actually dealt.
        if (entity instanceof EntityPlayer player) {
            RadiationEffectUtil.applyDebuff(player, intensity);
        }
    }

    private static void poisonEffect(@NotNull EntityLivingBase entity, float intensity, float resisted) {
        entity.addPotionEffect(new PotionEffect(MobEffects.POISON, (int) (resisted * 100), 1));
    }
}
