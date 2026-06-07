package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.TextComponentUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHPCA;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityNetworkSwitch;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;

public class ComputationProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":computation";
    }
    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo info, EntityPlayer player, World world,
                             IBlockState state, IProbeHitData data) {
        if (state.getBlock().hasTileEntity(state)) {
            TileEntity tileEntity = world.getTileEntity(data.getPos());
            if (tileEntity instanceof IGregTechTileEntity) {
                MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();

                if (metaTileEntity instanceof MetaTileEntityHPCA && ((MetaTileEntityHPCA) metaTileEntity).isStructureFormed()) {
                    displayCWUInfo(info, "gregtech.multiblock.computation.max", ((MetaTileEntityHPCA) metaTileEntity).getMaxCWUt());
                }

                if (metaTileEntity instanceof MetaTileEntityResearchStation && ((MetaTileEntityResearchStation) metaTileEntity).isStructureFormed()) {
                    displayCWUInfo(info, "gregtech.multiblock.computation.usage",
                            ((MetaTileEntityResearchStation) metaTileEntity).getRecipeMapWorkable().getCurrentDrawnCWUt());
                }

                if (metaTileEntity instanceof MetaTileEntityNetworkSwitch && ((MetaTileEntityNetworkSwitch) metaTileEntity).isStructureFormed()) {
                    displayCWUInfo(info, "gregtech.multiblock.computation.max", ((MetaTileEntityNetworkSwitch) metaTileEntity).getMaxCWUt());
                }
            }
        }
    }

    private void displayCWUInfo(IProbeInfo info, String translationKey, int cwuValue) {
        ITextComponent provide = TextComponentUtil.translationWithColor(
                TextFormatting.AQUA,
                translationKey,
                TextComponentUtil.stringWithColor(TextFormatting.AQUA,
                        TextFormattingUtil.formatNumbers(cwuValue) + " CWU/t"));
        info.text(provide.getFormattedText());
    }
}
