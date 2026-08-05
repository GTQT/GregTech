package gregtech.api.util;

import gregtech.api.damagesources.DamageSources;
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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import org.jetbrains.annotations.NotNull;

public class EntityDamageUtil {

    private static final int FROST_WALKER_ID = 9;

    /**
     * Get the resistance multiplier from the player's chest armor for a given hazard type.
     * Returns 1.0f (no reduction) if no valid armor is worn.
     */
    public static float getArmorResistance(@NotNull EntityLivingBase entity, ResistanceType type) {
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!chest.isEmpty() && chest.getItem() instanceof ArmorMetaItem) {
            ArmorMetaItem<?>.ArmorMetaValueItem meta = ((ArmorMetaItem<?>) chest.getItem()).getItem(chest);
            if (meta != null) {
                return switch (type) {
                    case HEAT -> meta.getArmorLogic().getHeatResistance();
                    case FROST -> meta.getArmorLogic().getHeatResistance();
                    case RADIATION -> meta.getArmorLogic().getRadiationResistance();
                    case POISON -> meta.getArmorLogic().getPoisonResistance();
                    case ELECTRIC -> meta.getArmorLogic().getElectricResistance();
                };
            }
        }
        return 1.0f;
    }

    /** Apply environmental damage with armor resistance and durability wear. */
    public static void applyHazardDamage(@NotNull EntityLivingBase entity, DamageSource source,
                                         float damage, ResistanceType type) {
        if (damage <= 0) return;
        if (!entity.isEntityAlive()) return;
        damage *= getArmorResistance(entity, type);
        if (damage <= 0) return;
        entity.attackEntityFrom(source.setDamageBypassesArmor(), damage);
        damageArmorForHazard(entity, source, damage);
    }

    public enum ResistanceType { HEAT, FROST, RADIATION, POISON, ELECTRIC }

    // ---- Temperature pipe damage ----

    public static void applyTemperatureDamage(@NotNull EntityLivingBase entity, int temperature, float multiplier,
                                              int maximum) {
        if (temperature > 320) {
            int damage = (int) ((multiplier * (temperature - 300)) / 50.0F);
            if (maximum > 0) damage = Math.min(maximum, damage);
            applyHazardDamage(entity, DamageSources.getHeatDamage(), damage, ResistanceType.HEAT);
        } else if (temperature < 260) {
            int damage = (int) ((multiplier * (273 - temperature)) / 25.0F);
            if (maximum > 0) damage = Math.min(maximum, damage);
            applyHazardDamage(entity, DamageSources.getFrostDamage(), damage, ResistanceType.FROST);
        }
    }

    public static void applyHeatDamage(@NotNull EntityLivingBase entity, int damage) {
        if (entity instanceof EntityBlaze || entity instanceof EntityMagmaCube ||
                entity instanceof EntityWitherSkeleton || entity instanceof EntityWither) return;
        if (entity.getActivePotionEffect(MobEffects.FIRE_RESISTANCE) != null) return;
        applyHazardDamage(entity, DamageSources.getHeatDamage(), damage, ResistanceType.HEAT);
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
        applyHazardDamage(entity, DamageSources.getFrostDamage(), damage, ResistanceType.FROST);
        if (entity instanceof EntityPlayerMP) AdvancementTriggers.COLD_DEATH.trigger((EntityPlayerMP) entity);
    }

    public static void applyChemicalDamage(@NotNull EntityLivingBase entity, int damage) {
        if (entity instanceof AbstractSkeleton) return;
        applyHazardDamage(entity, DamageSources.getChemicalDamage(), damage, ResistanceType.POISON);
        if (damage > 0) entity.addPotionEffect(new PotionEffect(MobEffects.POISON, damage * 100, 1));
        if (entity instanceof EntityPlayerMP) AdvancementTriggers.CHEMICAL_DEATH.trigger((EntityPlayerMP) entity);
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
