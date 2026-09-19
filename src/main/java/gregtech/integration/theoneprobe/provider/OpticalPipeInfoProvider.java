package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import mcjty.theoneprobe.apiimpl.styles.ProgressStyle;

/**
 * Shows the optical signal strength carried by the cable the player is pointing at, out of the
 * maximum the cable material can hold.
 */
public class OpticalPipeInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":optical_pipe";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo probeInfo, EntityPlayer player, World world,
                             IBlockState blockState, IProbeHitData hitData) {
        if (!(world.getTileEntity(hitData.getPos()) instanceof TileEntityOpticalPipe pipe)) return;

        int strength = (int) Math.round(pipe.getSignalStrength() * 100.0d);
        probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.optical_pipe.strength*}" + TextStyleClass.INFO + " " +
                TextStyleClass.OK + strength + " %");
        probeInfo.progress(strength, 100, new ProgressStyle()
                .suffix(" %")
                .filledColor(0xFF00C8FF)
                .alternateFilledColor(0xFF0080A0)
                .backgroundColor(0xFF555555)
                .borderColor(0xFF333333)
                .numberFormat(NumberFormat.COMMAS));
    }
}
