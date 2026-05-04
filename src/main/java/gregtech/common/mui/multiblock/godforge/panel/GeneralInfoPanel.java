package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.data.Formatters;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Modules;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class GeneralInfoPanel {

    private static final int SIZE = 200;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.GENERAL_INFO);

        registerSyncValues(hypervisor);

        panel.size(SIZE)
            .background(GTGuiTextures.BACKGROUND_GLOW_WHITE)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        BooleanSyncValue inversionSyncer = SyncValues.INVERSION
            .lookupFrom(Modules.CORE, Panels.GENERAL_INFO, hypervisor);

        DynamicSyncHandler handler = new DynamicSyncHandler().widgetProvider(($, $$) -> {
            ForgeOfGodsData data = hypervisor.getData();
            boolean inversion = inversionSyncer.getBoolValue();

            Flow column = new Column().coverChildren()
                .marginTop(12)
                .alignX(0.5f);

            column.child(
                IKey.lang("gt.blockmachines.multimachine.FOG.generalinfo")
                    .style(TextFormatting.GOLD)
                    .alignment(Alignment.CENTER)
                    .asWidget()
                    .alignX(0.5f)
                    .marginBottom(16));

            column.child(
                IKey.dynamic(() -> {
                    Formatters formatter = data.getFormatter();
                    return translateToLocal("gt.blockmachines.multimachine.FOG.totalpowerconsumed")
                        + ": "
                        + TextFormatting.GRAY
                        + formatter.format(data.getTotalPowerConsumed());
                })
                    .alignment(Alignment.CENTER)
                    .scale(0.7f)
                    .asWidget()
                    .width(180)
                    .alignX(0.5f));

            column.child(
                IKey.dynamic(() -> {
                    Formatters formatter = data.getFormatter();
                    return translateToLocal("gt.blockmachines.multimachine.FOG.totalrecipesprocessed")
                        + ": "
                        + TextFormatting.GRAY
                        + formatter.format(data.getTotalRecipesProcessed());
                })
                    .alignment(Alignment.CENTER)
                    .scale(0.7f)
                    .asWidget()
                    .width(180)
                    .alignX(0.5f)
                    .marginTop(10));

            column.child(
                IKey.dynamic(() -> {
                    Formatters formatter = data.getFormatter();
                    return translateToLocal("gt.blockmachines.multimachine.FOG.totalfuelconsumed")
                        + ": "
                        + TextFormatting.GRAY
                        + formatter.format(data.getTotalFuelConsumed());
                })
                    .alignment(Alignment.CENTER)
                    .scale(0.7f)
                    .asWidget()
                    .width(180)
                    .alignX(0.5f)
                    .marginTop(10));

            column.child(
                IKey.dynamic(() -> {
                    Formatters formatter = data.getFormatter();
                    return translateToLocal("gt.blockmachines.multimachine.FOG.gravitonshardsavailable")
                        + ": "
                        + TextFormatting.GRAY
                        + formatter.format(data.getGravitonShardsAvailable());
                })
                    .alignment(Alignment.CENTER)
                    .scale(0.7f)
                    .asWidget()
                    .width(180)
                    .alignX(0.5f)
                    .marginTop(10));

            if (inversion) {
                column.child(
                    IKey.lang("gt.blockmachines.multimachine.FOG.inversionactive")
                        .style(TextFormatting.WHITE, TextFormatting.BOLD)
                        .alignment(Alignment.CENTER)
                        .scale(0.8f)
                        .asWidget()
                        .width(180)
                        .alignX(0.5f)
                        .marginTop(10));
            }

            return column;
        });

        inversionSyncer.setChangeListener(() -> {
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
        SyncValues.INVERSION.registerFor(Modules.CORE, Panels.GENERAL_INFO, hypervisor);
        SyncValues.TOTAL_POWER_CONSUMED.registerFor(Panels.GENERAL_INFO, hypervisor);
        SyncValues.TOTAL_RECIPES_PROCESSED.registerFor(Panels.GENERAL_INFO, hypervisor);
        SyncValues.TOTAL_FUEL_CONSUMED.registerFor(Panels.GENERAL_INFO, hypervisor);
        SyncValues.AVAILABLE_GRAVITON_SHARDS.registerFor(Panels.GENERAL_INFO, hypervisor);
    }
}
