package gtqt.common.items.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.items.metaitem.stats.IItemMaxStackSizeProvider;
import gregtech.api.unification.material.Material;
import gregtech.common.items.behaviors.AbstractMaterialPartBehavior;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import gtqt.api.util.GTQTDateHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WindRotorBehavior extends AbstractMaterialPartBehavior implements IItemMaxStackSizeProvider {

    int MaxDurability;
    int tier;
    Material material;

    public WindRotorBehavior(int Durability, int tier, Material material) {
        this.MaxDurability = Durability;
        this.material = material;
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Nullable
    public static WindRotorBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof WindRotorBehavior)) return null;

        return (WindRotorBehavior) durabilityManager;
    }

    public double getDurabilityPercent(ItemStack itemStack) {
        return 1 - (double) getPartDamage(itemStack) / getPartMaxDurability(itemStack);
    }

    public void applyDamage(ItemStack itemStack, int damageApplied) {
        int Durability = getPartMaxDurability(itemStack);
        int resultDamage = getPartDamage(itemStack) + damageApplied;
        if (resultDamage >= Durability) {
            itemStack.shrink(1);
        } else {
            setPartDamage(itemStack, resultDamage);
        }
    }

    public void addInformation(ItemStack stack, List<String> lines) {
        int maxDurability = getPartMaxDurability(stack);
        int damage = getPartDamage(stack);
        lines.add(I18n.format("metaitem.tool.tooltip.durability", maxDurability - damage, maxDurability));
        lines.add(I18n.format("metaitem.tool.tooltip.primary_material", material.getLocalizedName()));
        lines.add(I18n.format("预计工作: " + GTQTDateHelper.getTimeFromTicks(MaxDurability)));
        lines.add(I18n.format("距离损坏: " + GTQTDateHelper.getTimeFromTicks(MaxDurability - getPartDamage(stack))));
    }

    @Override
    public int getPartMaxDurability(ItemStack itemStack) {
        return MaxDurability;
    }

    @Override
    public int getMaxStackSize(ItemStack itemStack, int i) {
        return 1;
    }

}
