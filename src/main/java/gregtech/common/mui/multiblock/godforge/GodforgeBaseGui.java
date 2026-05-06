package gregtech.common.mui.multiblock.godforge;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public abstract class GodforgeBaseGui<T extends MultiblockControllerBase> {

    protected final T multiblock;
    protected final SyncHypervisor hypervisor;

    public GodforgeBaseGui(T multiblock, SyncHypervisor hypervisor) {
        this.multiblock = multiblock;
        this.hypervisor = hypervisor;
    }

    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = getBasePanel(guiData, syncManager, settings);
        registerSyncValues(syncManager);

        panel.child(createButtonColumn(panel, syncManager));
        panel.child(createPanelGap(panel, syncManager));
        panel.child(createTerminalTextWidget(syncManager, panel));
        panel.child(createTerminalRightCornerColumn(panel, syncManager));
        panel.child(createTerminalLeftCornerColumn(panel, syncManager));
        panel.child(createMuffleButton());
        panel.child(createLeftPanelGapRow(panel, syncManager));
        panel.child(createRightPanelGapRow(panel, syncManager));

        panel.bindPlayerInventory(7);
        return panel;
    }

    protected ModularPanel getBasePanel(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return ModularPanel.defaultPanel("godforge_gui")
            .background(GTGuiTextures.BACKGROUND)
            .size(198, 208);
    }

    protected void registerSyncValues(PanelSyncManager syncManager) {
        SyncValues.STRUCTURE_UPDATE.registerFor(Panels.MAIN, hypervisor);
    }

    protected abstract Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager);

    protected Flow createPanelGap(ModularPanel panel, PanelSyncManager syncManager) {
        return new Row().collapseDisabledChild()
            .widthRel(1)
            .paddingRight(6)
            .paddingLeft(5)
            .childPadding(2)
            .height(getTextBoxToInventoryGap());
    }

    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return new ListWidget<>().widthRel(1)
            .align(Alignment.TopCenter);
    }

    protected Flow createTerminalRightCornerColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return new Column().coverChildren()
            .rightRel(0, 6, 0)
            .bottomRel(0, 6, 0);
    }

    protected Flow createTerminalLeftCornerColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return new Column().coverChildren()
            .leftRel(0, 6, 0)
            .bottomRel(0, 6, 0);
    }

    protected Flow createLeftPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return new Row().collapseDisabledChild()
            .widthRel(1)
            .paddingRight(6)
            .paddingLeft(5)
            .childPadding(2)
            .height(getTextBoxToInventoryGap());
    }

    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return new Row().collapseDisabledChild()
            .widthRel(1)
            .paddingRight(6)
            .paddingLeft(5)
            .childPadding(2)
            .height(getTextBoxToInventoryGap());
    }

    protected int getTextBoxToInventoryGap() {
        return 20;
    }

    protected ToggleButton createMuffleButton() {
        return new ToggleButton().size(7)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .overlay(true, GTGuiTextures.GODFORGE_SOUND_OFF)
            .overlay(false, GTGuiTextures.GODFORGE_SOUND_ON)
            .top(8)
            .right(8);
    }

    protected ToggleButton createPowerSwitchButton() {
        return new ToggleButton().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .selectedBackground(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32);
    }

    protected IWidget createStructureUpdateButton(PanelSyncManager syncManager) {
        BooleanSyncValue refreshSyncer = SyncValues.STRUCTURE_UPDATE.lookupFrom(Panels.MAIN, hypervisor);
        return new ButtonWidget<>().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(GTGuiTextures.TT_OVERLAY_BUTTON_STRUCTURE_CHECK)
            .onMousePressed(d -> {
                refreshSyncer.setBoolValue(!refreshSyncer.getBoolValue());
                return true;
            })
            .clickSound(ForgeOfGodsGuiUtil.getButtonSound());
    }

    protected IWidget createVoidExcessButton(PanelSyncManager syncManager) {
        return new ButtonWidget<>().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(GTGuiTextures.TT_OVERLAY_BUTTON_VOIDING_OFF)
            .clickSound(ForgeOfGodsGuiUtil.getButtonSound());
    }

    protected IWidget createInputSeparationButton(PanelSyncManager syncManager) {
        return new ToggleButton().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .selectedBackground(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(GTGuiTextures.TT_OVERLAY_BUTTON_INPUT_SEPARATION);
    }

    protected IWidget createBatchModeButton(PanelSyncManager syncManager) {
        return new ToggleButton().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .selectedBackground(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(GTGuiTextures.TT_OVERLAY_BUTTON_BATCH_MODE);
    }

    protected IWidget createLockToSingleRecipeButton(PanelSyncManager syncManager) {
        return new ToggleButton().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .selectedBackground(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(GTGuiTextures.TT_OVERLAY_BUTTON_RECIPE_LOCKED);
    }
}
