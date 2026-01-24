package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class BlastComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.heat_available"));
        tooltip.add(I18n.format("gregtech.machine.electric_blast_furnace.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.electric_blast_furnace.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.electric_blast_furnace.tooltip.3"));
    }
}
