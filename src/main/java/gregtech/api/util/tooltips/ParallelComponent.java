package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class ParallelComponent extends AbstractTooltipComponent {
    private final int parallel;
    public ParallelComponent(int parallel) {
        this.parallel = parallel;
    }
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.universal.tooltip.parallel",parallel));
    }
}
