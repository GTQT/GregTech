package gregtech.common.mui.widget.workbench;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 虚拟滚动视口 handler。
 * <p>
 * 内部持有一个固定大小的 viewport（如 48 slot），通过 slotMapping 数组
 * 将 viewport 中的逻辑 slot 映射到底层 backing handler 的实际 slot。
 * <p>
 * 滚动和搜索通过修改 slotMapping 实现，无需重建 widget。
 * 使用 {@link Supplier} 获取底层 handler，确保引用始终是最新的（应对库存结构变化）。
 */
public class InventoryViewHandler implements IItemHandlerModifiable {

    /** 底层真实库存的延迟引用 */
    private final Supplier<IItemHandlerModifiable> backingSupplier;
    /** viewport 中每个逻辑 slot 映射到的 backing slot 索引，-1 表示无效/空 */
    private final int[] slotMapping;
    /** viewport 固定大小 */
    private final int viewportSize;
    /** 当前可见的有效 slot 数量（<= viewportSize） */
    private int visibleCount;

    // ==================== 搜索/过滤相关 ====================
    /** 过滤后的 slot 索引列表（backing 中的实际索引） */
    private IntList filteredSlots;
    /** 当前搜索关键字（空字符串表示无过滤） */
    private String searchText = "";
    /** 当前滚动行偏移 */
    private int scrollRow = 0;
    /** 每行的 slot 数量 */
    private final int columns;
    private ItemStack[] lastSnapshot = new ItemStack[0];

    public InventoryViewHandler(Supplier<IItemHandlerModifiable> backingSupplier, int viewportSize, int columns) {
        this.backingSupplier = backingSupplier;
        this.viewportSize = viewportSize;
        this.columns = columns;
        this.slotMapping = new int[viewportSize];
        rebuildView();
    }

    private IItemHandlerModifiable backing() {
        return backingSupplier.get();
    }

    /**
     * 设置搜索关键字并重建视图。
     */
    public void setSearchText(String text) {
        this.searchText = text == null ? "" : text.toLowerCase();
        this.scrollRow = 0;
        rebuildView();
    }

    public String getSearchText() {
        return searchText;
    }

    /**
     * 设置滚动行偏移并重建 slot 映射。
     */
    public void setScrollRow(int row) {
        int maxRow = getMaxScrollRow();
        this.scrollRow = Math.max(0, Math.min(row, maxRow));
        rebuildSlotMapping();
    }

    public int getScrollRow() {
        return scrollRow;
    }

    /**
     * 获取最大可滚动行数。
     */
    public int getMaxScrollRow() {
        int totalRows = (getTotalFilteredSlots() + columns - 1) / columns;
        int viewportRows = viewportSize / columns;
        return Math.max(0, totalRows - viewportRows);
    }

    /**
     * 获取过滤后的总 slot 数。
     */
    public int getTotalFilteredSlots() {
        return filteredSlots.size();
    }

    /**
     * 重建过滤列表和 slot 映射。
     * 有物品的 slot 排在前面，空 slot 排在后面。
     */
    private void rebuildView() {
        IItemHandlerModifiable backing = backing();
        int totalSlots = backing.getSlots();

        filteredSlots = new IntArrayList();
        IntList emptySlots = new IntArrayList();

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = backing.getStackInSlot(i);
            if (!stack.isEmpty()) {
                // 有搜索文本时额外检查名字匹配
                if (searchText.isEmpty() || stack.getDisplayName().toLowerCase().contains(searchText)) {
                    filteredSlots.add(i);
                }
            } else {
                // 空 slot 只在无搜索时才收集（搜索时只显示匹配的有物品槽位）
                if (searchText.isEmpty()) {
                    emptySlots.add(i);
                }
            }
        }

        // 有物品的在前，空 slot 在后
        filteredSlots.addAll(emptySlots);

