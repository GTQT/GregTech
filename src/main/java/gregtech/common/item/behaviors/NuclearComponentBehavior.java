package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.items.metaitem.stats.IItemMaxStackSizeProvider;
import gregtech.common.items.behaviors.AbstractMaterialPartBehavior;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NuclearComponentBehavior extends AbstractMaterialPartBehavior
        implements IItemDurabilityManager, IItemMaxStackSizeProvider {

    int MaxDurability;

    public NuclearComponentBehavior(int Durability) {
        this.MaxDurability = Durability;
    }

    @Nullable
    public static NuclearComponentBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof NuclearComponentBehavior)) return null;

        return (NuclearComponentBehavior) durabilityManager;
    }

    public double getDurabilityPercent(ItemStack itemStack) {
        return 1 - (double) getPartDamage(itemStack) / getPartMaxDurability(itemStack);
    }

    public boolean applyDamage(ItemStack itemStack, int damageApplied) {
        int Durability = getPartMaxDurability(itemStack);
        int resultDamage = getPartDamage(itemStack) + damageApplied;
        if (resultDamage >= Durability) {
            itemStack.shrink(1);
            return false;
        } else {
            setPartDamage(itemStack, resultDamage);
            return true;
        }
    }

    public void addInformation(ItemStack stack, List<String> lines) {
        int maxDurability = getPartMaxDurability(stack);
        int damage = getPartDamage(stack);
        lines.add(I18n.format("metaitem.tool.tooltip.durability", maxDurability - damage, maxDurability));
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
