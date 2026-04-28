package gregtech.common.mui.widget.workbench;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import java.util.Arrays;

/**
 * 库存虚拟滚动视图的同步器。
 * <p>
 * 职责：
 * <ul>
 *   <li>客户端→服务端：滚动行变更</li>
 *   <li>服务端→客户端：总行数同步（用于客户端滚动条计算）</li>
 * </ul>
 * <p>
 * 搜索文本同步由 {@link com.cleanroommc.modularui.value.sync.StringSyncValue} 处理。
 * slot 内容同步由 {@link com.cleanroommc.modularui.widgets.slot.ModularSlot} 的
 * Minecraft Container 机制自动处理。
 */
public class InventoryViewSyncHandler extends SyncHandler {

    // ==================== 协议 ID ====================
    /** 客户端→服务端：滚动行变更 */
    private static final int SCROLL_ROW = 1;
    private static final int SYNC_VIEW = 2;

    private final InventoryViewHandler viewHandler;
    private final int columns;
    private final int viewportSize;

    // ==================== 服务端变化检测缓存 ====================
    /** 上一次同步的总行数 */
    private int lastSyncedTotalRows = -1;
    private int lastSyncedScrollRow = -1;
    private int[] lastSyncedSlotMapping = new int[0];

    // ==================== 客户端状态 ====================
    /** 客户端缓存的总行数 */
    private int clientTotalRows = 0;
    /** 客户端当前滚动行 */
    private int clientScrollRow = 0;

    public InventoryViewSyncHandler(InventoryViewHandler viewHandler, int viewportSize, int columns) {
        this.viewHandler = viewHandler;
        this.viewportSize = viewportSize;
        this.columns = columns;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        boolean inventoryChanged = viewHandler.refreshFilterIfChanged();
        int totalFiltered = viewHandler.getTotalFilteredSlots();
        int totalRows = (totalFiltered + columns - 1) / columns;
        int scrollRow = viewHandler.getScrollRow();
        int[] slotMapping = viewHandler.copySlotMapping();
        if (init || inventoryChanged || totalRows != lastSyncedTotalRows || scrollRow != lastSyncedScrollRow ||
                !Arrays.equals(slotMapping, lastSyncedSlotMapping)) {
            lastSyncedTotalRows = totalRows;
            lastSyncedScrollRow = scrollRow;
            lastSyncedSlotMapping = slotMapping;
            syncToClient(SYNC_VIEW, buf -> {
                buf.writeVarInt(totalRows);
                buf.writeVarInt(scrollRow);
                for (int slot : slotMapping) {
                    buf.writeVarInt(slot);
                }
            });
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        if (id == SCROLL_ROW) {
            int row = buf.readVarInt();
            viewHandler.setScrollRow(row);
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id == SYNC_VIEW) {
            clientTotalRows = buf.readVarInt();
            clientScrollRow = buf.readVarInt();
            int[] slotMapping = new int[viewportSize];
            for (int i = 0; i < viewportSize; i++) {
                slotMapping[i] = buf.readVarInt();
            }
            viewHandler.setSlotMapping(slotMapping);
        }
    }

    // ==================== 客户端操作方法 ====================

    /**
     * 客户端发送滚动行到服务端，并同时更新客户端本地 viewHandler 映射。
     */
    public void sendScrollRow(int row) {
        this.clientScrollRow = row;
        // 立即更新客户端映射，使 setEnabledIf 检查能即时反映滚动位置变化
        viewHandler.setScrollRow(row);
        syncToServer(SCROLL_ROW, buf -> buf.writeVarInt(row));
    }

    /**
     * 获取客户端缓存的总行数。
     */
    public int getClientTotalRows() {
        return clientTotalRows;
    }

    /**
     * 获取客户端当前滚动行。
     */
    public int getClientScrollRow() {
        return clientScrollRow;
    }

    /**
     * 获取 viewport 行数。
     */
    public int getViewportRows() {
        return viewportSize / columns;
    }

    public InventoryViewHandler getViewHandler() {
        return viewHandler;
    }
}
