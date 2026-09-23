package gregtech.common.covers;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithLeisureUI;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.inventory.handlers.SingleItemStackHandler;
import gregtech.common.inventory.handlers.ToolItemStackHandler;
import gregtech.common.metatileentities.workbench.CraftingRecipeLogic;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory;
import gregtech.common.metatileentities.workbench.IWorkbenchHolder;
import gregtech.common.mui.widget.workbench.WorkbenchUI;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SidedPosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * 工作台覆盖板：直接安装在机器 / 管道侧面，右键即可打开与工作台机器完全一致的合成站界面。
 * <p>
 * 界面由 {@link WorkbenchUI} 构建，与 {@link gregtech.common.metatileentities.workbench.MetaTileEntityWorkbench}
 * 共用同一套逻辑，因此 3x3 合成、库存取料、工具槽、内部库存、配方记忆与合成链功能都与工作台机器保持一致。
 */
public class CoverCraftingTable extends CoverBase
                                 implements CoverWithUI, CoverWithLeisureUI, ITickable, IWorkbenchHolder {

    /** BFS 库存扫描的最大搜索方块数量 */
    private static final int MAX_SCAN_RANGE = 24;
    /** BFS 库存扫描定期执行间隔（tick），用于检测远处库存变化 */
    private static final int SCAN_INTERVAL = 20;

    private final ItemStackHandler craftingGrid = new SingleItemStackHandler(9);
    private final ItemStackHandler internalInventory = new ItemStackHandler(18);
    private final ItemStackHandler toolInventory = new ToolItemStackHandler(9);
    private final CraftingRecipeMemory recipeMemory = new CraftingRecipeMemory(
            CraftingRecipeMemory.TEMP_RECIPE_SLOTS + CraftingRecipeMemory.LOCKED_RECIPE_SLOTS, this.craftingGrid);
    /** 周围可访问的库存，客户端为占位实现（槽位数由服务端同步） */
    private ItemHandlerList connectedInventory = new ItemHandlerList(Collections.emptyList());
    private ItemHandlerList combinedInventory;
    /** 标记缓存的 connectedInventory/combinedInventory 是否需要重建 */
    private boolean inventoryCacheDirty = true;
    private CraftingRecipeLogic recipeLogic = null;
    /** One-shot server warmup to move lazy UI init cost out of first right-click. */
    private boolean uiWarmupDone = false;
    private int itemsCrafted = 0;

    public CoverCraftingTable(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                              @NotNull EnumFacing attachedSide) {
        super(definition, coverableView, attachedSide);
        rebuildInventoryList();
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return true;
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.CRAFTING.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public void onRemoval() {
        dropInventoryContents(craftingGrid);
        dropInventoryContents(internalInventory);
        dropInventoryContents(toolInventory);
    }

    @Override
    public @NotNull EnumActionResult onRightClick(@NotNull EntityPlayer player, @NotNull EnumHand hand,
                                                  @NotNull CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            openUI((EntityPlayerMP) player);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public @NotNull EnumActionResult onScrewdriverClick(@NotNull EntityPlayer player, @NotNull EnumHand hand,
                                                        @NotNull CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            openUI((EntityPlayerMP) player);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void update() {
        if (getWorld().isRemote) return;

        if (!uiWarmupDone) {
            // Warm up expensive lazy state once on server tick to reduce first-open UI latency.
            getAvailableHandlers();
            initializeRecipeLogic(false);
            uiWarmupDone = true;
        }

        // 定期重扫描，使用坐标哈希错开执行时机
        long time = getWorld().getTotalWorldTime();
        if (time % SCAN_INTERVAL == Math.abs(getPos().hashCode()) % SCAN_INTERVAL) {
            int oldSlots = this.connectedInventory.getSlots();
            this.inventoryCacheDirty = true;
            getAvailableHandlers();
            int newSlots = this.connectedInventory.getSlots();
            getCraftingRecipeLogic().updateInventory(getAvailableHandlers());
            // 只在 slot 数量变化时才发包给客户端，避免不必要的网络开销
            if (newSlots != oldSlots) {
                writeCustomData(GregtechDataCodes.UPDATE_CLIENT_HANDLER, buf -> buf.writeVarInt(newSlots));
            }
        }
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        return WorkbenchUI.build(this, guiSyncManager);
    }

    /**
     * 机器主界面侧边按钮栏中的按钮：点击后在机器界面内部展开 / 收起工作台子面板，
     * 这样单方块机器不必再单独开一个界面就能合成。
     */
    @Override
    public @NotNull IWidget initUILeisure(@NotNull GuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                          int index) {
        IPanelHandler panelHandler = guiSyncManager.syncedPanel("workbench_cover_panel" + index, true,
                (syncManager, panel) -> WorkbenchUI.buildEmbeddedPanel(this, syncManager, guiSyncManager, index));

        return new ButtonWidget<>()
                .size(18, 18)
                .overlay(new ItemDrawable(getWorkbenchIcon()).asIcon().size(16))
                .addTooltipLine(IKey.lang(getWorkbenchTitleKey()) + " 方位：" +
                        EnumFacing.byIndex(index).getName())
                .onMousePressed(mouseButton -> {
                    if (panelHandler.isPanelOpen()) {
                        panelHandler.closePanel();
                    } else {
                        panelHandler.openPanel();
                    }
                    return true;
                });
    }

    // ==================== IWorkbenchHolder ====================

    @Override
    public @NotNull CraftingRecipeLogic getCraftingRecipeLogic() {
        initializeRecipeLogic(true);
        return this.recipeLogic;
    }

    @Override
    public CraftingRecipeMemory getRecipeMemory() {
        return recipeMemory;
    }

    @Override
    public ItemStackHandler getCraftingGrid() {
        return craftingGrid;
    }

    @Override
    public ItemStackHandler getInternalInventory() {
        return internalInventory;
    }

    @Override
    public ItemStackHandler getToolInventory() {
        return toolInventory;
    }

    @Override
    public ItemHandlerList getConnectedInventory() {
        return connectedInventory;
    }

    @Override
    public ItemHandlerList getAvailableHandlers() {
        if (!getWorld().isRemote && inventoryCacheDirty) {
            rebuildInventoryCache();
        }
        return this.combinedInventory;
    }

    @Override
    public void refreshInventoryCache() {
        this.inventoryCacheDirty = true;
        getAvailableHandlers();
    }

    @Override
    public int getItemsCrafted() {
        return this.itemsCrafted;
    }

    @Override
    public void setItemsCrafted(int itemsCrafted) {
        this.itemsCrafted = itemsCrafted;
    }

    @Override
    public ItemStack getWorkbenchIcon() {
        return getPickItem();
    }

    @Override
    public String getWorkbenchPanelName() {
        return getDefinition().getResourceLocation().getPath();
    }

    @Override
    public String getWorkbenchTitleKey() {
        return "metaitem.cover.crafting.name";
    }

    // ==================== logic / inventory ====================

    private void initializeRecipeLogic(boolean syncClientHandlerSize) {
        Preconditions.checkState(getWorld() != null, "getCraftingRecipeLogic called too early");
        if (this.recipeLogic == null) {
            this.recipeLogic = new CraftingRecipeLogic(getWorld(), getAvailableHandlers(), getCraftingGrid());
            this.recipeLogic.setRecipeMemory(this.recipeMemory);
            if (syncClientHandlerSize && !getWorld().isRemote) {
                writeCustomData(GregtechDataCodes.UPDATE_CLIENT_HANDLER,
                        buf -> buf.writeVarInt(this.connectedInventory.getSlots()));
            }
        }
    }

    /** 用当前的三个库存重建合并库存列表 */
    private void rebuildInventoryList() {
        this.combinedInventory = new ItemHandlerList(Arrays.asList(
                this.internalInventory,
                this.toolInventory,
                this.connectedInventory));
    }

    /** 重建库存缓存（BFS + ItemHandlerList 构造），仅在结构变化时调用。 */
    private void rebuildInventoryCache() {
        this.inventoryCacheDirty = false;
        if (getWorld().isRemote) {
            // 客户端只使用服务端同步过来的槽位数
            rebuildInventoryList();
            return;
        }

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

        this.connectedInventory = new ItemHandlerList(handlers);
        rebuildInventoryList();
        this.recipeLogicUpdateInventory();
    }

    private void recipeLogicUpdateInventory() {
        if (this.recipeLogic != null) {
            this.recipeLogic.updateInventory(this.combinedInventory);
        }
    }

    /** 读取服务端同步过来的周围库存槽位数 */
    private void readHandler(PacketBuffer buf) {
        int connected = buf.readVarInt();
        this.connectedInventory = new ItemHandlerList(Collections.singletonList(new ItemStackHandler(connected)));
        rebuildInventoryList();
        recipeLogicUpdateInventory();
    }

    // ==================== sync ====================

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.itemsCrafted);
        for (int i = 0; i < craftingGrid.getSlots(); i++) {
            NetworkUtils.writeItemStack(buf, craftingGrid.getStackInSlot(i));
        }
        this.recipeMemory.writeInitialSyncData(buf);
        // 使用已缓存的 connectedInventory，避免重复 BFS
        getAvailableHandlers();
        buf.writeVarInt(this.connectedInventory.getSlots());
    }

    @Override
    public void readInitialSyncData(@NotNull PacketBuffer buf) {
        super.readInitialSyncData(buf);
        this.itemsCrafted = buf.readInt();
        for (int i = 0; i < craftingGrid.getSlots(); i++) {
            craftingGrid.setStackInSlot(i, NetworkUtils.readItemStack(buf));
        }
        this.recipeMemory.receiveInitialSyncData(buf);
        readHandler(buf);
    }

    @Override
    public void readCustomData(int discriminator, @NotNull PacketBuffer buf) {
        super.readCustomData(discriminator, buf);
        if (discriminator == GregtechDataCodes.UPDATE_CLIENT_HANDLER) {
            readHandler(buf);
        }
    }

    // ==================== nbt ====================

    @Override
    public void writeToNBT(@NotNull NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setTag("CraftingGridInventory", craftingGrid.serializeNBT());
        tagCompound.setTag("ToolInventory", toolInventory.serializeNBT());
        tagCompound.setTag("InternalInventory", internalInventory.serializeNBT());
        tagCompound.setInteger("ItemsCrafted", itemsCrafted);
        tagCompound.setTag("RecipeMemory", recipeMemory.serializeNBT());
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        this.craftingGrid.deserializeNBT(tagCompound.getCompoundTag("CraftingGridInventory"));
        this.toolInventory.deserializeNBT(tagCompound.getCompoundTag("ToolInventory"));
        this.internalInventory.deserializeNBT(tagCompound.getCompoundTag("InternalInventory"));
        this.itemsCrafted = tagCompound.getInteger("ItemsCrafted");
        this.recipeMemory.deserializeNBT(tagCompound.getCompoundTag("RecipeMemory"));
        this.uiWarmupDone = false;
        this.inventoryCacheDirty = true;
    }
}