        // 确保 scrollRow 不超出范围
        int maxRow = getMaxScrollRow();
        if (scrollRow > maxRow) {
            scrollRow = maxRow;
        }
        updateSnapshot(backing, totalSlots);
        rebuildSlotMapping();
    }

    /**
     * 根据当前 scrollRow 重建 viewport 的 slot 映射。
     */
    private void rebuildSlotMapping() {
        int startIndex = scrollRow * columns;
        int totalFiltered = filteredSlots.size();
        visibleCount = 0;

        for (int i = 0; i < viewportSize; i++) {
            int filteredIndex = startIndex + i;
            if (filteredIndex < totalFiltered) {
                slotMapping[i] = filteredSlots.getInt(filteredIndex);
                visibleCount++;
            } else {
                slotMapping[i] = -1;
            }
        }
    }

    /**
     * 获取 viewport slot 对应的 backing slot 索引，-1 表示无效。
     */
    public int getBackingSlot(int viewportSlot) {
        if (viewportSlot < 0 || viewportSlot >= viewportSize) return -1;
        return slotMapping[viewportSlot];
    }

    public int[] copySlotMapping() {
        return Arrays.copyOf(slotMapping, slotMapping.length);
    }

    public void setSlotMapping(int[] mapping) {
        int copyLength = Math.min(mapping.length, slotMapping.length);
        System.arraycopy(mapping, 0, slotMapping, 0, copyLength);
        Arrays.fill(slotMapping, copyLength, slotMapping.length, -1);

        visibleCount = 0;
        for (int slot : slotMapping) {
            if (slot >= 0) {
                visibleCount++;
            }
        }
    }

    /**
     * 刷新搜索过滤（库存内容变化时调用，不改变搜索文本和滚动位置）。
     */
    public void refreshFilter() {
        rebuildView();
    }

    public boolean refreshFilterIfChanged() {
        IItemHandlerModifiable backing = backing();
        int totalSlots = backing.getSlots();
        if (!hasInventoryChanged(backing, totalSlots)) {
            return false;
        }

        rebuildView();
        return true;
    }

    private boolean hasInventoryChanged(IItemHandlerModifiable backing, int totalSlots) {
        if (lastSnapshot.length != totalSlots) {
            return true;
        }

        for (int i = 0; i < totalSlots; i++) {
            ItemStack current = backing.getStackInSlot(i);
            ItemStack last = lastSnapshot[i];
            if (current.isEmpty() && last.isEmpty()) continue;
            if (!ItemStack.areItemStacksEqual(current, last)) {
                return true;
            }
        }
        return false;
    }

    private void updateSnapshot(IItemHandlerModifiable backing, int totalSlots) {
        if (lastSnapshot.length != totalSlots) {
            lastSnapshot = new ItemStack[totalSlots];
        }

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = backing.getStackInSlot(i);
            lastSnapshot[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }

    // ==================== IItemHandlerModifiable 实现 ====================

    @Override
    public int getSlots() {
        return viewportSize;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        int backingSlot = getBackingSlot(slot);
        IItemHandlerModifiable backing = backing();
        if (backingSlot < 0 || backingSlot >= backing.getSlots()) return ItemStack.EMPTY;
        return backing.getStackInSlot(backingSlot);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int backingSlot = getBackingSlot(slot);
        IItemHandlerModifiable backing = backing();
        if (backingSlot < 0 || backingSlot >= backing.getSlots()) return;
        backing.setStackInSlot(backingSlot, stack);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int backingSlot = getBackingSlot(slot);
        IItemHandlerModifiable backing = backing();
        if (backingSlot < 0 || backingSlot >= backing.getSlots()) return stack;
        return backing.insertItem(backingSlot, stack, simulate);
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int backingSlot = getBackingSlot(slot);
        IItemHandlerModifiable backing = backing();
        if (backingSlot < 0 || backingSlot >= backing.getSlots()) return ItemStack.EMPTY;
        return backing.extractItem(backingSlot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int backingSlot = getBackingSlot(slot);
        IItemHandlerModifiable backing = backing();
        if (backingSlot < 0 || backingSlot >= backing.getSlots()) return 0;
        return backing.getSlotLimit(backingSlot);
    }
}
