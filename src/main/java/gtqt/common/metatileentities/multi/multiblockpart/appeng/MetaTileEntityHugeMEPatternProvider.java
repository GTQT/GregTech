package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.capability.impl.LargeSlotItemStackHandler;
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
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.AEApi;
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
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
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
import com.cleanroommc.modularui.network.NetworkUtils;
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
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.common.item.fake.FakeItemRegister;
import gtqt.api.util.PatternUtils;
import gtqt.common.metatileentities.GTQTMetaTileEntities;
import lombok.Getter;
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
import static net.minecraft.util.text.TextFormatting.GREEN;

public class MetaTileEntityHugeMEPatternProvider extends MetaTileEntityMEControlBase
        implements IMultiblockAbilityPart<DualHandler>, IGhostSlotConfigurable,
                   ICraftingProvider, IAEFluidInventory, IDataStickIntractable,
                   IGridProxyable, IPowerChannelState {

    private static final IDrawable CHEST = new ItemDrawable(new ItemStack(Blocks.CHEST))
            .asIcon().size(16);
    private final IDrawable HATCH = new ItemDrawable(getStackForm())
            .asIcon().size(16);
    private final IDrawable PROXY = new ItemDrawable(Mods.AppliedEnergistics2.getItem("interface"))
            .asIcon().size(16);
    private final IDrawable TERMINAL = new ItemDrawable(new ItemStack(Items.NAME_TAG))
            .asIcon().size(16);

    private final int numSlots;
    private final int tankSize;
    // only holding this for convenience
    private final FluidTankList fluidTankList;
    private final List<ICraftingPatternDetails> patternDetails;
    //item
    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    //AE
    boolean export;
    int aeProxy_x;
    int aeProxy_y;
    int aeProxy_z;
    boolean useProxy;
    private LargeSlotItemStackHandler largeSlotItemStackHandler;
    @Getter
    private IItemHandlerModifiable actualImportItems;
    @Getter
    private boolean autoCollapse;
    @Getter
    private ItemStackHandler patternSlot;
    private boolean needPatternSync = true;
    private IItemHandlerModifiable extraItem;
    // Controls blocking
    private boolean isBlockedMode = true;
    private boolean patternDeal = false;
    private boolean advancedCircuit = false;
    private int parallel;
    private int lastParallel;
    //样板管理
    @Getter
    private String showName = IKey.lang(this.getMetaFullName()).toString();
    @Getter
    private boolean hideInfo = true;

    public MetaTileEntityHugeMEPatternProvider(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, false);
        this.numSlots = getTier();
        this.tankSize = Integer.MAX_VALUE;
        FluidTank[] fluidsHandlers = new FluidTank[numSlots];
        for (int i = 0; i < fluidsHandlers.length; i++) {
            fluidsHandlers[i] = new NotifiableFluidTank(tankSize, this, false);
        }
        this.fluidTankList = new FluidTankList(false, fluidsHandlers);

        patternDetails = new ArrayList<>(Collections.nCopies(getSlotByTier(), null));
        initializeInventory();
    }

    public int getSlotByTier() {
        return getTier() * getTier();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHugeMEPatternProvider(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        this.patternSlot = new ItemStackHandler(getSlotByTier()) {

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
        this.extraItem = new NotifiableItemStackHandler(this, getTier() + 1, null, false);
        this.largeSlotItemStackHandler = new LargeSlotItemStackHandler(this, getSlotByTier(), null, false,
                () -> Integer.MAX_VALUE);

        if (this.hasGhostCircuitInventory()) {
            this.circuitInventory = new GhostCircuitItemStackHandler(this);
            this.circuitInventory.addNotifiableMetaTileEntity(this);
            this.actualImportItems = new ItemHandlerList(
                    Arrays.asList(largeSlotItemStackHandler, this.circuitInventory, extraItem));
        } else {
            this.actualImportItems = null;
        }
        this.importItems = createImportItemHandler();
        this.exportItems = createExportItemHandler();
        this.itemInventory = new ItemHandlerProxy(importItems, exportItems);

        if (this.fluidTankList == null) return;
        this.importFluids = createImportFluidHandler();
        this.exportFluids = createExportFluidHandler();
        this.fluidInventory = new FluidHandlerProxy(importFluids, exportFluids);
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return this.actualImportItems == null ? largeSlotItemStackHandler : this.actualImportItems;
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        if (hasGhostCircuitInventory() && this.actualImportItems instanceof ItemHandlerList) {
            for (IItemHandler handler : ((ItemHandlerList) this.actualImportItems).getBackingHandlers()) {
                if (handler instanceof INotifiableHandler notifiable) {
                    notifiable.addNotifiableMetaTileEntity(controllerBase);
                    notifiable.addToNotifiedList(this, handler, false);
                }
            }
        }
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        super.removeFromMultiBlock(controllerBase);
        if (hasGhostCircuitInventory() && this.actualImportItems instanceof ItemHandlerList) {
            for (IItemHandler handler : ((ItemHandlerList) this.actualImportItems).getBackingHandlers()) {
                if (handler instanceof INotifiableHandler notifiable) {
                    notifiable.removeNotifiableMetaTileEntity(controllerBase);
                }
            }
        }
    }

    @Override
    public void update() {
        super.update();
        if (patternDeal && getOffsetTimer() % 20 == 0) {
            if (isAttachedToMultiBlock()) {
                MultiblockControllerBase controllerBase = getController();
                if (controllerBase instanceof RecipeMapMultiblockController controller) {
                    if (controller.getRecipeMapWorkable().getParallelLimit() != 0) {

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

        // Only attempt to auto collapse the inventory contents once the bus has been notified
        if (isAutoCollapse()) {
            // Exclude the ghost circuit inventory from the auto collapse, so it does not extract any ghost circuits
            // from the slot
            IItemHandlerModifiable inventory = (largeSlotItemStackHandler);
            if (!isAttachedToMultiBlock() || (this.getNotifiedItemInputList().contains(inventory))) {
                GTUtility.collapseInventorySlotContents(inventory);
            }

            FluidTankList fluidInventory = (this.getImportFluids());
            if (!isAttachedToMultiBlock()) {
                GTUtility.collapseFluidTankContents(fluidInventory);
            }
        }
        if (export) {
            returnItems();
            returnFluids();
        }
    }

    public void pushToGridCache(){
        if(useProxy) {
            try {
                if(getProxy()!=null&&getProxy().getGrid()!=null)
                    getProxy().getGrid().getCache(ICraftingGrid.class).addNode(getProxy().getNode(), this);
            } catch (GridAccessException ignored) {

            }
        }
    }
    public void removeFromGridCache(){
        if(useProxy) {
            try {
                if(getProxy()!=null&&getProxy().getGrid()!=null)
                    getProxy().getGrid().getCache(ICraftingGrid.class).removeNode(getProxy().getNode(), this);
            } catch (GridAccessException ignored) {

            }
        }
    }

    private void returnFluids() {
        if (checkIfFluidEmpty()) return;
        IMEMonitor<IAEFluidStack> monitor = getFluidMonitor();
        if (monitor == null) return;
        for (int x = 0; x < this.fluidTankList.getTanks(); x++)
            handleEmptyFluidTarget(monitor, fluidTankList.getTankAt(x));
    }

    private void handleEmptyFluidTarget(IMEMonitor<IAEFluidStack> monitor, IFluidTank exportTank) {
        FluidStack exportFluid = exportTank.getFluid();
        if (exportFluid != null) {
            IAEFluidStack aeFluid = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
                    .createStack(exportFluid);
            if (aeFluid != null) {
                IAEFluidStack remaining = monitor.injectItems(aeFluid, Actionable.MODULATE, getActionSource());
                if (remaining != null) {
                    exportTank.drain((int) (aeFluid.getStackSize() - remaining.getStackSize()), true);
                } else {
                    exportTank.drain(exportFluid.amount, true);
                }
            }
        }
    }

    private void returnItems() {
        if (checkIfEmpty()) return;

        IMEMonitor<IAEItemStack> monitor = getItemMonitor();
        if (monitor == null) return;

        for (int x = 0; x < this.importItems.getSlots(); x++) {
            ItemStack itemStack = this.importItems.getStackInSlot(x);
            if (itemStack.isEmpty()) continue;

            IAEItemStack iaeItemStack = AEItemStack.fromItemStack(itemStack);

            IAEItemStack notInserted = monitor.injectItems(iaeItemStack, Actionable.MODULATE, getActionSource());
            if (notInserted != null && notInserted.getStackSize() > 0) {
                itemStack.setCount((int) notInserted.getStackSize());
            } else {
                this.importItems.setStackInSlot(x, ItemStack.EMPTY);
            }
        }
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
        if (capability.equals(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.largeSlotItemStackHandler);
        }
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
    protected IItemHandlerModifiable createImportItemHandler() {
        return new LargeSlotItemStackHandler(this, getSlotByTier(), getController(), false);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return fluidTankList;
    }

    @Override
    public MultiblockAbility<DualHandler> getAbility() {
        return MultiblockAbility.DUAL_IMPORT;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        for (var tank : fluidTankList.getFluidTanks()) {
            NetworkUtils.writeFluidStack(buf, tank.getFluid());
        }
        buf.writeBoolean(this.isBlockedMode);
        buf.writeBoolean(this.export);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        for (var tank : fluidTankList.getFluidTanks()) {
            var fluid = NetworkUtils.readFluidStack(buf);
            tank.fill(fluid, true);
        }
        this.isBlockedMode = buf.readBoolean();
        this.export = buf.readBoolean();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("Pattern", this.patternSlot.serializeNBT());
        data.setTag("largeSlotItemStackHandler", this.largeSlotItemStackHandler.serializeNBT());
        data.setBoolean("BlockingEnabled", this.isBlockedMode);
        data.setBoolean("Export", this.export);
        data.setBoolean("patternDeal", this.patternDeal);
        data.setBoolean("advancedCircuit", this.advancedCircuit);
        data.setInteger("parallel", this.parallel);
        data.setInteger("lastParallel", this.lastParallel);

        data.setBoolean("useProxy", this.useProxy);
        data.setInteger("aeProxy_x", this.aeProxy_x);
        data.setInteger("aeProxy_y", this.aeProxy_y);
        data.setInteger("aeProxy_z", this.aeProxy_z);

        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }

        data.setBoolean("hideInfo", this.hideInfo);
        data.setString("showName", this.showName);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.patternSlot.deserializeNBT(data.getCompoundTag("Pattern"));
        setPatternDetails();
        this.largeSlotItemStackHandler.deserializeNBT(data.getCompoundTag("largeSlotItemStackHandler"));
        this.isBlockedMode = data.getBoolean("BlockingEnabled");
        this.export = data.getBoolean("Export");
        this.patternDeal = data.getBoolean("patternDeal");
        this.advancedCircuit = data.getBoolean("advancedCircuit");
        this.parallel = data.getInteger("parallel");
        this.lastParallel = data.getInteger("lastParallel");

        this.useProxy = data.getBoolean("useProxy");
        this.aeProxy_x = data.getInteger("aeProxy_x");
        this.aeProxy_y = data.getInteger("aeProxy_y");
        this.aeProxy_z = data.getInteger("aeProxy_z");

        if (this.circuitInventory != null) {
            this.circuitInventory.read(data);
        }

        this.hideInfo = data.getBoolean("hideInfo");
        this.showName = data.getString("showName");
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_ACTIVE) {
            this.isBlockedMode = buf.readBoolean();
        }
    }

    @Override
    public AENetworkProxy getProxy() {
        if (useProxy) {
            if (this.getWorld() != null) {
                TileEntity tileEntity = this.getWorld().getTileEntity(new BlockPos(aeProxy_x, aeProxy_y, aeProxy_z));
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
        for (int i = 0; i < getSlotByTier(); i++) {
            if (patternDetails.get(i) != null) iCraftingProviderHelper.addCraftingOption(this, patternDetails.get(i));
        }
    }

    private void setPatternDetails() {
        for (int i = 0; i < getSlotByTier(); i++) {
            ItemStack pattern = patternSlot.getStackInSlot(i);
            if (pattern.isEmpty()) {
                patternDetails.set(i, null);
                continue;
            }

            if (pattern.getItem() instanceof ICraftingPatternItem patternItem) {
                patternDetails.set(i, patternItem.getPatternForItem(pattern, getWorld()));
            }
        }
        removeFromGridCache();
        pushToGridCache();
    }

    @Override
    public void onRemoval() {
        if(useProxy) {
            removeFromGridCache();
            useProxy = false;
            getProxy();
        }
        super.onRemoval();
        GTTransferUtils.dropInventoryItems(getWorld(),getPos(), largeSlotItemStackHandler);
        GTTransferUtils.dropInventoryItems(getWorld(),getPos(), patternSlot);
        GTTransferUtils.dropInventoryItems(getWorld(),getPos(), extraItem);
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (this.hasGhostCircuitInventory() && this.actualImportItems != null) {
            abilityInstances.add(new DualHandler(this.actualImportItems,
                    importFluids, true));

        } else {
            abilityInstances.add(new DualHandler(this.importItems,
                    importFluids, false));
        }
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

                IItemHandlerModifiable handler = largeSlotItemStackHandler;
                widgetsItem.get(i)
                        .add(new ItemSlot()
                                .slot(new ModularSlot(handler, index) {

                                    @Override
                                    public int getSlotStackLimit() {
                                        return Integer.MAX_VALUE;
                                    }
                                }
                                        .ignoreMaxStackSize(true)
                                        .slotGroup("item_inv")
                                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                            if (onlyAmountChanged &&
                                                    handler instanceof GTItemStackHandler gtHandler) {
                                                gtHandler.onContentsChanged(index);
                                            }
                                        })
                                        .accessibility(true, true)));
            }
            widgetsItem.get(i).add(new GTFluidSlot()
                    .syncHandler(GTFluidSlot.sync(fluidTankList.getTankAt(i))
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
                () -> "X:" + aeProxy_x,  // 显示时带前缀
                str -> {
                    // 移除前缀并解析
                    if (str.startsWith("X:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("x:")) {
                        str = str.substring(2);
                    }
                    try {
                        aeProxy_x = Integer.parseInt(str.trim());
                    } catch (NumberFormatException e) {
                        // 解析失败时保持原值
                        System.err.println("Invalid X coordinate: " + str);
                    }
                }
        );

        StringSyncValue displayYValue = new StringSyncValue(
                () -> "Y:" + aeProxy_y,
                str -> {
                    if (str.startsWith("Y:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("y:")) {
                        str = str.substring(2);
                    }
                    try {
                        aeProxy_y = Integer.parseInt(str.trim());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Y coordinate: " + str);
                    }
                }
        );

        StringSyncValue displayZValue = new StringSyncValue(
                () -> "Z:" + aeProxy_z,
                str -> {
                    if (str.startsWith("Z:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("z:")) {
                        str = str.substring(2);
                    }
                    try {
                        aeProxy_z = Integer.parseInt(str.trim());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Z coordinate: " + str);
                    }
                }
        );

        // 注册同步值
        BooleanSyncValue useProxyStateValue = new BooleanSyncValue(() -> useProxy, val -> useProxy = val);
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
                () -> showName,
                str -> {
                    if (str != null) {
                        this.showName = str;
                    } else {
                        this.showName = IKey.lang(this.getMetaFullName()).toString();
                    }
                }
        );
        
        BooleanSyncValue blockStateValue = new BooleanSyncValue(() -> isBlockedMode, val -> isBlockedMode = val);
        guiSyncManager.syncValue("block_state", blockStateValue);

        BooleanSyncValue collapseStateValue = new BooleanSyncValue(() -> autoCollapse, val -> autoCollapse = val);
        guiSyncManager.syncValue("collapse_state", collapseStateValue);

        BooleanSyncValue exportStateValue = new BooleanSyncValue(() -> export, val -> export = val);
        guiSyncManager.syncValue("export_state", exportStateValue);

        BooleanSyncValue patternStateValue = new BooleanSyncValue(() -> patternDeal, val -> patternDeal = val);
        guiSyncManager.syncValue("pattern_state", patternStateValue);

        BooleanSyncValue ghostCircuitStateValue = new BooleanSyncValue(() -> advancedCircuit, val -> advancedCircuit = val);
        guiSyncManager.syncValue("ghost_circuit_state", ghostCircuitStateValue);

        BooleanSyncValue showInfoStateValue = new BooleanSyncValue(() -> hideInfo, val -> hideInfo = val);
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
                                        .childIf(useProxy, () -> Column.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("无线代理模式")))
                                                .childIf(useProxy, () -> {
                                                    TileEntity tileEntity = this.getWorld().getTileEntity(
                                                            new BlockPos(aeProxy_x, aeProxy_y, aeProxy_z));
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
                                                                        "坐标:" + aeProxy_x + ", " + aeProxy_y + ", " +
                                                                                aeProxy_z
                                                                )));
                                                    }
                                                })
                                        )
                                        .childIf(!useProxy, () -> Column.column() // 创建多行文本列
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
                                                    if (str == null || str.isEmpty()) return IKey.lang(this.getMetaFullName()).toString();
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
    public boolean hasGhostCircuitInventory() {
        return true;
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        setAutoCollapse(!this.autoCollapse);

        if (!getWorld().isRemote) {
            if (this.autoCollapse) {
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
            if (autoCollapse) {
                addNotifiedInput(largeSlotItemStackHandler);
                addNotifiedInput(this.getImportFluids());
            }
            writeCustomData(GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS,
                    packetBuffer -> packetBuffer.writeBoolean(autoCollapse));
            notifyBlockUpdate();
            markDirty();
        }
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
    public int getGhostCircuitConfig() {
        if (this.circuitInventory == null) {
            return 0;
        }
        return this.circuitInventory.getCircuitValue();
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
        if (this == GTQTMetaTileEntities.HUGE_ME_PATTERN_PROVIDER[0]) {
            for (var hatch : GTQTMetaTileEntities.HUGE_ME_PATTERN_PROVIDER) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
        } else if (this.getClass() != MetaTileEntityHugeMEPatternProvider.class) {
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
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            // 处理假流体/气体物品
            if (FakeFluids.isFluidFakeItem(itemStack)) {
                FluidStack fluid = FakeItemRegister.getStack(itemStack);
                if (fluid != null) {
                    if (fluidTankList.fill(fluid, false) < fluid.amount) {
                        return false;
                    }
                    continue;
                }
            }

            // 处理集成电路 - 模拟阶段
            if (advancedCircuit && isOnline && MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                IMEMonitor<IAEItemStack> monitor = getItemMonitor();
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                if (aeStack != null) {
                    // 模拟注入网络，检查是否可返还
                    IAEItemStack remaining = monitor.injectItems(aeStack, Actionable.SIMULATE, getActionSource());
                    if (remaining != null && remaining.getStackSize() > 0) {
                        return false; // 网络无法完全接收物品
                    }
                }
                continue; // 跳过容器插入检查
            }

            // 普通物品模拟插入检查
            ItemStack simulated = itemStack.copy();
            for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                ItemStack remaining = importItems.insertItem(slot, simulated, true);
                if (remaining.getCount() < simulated.getCount()) {
                    simulated.shrink(simulated.getCount() - remaining.getCount());
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
                    fluidTankList.fill(fluid, true);
                    continue;
                }
            }

            // 处理集成电路 - 实际执行阶段
            if (advancedCircuit && isOnline && MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
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

            // 普通物品实际插入
            ItemStack toInsert = itemStack.copy();
            for (int slot = 0; slot < importItems.getSlots() && !toInsert.isEmpty(); slot++) {
                toInsert = importItems.insertItem(slot, toInsert, false);
            }
        }

        return true;
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails iCraftingPatternDetails, InventoryCrafting inventoryCrafting) {
        if (!isActive()) return false;

        if (checkIfEmpty() && checkIfFluidEmpty()) {
            try {
                return addItemAndFluid(inventoryCrafting);
            } catch (GridAccessException e) {
                GTLog.logger.warn("Grid access failed", e);
            }
        }

        if (isBlockedMode) {
            for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
                ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
                if (itemStack.isEmpty()) continue;
                if (MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) continue;
                // 处理流体假物品
                if (FakeFluids.isFluidFakeItem(itemStack)) {
                    FluidStack fluid = FakeItemRegister.getStack(itemStack);
                    if (fluid == null) return false;

                    boolean fluidExists = false;
                    for (IFluidTank tank : fluidTankList) {
                        FluidStack tankFluid = tank.getFluid();
                        if (tankFluid != null && tankFluid.isFluidEqual(fluid)) {
                            fluidExists = true;
                            break;
                        }
                    }
                    if (!fluidExists) return false;
                }
                // 处理普通物品
                else {
                    boolean itemExists = false;
                    for (int slot = 0; slot < importItems.getSlots(); slot++) {
                        ItemStack slotStack = importItems.getStackInSlot(slot);
                        if (!slotStack.isEmpty() && slotStack.isItemEqual(itemStack)) {
                            itemExists = true;
                            break;
                        }
                    }
                    if (!itemExists) return false;
                }
            }
        }

        try {
            return addItemAndFluid(inventoryCrafting);
        } catch (GridAccessException e) {
            GTLog.logger.warn("Grid access failed", e);
            return false;
        }
    }

    @Override
    public boolean isBusy() {
        return export;
    }

    /**
     * @return false if items are in any slot, true if empty
     */
    private boolean checkIfEmpty() {
        return isInventoryEmpty(importItems);
    }

    private boolean checkIfFluidEmpty() {
        return isFluidTankListEmpty(fluidTankList);
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
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getSlotByTier()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", numSlots, tankSize));
        tooltip.add(I18n.format("gregtech.machine.me.data_stick_proxy"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
        tooltip.add(GREEN + I18n.format("gregtech.machine.super_item_bus.tooltip"));
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
            useProxy = false;
            readLocationFromTag(tag.getCompoundTag("CommonPos"));
            player.sendStatusMessage(new TextComponentTranslation("无线接入点坐标已载入"), true);
            useProxy = true;
            return true;
        }
        return false;
    }

    private void readLocationFromTag(NBTTagCompound tag) {
        this.aeProxy_x = tag.getInteger("MainX");
        this.aeProxy_y = tag.getInteger("MainY");
        this.aeProxy_z = tag.getInteger("MainZ");
    }

    @Override
    public IGridNode getGridNode(AEPartLocation aePartLocation) {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {

    }
}
