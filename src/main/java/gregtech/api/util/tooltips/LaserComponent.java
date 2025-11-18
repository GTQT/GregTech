package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class LaserComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.laser_available"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.tooltip.laser_hatch.tooltips"));
    }
}
