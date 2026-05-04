package gregtech.common.mui.multiblock.godforge.sync;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

public enum Panels {

    MAIN,
    MAIN_SMELTING,
    MAIN_MOLTEN,
    MAIN_PLASMA,
    MAIN_EXOTIC,

    GENERAL_INFO,
    VOLTAGE_CONFIG,

    MILESTONE,
    INDIVIDUAL_MILESTONE,
    FUEL_CONFIG,
    BATTERY_CONFIG,
    STAR_COSMETICS,
    CUSTOM_STAR_COLOR,
    STAR_COLOR_IMPORT,
    UPGRADE_TREE,
    INDIVIDUAL_UPGRADE,
    MANUAL_INSERTION,
    STATISTICS,
    SPECIAL_THANKS,

    EXOTIC_INPUTS_LIST,
    EXOTIC_POSSIBLE_INPUTS_LIST,
    PLASMA_DEBUG,

    ;

    public static final Panels[] VALUES = values();

    private final String panelId = "fog.panel." + name().toLowerCase();
    private final BiFunction<SyncHypervisor, Modules<?>, ModularPanel> panelSupplier;

    Panels() {
        this.panelSupplier = null;
    }

    Panels(Function<SyncHypervisor, ModularPanel> panelSupplier) {
        this.panelSupplier = (hypervisor, module) -> panelSupplier.apply(hypervisor);
    }

    Panels(BiFunction<SyncHypervisor, Modules<?>, ModularPanel> modulePanelSupplier) {
        this.panelSupplier = modulePanelSupplier;
    }

    public String getPanelId(Modules<?> module, SyncHypervisor hypervisor) {
        if (module != hypervisor.getMainModule()) {
            return module.getModuleId() + "/" + panelId;
        }
        return panelId;
    }

    public IPanelHandler getFrom(Panels fromPanel, SyncHypervisor hypervisor) {
        return getFrom(hypervisor.getMainModule(), fromPanel, hypervisor);
    }

    public IPanelHandler getFrom(Modules<?> fromModule, Panels fromPanel, SyncHypervisor hypervisor) {
        if (this == hypervisor.getMainPanel()) {
            throw new IllegalStateException("Cannot get panel handler of main panel!");
        }

        PanelSyncManager syncManager = hypervisor.getSyncManager(fromModule, fromPanel);

        return syncManager.syncedPanel(getPanelId(fromModule, hypervisor), true, (p_syncManager, syncHandler) -> {
            ModularPanel panel = createPanel(fromModule, hypervisor);
            hypervisor.setModularPanel(fromModule, this, panel);
            hypervisor.setSyncManager(fromModule, this, p_syncManager);

            return panelSupplier.apply(hypervisor, fromModule);
        });
    }

    private ModularPanel createPanel(Modules<?> fromModule, SyncHypervisor hypervisor) {
        return new ModularPanel(getPanelId(fromModule, hypervisor)) {

            @Override
            public void dispose() {
                hypervisor.onPanelDispose(fromModule, Panels.this);
                super.dispose();
            }
        };
    }
}
