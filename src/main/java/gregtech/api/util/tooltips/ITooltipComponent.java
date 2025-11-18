package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import java.util.List;

public interface ITooltipComponent {
    void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip);
}
