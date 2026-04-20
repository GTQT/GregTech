package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.bridge.IGTMachineInfo;
import gregtech.api.bridge.IGTPatternProviderInfo;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

public interface IMEPatternProviderPart extends IGTPatternProviderInfo {

    int getTier();

    @Nullable
    IItemHandler getPatternSlot();

    String getShowName();

    @Nullable
    MetaTileEntity getController();

    @Override
    default IGTMachineInfo getMachineInfo() {
        if (this instanceof MetaTileEntity mte) {
            return mte;
        }
        return null;
    }
}
