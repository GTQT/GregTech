package gregicality.multiblocks.api.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.tooltips.AbstractTooltipComponent;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class GGCYMMMultiblockInformation extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gcym.tooltip.parallel_avaliable"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gcym.tooltip.parallel_enabled"));
        /*
        if (TooltipHelper.isCtrlDown()) {
            tooltip.add(I18n.format("tile.gcym.tooltip.1"));
            tooltip.add(I18n.format("tile.gcym.tooltip.2"));
            tooltip.add(I18n.format("tile.gcym.tooltip.3"));
            tooltip.add(I18n.format("tile.gcym.tooltip.4"));
            tooltip.add(I18n.format("tile.gcym.tooltip.5"));
            tooltip.add(I18n.format("tile.gcym.tooltip.6"));
            tooltip.add(I18n.format("tile.gcym.tooltip.7"));
        } else {
            tooltip.add(I18n.format("gcym.tooltip.ctrl"));
        }
         */
    }
}
