package gregtech.common.items.behaviors.nuclear;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ReactorHeatExchangerBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;       // 材料
    @Getter
    private final int heatStorage;         // 热存储容量（HU），限制单个模拟步能搬运的热量
    @Getter
    private final int transferRate;        // 热传递速率（HU/s，按每个模拟步=1秒结算）

    public ReactorHeatExchangerBehavior(int maxDurability,
                                        Material material,
                                        int heatStorage,
                                        int transferRate) {
        super(maxDurability);
        this.material = material;
        this.heatStorage = Math.max(1, heatStorage);
        this.transferRate = Math.max(1, transferRate);
    }

    @Nullable
    public static ReactorHeatExchangerBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof ReactorHeatExchangerBehavior)) return null;

        return (ReactorHeatExchangerBehavior) durabilityManager;
    }

    // 获取耐久消耗
    public int getDurabilityCost() {
        return 1;
    }

    /** An exchanger is only consumed while it actually moves heat into an adjacent sink. */
    @Override
    public boolean wearsOnlyWhileWorking() {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        super.addInformation(stack, lines);

        lines.add(I18n.format("材料: " + material.getLocalizedName()));
        lines.add(I18n.format("热存储容量: " + heatStorage + " HU（限制单个模拟步可搬运的热量）"));
        lines.add(I18n.format("热传递速率: " + transferRate + " HU/s"));
        lines.add(I18n.format("耐久消耗: " + getDurabilityCost() + "/s"));
        lines.add(I18n.format("反应堆热交换器: 把堆芯热量转移给相邻的散热片/冷却单元（需紧邻热沉）"));
    }
}
