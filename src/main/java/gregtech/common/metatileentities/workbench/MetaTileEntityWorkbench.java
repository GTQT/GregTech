package gregtech.common.metatileentities.workbench;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.inventory.handlers.SingleItemStackHandler;
import gregtech.common.inventory.handlers.ToolItemStackHandler;
import gregtech.common.mui.widget.workbench.WorkbenchUI;

import net.minecraft.block.SoundType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
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
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MetaTileEntityWorkbench extends MetaTileEntity implements IWorkbenchHolder {

    /** BFS 库存扫描的最大搜索方块数量，可配置 */
    private static final int MAX_SCAN_RANGE = 24;
    /** BFS 库存扫描定期执行间隔（tick），用于检测远处库存变化 */
    private static final int SCAN_INTERVAL = 20;
    private final ItemStackHandler craftingGrid = new SingleItemStackHandler(9);
    private final ItemStackHandler internalInventory = new GTItemStackHandler(this, 18);
    private final ItemStackHandler toolInventory = new ToolItemStackHandler(9);
    private final CraftingRecipeMemory recipeMemory = new CraftingRecipeMemory(
            CraftingRecipeMemory.TEMP_RECIPE_SLOTS + CraftingRecipeMemory.LOCKED_RECIPE_SLOTS, this.craftingGrid);
    private ItemHandlerList combinedInventory;
    private ItemHandlerList connectedInventory;
    /** 标记缓存的 connectedInventory/combinedInventory 是否需要重建 */
    private boolean inventoryCacheDirty = true;
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

    @Override
    public ItemHandlerList getAvailableHandlers() {
        if (!getWorld().isRemote && inventoryCacheDirty) {
            rebuildInventoryCache();
        }
        if (this.combinedInventory != null) {
            return this.combinedInventory;
        }
        // 首次调用或缓存尚未建立时，构建并缓存
        return rebuildInventoryCache();
    }

    @Override
    public void refreshInventoryCache() {
        this.inventoryCacheDirty = true;
        getAvailableHandlers();
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
     * 使用 BFS 搜索周围可达的库存方块。 搜索从工作台位置开始，通过有 IItemHandler 能力的方块级联扩展。 最多搜索 {@link #MAX_SCAN_RANGE} 个方块（不含起始位置）。
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
    @Override
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
        return WorkbenchUI.build(this, syncManager);
    }

    @Override
    public String getWorkbenchPanelName() {
        return metaTileEntityId.getPath();
    }

    @Override
    public String getWorkbenchTitleKey() {
        return getMetaFullName();
    }

    @Override
    public ItemStack getWorkbenchIcon() {
        return getStackForm();
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

    @Override
    public int getItemsCrafted() {
        return this.itemsCrafted;
    }

    @Override
    public void setItemsCrafted(int itemsCrafted) {
        this.itemsCrafted = itemsCrafted;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.workbench.tooltip1"));
        tooltip.add(I18n.format("gregtech.machine.workbench.tooltip2"));
    }

    @Override
    public ItemStackHandler getCraftingGrid() {
        return craftingGrid;
    }

    @Override
    public ItemStackHandler getToolInventory() {
        return toolInventory;
    }

    @Override
    public ItemStackHandler getInternalInventory() {
        return internalInventory;
    }

    @Override
    public ItemHandlerList getConnectedInventory() {
        return connectedInventory;
    }

    @Override
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
