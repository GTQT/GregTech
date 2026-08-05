/*
 * Ported from Susy-Core (https://github.com/SymmetricDevs/Susy-Core)
 * Copyright (c) 2023 SuperSymmetry contributors
 * Licensed under LGPLv3
 *
 * Original source: supersymmetry.common.item.armor.SimpleGasMask
 * Modified for GregTech integration.
 */
package gregtech.common.items.armor;

import gregtech.api.items.armor.IArmorLogic;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A simple single-use gas mask that provides full poison protection.
 * Has a limited lifetime of 600 seconds in hazardous environments.
 * Intended to be consumed when depleted.
 */
public class SimpleGasMask implements IArmorLogic {

    public static final double LIFETIME = 600;

    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        return EntityEquipmentSlot.HEAD;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "gregtech:textures/armor/simple_gas_mask.png";
    }

    @Override
    public float getPoisonResistance() {
        return 0.0f;
    }

    @Override
    public boolean canBreakWithDamage(ItemStack stack) {
        return getDamage(stack) >= 1;
    }

    public double getDamage(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (!stack.getTagCompound().hasKey("damage")) {
            stack.getTagCompound().setDouble("damage", 0);
        }
        return stack.getTagCompound().getDouble("damage");
    }

    public void changeDamage(ItemStack stack, double damageChange) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound == null) return;
        compound.setDouble("damage", getDamage(stack) + damageChange);
        stack.setTagCompound(compound);
    }
}
