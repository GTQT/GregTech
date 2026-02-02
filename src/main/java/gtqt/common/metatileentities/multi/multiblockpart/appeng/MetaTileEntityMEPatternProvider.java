package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
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
import gregtech.api.util.Mods;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.items.MetaItems;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.util.item.AEItemStack;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
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
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.common.item.fake.FakeItemRegister;
import gtqt.api.util.PatternUtils;
import gtqt.common.metatileentities.GTQTMetaTileEntities;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_ACTIVE;
import static gtqt.api.util.GTQTUtility.isFluidTankListEmpty;
import static gtqt.api.util.GTQTUtility.isInventoryEmpty;

public class MetaTileEntityMEPatternProvider extends MetaTileEntityMEControlBase
        implements IMultiblockAbilityPart<IItemHandlerModifiable>, IGhostSlotConfigurable,
                   ICraftingProvider, IAEFluidInventory, IDataStickIntractable,
                   IGridProxyable, IPowerChannelState {

    // ICONS
    private static final IDrawable CHEST = new ItemDrawable(new ItemStack(Blocks.CHEST))
            .asIcon().size(16);
    private final IDrawable HATCH = new ItemDrawable(getStackForm())
            .asIcon().size(16);
    private final IDrawable PROXY = new ItemDrawable(Mods.AppliedEnergistics2.getItem("interface"))
            .asIcon().size(16);
    private final IDrawable TERMINAL = new ItemDrawable(new ItemStack(Items.NAME_TAG))
            .asIcon().size(16);
    @Nullable
    private final List<ICraftingPatternDetails> patternDetails;
    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    // AE
    BlockPos AEProxy_pos = new BlockPos(0, 0, 0);
    @Setter
    @Getter
    boolean useProxy;
    @Setter
    @Getter
    boolean export = false;
    // SLOTS
    @Getter
    private IItemHandlerModifiable actualImportItems;
    @Nullable
    private ItemStackHandler extraItem;
    @Getter
    @Nullable
    private ItemStackHandler patternSlot;
    @Nullable
    private DualHandler dualHandler;
    private boolean needPatternSync = true;
    private int parallel;
    private int lastParallel;
    @Getter
    private boolean autoCollapse;
    @Setter
    @Getter
    private boolean blockedMode = true;
    @Setter
    @Getter
    private boolean patternDeal = false;
    @Setter
    @Getter
    private boolean advancedCircuit = false;
    @Getter
    @Setter
    private String showName = IKey.lang(this.getMetaFullName()).toString();
    @Getter
    @Setter
    private boolean hideInfo = false;

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
                needPatternSync = true;
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
        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0) {
            if (isWorkingEnabled()) {
                if (isExportHatch) {
                    pushItemsIntoNearbyHandlers(getFrontFacing());
                    pushFluidsIntoNearbyHandlers(getFrontFacing());
                } else {
                    pullItemsFromNearbyHandlers(getFrontFacing());
                    pullFluidsFromNearbyHandlers(getFrontFacing());
                }
            }

            if (isAutoCollapse()) {
                IItemHandlerModifiable itemHandler = importItems;
                if (!isAttachedToMultiBlock() || (isExportHatch ? getNotifiedItemOutputList().contains(itemHandler) :
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
        if (!getWorld().isRemote) {
            updateMEStatus();

            if (needPatternSync && getOffsetTimer() % 10 == 0) {
                needPatternSync = MEPatternChange();
            }
        }
        if (isExport()) {
            returnToNet();
        }
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
    }

    @Override
    public int getGhostCircuitConfig() {
        if (this.circuitInventory == null) {
            return 0;
        }
        return this.circuitInventory.getCircuitValue();
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (this.circuitInventory == null || this.circuitInventory.getCircuitValue() == config) {
            return;
        }
        this.circuitInventory.setCircuitValue(config);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(dualHandler);
    }

    public void pushToGridCache() {
        if (isUseProxy()) {
            try {
                if (getProxy() != null && getProxy().getGrid() != null)
                    getProxy().getGrid().getCache(ICraftingGrid.class).addNode(getProxy().getNode(), this);
            } catch (GridAccessException ignored) {

            }
        }
    }

    public void removeFromGridCache() {
        if (isUseProxy()) {
            try {
                if (getProxy() != null && getProxy().getGrid() != null)
                    getProxy().getGrid().getCache(ICraftingGrid.class).removeNode(getProxy().getNode(), this);
            } catch (GridAccessException ignored) {

            }
        }
    }

    private void returnToNet() {
        Utils.returnItems(getItemMonitor(), getImportItems(), getActionSource());
        Utils.returnFluids(getFluidMonitor(), getImportFluids(), getActionSource());
    }

    private boolean MEPatternChange() {
        // don't post until it's active
        if (getProxy() == null || !getProxy().isActive()) return true;

        try {
            getProxy().getGrid().postEvent(new MENetworkCraftingPatternChange(this, getProxy().getNode()));
        } catch (Exception ignored) {
            return true;
        }

        return false;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
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
        buf.writeBoolean(isBlockedMode());
        buf.writeBoolean(this.export);
        buf.writeBoolean(isAutoCollapse());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        setBlockedMode(buf.readBoolean());
        setExport(buf.readBoolean());
        setAutoCollapse(buf.readBoolean());
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
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_ACTIVE) {
            setBlockedMode(buf.readBoolean());
        }
    }

    @Override
    public AENetworkProxy getProxy() {
        if (isUseProxy()) {
            if (this.getWorld() != null) {
                TileEntity tileEntity = this.getWorld().getTileEntity(AEProxy_pos);
                if (tileEntity instanceof AENetworkPowerTile proxy) {
                    return proxy.getProxy();
                }
            }
        }
        return super.getProxy();
    }

    @Override
    public AENetworkProxy createProxy() {
        AENetworkProxy proxy = new AENetworkProxy(this, "mte_proxy", this.getStackForm(), true);
        proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        proxy.setIdlePowerUsage(ConfigHolder.compat.ae2.meHatchEnergyUsage);
        proxy.setValidSides(EnumSet.of(this.getFrontFacing()));
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(getWorld(), getPos());
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper iCraftingProviderHelper) {
        if (!isActive() || patternDetails == null) return;
        for (int i = 0; i < getItemSize(); i++) {
            if (patternDetails.get(i) != null) iCraftingProviderHelper.addCraftingOption(this, patternDetails.get(i));
        }
    }

    private void setPatternDetails() {
        for (int i = 0; i < getItemSize(); i++) {
            ItemStack pattern = patternSlot.getStackInSlot(i);
            if (pattern.isEmpty()) {
                patternDetails.set(i, null);
                continue;
            }

            if (pattern.getItem() instanceof ICraftingPatternItem patternItem) {
                patternDetails.set(i, patternItem.getPatternForItem(pattern, getWorld()));
            }
        }
        if (isUseProxy()) {
            removeFromGridCache();
            pushToGridCache();
        }
    }

    @Override
    public void onRemoval() {
        if (isUseProxy()) {
            removeFromGridCache();
            setUseProxy(false);
            getProxy();
        }
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
        int rowSize = getTier();
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
                .tooltipBuilder(t -> t.setAutoUpdate(true)
                        .addLine(IKey.lang("无线代理模式"))));

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
                () -> getShowName(),
                str -> {
                    if (str != null) {
                        setShowName(str);
                    } else {
                        setShowName(IKey.lang(this.getMetaFullName()).toString());
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
                        .debugName("tab row")
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
                                Column.column() // 使用列布局
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
                                        .childIf(isUseProxy(), () -> Column.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("无线代理模式")))
                                                .childIf(isUseProxy(), () -> {
                                                    TileEntity tileEntity = this.getWorld().getTileEntity(AEProxy_pos);
                                                    if (tileEntity instanceof AENetworkPowerTile proxy) {
                                                        return Column.column()
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
                                                        return Column.column()
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
                                        .childIf(!isUseProxy(), () -> Column.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("有线代理模式")))
                                        )
                        )
                        .addPage(// 终端设置
                                Column.column()
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
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(IKey.lang("阻挡模式"))))
                        .child(new ToggleButton()
                                .top(18 * 2)
                                .left(18)
                                .value(new BoolValue.Dynamic(exportStateValue::getBoolValue,
                                        exportStateValue::setBoolValue))
                                .overlay(GTGuiTextures.EXPORT_OVERLAY)
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(IKey.lang("返回模式"))))

                        .child(new ToggleButton()
                                .top(18)
                                .value(new BoolValue.Dynamic(collapseStateValue::getBoolValue,
                                        collapseStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_COLLAPSE)
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(IKey.lang("自动整理"))))

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
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(IKey.lang("样板优化"))))

                        .child(new ToggleButton()
                                .top(0)
                                .left(18)
                                .value(new BoolValue.Dynamic(ghostCircuitStateValue::getBoolValue,
                                        ghostCircuitStateValue::setBoolValue))
                                .overlay(GTGuiTextures.CIRCUIT_OVERLAY)
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(IKey.lang("高级样板电路"))))

                );
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        setAutoCollapse(!isAutoCollapse());

        if (!getWorld().isRemote) {
            if (isAutoCollapse()) {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_true"), true);
            } else {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_false"), true);
            }
        }
        return true;
    }

    public void setAutoCollapse(boolean inverted) {
        autoCollapse = inverted;
        if (!getWorld().isRemote) {
            if (isAutoCollapse()) {
                addNotifiedInput(super.getImportItems());
                addNotifiedInput(this.getImportFluids());
            }
            writeCustomData(GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS,
                    packetBuffer -> packetBuffer.writeBoolean(isAutoCollapse()));
            notifyBlockUpdate();
            markDirty();
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_collapse"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
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
    public void gridChanged() {
        needPatternSync = true;
    }

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    public boolean addItemAndFluid(InventoryCrafting inventoryCrafting) throws GridAccessException {
        // 第一阶段：模拟检查所有物品是否可插入
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
            //实际插入的物品
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            //处理流体

            // 处理假流体/气体物品
            if (FakeFluids.isFluidFakeItem(itemStack)) {
                FluidStack fluid = FakeItemRegister.getStack(itemStack);
                if (fluid != null) {
                    if (getImportFluids().fill(fluid, false) < fluid.amount) {
                        return false;
                    }
                    continue;
                }
            }

            //处理物品

            // 处理集成电路 - 模拟阶段
            if (isAdvancedCircuit() && isOnline && MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                if (aeStack != null) {
                    // 模拟注入网络，检查是否可返还
                    IAEItemStack remaining = getItemMonitor().injectItems(aeStack, Actionable.SIMULATE,
                            getActionSource());
                    //大于0代表无法返回网络（可能是网络满了）
                    if (remaining != null && remaining.getStackSize() > 0) {
                        return false;
                    }
                }
                continue; // 跳过容器插入检查
            }

            //普通物品模拟插入检查
            //样板转化会ItemStack
            ItemStack simulated = itemStack.copy();
            //如果开了自动整理
            if (isAutoCollapse()) {
                //轮插 simulated轮询所有槽位，直到装填完毕
                for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                    ItemStack remaining = importItems.insertItem(slot, simulated, true);
                    if (remaining.getCount() < simulated.getCount()) {
                        simulated.shrink(simulated.getCount() - remaining.getCount());
                    }
                }
            } else {
                //simulated只会去填充空槽，用于装配线非64自动化
                //如果没有空槽再去轮询

                // 步骤1: 先尝试填充空槽
                for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                    // 检查是否是空槽
                    if (importItems.getStackInSlot(slot).isEmpty()) {
                        ItemStack remaining = importItems.insertItem(slot, simulated, true);
                        if (remaining.getCount() < simulated.getCount()) {
                            simulated.shrink(simulated.getCount() - remaining.getCount());
                        }
                    }
                }

                // 步骤2: 如果没有空槽或者空槽装不完，再轮询所有槽位
                if (!simulated.isEmpty()) {
                    for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                        ItemStack remaining = importItems.insertItem(slot, simulated, true);
                        if (remaining.getCount() < simulated.getCount()) {
                            simulated.shrink(simulated.getCount() - remaining.getCount());
                        }
                    }
                }
            }

            if (!simulated.isEmpty()) {
                return false;
            }
        }

        // 第二阶段：实际执行插入操作
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            // 处理假流体/气体物品
            if (FakeFluids.isFluidFakeItem(itemStack)) {
                FluidStack fluid = FakeItemRegister.getStack(itemStack);
                if (fluid != null) {
                    getImportFluids().fill(fluid, true);
                    continue;
                }
            }

            // 处理集成电路 - 实际执行阶段
            if (isAdvancedCircuit() && isOnline && MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                IMEMonitor<IAEItemStack> monitor = getItemMonitor();
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                if (aeStack != null) {
                    // 实际注入网络返还物品
                    monitor.injectItems(aeStack, Actionable.MODULATE, getActionSource());
                    // 设置机器电路配置
                    this.setGhostCircuitConfig(IntCircuitIngredient.getCircuitConfiguration(itemStack));
                }
                continue; // 跳过容器插入
            }

            // 普通物品实际插入 - 根据是否自动整理采用不同策略
            ItemStack toInsert = itemStack.copy();

            if (isAutoCollapse()) {
                // 自动整理模式：轮询所有槽位
                GTTransferUtils.insertItem(importItems, toInsert,false);
            } else {
                // 非自动整理模式：先尝试空槽，再尝试所有槽位

                // 阶段1: 只填充空槽
                for (int slot = 0; slot < importItems.getSlots() && !toInsert.isEmpty(); slot++) {
                    if (importItems.getStackInSlot(slot).isEmpty()) {
                        toInsert = importItems.insertItem(slot, toInsert, false);
                    }
                }

                // 阶段2: 如果还有剩余，再尝试所有槽位
                GTTransferUtils.insertItem(importItems, toInsert,false);
            }
        }

        return true;
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting inventoryCrafting) {
        if (!isActive()) {
            GTLog.logger.debug("Machine is not active, rejecting pattern");
            return false;
        }

        boolean isEmpty = isInventoryEmpty(getImportItems()) && isFluidTankListEmpty(getImportFluids());

        // 如果不是空容器且处于阻塞模式，进行兼容性检查
        if (!isEmpty && isBlockedMode()) {
            if (!checkBlockedModeCompatibility(inventoryCrafting)) {
                GTLog.logger.debug("Pattern rejected by blocked mode compatibility check");
                return false;
            }
        }

        try {
            return addItemAndFluid(inventoryCrafting);
        } catch (GridAccessException e) {
            GTLog.logger.warn("Grid access failed while pushing pattern", e);
            return false;
        }
    }

    private boolean checkBlockedModeCompatibility(InventoryCrafting inventoryCrafting) {
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            // 集成电路特殊处理
            if (MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                continue;
            }

            // 处理流体假物品
            if (FakeFluids.isFluidFakeItem(itemStack)) {
                FluidStack fluid = FakeItemRegister.getStack(itemStack);
                if (fluid == null || !GTUtility.hasMatchingFluid(fluid, getImportFluids())) {
                    return false;
                }
            }
            // 处理普通物品
            else {
                if (!GTUtility.hasMatchingItem(itemStack, importItems)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isBusy() {
        return isExport();
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank iaeFluidTank, int i) {
        markDirty();
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

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setTag("BudgetCRIB", writeLocationToTag());
        dataStick.setTagCompound(tag);
        dataStick.setTranslatableName("gregtech.machine.budget_crib.data_stick_name");
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.budget_crib.data_stick_use"), true);
    }

    private NBTTagCompound writeLocationToTag() {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setInteger("MainX", getPos().getX());
        tag.setInteger("MainY", getPos().getY());
        tag.setInteger("MainZ", getPos().getZ());

        return tag;
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null) return false;
        if (tag.hasKey("CommonPos")) {
            setUseProxy(false);
            readLocationFromTag(tag.getCompoundTag("CommonPos"));
            player.sendStatusMessage(new TextComponentTranslation("无线接入点坐标已载入"), true);
            setUseProxy(true);
            return true;
        }
        return false;
    }

    private void readLocationFromTag(NBTTagCompound tag) {
        AEProxy_pos = new BlockPos(tag.getInteger("MainX"), tag.getInteger("MainY"), tag.getInteger("MainZ"));
    }

    @Override
    public IGridNode getGridNode(@NotNull AEPartLocation aePartLocation) {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {

    }
}
