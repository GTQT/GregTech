package gregtech.common.mui.multiblock.godforge;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public abstract class GodforgeBaseGui<T extends MultiblockControllerBase> {

    // Terminal display area dimensions, matching GT5 TTMultiblockBase terminal layout
    private static final int PANEL_BORDER = 4;
    private static final int TERMINAL_ROW_WIDTH = 190;
    private static final int TERMINAL_ROW_HEIGHT = 94;
    // Inner widget width/height inside the terminal ParentWidget (padding 4/4/4/0)
    private static final int TERMINAL_INNER_WIDTH = TERMINAL_ROW_WIDTH - 4;
    private static final int TERMINAL_INNER_HEIGHT = TERMINAL_ROW_HEIGHT - 8;
    private static final int INVENTORY_ROW_HEIGHT = 76;

    protected final T multiblock;
    protected final SyncHypervisor hypervisor;

    public GodforgeBaseGui(T multiblock, SyncHypervisor hypervisor) {
        this.multiblock = multiblock;
        this.hypervisor = hypervisor;
    }

    /**
     * Main entry point for building the GUI.
     * Mirrors the GT5 TTMultiblockBaseGui layout hierarchy:
     *   Column (padding=4)
     *     └─ terminalRow (Row, 190×94)
     *          └─ terminalParentWidget (ParentWidget, DISPLAY bg, padding 4/4/4/0)
     *               ├─ textWidget (ListWidget)
     *               ├─ rightCornerColumn (absolute)
     *               └─ leftCornerColumn (absolute)
     *     └─ muffleButton (ToggleButton, absolute top/right)
     *     └─ panelGap (Row, height=20)
     *     └─ inventoryRow (Row, height=76)
     *          ├─ playerInventory (SlotGroupWidget)
     *          └─ buttonColumn (Column, width=18)
     */
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = getBasePanel(guiData, syncManager, settings);
        hypervisor.setModularPanel(hypervisor.getMainPanel(), panel);
        hypervisor.setSyncManager(hypervisor.getMainPanel(), syncManager);
        registerSyncValues(syncManager);

        panel.child(
            new Column().sizeRel(1)
                .padding(PANEL_BORDER)
                .child(createTerminalRow(panel, syncManager))
                .child(createMuffleButton())
                .child(createPanelGap(panel, syncManager))
                .child(createInventoryRow(panel, syncManager)));

        return panel;
    }

    protected ModularPanel getBasePanel(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        int panelHeight = PANEL_BORDER * 2 + TERMINAL_ROW_HEIGHT + getTextBoxToInventoryGap() + INVENTORY_ROW_HEIGHT;
        return ModularPanel.defaultPanel("godforge_gui")
            .background(GTGuiTextures.BACKGROUND)
            .size(198, panelHeight);
    }

    protected void registerSyncValues(PanelSyncManager syncManager) {
        SyncValues.STRUCTURE_UPDATE.registerFor(hypervisor.getMainModule(), hypervisor.getMainPanel(), hypervisor);
    }

    // --- Terminal section ---

    /** Wraps the terminal ParentWidget in a Row, matching GT5's createTerminalRow(). */
    protected Flow createTerminalRow(ModularPanel panel, PanelSyncManager syncManager) {
        return new Row().size(TERMINAL_ROW_WIDTH, TERMINAL_ROW_HEIGHT)
            .child(createTerminalParentWidget(panel, syncManager));
    }

    /**
     * The terminal display area: dark background widget that contains the text list,
     * right-corner column, and left-corner column.
     * Mirrors GT5's ParentWidget with BACKGROUND_TERMINAL theme (display.png, adaptable(2)).
     */
    protected ParentWidget<?> createTerminalParentWidget(ModularPanel panel, PanelSyncManager syncManager) {
        return new ParentWidget<>()
            .size(TERMINAL_ROW_WIDTH, TERMINAL_ROW_HEIGHT)
            .paddingTop(4)
            .paddingBottom(4)
            .paddingLeft(4)
            .paddingRight(0)
            .background(GTGuiTextures.DISPLAY)
            .child(
                createTerminalTextWidget(syncManager, panel)
                    .size(TERMINAL_INNER_WIDTH, TERMINAL_INNER_HEIGHT))
            .child(createTerminalRightCornerColumn(panel, syncManager))
            .child(createTerminalLeftCornerColumn(panel, syncManager));
    }

    /** Text content displayed inside the terminal display area. */
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return new ListWidget<>().widthRel(1)
            .align(Alignment.TopCenter);
    }

    /** Absolute-positioned column in the bottom-right of the terminal display area. */
    protected Flow createTerminalRightCornerColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return new Column().coverChildren()
            .rightRel(0, 6, 0)
            .bottomRel(0, 6, 0);
    }

    /** Absolute-positioned column in the bottom-left of the terminal display area. */
    protected Flow createTerminalLeftCornerColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return new Column().coverChildren()
            .leftRel(0, 6, 0)
            .bottomRel(0, 6, 0);
    }

    // --- Muffle button (sits in the column but is absolute-positioned like GT5) ---

    protected ToggleButton createMuffleButton() {
        return new ToggleButton().size(7)
            .value(new BoolValue.Dynamic(multiblock::isMuffled, muffled -> {
                if (muffled != multiblock.isMuffled()) {
                    multiblock.toggleMuffled();
                }
            }))
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .background(GTGuiTextures.GODFORGE_SOUND_ON)
            .selectedBackground(GTGuiTextures.GODFORGE_SOUND_OFF)
            .top(8)
            .right(8);
    }

    // --- Panel gap row between terminal and inventory ---

    /**
     * Row between the terminal area and the inventory, containing mode-specific buttons.
     * Height matches getTextBoxToInventoryGap(), split into left and right sub-rows.
     */
    protected Flow createPanelGap(ModularPanel panel, PanelSyncManager syncManager) {
        return new Row().widthRel(1)
            .paddingRight(2)
            .paddingLeft(4)
            .height(getTextBoxToInventoryGap())
            .child(createLeftPanelGapRow(panel, syncManager))
            .child(createRightPanelGapRow(panel, syncManager));
    }

    protected Flow createLeftPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return new Row().coverChildrenWidth()
            .heightRel(1)
            .childPadding(2)
            .paddingLeft(1);
    }

    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return new Row().coverChildrenWidth()
            .heightRel(1)
            .childPadding(2)
            .align(Alignment.CenterRight);
    }

    protected int getTextBoxToInventoryGap() {
        return 20;
    }

    // --- Inventory row ---

    /**
     * Bottom row containing the player inventory slots and the button column.
     * Mirrors GT5's createInventoryRow().
     */
    protected Flow createInventoryRow(ModularPanel panel, PanelSyncManager syncManager) {
        return new Row().widthRel(1)
            .height(INVENTORY_ROW_HEIGHT)
            .child(
                SlotGroupWidget.playerInventory(false)
                    .marginLeft(4))
            .child(createButtonColumn(panel, syncManager));
    }

    /**
     * Column of function buttons to the right of the player inventory.
     * Subclasses provide machine-specific buttons.
     */
    protected abstract Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager);

    // --- Common reusable button factories ---

    protected ToggleButton createPowerSwitchButton() {
        return new ToggleButton().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .selectedBackground(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32);
    }

    protected IWidget createStructureUpdateButton(PanelSyncManager syncManager) {
        BooleanSyncValue refreshSyncer = SyncValues.STRUCTURE_UPDATE
            .lookupFrom(hypervisor.getMainModule(), hypervisor.getMainPanel(), hypervisor);
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
