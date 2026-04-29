package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

public interface IMEPatternProviderPart {

    int getTier();

    @Nullable
    IItemHandler getPatternSlot();

    String getShowName();

    @Nullable
    MetaTileEntity getController();
}
