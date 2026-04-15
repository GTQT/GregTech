package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.tile.grid.AENetworkPowerTile;
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
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.common.item.fake.FakeItemRegister;
import gtqt.api.util.PatternUtils;
import gtqt.common.items.behaviors.ProgrammableCircuit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class MetaTileEntityMEPatternProvider extends MetaTileEntityAECraftingPart {

    // ==================== 缓冲区池常量 ====================
    public static final int BUFFER_COUNT = 24;

    // ==================== 缓冲区池 ====================
    private final List<PatternBuffer> bufferPool = new ArrayList<>();

    // ==================== 固定参数 ====================
    private static final int PATTERN_SLOTS = 36;
    private static final int TANK_COUNT = 6;
    private static final int TANK_CAPACITY = 64_000;

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
        this.extraItem = new NotifiableItemStackHandler(this, TANK_COUNT + 1, null, false);

        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                java.util.Arrays.asList(this.importItems, this.circuitInventory, extraItem));

        dualHandler = new DualHandler(
                this.actualImportItems,
                getImportFluids(),
                isExportHatch);

        // 初始化缓冲区池
        initBufferPool();
    }

    /**
     * 初始化缓冲区池，创建固定数量的缓冲区实例。
     */
    private void initBufferPool() {
        bufferPool.clear();
        for (int i = 0; i < BUFFER_COUNT; i++) {
            bufferPool.add(new PatternBuffer(PATTERN_SLOTS, TANK_COUNT, TANK_CAPACITY));
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
        // 注册所有非空缓冲区的 DualHandler，使多方块 distinct 模式可以逐个匹配
        for (PatternBuffer buffer : bufferPool) {
            if (!buffer.isEmpty()) {
                abilityInstances.add(buffer.getDualHandler());
            }
        }
        // 如果所有缓冲区都空，注册一个空的 DualHandler 以保持兼容
        if (bufferPool.stream().allMatch(PatternBuffer::isEmpty)) {
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
            if (FakeFluids.isFluidFakeItem(stack)) {
                FluidStack fluid = FakeItemRegister.getStack(stack);
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
     * 相同签名的物品进入同一个缓冲区。
     */
    @Nullable
    private PatternBuffer findOrAllocateBuffer(BufferSignature signature) {
        // 第一步：查找已有的与签名匹配的缓冲区
        for (PatternBuffer buffer : bufferPool) {
            if (!buffer.isEmpty() && buffer.matchesSignature(signature)) {
                return buffer;
            }
        }
        // 第二步：分配一个空缓冲区
        for (PatternBuffer buffer : bufferPool) {
            if (buffer.isEmpty()) {
                return buffer;
            }
        }
        // 所有缓冲区都满了
        return null;
    }

    /**
     * 将 AE 推送的材料分配到缓冲区中。
     * 相同物品组合进入同一个缓冲区，不同物品组合分配新缓冲区。
     */
    public boolean pushToBuffer(net.minecraft.inventory.InventoryCrafting inventoryCrafting) {
        BufferSignature signature = extractSignature(inventoryCrafting);
        PatternBuffer buffer = findOrAllocateBuffer(signature);
        if (buffer == null) {
            return false;
        }

        // 将签名记录到缓冲区（如果是空缓冲区则首次记录）
        if (buffer.isEmpty()) {
            buffer.setSignature(signature);
        }

        // 将物品和流体实际插入缓冲区
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); i++) {
            ItemStack stack = inventoryCrafting.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 处理假流体物品
            if (FakeFluids.isFluidFakeItem(stack)) {
                FluidStack fluid = FakeItemRegister.getStack(stack);
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
                    // 空白可编程电路：不做处理
                }
                continue;
            }

            // 普通物品：插入缓冲区的物品槽
            ItemStack toInsert = stack.copy();
            IItemHandlerModifiable itemHandler = buffer.getItemHandler();
            for (int slot = 0; slot < itemHandler.getSlots() && !toInsert.isEmpty(); slot++) {
                if (itemHandler.getStackInSlot(slot).isEmpty()) {
                    toInsert = itemHandler.insertItem(slot, toInsert, false);
                }
            }
            // 如果空槽不够，再尝试所有槽位
            if (!toInsert.isEmpty()) {
                for (int slot = 0; slot < itemHandler.getSlots() && !toInsert.isEmpty(); slot++) {
                    toInsert = itemHandler.insertItem(slot, toInsert, false);
                }
            }
        }

        return true;
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

                // 缓冲区清理：当物品和流体全部被配方消耗后，清空电路槽和签名使缓冲区可复用
                for (PatternBuffer buffer : bufferPool) {
                    if (buffer.getSignature() != null && buffer.isItemAndFluidEmpty()) {
                        buffer.clear();
                    }
                }
            }

            if (isPatternDeal() && getOffsetTimer() % 20 == 0) {
                if (isAttachedToMultiBlock()) {
                    MultiblockControllerBase controllerBase = getController();
                    if (controllerBase instanceof RecipeMapMultiblockController controller) {
                        if (controller.getRecipeMapWorkable().getParallelLimit() != 0 && lastParallel != parallel) {

                            lastParallel = parallel;
                            parallel = controller.getRecipeMapWorkable().getParallelLimit();

                            if (lastParallel != 1 || parallel != 1) {
                                for (int i = 0; i < patternSlot.getSlots(); i++) {
                                    ItemStack pattern = patternSlot.getStackInSlot(i);
                                    if (pattern.getItem() instanceof ICraftingPatternItem) {
                                        PatternUtils.adjustPatternMultipliers(pattern, lastParallel, parallel);
                                    }
                                }
                            }
                        }
                    }
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
        data.setTag("ExtraItem", this.extraItem.serializeNBT());

        data.setBoolean("BlockingEnabled", isBlockedMode());
        data.setBoolean("Export", isExport());
        data.setBoolean("patternDeal", isPatternDeal());
        data.setInteger("parallel", this.parallel);
        data.setInteger("lastParallel", this.lastParallel);

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
        this.extraItem.deserializeNBT(data.getCompoundTag("ExtraItem"));

        setBlockedMode(data.getBoolean("BlockingEnabled"));
        setExport(data.getBoolean("Export"));
        setPatternDeal(data.getBoolean("patternDeal"));
        this.parallel = data.getInteger("parallel");
        this.lastParallel = data.getInteger("lastParallel");
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
    public void setPatternDetails() {
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            ItemStack pattern = patternSlot.getStackInSlot(i);
            if (pattern == ItemStack.EMPTY) {
                patternDetails.set(i, null);
                continue;
            }

            if (pattern.getItem() instanceof ICraftingPatternItem patternItem) {
                patternDetails.set(i, patternItem.getPatternForItem(pattern, getWorld()));
            }
        }
    }

    @Override
    public void onRemoval() {
        removeFromGridCache();
        super.onRemoval();
        GTTransferUtils.dropInventoryItems(getWorld(), getPos(), patternSlot);
        GTTransferUtils.dropInventoryItems(getWorld(), getPos(), extraItem);
        // 掉落所有缓冲区中的物品
        for (PatternBuffer buffer : bufferPool) {
            GTTransferUtils.dropInventoryItems(getWorld(), getPos(), buffer.getItemHandler());
        }
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
                (rowSize + 1) * 18 + 14 + 18); // Bus Inv width
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

            widgetsPattern.get(i)
                    .add(new ItemSlot()
                            .slot(SyncHandlers.itemSlot(extraItem, i)
                                    .slotGroup("item_inv")
                                    .accessibility(true, true)
                            )
                            .background(GTGuiTextures.SLOT, GTGuiTextures.EXTRA_SLOT_OVERLAY)
                    );
        }

        // 物品检索页面（显示原始 importItems 和 fluids）
        List<List<IWidget>> widgetsItem = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgetsItem.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;

                IItemHandlerModifiable handler = importItems;
                widgetsItem.get(i)
                        .add(new ItemSlot()
                                .slot(SyncHandlers.itemSlot(handler, index)
                                        .slotGroup("item_inv")
                                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                            if (onlyAmountChanged &&
                                                    handler instanceof GTItemStackHandler gtHandler) {
                                                gtHandler.onContentsChanged(index);
                                            }
                                        })
                                        .accessibility(true, true)));
            }
            IFluidTank tankHandler = dualHandler.getTankAt(i);
            widgetsItem.get(i).add(new GTFluidSlot()
                    .syncHandler(GTFluidSlot.sync(tankHandler)
                            .accessibility(true, true))
            );
        }

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

        BooleanSyncValue blockStateValue = new BooleanSyncValue(this::isBlockedMode, this::setBlockedMode);
        guiSyncManager.syncValue("block_state", blockStateValue);

        BooleanSyncValue collapseStateValue = new BooleanSyncValue(this::isAutoCollapse, this::setAutoCollapse);
        guiSyncManager.syncValue("collapse_state", collapseStateValue);

        BooleanSyncValue exportStateValue = new BooleanSyncValue(this::isExport, this::setExport);
        guiSyncManager.syncValue("export_state", exportStateValue);

        BooleanSyncValue patternStateValue = new BooleanSyncValue(this::isPatternDeal, this::setPatternDeal);
        guiSyncManager.syncValue("pattern_state", patternStateValue);

        BooleanSyncValue showInfoStateValue = new BooleanSyncValue(this::isHideInfo, this::setHideInfo);
        guiSyncManager.syncValue("hide_info", showInfoStateValue);

        boolean hasGhostCircuit = hasGhostCircuitInventory() && this.circuitInventory != null;

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
                                .addTooltipLine(IKey.lang("物品检索"))
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
                        .addPage(// 物品模式页面
                                new Grid()
                                        .top(0)
                                        .height(rowSize * 18)
                                        .minElementMargin(0, 0)
                                        .minColWidth(18)
                                        .minRowHeight(18)
                                        .leftRel(0.5f)
                                        .matrix(widgetsItem))
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
                        .pos(backgroundWidth - 7 - 36, backgroundHeight - 18 * 4 - 7 - 5)
                        .width(18).height(18 * 4 + 5)

                        .child(GTGuiTextures.getLogo(getUITheme()).asWidget()
                                .top(18 * 3 + 5)
                                .size(17)
                        )

                        .child(new ToggleButton()
                                .top(18 * 2)
                                .value(new BoolValue.Dynamic(blockStateValue::getBoolValue,
                                        blockStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_OUTPUT)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("阻挡模式"))))
                        .child(new ToggleButton()
                                .top(18 * 2)
                                .left(18)
                                .value(new BoolValue.Dynamic(exportStateValue::getBoolValue,
                                        exportStateValue::setBoolValue))
                                .overlay(GTGuiTextures.EXPORT_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("返回模式"))))

                        .child(new ToggleButton()
                                .top(18)
                                .value(new BoolValue.Dynamic(collapseStateValue::getBoolValue,
                                        collapseStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_COLLAPSE)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("自动整理"))))

                        .childIf(hasGhostCircuit, new GhostCircuitSlotWidget()
                                .top(18)
                                .left(18)
                                .slot(circuitInventory, 0)
                                .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY))
                        .childIf(!hasGhostCircuit, new Widget<>()
                                .top(18)
                                .left(18)
                                .background(GTGuiTextures.SLOT, GTGuiTextures.BUTTON_X)
                                .tooltip(t -> t.addLine(
                                        IKey.lang("gregtech.gui.configurator_slot.unavailable.tooltip")))
                        )

                        .child(new ToggleButton()
                                .top(0)
                                .value(new BoolValue.Dynamic(patternStateValue::getBoolValue,
                                        patternStateValue::setBoolValue))
                                .overlay(GTGuiTextures.PATTERN_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("样板优化"))))

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
        tooltip.add(I18n.format("gregtech.machine.dual_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", PATTERN_SLOTS));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", TANK_COUNT,
                TANK_CAPACITY));
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
     */
    public static class PatternBuffer {
        private final NotifiableItemStackHandler itemHandler;
        private final FluidTankList fluidHandler;
        /**
         * 缓冲区电路槽 — 用简单的 ItemStackHandler 代替 GhostCircuitItemStackHandler，
         * 避免传入 null MetaTileEntity 导致的潜在 NPE。
         * 槽位 0 存储电路 ItemStack（集成电路或自定义物品）。
         */
        private final ItemStackHandler circuitSlot;
        private final IItemHandlerModifiable combinedItemHandler;
        private final DualHandler dualHandler;
        private BufferSignature signature;

        public PatternBuffer(int itemSlots, int fluidSlots, int tankCapacity) {
            this.itemHandler = new NotifiableItemStackHandler(null, itemSlots, null, false);
            IFluidTank[] tanks = new IFluidTank[fluidSlots];
            for (int i = 0; i < fluidSlots; i++) {
                tanks[i] = new NotifiableFluidTank(tankCapacity, null, false);
            }
            this.fluidHandler = new FluidTankList(false, tanks);
            this.circuitSlot = new ItemStackHandler(1);
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
         */
        public void setCustomCircuit(@NotNull ItemStack stack) {
            if (stack.isEmpty()) {
                circuitSlot.setStackInSlot(0, ItemStack.EMPTY);
            } else {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                circuitSlot.setStackInSlot(0, copy);
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
            // 检查电路
            if (!circuitSlot.getStackInSlot(0).isEmpty()) return false;

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
            circuitSlot.setStackInSlot(0, ItemStack.EMPTY);
            this.signature = null;
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

            // 序列化电路
            ItemStack circuit = circuitSlot.getStackInSlot(0);
            if (!circuit.isEmpty()) {
                tag.setTag("CircuitItem", circuit.writeToNBT(new NBTTagCompound()));
            }

            // 序列化签名
            if (signature != null) {
                tag.setTag("Signature", signature.writeToNBT());
            }

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

            // 反序列化电路
            if (tag.hasKey("CircuitItem", Constants.NBT.TAG_COMPOUND)) {
                ItemStack circuit = new ItemStack(tag.getCompoundTag("CircuitItem"));
                circuitSlot.setStackInSlot(0, circuit);
            }

            // 反序列化签名
            if (tag.hasKey("Signature", Constants.NBT.TAG_COMPOUND)) {
                this.signature = BufferSignature.readFromNBT(tag.getCompoundTag("Signature"));
            }
        }
    }
}
