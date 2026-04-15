package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 巨型样板映射区 — 拥有 144 个样板槽位（普通版本的4倍），
 * 其余功能与普通映射区完全相同。
 */
public class MetaTileEntityHugePatternProviderMappingSlave extends MetaTileEntityPatternProviderMappingSlave {

    private static final int HUGE_PATTERN_SLOT_COUNT = 144;

    public MetaTileEntityHugePatternProviderMappingSlave(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHugePatternProviderMappingSlave(metaTileEntityId, getTier());
    }

    @Override
    protected int getPatternSlotCount() {
        return HUGE_PATTERN_SLOT_COUNT;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.huge_pattern_mapping_slave.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.me.data_stick_proxy"));
    }
}
