package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;
import lombok.Getter;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ReactorPlatingBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;
    @Getter
    private final int heatCapacityBoost;
    @Getter
    private final float explosionResistance;

    public ReactorPlatingBehavior(int maxDurability,
                                  Material material,
                                  int heatCapacityBoost,
                                  float explosionResistance) {
        super(Math.max(1, maxDurability));
        this.material = material;
        this.heatCapacityBoost = Math.max(0, heatCapacityBoost);
        this.explosionResistance = Math.max(0.0f, Math.min(1.0f, explosionResistance));
    }

    @Nullable
    public static ReactorPlatingBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof ReactorPlatingBehavior)) return null;

        return (ReactorPlatingBehavior) durabilityManager;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        super.addInformation(stack, lines);

        // 基础信息
        lines.add(I18n.format("材料: " + material.getLocalizedName()));

        // 性能参数
        if (heatCapacityBoost > 0) {
            lines.add(I18n.format("热容量提升: " + heatCapacityBoost + " HU"));
        }

        if (explosionResistance > 0) {
            lines.add(I18n.format("爆炸抗性: " + String.format("%.1f", explosionResistance * 100) + "%%"));
        }

        // 每tick耐久消耗
        lines.add(I18n.format("耐久消耗: 永不消耗"));

        // 功能说明
        lines.add(I18n.format("反应堆隔板: 提高反应堆结构强度"));
    }
}
