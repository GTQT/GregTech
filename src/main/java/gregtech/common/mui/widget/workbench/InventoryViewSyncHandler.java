package gregtech.common.mui.widget.workbench;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

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
    /** 服务端→客户端：同步总行数（用于客户端滚动条计算） */
    private static final int SYNC_TOTAL_ROWS = 2;

    private final InventoryViewHandler viewHandler;
    private final int columns;
    private final int viewportSize;

    // ==================== 服务端变化检测缓存 ====================
    /** 上一次同步的总行数 */
    private int lastSyncedTotalRows = -1;

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
        // 同步总行数（用于客户端滚动条）
        int totalFiltered = viewHandler.getTotalFilteredSlots();
        int totalRows = (totalFiltered + columns - 1) / columns;
        if (init || totalRows != lastSyncedTotalRows) {
            lastSyncedTotalRows = totalRows;
            syncToClient(SYNC_TOTAL_ROWS, buf -> buf.writeVarInt(totalRows));
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
        if (id == SYNC_TOTAL_ROWS) {
            clientTotalRows = buf.readVarInt();
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
