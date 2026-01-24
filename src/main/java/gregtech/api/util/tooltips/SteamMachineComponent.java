package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class SteamMachineComponent extends AbstractTooltipComponent {
    private final int parallel;
    public SteamMachineComponent(int parallel) {
        this.parallel = parallel;
    }
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.steam_available"));
        tooltip.add(I18n.format("gregtech.multiblock.steam_.duration_modifier"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.parallel", parallel));
        tooltip.add(TooltipHelper.BLINKING_ORANGE + I18n.format("gregtech.multiblock.require_steam_parts"));
    }
}
