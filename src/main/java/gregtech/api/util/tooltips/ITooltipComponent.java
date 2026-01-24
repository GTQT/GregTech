package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import java.util.List;

public interface ITooltipComponent {
    void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip);
}
