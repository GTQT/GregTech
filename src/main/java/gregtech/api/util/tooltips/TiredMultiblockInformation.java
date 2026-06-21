package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class TiredMultiblockInformation extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.tiered_avaliable"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.tooltip.tiered_hatch_enabled"));
        if (TooltipHelper.isShiftDown()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tile.gregtech.tiered.tooltip.1"));
            tooltip.add(TextFormatting.GRAY + I18n.format("tile.gregtech.tiered.tooltip.2"));
            tooltip.add(TextFormatting.GRAY + I18n.format("tile.gregtech.tiered.tooltip.3"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.shift"));
        }
    }
}
