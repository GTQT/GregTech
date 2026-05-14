package gregtech.common.mui.multiblock.godforge.panel;

import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.util.GTLog;
import gregtech.common.metatileentities.multi.electric.godforge.data.Milestones;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class MilestonePanel {

    private static final int SIZE_W = 400;
    private static final int SIZE_H = 300;

    private static final int MILESTONE_BUTTON_SIZE_W = 130;
    private static final int MILESTONE_BUTTON_SIZE_H = 100;

    private static final int MILESTONE_BUTTON_MARGIN_X = 37;
    private static final int MILESTONE_BUTTON_MARGIN_Y = 24;

    private static final int MILESTONE_PROGRESS_BAR_H = 7;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.MILESTONE);

        registerSyncValues(hypervisor);

        panel.size(SIZE_W, SIZE_H)
            .background(GTGuiTextures.BACKGROUND_SPACE)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        panel.child(createMilestone(Milestones.CHARGE, hypervisor));
        panel.child(createMilestone(Milestones.CONVERSION, hypervisor));
        panel.child(createMilestone(Milestones.CATALYST, hypervisor));
        panel.child(createMilestone(Milestones.COMPOSITION, hypervisor));

        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        SyncValues.MILESTONE_CLICKED.registerFor(Panels.MILESTONE, hypervisor);

        SyncValues.MILESTONE_CHARGE_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CHARGE_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CONVERSION_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CONVERSION_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CATALYST_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_CATALYST_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_COMPOSITION_PROGRESS.registerFor(Panels.MILESTONE, hypervisor);
        SyncValues.MILESTONE_COMPOSITION_PROGRESS_INVERTED.registerFor(Panels.MILESTONE, hypervisor);
    }

    private static ParentWidget<?> createMilestone(Milestones milestone, SyncHypervisor hypervisor) {
        IPanelHandler individualPanel = Panels.INDIVIDUAL_MILESTONE.getFrom(Panels.MILESTONE, hypervisor);

        EnumSyncValue<Milestones> milestoneSyncer = SyncValues.MILESTONE_CLICKED
            .lookupFrom(Panels.MILESTONE, hypervisor);
        FloatSyncValue progressSyncer = milestone.getProgressSyncer()
            .lookupFrom(Panels.MILESTONE, hypervisor);
        FloatSyncValue invertedProgressSyncer = milestone.getProgressInvertedSyncer()
            .lookupFrom(Panels.MILESTONE, hypervisor);

        // DEBUG: Log syncer state at panel creation time
        GTLog.logger.info("[FOG Milestone DEBUG] createMilestone {} - progressSyncer={}, " +
            "invertedProgressSyncer={}, isClient={}",
            milestone.name(),
            progressSyncer != null ? progressSyncer.getFloatValue() : "NULL",
            invertedProgressSyncer != null ? invertedProgressSyncer.getFloatValue() : "NULL",
            hypervisor.isClient());

        ParentWidget<?> parent = new ParentWidget<>().size(MILESTONE_BUTTON_SIZE_W, MILESTONE_BUTTON_SIZE_H)
            .align(milestone.getPosition())
            .margin(MILESTONE_BUTTON_MARGIN_X, MILESTONE_BUTTON_MARGIN_Y);

        // Background image and individual milestone button
        parent.child(
            new ButtonWidget<>().alignX(Alignment.CENTER)
                .size(milestone.getMainWidth(), milestone.getMainHeight())
                .background(milestone.getMainBackground())
                .disableHoverBackground()
                .onMousePressed(d -> {
                    milestoneSyncer.setValue(milestone);
                    if (!individualPanel.isPanelOpen()) {
                        individualPanel.openPanel();
                    }
                    return true;
                })
                .tooltip(t -> t.addLine(translateToLocal("gt.blockmachines.multimachine.FOG.milestoneinfo")))
                .clickSound(ForgeOfGodsGuiUtil.getButtonSound()));

        // Milestone progress bar
        parent.child(
            new ProgressWidget().value(progressSyncer)
                .texture(
                    GTGuiTextures.PROGRESSBAR_GODFORGE_MILESTONE_BACKGROUND,
                    milestone.getProgressBarMainOverlay(),
                    -1)
                .direction(Direction.RIGHT)
                .alignY(Alignment.CENTER)
                .widthRel(1.0f)
                .height(MILESTONE_PROGRESS_BAR_H));
        parent.child(
            new ProgressWidget().value(invertedProgressSyncer)
                .texture(GTGuiTextures.BLANK_TRANSPARENT, milestone.getProgressBarInvertedOverlay(), -1)
                .direction(Direction.LEFT)
                .alignY(Alignment.CENTER)
                .widthRel(1.0f)
                .height(MILESTONE_PROGRESS_BAR_H));

        // Milestone title
        parent.child(
            IKey.lang(milestone.getTitleLangKey())
                .style(TextFormatting.GOLD)
                .asWidget()
                .alignY(0.35f)
                .alignX(Alignment.CENTER));

        return parent;
    }
}
