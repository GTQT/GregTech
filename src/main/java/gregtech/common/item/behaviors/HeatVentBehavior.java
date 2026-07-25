package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;
import lombok.Getter;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HeatVentBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;               // 材料
    @Getter
    private final int heatDissipation;            // 散热能力（HU/t）

    public HeatVentBehavior(int maxDurability,
                            Material material,
                            int heatDissipation) {
        super(maxDurability);
        this.material = material;
        this.heatDissipation = Math.max(1, heatDissipation);
    }

    @Nullable
    public static HeatVentBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof HeatVentBehavior)) return null;

        return (HeatVentBehavior) durabilityManager;
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
        lines.add(I18n.format("散热能力: " + heatDissipation + " HU/t"));

        // 每tick耐久消耗
        lines.add(I18n.format("耐久消耗: " + getDurabilityCost() + "/tick"));
    }
}