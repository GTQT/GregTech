package gregtech.common.mui.widget.workbench;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory.MemorizedRecipe;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;

/**
 * 锁定配方区域的虚拟滚动网格 widget。
 * <p>
 * 使用固定数量（COLS × VISIBLE_ROWS）的 {@link RecipeMemorySlot}，通过动态 index
 * 实现虚拟滚动，只渲染可见区域的配方。
 * <p>
 * 支持按配方输出物品名搜索过滤（纯客户端，配方数据已由 SyncHandler 同步）。
 * 搜索框由外部提供，通过 {@link #setSearchField(GTTextFieldWidget)} 关联。
 * <p>
 * 设计思路与 {@link InventoryViewWidget} 类似，但更轻量：
 * 配方数据已由 {@link CraftingRecipeMemory} 的 SyncHandler 同步，
 * 无需额外的 slot 映射和同步机制。
 */
public class RecipeMemoryGridWidget extends ParentWidget<RecipeMemoryGridWidget> implements Interactable {

    /** 每行配方 slot 数 */
    public static final int COLS = 3;
    /** 可见行数 */
    public static final int VISIBLE_ROWS = 3;
    /** viewport 固定 slot 数 */
    public static final int VIEWPORT_SIZE = COLS * VISIBLE_ROWS;
    /** 滚动条宽度 */
    private static final int SCROLLBAR_WIDTH = 4;

    private final CraftingRecipeMemory memory;
    /** 锁定区起始 index（在 memorizedRecipes 数组中的偏移） */
    private final int lockedStart;
    /** 锁定区总容量 */
    private final int lockedCapacity;
    /** 当前滚动行偏移 */
    private int scrollRow = 0;

    // ==================== 搜索过滤相关 ====================
    /** 当前搜索关键字（小写） */
    private String searchText = "";
    /** 过滤后的配方 index 列表（memorizedRecipes 数组中的实际索引），null 表示不过滤 */
    private IntList filteredIndices;
    /** 外部搜索框引用，用于每帧检测文本变化 */
    private GTTextFieldWidget searchField;

    private final List<RecipeMemorySlot> slotWidgets = new ArrayList<>(VIEWPORT_SIZE);

    public RecipeMemoryGridWidget(CraftingRecipeMemory memory) {
        this.memory = memory;
        this.lockedStart = CraftingRecipeMemory.TEMP_RECIPE_SLOTS;
        this.lockedCapacity = memory.getLockedRecipeSlots();

        size(COLS * 18 + SCROLLBAR_WIDTH, VISIBLE_ROWS * 18);

        // 创建固定数量的 RecipeMemorySlot，使用动态 index
        for (int i = 0; i < VIEWPORT_SIZE; i++) {
            final int slotOffset = i;
            int col = i % COLS;
            int row = i / COLS;

            RecipeMemorySlot slot = new RecipeMemorySlot(memory, -1)
                    .dynamicIndex(() -> getIndexForSlot(slotOffset));
            slot.background(GTGuiTextures.SLOT);
            slot.pos(col * 18, row * 18);
            slot.size(18, 18);
            slotWidgets.add(slot);
            child(slot);
        }
    }

    /** 设置外部搜索框引用（由 MetaTileEntityWorkbench 布局时调用） */
    public RecipeMemoryGridWidget setSearchField(GTTextFieldWidget searchField) {
        this.searchField = searchField;
        return this;
    }

    /**
     * 根据 viewport 中的 slot 偏移量，计算对应的 memorizedRecipes 实际 index。
     * 如果有搜索过滤，则映射到过滤结果列表；否则映射到整个锁定区。
     */
    private int getIndexForSlot(int slotOffset) {
        int flatIndex = scrollRow * COLS + slotOffset;
        if (filteredIndices != null) {
            if (flatIndex >= filteredIndices.size()) return -1;
            return filteredIndices.getInt(flatIndex);
        } else {
            if (flatIndex >= lockedCapacity) return -1;
            return lockedStart + flatIndex;
        }
    }

    /** 获取当前可见的配方总数（过滤后） */
    private int getFilteredCount() {
        return filteredIndices != null ? filteredIndices.size() : lockedCapacity;
    }

    /** 获取总行数 */
    private int getTotalRows() {
        return (getFilteredCount() + COLS - 1) / COLS;
    }

    /** 获取最大可滚动行偏移 */
    private int getMaxScrollRow() {
        return Math.max(0, getTotalRows() - VISIBLE_ROWS);
    }

    // ==================== 搜索过滤 ====================

    /**
     * 更新搜索过滤。根据当前搜索文本重建过滤后的索引列表。
     */
    private void updateFilter(String newText) {
        this.searchText = newText == null ? "" : newText.toLowerCase();
        this.scrollRow = 0;

        if (searchText.isEmpty()) {
            filteredIndices = null;
        } else {
            filteredIndices = new IntArrayList();
            for (int i = 0; i < lockedCapacity; i++) {
                int actualIndex = lockedStart + i;
                MemorizedRecipe recipe = memory.getRecipeAtIndex(actualIndex);
                if (recipe != null) {
                    ItemStack result = recipe.getRecipeResult();
                    if (!result.isEmpty() && result.getDisplayName().toLowerCase().contains(searchText)) {
                        filteredIndices.add(actualIndex);
                    }
                }
            }
        }

        // 确保 scrollRow 不超出范围
        int maxRow = getMaxScrollRow();
        if (scrollRow > maxRow) {
            scrollRow = maxRow;
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // 每帧检测外部搜索框文本变化，触发过滤更新
        if (searchField != null) {
            String currentText = searchField.getText().toLowerCase();
            if (!currentText.equals(searchText)) {
                updateFilter(currentText);
            }
        }
        // 搜索时实时刷新过滤（新配方可能被锁定或解锁）
        if (filteredIndices != null) {
            updateFilter(searchText);
        }
    }

    // ==================== 鼠标滚轮滚动 ====================

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        int delta = scrollDirection == UpOrDown.UP ? -1 : 1;
        int newRow = Math.max(0, Math.min(scrollRow + delta, getMaxScrollRow()));
        if (newRow != scrollRow) {
            scrollRow = newRow;
        }
        return true;
    }

    // ==================== 滚动条渲染 ====================

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        // 绘制滚动条轨道
        int trackX = COLS * 18;
        int trackY = 0;
        int trackHeight = VISIBLE_ROWS * 18;
        GuiDraw.drawRect(trackX, trackY, SCROLLBAR_WIDTH, trackHeight, 0xFF2A2A2A);

        // 绘制滚动条滑块
        int totalRows = getTotalRows();
        if (totalRows > VISIBLE_ROWS) {
            float ratio = (float) VISIBLE_ROWS / totalRows;
            int thumbHeight = Math.max(8, (int) (trackHeight * ratio));
            int maxScroll = getMaxScrollRow();
            float scrollProgress = maxScroll > 0 ? (float) scrollRow / maxScroll : 0;
            int thumbY = trackY + (int) ((trackHeight - thumbHeight) * scrollProgress);
            GuiDraw.drawRect(trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight, 0xFF808080);
        } else {
            // 不需要滚动时，滑块填满整个轨道
            GuiDraw.drawRect(trackX, trackY, SCROLLBAR_WIDTH, trackHeight, 0xFF505050);
        }
    }
}
