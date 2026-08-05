/*
 * Ported from Susy-Core (https://github.com/SymmetricDevs/Susy-Core)
 * Copyright (c) 2023 SuperSymmetry contributors
 * Licensed under LGPLv3
 *
 * Original source: supersymmetry.common.item.armor.AdvancedBreathingApparatus
 *                  supersymmetry.common.item.armor.AdvancedBreathingTank (nomex variant)
 * Modified for GregTech integration — plain armor, no oxygen system.
 */
package gregtech.common.items.armor;

import gregtech.api.items.armor.IArmorLogic;

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

/**
 * Nomex fireproof suit. Full heat immunity, half radiation protection, higher durability.
 */
public class NomexSuit implements IArmorLogic {

    protected final EntityEquipmentSlot SLOT;
    protected final int maxDurability;

    public NomexSuit(EntityEquipmentSlot slot, int maxDurability) {
        this.SLOT = slot;
        this.maxDurability = maxDurability;
    }

    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        return SLOT;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return SLOT != EntityEquipmentSlot.LEGS ?
                "gregtech:textures/armor/nomex_1.png" :
                "gregtech:textures/armor/nomex_2.png";
    }

    @Override
    public float getHeatResistance() {
        return 0.0f;
    }

    @Override
    public float getRadiationResistance() {
        return 0.5f;
    }

    // ---- Durability ----

    @Override
    public boolean canBreakWithDamage(ItemStack stack) {
        return getDurability(stack) <= 0;
    }

    @Override
    public void damageArmor(EntityLivingBase entity, ItemStack itemStack, DamageSource source, int damage,
                            EntityEquipmentSlot equipmentSlot) {
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
        if (getDurability(itemStack) <= 0 && entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            player.renderBrokenItemStack(itemStack);
            player.setItemStackToSlot(SLOT, ItemStack.EMPTY);
        }
    }

    public int getDurability(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (!stack.getTagCompound().hasKey("durability")) {
            stack.getTagCompound().setInteger("durability", maxDurability);
        }
        return stack.getTagCompound().getInteger("durability");
    }

    public void changeDurability(ItemStack stack, int delta) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound compound = stack.getTagCompound();
        compound.setInteger("durability", getDurability(stack) + delta);
        stack.setTagCompound(compound);
    }
}
