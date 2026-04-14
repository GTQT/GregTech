package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.implementations.ICraftingPatternItem;
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
import gtqt.api.util.PatternUtils;
import gtqt.common.metatileentities.GTQTMetaTileEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MetaTileEntityMEPatternProvider extends MetaTileEntityAECraftingPart {

    public MetaTileEntityMEPatternProvider(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, false);
        patternDetails = new ArrayList<>(Collections.nCopies(getItemSize(), null));
        initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEPatternProvider(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.patternSlot = new ItemStackHandler(getItemSize()) {

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
        this.extraItem = new NotifiableItemStackHandler(this, getTankSize() + 1, null, false);

        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                Arrays.asList(this.importItems, this.circuitInventory, extraItem));

        dualHandler = new DualHandler(
                this.actualImportItems,
                getImportFluids(),
                isExportHatch);
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return dualHandler;
    }

    protected IFluidTank[] createTanks() {
        int size = getTankSize();
        IFluidTank[] tanks = new IFluidTank[size];
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = new NotifiableFluidTank(getTankCapacity(), null, isExportHatch);
        }
        return tanks;
    }

    protected int getTankSize() {
        return 1 + Math.min(GTValues.UHV, getTier());
    }

    protected int getItemSize() {
        return getTankSize() * getTankSize();
    }

    protected int getTankCapacity() {
        return 8_000 * Math.min(Integer.MAX_VALUE, 1 << getTier());
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, getItemSize(), null, false);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false, createTanks());
    }

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
                    IItemHandlerModifiable itemHandler = importItems;
                    if (!isAttachedToMultiBlock() ||
                            (isExportHatch ? getNotifiedItemOutputList().contains(itemHandler) :
                                    getNotifiedItemInputList().contains(itemHandler))) {
                        GTUtility.collapseInventorySlotContents(itemHandler);
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

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.ME_BUFFER_HATCH_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

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
        data.setBoolean("advancedCircuit", isAdvancedCircuit());
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
        setAdvancedCircuit(data.getBoolean("advancedCircuit"));
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
    }

    @Override
    public void setPatternDetails() {
        for (int i = 0; i < getItemSize(); i++) {
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
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = getTankSize();
        guiSyncManager.registerSlotGroup("item_inv", rowSize);

        int backgroundWidth = Math.max(
                9 * 18 + 18 + 14 + 5 + 18,   // Player Inv width
                (rowSize + 1) * 18 + 14 + 18); // Bus Inv width
        int backgroundHeight = 18 + 18 * Math.max(4, rowSize) + 94;

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

        BooleanSyncValue ghostCircuitStateValue = new BooleanSyncValue(this::isAdvancedCircuit,
                this::setAdvancedCircuit);
        guiSyncManager.syncValue("ghost_circuit_state", ghostCircuitStateValue);

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

                        .child(new ToggleButton()
                                .top(0)
                                .left(18)
                                .value(new BoolValue.Dynamic(ghostCircuitStateValue::getBoolValue,
                                        ghostCircuitStateValue::setBoolValue))
                                .overlay(GTGuiTextures.CIRCUIT_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("高级样板电路"))))

                );
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        // override here is gross, but keeps things in order despite
        // IDs being out of order, due to UEV+ being added later
        if (this == GTQTMetaTileEntities.ME_PATTERN_PROVIDER[0]) {
            for (var hatch : GTQTMetaTileEntities.ME_PATTERN_PROVIDER) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
        } else if (this.getClass() != MetaTileEntityMEPatternProvider.class) {
            // let subclasses fall through this override
            super.getSubItems(creativeTab, subItems);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.4"));
        tooltip.add(I18n.format("gregtech.machine.dual_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getItemSize()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", getTankSize(),
                getTankCapacity()));
        tooltip.add(I18n.format("gregtech.machine.me.data_stick_proxy"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

}
