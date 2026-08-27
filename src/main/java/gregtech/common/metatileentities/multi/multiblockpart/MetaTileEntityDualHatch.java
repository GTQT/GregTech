package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.FilteredDualHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.covers.filter.FluidFilterContainer;
import gregtech.common.covers.filter.ItemFilterContainer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetaTileEntityDualHatch extends MetaTileEntityMultiblockNotifiablePart implements
                                                                                    IMultiblockAbilityPart<IItemHandlerModifiable>,
                                                                                    IControllable,
                                                                                    IGhostSlotConfigurable {

    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    @Nullable
    private IItemHandlerModifiable actualImportItems;
    private DualHandler dualHandler;

    private boolean workingEnabled = true;
    private boolean autoCollapse = false;
    private boolean disallowSameItemInsert = false;

    @Nullable
    private ItemFilterContainer itemFilterContainer;
    @Nullable
    private FluidFilterContainer fluidFilterContainer;

    public MetaTileEntityDualHatch(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
        if (this.isExportHatch) {
            this.itemFilterContainer = new ItemFilterContainer(this::markDirty);
            this.fluidFilterContainer = new FluidFilterContainer(this::markDirty);
        }
        initializeInventory();
        if (this.isExportHatch) {
            dualHandler = new FilteredDualHandler(dualHandler, itemFilterContainer, fluidFilterContainer);
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityDualHatch(metaTileEntityId, getTier(), isExportHatch);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        if (hasGhostCircuitInventory()) {
            circuitInventory = new GhostCircuitItemStackHandler(this);
            circuitInventory.addNotifiableMetaTileEntity(this);
            actualImportItems = new ItemHandlerList(Arrays.asList(this.importItems, circuitInventory));
        } else {
            actualImportItems = this.importItems;
        }
        dualHandler = new DualHandler(
                isExportHatch ? this.exportItems : this.actualImportItems,
                isExportHatch ? getExportFluids() : getImportFluids(),
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
        return isExportHatch ? new GTItemStackHandler(this, 0) :
                new NotifiableItemStackHandler(this, getItemSize(), null, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return isExportHatch ? new NotifiableItemStackHandler(this, getItemSize(), null, true) :
                new GTItemStackHandler(this, 0);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return isExportHatch ? new FluidTankList(false) : new FluidTankList(false, createTanks());
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return isExportHatch ? new FluidTankList(false, createTanks()) : new FluidTankList(false);
    }

    @Override
    public void update() {
        super.update();

        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0) {
            if (workingEnabled) {
                if (isExportHatch) {
                    pushItemsIntoNearbyHandlers(getFrontFacing());
                    pushFluidsIntoNearbyHandlers(getFrontFacing());
                } else {
                    pullItemsFromNearbyHandlers(getFrontFacing());
                    pullFluidsFromNearbyHandlers(getFrontFacing());
                }
            }

            if (isAutoCollapse()) {
                IItemHandlerModifiable itemHandler = isExportHatch ? getExportItems() : super.getImportItems();
                if (!isAttachedToMultiBlock() || (isExportHatch ? getNotifiedItemOutputList().contains(itemHandler) :
                        getNotifiedItemInputList().contains(itemHandler))) {
                    GTUtility.collapseInventorySlotContents(itemHandler);
                }
            }
        }
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return !this.isExportHatch;
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
    public void setGhostCustomStack(@NotNull ItemStack stack) {
        if (this.circuitInventory == null) {
            return;
        }
        this.circuitInventory.setCustomStack(stack);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return isExportHatch ? MultiblockAbility.EXPORT_ITEMS : MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(dualHandler);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = getTankSize();
        guiSyncManager.registerSlotGroup("item_inv", rowSize);

        int backgroundWidth = Math.max(
                9 * 18 + 18 + 14 + 5,   // Player Inv width
                rowSize * 18 + 14 + 18); // Bus Inv width
        int gridTop = 18;
        int backgroundHeight = gridTop + 18 * rowSize + 94;

        List<List<IWidget>> widgets = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgets.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;
                IItemHandlerModifiable handler = isExportHatch ? getExportItems() : getImportItems();
                widgets.get(i).add(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(handler, index)
                                .slotGroup("item_inv")
                                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                    if (onlyAmountChanged && handler instanceof GTItemStackHandler gtHandler) {
                                        gtHandler.onContentsChanged(index);
                                    }
                                })
                                .accessibility(!isExportHatch, true)));
            }

            IFluidTank tankHandler = dualHandler.getTankAt(i);
            widgets.get(i).add(new GTFluidSlot()
                    .syncHandler(GTFluidSlot.sync(tankHandler)
                            .accessibility(true, !isExportHatch)));
        }

        BooleanSyncValue workingStateValue = new BooleanSyncValue(() -> workingEnabled, val -> workingEnabled = val);
        guiSyncManager.syncValue("working_state", workingStateValue);
        BooleanSyncValue collapseStateValue = new BooleanSyncValue(() -> autoCollapse, val -> autoCollapse = val);
        guiSyncManager.syncValue("collapse_state", collapseStateValue);

        boolean hasGhostCircuit = hasGhostCircuitInventory() && circuitInventory != null;

        Flow column = Flow.column()
                .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 * 5 - 7 - 4)
                .width(18).height(18 * 5 + 4)
                .child(GTGuiTextures.getLogo(getUITheme()).asWidget().size(17).top(18 * 4 + 4))
                .child(new ToggleButton()
                        .top(18 * 3)
                        .value(new BoolValue.Dynamic(workingStateValue::getBoolValue,
                                workingStateValue::setBoolValue))
                        .overlay(GTGuiTextures.BUTTON_DUAL_OUTPUT)
                        .tooltipBuilder(t -> t.setAutoUpdate(true)
                                .addLine(isExportHatch ?
                                        (workingStateValue.getBoolValue() ?
                                                IKey.lang("gregtech.gui.dual_auto_output.tooltip.enabled") :
                                                IKey.lang("gregtech.gui.dual_auto_output.tooltip.disabled")) :
                                        (workingStateValue.getBoolValue() ?
                                                IKey.lang("gregtech.gui.dual_auto_input.tooltip.enabled") :
                                                IKey.lang("gregtech.gui.dual_auto_input.tooltip.disabled")))));

        if (!isExportHatch) {
            BooleanSyncValue disallowSameItemValue = new BooleanSyncValue(
                    this::isDisallowSameItemInsert, this::setDisallowSameItemInsert);
            column.child(new ToggleButton()
                    .top(18 * 2)
                    .value(disallowSameItemValue)
                    .overlay(GTGuiTextures.BUTTON_LOCK)
                    .addTooltip(true, IKey.lang("gregtech.machine.disallow_same_item.enabled"))
                    .addTooltip(false, IKey.lang("gregtech.machine.disallow_same_item.disabled")));
        } else {
            IPanelHandler filterPopup = guiSyncManager.syncedPanel("dual_filter_popup", true,
                    (psm, handler) -> {
                        Widget<?> itemRow = (Widget<?>) itemFilterContainer.initUI(guiData, psm);
                        itemRow.pos(4, 12).width(168);
                        Widget<?> fluidRow = (Widget<?>) fluidFilterContainer.initUI(guiData, psm);
                        fluidRow.pos(4, 34).width(168);
                        return GTGuis.createPopupPanel("dual_filter_popup", 176, 60, false)
                                .child(itemRow)
                                .child(fluidRow);
                    });
            column.child(new ButtonWidget<>()
                    .top(18 * 2)
                    .size(18)
                    .overlay(GTGuiTextures.FILTER_SETTINGS_OVERLAY.asIcon().size(16))
                    .addTooltipLine(IKey.str("过滤覆盖版"))
                    .onMousePressed(i -> {
                        if (!filterPopup.isPanelOpen()) filterPopup.openPanel();
                        else filterPopup.closePanel();
                        return true;
                    }));
        }

        column.child(new ToggleButton()
                        .top(18)
                        .value(new BoolValue.Dynamic(collapseStateValue::getBoolValue,
                                collapseStateValue::setBoolValue))
                        .overlay(GTGuiTextures.BUTTON_DUAL_COLLAPSE)
                        .tooltipBuilder(t -> t.setAutoUpdate(true)
                                .addLine(collapseStateValue.getBoolValue() ?
                                        IKey.lang("gregtech.gui.dual_auto_collapse.tooltip.enabled") :
                                        IKey.lang("gregtech.gui.dual_auto_collapse.tooltip.disabled"))))
                .childIf(hasGhostCircuit, () -> new GhostCircuitSlotWidget()
                        .slot(circuitInventory, 0)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY))
                .childIf(!hasGhostCircuit, () -> new Widget<>()
                        .background(GTGuiTextures.SLOT, GTGuiTextures.BUTTON_X)
                        .tooltip(t -> t.addLine(
                                IKey.lang("gregtech.gui.configurator_slot.unavailable.tooltip"))));

        ModularPanel panel = GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));

        panel.child(new Grid()
                        .top(gridTop).height(rowSize * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .alignX(0.5f)
                        .matrix(widgets))
                .child(column);

        return panel;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = isExportHatch ? Textures.DUAL_HATCH_OUTPUT_OVERLAY :
                    Textures.DUAL_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
        buf.writeBoolean(autoCollapse);
        buf.writeBoolean(disallowSameItemInsert);
        boolean hasItemFilter = itemFilterContainer != null;
        boolean hasFluidFilter = fluidFilterContainer != null;
        buf.writeBoolean(hasItemFilter);
        buf.writeBoolean(hasFluidFilter);
        if (hasItemFilter) itemFilterContainer.writeInitialSyncData(buf);
        if (hasFluidFilter) fluidFilterContainer.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        workingEnabled = buf.readBoolean();
        autoCollapse = buf.readBoolean();
        disallowSameItemInsert = buf.readBoolean();
        if (buf.readerIndex() < buf.writerIndex()) {
            boolean hasItemFilter = buf.readBoolean();
            boolean hasFluidFilter = buf.readBoolean();
            if (hasItemFilter && itemFilterContainer != null) itemFilterContainer.readInitialSyncData(buf);
            if (hasFluidFilter && fluidFilterContainer != null) fluidFilterContainer.readInitialSyncData(buf);
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
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @SuppressWarnings("DuplicatedCode")
    public void setAutoCollapse(boolean inverted) {
        autoCollapse = inverted;
        if (!getWorld().isRemote) {
            if (autoCollapse) {
                if (isExportHatch) {
                    addNotifiedOutput(getExportItems());
                } else {
                    addNotifiedInput(getImportItems());
                }
            }
            writeCustomData(GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS,
                    packetBuffer -> packetBuffer.writeBoolean(autoCollapse));
            notifyBlockUpdate();
            markDirty();
        }
    }

    public boolean isAutoCollapse() {
        return autoCollapse;
    }

    public boolean isDisallowSameItemInsert() {
        return disallowSameItemInsert;
    }

    public void setDisallowSameItemInsert(boolean disallowSameItemInsert) {
        this.disallowSameItemInsert = disallowSameItemInsert;
        if (!getWorld().isRemote) {
            IItemHandlerModifiable handler = isExportHatch ? getExportItems() : getImportItems();
            if (handler instanceof DualHandler dual) {
                handler = dual.getItemDelegate();
            }
            if (handler instanceof ItemHandlerList list) {
                for (var h : list.getBackingHandlers()) {
                    if (h instanceof GTItemStackHandler gtHandler) {
                        gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
                    }
                }
            } else if (handler instanceof GTItemStackHandler gtHandler) {
                gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
            }
            writeCustomData(GregtechDataCodes.UPDATE_DISALLOW_SAME_ITEM,
                    buf -> buf.writeBoolean(disallowSameItemInsert));
            markDirty();
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            workingEnabled = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS) {
            autoCollapse = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.UPDATE_DISALLOW_SAME_ITEM) {
            disallowSameItemInsert = buf.readBoolean();
        }
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        setAutoCollapse(!autoCollapse);

        if (!getWorld().isRemote) {
            if (autoCollapse) {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_true"), true);
            } else {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_false"), true);
            }
        }
        return true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("workingEnabled", workingEnabled);
        data.setBoolean("autoCollapse", autoCollapse);
        data.setBoolean("DisallowSameItemInsert", disallowSameItemInsert);

        if (circuitInventory != null) {
            circuitInventory.write(data);
        }
        if (itemFilterContainer != null) {
            data.setTag("OutputItemFilter", itemFilterContainer.serializeNBT());
        }
        if (fluidFilterContainer != null) {
            data.setTag("OutputFluidFilter", fluidFilterContainer.serializeNBT());
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        this.workingEnabled = data.getBoolean("workingEnabled");
        this.autoCollapse = data.getBoolean("autoCollapse");
        this.disallowSameItemInsert = data.getBoolean("DisallowSameItemInsert");
        // 同步更新底层 handler
        IItemHandlerModifiable updateHandler = isExportHatch ? getExportItems() : getImportItems();
        if (updateHandler instanceof DualHandler dual) {
            updateHandler = dual.getItemDelegate();
        }
        if (updateHandler instanceof ItemHandlerList list) {
            for (var h : list.getBackingHandlers()) {
                if (h instanceof GTItemStackHandler gtHandler) {
                    gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
                }
            }
        } else if (updateHandler instanceof GTItemStackHandler gtHandler) {
            gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
        }

        if (circuitInventory != null) {
            circuitInventory.read(data);
        }
        if (data.hasKey("OutputItemFilter") && itemFilterContainer != null) {
            itemFilterContainer.deserializeNBT(data.getCompoundTag("OutputItemFilter"));
        }
        if (data.hasKey("OutputFluidFilter") && fluidFilterContainer != null) {
            fluidFilterContainer.deserializeNBT(data.getCompoundTag("OutputFluidFilter"));
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        if (this.isExportHatch)
            tooltip.add(I18n.format("gregtech.machine.dual_hatch.import.tooltip"));
        else
            tooltip.add(I18n.format("gregtech.machine.dual_hatch.export.tooltip"));

        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getItemSize()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", getTankSize(),
                getTankCapacity()));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_collapse"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
