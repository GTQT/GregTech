package gregtech.common.metatileentities.workbench;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.inventory.handlers.SingleItemStackHandler;
import gregtech.common.inventory.handlers.ToolItemStackHandler;
import gregtech.common.mui.widget.workbench.CraftingInputSlot;
import gregtech.common.mui.widget.workbench.CraftingOutputSlot;
import gregtech.common.mui.widget.workbench.InventoryViewHandler;
import gregtech.common.mui.widget.workbench.InventoryViewSyncHandler;
import gregtech.common.mui.widget.workbench.InventoryViewWidget;
import gregtech.common.mui.widget.workbench.RecipeMemoryGridWidget;
import gregtech.common.mui.widget.workbench.RecipeMemorySlot;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.block.SoundType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
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
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MetaTileEntityWorkbench extends MetaTileEntity {

    private static final IDrawable CHEST = new ItemDrawable(new ItemStack(Blocks.CHEST))
            .asIcon().size(16);

    /** BFS 库存扫描的最大搜索方块数量，可配置 */
    private static final int MAX_SCAN_RANGE = 24;

    private final IDrawable WORKSTATION = new ItemDrawable(getStackForm())
            .asIcon().size(16);

    private final ItemStackHandler craftingGrid = new SingleItemStackHandler(9);
    private final ItemStackHandler internalInventory = new GTItemStackHandler(this, 18);
    private final ItemStackHandler toolInventory = new ToolItemStackHandler(9);

    private ItemHandlerList combinedInventory;
    private ItemHandlerList connectedInventory;
    /** 标记缓存的 connectedInventory/combinedInventory 是否需要重建 */
    private boolean inventoryCacheDirty = true;

    private final CraftingRecipeMemory recipeMemory = new CraftingRecipeMemory(
            CraftingRecipeMemory.TEMP_RECIPE_SLOTS + CraftingRecipeMemory.LOCKED_RECIPE_SLOTS, this.craftingGrid);
    private CraftingRecipeLogic recipeLogic = null;
    /** One-shot server warmup to move lazy UI init cost out of first right-click. */
    private boolean uiWarmupDone = false;
    private int itemsCrafted = 0;

    public MetaTileEntityWorkbench(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityWorkbench(metaTileEntityId);
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.CRAFTING_TABLE.getParticleSprite(), getDefaultPaintingColor());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        int paintingColor = getPaintingColorForRendering();
        pipeline = ArrayUtils.add(pipeline, new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(paintingColor)));
        Textures.CRAFTING_TABLE.renderOriented(renderState, translation, pipeline, getFrontFacing());
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.itemsCrafted);
        for (int i = 0; i < craftingGrid.getSlots(); i++) {
            NetworkUtils.writeItemStack(buf, craftingGrid.getStackInSlot(i));
        }
        this.recipeMemory.writeInitialSyncData(buf);
        // 使用已缓存的 connectedInventory，避免重复 BFS
        if (this.connectedInventory == null) {
            computeConnectedInventory();
        }
        buf.writeVarInt(this.connectedInventory.getSlots());
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.itemsCrafted = buf.readInt();
        for (int i = 0; i < craftingGrid.getSlots(); i++) {
            craftingGrid.setStackInSlot(i, NetworkUtils.readItemStack(buf));
        }
        this.recipeMemory.receiveInitialSyncData(buf);
        this.connectedInventory = new ItemHandlerList(
                Collections.singletonList(new GTItemStackHandler(this, buf.readVarInt())));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("CraftingGridInventory", craftingGrid.serializeNBT());
        data.setTag("ToolInventory", toolInventory.serializeNBT());
        data.setTag("InternalInventory", internalInventory.serializeNBT());
        data.setInteger("ItemsCrafted", itemsCrafted);
        data.setTag("RecipeMemory", recipeMemory.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.craftingGrid.deserializeNBT(data.getCompoundTag("CraftingGridInventory"));
        this.toolInventory.deserializeNBT(data.getCompoundTag("ToolInventory"));
        this.internalInventory.deserializeNBT(data.getCompoundTag("InternalInventory"));
        this.itemsCrafted = data.getInteger("ItemsCrafted");
        this.recipeMemory.deserializeNBT(data.getCompoundTag("RecipeMemory"));
        this.uiWarmupDone = false;
        this.inventoryCacheDirty = true;
    }

    public IItemHandlerModifiable getAvailableHandlers() {
        if (!getWorld().isRemote && inventoryCacheDirty) {
            rebuildInventoryCache();
        }
        if (this.combinedInventory != null) {
            return this.combinedInventory;
        }
        // 首次调用或缓存尚未建立时，构建并缓存
        return rebuildInventoryCache();
    }

    /**
     * 重建库存缓存（BFS + ItemHandlerList 构造），仅在结构变化时调用。
     */
    private ItemHandlerList rebuildInventoryCache() {
        inventoryCacheDirty = false;
        ArrayList<IItemHandler> handlers = new ArrayList<>();
        handlers.add(this.internalInventory);
        handlers.add(this.toolInventory);
        if (getWorld().isRemote) {
            if (this.connectedInventory != null) {
                handlers.add(this.connectedInventory);
            }
        } else {
            handlers.add(computeConnectedInventory());
        }
        return this.combinedInventory = new ItemHandlerList(handlers);
    }

    /**
     * 使用 BFS 搜索周围可达的库存方块。
     * 搜索从工作台位置开始，通过有 IItemHandler 能力的方块级联扩展。
     * 最多搜索 {@link #MAX_SCAN_RANGE} 个方块（不含起始位置）。
     */
    private ItemHandlerList computeConnectedInventory() {
        ArrayList<IItemHandler> handlers = new ArrayList<>();
        // 用 IdentityHashSet 去重，防止同一个 IItemHandler 实例被多次添加（如大箱子的两个方块位置）
        Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        Queue<BlockPos> toCheck = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        toCheck.add(getPos());
        visited.add(getPos());

        while (!toCheck.isEmpty() && visited.size() <= MAX_SCAN_RANGE) {
            BlockPos current = toCheck.poll();
            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos neighbor = current.offset(facing);
                if (visited.contains(neighbor)) continue;
                if (visited.size() > MAX_SCAN_RANGE) break;
                visited.add(neighbor);

                TileEntity te = getWorld().getTileEntity(neighbor);
                if (te == null) continue;
                IItemHandler handler = te.getCapability(
                        CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                        facing.getOpposite());
                if (handler == null) continue;

                if (seenHandlers.add(handler)) {
                    handlers.add(handler);
                }
                // 通过有库存的方块继续扩展搜索
                toCheck.add(neighbor);
            }
        }

        return this.connectedInventory = new ItemHandlerList(handlers);
    }

    /** BFS 库存扫描定期执行间隔（tick），用于检测远处库存变化 */
    private static final int SCAN_INTERVAL = 20;

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (!uiWarmupDone) {
                // Warm up expensive lazy state once on server tick to reduce first-open UI latency.
                if (inventoryCacheDirty || this.connectedInventory == null || this.combinedInventory == null) {
                    rebuildInventoryCache();
                }
                initializeRecipeLogic(false);
                uiWarmupDone = true;
            }
            // 定期重扫描，使用坐标哈希错开执行时机（参考 Tom's Simple Storage）
            long time = getWorld().getTotalWorldTime();
            if (time % SCAN_INTERVAL == Math.abs(getPos().hashCode()) % SCAN_INTERVAL) {
                // 记录旧的 slot 数用于比较
                int oldSlots = this.connectedInventory != null ? this.connectedInventory.getSlots() : -1;
                // 标记缓存脏，下次 getAvailableHandlers() 时重建
                inventoryCacheDirty = true;
                IItemHandlerModifiable newHandlers = getAvailableHandlers();
                int newSlots = this.connectedInventory != null ? this.connectedInventory.getSlots() : 0;
                getCraftingRecipeLogic().updateInventory(newHandlers);
                // 只在 slot 数量变化时才发包给客户端，避免不必要的网络开销
                if (newSlots != oldSlots) {
                    writeCustomData(GregtechDataCodes.UPDATE_CLIENT_HANDLER, this::sendHandlerToClient);
                }
            }
        }
    }

    @Override
    public void onNeighborChanged() {
        // 邻居变化时立即标记缓存脏
        inventoryCacheDirty = true;
        getCraftingRecipeLogic().updateInventory(getAvailableHandlers());
        if (!getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.UPDATE_CLIENT_HANDLER, this::sendHandlerToClient);
        }
    }

    // this is called on client and server
    public @NotNull CraftingRecipeLogic getCraftingRecipeLogic() {
        initializeRecipeLogic(true);
        return this.recipeLogic;
    }

    private void initializeRecipeLogic(boolean syncClientHandlerSize) {
        Preconditions.checkState(getWorld() != null, "getRecipeResolver called too early");
        if (this.recipeLogic == null) {
            this.recipeLogic = new CraftingRecipeLogic(getWorld(), getAvailableHandlers(), getCraftingGrid());
            this.recipeLogic.setRecipeMemory(this.recipeMemory);
            if (syncClientHandlerSize && !getWorld().isRemote) {
                writeCustomData(GregtechDataCodes.UPDATE_CLIENT_HANDLER, this::sendHandlerToClient);
            }
        }
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        super.clearMachineInventory(itemBuffer);
        clearInventory(itemBuffer, internalInventory);
        clearInventory(itemBuffer, toolInventory);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        // 强制刷新库存缓存，确保 connectedInventory 是最新的
        // （GUI 关闭期间远处箱子内容可能已变化）
        inventoryCacheDirty = true;
        getAvailableHandlers();

        getCraftingRecipeLogic().updateCurrentRecipe();
        this.recipeLogic.clearSlotMap();

        syncManager.syncValue("recipe_logic", this.recipeLogic);
        syncManager.syncValue("recipe_memory", this.recipeMemory);

        var controller = new PagedWidget.Controller();
        syncManager.syncValue("page_controller", 0, new PagedWidgetSyncHandler(controller));

        return GTGuis.createPanel(this, 176, 224)
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
                                .overlay(WORKSTATION))
                        .child(new PageButton(1, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("gregtech.machine.workbench.tab.item_list"))
                                .addTooltipLine(IKey.lang("gregtech.machine.workbench.storage_note")
                                        .style(TextFormatting.DARK_GRAY))
                                .overlay(CHEST)))
                .child(IKey.lang(getMetaFullName())
                        .asWidget()
                        .top(7).left(7))
                .child(new PagedWidget<>()
                        .top(22)
                        .margin(7)
                        .widthRel(0.9f)
                        .controller(controller)
                        .coverChildrenHeight()
                        // workstation page
                        .addPage(Flow.column()
                                .name("crafting page")
                                .coverChildrenWidth()
                                .child(Flow.row()
                                        .name("crafting row")
                                        .coverChildrenHeight()
                                        .widthRel(1f)
                                        // crafting grid
                                        .child(createCraftingGrid())
                                        // crafting output slot
                                        .child(createCraftingOutput(guiData, syncManager))
                                        // recipe memory
                                        .child(createRecipeMemoryPanel(syncManager)))
                                // tool inventory
                                .child(createToolInventory(syncManager))
                                // internal inventory
                                .child(createInternalInventory(syncManager)))
                        // storage page
                        .addPage(createInventoryPage(syncManager)))
                .bindPlayerInventory();
    }

    private ModularSlot trackSlot(IItemHandler handler, int slot) {
        int offset = combinedInventory.getIndexOffset(handler);
        if (offset == -1) throw new NullPointerException("handler cannot be found");
        this.recipeLogic.updateSlotMap(offset, slot);
        return new ModularSlot(handler, slot);
    }

    public IWidget createToolInventory(PanelSyncManager syncManager) {
        var toolSlots = new SlotGroup("tool_slots", 9, -120, true);
        syncManager.registerSlotGroup(toolSlots);

        return SlotGroupWidget.builder()
                .row("XXXXXXXXX")
                .key('X', i -> new ItemSlot()
                        .background(GTGuiTextures.SLOT, GTGuiTextures.TOOL_SLOT_OVERLAY)
                        .slot(trackSlot(this.toolInventory, i)
                                .slotGroup(toolSlots)))
                .build().marginTop(2);
    }

    public IWidget createInternalInventory(PanelSyncManager syncManager) {
        var inventory = new SlotGroup("internal_slots", 9, -100, true);
        syncManager.registerSlotGroup(inventory);

        return SlotGroupWidget.builder()
                .row("XXXXXXXXX")
                .row("XXXXXXXXX")
                .key('X', i -> new ItemSlot()
                        .slot(trackSlot(this.internalInventory, i)
                                .slotGroup(inventory)))
                .build().marginTop(2);
    }

    public IWidget createCraftingGrid() {
        return SlotGroupWidget.builder()
                .matrix("XXX",
                        "XXX",
                        "XXX")
                .key('X', i -> CraftingInputSlot.create(this.recipeLogic, this.craftingGrid, i)
                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                            if (!init) {
                                this.recipeLogic.updateCurrentRecipe();
                            }
                        })
                        .background(GTGuiTextures.SLOT))
                .build();
    }

    public IWidget createCraftingOutput(PosGuiData guiData, PanelSyncManager syncManager) {
        var amountCrafted = new IntSyncValue(this::getItemsCrafted, this::setItemsCrafted);
        syncManager.syncValue("amount_crafted", amountCrafted);

        return Flow.column()
                .size(54)
                .child(new CraftingOutputSlot(amountCrafted, this)
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
                            this.recipeLogic.clearCraftingGrid();
                            return true;
                        }));
    }

    public IWidget createRecipeMemoryPanel(PanelSyncManager syncManager) {
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
                        .addPage(createTemporaryRecipeMemoryGrid())
                        .addPage(createLockedRecipeMemoryGrid(searchField)));
    }

    private IWidget createTemporaryRecipeMemoryGrid() {
        return SlotGroupWidget.builder()
                .matrix("XXX",
                        "XXX",
                        "XXX")
                .key('X', i -> new RecipeMemorySlot(this.recipeMemory, this.recipeMemory.getTemporaryRecipeIndex(i))
                        .background(GTGuiTextures.SLOT))
                .build().right(0);
    }

    private IWidget createLockedRecipeMemoryGrid(GTTextFieldWidget searchField) {
        return new RecipeMemoryGridWidget(this.recipeMemory)
                .setSearchField(searchField);
    }

    public IWidget createInventoryPage(PanelSyncManager syncManager) {
        if (this.connectedInventory.getSlots() == 0) {
            return Flow.column()
                    .name("inventory page - empty")
                    .leftRel(0.5f)
                    .padding(2)
                    .height(18 * InventoryViewWidget.ROWS)
                    .width(18 * InventoryViewWidget.COLS + 4)
                    .background(GTGuiTextures.DISPLAY);
        }

        // 虚拟滚动视图：固定 48 个 widget（8×6），通过 InventoryViewHandler 动态映射到实际 slot
        // 使用 Supplier 确保库存结构变化时（箱子放置/移除）viewHandler 始终引用最新的 connectedInventory
        var viewHandler = new InventoryViewHandler(
                () -> this.connectedInventory,
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

    public void sendHandlerToClient(PacketBuffer buffer) {
        buffer.writeVarInt(this.connectedInventory.getSlots());
    }

    public void readHandler(PacketBuffer buf) {
        int connected = buf.readVarInt();

        // set connected inventory
        this.connectedInventory = new ItemHandlerList(Collections.singletonList(new ItemStackHandler(connected)));

        // set combined inventory
        this.combinedInventory = new ItemHandlerList(Arrays.asList(
                this.internalInventory,
                this.toolInventory,
                this.connectedInventory));

        getCraftingRecipeLogic().updateInventory(this.combinedInventory);
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_CLIENT_HANDLER) {
            readHandler(buf);
        }
    }

    public int getItemsCrafted() {
        return this.itemsCrafted;
    }

    public void setItemsCrafted(int itemsCrafted) {
        this.itemsCrafted = itemsCrafted;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.workbench.tooltip1"));
        tooltip.add(I18n.format("gregtech.machine.workbench.tooltip2"));
    }

    public ItemStackHandler getCraftingGrid() {
        return craftingGrid;
    }

    public ItemStackHandler getToolInventory() {
        return toolInventory;
    }

    public CraftingRecipeMemory getRecipeMemory() {
        return recipeMemory;
    }

    @Override
    public boolean canPlaceCoverOnSide(@NotNull EnumFacing side) {
        return false;
    }

    @Override
    public boolean acceptsCovers() {
        return false;
    }

    @Override
    public boolean canRenderMachineGrid(@NotNull ItemStack mainHandStack, @NotNull ItemStack offHandStack) {
        return false;
    }

    @Override
    public boolean showToolUsages() {
        return false;
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return SoundType.WOOD;
    }
}
