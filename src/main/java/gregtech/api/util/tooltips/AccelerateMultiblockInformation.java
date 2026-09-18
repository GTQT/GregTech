package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class AccelerateMultiblockInformation extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.accelerate_avaliable"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.tooltip.accelerate_enabled"));
        if (TooltipHelper.isShiftDown()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tile.gregtech.accelerate.tooltip.1"));
            tooltip.add(TextFormatting.GRAY + I18n.format("tile.gregtech.accelerate.tooltip.2"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.shift"));
        }
    }
}
