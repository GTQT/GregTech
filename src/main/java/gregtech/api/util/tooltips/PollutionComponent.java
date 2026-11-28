package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class PollutionComponent extends AbstractTooltipComponent {

    private final double perSecond;
    private final double fluxPerSecond;

    /**
     * @param pollutionPerSecond 机器每秒产生的污染点，唯一单位
     */
    public PollutionComponent(double pollutionPerSecond) {
        super(true);
        this.perSecond = pollutionPerSecond;
        this.fluxPerSecond = perSecond / 400.0D;
    }

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.pollution_mte_available"));
        tooltip.add(I18n.format("gregtech.multiblock.pollution_multiblock.tooltip",
                format(perSecond), format(fluxPerSecond)));
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
