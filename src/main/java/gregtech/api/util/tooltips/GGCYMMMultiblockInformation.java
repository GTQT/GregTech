package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class GGCYMMMultiblockInformation extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.parallel_avaliable"));
        tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.tooltip.parallel_enabled"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tile.gregtech.parallel.tooltip.1"));
    }
}
