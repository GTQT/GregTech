package gregtech.common.mui.multiblock.godforge;

import gregtech.common.metatileentities.multi.electric.godforge.module.MTEMoltenModule;
import gregtech.common.mui.multiblock.godforge.sync.Modules;

public class MTEMoltenModuleGui extends MTEBaseModuleGui<MTEMoltenModule> {

    public MTEMoltenModuleGui(MTEMoltenModule multiblock) {
        super(multiblock);
    }

    @Override
    public Modules<MTEMoltenModule> getModuleType() {
        return Modules.MOLTEN;
    }
}
