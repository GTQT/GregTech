package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IHeatable;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;
import gregtech.integration.theoneprobe.handler.TextColor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.capabilities.Capability;

import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.TextStyleClass;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;
import mcjty.theoneprobe.apiimpl.styles.ProgressStyle;
import org.jetbrains.annotations.NotNull;

public class HeatContainerInfoProvider extends CapabilityInfoProvider<IHeatable> {

    @Override
    public String getID() {
        return GTValues.MODID + ":heat_container_provider";
    }

    @NotNull
    @Override
    protected Capability<IHeatable> getCapability() {
        return GregtechCapabilities.CAPABILITY_HEAT_CONTAINER;
    }

    @Override
    protected void addProbeInfo(@NotNull IHeatable capability, @NotNull IProbeInfo probeInfo,
                                EntityPlayer player, @NotNull TileEntity tileEntity, @NotNull IProbeHitData data) {
        if (tileEntity instanceof TileEntityHeatConductor) return;

        long maxHeat = capability.getHeatCapacity();
        long storedHeat = capability.getHeatStored();

        // 如果容量为0，则不显示进度条
        if (maxHeat == 0) return;

        // 获取温度信息
        int currentTemp = capability.getTemperature();
        int maxTemp = capability.getMaxTemperature();

        // 创建水平面板显示基本信息
        IProbeInfo horizontalPane = probeInfo.horizontal(probeInfo.defaultLayoutStyle().alignment(
                ElementAlignment.ALIGN_CENTER));

        // 温度显示
        horizontalPane.text(TextStyleClass.INFO + "{*gregtech.top.heat_pipe.temperature*}");
        horizontalPane.text(TextStyleClass.INFO + " " +
                TextColor.getTemperatureColor(currentTemp, maxTemp) +
                currentTemp + " / " + maxTemp + " K");

        // 显示热量存储进度条
        probeInfo.progress(storedHeat, maxHeat, probeInfo.defaultProgressStyle()
                .numberFormat(player.isSneaking() || storedHeat < 10000 ?
                        NumberFormat.COMMAS :
                        NumberFormat.COMPACT)
                .suffix(" / " + (player.isSneaking() || maxHeat < 10000 ?
                        ElementProgress.format(maxHeat, NumberFormat.COMMAS, " HU") :
                        ElementProgress.format(maxHeat, NumberFormat.COMPACT, "HU")))
                .filledColor(0xFFCC5500)  // 橙色表示热量
                .alternateFilledColor(0xFFCC5500)
                .borderColor(0xFF553300));

        // 温度进度条（显示当前温度占最大温度的比例）
        if (maxTemp > 0) {
            int progress = (int) ((currentTemp / (float) maxTemp) * 100);
            progress = Math.min(progress, 100); // 确保不超过100%

            // 根据温度比例设置进度条颜色
            int color = TextColor.getTemperatureProgressColor(currentTemp, maxTemp);

            probeInfo.progress(progress, 100,
                    new ProgressStyle()
                            .suffix(" %")
                            .filledColor(color)
                            .alternateFilledColor(color)
                            .backgroundColor(0xFF555555)
                            .borderColor(0xFF333333)
                            .numberFormat(NumberFormat.COMMAS)
                            .showText(true));
        }

        // 显示热量传输速率（如果支持）
        long inputPerSec = capability.getInputPerSec();
        long outputPerSec = capability.getOutputPerSec();

        if (inputPerSec > 0 || outputPerSec > 0) {
            if (inputPerSec > 0) {
                probeInfo.text("{*gregtech.top.heat.input*} " +
                        (player.isSneaking() || inputPerSec < 10000 ?
                                ElementProgress.format(inputPerSec, NumberFormat.COMMAS, " HU/s") :
                                ElementProgress.format(inputPerSec, NumberFormat.COMPACT, "HU/s")));
            }
            if (outputPerSec > 0) {
                probeInfo.text("{*gregtech.top.heat.output*} " +
                        (player.isSneaking() || outputPerSec < 10000 ?
                                ElementProgress.format(outputPerSec, NumberFormat.COMMAS, " HU/s") :
                                ElementProgress.format(outputPerSec, NumberFormat.COMPACT, "HU/s")));
            }
        }

        // 显示热量状态（是否可接受/输出）
        if (player.isSneaking()) {
            probeInfo.text(capability.canAcceptHeat() ? "{*gregtech.top.heat.accept*}" :
                    "{*gregtech.top.heat.cannot_accept*}");
            if (capability.canOutputHeat()) {
                probeInfo.text("{*gregtech.top.heat.can_output*}");
            }
        }
    }
}
