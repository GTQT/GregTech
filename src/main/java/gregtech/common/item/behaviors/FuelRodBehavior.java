package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.unification.material.Material;
import lombok.Getter;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FuelRodBehavior extends NuclearComponentBehavior {

    @Getter
    private final Material material;               // 燃料材料
    @Getter
    private final int heatOutput;                 // 热量产出（HU/t）
    @Getter
    private final int energyOutput;               // 能量产出（EU/t）
    @Getter
    private final float neutronEmission;          // 中子发射率
    @Getter
    private final ItemStack depletedItem; // 废弃产物ID

    public FuelRodBehavior(int maxDurability,
                           Material material,
                           int heatOutput,
                           int energyOutput,
                           float neutronEmission,
                           ItemStack depletedItem) {
        super(maxDurability);
        this.material = material;
        this.heatOutput = Math.max(0, heatOutput);
        this.energyOutput = Math.max(0, energyOutput);
        this.neutronEmission = Math.max(0.0f, neutronEmission);
        this.depletedItem = depletedItem;
    }

    @Nullable
    public static FuelRodBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof FuelRodBehavior)) return null;

        return (FuelRodBehavior) durabilityManager;
    }

    @Override
    public boolean applyDamage(ItemStack itemStack, int damageApplied) {
        int Durability = getPartMaxDurability(itemStack);
        int resultDamage = getPartDamage(itemStack) + damageApplied;
        if (resultDamage >= Durability) {
            itemStack.setItemDamage(depletedItem.getItemDamage());
            itemStack.setTagCompound(new NBTTagCompound());
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
        lines.add(I18n.format("燃料材料: " + material.getLocalizedName()));

        // 性能参数
        lines.add(I18n.format("热量产出: " + heatOutput + " HU/t"));
        lines.add(I18n.format("能量产出: " + energyOutput + " EU/t"));
        lines.add(I18n.format("中子发射率: " + String.format("%.2f", neutronEmission)));

        // 废弃产物信息
        lines.add(I18n.format("废弃产物: " + depletedItem.getDisplayName()));

        // 安全警告
        lines.add(I18n.format("§c警告: 完全耗尽后将自动转换为废弃核燃料"));
    }
}