package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.electric.MetaTileEntityEnergyDistributor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;

public class EnergyDistributorInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":energy_distributor_provider";
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo info, EntityPlayer player, World world, IBlockState state,
                             IProbeHitData hitData) {
        if (!state.getBlock().hasTileEntity(state)) return;

        var tile = world.getTileEntity(hitData.getPos());
        if (!(tile instanceof IGregTechTileEntity igte)) return;

        var mte = igte.getMetaTileEntity();
        if (!(mte instanceof MetaTileEntityEnergyDistributor distributor)) return;

        if (distributor.isDistributeMode()) {
            info.text("{*gregtech.top.energy_distributor.mode_distribute*}");
        } else {
            info.text("{*gregtech.top.energy_distributor.mode_combine*}");
        }
    }
}
