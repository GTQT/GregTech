package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.data.Milestones;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class MilestonePanel {

    private static final int SIZE = 200;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.MILESTONE);

        registerSyncValues(hypervisor);

        panel.size(SIZE)
            .background(GTGuiTextures.BACKGROUND_GLOW_WHITE)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        EnumSyncValue<Milestones> milestoneSyncer = SyncValues.MILESTONE_CLICKED
            .lookupFrom(Panels.MILESTONE, hypervisor);

        Flow column = new Column().coverChildren()
            .marginTop(12)
            .alignX(0.5f);

        column.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.milestones")
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .marginBottom(16));

        for (Milestones milestone : Milestones.VALUES) {
            column.child(createMilestoneRow(hypervisor, milestone, milestoneSyncer));
        }

        panel.child(column);
        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        SyncValues.MILESTONE_CLICKED.registerFor(Panels.MILESTONE, hypervisor);

        SyncValues.MILESTONE_CHARGE_LEVEL.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CHARGE_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CHARGE_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);

        SyncValues.MILESTONE_CONVERSION_LEVEL.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CONVERSION_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CONVERSION_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);

        SyncValues.MILESTONE_CATALYST_LEVEL.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CATALYST_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CATALYST_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);

        SyncValues.MILESTONE_COMPOSITION_LEVEL.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_COMPOSITION_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_COMPOSITION_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);
    }

    private static Flow createMilestoneRow(SyncHypervisor hypervisor, Milestones milestone,
        EnumSyncValue<Milestones> syncer) {
        Flow row = new Row().size(180, 36)
            .marginBottom(4)
            .alignX(0.5f);

        row.child(
            new ButtonWidget<>().size(36)
                .background(milestone.getMainBackground())
                .disableHoverBackground()
                .overlay(new DynamicDrawable(() -> {
                    BooleanSyncValue inversionSyncer = SyncValues.INVERSION
                        .lookupFrom(hypervisor.getMainModule(), Panels.MILESTONE, hypervisor);
                    boolean inversion = inversionSyncer.getBoolValue();

                    FloatSyncValue progressSyncer;
                    if (inversion) {
                        progressSyncer = milestone.getProgressInvertedSyncer()
                            .lookupFrom(Panels.MILESTONE, hypervisor);
                    } else {
                        progressSyncer = milestone.getProgressSyncer()
                            .lookupFrom(Panels.MILESTONE, hypervisor);
                    }

                    return milestone.getProgressBarMainOverlay()
                        .getSubArea(0f, 0f, progressSyncer.getFloatValue(), 1f);
                }))
                .onMousePressed(d -> {
                    syncer.setValue(milestone);
                    return true;
                })
                .tooltip(t -> t.addLine(translateToLocal(milestone.getTitleLangKey())))
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound()));

        row.child(
            IKey.lang(milestone.getTitleLangKey())
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(140, 36)
                .marginLeft(4));

        return row;
    }
}
