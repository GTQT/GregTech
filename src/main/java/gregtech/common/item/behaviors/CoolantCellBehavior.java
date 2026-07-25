package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;
import lombok.Getter;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CoolantCellBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material coolantMaterial;       // 冷却剂材料
    @Getter
    private final int heatCapacity;               // 热容量（HU）
    @Getter
    private final int coolingRate;                // 冷却速率（HU/t）

    public CoolantCellBehavior(int maxDurability,
                               Material coolantMaterial,
                               int heatCapacity,
                               int coolingRate) {
        super(maxDurability);
        this.coolantMaterial = coolantMaterial;
        this.heatCapacity = Math.max(1, heatCapacity);
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

    // 获取当前冷却量（直接返回冷却速率）
    public int getCoolingAmount() {
        return coolingRate;
    }

    // 获取耐久消耗（每tick固定消耗1耐久）
    public int getDurabilityCost() {
        return 1;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        super.addInformation(stack, lines);

        // 基础信息
        lines.add(I18n.format("冷却剂: " + coolantMaterial.getLocalizedName()));

        // 性能参数
        lines.add(I18n.format("热容量: " + heatCapacity + " HU"));
        lines.add(I18n.format("冷却速率: " + coolingRate + " HU/t"));

        // 每tick耐久消耗
        lines.add(I18n.format("耐久消耗: " + getDurabilityCost() + "/tick"));
    }
}