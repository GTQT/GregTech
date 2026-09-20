package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class OpticalComponent extends AbstractTooltipComponent {

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.optical_available"));
        tooltip.add(TextFormatting.AQUA + I18n.format("gregtech.tooltip.optical_hatch.tooltips.1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("gregtech.tooltip.optical_hatch.tooltips.2"));
    }
}
