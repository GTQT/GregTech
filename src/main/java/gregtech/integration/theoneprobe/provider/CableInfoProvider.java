package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;

public class CableInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":cable";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo probeInfo, EntityPlayer player,
                             World world, IBlockState blockState, IProbeHitData hitData) {
        if (world.getTileEntity(hitData.getPos()) instanceof TileEntityCable cable) {
            double avgVoltage = cable.getAverageVoltage();
            double avgAmperage = cable.getAverageAmperage();
            long ratedVoltage = cable.getMaxVoltage();
            long ratedAmperage = cable.getMaxAmperage();

            // Use actual peak voltage recorded on the cable, capped at rated limit
            long peakVoltage = Math.min(cable.getCurrentMaxVoltage(), ratedVoltage);

            // Compute current power: voltage (capped at rated) × average amperage
            double operatingVoltage = Math.min(avgVoltage, ratedVoltage);
            double currentPower = operatingVoltage * avgAmperage;
            double maxRatedPower = (double) ratedVoltage * ratedAmperage;

            String amperageStr = TextFormattingUtil.formatNumbers(avgAmperage);
            String ratedAmperageStr = TextFormattingUtil.formatNumbers(ratedAmperage);

            String tierStr = GTValues.VNF[GTUtility.getTierByVoltage(peakVoltage)];
            String ratedTierStr = GTValues.VNF[GTUtility.getTierByVoltage(ratedVoltage)];

            probeInfo.progress((int) currentPower, (int) maxRatedPower, probeInfo.defaultProgressStyle()
                    .numberFormat(player.isSneaking() || currentPower < 10000 ?
                            NumberFormat.COMMAS :
                            NumberFormat.COMPACT)
                    .suffix(" / " + (player.isSneaking() || maxRatedPower < 10000 ?
                            ElementProgress.format((long) maxRatedPower, NumberFormat.COMMAS, " W") :
                            ElementProgress.format((long) maxRatedPower, NumberFormat.COMPACT, "W")))
                    .filledColor(0xFFEEE600)
                    .alternateFilledColor(0xFFEEE600)
                    .borderColor(0xFF555555));

            probeInfo.text("{*gregtech.top.v*}" + " " +
                    TextFormatting.AQUA + avgVoltage +
                    TextFormatting.WHITE + TextFormatting.BOLD + "/" +
                    TextFormatting.GOLD + ratedVoltage +
                    TextFormatting.RED + " EU" +
                    TextFormatting.DARK_GRAY +
                    " (" + tierStr +
                    TextFormatting.DARK_GRAY + "/" +
                    ratedTierStr +
                    TextFormatting.DARK_GRAY + ")");

            probeInfo.text("{*gregtech.top.i*}" + " " +
                    TextFormatting.AQUA + amperageStr +
                    TextFormatting.WHITE + TextFormatting.BOLD + "/" +
                    TextFormatting.GOLD + ratedAmperageStr +
                    TextFormatting.RED + " A");
        }
    }
}
