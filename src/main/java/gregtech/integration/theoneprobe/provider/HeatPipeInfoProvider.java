package gregtech.integration.theoneprobe.provider;

import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;
import gregtech.integration.theoneprobe.handler.TextColor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;

import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import mcjty.theoneprobe.apiimpl.styles.ProgressStyle;

@Mod.EventBusSubscriber
public class HeatPipeInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return "gregtech:heat_pipe";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo iProbeInfo, EntityPlayer entityPlayer, World world,
                             IBlockState iBlockState, IProbeHitData iProbeHitData) {
        if (world.getTileEntity(iProbeHitData.getPos()) instanceof TileEntityHeatConductor heatPipe) {
            // 获取热导属性
            HeatConductorProperties properties = heatPipe.getNodeData();

            // 当前温度和最大温度
            int currentTemp = heatPipe.getTemperature();
            int maxTemp = properties.getMaxTemperature();

            // 热传导率和热损失
            int heatTransfer = properties.getHeatTransfer();
            float heatLossPercent = properties.getHeatLossPerBlock();

            // 创建水平面板显示基本信息
            IProbeInfo horizontalPane = iProbeInfo.horizontal(
                    iProbeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER));

            // 温度显示
            horizontalPane.text(TextStyleClass.INFO + "{*gregtech.top.heat_pipe.temperature*}");
            horizontalPane.text(TextStyleClass.INFO + " " +
                    TextColor.getTemperatureColor(currentTemp, maxTemp) +
                    currentTemp + " / " + maxTemp + " K");

            // 热传导率显示
            iProbeInfo.text(TextStyleClass.INFO + "{*gregtech.top.heat_pipe.heat_transfer*}" +
                    TextStyleClass.INFO + " " + TextStyleClass.OK + heatTransfer + " HU/t");

            // 热损失显示
            iProbeInfo.text(TextStyleClass.INFO + "{*gregtech.top.heat_pipe.heat_loss*}" +
                    TextStyleClass.INFO + " " + TextStyleClass.WARNING + heatLossPercent + "%");

            // 温度进度条（显示当前温度占最大温度的比例）
            if (maxTemp > 0) {
                int progress = (int) ((currentTemp / (float) maxTemp) * 100);
                progress = Math.min(progress, 100); // 确保不超过100%

                // 根据温度比例设置进度条颜色
                int color = TextColor.getTemperatureProgressColor(currentTemp, maxTemp);

                iProbeInfo.progress(progress, 100,
                        new ProgressStyle()
                                .suffix(" %")
                                .filledColor(color)
                                .alternateFilledColor(color)
                                .backgroundColor(0xFF555555)
                                .borderColor(0xFF333333)
                                .numberFormat(NumberFormat.COMMAS)
                                .showText(true));
            }
        }
    }

}
