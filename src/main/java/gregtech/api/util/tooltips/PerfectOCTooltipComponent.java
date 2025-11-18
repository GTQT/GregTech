package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class PerfectOCTooltipComponent extends AbstractTooltipComponent {

    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        tooltip.add(TooltipHelper.RAINBOW_SLOW + I18n.format("gregtech.tooltip.perfect_oc_available"));
        tooltip.add(I18n.format("gregtech.machine.perfect_oc"));
    }
}
