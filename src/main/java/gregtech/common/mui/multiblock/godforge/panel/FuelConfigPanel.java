package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.math.MathHelper;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTWidgetThemes;
import gregtech.common.metatileentities.multi.electric.godforge.data.Formatters;
import gregtech.common.metatileentities.multi.electric.godforge.data.Fuels;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;
import gregtech.common.metatileentities.multi.electric.godforge.util.GodforgeMath;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.LinkedBoolValue;
import gregtech.common.mui.multiblock.godforge.SelectButton;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class FuelConfigPanel {

    private static final int SIZE_W = 78;
    private static final int SIZE_H = 138;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.FUEL_CONFIG);
        ForgeOfGodsData data = hypervisor.getData();

        registerSyncHandlers(hypervisor);

        panel.relative(hypervisor.getModularPanel(Panels.MAIN))
            .size(SIZE_W, SIZE_H)
            .topRel(0)
            .leftRelOffset(1, -3);

        Flow column = new Column().size(SIZE_W, SIZE_H);

        column.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.fuelconsumption")
                .alignment(Alignment.CENTER)
                .asWidget()
                .width(SIZE_W - 4)
                .alignX(0.5f)
                .marginTop(5));

        column.child(
            new TextFieldWidget()
                .setNumbers(raw -> MathHelper.clamp(raw, 1, GodforgeMath.calculateMaxFuelFactor(data)))
                .setTextAlignment(Alignment.CENTER)
                .value(SyncValues.FUEL_FACTOR.create(hypervisor))
                .setTooltipOverride(true)
                .size(70, 18)
                .marginLeft(4)
                .marginTop(3));

        panel.child(
            GTGuiTextures.PICTURE_INFO.asWidget()
                .size(10)
                .pos(SIZE_W - 10 - 4, 24)
                .tooltip(t -> {
                    t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.fuelinfo.0"));
                    t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.fuelinfo.1"));
                    t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.fuelinfo.2"));
                    t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.fuelinfo.3"));
                    t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.fuelinfo.4"));
                    t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.fuelinfo.5"));
                })
                .tooltipShowUpTimer(TOOLTIP_DELAY));

        column.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.fueltype")
                .alignment(Alignment.CENTER)
                .asWidget()
                .width(SIZE_W - 4)
                .alignX(0.5f)
                .marginTop(5));

        EnumSyncValue<Fuels> selectionSyncer = SyncValues.SELECTED_FUEL.lookupFrom(Panels.FUEL_CONFIG, hypervisor);
        Flow fuelRow = new Row().coverChildren()
            .alignX(0.5f)
            .marginTop(5)
            .childPadding(7)
            .child(createFuelSelection(hypervisor, selectionSyncer, Fuels.RESIDUE))
            .child(createFuelSelection(hypervisor, selectionSyncer, Fuels.STELLAR))
            .child(createFuelSelection(hypervisor, selectionSyncer, Fuels.MHDCSM));
        column.child(fuelRow);

        column.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.fuelusage")
                .alignment(Alignment.CENTER)
                .asWidget()
                .width(SIZE_W - 4)
                .alignX(0.5f)
                .marginTop(5));
        column.child(IKey.dynamic(() -> {
            Formatters formatter = data.getFormatter();
            return formatter.format(data.getFuelConsumption()) + " L/5s";
        })
            .alignment(Alignment.CENTER)
            .color(0x404040)
            .asWidget()
            .widgetTheme(GTWidgetThemes.DISPLAY_TEXT)
            .width(SIZE_W - 4)
            .alignX(0.5f)
            .marginTop(3));

        return panel.child(column);
    }

    private static void registerSyncHandlers(SyncHypervisor hypervisor) {
        SyncValues.SELECTED_FUEL.registerFor(Panels.FUEL_CONFIG, hypervisor);
        SyncValues.FUEL_CONSUMPTION.registerFor(Panels.FUEL_CONFIG, hypervisor);
    }

    private static ParentWidget<?> createFuelSelection(SyncHypervisor hypervisor, EnumSyncValue<Fuels> syncer,
        Fuels option) {
        return new ParentWidget<>().coverChildrenWidth()
            .size(18)
            .child(
                new FluidDisplayWidget().background(IDrawable.EMPTY)
                    .value(option.getFluid())
                    .displayAmount(false)
                    .align(Alignment.TopLeft)
                    .size(18))
            .child(
                new SelectButton().value(LinkedBoolValue.of(syncer, option))
                    .disableThemeBackground(true)
                    .disableHoverThemeBackground(true)
                    .selectedBackground(GTGuiTextures.SLOT_OUTLINE_GREEN)
                    .size(18)
                    .tooltip(t -> {
                        if (hypervisor.isClient()) {
                            t.add(option.getFluid().getLocalizedName());
                        }
                    })
                    .tooltipShowUpTimer(TOOLTIP_DELAY));
    }
}
