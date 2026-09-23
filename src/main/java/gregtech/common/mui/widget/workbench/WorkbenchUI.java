package gregtech.common.mui.widget.workbench;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.workbench.CraftingRecipeLogic;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory;
import gregtech.common.metatileentities.workbench.IWorkbenchHolder;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.items.IItemHandler;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the crafting station (workbench) GUI.
 * <p>
 * The GUI is shared by {@code MetaTileEntityWorkbench} and by the crafting table cover, which both provide
 * their state through {@link IWorkbenchHolder}. Keeping a single implementation guarantees that the cover
 * behaves exactly like the workbench machine.
 */
public final class WorkbenchUI {

    private static final IDrawable CHEST = new ItemDrawable(new ItemStack(Blocks.CHEST))
            .asIcon().size(16);

    private WorkbenchUI() {}

    public static ModularPanel build(@NotNull IWorkbenchHolder holder, @NotNull PanelSyncManager syncManager) {
        prepare(holder, syncManager);

        var controller = new PagedWidget.Controller();
        syncManager.syncValue("page_controller", 0, new PagedWidgetSyncHandler(controller));

        IDrawable workstation = new ItemDrawable(holder.getWorkbenchIcon()).asIcon().size(16);

        return GTGuis.createPanel(holder.getWorkbenchPanelName(), 176, 224)
                .child(Flow.row()
                        .name("tab row")
                        .widthRel(1f)
                        .leftRel(0.5f)
                        .margin(3, 0)
                        .coverChildrenHeight()
                        .topRel(0f, 3, 1f)
                        .child(new PageButton(0, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("gregtech.machine.workbench.tab.workbench"))
                                .overlay(workstation))
                        .child(new PageButton(1, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("gregtech.machine.workbench.tab.item_list"))
                                .addTooltipLine(IKey.lang("gregtech.machine.workbench.storage_note")
                                        .style(TextFormatting.DARK_GRAY))
                                .overlay(CHEST)))
                .child(IKey.lang(holder.getWorkbenchTitleKey())
                        .asWidget()
                        .top(7).left(7))
                .child(new PagedWidget<>()
                        .top(22)
                        .margin(7)
                        .widthRel(0.9f)
                        .controller(controller)
                        .coverChildrenHeight()
                        // workstation page
                        .addPage(createWorkstationPage(holder, syncManager))
                        // storage page
                        .addPage(createInventoryPage(holder, syncManager)))
                .bindPlayerInventory();
    }

    /**
     * 构建可嵌入机器界面的工作台子面板：只包含合成页，玩家背包槽位由机器主界面提供。
     *
     * @param mainSyncManager 机器主面板的同步管理器，用于把玩家背包槽位组接进子面板
     * @param index           覆盖板所在面的索引，用于保证面板名唯一
     */
    public static ModularPanel buildEmbeddedPanel(@NotNull IWorkbenchHolder holder,
                                                  @NotNull PanelSyncManager syncManager,
                                                  @NotNull PanelSyncManager mainSyncManager, int index) {
        prepare(holder, syncManager);

        // 合成输出槽的 shift 连续合成会遍历本面板注册的槽位组，把主面板的玩家背包组也登记进来，
        // 机器界面里的合成产物才能像工作台那样回收到玩家背包（槽位本身仍属于主面板，不会重复添加）。
        SlotGroup playerSlotGroup = mainSyncManager.getSlotGroup(PlayerSlotGroup.NAME);
        if (playerSlotGroup != null) {
            syncManager.registerSlotGroup(playerSlotGroup);
        }

        // 直接放置合成页：它自身同时 cover 宽高，因此不依赖父级尺寸（外面再套一层 coverChildren 容器会触发
        // MUI 的 "Can't cover children when all children depend on their parent"）。
        // 面板右上角是关闭按钮，内容整体下移，避免遮挡配方记忆页签。
        return GTGuis.createPopupPanel("workbench_cover_" + index, 176, 168)
                .child(IKey.lang(holder.getWorkbenchTitleKey()).asWidget().pos(5, 5))
                .child(createWorkstationPage(holder, syncManager).top(30).left(7));
    }

    /**
     * 刷新连接库存缓存并注册两个核心同步器，主面板与内嵌子面板都必须先调用它。
     */
    private static void prepare(@NotNull IWorkbenchHolder holder, @NotNull PanelSyncManager syncManager) {
        // Force the connected inventory cache to refresh, since the contents of remote inventories may
        // have changed while the GUI was closed.
        holder.refreshInventoryCache();

        CraftingRecipeLogic recipeLogic = holder.getCraftingRecipeLogic();
        recipeLogic.updateCurrentRecipe();
        recipeLogic.clearSlotMap();

        syncManager.syncValue("recipe_logic", recipeLogic);
        syncManager.syncValue("recipe_memory", holder.getRecipeMemory());
    }

