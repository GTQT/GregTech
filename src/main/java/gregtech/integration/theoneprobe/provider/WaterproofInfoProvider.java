package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.items.behaviors.WaterproofSprayBehavior;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import org.jetbrains.annotations.NotNull;

public class WaterproofInfoProvider implements IProbeInfoProvider {

    private static final String KEY_WATERPROOF = "gregtech.top.waterproof";

    private static void show(@NotNull IProbeInfo probeInfo, @NotNull TextFormatting color, @NotNull String key) {
        probeInfo.text(color + IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC);
    }

    @Override
    public String getID() {
        return GTValues.MODID + ":waterproof_provider";
    }

    @Override
    public void addProbeInfo(@NotNull ProbeMode mode, @NotNull IProbeInfo probeInfo, @NotNull EntityPlayer player,
                             @NotNull World world, @NotNull IBlockState blockState, @NotNull IProbeHitData data) {
        MetaTileEntity mte = WaterproofSprayBehavior.getMachine(world, data.getPos());
        if (mte == null) {
            return;
        }

        if (mte.getIsWeatherOrTerrainResistant() || mte.isWaterproof()) {
            show(probeInfo, TextFormatting.GREEN, KEY_WATERPROOF);
        }
    }
}
