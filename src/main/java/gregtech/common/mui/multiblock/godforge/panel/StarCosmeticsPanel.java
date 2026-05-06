package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTWidgetThemes;
import gregtech.common.metatileentities.multi.electric.godforge.color.ForgeOfGodsStarColor;
import gregtech.common.metatileentities.multi.electric.godforge.color.StarColorStorage;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncActions;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValue.ForgeOfGodsSyncValue;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class StarCosmeticsPanel {

    private static final int SIZE = 200;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.STAR_COSMETICS);

        registerSyncValues(hypervisor);

        panel.size(SIZE)
            .background(GTGuiTextures.BACKGROUND_GLOW_WHITE)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        panel.child(
            IKey.lang("fog.cosmetics.header")
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .marginTop(8));

        Flow colorColumn = new Column().coverChildren()
            .marginTop(28)
            .marginLeft(4);

        colorColumn.child(
            IKey.lang("fog.cosmetics.color")
                .style(TextFormatting.GOLD, TextFormatting.UNDERLINE)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .leftRelOffset(0, 5));

        IPanelHandler customStarColorPanel = Panels.CUSTOM_STAR_COLOR.getFrom(Panels.STAR_COSMETICS, hypervisor);

        DynamicSyncHandler handler = new DynamicSyncHandler().widgetProvider(($, $$) -> {
            ForgeOfGodsData data = hypervisor.getData();
            StarColorStorage starColors = data.getStarColors();
            ListWidget<IWidget, ?> colorList = new ListWidget<>().size(100, 147)
                .scrollDirection(new VerticalScrollData(true));

            for (int i = 0; i < starColors.size(); i++) {
                ForgeOfGodsStarColor starColor = starColors.getByIndex(i);
                colorList.child(createStarColorRow(starColor, i, hypervisor));
            }

            Flow newStarColorRow = new Row().coverChildren();
            newStarColorRow.child(
                new ButtonWidget<>().size(16)
                    .disableHoverBackground()
                    .background(GTGuiTextures.BUTTON_OUTLINE_HOLLOW)
                    .overlay(
                        IKey.str("+")
                            .alignment(Alignment.CENTER))
                    .onMousePressed(d -> {
                        setEditingStarColor(null, -1, hypervisor);
                        if (!customStarColorPanel.isPanelOpen()) {
                            customStarColorPanel.openPanel();
                        }
                        return true;
                    })
                    .clickSound(ForgeOfGodsGuiUtil.getButtonSound())
                    .tooltip(t -> t.addLine(translateToLocal("fog.cosmetics.starcolor")))
                    .tooltipShowUpTimer(TOOLTIP_DELAY)
                    .marginLeft(5));

            newStarColorRow.child(
                IKey.lang("fog.cosmetics.customstarcolor")
                    .style(TextFormatting.GOLD)
                    .alignment(Alignment.CenterLeft)
                    .asWidget()
                    .size(75, 16)
                    .marginLeft(4));

            colorList.child(newStarColorRow);

            return colorList;
        });

        SyncValues.STAR_COLORS.lookupFrom(Panels.STAR_COSMETICS, hypervisor)
            .setChangeListener(() -> handler.notifyUpdate($ -> {}));

        colorColumn.child(
            new DynamicSyncedWidget<>().coverChildren()
                .marginTop(10)
                .syncHandler(handler));
        panel.child(colorColumn);

        Flow miscColumn = new Column().coverChildren()
            .alignX(1)
            .marginTop(28)
            .marginRight(9);

        miscColumn.child(
            IKey.lang("fog.cosmetics.misc")
                .style(TextFormatting.GOLD, TextFormatting.UNDERLINE)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .alignX(0)
                .marginBottom(10));

        miscColumn.child(createMiscTextFieldRow(SyncValues.STAR_ROTATION_SPEED, "spin", 100, hypervisor));
        miscColumn.child(createMiscTextFieldRow(SyncValues.STAR_SIZE, "size", 40, hypervisor));

        BooleanSyncValue rendererDisabledSyncer = SyncValues.RENDERER_DISABLED
            .lookupFrom(Panels.STAR_COSMETICS, hypervisor);
        miscColumn.child(
            new Row().coverChildren()
                .child(
                    IKey.lang("fog.cosmetics.animations")
                        .style(TextFormatting.GOLD)
                        .alignment(Alignment.CenterLeft)
                        .asWidget()
                        .size(53, 16))
                .child(
                    new ButtonWidget<>().size(16)
                        .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
                        .overlay(new DynamicDrawable(() -> {
                            if (rendererDisabledSyncer.getBoolValue()) {
                                return GTGuiTextures.TT_OVERLAY_BUTTON_POWER_SWITCH_OFF;
                            }
                            return GTGuiTextures.TT_OVERLAY_BUTTON_POWER_SWITCH_ON;
                        }))
                        .onMousePressed(d -> {
                            SyncActions.DISABLE_RENDERER
                                .callFrom(Panels.STAR_COSMETICS, hypervisor, !rendererDisabledSyncer.getBoolValue());
                            return true;
                        })
                        .tooltip(t -> t.addLine(translateToLocal("fog.cosmetics.animations.tooltip")))
                        .tooltipShowUpTimer(TOOLTIP_DELAY)));

        panel.child(miscColumn);

        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        SyncValues.STAR_COLORS.registerFor(Panels.STAR_COSMETICS, hypervisor);
        SyncValues.STAR_COLOR_CLICKED.registerFor(Panels.STAR_COSMETICS, hypervisor);
        SyncValues.STAR_COLOR_EDITING_INDEX.registerFor(Panels.STAR_COSMETICS, hypervisor);
        SyncValues.SELECTED_STAR_COLOR.registerFor(Panels.STAR_COSMETICS, hypervisor);
        SyncValues.RENDERER_DISABLED.registerFor(Panels.STAR_COSMETICS, hypervisor);

        SyncActions.UPDATE_RENDERER.registerFor(Panels.STAR_COSMETICS, hypervisor, hypervisor.getMultiblock());
        SyncActions.DISABLE_RENDERER.registerFor(Panels.STAR_COSMETICS, hypervisor, hypervisor.getMultiblock());
    }

    private static void setEditingStarColor(ForgeOfGodsStarColor starColor, int index, SyncHypervisor hypervisor) {
        GenericSyncValue<ForgeOfGodsStarColor> starColorClicked = SyncValues.STAR_COLOR_CLICKED
            .lookupFrom(Panels.STAR_COSMETICS, hypervisor);
        IntSyncValue editingColorIndex = SyncValues.STAR_COLOR_EDITING_INDEX
            .lookupFrom(Panels.STAR_COSMETICS, hypervisor);

        starColorClicked.setValue(starColor);
        editingColorIndex.setIntValue(index);
    }

    private static Flow createStarColorRow(ForgeOfGodsStarColor starColor, int index, SyncHypervisor hypervisor) {
        IPanelHandler customStarColorPanel = Panels.CUSTOM_STAR_COLOR.getFrom(Panels.STAR_COSMETICS, hypervisor);

        StringSyncValue selectedStarColorSyncer = SyncValues.SELECTED_STAR_COLOR
            .lookupFrom(Panels.STAR_COSMETICS, hypervisor);

        Flow row = new Row().coverChildren()
            .marginBottom(4);

        row.child(
            new ButtonWidget<>().size(16)
                .disableHoverBackground()
                .background(new DynamicDrawable(() -> {
                    if (starColor.getName()
                        .equals(selectedStarColorSyncer.getValue())) {
                        return GTGuiTextures.BUTTON_STANDARD_PRESSED;
                    }
                    return GTGuiTextures.BUTTON_STANDARD;
                }))
                .overlay(
                    starColor.getDrawable()
                        .asIcon()
                        .size(14)
                        .margin(1))
                .onMousePressed(d -> {
                    if (Interactable.hasShiftDown() && !starColor.isPresetColor()) {
                        setEditingStarColor(starColor, index, hypervisor);
                        if (!customStarColorPanel.isPanelOpen()) {
                            customStarColorPanel.openPanel();
                        }
                    } else {
                        selectedStarColorSyncer.setValue(starColor.getName());
                        SyncActions.UPDATE_RENDERER.callFrom(Panels.STAR_COSMETICS, hypervisor, null);
                    }
                    return true;
                })
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound())
                .tooltip(t -> {
                    t.addLine(translateToLocal("fog.cosmetics.selectcolor.tooltip.1"));
                    t.addLine(translateToLocal("fog.cosmetics.selectcolor.tooltip.2"));
                })
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .marginLeft(5));

        row.child(
            IKey.dynamic(starColor::getLocalizedName)
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT)
                .size(75, 16)
                .marginLeft(4));

        return row;
    }

    private static Flow createMiscTextFieldRow(ForgeOfGodsSyncValue<IntSyncValue> syncer, String name, int maxValue,
        SyncHypervisor hypervisor) {
        IntSyncValue syncValue = syncer.create(hypervisor);
        syncValue
            .setChangeListener(() -> SyncActions.UPDATE_RENDERER.callFrom(Panels.STAR_COSMETICS, hypervisor, null));
        return new Row().coverChildren()
            .marginBottom(2)
            .child(
                IKey.lang("fog.cosmetics." + name)
                    .style(TextFormatting.GOLD)
                    .alignment(Alignment.CenterLeft)
                    .asWidget()
                    .size(34, 16))
            .child(
                new TextFieldWidget()
                    .setNumbers(0, maxValue)
                    .setTextAlignment(Alignment.CENTER)
                    .value(syncValue)
                    .tooltip(t -> t.addLine(translateToLocal("fog.cosmetics.onlyintegers")))
                    .tooltipShowUpTimer(TOOLTIP_DELAY)
                    .size(35, 18));
    }
}
