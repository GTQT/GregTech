package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class SpecialLogicComponent extends AbstractTooltipComponent {

    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.special_available"));
    }
}
