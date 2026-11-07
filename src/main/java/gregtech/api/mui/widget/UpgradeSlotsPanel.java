package gregtech.api.mui.widget;

import gregtech.api.metatileentity.IUpgradeSlots;
import gregtech.api.mui.GTGuis;
import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器界面左侧那一列里的“升级插件”入口按钮，以及它点开的浮动面板。
 *
 * <p>
 * 面板里是 {@link IUpgradeSlots#UPGRADE_SLOT_COUNT} 个物品槽（每行 4 个）。槽位里的物品目前只是
 * “放进去”，没有任何效果。
 *
 * <p>
 * <b>和 {@link WorldPreviewPanel} 一样，这个方法两端都会跑</b>（主界面构建时服务端也要注册一遍
 * {@code syncedPanel} 和槽位组），所以它不能整体标 {@code @SideOnly(CLIENT)}，也不做
 * “是不是客户端”的早退。槽位本身由 MUI 同步，不需要自己写同步值。
 */
public final class UpgradeSlotsPanel {

    /** 浮动面板的 id，一台机器同时只会开一个。 */
    private static final String PANEL_NAME = "upgrade_slots";
    /** 同步 key，两端必须一致。 */
    private static final String SYNC_KEY = "upgrade_slots_panel";
    /** 槽位组名，两端必须一致。 */
    private static final String SLOT_GROUP = "upgrade_slots";

    private UpgradeSlotsPanel() {}

    /**
     * 构建入口按钮，并把浮动面板注册进主面板的同步管理器。
     *
     * <p>
     * <b>这个方法两端都会跑</b>（由 {@code MetaTileEntity#createUpgradeSlotsButton} 调用），
     * {@code syncedPanel} 必须在服务端也注册一遍，否则客户端点开时发来的同步包在服务端找不到
     * 处理器，按钮点不开。
     *
     * @param syncManager 机器主面板的同步管理器
     * @param inventory   升级插件库存，槽位数不少于 {@link IUpgradeSlots#UPGRADE_SLOT_COUNT}
     * @param stack       标题栏里显示的机器图标
     * @return 挂到左侧那一列的按钮
     */
    public static @NotNull IWidget create(@NotNull PanelSyncManager syncManager,
                                          @NotNull IItemHandlerModifiable inventory,
                                          @NotNull ItemStack stack) {
        IPanelHandler panel = syncManager.syncedPanel(SYNC_KEY, true,
                (panelSyncManager, $) -> buildPanel(panelSyncManager, inventory, stack));

        return new ButtonWidget<>()
                .size(18, 18)
                // 插件物品还没有做，先用处理器图标占位
                .overlay(new ItemDrawable(MetaItems.MICROPROCESSOR_LV.getStackForm()).asIcon().size(16))
                .addTooltipLine(IKey.str("升级插件"))
                .onMousePressed(mouseButton -> {
                    if (panel.isPanelOpen()) {
                        panel.closePanel();
                    } else {
                        panel.openPanel();
                    }
                    return true;
                });
    }

    /** 浮动面板：标题 + 槽位。 */
    private static @NotNull ModularPanel buildPanel(@NotNull PanelSyncManager syncManager,
                                                    @NotNull IItemHandlerModifiable inventory,
                                                    @NotNull ItemStack stack) {
        // 槽位组要在子面板自己的同步管理器上注册（和 CoverStorage 的写法一致），一排 4 个
        syncManager.registerSlotGroup(SLOT_GROUP, 4);

        int slots = Math.min(inventory.getSlots(), IUpgradeSlots.UPGRADE_SLOT_COUNT);
        List<List<IWidget>> rows = new ArrayList<>();
        for (int row = 0; row * 4 < slots; row++) {
            List<IWidget> rowWidgets = new ArrayList<>();
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                if (index >= slots) break;
                rowWidgets.add(new ItemSlot()
                        .slot(new ModularSlot(inventory, index).slotGroup(SLOT_GROUP)));
            }
            rows.add(rowWidgets);
        }

        return GTGuis.createPopupPanel(PANEL_NAME, 176, 26 + rows.size() * 18 + 7)
                .child(GTGuis.popupTitle(stack, "升级插件"))
                .child(new Grid()
                        .horizontalCenter().top(26)
                        .width(4 * 18).height(rows.size() * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .matrix(rows));
    }
}
