package gregtech.common.metatileentities.multi.electric.godforge;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.common.mui.multiblock.godforge.MTEForgeOfGodsGui;

import org.jetbrains.annotations.NotNull;

/**
 * Custom UI factory for the Forge of Gods that delegates entirely to {@link MTEForgeOfGodsGui}.
 * Bypasses the standard multiblock display panel in favor of the godforge's full-screen terminal UI.
 */
public class GodforgeUIFactory extends MultiblockUIFactory {

    private final MTEForgeOfGodsGui gui;

    public GodforgeUIFactory(MetaTileEntityForgeOfGods mte) {
        super(mte);
        this.gui = new MTEForgeOfGodsGui(mte);
    }

    @Override
    public @NotNull ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager) {
        return gui.buildUI(guiData, panelSyncManager, null);
    }
}
