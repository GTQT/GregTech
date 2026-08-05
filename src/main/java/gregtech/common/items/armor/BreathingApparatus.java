/*
 * Ported from Susy-Core (https://github.com/SymmetricDevs/Susy-Core)
 * Copyright (c) 2023 SuperSymmetry contributors
 * Licensed under LGPLv3
 *
 * Original source: supersymmetry.common.item.armor.BreathingApparatus
 * Modified for GregTech integration — split into mask (HEAD) and tank (CHEST, IOxygenTank).
 */
package gregtech.common.items.armor;

import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.api.items.armor.IArmorLogic;
import gregtech.api.items.armor.IOxygenTank;

import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentDurability;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * HEAD = gas mask (consumes oxygen), CHEST = oxygen tank (stores oxygen).
 * The mask works with any chest armor implementing {@link IOxygenTank}.
 * Provides full poison protection.
 */
public class BreathingApparatus implements IArmorLogic, IOxygenTank {

    protected final EntityEquipmentSlot SLOT;
    protected final int maxDurability;
    protected final double maxOxygen;

    public BreathingApparatus(EntityEquipmentSlot slot, int maxDurability, double maxOxygen) {
        this.SLOT = slot;
        this.maxDurability = maxDurability;
        this.maxOxygen = maxOxygen;
    }

    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        return SLOT;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        if (SLOT == EntityEquipmentSlot.HEAD) {
            return "gregtech:textures/armor/gas_mask.png";
        } else {
            return "gregtech:textures/armor/gas_tank.png";
        }
    }

    @Override
    public float getPoisonResistance() {
        return 0.0f;
    }

    // ---- IOxygenTank (CHEST only) ----

    @Override
    public double getOxygen(ItemStack stack) {
        if (stack.getTagCompound() == null) return 1;
        if (!stack.getTagCompound().hasKey("oxygen")) {
            stack.getTagCompound().setDouble("oxygen", maxOxygen);
        }
        return stack.getTagCompound().getDouble("oxygen");
    }

    @Override
    public double getMaxOxygen(ItemStack stack) {
        return maxOxygen;
    }

    @Override
    public void changeOxygen(ItemStack stack, double delta) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound compound = stack.getTagCompound();
        compound.setDouble("oxygen", getOxygen(stack) + delta);
        stack.setTagCompound(compound);
    }

    // ---- Durability ----

    protected int getDurability(ItemStack stack) {
        if (stack.getTagCompound() == null) return 0;
        if (!stack.getTagCompound().hasKey("durability")) {
            stack.getTagCompound().setInteger("durability", maxDurability);
        }
        return stack.getTagCompound().getInteger("durability");
    }

    protected void changeDurability(ItemStack stack, int delta) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound compound = stack.getTagCompound();
        compound.setInteger("durability", getDurability(stack) + delta);
        stack.setTagCompound(compound);
    }

    @Override
    public void damageArmor(EntityLivingBase entity, ItemStack itemStack, DamageSource source, int damage,
                            EntityEquipmentSlot equipmentSlot) {
        itemStack.attemptDamageItem(damage, entity.getRNG(), null);
        if (damage > 0) {
            int unlvl = EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, itemStack);
            int negated = 0;
            for (int k = 0; unlvl > 0 && k < damage; ++k) {
                if (EnchantmentDurability.negateDamage(itemStack, unlvl, entity.getRNG())) ++negated;
            }
            damage -= negated;
            if (damage <= 0) return;
        }
        changeDurability(itemStack, -damage);
    }

    // ---- Mask breathing logic (HEAD only) ----

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack itemStack) {
        if (SLOT != EntityEquipmentSlot.HEAD) return;
        if (player.getItemStackFromSlot(EntityEquipmentSlot.HEAD) != itemStack) return;

        if (player.isInsideOfMaterial(Material.WATER)) {
            ItemStack chest = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
            IOxygenTank tank = getOxygenTank(chest);
            if (tank != null && tank.getOxygen(chest) > 0) {
                player.setAir(300);
                tank.changeOxygen(chest, -1.0 / 20.0);
            }
        }
    }

    /** Find an IOxygenTank in the chest slot. Returns null if none. */
    public static IOxygenTank getOxygenTank(ItemStack chestStack) {
        if (chestStack.getItem() instanceof ArmorMetaItem) {
            ArmorMetaItem<?> armorItem = (ArmorMetaItem<?>) chestStack.getItem();
            ArmorMetaItem<?>.ArmorMetaValueItem meta = armorItem.getItem(chestStack);
            if (meta.getArmorLogic() instanceof IOxygenTank tank) {
                return tank;
            }
        }
        return null;
    }
}
