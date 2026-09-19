package gregtech.common.items.behaviors.nuclear;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Active coolant component. Its heat capacity is the amount of heat it can absorb before it is used up: the reactor
 * simulator spends one durability point per HU the cell actually absorbs, so a cell that is not cooling anything is
 * not consumed either.
 */
public class CoolantCellBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material coolantMaterial;       // 冷却剂材料
    @Getter
    private final int coolingRate;                // 冷却速率（HU/s，按每个模拟步=1秒结算）

    public CoolantCellBehavior(Material coolantMaterial,
                               int heatCapacity,
                               int coolingRate) {
        super(Math.max(1, heatCapacity));
        this.coolantMaterial = coolantMaterial;
        this.coolingRate = Math.max(1, coolingRate);
    }

    @Nullable
    public static CoolantCellBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof CoolantCellBehavior)) return null;

        return (CoolantCellBehavior) durabilityManager;
    }

    /** @return the total heat this cell can absorb over its life, in HU (its durability). */
    public int getHeatCapacity(ItemStack itemStack) {
        return getPartMaxDurability(itemStack);
    }

    @Override
    public boolean wearsOnlyWhileWorking() {
        return true;
    }

    /** A coolant cell is consumed by the heat it absorbs: one durability point per HU. */
    @Override
    public int getDurabilityCostForStep(int heatMoved) {
        return Math.max(0, heatMoved);
    }

    public boolean applyDamage(ItemStack itemStack, int damageApplied) {
        int Durability = getPartMaxDurability(itemStack);
        int resultDamage = getPartDamage(itemStack) + damageApplied;
        if (resultDamage >= Durability) {
            return false;
        } else {
            setPartDamage(itemStack, resultDamage);
            return true;
        }
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        super.addInformation(stack, lines);

        // 基础信息
        lines.add(I18n.format("冷却剂: " + coolantMaterial.getLocalizedName()));

        // 性能参数（热量按每个模拟步=1秒结算）
        lines.add(I18n.format("热容量: " + getHeatCapacity(stack) + " HU（每吸收1HU消耗1点，耗尽后报废）"));
        lines.add(I18n.format("冷却速率: " + coolingRate + " HU/s"));
    }
}
