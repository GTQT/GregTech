package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import static gtqt.api.util.AE2PatternCompat.isFluidDrop;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.util.item.AEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import appeng.api.networking.crafting.IMultiplePatternPushable;
import gtqt.common.items.behaviors.ProgrammableCircuit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static gtqt.api.util.AE2PatternCompat.getFluidStack;

/**
 * 可编程样板总成 — 带缓冲区池机制。
 * <p>
 * 核心机制（移植自 Programmable-Hatches-Mod 的 BufferedDualInputHatch）：
 * <ul>
 *   <li>24 个共享缓冲区（PatternBuffer），每个缓冲区有独立的物品槽、流体槽和虚拟电路槽</li>
 *   <li>AE 推送样板材料时，相同物品组合进入同一个缓冲区，不同组合才分配新缓冲区</li>
 *   <li>所有缓冲区满时 isBusy() 返回 true，AE 暂停推送（阻挡模式）</li>
 *   <li>多方块配方系统通过 registerAbilities 获取所有非空缓冲区的 DualHandler 进行独立匹配</li>
 *   <li>可编程电路适配：推送的物品中如果有可编程电路，自动解包并设置到缓冲区的虚拟电路槽</li>
 * </ul>
 */
public class MetaTileEntityMEPatternProvider extends MetaTileEntityAECraftingPart
        implements IMultiplePatternPushable, IMEPatternProviderPart {

    // ==================== 缓冲区池常量 ====================
    public static final int BUFFER_COUNT = 24;

    // ==================== 缓冲区池 ====================
    private List<PatternBuffer> bufferPool;

    // ==================== 固定参数 ====================
    private static final int PATTERN_SLOTS = 36;
    private static final int TANK_COUNT = 6;
    private static final int TANK_CAPACITY = Integer.MAX_VALUE;
    // 缓冲区配方消耗后延迟释放的 tick 数（防止不同配方抢占同一缓冲区）
    private static final int DEFAULT_UNLOCK_DELAY = 10;
    // 缓冲区物品槽堆叠上限
    private static final int ITEM_STACK_LIMIT = Integer.MAX_VALUE;
    // 缓冲区额外电路槽数量（移植自 PH-Mod 的 v=4）
    private static final int CIRCUIT_SLOT_COUNT = 4;

    public MetaTileEntityMEPatternProvider(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 5, false);
        patternDetails = new ArrayList<>(Collections.nCopies(PATTERN_SLOTS, null));
        initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEPatternProvider(metaTileEntityId);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.patternSlot = new ItemStackHandler(PATTERN_SLOTS) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return stack.getItem() instanceof ICraftingPatternItem;
            }

            @Override
            protected void onContentsChanged(int slot) {
                setNeedPatternSync(true);
                setPatternDetails();
            }
        };
        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                java.util.Arrays.asList(this.importItems, this.circuitInventory));

        dualHandler = new DualHandler(
                this.actualImportItems,
                getImportFluids(),
                isExportHatch);

        // 初始化缓冲区池
        initBufferPool();
    }

    /**
     * 初始化缓冲区池，创建固定数量的缓冲区实例。
     * 注意：由于父类 MetaTileEntity 构造函数会先于子类字段初始化器执行，
     * 此方法在首次调用时 bufferPool 可能为 null，需要在此处创建列表。
     */
    private void initBufferPool() {
        bufferPool = new ArrayList<>();
        for (int i = 0; i < BUFFER_COUNT; i++) {
            bufferPool.add(new PatternBuffer(this, PATTERN_SLOTS, TANK_COUNT, TANK_CAPACITY));
        }
    }

    /**
     * 获取缓冲区池（供镜像和映射区访问）。
     */
    public List<PatternBuffer> getBufferPool() {
        return bufferPool;
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return dualHandler;
    }

    protected IFluidTank[] createTanks() {
        IFluidTank[] tanks = new IFluidTank[TANK_COUNT];
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = new NotifiableFluidTank(TANK_CAPACITY, null, isExportHatch);
        }
        return tanks;
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, PATTERN_SLOTS, null, false);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false, createTanks());
    }

    // ==================== 缓冲区能力注册 ====================

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        // 收集所有非空缓冲区，并按最近匹配时间降序排列（最近使用的在前）
        // 移植自 PH-Mod 的 PiorityBuffer 排序机制，优化配方缓存命中率
        List<PatternBuffer> activeBuffers = new ArrayList<>();
        for (PatternBuffer buffer : bufferPool) {
            if (!buffer.isEmpty()) {
                activeBuffers.add(buffer);
            }
        }
        activeBuffers.sort((a, b) -> Long.compare(b.getLastMatchTick(), a.getLastMatchTick()));
        for (PatternBuffer buffer : activeBuffers) {
            abilityInstances.add(buffer.getDualHandler());
        }
        // 如果所有缓冲区都空，注册一个空的 DualHandler 以保持兼容
        if (activeBuffers.isEmpty()) {
            abilityInstances.add(dualHandler);
        }
    }

    // ==================== AE2 推送与缓冲区分配 ====================

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails,
                               net.minecraft.inventory.InventoryCrafting inventoryCrafting) {
        if (!isActive()) {
            return false;
        }
        // 使用缓冲区池机制分配材料
        return pushToBuffer(inventoryCrafting);
    }

    @Override
    public boolean isBusy() {
        // 所有缓冲区都不为空时视为繁忙
        for (PatternBuffer buffer : bufferPool) {
            if (buffer.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从 InventoryCrafting 中提取物品和流体的签名（用于缓冲区匹配）。
     * 签名是物品类型列表（忽略数量）和流体类型列表（忽略数量）。
     */
    private BufferSignature extractSignature(net.minecraft.inventory.InventoryCrafting inventoryCrafting) {
        List<ItemStack> itemTypes = new ArrayList<>();
        List<FluidStack> fluidTypes = new ArrayList<>();
        ItemStack circuitStack = ItemStack.EMPTY;

        for (int i = 0; i < inventoryCrafting.getSizeInventory(); i++) {
            ItemStack stack = inventoryCrafting.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 处理假流体物品
            if (isFluidDrop(stack)) {
                FluidStack fluid = getFluidStack(stack);
                if (fluid != null) {
                    FluidStack type = fluid.copy();
                    type.amount = 0;
                    fluidTypes.add(type);
                    continue;
                }
            }

            // 处理可编程电路 — 不加入物品签名
            if (ProgrammableCircuit.getInstanceFor(stack) != null) {
                circuitStack = stack.copy();
                continue;
            }

            // 普通物品：记录类型（数量设为1）
            ItemStack type = stack.copy();
            type.setCount(1);
            itemTypes.add(type);
        }

        return new BufferSignature(itemTypes, fluidTypes, circuitStack);
    }

    /**
     * 找到一个与签名匹配的缓冲区，或者分配一个空的缓冲区。
     * 相同签名的物品进入同一个缓冲区（前提是缓冲区未满）。
     */
    @Nullable
    private PatternBuffer findOrAllocateBuffer(BufferSignature signature) {
        // 第一步：查找已有的与签名匹配且未满的缓冲区
        for (PatternBuffer buffer : bufferPool) {
            if (!buffer.isEmpty() && buffer.matchesSignature(signature) && !buffer.full()) {
                return buffer;
            }
        }
        // 第二步：分配一个空缓冲区
        for (PatternBuffer buffer : bufferPool) {
            if (buffer.isEmpty() && !buffer.isRecipeLocked()) {
                return buffer;
            }
        }
        // 所有缓冲区都满了或没有空闲缓冲区
        return null;
    }

    /**
     * 将 AE 推送的材料分配到缓冲区中。
     * 相同物品组合进入同一个缓冲区并累积数量，不同物品组合分配新缓冲区。
     * 实现类似 PH-Mod pushPatternMulti 的累积效果。
     */
    public boolean pushToBuffer(net.minecraft.inventory.InventoryCrafting inventoryCrafting) {
        BufferSignature signature = extractSignature(inventoryCrafting);
        PatternBuffer buffer = findOrAllocateBuffer(signature);
        if (buffer == null) {
            return false;
        }

        // 将签名记录到缓冲区（如果是空缓冲区则首次记录）
        if (buffer.isEmpty() && !buffer.isRecipeLocked()) {
            buffer.setSignature(signature);
        }

        // 将物品和流体实际插入缓冲区（累积模式：相同签名直接增加数量）
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); i++) {
            ItemStack stack = inventoryCrafting.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 处理假流体物品
            if (isFluidDrop(stack)) {
                FluidStack fluid = getFluidStack(stack);
                if (fluid != null) {
                    buffer.getFluidHandler().fill(fluid, true);
                    continue;
                }
            }

            // 处理可编程电路 — 解包并设置到缓冲区的虚拟电路槽
            if (ProgrammableCircuit.getInstanceFor(stack) != null) {
                if (ProgrammableCircuit.hasWrappedItem(stack)) {
                    // 有包裹物品：解包并设置为自定义电路
                    ProgrammableCircuit.getWrappedItem(stack).ifPresent(
                            wrappedItem -> buffer.setCustomCircuit(wrappedItem));
                } else {
                    // 空白可编程电路：清空缓冲区电路槽
                    buffer.clearCircuit();
                }
                continue;
            }

            // 普通物品：累积插入缓冲区的物品槽
            ItemStack toInsert = stack.copy();
            IItemHandlerModifiable itemHandler = buffer.getItemHandler();
            // 先尝试合并到已有相同物品的槽位
            for (int slot = 0; slot < itemHandler.getSlots() && !toInsert.isEmpty(); slot++) {
                toInsert = itemHandler.insertItem(slot, toInsert, false);
            }
        }

        // 标记缓冲区已绑定配方
        buffer.setRecipeLocked(true);

        // 记录配方匹配事件（移植自 PH-Mod 的 recordRecipe）
        long worldTick = getWorld() != null ? getWorld().getTotalWorldTime() : 0;
        int signatureHash = signature.hashCode();
        buffer.recordRecipeMatch(worldTick, signatureHash);

        return true;
    }

    // ==================== 批量推送（移植自 PH-Mod IMultiplePatternPushable）====================

    /**
     * 批量推送多份相同样板的材料到缓冲区中。
     * 由 AE2 的 CraftingCPUCluster.executeBatchPush() 调用。
     * 性能优化：只做一次签名提取和缓冲区匹配，然后按倍数直接插入。
     * 移植自 PH-Mod 的 classifyForce() 批量插入模式。
     *
     * @param patternDetails 合成样板详情
     * @param table          单份材料的 InventoryCrafting
     * @param maxTodo        最大允许推送的份数
     * @return [0] = 实际成功推送的份数
     */
    @Override
    public int[] pushPatternMulti(ICraftingPatternDetails patternDetails,
                                   net.minecraft.inventory.InventoryCrafting table,
                                   int maxTodo) {
        // 第一步：一次性提取签名（避免重复创建对象）
        BufferSignature signature = extractSignature(table);

        // 第二步：一次性查找或分配缓冲区
        PatternBuffer buffer = findOrAllocateBuffer(signature);
        if (buffer == null) return new int[]{0};

        // 第三步：通过 space() 计算缓冲区剩余容量，确定实际推送份数
        int effectiveMax;
        if (buffer.isEmpty()) {
            // 空缓冲区：设置签名后再计算
            buffer.setSignature(signature);
            effectiveMax = maxTodo;
        } else {
            effectiveMax = Math.min(maxTodo, buffer.space());
        }
        if (effectiveMax <= 0) {
            return new int[]{0};
        }

        // 第四步：按倍数直接插入物品和流体（不重复提取签名和查找缓冲区）
        for (int s = 0; s < table.getSizeInventory(); s++) {
            ItemStack ingredient = table.getStackInSlot(s);
            if (ingredient.isEmpty()) continue;

            // 检测 FakeFluid — 流体编码为假物品
            if (isFluidDrop(ingredient)) {
                FluidStack fluid = getFluidStack(ingredient);
                if (fluid != null) {
                    FluidStack toFill = fluid.copy();
                    toFill.amount *= effectiveMax;
                    buffer.getFluidHandler().fill(toFill, true);
                }
                continue;
            }

            // 处理可编程电路 — 解包并设置到缓冲区的虚拟电路槽
            if (ProgrammableCircuit.getInstanceFor(ingredient) != null) {
                if (ProgrammableCircuit.hasWrappedItem(ingredient)) {
                    // 有包裹物品：解包并设置为自定义电路
                    ProgrammableCircuit.getWrappedItem(ingredient).ifPresent(buffer::setCustomCircuit);
                } else {
                    // 空白可编程电路：清空缓冲区电路槽
                    buffer.clearCircuit();
                }
                continue;
            }

            // 普通物品 — 按倍数插入
            ItemStack toInsert = ingredient.copy();
            toInsert.setCount(ingredient.getCount() * effectiveMax);
            for (int slot = 0; slot < buffer.getItemHandler().getSlots(); slot++) {
                toInsert = buffer.getItemHandler().insertItem(slot, toInsert, false);
                if (toInsert.isEmpty()) break;
            }
        }

        // 标记缓冲区已绑定配方
        buffer.setRecipeLocked(true);

        // 记录配方匹配事件
        long worldTick = getWorld() != null ? getWorld().getTotalWorldTime() : 0;
        int signatureHash = signature.hashCode();
        buffer.recordRecipeMatch(worldTick, signatureHash);

        return new int[]{effectiveMax};
    }

    // ==================== update 主循环 ====================

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (isWorkingEnabled() && isOnline && shouldSyncME()) {
                if (isNeedPatternSync()) setNeedPatternSync(MEPatternChange());
                if (isExport()) returnToNet();
            }

            if (getOffsetTimer() % 5 == 0) {
                if (isExportHatch) {
                    pushItemsIntoNearbyHandlers(getFrontFacing());
                    pushFluidsIntoNearbyHandlers(getFrontFacing());
                } else {
                    pullItemsFromNearbyHandlers(getFrontFacing());
                    pullFluidsFromNearbyHandlers(getFrontFacing());
                }

                if (isAutoCollapse()) {
                    // 对缓冲区池中的非空缓冲区进行自动整理
                    for (PatternBuffer buffer : bufferPool) {
                        if (!buffer.isEmpty()) {
                            GTUtility.collapseInventorySlotContents(buffer.getItemHandler());
                        }
                    }
                }

                // 缓冲区清理：使用锁定/延迟解锁机制
                for (PatternBuffer buffer : bufferPool) {
                    // 先清理零数量的物品和流体（移植自 PH-Mod DualInvBuffer.updateSlots()）
                    buffer.updateSlots();
                    buffer.clearRecipeIfNeeded();
                }
            }
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.ME_BUFFER_HATCH_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    // ==================== 同步与 NBT ====================

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(getShowName().length());
        buf.writeString(getShowName());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        int size = buf.readInt();
        setShowName(buf.readString(size));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("Pattern", this.patternSlot.serializeNBT());

        data.setBoolean("BlockingEnabled", isBlockedMode());
        data.setBoolean("Export", isExport());

        data.setBoolean("useProxy", isUseProxy());
        data.setInteger("aeProxy_x", AEProxy_pos.getX());
        data.setInteger("aeProxy_y", AEProxy_pos.getY());
        data.setInteger("aeProxy_z", AEProxy_pos.getZ());

        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }

        data.setBoolean("hideInfo", isHideInfo());
        data.setString("showName", getShowName());

        // 序列化缓冲区池
        NBTTagList bufferListTag = new NBTTagList();
        for (PatternBuffer buffer : bufferPool) {
            bufferListTag.appendTag(buffer.writeToNBT());
        }
        data.setTag("BufferPool", bufferListTag);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.patternSlot.deserializeNBT(data.getCompoundTag("Pattern"));
        setPatternDetails();

        setBlockedMode(data.getBoolean("BlockingEnabled"));
        setExport(data.getBoolean("Export"));
        setUseProxy(data.getBoolean("useProxy"));
        AEProxy_pos = new BlockPos(data.getInteger("aeProxy_x"), data.getInteger("aeProxy_y"),
                data.getInteger("aeProxy_z"));

        if (this.circuitInventory != null) {
            this.circuitInventory.read(data);
        }

        setHideInfo(data.getBoolean("hideInfo"));
        setShowName(data.getString("showName"));

        // 反序列化缓冲区池
        if (data.hasKey("BufferPool", Constants.NBT.TAG_LIST)) {
            NBTTagList bufferListTag = data.getTagList("BufferPool", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < Math.min(bufferListTag.tagCount(), bufferPool.size()); i++) {
                bufferPool.get(i).readFromNBT(bufferListTag.getCompoundTagAt(i));
            }
        }
    }

    @Override
    protected int getPatternSlotCount() {
        return PATTERN_SLOTS;
    }

    @Override
    public void onRemoval() {
        // 先尝试退还所有缓冲区物品到 AE 网络
        refundAll();
        removeFromGridCache();
        super.onRemoval();
        GTTransferUtils.dropInventoryItems(getWorld(), getPos(), patternSlot);
        // 退还失败后，将缓冲区中剩余的物品掉落到地面
        for (PatternBuffer buffer : bufferPool) {
            GTTransferUtils.dropInventoryItems(getWorld(), getPos(), buffer.getItemHandler());
        }
    }

    // ==================== 退还机制（refundAll） ====================

    /**
     * 将所有缓冲区中的物品和流体退还到 AE 网络。
     * 移植自 PH-Mod 的 PatternDualInputHatch.refundAll()。
     * 退还失败的物品保留在缓冲区中，后续由 onRemoval() 掉落到地面。
     */
    public void refundAll() {
        IMEMonitor<IAEItemStack> itemMonitor = getItemMonitor();
        IMEMonitor<IAEFluidStack> fluidMonitor = getFluidMonitor();

        for (PatternBuffer buffer : bufferPool) {
            // 退还物品
            if (itemMonitor != null) {
                IItemHandlerModifiable itemHandler = buffer.getItemHandler();
                for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                    ItemStack itemStack = itemHandler.getStackInSlot(slot);
                    if (itemStack.isEmpty()) continue;

                    IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                    if (aeStack == null) continue;

                    IAEItemStack notInserted = itemMonitor.injectItems(aeStack, Actionable.MODULATE, getActionSource());
                    if (notInserted != null && notInserted.getStackSize() > 0) {
                        // 退还失败：更新剩余数量
                        itemStack.setCount((int) notInserted.getStackSize());
                    } else {
                        // 全部退还成功
                        itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                }
            }

            // 退还流体
            if (fluidMonitor != null) {
                FluidTankList fluidHandler = buffer.getFluidHandler();
                for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                    FluidStack fluidStack = fluidHandler.getTankAt(tank).getFluid();
                    if (fluidStack == null || fluidStack.amount <= 0) continue;

                    IAEFluidStack aeFluid = AEApi.instance().storage()
                            .getStorageChannel(IFluidStorageChannel.class)
                            .createStack(fluidStack);
                    if (aeFluid == null) continue;

                    IAEFluidStack remaining = fluidMonitor.injectItems(aeFluid, Actionable.MODULATE, getActionSource());
                    if (remaining != null && remaining.getStackSize() > 0) {
                        // 退还部分成功
                        fluidHandler.getTankAt(tank).drain(
                                (int) (aeFluid.getStackSize() - remaining.getStackSize()), true);
                    } else {
                        // 全部退还成功
                        fluidHandler.getTankAt(tank).drain(fluidStack.amount, true);
                    }
                }
            }

            // 仅在物品/流体都成功退空后，才重置缓冲区状态。
            // 若 AE 无法接收全部内容，剩余物品应保留在缓冲区中（避免吞物）。
            if (buffer.isItemAndFluidEmpty()) {
                buffer.clear();
            }
        }
    }

    // ==================== 缓冲区状态文本构建 ====================

    /**
     * 构建缓冲区状态文本，用于 GUI 显示。
     * 显示每个非空缓冲区的编号、物品种类数量、流体种类数量和电路状态。
     */
    private String buildBufferStatusText() {
        StringBuilder sb = new StringBuilder();
        int usedCount = 0;
        for (int i = 0; i < bufferPool.size(); i++) {
            PatternBuffer buffer = bufferPool.get(i);
            if (buffer.isEmpty() && buffer.getSignature() == null) continue;
            usedCount++;

            sb.append("§e#").append(i + 1).append("§r ");

            // 统计物品种类和总数量
            int itemTypes = 0;
            long itemTotal = 0;
            for (int s = 0; s < buffer.getItemHandler().getSlots(); s++) {
                ItemStack stack = buffer.getItemHandler().getStackInSlot(s);
                if (!stack.isEmpty()) {
                    itemTypes++;
                    itemTotal += stack.getCount();
                }
            }

            // 统计流体种类和总数量
            int fluidTypes = 0;
            long fluidTotal = 0;
            for (int t = 0; t < buffer.getFluidHandler().getTanks(); t++) {
                IFluidTank tank = buffer.getFluidHandler().getTankAt(t);
                if (tank.getFluid() != null && tank.getFluidAmount() > 0) {
                    fluidTypes++;
                    fluidTotal += tank.getFluidAmount();
                }
            }

            // 电路状态（多电路槽）
            StringBuilder circuitInfo = new StringBuilder();
            for (int c = 0; c < buffer.getCircuitSlot().getSlots(); c++) {
                ItemStack circuit = buffer.getCircuitSlot().getStackInSlot(c);
                if (!circuit.isEmpty()) {
                    if (circuitInfo.length() > 0) circuitInfo.append(",");
                    circuitInfo.append(circuit.getDisplayName());
                }
            }

            if (itemTypes > 0) {
                sb.append("§b物品:").append(itemTypes).append("种/").append(itemTotal).append("个§r ");
            }
            if (fluidTypes > 0) {
                sb.append("§9流体:").append(fluidTypes).append("种/").append(fluidTotal).append("mB§r ");
            }
            if (circuitInfo.length() > 0) {
                sb.append("§d电路:").append(circuitInfo).append("§r");
            }
            if (buffer.isRecipeLocked()) {
                sb.append(" §c[锁定]§r");
            }
            if (buffer.full()) {
                sb.append(" §4[满]§r");
            }
            sb.append("\n");
        }

        if (usedCount == 0) {
            sb.append("§7所有缓冲区空闲§r\n");
        }
        sb.append("§f已用: ").append(usedCount).append("/").append(BUFFER_COUNT).append("§r");
        return sb.toString();
    }

    // ==================== GUI ====================

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = TANK_COUNT;
        guiSyncManager.registerSlotGroup("item_inv", rowSize);

        int backgroundWidth = Math.max(
                9 * 18 + 18 + 14 + 5 + 18,   // Player Inv width
                rowSize * 18 + 14 + 18); // Bus Inv width
        int backgroundHeight = 18 + 18 * Math.max(4, rowSize) + 94;

        // 样板模式页面
        List<List<IWidget>> widgetsPattern = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgetsPattern.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;

                widgetsPattern.get(i)
                        .add(new ItemSlot()
                                .slot(SyncHandlers.itemSlot(patternSlot, index)
                                        .slotGroup("item_inv")
                                        .accessibility(true, true)
                                )
                                .background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY)
                        );
            }

        }

        // 缓冲区状态页面（替代原物品检索页面）
        StringSyncValue bufferStatusValue = new StringSyncValue(
                () -> buildBufferStatusText(),
                str -> {}
        );
        guiSyncManager.syncValue("buffer_status", bufferStatusValue);

        // 创建用于显示的值（带前缀）和用于存储的值（纯数字）
        StringSyncValue displayXValue = new StringSyncValue(
                () -> "X:" + AEProxy_pos.getX(),  // 显示时带前缀
                str -> {
                    // 移除前缀并解析
                    if (str.startsWith("X:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("x:")) {
                        str = str.substring(2);
                    }
                    try {
                        AEProxy_pos = new BlockPos(Integer.parseInt(str.trim()), AEProxy_pos.getY(),
                                AEProxy_pos.getZ());
                    } catch (NumberFormatException e) {
                        // 解析失败时保持原值
                        System.err.println("Invalid X coordinate: " + str);
                    }
                }
        );

        StringSyncValue displayYValue = new StringSyncValue(
                () -> "Y:" + AEProxy_pos.getY(),
                str -> {
                    if (str.startsWith("Y:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("y:")) {
                        str = str.substring(2);
                    }
                    try {
                        AEProxy_pos = new BlockPos(AEProxy_pos.getX(), Integer.parseInt(str.trim()),
                                AEProxy_pos.getZ());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Y coordinate: " + str);
                    }
                }
        );

        StringSyncValue displayZValue = new StringSyncValue(
                () -> "Z:" + AEProxy_pos.getZ(),
                str -> {
                    if (str.startsWith("Z:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("z:")) {
                        str = str.substring(2);
                    }
                    try {
                        AEProxy_pos = new BlockPos(AEProxy_pos.getX(), AEProxy_pos.getY(),
                                Integer.parseInt(str.trim()));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Z coordinate: " + str);
                    }
                }
        );

        // 注册同步值
        BooleanSyncValue useProxyStateValue = new BooleanSyncValue(this::isUseProxy, this::setUseProxy);
        guiSyncManager.syncValue("useProxyStateValue", useProxyStateValue);

        List<List<IWidget>> weightsPos = new ArrayList<>();
        List<IWidget> row = new ArrayList<>();

        // 添加开关按钮
        row.add(new ToggleButton()
                .width(20)
                .height(20)
                .value(new BoolValue.Dynamic(useProxyStateValue::getBoolValue,
                        useProxyStateValue::setBoolValue))
                .overlay(GTGuiTextures.PROXY_OVERLAY)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("无线代理模式"))));

        // 添加X坐标文本框
        row.add((new TextFieldWidget()
                .widthRel(0.25f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    // 确保字符串以X:开头
                    if (!str.startsWith("X:") && !str.startsWith("x:")) {
                        if (str.isEmpty()) {
                            return "X:";
                        }
                        // 如果用户删除了前缀，自动添加回来
                        return "X:" + str;
                    }

                    // 提取数字部分进行验证
                    String numPart = str.substring(2);
                    if (numPart.isEmpty()) {
                        return str; // 允许空数字部分（用户正在输入）
                    }

                    try {
                        // 验证数字部分
                        Long.parseLong(numPart.trim());
                        return str; // 验证通过
                    } catch (NumberFormatException e) {
                        // 验证失败，返回当前值
                        return displayXValue.getValue();
                    }
                })
                .value(displayXValue)
                .background(GTGuiTextures.DISPLAY)));

        // 添加Y坐标文本框
        row.add((new TextFieldWidget()
                .widthRel(0.25f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    if (!str.startsWith("Y:") && !str.startsWith("y:")) {
                        if (str.isEmpty()) {
                            return "Y:";
                        }
                        return "Y:" + str;
                    }

                    String numPart = str.substring(2);
                    if (numPart.isEmpty()) {
                        return str;
                    }

                    try {
                        Long.parseLong(numPart.trim());
                        return str;
                    } catch (NumberFormatException e) {
                        return displayYValue.getValue();
                    }
                })
                .value(displayYValue)
                .background(GTGuiTextures.DISPLAY)));

        // 添加Z坐标文本框
        row.add((new TextFieldWidget()
                .widthRel(0.25f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    if (!str.startsWith("Z:") && !str.startsWith("z:")) {
                        if (str.isEmpty()) {
                            return "Z:";
                        }
                        return "Z:" + str;
                    }

                    String numPart = str.substring(2);
                    if (numPart.isEmpty()) {
                        return str;
                    }

                    try {
                        Long.parseLong(numPart.trim());
                        return str;
                    } catch (NumberFormatException e) {
                        return displayZValue.getValue();
                    }
                })
                .value(displayZValue)
                .background(GTGuiTextures.DISPLAY)));

        weightsPos.add(row);

        StringSyncValue nameValue = new StringSyncValue(
                () -> IKey.lang(getShowName()).toString(),
                str -> {
                    if (str != "") {
                        setShowName(str);
                    } else {
                        setShowName(getMetaFullName());
                    }
                }
        );

        BooleanSyncValue collapseStateValue = new BooleanSyncValue(this::isAutoCollapse, this::setAutoCollapse);
        guiSyncManager.syncValue("collapse_state", collapseStateValue);

        BooleanSyncValue showInfoStateValue = new BooleanSyncValue(this::isHideInfo, this::setHideInfo);
        guiSyncManager.syncValue("hide_info", showInfoStateValue);

        // One-shot refund action. Client button increments this value; server setter executes refundAll().
        IntSyncValue refundActionValue = new IntSyncValue(
                () -> 0,
                value -> {
                    if (value <= 0 || getWorld() == null || getWorld().isRemote) {
                        return;
                    }
                    refundAll();
                    markDirty();
                });
        guiSyncManager.syncValue("refund_action", refundActionValue);

        var controller = new PagedWidget.Controller();
        guiSyncManager.syncValue("page_controller", new PagedWidgetSyncHandler(controller));

        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(Flow.row()
                        .name("tab row")
                        .widthRel(1f)
                        .leftRel(0.5f)
                        .margin(3, 0)
                        .coverChildrenHeight()
                        .topRel(0f, 3, 1f)
                        .child(new PageButton(0, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("样板模式"))
                                .overlay(HATCH))
                        .child(new PageButton(1, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("缓冲区状态"))
                                .overlay(CHEST))
                        .child(new PageButton(2, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("网络代理"))
                                .overlay(PROXY))
                        .child(new PageButton(3, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("终端显示"))
                                .overlay(TERMINAL))
                )
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new PagedWidget<>()
                        .top(18) // 调整 PagedWidget 的顶部位置为 18
                        .margin(0) // 移除 margin 避免偏移
                        .widthRel(1f) // 宽度设为父容器的 100%
                        .controller(controller)
                        .addPage(// 样板模式页面
                                new Grid()
                                        .top(0) // 内部 Grid 相对于 PagedWidget 顶部对齐
                                        .height(rowSize * 18) // 设置高度
                                        .minElementMargin(0, 0)
                                        .minColWidth(18)
                                        .minRowHeight(18)
                                        .leftRel(0.5f) // 水平居中
                                        .matrix(widgetsPattern))
                        .addPage(// 缓冲区状态页面
                                Flow.column()
                                        .top(0)
                                        .widthRel(1f)
                                        .leftRel(0.5f)
                                        .margin(5, 0)
                                        .child(new TextWidget<>(
                                                IKey.dynamic(() -> bufferStatusValue.getValue()))
                                                .widthRel(1f)
                                                .height(rowSize * 18)))
                        .addPage(// 代理模式页面
                                Flow.column() // 使用列布局
                                        .top(0)
                                        .widthRel(1f)
                                        .leftRel(0.5f)
                                        .child(
                                                new Grid()
                                                        .height(25)
                                                        .minElementMargin(0, 0)
                                                        .minColWidth((int) (0.24f * backgroundWidth))
                                                        .minRowHeight(18)
                                                        .matrix(weightsPos)
                                        )
                                        .childIf(isUseProxy(), () -> Flow.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("无线代理模式")))
                                                .childIf(isUseProxy(), () -> {
                                                    TileEntity tileEntity = this.getWorld().getTileEntity(AEProxy_pos);
                                                    if (tileEntity instanceof AENetworkPowerTile proxy) {
                                                        return Flow.column()
                                                                .widthRel(1f)
                                                                .child(new TextWidget<>(IKey.str("连接至无线网络")))
                                                                .child(new TextWidget<>(IKey.dynamic(() ->
                                                                        "位置:" + proxy.getLocation()
                                                                )))
                                                                .child(new TextWidget<>(IKey.dynamic(() ->
                                                                        "名称:" +
                                                                                proxy.getBlockType().getLocalizedName()
                                                                )));
                                                    } else {
                                                        return Flow.column()
                                                                .widthRel(1f)
                                                                .child(new TextWidget<>(IKey.str("未找到无线网络代理")))
                                                                .child(new TextWidget<>(IKey.dynamic(() ->
                                                                        "坐标:" + AEProxy_pos.getX() + ", " +
                                                                                AEProxy_pos.getY() + ", " +
                                                                                AEProxy_pos.getZ()
                                                                )));
                                                    }
                                                })
                                        )
                                        .childIf(!isUseProxy(), () -> Flow.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("有线代理模式")))
                                        )
                        )
                        .addPage(// 终端设置
                                Flow.column()
                                        .child(new TextWidget<>(IKey.str("终端设置")))
                                        .child(new ToggleButton()
                                                .size(18, 18)
                                                .overlay(false, GTGuiTextures.BUTTON_POWER[1])
                                                .overlay(true, GTGuiTextures.BUTTON_POWER[0])
                                                .value(new BoolValue.Dynamic(showInfoStateValue::getBoolValue,
                                                        showInfoStateValue::setBoolValue))
                                                .addTooltipLine(IKey.str("设置是否在样板管理器内显示"))
                                        )
                                        .child(new TextFieldWidget()
                                                .widthRel(0.5f)
                                                .height(20)
                                                .setTextColor(Color.WHITE.darker(1))
                                                .setValidator(str ->
                                                {
                                                    if (str == null || str.isEmpty())
                                                        return IKey.lang(this.getMetaFullName()).toString();
                                                    return str;
                                                })
                                                .value(nameValue)
                                                .background(GTGuiTextures.DISPLAY))

                        )
                )
                .child(Flow.column()
                        .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 * 2 - 7)
                        .width(18).height(18 * 2)

                        .child(new ToggleButton()
                                .top(18)
                                .value(new BoolValue.Dynamic(collapseStateValue::getBoolValue,
                                        collapseStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_COLLAPSE)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("自动整理"))))

                        // 退还按钮：将所有缓冲区中的物品和流体退还到 AE 网络
                        .child(new ButtonWidget<>()
                                .top(0)
                                .onMousePressed(mouseButton -> {
                                    refundActionValue.setIntValue(refundActionValue.getIntValue() + 1);
                                    return true;
                                })
                                .overlay(GTGuiTextures.EXPORT_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("退还所有缓冲区物品到AE网络"))))

                );
    }

    // ==================== 创造模式物品列表 ====================

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        super.getSubItems(creativeTab, subItems);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.buffer", BUFFER_COUNT));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.refund"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.lock"));
        tooltip.add(I18n.format("gregtech.machine.dual_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", PATTERN_SLOTS));
        tooltip.add(I18n.format("gregtech.machine.me.data_stick_proxy"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    // ==================== 内部类：缓冲区签名 ====================

    /**
     * 缓冲区签名 — 用于判断物品组合是否应该进入同一个缓冲区。
     * 比较的是物品/流体的类型（忽略数量）。
     */
    public static class BufferSignature {
        private final List<ItemStack> itemTypes;
        private final List<FluidStack> fluidTypes;
        private final ItemStack circuitStack;

        public BufferSignature(List<ItemStack> itemTypes, List<FluidStack> fluidTypes, ItemStack circuitStack) {
            this.itemTypes = itemTypes;
            this.fluidTypes = fluidTypes;
            this.circuitStack = circuitStack;
        }

        public List<ItemStack> getItemTypes() {
            return itemTypes;
        }

        public List<FluidStack> getFluidTypes() {
            return fluidTypes;
        }

        public ItemStack getCircuitStack() {
            return circuitStack;
        }

        /**
         * 比较两个签名是否匹配（物品类型和流体类型完全相同）。
         */
        public boolean matches(BufferSignature other) {
            if (this.itemTypes.size() != other.itemTypes.size()) return false;
            if (this.fluidTypes.size() != other.fluidTypes.size()) return false;

            // 比较物品类型（忽略数量）
            for (int i = 0; i < this.itemTypes.size(); i++) {
                if (!ItemStack.areItemsEqual(this.itemTypes.get(i), other.itemTypes.get(i))) return false;
                if (!ItemStack.areItemStackTagsEqual(this.itemTypes.get(i), other.itemTypes.get(i))) return false;
            }

            // 比较流体类型（忽略数量）
            for (int i = 0; i < this.fluidTypes.size(); i++) {
                if (!this.fluidTypes.get(i).isFluidEqual(other.fluidTypes.get(i))) return false;
            }

            // 比较电路
            if (!ItemStack.areItemStacksEqual(this.circuitStack, other.circuitStack)) return false;

            return true;
        }

        /**
         * 基于内容的哈希值，用于配方 ID 跟踪。
         */
        @Override
        public int hashCode() {
            int hash = 1;
            for (ItemStack stack : itemTypes) {
                hash = 31 * hash + Item.getIdFromItem(stack.getItem());
                hash = 31 * hash + stack.getMetadata();
            }
            for (FluidStack fluid : fluidTypes) {
                hash = 31 * hash + FluidRegistry.getFluidName(fluid.getFluid()).hashCode();
            }
            if (!circuitStack.isEmpty()) {
                hash = 31 * hash + Item.getIdFromItem(circuitStack.getItem());
                hash = 31 * hash + circuitStack.getMetadata();
            }
            return hash;
        }

        /**
         * 将签名序列化为 NBT。
         */
        public NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();

            NBTTagList itemList = new NBTTagList();
            for (ItemStack stack : itemTypes) {
                itemList.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("Items", itemList);

            NBTTagList fluidList = new NBTTagList();
            for (FluidStack fluid : fluidTypes) {
                fluidList.appendTag(fluid.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("Fluids", fluidList);

            if (!circuitStack.isEmpty()) {
                tag.setTag("Circuit", circuitStack.writeToNBT(new NBTTagCompound()));
            }

            return tag;
        }

        /**
         * 从 NBT 反序列化签名。
         */
        public static BufferSignature readFromNBT(NBTTagCompound tag) {
            List<ItemStack> items = new ArrayList<>();
            NBTTagList itemList = tag.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < itemList.tagCount(); i++) {
                items.add(new ItemStack(itemList.getCompoundTagAt(i)));
            }

            List<FluidStack> fluids = new ArrayList<>();
            NBTTagList fluidList = tag.getTagList("Fluids", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < fluidList.tagCount(); i++) {
                fluids.add(FluidStack.loadFluidStackFromNBT(fluidList.getCompoundTagAt(i)));
            }

            ItemStack circuit = tag.hasKey("Circuit") ?
                    new ItemStack(tag.getCompoundTag("Circuit")) : ItemStack.EMPTY;

            return new BufferSignature(items, fluids, circuit);
        }
    }

    // ==================== 内部类：缓冲区 ====================

    /**
     * 单个缓冲区 — 持有独立的物品、流体和虚拟电路槽。
     * 类似 Programmable-Hatches-Mod 的 DualInvBuffer。
     * <p>
     * 每个缓冲区包装为一个 DualHandler，供多方块配方系统独立匹配。
     * <p>
     * 锁定机制（移植自 PH-Mod）：
     * <ul>
     *   <li>recipeLocked：当缓冲区接收到 AE 推送的物品后设为 true，表示已绑定到某配方</li>
     *   <li>lock：由 GUI 控制，为 true 时即使缓冲区清空也不释放签名（手动锁定）</li>
     *   <li>unlockDelay：配方消耗完毕后延迟若干 tick 才释放，防止不同配方抢占</li>
     * </ul>
     */
    public static class PatternBuffer {
        private final NotifiableItemStackHandler itemHandler;
        private final FluidTankList fluidHandler;
        /**
         * 缓冲区电路槽 — 用简单的 ItemStackHandler 代替 GhostCircuitItemStackHandler，
         * 避免传入 null MetaTileEntity 导致的潜在 NPE。
         * 支持多个电路槽（移植自 PH-Mod 的 v=4），可同时绑定多种编程电路。
         */
        private final ItemStackHandler circuitSlot;
        private final IItemHandlerModifiable combinedItemHandler;
        private final DualHandler dualHandler;
        private BufferSignature signature;

        // ==================== 缓冲区锁定字段 ====================
        /** 当缓冲区接收到 AE 推送物品后设为 true */
        private boolean recipeLocked;
        /** 手动锁定：为 true 时缓冲区即使清空也不释放签名 */
        private boolean lock;
        /** 配方消耗完毕后延迟释放的倒计时 */
        private int unlockDelay;

        // ==================== 配方跟踪字段（移植自 PH-Mod PID 机制）====================
        /** 最后一次被配方系统匹配到的 tick，用于 registerAbilities 排序优化 */
        private long lastMatchTick;
        /** 配方身份标识 ID，用于缓冲区排序和配方缓存优化 */
        private int recipeId;

        public PatternBuffer(MetaTileEntity owner, int itemSlots, int fluidSlots, int tankCapacity) {
            this.itemHandler = new NotifiableItemStackHandler(owner, itemSlots, null, false) {

                @Override
                public int getSlotLimit(int slot) {
                    return ITEM_STACK_LIMIT;
                }
            };
            IFluidTank[] tanks = new IFluidTank[fluidSlots];
            for (int i = 0; i < fluidSlots; i++) {
                tanks[i] = new NotifiableFluidTank(tankCapacity, null, false);
            }
            this.fluidHandler = new FluidTankList(false, tanks);
            this.circuitSlot = new ItemStackHandler(CIRCUIT_SLOT_COUNT);
            this.combinedItemHandler = new ItemHandlerList(
                    java.util.Arrays.asList(this.itemHandler, this.circuitSlot));
            this.dualHandler = new DualHandler(this.combinedItemHandler, this.fluidHandler, false);
        }

        public NotifiableItemStackHandler getItemHandler() {
            return itemHandler;
        }

        public FluidTankList getFluidHandler() {
            return fluidHandler;
        }

        /**
         * 获取缓冲区的电路槽 handler。
         */
        public ItemStackHandler getCircuitSlot() {
            return circuitSlot;
        }

        // ==================== 配方跟踪方法 ====================

        /**
         * 获取最后一次匹配到配方的 tick。
         * 用于 registerAbilities 排序，使最近使用的缓冲区排在前面。
         */
        public long getLastMatchTick() {
            return lastMatchTick;
        }

        /**
         * 记录配方匹配事件（移植自 PH-Mod 的 recordRecipe）。
         * 在缓冲区被配方系统消耗物品后调用。
         */
        public void recordRecipeMatch(long worldTick, int recipeId) {
            this.lastMatchTick = worldTick;
            this.recipeId = recipeId;
        }

        /**
         * 获取配方身份 ID。
         */
        public int getRecipeId() {
            return recipeId;
        }

        /**
         * 设置电路值（集成电路 0-32）到缓冲区。
         */
        public void setCircuitValue(int config) {
            if (config >= IntCircuitIngredient.CIRCUIT_MIN && config <= IntCircuitIngredient.CIRCUIT_MAX) {
                circuitSlot.setStackInSlot(0, IntCircuitIngredient.getIntegratedCircuit(config));
            } else {
                circuitSlot.setStackInSlot(0, ItemStack.EMPTY);
            }
        }

        /**
         * 设置自定义电路物品（可编程电路解包后的物品）到缓冲区。
         * 支持多电路槽：找到第一个空槽位放入，如果已有相同电路则跳过。
         * 移植自 PH-Mod 的 programLocal() 多电路解包逻辑。
         */
        public void setCustomCircuit(@NotNull ItemStack stack) {
            if (stack.isEmpty()) return;

            ItemStack copy = stack.copy();
            copy.setCount(1);

            // 检查是否已有相同电路
            for (int i = 0; i < circuitSlot.getSlots(); i++) {
                ItemStack existing = circuitSlot.getStackInSlot(i);
                if (ItemStack.areItemStacksEqual(existing, copy)) {
                    return;
                }
            }

            // 找到第一个空槽位放入
            for (int i = 0; i < circuitSlot.getSlots(); i++) {
                if (circuitSlot.getStackInSlot(i).isEmpty()) {
                    circuitSlot.setStackInSlot(i, copy);
                    return;
                }
            }
        }

        /**
         * 清空缓冲区的所有电路槽。
         * 用于空白可编程电路重置虚拟电路槽。
         */
        public void clearCircuit() {
            for (int i = 0; i < circuitSlot.getSlots(); i++) {
                circuitSlot.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        public DualHandler getDualHandler() {
            return dualHandler;
        }

        public BufferSignature getSignature() {
            return signature;
        }

        public void setSignature(BufferSignature signature) {
            this.signature = signature;
        }

        public boolean isRecipeLocked() {
            return recipeLocked;
        }

        public void setRecipeLocked(boolean recipeLocked) {
            this.recipeLocked = recipeLocked;
        }

        public boolean isLock() {
            return lock;
        }

        public void setLock(boolean lock) {
            this.lock = lock;
        }

        /**
         * 清理零数量的物品和流体。
         * 移植自 PH-Mod 的 DualInvBuffer.updateSlots()。
         * 防止配方系统消耗后留下 count=0 的 ItemStack 或 amount=0 的 FluidStack。
         */
        public void updateSlots() {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getCount() <= 0) {
                    itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                IFluidTank tank = fluidHandler.getTankAt(i);
                if (tank.getFluid() != null && tank.getFluidAmount() <= 0) {
                    tank.drain(Integer.MAX_VALUE, true);
                }
            }
        }

        /**
         * 判断缓冲区是否已满（物品或流体达到上限）。
         * 移植自 PH-Mod 的 DualInvBuffer.full()。
         * 当任何一个槽位的数量达到上限时返回 true，防止无限累积。
         */
        public boolean full() {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getCount() >= ITEM_STACK_LIMIT) {
                    return true;
                }
            }
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                IFluidTank tank = fluidHandler.getTankAt(i);
                if (tank.getFluid() != null && tank.getFluidAmount() >= TANK_CAPACITY) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 计算缓冲区还能容纳多少"份"相同签名的材料。
         * 移植自 PH-Mod 的 DualInvBuffer.space()。
         * 遍历每种已有物品和流体，计算 (上限 - 当前数量) / 单份数量，取最小值。
         *
         * @return 还能容纳的份数；空缓冲区或无签名时返回 0
         */
        public int space() {
            if (signature == null) return 0;

            long ret = Long.MAX_VALUE;
            boolean found = false;

            // 物品维度：对每种已有物品计算剩余可容纳份数
            for (int i = 0; i < signature.getItemTypes().size(); i++) {
                ItemStack singleStack = signature.getItemTypes().get(i);
                if (singleStack.isEmpty() || singleStack.getCount() <= 0) continue;

                long currentAmount = 0;
                for (int s = 0; s < itemHandler.getSlots(); s++) {
                    ItemStack slot = itemHandler.getStackInSlot(s);
                    if (!slot.isEmpty() && slot.getItem() == singleStack.getItem()
                            && slot.getMetadata() == singleStack.getMetadata()) {
                        currentAmount += slot.getCount();
                    }
                }

                long canFit = ((long) ITEM_STACK_LIMIT - currentAmount) / singleStack.getCount();
                if (canFit < ret) {
                    ret = canFit;
                    found = true;
                }
            }

            // 流体维度：对每种已有流体计算剩余可容纳份数
            for (int i = 0; i < signature.getFluidTypes().size(); i++) {
                FluidStack singleFluid = signature.getFluidTypes().get(i);
                if (singleFluid == null || singleFluid.amount <= 0) continue;

                long currentAmount = 0;
                for (int t = 0; t < fluidHandler.getTanks(); t++) {
                    IFluidTank tank = fluidHandler.getTankAt(t);
                    if (tank.getFluid() != null
                            && tank.getFluid().getFluid() == singleFluid.getFluid()) {
                        currentAmount += tank.getFluidAmount();
                    }
                }

                long canFit = ((long) TANK_CAPACITY - currentAmount) / singleFluid.amount;
                if (canFit < ret) {
                    ret = canFit;
                    found = true;
                }
            }

            if (found) return (int) Math.min(ret, Integer.MAX_VALUE);
            return 0;
        }

        /**
         * 判断缓冲区是否与给定签名匹配。
         */
        public boolean matchesSignature(BufferSignature other) {
            return this.signature != null && this.signature.matches(other);
        }

        /**
         * 判断缓冲区是否为空（无物品、无流体、无电路配置）。
         */
        public boolean isEmpty() {
            // 检查物品
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (!itemHandler.getStackInSlot(i).isEmpty()) return false;
            }
            // 检查流体
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                IFluidTank tank = fluidHandler.getTankAt(i);
                if (tank.getFluid() != null && tank.getFluidAmount() > 0) return false;
            }
            // 检查电路（所有电路槽）
            for (int i = 0; i < circuitSlot.getSlots(); i++) {
                if (!circuitSlot.getStackInSlot(i).isEmpty()) return false;
            }

            return true;
        }

        /**
         * 判断缓冲区的物品和流体是否全部为空（不检查电路槽）。
         * 用于判断配方是否已消耗完毕，即使电路槽还有值也算"消耗完成"。
         */
        public boolean isItemAndFluidEmpty() {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (!itemHandler.getStackInSlot(i).isEmpty()) return false;
            }
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                IFluidTank tank = fluidHandler.getTankAt(i);
                if (tank.getFluid() != null && tank.getFluidAmount() > 0) return false;
            }
            return true;
        }

        /**
         * 缓冲区清理逻辑 — 移植自 PH-Mod 的 DualInvBuffer.clearRecipeIfNeeded()。
         * <p>
         * 返回值：
         * <ul>
         *   <li>0 → 缓冲区还未就绪（正在延迟解锁或仍有内容物）</li>
         *   <li>1 → 缓冲区已就绪，可以重新使用</li>
         * </ul>
         */
        public int clearRecipeIfNeeded() {
            // 手动锁定模式：永不释放签名
            if (lock) {
                unlockDelay = 0;
                return !recipeLocked ? 1 : 0;
            }

            if (isItemAndFluidEmpty()) {
                if (!recipeLocked) {
                    return 1;
                }

                // 延迟解锁机制：防止不同配方抢占同一缓冲区
                if (unlockDelay == 0) {
                    unlockDelay = DEFAULT_UNLOCK_DELAY;
                    return 0;
                }
                if (unlockDelay > 0) {
                    unlockDelay--;
                    if (unlockDelay != 0) return 0;
                }

                // 延迟结束，释放缓冲区
                clear();
                return 1;
            } else {
                unlockDelay = 0;
            }
            return 0;
        }

        /**
         * 清空缓冲区（配方消耗完毕后调用）。
         */
        public void clear() {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                IFluidTank tank = fluidHandler.getTankAt(i);
                tank.drain(Integer.MAX_VALUE, true);
            }
            // 清空所有电路槽
            for (int i = 0; i < circuitSlot.getSlots(); i++) {
                circuitSlot.setStackInSlot(i, ItemStack.EMPTY);
            }
            this.signature = null;
            this.recipeLocked = false;
            this.unlockDelay = 0;
            this.recipeId = 0;
        }

        /**
         * 将缓冲区序列化为 NBT。
         */
        public NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("Items", itemHandler.serializeNBT());

            // 序列化流体
            NBTTagList fluidList = new NBTTagList();
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                IFluidTank tank = fluidHandler.getTankAt(i);
                NBTTagCompound fluidTag = new NBTTagCompound();
                FluidStack fluid = tank.getFluid();
                if (fluid != null) {
                    fluid.writeToNBT(fluidTag);
                }
                fluidList.appendTag(fluidTag);
            }
            tag.setTag("Fluids", fluidList);

            // 序列化电路（多电路槽）
            NBTTagList circuitList = new NBTTagList();
            for (int i = 0; i < circuitSlot.getSlots(); i++) {
                ItemStack circuit = circuitSlot.getStackInSlot(i);
                circuitList.appendTag(circuit.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("CircuitSlots", circuitList);

            // 序列化签名
            if (signature != null) {
                tag.setTag("Signature", signature.writeToNBT());
            }

            // 序列化锁定状态
            tag.setBoolean("recipeLocked", recipeLocked);
            tag.setBoolean("lock", lock);
            tag.setInteger("unlockDelay", unlockDelay);

            // 序列化配方跟踪字段
            tag.setLong("lastMatchTick", lastMatchTick);
            tag.setInteger("recipeId", recipeId);

            return tag;
        }

        /**
         * 从 NBT 反序列化缓冲区。
         */
        public void readFromNBT(NBTTagCompound tag) {
            itemHandler.deserializeNBT(tag.getCompoundTag("Items"));

            // 反序列化流体
            if (tag.hasKey("Fluids", Constants.NBT.TAG_LIST)) {
                NBTTagList fluidList = tag.getTagList("Fluids", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < Math.min(fluidList.tagCount(), fluidHandler.getTanks()); i++) {
                    IFluidTank tank = fluidHandler.getTankAt(i);
                    NBTTagCompound fluidTag = fluidList.getCompoundTagAt(i);
                    FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluidTag);
                    if (fluid != null) {
                        tank.fill(fluid, true);
                    }
                }
            }

            // 反序列化电路（多电路槽，兼容旧格式）
            if (tag.hasKey("CircuitSlots", Constants.NBT.TAG_LIST)) {
                NBTTagList circuitList = tag.getTagList("CircuitSlots", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < Math.min(circuitList.tagCount(), circuitSlot.getSlots()); i++) {
                    ItemStack circuit = new ItemStack(circuitList.getCompoundTagAt(i));
                    circuitSlot.setStackInSlot(i, circuit);
                }
            } else if (tag.hasKey("CircuitItem", Constants.NBT.TAG_COMPOUND)) {
                // 兼容旧版单电路槽格式
                ItemStack circuit = new ItemStack(tag.getCompoundTag("CircuitItem"));
                circuitSlot.setStackInSlot(0, circuit);
            }

            // 反序列化签名
            if (tag.hasKey("Signature", Constants.NBT.TAG_COMPOUND)) {
                this.signature = BufferSignature.readFromNBT(tag.getCompoundTag("Signature"));
            }

            // 反序列化锁定状态
            this.recipeLocked = tag.getBoolean("recipeLocked");
            this.lock = tag.getBoolean("lock");
            this.unlockDelay = tag.getInteger("unlockDelay");

            // 反序列化配方跟踪字段
            this.lastMatchTick = tag.getLong("lastMatchTick");
            this.recipeId = tag.getInteger("recipeId");
        }
    }
}