    /**
     * 工作台页：3x3 合成网格 + 输出槽 + 配方记忆 + 工具槽 + 内部库存。
     * <p>
     * 宽高都由子控件决定，因此既能作为 {@link PagedWidget} 的一页，也能直接放进内嵌子面板。
     */
    public static Flow createWorkstationPage(@NotNull IWorkbenchHolder holder,
                                             @NotNull PanelSyncManager syncManager) {
        return Flow.column()
                .name("crafting page")
                .coverChildrenWidth()
                .coverChildrenHeight()
                .child(Flow.row()
                        .name("crafting row")
                        .coverChildrenHeight()
                        .widthRel(1f)
                        // crafting grid
                        .child(createCraftingGrid(holder))
                        // crafting output slot
                        .child(createCraftingOutput(holder, syncManager))
                        // recipe memory
                        .child(createRecipeMemoryPanel(holder, syncManager)))
                // tool inventory
                .child(createToolInventory(holder, syncManager))
                // internal inventory
                .child(createInternalInventory(holder, syncManager));
    }

    private static ModularSlot trackSlot(IWorkbenchHolder holder, IItemHandler handler, int slot) {
        int offset = holder.getAvailableHandlers().getIndexOffset(handler);
        if (offset == -1) throw new NullPointerException("handler cannot be found");
        holder.getCraftingRecipeLogic().updateSlotMap(offset, slot);
        return new ModularSlot(handler, slot);
    }

    public static IWidget createToolInventory(IWorkbenchHolder holder, PanelSyncManager syncManager) {
        var toolSlots = new SlotGroup("tool_slots", 9, -120, true);
        syncManager.registerSlotGroup(toolSlots);

        return SlotGroupWidget.builder()
                .row("XXXXXXXXX")
                .key('X', i -> new ItemSlot()
                        .background(GTGuiTextures.SLOT, GTGuiTextures.TOOL_SLOT_OVERLAY)
                        .slot(trackSlot(holder, holder.getToolInventory(), i)
                                .slotGroup(toolSlots)))
                .build().marginTop(2);
    }

    public static IWidget createInternalInventory(IWorkbenchHolder holder, PanelSyncManager syncManager) {
        var inventory = new SlotGroup("internal_slots", 9, -100, true);
        syncManager.registerSlotGroup(inventory);

        return SlotGroupWidget.builder()
                .row("XXXXXXXXX")
                .row("XXXXXXXXX")
                .key('X', i -> new ItemSlot()
                        .slot(trackSlot(holder, holder.getInternalInventory(), i)
                                .slotGroup(inventory)))
                .build().marginTop(2);
    }

