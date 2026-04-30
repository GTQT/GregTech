package gregtech.common.mui.widget.workbench;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.mui.widget.GTTextFieldWidget;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存虚拟滚动视图 widget。
 * <p>
 * 使用固定数量（COLS × ROWS = 48）的 ItemSlot 配合 {@link InventoryViewHandler}
 * 实现虚拟滚动，同时通过搜索框支持按物品名过滤。
 * <p>
 * 对比传统方案（创建 5000+ widget）的优势：
 * <ul>
 *   <li>widget 数量恒定（48 个），内存和渲染开销固定</li>
 *   <li>所有 slot 通过滚动均可访问</li>
 *   <li>搜索框可以快速定位物品</li>
 * </ul>
 */
public class InventoryViewWidget extends ParentWidget<InventoryViewWidget> implements Interactable {

    /** 每行 slot 数 */
    public static final int COLS = 8;
    /** 可见行数 */
    public static final int ROWS = 6;
    /** viewport 固定 slot 数 */
    public static final int VIEWPORT_SIZE = COLS * ROWS;

    /** 滚动条宽度 */
    private static final int SCROLLBAR_WIDTH = 4;
    /** 搜索框高度 */
    private static final int SEARCH_HEIGHT = 12;

    private InventoryViewSyncHandler viewSyncHandler;
    private final List<ItemSlot> slotWidgets = new ArrayList<>(VIEWPORT_SIZE);

    // ==================== 滚动条拖动状态 ====================
    /** 是否正在拖动滚动条 */
    private boolean draggingScrollbar = false;

    public InventoryViewWidget() {
        size(COLS * 18 + SCROLLBAR_WIDTH, ROWS * 18 + SEARCH_HEIGHT + 2);
    }

    /**
     * 绑定同步器。必须在 widget 构建时调用。
     */
    public InventoryViewWidget syncHandler(InventoryViewSyncHandler handler) {
        this.viewSyncHandler = handler;
        setSyncHandler(handler);
        return this;
    }

    /**
     * 添加搜索框和 slot widget 子节点。
     * 在 buildUI 时由 MetaTileEntityWorkbench 调用。
     */
    public InventoryViewWidget buildContent(InventoryViewHandler viewHandler, SlotGroup slotGroup,
                                             StringSyncValue searchSyncValue) {
        // 搜索框：失焦时通过 StringSyncValue 自动同步到服务端触发过滤
        GTTextFieldWidget searchField = new GTTextFieldWidget()
                .setMaxLength(64)
                .value(searchSyncValue);
        searchField.size(COLS * 18 - 2, SEARCH_HEIGHT)
                .pos(0, 0);
        child(searchField);

        // 创建固定数量的 slot widget，绑定到 InventoryViewHandler 的虚拟 slot
        for (int i = 0; i < VIEWPORT_SIZE; i++) {
            final int slotIndex = i;
            int col = i % COLS;
            int row = i / COLS;
            ItemSlot slot = new ItemSlot()
                    .setEnabledIf(s -> viewHandler.getBackingSlot(slotIndex) >= 0)
                    .slot(new ModularSlot(viewHandler, slotIndex)
                            .slotGroup(slotGroup))
                    .background(GTGuiTextures.SLOT);
            slot.pos(col * 18, SEARCH_HEIGHT + 2 + row * 18);
            slot.size(18, 18);
            slotWidgets.add(slot);
            child(slot);
        }

        return this;
    }

    @Override
    public boolean isValidSyncHandler(SyncHandler syncHandler) {
        return syncHandler instanceof InventoryViewSyncHandler;
    }

    // ==================== 鼠标滚轮滚动 ====================

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (viewSyncHandler == null) return false;
        int currentRow = viewSyncHandler.getClientScrollRow();
        int delta = scrollDirection == UpOrDown.UP ? -1 : 1;
        int newRow = currentRow + delta;
        int maxRow = viewSyncHandler.getClientTotalRows() - viewSyncHandler.getViewportRows();
        newRow = Math.max(0, Math.min(newRow, Math.max(0, maxRow)));
        if (newRow != currentRow) {
            viewSyncHandler.sendScrollRow(newRow);
        }
        return true;
    }

    // ==================== 滚动条鼠标拖动 ====================

    /**
     * 判断鼠标是否在滚动条轨道区域。
     */
    private boolean isMouseOnScrollbar(int mouseX, int mouseY) {
        int absX = getArea().x;
        int absY = getArea().y;
        int trackX = absX + COLS * 18;
        int trackY = absY + SEARCH_HEIGHT + 2;
        int trackHeight = ROWS * 18;
        return mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                && mouseY >= trackY && mouseY < trackY + trackHeight;
    }

    /**
     * 根据鼠标 Y 坐标计算目标滚动行。
     */
    private int getScrollRowFromMouseY(int mouseY) {
        if (viewSyncHandler == null) return 0;
        int trackY = getArea().y + SEARCH_HEIGHT + 2;
        int trackHeight = ROWS * 18;
        int totalRows = viewSyncHandler.getClientTotalRows();
        int viewportRows = viewSyncHandler.getViewportRows();
        int maxRow = Math.max(0, totalRows - viewportRows);
        if (maxRow == 0) return 0;

        float relativeY = (float) (mouseY - trackY) / trackHeight;
        relativeY = Math.max(0f, Math.min(1f, relativeY));
        return Math.round(relativeY * maxRow);
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton == 0 && viewSyncHandler != null) {
            int mouseX = getContext().getMouseX();
            int mouseY = getContext().getMouseY();
            if (isMouseOnScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                int newRow = getScrollRowFromMouseY(mouseY);
                if (newRow != viewSyncHandler.getClientScrollRow()) {
                    viewSyncHandler.sendScrollRow(newRow);
                }
                return Result.STOP;
            }
        }
        return Result.IGNORE;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (draggingScrollbar && viewSyncHandler != null) {
            int mouseY = getContext().getMouseY();
            int newRow = getScrollRowFromMouseY(mouseY);
            if (newRow != viewSyncHandler.getClientScrollRow()) {
                viewSyncHandler.sendScrollRow(newRow);
            }
        }
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    // ==================== 滚动条渲染 ====================

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        if (viewSyncHandler == null) return;

        // 绘制滚动条轨道
        int trackX = COLS * 18;
        int trackY = SEARCH_HEIGHT + 2;
        int trackHeight = ROWS * 18;
        GuiDraw.drawRect(trackX, trackY, SCROLLBAR_WIDTH, trackHeight, 0xFF2A2A2A);

        // 绘制滚动条滑块
        int totalRows = viewSyncHandler.getClientTotalRows();
        int viewportRows = viewSyncHandler.getViewportRows();
        if (totalRows > viewportRows) {
            float ratio = (float) viewportRows / totalRows;
            int thumbHeight = Math.max(8, (int) (trackHeight * ratio));
            int maxScroll = totalRows - viewportRows;
            int currentRow = viewSyncHandler.getClientScrollRow();
            float scrollProgress = maxScroll > 0 ? (float) currentRow / maxScroll : 0;
            int thumbY = trackY + (int) ((trackHeight - thumbHeight) * scrollProgress);
            GuiDraw.drawRect(trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight, 0xFF808080);
        } else {
            // 不需要滚动时，滑块填满整个轨道
            GuiDraw.drawRect(trackX, trackY, SCROLLBAR_WIDTH, trackHeight, 0xFF505050);
        }
    }
}
