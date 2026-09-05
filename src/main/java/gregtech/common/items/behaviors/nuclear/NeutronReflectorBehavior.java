package gregtech.common.items.behaviors.nuclear;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NeutronReflectorBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;               // 材料
    @Getter
    private final float reflectionEfficiency;      // 中子反射效率 0.0-1.0

    public NeutronReflectorBehavior(int maxDurability,
                                    Material material,
                                    float reflectionEfficiency) {
        super(maxDurability);
        this.material = material;
        this.reflectionEfficiency = Math.max(0.0f, Math.min(1.0f, reflectionEfficiency));
    }

    @Nullable
    public static NeutronReflectorBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof NeutronReflectorBehavior)) return null;

        return (NeutronReflectorBehavior) durabilityManager;
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
        lines.add(I18n.format("中子反射效率: " + String.format("%.1f", reflectionEfficiency * 100) + "%%"));

        // 每tick耐久消耗
        lines.add(I18n.format("耐久消耗: " + getDurabilityCost() + "/tick"));

        // 功能说明
        lines.add(I18n.format("中子反射: 提高相邻燃料棒效率"));
    }
}
