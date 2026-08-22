package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.ColorUtil;
import gregtech.api.util.GTUtility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import org.jetbrains.annotations.NotNull;

/**
 * 在 TOP 检测器信息中显示仓的颜色通道(输入仓染色隔离)。
 * 与 GT5U 的 WAILA 颜色通道显示对应。
 */
public class ColorChannelInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":color_channel_provider";
    }

    @Override
    public void addProbeInfo(@NotNull ProbeMode mode, @NotNull IProbeInfo probeInfo, @NotNull EntityPlayer player,
                             @NotNull World world, @NotNull IBlockState blockState, @NotNull IProbeHitData data) {
        MetaTileEntity mte = GTUtility.getMetaTileEntity(world, data.getPos());
        if (mte == null || !mte.isPainted()) return;
        EnumDyeColor dye = ColorUtil.getDyeColorFromRGB(mte.getPaintingColor());
        if (dye == null) return;
        String dyeName = I18n.translateToLocal("item.dyePowder." + dye.getName() + ".name");
        probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.color_channel*} "
                + ColorUtil.getTextFormatting(mte.getPaintingColor()) + dyeName);
    }
}
