package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

public interface IMEPatternProviderPart {

    int getTier();

    @Nullable
    IItemHandler getPatternSlot();

    String getShowName();

    @Nullable
    MultiblockControllerBase getController();
}
