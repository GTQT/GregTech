package gregtech.common.metatileentities.multi.multiblockpart.qc;

import gregtech.api.GTValues;
import gregtech.api.capability.IQCUncertaintyHatch;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 创造版量子不确定性舱：始终以最高解析模式（5）运行且永远视为已解析，
 * 无需小游戏，也没有 GUI。
 */
public class MetaTileEntityQCCreativeUncertaintyHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IQCUncertaintyHatch>, IQCUncertaintyHatch {

    public MetaTileEntityQCCreativeUncertaintyHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.MAX);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQCCreativeUncertaintyHatch(metaTileEntityId);
    }

    // region IQCUncertaintyHatch —— 最高档 + 永远已解析

    @Override
    public int getUncertaintyMode() {
        return 5;
    }

    @Override
    public void updateUncertaintyMode(int mode) {
        // 不受结构层数限制，始终为最高模式
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    // endregion

    @Override
    public MultiblockAbility<IQCUncertaintyHatch> getAbility() {
        return MultiblockAbility.QC_UNCERTAINTY;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public boolean canPartShare() {
        return false;
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false; // 无需小游戏 UI
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.creative_tooltip.1") + TooltipHelper.RAINBOW +
                I18n.format("gregtech.creative_tooltip.2") + I18n.format("gregtech.creative_tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.creative_uncertainty_hatch.tooltip"));
    }
}
