package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BigIntSyncValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.data.Fuels;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Modules;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class PlasmaDebugPanel {

    private static final int SIZE = 200;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Modules.PLASMA, Panels.PLASMA_DEBUG);

        registerSyncValues(hypervisor);

        panel.size(SIZE)
            .background(GTGuiTextures.BACKGROUND_GLOW_WHITE)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        Flow column = new Column().coverChildren()
            .marginTop(12)
            .alignX(0.5f);

        column.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.plasmadebug")
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .marginBottom(16));

        if (hasForgeData(hypervisor)) {
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.totalpowerconsumed",
                SyncValues.TOTAL_POWER_CONSUMED.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.totalrecipesprocessed",
                SyncValues.TOTAL_RECIPES_PROCESSED.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.totalfuelconsumed",
                SyncValues.TOTAL_FUEL_CONSUMED.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));

            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.availablegravitons",
                SyncValues.AVAILABLE_GRAVITON_SHARDS.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));

            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.inversionactive",
                SyncValues.INVERSION.lookupFrom(Modules.CORE, Panels.PLASMA_DEBUG, hypervisor)));

            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.fuelconsumptionfactor",
                SyncValues.FUEL_FACTOR.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));

            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.selectedfuel",
                SyncValues.SELECTED_FUEL.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));

            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.maxbatterycharge",
                SyncValues.MAX_BATTERY_CHARGE.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
        }

        column.child(createDebugRow("gt.blockmachines.multimachine.FOG.maxparallel",
            SyncValues.MODULE_CALCULATED_MAX_PARALLEL.lookupFrom(Modules.PLASMA, Panels.PLASMA_DEBUG, hypervisor)));

        column.child(createDebugRow("gt.blockmachines.multimachine.FOG.processingvoltage",
            SyncValues.MODULE_PROCESSING_VOLTAGE.lookupFrom(Modules.PLASMA, Panels.PLASMA_DEBUG, hypervisor)));

        column.child(createDebugRow("gt.blockmachines.multimachine.FOG.alwaysmaxparallel",
            SyncValues.MODULE_ALWAYS_MAX_PARALLEL.lookupFrom(Modules.PLASMA, Panels.PLASMA_DEBUG, hypervisor)));

        if (hasForgeData(hypervisor)) {
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.milestonechargelevel",
                SyncValues.MILESTONE_CHARGE_LEVEL.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.milestoneconversionlevel",
                SyncValues.MILESTONE_CONVERSION_LEVEL.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.milestonecatalystlevel",
                SyncValues.MILESTONE_CATALYST_LEVEL.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
            column.child(createDebugRow("gt.blockmachines.multimachine.FOG.milestonecompositionlevel",
                SyncValues.MILESTONE_COMPOSITION_LEVEL.lookupFrom(Panels.PLASMA_DEBUG, hypervisor)));
        }

        panel.child(column);
        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        if (hasForgeData(hypervisor)) {
            SyncValues.TOTAL_POWER_CONSUMED.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.TOTAL_RECIPES_PROCESSED.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.TOTAL_FUEL_CONSUMED.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.AVAILABLE_GRAVITON_SHARDS.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.INVERSION.registerFor(Modules.CORE, Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.FUEL_FACTOR.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.SELECTED_FUEL.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.MAX_BATTERY_CHARGE.registerFor(Panels.PLASMA_DEBUG, hypervisor);
        }

        SyncValues.MODULE_CALCULATED_MAX_PARALLEL.registerFor(Modules.PLASMA, Panels.PLASMA_DEBUG, hypervisor);
        SyncValues.MODULE_PROCESSING_VOLTAGE.registerFor(Modules.PLASMA, Panels.PLASMA_DEBUG, hypervisor);
        SyncValues.MODULE_ALWAYS_MAX_PARALLEL.registerFor(Modules.PLASMA, Panels.PLASMA_DEBUG, hypervisor);

        if (hasForgeData(hypervisor)) {
            SyncValues.MILESTONE_CHARGE_LEVEL.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.MILESTONE_CONVERSION_LEVEL.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.MILESTONE_CATALYST_LEVEL.registerFor(Panels.PLASMA_DEBUG, hypervisor);
            SyncValues.MILESTONE_COMPOSITION_LEVEL.registerFor(Panels.PLASMA_DEBUG, hypervisor);
        }
    }

    private static boolean hasForgeData(SyncHypervisor hypervisor) {
        return hypervisor.getData() != null && hypervisor.getSyncManager(Modules.CORE, Panels.PLASMA_DEBUG) != null;
    }

    private static Flow createDebugRow(String labelKey, IntSyncValue syncer) {
        Flow row = new Row().size(180, 18)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            IKey.lang(labelKey)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(100, 18));

        row.child(
            new TextFieldWidget()
                .setNumbers(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .setTextAlignment(Alignment.CENTER)
                .value(syncer)
                .size(80, 18));

        return row;
    }

    private static Flow createDebugRow(String labelKey, LongSyncValue syncer) {
        Flow row = new Row().size(180, 18)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            IKey.lang(labelKey)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(100, 18));

        row.child(
            new TextFieldWidget()
                .setNumbersLong(raw -> raw)
                .setTextAlignment(Alignment.CENTER)
                .value(syncer)
                .size(80, 18));

        return row;
    }

    private static Flow createDebugRow(String labelKey, BigIntSyncValue syncer) {
        Flow row = new Row().size(180, 18)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            IKey.lang(labelKey)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(100, 18));

        row.child(
            new TextFieldWidget()
                .setTextAlignment(Alignment.CENTER)
                .value(syncer)
                .size(80, 18));

        return row;
    }

    private static Flow createDebugRow(String labelKey, FloatSyncValue syncer) {
        Flow row = new Row().size(180, 18)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            IKey.lang(labelKey)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(100, 18));

        row.child(
            new TextFieldWidget().setNumbersDouble(raw -> raw)
                .setTextAlignment(Alignment.CENTER)
                .value(syncer)
                .size(80, 18));

        return row;
    }

    private static Flow createDebugRow(String labelKey, BooleanSyncValue syncer) {
        Flow row = new Row().size(180, 18)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            IKey.lang(labelKey)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(100, 18));

        row.child(
            new ButtonWidget<>().size(80, 18)
                .background(GTGuiTextures.BUTTON_OUTLINE_HOLLOW)
                .overlay(IKey.dynamic(() -> syncer.getBoolValue() ? "true" : "false"))
                .onMousePressed(d -> {
                    syncer.setValue(!syncer.getBoolValue());
                    return true;
                })
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound()));

        return row;
    }

    private static Flow createDebugRow(String labelKey, EnumSyncValue<Fuels> syncer) {
        Flow row = new Row().size(180, 18)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            IKey.lang(labelKey)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(100, 18));

        row.child(
            new ButtonWidget<>().size(80, 18)
                .background(GTGuiTextures.BUTTON_OUTLINE_HOLLOW)
                .overlay(IKey.dynamic(() -> syncer.getValue().name()))
                .onMousePressed(d -> {
                    Fuels[] values = Fuels.VALUES;
                    int next = (syncer.getValue().ordinal() + 1) % values.length;
                    syncer.setValue(values[next]);
                    return true;
                })
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound()));

        return row;
    }
}
