package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class PollutionComponent extends AbstractTooltipComponent {

    double pollutionAmount;
    int ticks;

    public PollutionComponent(double pollutionAmount,int ticks) {
        super(true);
        this.pollutionAmount = pollutionAmount;
        this.ticks = ticks;
    }
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.pollution_mte_available"));
        tooltip.add(I18n.format("gregtech.multiblock.pollution_multiblock.tooltip",ticks,pollutionAmount));
    }
}
