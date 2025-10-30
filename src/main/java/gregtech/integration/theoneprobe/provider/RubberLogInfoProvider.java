package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.common.blocks.wood.BlockRubberLog;
import gregtech.common.items.MetaItems;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;

public class RubberLogInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":rubber_log_provider";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo probeInfo, EntityPlayer entityPlayer, World world,
                             IBlockState blockState, IProbeHitData probeHitData) {
        if (blockState.getBlock() instanceof BlockRubberLog) {
            BlockRubberLog.RubberWoodState woodState = blockState.getValue(BlockRubberLog.STATE);

            if (woodState.wet) {
                // 湿状态：显示树脂信息
                IProbeInfo horizontalInfo = probeInfo.horizontal(
                        probeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER));

                horizontalInfo.item(MetaItems.STICKY_RESIN.getStackForm());
                horizontalInfo.text(TextStyleClass.OK + "{*gregtech.top.has_resin*}");

                // 在调试模式或潜行时显示更多信息
                if (probeMode == ProbeMode.EXTENDED || entityPlayer.isSneaking()) {
                    probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.resin_ready*}");
                }

            } else if (!woodState.isPlain() && woodState.canRegenerate()) {
                IProbeInfo horizontalInfo = probeInfo.horizontal(
                        probeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER));

                horizontalInfo.item(MetaItems.STICKY_RESIN.getStackForm());
                horizontalInfo.text(TextStyleClass.WARNING + "{*gregtech.top.regenerating*}");
            }

            // 调试模式显示完整状态信息
            if (probeMode == ProbeMode.DEBUG) {
                displayDebugInfo(probeInfo, woodState);
            }
        }
    }

    private void displayDebugInfo(IProbeInfo probeInfo, BlockRubberLog.RubberWoodState woodState) {
        probeInfo.text(TextStyleClass.LABEL + "State: " + woodState.getName());
        probeInfo.text(TextStyleClass.LABEL + "Axis: " + woodState.axis);
        if (woodState.facing != null) {
            probeInfo.text(TextStyleClass.LABEL + "Facing: " + woodState.facing);
        }
        probeInfo.text(TextStyleClass.LABEL + "Wet: " + woodState.wet);
        probeInfo.text(TextStyleClass.LABEL + "Can Regenerate: " + woodState.canRegenerate());
        probeInfo.text(TextStyleClass.LABEL + "Is Plain: " + woodState.isPlain());
    }
}
