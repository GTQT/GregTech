package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.IBatch;
import gregtech.api.capability.IRecipeLock;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;

public class MultiblockModeProvider implements IProbeInfoProvider {

    private static IProbeInfo newVertical(final IProbeInfo probeInfo) {
        return probeInfo.vertical(probeInfo.defaultLayoutStyle().spacing(0));
    }

    private static IProbeInfo newBox(final IProbeInfo info) {
        return info.horizontal(info.defaultLayoutStyle().borderColor(0x801E90FF));
    }

    @Override
    public String getID() {
        return GTValues.MODID + ":multiblock_mode_provider";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo iProbeInfo, EntityPlayer entityPlayer, World world,
                             IBlockState iBlockState, IProbeHitData iProbeHitData) {
        if (iBlockState.getBlock().hasTileEntity(iBlockState)) {
            TileEntity te = world.getTileEntity(iProbeHitData.getPos());
            if (te instanceof IGregTechTileEntity igtte) {
                MetaTileEntity mte = igtte.getMetaTileEntity();

                if (mte instanceof IBatch iBatch) {
                    if (iBatch.isBatchAllowed()) iProbeInfo.text(
                            TextStyleClass.INFO + (iBatch.isBatchEnable() ? "{*gregtech.top.batch_enable*}" :
                                    "{*gregtech.top.batch_disable*}"));
                }
                if (mte instanceof IRecipeLock recipeLock) {
                    if (recipeLock.enableExtendControl()) iProbeInfo.text(
                            TextStyleClass.INFO + (recipeLock.isRecipeLocked() ? "{*gregtech.top.lock_enable*}" :
                                    "{*gregtech.top.lock_disable*}"));
                }
            }
        }
    }
}
