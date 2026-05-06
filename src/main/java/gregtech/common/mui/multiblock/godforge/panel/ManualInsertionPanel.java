package gregtech.common.mui.multiblock.godforge.panel;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import net.minecraftforge.items.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTWidgetThemes;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.SlotLikeButtonWidget;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncActions;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class ManualInsertionPanel {

    private static final int SIZE_W = 190;
    private static final int SIZE_H = 115;

    private static final int BUTTON_W = 180;
    private static final int BUTTON_H = 18;

    private static final String SLOT_GROUP_SYNC_NAME = "item_inv_manual_insertion";

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.MANUAL_INSERTION);

        registerSyncValues(hypervisor);

        ModularPanel mainPanel = hypervisor.getModularPanel(Panels.MAIN);
        panel.relative(mainPanel)
            .leftRelOffset(0, 4)
            .topRelOffset(0, 3)
            .size(SIZE_W, SIZE_H)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButtonStandard())
            .onCloseAction(() -> {
                IPanelHandler handler = Panels.UPGRADE_TREE.getFrom(Panels.MAIN, hypervisor);
                if (!handler.isPanelOpen()) {
                    handler.openPanel();
                }

                handler = Panels.INDIVIDUAL_UPGRADE.getFrom(Panels.UPGRADE_TREE, hypervisor);
                if (!handler.isPanelOpen()) {
                    handler.openPanel();
                }
            });

        panel.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.payUpgradeCosts")
                .style(TextFormatting.DARK_GRAY)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .marginTop(5));

        Flow mainRow = new Row().size(SIZE_W - 10, 72)
            .align(Alignment.TopLeft)
            .marginLeft(5)
            .marginTop(16);

        mainRow.child(
            new Column().size(36, 72)
                .child(createCostRow(hypervisor, 0))
                .child(createCostRow(hypervisor, 1))
                .child(createCostRow(hypervisor, 2))
                .child(createCostRow(hypervisor, 3)));
        mainRow.child(
            new Column().size(36, 72)
                .child(createCostRow(hypervisor, 4))
                .child(createCostRow(hypervisor, 5))
                .child(createCostRow(hypervisor, 6))
                .child(createCostRow(hypervisor, 7)));
        mainRow.child(
            new Column().size(36, 72)
                .child(createCostRow(hypervisor, 8))
                .child(createCostRow(hypervisor, 9))
                .child(createCostRow(hypervisor, 10))
                .child(createCostRow(hypervisor, 11)));

        mainRow.child(createInputSlots(hypervisor));

        panel.child(mainRow);

        EnumSyncValue<ForgeOfGodsUpgrade> upgradeSyncer = SyncValues.UPGRADE_CLICKED
            .lookupFrom(Panels.UPGRADE_TREE, hypervisor);

        panel.child(
            new ButtonWidget<>().overlay(
                IKey.lang("gt.blockmachines.multimachine.FOG.consumeUpgradeMats")
                    .style(TextFormatting.DARK_GRAY)
                    .alignment(Alignment.CENTER)
                    .scale(0.75f))
                .disableHoverBackground()
                .disableHoverOverlay()
                .onMousePressed(d -> {
                    SyncActions.PAY_UPGRADE_COST
                        .callFrom(Panels.MANUAL_INSERTION, hypervisor, upgradeSyncer.getValue());
                    return true;
                })
                .size(BUTTON_W, BUTTON_H)
                .align(Alignment.BottomLeft)
                .marginBottom(5)
                .marginLeft(5)
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound()));

        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        SyncActions.PAY_UPGRADE_COST.registerFor(Panels.MANUAL_INSERTION, hypervisor);

        PanelSyncManager syncManager = hypervisor.getSyncManager(Panels.MANUAL_INSERTION);
        syncManager.registerSlotGroup(SLOT_GROUP_SYNC_NAME, 4);
    }

    private static ParentWidget<?> createCostRow(SyncHypervisor hypervisor, int index) {
        EnumSyncValue<ForgeOfGodsUpgrade> upgradeSyncer = SyncValues.UPGRADE_CLICKED
            .lookupFrom(Panels.UPGRADE_TREE, hypervisor);

        return new ParentWidget<>().size(36, 18)

            .child(
                new SlotLikeButtonWidget(
                    () -> upgradeSyncer.getValue()
                        .getExtraCost()[index]).onMousePressed(d -> {
                            return true;
                        })
                            .tooltipDynamic(t -> {
                                if (hasExtraCost(upgradeSyncer, index)) {
                                    ForgeOfGodsUpgrade upgrade = upgradeSyncer.getValue();
                                    t.addFromItem(upgrade.getExtraCost()[index]);
                                }
                            })
                            .tooltipAutoUpdate(true)
                            .setEnabledIf($ -> hasExtraCost(upgradeSyncer, index))
                            .size(18)
                            .alignX(0))
            .child(IKey.dynamic(() -> {
                ForgeOfGodsUpgrade upgrade = upgradeSyncer.getValue();
                ItemStack costStack = upgrade.getExtraCost()[index];
                short amountPaid = hypervisor.getData()
                    .getUpgrades()
                    .getPaidCosts(upgrade)[index];
                if (costStack == null) return "";

                TextFormatting color = TextFormatting.YELLOW;
                if (amountPaid == 0) color = TextFormatting.RED;
                else if (amountPaid == costStack.getCount()) color = TextFormatting.GREEN;

                return color + "x" + (costStack.getCount() - amountPaid);
            })
                .alignment(Alignment.CENTER)
                .scale(0.8f)
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT)
                .size(18)
                .alignX(1)
                .setEnabledIf(
                    $ -> hasExtraCost(upgradeSyncer, index)
                        && !isExtraCostPaid(upgradeSyncer, hypervisor.getData(), index)))
            .child(
                GTGuiTextures.GREEN_CHECKMARK_11x9.asWidget()
                    .size(11, 9)
                    .alignX(1)
                    .marginRight(4)
                    .marginTop(5)
                    .setEnabledIf($ -> isExtraCostPaid(upgradeSyncer, hypervisor.getData(), index)))

            .child(
                GTGuiTextures.BUTTON_STANDARD_DISABLED.asWidget()
                    .size(18)
                    .alignX(0)
                    .setEnabledIf($ -> !hasExtraCost(upgradeSyncer, index)));
    }

    private static boolean hasExtraCost(EnumSyncValue<ForgeOfGodsUpgrade> syncer, int index) {
        return syncer.getValue()
            .getExtraCost()[index] != null;
    }

    private static boolean isExtraCostPaid(EnumSyncValue<ForgeOfGodsUpgrade> syncer, ForgeOfGodsData data, int index) {
        ForgeOfGodsUpgrade upgrade = syncer.getValue();
        ItemStack costStack = upgrade.getExtraCost()[index];
        short amountPaid = data.getUpgrades()
            .getPaidCosts(upgrade)[index];

        if (costStack == null) return false;
        return amountPaid >= costStack.getCount();
    }

    private static SlotGroupWidget createInputSlots(SyncHypervisor hypervisor) {
        ItemStackHandler handler = hypervisor.getData()
            .getUpgradeWindowHandler();
        String[] matrix = new String[4];
        String repeat = "ssss";
        Arrays.fill(matrix, repeat);
        return SlotGroupWidget.builder()
            .matrix(matrix)
            .key('s', i -> new ItemSlot().slot(new ModularSlot(handler, i).slotGroup(SLOT_GROUP_SYNC_NAME)))
            .build()
            .coverChildren()
            .align(Alignment.CenterRight);
    }
}
