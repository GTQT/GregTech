package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class ThreadMultiblockInformation extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.thread_avaliable"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.tooltip.thread_enabled"));
        if (TooltipHelper.isShiftDown()) {
            tooltip.add(I18n.format("tile.gregtech.thread.tooltip.1"));
            tooltip.add(I18n.format("tile.gregtech.thread.tooltip.2"));
            tooltip.add(I18n.format("tile.gregtech.thread.tooltip.3"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.shift"));
        }
    }
}
