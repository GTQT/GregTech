package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class InformationHandler {

    public void defaultInformation(MultiblockWithDisplayBase metaTileEntity,
                                   List<String> tooltip) {
        TooltipBuilder.createDefault().build(metaTileEntity, tooltip);
    }

    public void emptyInformation(MultiblockWithDisplayBase metaTileEntity,
                                 List<String> tooltip) {
        TooltipBuilder.create().build(metaTileEntity, tooltip);
    }

    public void topTooltips(String key, List<String> tooltip) {
        tooltip.add(TooltipHelper.RAINBOW_SLOW + I18n.format(key));
    }
}
