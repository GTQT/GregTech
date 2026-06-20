package gregicality.multiblocks.api.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.tooltips.AbstractTooltipComponent;
import gregtech.client.utils.TooltipHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class ThreadMultiblockInformation extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gcym.tooltip.thread_avaliable"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gcym.tooltip.thread_enabled"));
        if (TooltipHelper.isShiftDown()) {
            tooltip.add(I18n.format("tile.gcym.tooltip.8"));
            tooltip.add(I18n.format("tile.gcym.tooltip.9"));
            tooltip.add(I18n.format("tile.gcym.tooltip.10"));
        } else {
            tooltip.add(I18n.format("gcym.tooltip.shift"));
        }
    }
}