    public static IWidget createCraftingGrid(IWorkbenchHolder holder) {
        CraftingRecipeLogic recipeLogic = holder.getCraftingRecipeLogic();
        return SlotGroupWidget.builder()
                .matrix("XXX",
                        "XXX",
                        "XXX")
                .key('X', i -> CraftingInputSlot.create(recipeLogic, holder.getCraftingGrid(), i)
                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                            if (!init) {
                                recipeLogic.updateCurrentRecipe();
                            }
                        })
                        .background(GTGuiTextures.SLOT))
                .build();
    }

    public static IWidget createCraftingOutput(IWorkbenchHolder holder, PanelSyncManager syncManager) {
        var amountCrafted = new IntSyncValue(holder::getItemsCrafted, holder::setItemsCrafted);
        syncManager.syncValue("amount_crafted", amountCrafted);

        return Flow.column()
                .size(54)
                .child(new CraftingOutputSlot(amountCrafted, holder)
                        .marginTop(18)
                        .background(GTGuiTextures.SLOT.asIcon().size(22))
                        .marginBottom(4))
                .child(IKey.dynamic(() -> TextFormattingUtil.formatLongToCompactString(amountCrafted.getIntValue(), 5))
                        .alignment(Alignment.Center)
                        .asWidget().widthRel(1f))
                .child(new ButtonWidget<>()
                        .margin(2)
                        .size(8)
                        .posRel(Alignment.TopLeft)
                        .background(GTGuiTextures.BUTTON_CLEAR_GRID)
                        .addTooltipLine(IKey.lang("gregtech.machine.workbench.clear_grid"))
                        .disableHoverBackground()
                        .onMousePressed(mouseButton -> {
                            holder.getCraftingRecipeLogic().clearCraftingGrid();
                            return true;
                        }));
    }

    public static IWidget createRecipeMemoryPanel(IWorkbenchHolder holder, PanelSyncManager syncManager) {
        var memoryController = new PagedWidget.Controller();
        var memorySyncHandler = new PagedWidgetSyncHandler(memoryController);
        syncManager.syncValue("recipe_memory_page_controller", 0, memorySyncHandler);

        // 锁定配方搜索框（纯客户端过滤，不需要服务端同步）
        var searchField = new GTTextFieldWidget()
                .setMaxLength(64)
                .value(new StringValue(""));
        searchField.size(18 * 3 - 24 - 2, 12);

        // 配方记忆切换按钮（临时/锁定）+ 搜索框
        return Flow.column()
                .right(0)
                .top(-15)
                .coverChildrenWidth()
                .child(Flow.row()
                        .name("recipe memory tabs")
                        .width(18 * 3)
                        .coverChildrenHeight()
                        .marginBottom(1)
                        .child(new ButtonWidget<>()
                                .size(12)
                                .overlay(IKey.str("T").asIcon().size(10))
                                .addTooltipLine(IKey.str("Temporary Recipes"))
                                .onMousePressed(mouseButton -> {
                                    memorySyncHandler.setPage(0);
                                    return true;
                                }))
                        .child(new ButtonWidget<>()
                                .size(12)
                                .overlay(GTGuiTextures.RECIPE_LOCK_WHITE.asIcon().size(10))
                                .addTooltipLine(IKey.str("Locked Recipes"))
                                .onMousePressed(mouseButton -> {
                                    memorySyncHandler.setPage(1);
                                    return true;
                                }))
                        .child(searchField))
                .child(new PagedWidget<>()
                        .controller(memoryController)
                        .coverChildrenWidth()
                        .coverChildrenHeight()
                        .addPage(createTemporaryRecipeMemoryGrid(holder))
                        .addPage(createLockedRecipeMemoryGrid(holder, searchField)));
    }

    private static IWidget createTemporaryRecipeMemoryGrid(IWorkbenchHolder holder) {
        CraftingRecipeMemory recipeMemory = holder.getRecipeMemory();
        return SlotGroupWidget.builder()
                .matrix("XXX",
                        "XXX",
                        "XXX")
                .key('X', i -> new RecipeMemorySlot(recipeMemory, recipeMemory.getTemporaryRecipeIndex(i))
                        .background(GTGuiTextures.SLOT))
                .build().right(0);
    }

    private static IWidget createLockedRecipeMemoryGrid(IWorkbenchHolder holder, GTTextFieldWidget searchField) {
        return new RecipeMemoryGridWidget(holder.getRecipeMemory())
                .setSearchField(searchField);
    }

    public static IWidget createInventoryPage(IWorkbenchHolder holder, PanelSyncManager syncManager) {
        // 注意：这里始终构建完整的虚拟滚动视图，即使当前没有连接任何库存。
        // 客户端缓存的槽位数可能比服务端落后一个扫描周期，如果按槽位数切换页面结构，
        // 客户端与服务端的 Container 槽位数量就会不一致。固定结构可以彻底避免这种不同步。
        // 虚拟滚动视图：固定 48 个 widget（8×6），通过 InventoryViewHandler 动态映射到实际 slot
        // 使用 Supplier 确保库存结构变化时（箱子放置/移除）viewHandler 始终引用最新的 connectedInventory
        var viewHandler = new InventoryViewHandler(
                holder::getConnectedInventory,
                InventoryViewWidget.VIEWPORT_SIZE,
                InventoryViewWidget.COLS);

        // 搜索文本同步：客户端输入 → 服务端过滤 → slot 映射更新 → Container 自动同步 slot 内容
        var searchSyncValue = new StringSyncValue(
                viewHandler::getSearchText,
                viewHandler::setSearchText);
        syncManager.syncValue("inventory_search", searchSyncValue);

        var viewSyncHandler = new InventoryViewSyncHandler(
                viewHandler,
                InventoryViewWidget.VIEWPORT_SIZE,
                InventoryViewWidget.COLS);
        syncManager.syncValue("inventory_view", viewSyncHandler);

        var connected = new SlotGroup("connected_inventory", InventoryViewWidget.COLS, true)
                .setAllowSorting(false);
        syncManager.registerSlotGroup(connected);

        var viewWidget = new InventoryViewWidget()
                .syncHandler(viewSyncHandler)
                .buildContent(viewHandler, connected, searchSyncValue);

        return Flow.column()
                .name("inventory page")
                .padding(2)
                .leftRel(0.5f)
                .coverChildren()
                .background(GTGuiTextures.DISPLAY)
                .child(viewWidget);
    }
}
