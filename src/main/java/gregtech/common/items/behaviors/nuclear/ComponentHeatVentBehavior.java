package gregtech.common.items.behaviors.nuclear;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ComponentHeatVentBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;               // 材料
    @Getter
    private final int coolingRate;                // 冷却速率（HU/t）

    public ComponentHeatVentBehavior(int maxDurability,
                                     Material material,
                                     int coolingRate) {
        super(maxDurability);
        this.material = material;
        this.coolingRate = Math.max(1, coolingRate);
    }

    @Nullable
    public static ComponentHeatVentBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof ComponentHeatVentBehavior)) return null;

        return (ComponentHeatVentBehavior) durabilityManager;
    }

    // 获取耐久消耗（每tick固定消耗1耐久）
    public int getDurabilityCost() {
        return 1;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        super.addInformation(stack, lines);

        // 基础信息
        lines.add(I18n.format("材料: " + material.getLocalizedName()));

        // 性能参数
        lines.add(I18n.format("冷却速率: " + coolingRate + " HU/t"));

        // 每tick耐久消耗
        lines.add(I18n.format("耐久消耗: " + getDurabilityCost() + "/tick"));

        // 特性说明
        lines.add(I18n.format("元件散热: 冷却相邻组件"));
    }
}
