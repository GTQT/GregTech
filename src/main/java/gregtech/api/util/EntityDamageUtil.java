package gregtech.api.util;

import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.core.advancement.AdvancementTriggers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

/**
 * Convenience wrappers around {@link Hazard} for callers that need the entity-type
 * filters, enchantment checks and advancements that come with a specific hazard.
 * <p>
 * Anything that just wants to hurt an entity should call {@link Hazard#applyTo}
 * directly — these wrappers exist for the sources that have extra conditions.
 */
public class EntityDamageUtil {

    private static final int FROST_WALKER_ID = 9;

    // ---- Temperature pipe damage ----

    public static void applyTemperatureDamage(@NotNull EntityLivingBase entity, int temperature, float multiplier,
                                              int maximum) {
        if (temperature > 320) {
            int damage = (int) ((multiplier * (temperature - 300)) / 50.0F);
            if (maximum > 0) damage = Math.min(maximum, damage);
            Hazard.HEAT.applyTo(entity, damage);
        } else if (temperature < 260) {
            int damage = (int) ((multiplier * (273 - temperature)) / 25.0F);
            if (maximum > 0) damage = Math.min(maximum, damage);
            Hazard.FROST.applyTo(entity, damage);
        }
    }

    /** As {@link #applyTemperatureDamage}, for every player inside a box. */
    public static void applyTemperatureDamageNearby(@NotNull World world, @NotNull AxisAlignedBB box,
                                                    int temperature, float multiplier, int maximum) {
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, box)) {
            applyTemperatureDamage(player, temperature, multiplier, maximum);
        }
    }

    public static void applyHeatDamage(@NotNull EntityLivingBase entity, int damage) {
        if (entity instanceof EntityBlaze || entity instanceof EntityMagmaCube ||
                entity instanceof EntityWitherSkeleton || entity instanceof EntityWither) return;
        if (entity.getActivePotionEffect(MobEffects.FIRE_RESISTANCE) != null) return;
        Hazard.HEAT.applyTo(entity, damage);
        if (entity instanceof EntityPlayerMP) AdvancementTriggers.HEAT_DEATH.trigger((EntityPlayerMP) entity);
    }

    public static void applyFrostDamage(@NotNull EntityLivingBase entity, int damage) {
        if (entity instanceof EntitySnowman || entity instanceof EntityPolarBear || entity instanceof EntityStray) return;
        ItemStack feet = entity.getItemStackFromSlot(EntityEquipmentSlot.FEET);
        if (!feet.isEmpty()) {
            for (NBTBase base : feet.getEnchantmentTagList()) {
                if (((NBTTagCompound) base).getShort("id") == FROST_WALKER_ID) {
                    feet.damageItem(1, entity);
                    return;
                }
            }
        }
        Hazard.FROST.applyTo(entity, damage);
        if (entity instanceof EntityPlayerMP) AdvancementTriggers.COLD_DEATH.trigger((EntityPlayerMP) entity);
    }

    public static void applyChemicalDamage(@NotNull EntityLivingBase entity, int damage) {
        if (entity instanceof AbstractSkeleton) return;
        Hazard.POISON.applyTo(entity, damage);
        if (entity instanceof EntityPlayerMP) AdvancementTriggers.CHEMICAL_DEATH.trigger((EntityPlayerMP) entity);
    }

    /** As {@link #applyChemicalDamage}, for every player inside a box. */
    public static void applyChemicalDamageNearby(@NotNull World world, @NotNull AxisAlignedBB box, int damage) {
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, box)) {
            applyChemicalDamage(player, damage);
        }
    }

    /** Damage chest armor durability after hazard damage. */
    public static void damageArmorForHazard(@NotNull EntityLivingBase entity, DamageSource source, float damage) {
        int durabilityDamage = Math.max(1, (int) damage);
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!chest.isEmpty() && chest.getItem() instanceof ArmorMetaItem) {
            ArmorMetaItem<?>.ArmorMetaValueItem meta = ((ArmorMetaItem<?>) chest.getItem()).getItem(chest);
            if (meta != null) {
                meta.getArmorLogic().damageArmor(entity, chest, source, durabilityDamage, EntityEquipmentSlot.CHEST);
            }
        }
    }
}
