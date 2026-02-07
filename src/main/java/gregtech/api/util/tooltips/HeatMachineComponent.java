package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class HeatMachineComponent extends AbstractTooltipComponent {
    private final int parallel;

    public HeatMachineComponent(int parallel) {
        this.parallel = parallel;
    }
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.heat_mte_available"));
        if(parallel>1)tooltip.add(I18n.format("gregtech.universal.tooltip.parallel", parallel));
        tooltip.add(I18n.format("gregtech.multiblock.heat_multiblock.tooltip.1"));
        tooltip.add(I18n.format("gregtech.multiblock.heat_multiblock.tooltip.2"));
        tooltip.add(TooltipHelper.BLINKING_ORANGE + I18n.format("gregtech.multiblock.require_heat_parts"));
    }
}
