package gregtech.common.mui.widget.workbench;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.common.metatileentities.workbench.MetaTileEntityAutoWorkbench;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import org.jetbrains.annotations.NotNull;

/**
 * 自动工作台 GUI (移植自 GT5-Unofficial 的 {@code MTEElectricAutoWorkbenchGui})。
 * <p>
 * 布局与源实现保持一致: 左侧为输入/合成网格, 中间为幻影合成模板, 右侧是吞吐量/模式按钮与配方预览、
 * 结果槽, 下方是一排容器返还槽与内部储罐。整体外观沿用本项目的合成站 ({@link WorkbenchUI}) 风格。
 */
public final class AutoWorkbenchUI {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 208;

    private static final String GROUP_INPUT = "auto_workbench_input";
    private static final String GROUP_OUTPUT = "auto_workbench_output";
    private static final String GROUP_TEMPLATES = "auto_workbench_templates";

    private AutoWorkbenchUI() {}

    public static ModularPanel build(@NotNull MetaTileEntityAutoWorkbench machine,
                                     @NotNull PanelSyncManager syncManager) {
        IntSyncValue modeSync = new IntSyncValue(machine::getMode, machine::setMode);
        IntSyncValue throughputSync = new IntSyncValue(machine::getThroughput, machine::setThroughput);
        syncManager.syncValue("auto_workbench_mode", 0, modeSync);
        syncManager.syncValue("auto_workbench_throughput", 0, throughputSync);

        syncManager.registerSlotGroup(GROUP_INPUT, 9);
        syncManager.registerSlotGroup(GROUP_OUTPUT, 9);
        syncManager.registerSlotGroup(GROUP_TEMPLATES, 9);

        return GTGuis.createPanel(machine, PANEL_WIDTH, PANEL_HEIGHT)
                .child(IKey.lang(machine.getMetaFullName()).asWidget().pos(5, 5))
                .child(Flow.column()
                        .left(7)
                        .top(18)
                        .coverChildren()
                        .child(Flow.row()
                                .coverChildrenHeight()
                                // 输入 / 合成网格
                                .child(createGrid(machine.getBenchInventory(), 0, GROUP_INPUT, false)
                                        .tooltipBuilder(t -> t.addLine(IKey.lang(
                                                "gregtech.machine.auto_workbench.slot.input"))))
                                // 幻影合成模板
                                .child(createGrid(machine.getTemplateInventory(), 0, GROUP_TEMPLATES, true)
                                        .marginLeft(1)
                                        .tooltipBuilder(t -> t.addLine(IKey.lang(
                                                "gregtech.machine.auto_workbench.slot.template"))))
                                // 按钮与预览/结果
                                .child(createControlColumn(machine, throughputSync, modeSync).marginLeft(2)))
                        // 容器返还槽
                        .child(createOutputRow(machine).marginTop(1))
                        // 内部储罐
                        .child(createTankRow(machine).marginTop(2)))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    /** 内部储罐, 用于给缓冲区中的流体容器装罐 */
    private static Flow createTankRow(@NotNull MetaTileEntityAutoWorkbench machine) {
        return Flow.row()
                .coverChildrenHeight()
                .child(new GTFluidSlot()
                        .syncHandler(GTFluidSlot.sync(machine.getTank())))
                .child(IKey.lang("gregtech.machine.auto_workbench.slot.tank")
                        .asWidget()
                        .marginLeft(4)
                        .marginTop(5));
    }

    /** 3x3 网格: {@code phantom} 为 true 时使用幻影槽位(合成模板) */
    private static SlotGroupWidget createGrid(@NotNull IItemHandlerModifiable handler, int offset,
                                              @NotNull String group, boolean phantom) {
        return SlotGroupWidget.builder()
                .matrix("SSS", "SSS", "SSS")
                .key('S', index -> {
                    ModularSlot slot = SyncHandlers.itemSlot(handler, offset + index).slotGroup(group);
                    if (phantom) {
                        return new PhantomItemSlot()
                                .slot(slot)
                                .background(GTGuiTextures.SLOT_DARK);
                    }
                    return new ItemSlot()
                            .slot(slot)
                            .background(GTGuiTextures.SLOT);
                })
                .build();
    }

    /** 一排 9 个容器返还槽(源实现的 9-17 号槽位) */
    private static SlotGroupWidget createOutputRow(@NotNull MetaTileEntityAutoWorkbench machine) {
        return SlotGroupWidget.builder()
                .matrix("SSSSSSSSS")
                .key('S', index -> new ItemSlot()
                        .slot(SyncHandlers.itemSlot(machine.getBenchInventory(),
                                MetaTileEntityAutoWorkbench.OUTPUT_SLOT_OFFSET + index).slotGroup(GROUP_OUTPUT))
                        .background(GTGuiTextures.SLOT, GTGuiTextures.IN_SLOT_OVERLAY))
                .build();
    }

    /** 右侧控制区: 吞吐量按钮 + 配方预览 / Logo + 工作台图标 + 箭头 / 模式按钮 + 结果槽 */
    private static Flow createControlColumn(@NotNull MetaTileEntityAutoWorkbench machine,
                                            @NotNull IntSyncValue throughputSync,
                                            @NotNull IntSyncValue modeSync) {
        Flow topRow = Flow.row()
                .coverChildrenHeight()
                .child(createThroughputButton(throughputSync).marginLeft(2).marginRight(2))
                .child(createPreviewSlot(machine));

        Flow middleRow = Flow.row()
                .coverChildrenHeight()
                .child(GTGuiTextures.getLogo(machine.getUITheme()).asWidget()
                        .size(17)
                        .marginTop(1)
                        .marginRight(1))
                .child(GTGuiTextures.PICTURE_WORKBENCH_CIRCLE.asWidget()
                        .size(16)
                        .marginTop(1)
                        .marginRight(1))
                .child(GTGuiTextures.PICTURE_ARROW_WHITE_DOWN.asWidget()
                        .size(10, 16)
                        .marginTop(1));

        Flow bottomRow = Flow.row()
                .coverChildrenHeight()
                .child(createModeButton(modeSync).marginLeft(2).marginRight(2))
                .child(createResultSlot(machine));

        return Flow.column()
                .size(51, 54)
                .child(topRow)
                .child(middleRow.marginTop(1))
                .child(bottomRow.marginTop(1));
    }

    /** 吞吐量按钮: 4 个状态, 同时控制自动输出物品与对外输出能量 */
    private static CycleButtonWidget createThroughputButton(@NotNull IntSyncValue throughputSync) {
        CycleButtonWidget button = new CycleButtonWidget()
                .size(18)
                .value(throughputSync)
                .stateCount(MetaTileEntityAutoWorkbench.MAX_THROUGHPUT);

        for (int i = 0; i < MetaTileEntityAutoWorkbench.MAX_THROUGHPUT; i++) {
            button.stateOverlay(i, GTGuiTextures.OVERLAY_BUTTON_THROUGHPUT[i]);
            int state = i;
            button.tooltipBuilder(i, tooltip -> {
                boolean itemOutput = state < 2;
                boolean energyOutput = state % 2 == 0;
                tooltip.addLine(IKey.lang("gregtech.machine.auto_workbench.throughput.item_output",
                        IKey.lang(itemOutput ? "gregtech.machine.auto_workbench.tooltip.enabled" :
                                "gregtech.machine.auto_workbench.tooltip.disabled")));
                tooltip.addLine(IKey.lang("gregtech.machine.auto_workbench.throughput.energy_output",
                        IKey.lang(energyOutput ? "gregtech.machine.auto_workbench.tooltip.enabled" :
                                "gregtech.machine.auto_workbench.tooltip.disabled")));
            });
        }
        return button;
    }

    /** 合成模式按钮: 10 个状态 */
    private static CycleButtonWidget createModeButton(@NotNull IntSyncValue modeSync) {
        CycleButtonWidget button = new CycleButtonWidget()
                .size(18)
                .value(modeSync)
                .stateCount(MetaTileEntityAutoWorkbench.MAX_MODES);

        for (int i = 0; i < MetaTileEntityAutoWorkbench.MAX_MODES; i++) {
            button.stateOverlay(i, GTGuiTextures.OVERLAY_BUTTON_MODE[i]);
            int mode = i;
            button.tooltipBuilder(i, tooltip -> tooltip.addLine(IKey.lang(
                    "gregtech.machine.auto_workbench.mode.tooltip",
                    IKey.lang("gregtech.machine.auto_workbench.mode." + mode))));
        }
        return button;
    }

    /** 配方预览槽, 仅展示当前匹配到的产物 */
    private static ItemSlot createPreviewSlot(@NotNull MetaTileEntityAutoWorkbench machine) {
        return new ItemSlot()
                .slot(SyncHandlers.itemSlot(machine.getPreviewInventory(), 0).accessibility(false, false))
                .background(GTGuiTextures.SLOT, GTGuiTextures.OUT_SLOT_OVERLAY)
                .tooltipBuilder(t -> t.addLine(IKey.lang("gregtech.machine.auto_workbench.slot.preview")));
    }

    /** 结果槽(源实现的 18 号槽位), 自动化可抽取 */
    private static ItemSlot createResultSlot(@NotNull MetaTileEntityAutoWorkbench machine) {
        return new ItemSlot()
                .slot(SyncHandlers.itemSlot(machine.getBenchInventory(), MetaTileEntityAutoWorkbench.RESULT_SLOT)
                        .accessibility(false, true))
                .background(GTGuiTextures.SLOT, GTGuiTextures.OUT_SLOT_OVERLAY)
                .tooltipBuilder(t -> t.addLine(IKey.lang("gregtech.machine.auto_workbench.slot.result")));
    }
}
