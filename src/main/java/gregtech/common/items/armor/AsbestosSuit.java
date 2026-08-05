package gregtech.common.items.armor;

import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.api.items.armor.ISpecialArmorLogic;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.common.items.behaviors.TooltipBehavior;

import net.minecraft.client.resources.I18n;

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
import net.minecraftforge.common.ISpecialArmor.ArmorProperties;

import org.jetbrains.annotations.NotNull;

public class AsbestosSuit implements ISpecialArmorLogic, IItemDurabilityManager {

    protected final EntityEquipmentSlot SLOT;
    protected final int maxDurability;

    public AsbestosSuit(EntityEquipmentSlot slot, int maxDurability) {
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
                "gregtech:textures/armor/asbestos_1.png" :
                "gregtech:textures/armor/asbestos_2.png";
    }

    @Override
    public float getHeatResistance() {
        return 0.0f;
    }

    @Override
    public float getElectricResistance() {
        return 0.5f;
    }

    // ---- Physical armor ----

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, @NotNull ItemStack armor, DamageSource source,
                                         double damage, EntityEquipmentSlot equipmentSlot) {
        if (source.isUnblockable()) return new ArmorProperties(0, 0, 0);
        return new ArmorProperties(0, getAbsorption(SLOT), getDurability(armor) > 0 ? Integer.MAX_VALUE : 0);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, @NotNull ItemStack armor, int slot) {
        return (int) (getAbsorption(SLOT) * 20);
    }

    @Override
    public boolean handleUnblockableDamage(EntityLivingBase entity, @NotNull ItemStack armor, DamageSource source,
                                           double damage, EntityEquipmentSlot equipmentSlot) {
        return false;
    }

    private static float getAbsorption(EntityEquipmentSlot slot) {
        return switch (slot) {
            case HEAD, FEET -> 0.10f;
            case LEGS -> 0.20f;
            case CHEST -> 0.25f;
            default -> 0f;
        };
    }

    // ---- Durability ----

    @Override
    public void addToolComponents(ArmorMetaItem.ArmorMetaValueItem metaValueItem) {
        metaValueItem.addComponents(this);
        metaValueItem.addComponents(new TooltipBehavior(lines -> {
            lines.addAll(getResistanceTooltips());
        }) {
            @Override
            public void addInformation(ItemStack stack, java.util.List<String> lines) {
                lines.add(1, I18n.format("gregtech.armor.durability", getDurability(stack), maxDurability));
                super.addInformation(stack, lines);
            }
        });
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (double) getDurability(stack) / maxDurability;
    }

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
        if (getDurability(itemStack) <= 0 && entity instanceof EntityPlayer player) {
            player.renderBrokenItemStack(itemStack);
            player.setItemStackToSlot(SLOT, ItemStack.EMPTY);
        }
    }

    public int getDurability(ItemStack stack) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        if (!stack.getTagCompound().hasKey("durability")) {
            stack.getTagCompound().setInteger("durability", maxDurability);
        }
        return stack.getTagCompound().getInteger("durability");
    }

    public void changeDurability(ItemStack stack, int delta) {
        if (!stack.hasTagCompound()) return;
        stack.getTagCompound().setInteger("durability", getDurability(stack) + delta);
        stack.setTagCompound(stack.getTagCompound());
    }
}
