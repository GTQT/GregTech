package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.data.Formatters;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.UpgradeStorage;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncActions;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class IndividualUpgradePanel {

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.INDIVIDUAL_UPGRADE);

        registerSyncValues(hypervisor);

        EnumSyncValue<ForgeOfGodsUpgrade> upgradeSyncer = SyncValues.UPGRADE_CLICKED
            .lookupFrom(Panels.UPGRADE_TREE, hypervisor);

        IPanelHandler manualInsertionPanel = Panels.MANUAL_INSERTION.getFrom(Panels.UPGRADE_TREE, hypervisor);

        DynamicSyncHandler handler = new DynamicSyncHandler().widgetProvider(($, $$) -> {
            ForgeOfGodsUpgrade upgrade = upgradeSyncer.getValue();
            panel.size(upgrade.getPanelSize());
            panel.background(upgrade.getBackground());
            panel.disableHoverBackground();
            panel.scheduleResize();
            return buildPanel(upgrade.getPanelSize(), upgrade, hypervisor, manualInsertionPanel);
        });

        upgradeSyncer.setChangeListener(() -> {
            if (handler.isValid()) {
                handler.notifyUpdate($ -> {});
            }
        });

        panel.child(
            new DynamicSyncedWidget<>().coverChildren()
                .syncHandler(handler));

        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        SyncValues.AVAILABLE_GRAVITON_SHARDS.registerFor(Panels.INDIVIDUAL_UPGRADE, hypervisor);

        SyncActions.COMPLETE_UPGRADE.registerFor(Panels.INDIVIDUAL_UPGRADE, hypervisor);
        SyncActions.RESPEC_UPGRADE.registerFor(Panels.INDIVIDUAL_UPGRADE, hypervisor);
    }

    private static ParentWidget<?> buildPanel(int size, ForgeOfGodsUpgrade upgrade, SyncHypervisor hypervisor,
        IPanelHandler manualInsertionPanel) {
        ParentWidget<?> parent = new ParentWidget<>().size(size)
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        parent.child(
            upgrade.getSymbol()
                .asWidget()
                .size((int) (size / 2.0f * upgrade.getSymbolWidthRatio()), size / 2)
                .align(Alignment.CENTER));

        parent.child(
            upgrade.getOverlay()
                .asWidget()
                .size(size / 2)
                .align(Alignment.CENTER));

        Flow column = new Column().size(size - 16, size - 26)
            .marginTop(15)
            .alignX(0.5f);

        column.child(
            IKey.lang(upgrade.getNameKey())
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .widthRel(1));

        column.child(
            IKey.lang(upgrade.getBodyKey())
                .style(TextFormatting.WHITE)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .widthRel(1)
                .height(upgrade.getBodySize())
                .marginTop(7));

        column.child(
            IKey.lang(upgrade.getLoreKey())
                .style(TextFormatting.ITALIC)
                .color(0xFFBBBDBD)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .widthRel(1)
                .height(upgrade.getLoreSize())
                .marginTop(5));

        ParentWidget<?> bottomRow = new ParentWidget<>().widthRel(1)
            .height(15)
            .align(Alignment.BottomCenter);

        bottomRow.child(IKey.dynamic(() -> {
            String cost = " " + TextFormatting.BLUE + upgrade.getShardCost();
            return translateToLocal("gt.blockmachines.multimachine.FOG.shardcost") + cost;
        })
            .alignment(Alignment.CENTER)
            .scale(0.7f)
            .color(0xFF9C9C9C)
            .asWidget()
            .size(70, 15)
            .alignX(0));

        bottomRow.child(IKey.dynamic(() -> {
            int shardsAvailable = hypervisor.getData()
                .getGravitonShardsAvailable();
            Formatters formatter = hypervisor.getData()
                .getFormatter();

            TextFormatting enoughShards = TextFormatting.RED;
            if (shardsAvailable >= upgrade.getShardCost()) {
                enoughShards = TextFormatting.GREEN;
            }

            String available = " " + enoughShards + formatter.format(shardsAvailable);
            return translateToLocal("gt.blockmachines.multimachine.FOG.availableshards") + available;
        })
            .alignment(Alignment.CENTER)
            .scale(0.7f)
            .color(0xFF9C9C9C)
            .asWidget()
            .size(70, 15)
            .alignX(1));

        Flow buttonRow = new Row().size(78, 15)
            .alignX(0.5f);

        buttonRow.child(
            new ButtonWidget<>().size(40, 15)
                .background(new DynamicDrawable(() -> {
                    ForgeOfGodsData data = hypervisor.getData();
                    if (data.isUpgradeActive(upgrade)) {
                        return GTGuiTextures.BUTTON_OUTLINE_HOLLOW_PRESSED;
                    }
                    return GTGuiTextures.BUTTON_OUTLINE_HOLLOW;
                }))
                .overlay(new DynamicDrawable(() -> {
                    ForgeOfGodsData data = hypervisor.getData();
                    if (data.isUpgradeActive(upgrade)) {
                        return IKey.lang("fog.upgrade.respec")
                            .alignment(Alignment.CENTER)
                            .scale(0.7f);
                    }
                    return IKey.lang("fog.upgrade.confirm")
                        .alignment(Alignment.CENTER)
                        .scale(0.7f);
                }))
                .onMousePressed(d -> {
                    ForgeOfGodsData data = hypervisor.getData();
                    if (data.isUpgradeActive(upgrade)) {
                        SyncActions.RESPEC_UPGRADE.callFrom(Panels.INDIVIDUAL_UPGRADE, hypervisor, upgrade);
                    } else {
                        SyncActions.COMPLETE_UPGRADE.callFrom(Panels.INDIVIDUAL_UPGRADE, hypervisor, upgrade);
                    }
                    return true;
                })
                .tooltipDynamic(t -> {
                    ForgeOfGodsData data = hypervisor.getData();
                    if (data.isUpgradeActive(upgrade)) {
                        t.addLine(translateToLocal("fog.upgrade.respec"));
                    } else {
                        t.addLine(translateToLocal("fog.upgrade.confirm"));
                    }
                })
                .tooltipAutoUpdate(true)
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound())
                .alignX(0.5f));

        buttonRow.child(
            new ButtonWidget<>().size(15)
                .background(new DynamicDrawable(() -> {
                    UpgradeStorage storage = hypervisor.getData()
                        .getUpgrades();
                    if (storage.isCostPaid(upgrade)) {
                        return GTGuiTextures.BUTTON_BOXED_CHECKMARK_18x18;
                    }
                    return GTGuiTextures.BUTTON_BOXED_EXCLAMATION_POINT_18x18;
                }).asIcon()
                    .size(15))
                .onMousePressed(d -> {
                    ModularPanel upgradeTreePanel = hypervisor.getModularPanel(Panels.UPGRADE_TREE);
                    if (upgradeTreePanel != null) {
                        upgradeTreePanel.closeIfOpen();
                    }
                    parent.getPanel()
                        .closeIfOpen();
                    if (!manualInsertionPanel.isPanelOpen()) {
                        manualInsertionPanel.openPanel();
                    }
                    return true;
                })
                .tooltipDynamic(t -> {
                    UpgradeStorage storage = hypervisor.getData()
                        .getUpgrades();
                    if (storage.isCostPaid(upgrade)) {
                        t.addLine(translateToLocal("fog.button.materialrequirementsmet.tooltip"));
                    } else {
                        t.addLine(translateToLocal("fog.button.materialrequirements.tooltip"));
                    }
                    t.addLine(
                        TextFormatting.GRAY
                            + translateToLocal("fog.button.materialrequirements.tooltip.clickhere"));
                })
                .tooltipAutoUpdate(true)
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound())
                .setEnabledIf($ -> upgrade.hasExtraCost())
                .alignX(0));

        bottomRow.child(buttonRow);
        column.child(bottomRow);
        parent.child(column);
        return parent;
    }
}
