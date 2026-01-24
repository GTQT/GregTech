package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class TooltipsComponent extends AbstractTooltipComponent {

    String key;

    public TooltipsComponent(String key) {
        this.key = key;
    }

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(TooltipHelper.RAINBOW_SLOW + I18n.format(key));
    }
}
