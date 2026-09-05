package gregtech.common.items.behaviors.nuclear;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ComponentHeatExchangerBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;       // 材料
    @Getter
    private final int heatTransferRate;    // 热传递速率（HU/t）

    public ComponentHeatExchangerBehavior(int maxDurability,
                                          Material material,
                                          int heatTransferRate) {
        super(maxDurability);
        this.material = material;
        this.heatTransferRate = Math.max(1, heatTransferRate);
    }

    @Nullable
    public static ComponentHeatExchangerBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof ComponentHeatExchangerBehavior)) return null;

        return (ComponentHeatExchangerBehavior) durabilityManager;
    }

    // 获取耐久消耗
    public int getDurabilityCost() {
        return 1;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        super.addInformation(stack, lines);

        lines.add(I18n.format("材料: " + material.getLocalizedName()));
        lines.add(I18n.format("热传递速率: " + heatTransferRate + " HU/t"));
        lines.add(I18n.format("耐久消耗: " + getDurabilityCost() + "/tick"));
        lines.add(I18n.format("元件热交换器: 在组件间传递热量"));
    }
}
