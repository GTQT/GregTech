package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.capability.impl.LargeSlotItemStackHandler;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ItemSlot;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import gtqt.common.metatileentities.GTQTMetaTileEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.util.text.TextFormatting.GREEN;

public class MetaTileEntityHugeComplexDualHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<DualHandler>, IControllable, IGhostSlotConfigurable {

    private final int numSlots;
    private final int tankSize;
    // only holding this for convenience
    private final FluidTankList fluidTankList;
    //item
    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    private IItemHandlerModifiable actualImportItems;
    private LargeSlotItemStackHandler largeSlotItemStackHandler;
    private boolean workingEnabled;
    private boolean autoCollapse;

    public MetaTileEntityHugeComplexDualHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.workingEnabled = true;

        this.numSlots = getTier();
        this.tankSize = Integer.MAX_VALUE;
        FluidTank[] fluidsHandlers = new FluidTank[numSlots];
        for (int i = 0; i < fluidsHandlers.length; i++) {
            fluidsHandlers[i] = new NotifiableFluidTank(tankSize, this, true);
        }
        this.fluidTankList = new FluidTankList(false, fluidsHandlers);

        initializeInventory();
    }

    public int getSlotByTier() {
        return getTier() * getTier();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHugeComplexDualHatch(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        this.largeSlotItemStackHandler = new LargeSlotItemStackHandler(this, getInventorySize(), null, false,
                () -> Integer.MAX_VALUE);

        if (this.hasGhostCircuitInventory()) {
            this.circuitInventory = new GhostCircuitItemStackHandler(this);
            this.circuitInventory.addNotifiableMetaTileEntity(this);
            this.actualImportItems = new ItemHandlerList(
                    Arrays.asList(largeSlotItemStackHandler, this.circuitInventory));
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
    public IItemHandlerModifiable getExportItems() {
        return this.actualImportItems == null ? largeSlotItemStackHandler : this.actualImportItems;
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        if (hasGhostCircuitInventory() && this.actualImportItems instanceof ItemHandlerList) {
            for (IItemHandler handler : ((ItemHandlerList) this.actualImportItems).getBackingHandlers()) {
                if (handler instanceof INotifiableHandler notifiable) {
                    notifiable.addNotifiableMetaTileEntity(controllerBase);
                    notifiable.addToNotifiedList(this, handler, true);
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
        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0) {
            if (workingEnabled) {
                long timer = getOffsetTimer() / 5;
                if (timer % 2 == 0) {
                    // 偶数时刻执行push操作
                    pushItemsIntoNearbyHandlers(getFrontFacing());
                    pushFluidsIntoNearbyHandlers(getFrontFacing());
                } else {
                    // 奇数时刻执行pull操作
                    pullItemsFromNearbyHandlers(getFrontFacing());
                    pullFluidsFromNearbyHandlers(getFrontFacing());
                }
            }
            // Only attempt to auto collapse the inventory contents once the bus has been notified
            if (isAutoCollapse()) {
                // Exclude the ghost circuit inventory from the auto collapse, so it does not extract any ghost circuits
                // from the slot
                IItemHandlerModifiable inventory = actualImportItems;
                if (!isAttachedToMultiBlock() || (this.getNotifiedItemInputList().contains(inventory))) {
                    GTUtility.collapseInventorySlotContents(inventory);
                }

                FluidTankList fluidInventory = fluidTankList;
                if (!isAttachedToMultiBlock() || (this.getNotifiedFluidInputList().contains(fluidInventory))) {
                    GTUtility.collapseFluidTankContents(fluidInventory);
                }
            }
        }
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
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

            SimpleOverlayRenderer overlay = Textures.COMPLEX_DUAL_HATCH;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    private int getInventorySize() {
        return getSlotByTier();
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new LargeSlotItemStackHandler(this, getInventorySize(), getController(), true);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new LargeSlotItemStackHandler(this, getInventorySize(), getController(), false);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return fluidTankList;
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return fluidTankList;
    }

    @Override
    public MultiblockAbility<DualHandler> getAbility() {
        return MultiblockAbility.COMPLEX_DUAL;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
        buf.writeBoolean(autoCollapse);
        for (var tank : fluidTankList.getFluidTanks()) {
            NetworkUtils.writeFluidStack(buf, tank.getFluid());
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
        this.autoCollapse = buf.readBoolean();
        for (var tank : fluidTankList.getFluidTanks()) {
            var fluid = NetworkUtils.readFluidStack(buf);
            tank.fill(fluid, true);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("largeSlotItemStackHandler", this.largeSlotItemStackHandler.serializeNBT());
        data.setBoolean("workingEnabled", workingEnabled);
        data.setBoolean("autoCollapse", autoCollapse);
        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.largeSlotItemStackHandler.deserializeNBT(data.getCompoundTag("largeSlotItemStackHandler"));
        if (data.hasKey("workingEnabled")) {
            this.workingEnabled = data.getBoolean("workingEnabled");
        }
        if (data.hasKey("autoCollapse")) {
            this.autoCollapse = data.getBoolean("autoCollapse");
        }
        if (this.circuitInventory != null) {
            this.circuitInventory.read(data);
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS) {
            this.autoCollapse = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            this.workingEnabled = buf.readBoolean();
        }
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (this.hasGhostCircuitInventory() && this.actualImportItems != null) {
            abilityInstances.add(new DualHandler(this.actualImportItems, exportFluids, true));
            abilityInstances.add(new DualHandler(this.actualImportItems, importFluids, false));

        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager) {
        int rowSize = getTier();
        guiSyncManager.registerSlotGroup("item_inv", rowSize);

        int backgroundWidth = Math.max(
                9 * 18 + 18 + 14 + 5,   // Player Inv width
                (rowSize + 1) * 18 + 14); // Bus Inv width
        int backgroundHeight = 18 + 18 * rowSize + 94;

        List<List<IWidget>> widgets = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgets.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;
                IItemHandlerModifiable handler = largeSlotItemStackHandler;
                widgets.get(i)
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
            widgets.get(i).add(new GTFluidSlot()
                    .syncHandler(GTFluidSlot.sync(fluidTankList.getTankAt(i))
                            .accessibility(true, true))
            );
        }

        BooleanSyncValue workingStateValue = new BooleanSyncValue(() -> workingEnabled, val -> workingEnabled = val);
        guiSyncManager.syncValue("working_state", workingStateValue);
        BooleanSyncValue collapseStateValue = new BooleanSyncValue(() -> autoCollapse, val -> autoCollapse = val);
        guiSyncManager.syncValue("collapse_state", collapseStateValue);

        boolean hasGhostCircuit = hasGhostCircuitInventory() && this.circuitInventory != null;

        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory().left(7).bottom(7))
                .child(new Grid()
                        .top(18).height(rowSize * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .leftRel(0.5f)
                        .matrix(widgets))

                .child(Flow.column()
                        .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 * 4 - 7 - 5)
                        .width(18).height(18 * 4 + 5)
                        .child(GTGuiTextures.getLogo(getUITheme()).asWidget().size(17).top(18 * 3 + 5))
                        .child(new ToggleButton()
                                .top(18 * 2)
                                .value(new BoolValue.Dynamic(workingStateValue::getBoolValue,
                                        workingStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_OUTPUT)
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine((workingStateValue.getBoolValue() ?
                                                IKey.lang("gregtech.gui.complex_dual_auto_io.tooltip.enabled") :
                                                IKey.lang("gregtech.gui.complex_dual_auto_io.tooltip.disabled")))
                                ))

                        .child(new ToggleButton()
                                .top(18)
                                .value(new BoolValue.Dynamic(collapseStateValue::getBoolValue,
                                        collapseStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_COLLAPSE)
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(collapseStateValue.getBoolValue() ?
                                                IKey.lang("gregtech.gui.dual_auto_collapse.tooltip.enabled") :
                                                IKey.lang("gregtech.gui.dual_auto_collapse.tooltip.disabled"))))
                        .childIf(hasGhostCircuit, new GhostCircuitSlotWidget()
                                .slot(SyncHandlers.itemSlot(circuitInventory, 0))
                                .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY))
                        .childIf(!hasGhostCircuit, new Widget<>()
                                .background(GTGuiTextures.SLOT, GTGuiTextures.BUTTON_X)
                                .tooltip(t -> t.addLine(
                                        IKey.lang("gregtech.gui.configurator_slot.unavailable.tooltip"))))
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

    public boolean isAutoCollapse() {
        return autoCollapse;
    }

    public void setAutoCollapse(boolean inverted) {
        autoCollapse = inverted;
        if (!getWorld().isRemote) {
            if (autoCollapse) {

                addNotifiedOutput(this.getExportItems());
                addNotifiedOutput(this.getExportFluids());

                addNotifiedInput(this.getImportItems());
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
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.complex_dual_hatch.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.complex_dual_hatch.tooltip.2"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getInventorySize()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", numSlots, tankSize));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
        tooltip.add(GREEN + I18n.format("gregtech.machine.super_item_bus.tooltip"));
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
        if (this == GTQTMetaTileEntities.HUGE_COMPLEX_DUAL_HATCH[0]) {
            for (var hatch : GTQTMetaTileEntities.HUGE_COMPLEX_DUAL_HATCH) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
        } else if (this.getClass() != MetaTileEntityHugeComplexDualHatch.class) {
            // let subclasses fall through this override
            super.getSubItems(creativeTab, subItems);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        for (int i = 0; i < largeSlotItemStackHandler.getSlots(); i++) {
            var pos = getPos();
            if (!largeSlotItemStackHandler.getStackInSlot(i).isEmpty()) {
                getWorld().spawnEntity(new EntityItem(getWorld(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        largeSlotItemStackHandler.getStackInSlot(i)));
                largeSlotItemStackHandler.extractItem(i, 1, false);
            }
        }
    }
}
