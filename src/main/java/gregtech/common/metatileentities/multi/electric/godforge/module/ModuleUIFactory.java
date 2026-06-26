package gregtech.common.metatileentities.multi.electric.godforge.module;

import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.common.mui.multiblock.godforge.MTEBaseModuleGui;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

/**
 * Custom UI factory for Godforge modules that delegates to their respective {@link MTEBaseModuleGui} implementations.
 * Bypasses the standard multiblock display panel in favor of the godforge module terminal UI with connection status.
 */
public class ModuleUIFactory extends MultiblockUIFactory {

    private final MTEBaseModuleGui<?> gui;

    public ModuleUIFactory(MTEBaseModule module, MTEBaseModuleGui<?> gui) {
        super(module);
        this.gui = gui;
    }

    @Override
    public @NotNull ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager) {
        return gui.buildUI(guiData, panelSyncManager, null);
    }
}
