package gregtech.api.util;

import gregtech.api.damagesources.DamageSources;
import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.api.items.armor.IArmorLogic;
import gregtech.api.unification.material.Material;
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

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A hazard an entity can be exposed to, and the single place that knows how to
 * resolve, resist and apply it.
 * <p>
 * Every hazard answers four questions, so that adding one means adding one enum
 * constant rather than editing four call sites:
 * <ul>
 * <li>{@link #intensityOf} — how much of it does this (material, prefix) pair emit?</li>
 * <li>{@link #warnsOn} — should the item advertise it in its tooltip?</li>
 * <li>{@link #resistanceOf} — which armor stat scales it down?</li>
 * <li>{@link #onHit} — what non-damage effect comes with it?</li>
 * </ul>
 * This replaces the four hand-written copies of "look up armor resistance, scale
 * the damage, bypass armor, wear the chestplate" that used to live in
 * {@code MetaPrefixItem#onUpdate}, {@code MetaPrefixItem#onEntityItemUpdate},
 * {@code DimensionHazardHandler#onPlayerTick} and {@code BlockCable}.
 * <p>
 * Hazards fall into two camps. {@link #HEAT} and {@link #POISON} are prefix-sensitive
 * — a hot ingot burns you but hot dust does not exist. {@link #RADIATION} and
 * {@link #FROST} are material-wide: every derivative of a radioactive or cryogenic
 * material hurts uniformly. {@link #ELECTRIC} is not carried by materials at all.
 */
public enum Hazard {

    HEAT(DamageSources::getHeatDamage, "gregtech.material.tooltip.hot") {

        @Override
        public float intensityOf(@NotNull Material material, @NotNull OrePrefix prefix) {
            if (prefix.heatDamageFunction == null) return 0f;
            // Signed: a negative result means this prefix radiates cold, not heat.
            // Which materials the function applies to is the prefix's business, not ours —
            // a blast-temperature formula and a flat "hot depleted rod" value differ in that.
            return prefix.heatDamageFunction.apply(material);
        }

        @Override
        public boolean warnsOn(@NotNull Material material) {
            return material.hasProperty(PropertyKey.BLAST);
        }
    },

    FROST(DamageSources::getFrostDamage, "gregtech.material.tooltip.cold") {

        @Override
        public float intensityOf(@NotNull Material material, @NotNull OrePrefix prefix) {
            if (!material.hasProperty(PropertyKey.COLD)) return 0f;
            return material.getProperty(PropertyKey.COLD).getColdDamage();
        }

        @Override
        public boolean warnsOn(@NotNull Material material) {
            return material.hasProperty(PropertyKey.COLD);
        }
    },

    RADIATION(DamageSources::getRadioactiveDamage, "gregtech.material.tooltip.radioactive") {

        @Override
        public float intensityOf(@NotNull Material material, @NotNull OrePrefix prefix) {
            if (!material.hasProperty(PropertyKey.RADIOACTIVE)) return 0f;
            return material.getProperty(PropertyKey.RADIOACTIVE).getRadioactivity();
        }

        @Override
        public boolean warnsOn(@NotNull Material material) {
            return material.hasProperty(PropertyKey.FISSION_FUEL) || material.hasProperty(PropertyKey.RADIOACTIVE);
        }

        @Override
        protected void onHit(@NotNull EntityLivingBase entity, float intensity, float resisted) {
            // RadiationEffectUtil grades its debuff against the unshielded strength.
            if (entity instanceof EntityPlayer player) {
                RadiationEffectUtil.applyDebuff(player, intensity);
            }
        }
    },

    POISON(DamageSources::getChemicalDamage, "gregtech.material.tooltip.toxic") {

        @Override
        public float intensityOf(@NotNull Material material, @NotNull OrePrefix prefix) {
            if (!material.hasProperty(PropertyKey.TOXIC)) return 0f;
            float toxicity = material.getProperty(PropertyKey.TOXIC).getToxicity();
            if (prefix.poisonDamageFunction != null) {
                toxicity *= prefix.poisonDamageFunction.apply(material);
            }
            return toxicity;
        }

        @Override
        public boolean warnsOn(@NotNull Material material) {
            return material.hasProperty(PropertyKey.TOXIC);
        }

        @Override
        protected void onHit(@NotNull EntityLivingBase entity, float intensity, float resisted) {
            entity.addPotionEffect(new PotionEffect(MobEffects.POISON, (int) (resisted * 100), 1));
        }
    },

    ELECTRIC(DamageSources::getElectricDamage, null) {
        // Not carried by materials — only applied directly, e.g. by bare cables.
    };

    /** All hazards, in canonical application order. */
    public static final Hazard[] VALUES = values();

    /**
     * The hazards armor keeps its own resistance value for, in tooltip order.
     * {@link #FROST} is absent because it shares {@link #HEAT}'s value.
     */
    public static final List<Hazard> ARMOR_RESISTED = List.of(HEAT, RADIATION, POISON, ELECTRIC);

    private final Supplier<DamageSource> damageSource;
    private final @Nullable String materialTooltipKey;

    Hazard(@NotNull Supplier<DamageSource> damageSource, @Nullable String materialTooltipKey) {
        this.damageSource = damageSource;
        this.materialTooltipKey = materialTooltipKey;
    }

    /**
     * The raw damage per second this hazard deals from a given (material, prefix)
     * pair, before armor. Zero means the pair does not emit this hazard.
     * <p>
     * {@link #HEAT} is the one hazard whose result is signed: a negative value
     * means the prefix emits {@link #FROST} instead.
     */
    public float intensityOf(@NotNull Material material, @NotNull OrePrefix prefix) {
        return 0f;
    }

    /** Whether this hazard is worth advertising on the material's tooltip. */
    public boolean warnsOn(@NotNull Material material) {
        return false;
    }

    /** The lang key for this hazard's material tooltip line, or null if it has none. */
    public @Nullable String materialTooltipKey() {
        return materialTooltipKey;
    }

    /** Heat and cold — the hazards a pair of pincers lets you ignore. */
    public boolean isThermal() {
        return this == HEAT || this == FROST;
    }

    /** The multiplier this armor logic applies to this hazard. 1.0 means no protection. */
    public float resistanceOf(@NotNull IArmorLogic armor) {
        return switch (this) {
            case HEAT, FROST -> armor.getHeatResistance();
            case RADIATION -> armor.getRadiationResistance();
            case POISON -> armor.getPoisonResistance();
            case ELECTRIC -> armor.getElectricResistance();
        };
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

        DamageSource source = damageSource.get();
        entity.attackEntityFrom(source.setDamageBypassesArmor(), resisted);
        EntityDamageUtil.damageArmorForHazard(entity, source, resisted);
        onHit(entity, intensity, resisted);
    }

    /**
     * The effect this hazard applies on top of its damage.
     *
     * @param intensity damage per second <em>before</em> armor scaling —
     *                  {@link RadiationEffectUtil} grades its debuff on this
     * @param resisted  damage per second actually dealt — poison scales its
     *                  effect duration on this
     */
    protected void onHit(@NotNull EntityLivingBase entity, float intensity, float resisted) {}

    /**
     * Whether this (material, prefix) pair emits heat or cold at all. Callers that
     * shield against thermal hazards should check this <em>before</em> paying for the
     * shield — a pair of pincers should not wear out on a stack that was never hot.
     */
    public static boolean carriesThermal(@NotNull Material material, @NotNull OrePrefix prefix) {
        return HEAT.intensityOf(material, prefix) != 0f || FROST.intensityOf(material, prefix) != 0f;
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
        for (Hazard hazard : VALUES) {
            if (!filter.test(hazard)) continue;

            float raw = hazard.intensityOf(material, prefix);
            if (raw == 0f) continue;

            // heatDamageFunction is signed: a negative result means the prefix radiates cold.
            if (hazard == HEAT && raw < 0f) {
                // The substituted hazard has to clear the filter on its own account.
                if (filter.test(FROST)) FROST.applyTo(entity, -raw);
            } else {
                hazard.applyTo(entity, raw);
            }
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
}
