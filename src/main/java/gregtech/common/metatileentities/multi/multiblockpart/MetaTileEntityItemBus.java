package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.FilteredExportItemHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.covers.filter.ItemFilterContainer;

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
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static gregtech.api.util.GTUtility.collapseInventorySlotContents;

public class MetaTileEntityItemBus extends MetaTileEntityMultiblockNotifiablePart
        implements IMultiblockAbilityPart<IItemHandlerModifiable>, IControllable,
                   IGhostSlotConfigurable {

    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    private IItemHandlerModifiable actualImportItems;

    private boolean workingEnabled;
    private boolean autoCollapse;
    private boolean disallowSameItemInsert = false;

    @Nullable
    private ItemFilterContainer itemFilterContainer;
    private IItemHandlerModifiable filteredExportHandler;

    public MetaTileEntityItemBus(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
        this.workingEnabled = true;
        if (this.isExportHatch) {
            this.itemFilterContainer = new ItemFilterContainer(this::markDirty);
        }
        initializeInventory();
    }

    private static Widget<?> empty(int height) {
        return new Widget<>().height(height);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityItemBus(metaTileEntityId, getTier(), isExportHatch);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        if (this.hasGhostCircuitInventory()) {
            this.circuitInventory = new GhostCircuitItemStackHandler(this);
            this.actualImportItems = new ItemHandlerList(Arrays.asList(super.getImportItems(), this.circuitInventory));
        } else {
            this.actualImportItems = null;
        }
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return this.actualImportItems == null ? super.getImportItems() : this.actualImportItems;
    }

    @Override
    public IItemHandlerModifiable getExportItems() {
        if (isExportHatch && itemFilterContainer != null) {
            if (filteredExportHandler == null) {
                filteredExportHandler = new FilteredExportItemHandler(super.getExportItems(), itemFilterContainer);
            }
            return filteredExportHandler;
        }
        return super.getExportItems();
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0) {
            if (workingEnabled) {
                if (isExportHatch) {
                    pushItemsIntoNearbyHandlers(getFrontFacing());
                } else {
                    pullItemsFromNearbyHandlers(getFrontFacing());
                }
            }
            // Only attempt to auto collapse the inventory contents once the bus has been notified
            if (isAutoCollapse()) {
                // Exclude the ghost circuit inventory from the auto collapse, so it does not extract any ghost circuits
                // from the slot
                IItemHandlerModifiable inventory = (isExportHatch ? this.getExportItems() : super.getImportItems());
                if (!isAttachedToMultiBlock() || (isExportHatch ? this.getNotifiedItemOutputList().contains(inventory) :
                        this.getNotifiedItemInputList().contains(inventory))) {
                    collapseInventorySlotContents(inventory);
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
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
            Textures.PIPE_ITEM_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = isExportHatch ? Textures.ITEM_HATCH_OUTPUT_OVERLAY :
                    Textures.ITEM_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    public int getInventorySize() {
        int sizeRoot = 1 + Math.min(GTValues.UHV, getTier());
        return sizeRoot * sizeRoot;
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return isExportHatch ? new NotifiableItemStackHandler(this, getInventorySize(), getController(), true) :
                new GTItemStackHandler(this, 0);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return isExportHatch ? new GTItemStackHandler(this, 0) :
                new NotifiableItemStackHandler(this, getInventorySize(), getController(), false);
    }

    @Override
    public MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return isExportHatch ? MultiblockAbility.EXPORT_ITEMS : MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
        buf.writeBoolean(autoCollapse);
        buf.writeBoolean(disallowSameItemInsert);
        boolean hasFilter = itemFilterContainer != null;
        buf.writeBoolean(hasFilter);
        if (hasFilter) {
            itemFilterContainer.writeInitialSyncData(buf);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
        this.autoCollapse = buf.readBoolean();
        this.disallowSameItemInsert = buf.readBoolean();
        if (buf.readerIndex() < buf.writerIndex()) {
            if (buf.readBoolean() && itemFilterContainer != null) {
                itemFilterContainer.readInitialSyncData(buf);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("workingEnabled", workingEnabled);
        data.setBoolean("autoCollapse", autoCollapse);
        data.setBoolean("DisallowSameItemInsert", disallowSameItemInsert);
        if (this.circuitInventory != null && !this.isExportHatch) {
            this.circuitInventory.write(data);
        }
        if (itemFilterContainer != null) {
            data.setTag("OutputFilter", itemFilterContainer.serializeNBT());
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("workingEnabled")) {
            this.workingEnabled = data.getBoolean("workingEnabled");
        }
        if (data.hasKey("autoCollapse")) {
            this.autoCollapse = data.getBoolean("autoCollapse");
        }
        this.disallowSameItemInsert = data.getBoolean("DisallowSameItemInsert");
        // 同步更新底层 handler
        IItemHandlerModifiable updateHandler = isExportHatch ? exportItems : getImportItems();
        if (updateHandler instanceof ItemHandlerList list) {
            for (var h : list.getBackingHandlers()) {
                if (h instanceof GTItemStackHandler gtHandler) {
                    gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
                }
            }
        } else if (updateHandler instanceof GTItemStackHandler gtHandler) {
            gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
        }
        if (this.circuitInventory != null && !this.isExportHatch) {
            this.circuitInventory.read(data);
        }
        if (data.hasKey("OutputFilter") && itemFilterContainer != null) {
            itemFilterContainer.deserializeNBT(data.getCompoundTag("OutputFilter"));
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS) {
            this.autoCollapse = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            this.workingEnabled = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.UPDATE_DISALLOW_SAME_ITEM) {
            this.disallowSameItemInsert = buf.readBoolean();
        }
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (this.hasGhostCircuitInventory() && this.actualImportItems != null) {
            abilityInstances.add(isExportHatch ? getExportItems() : this.actualImportItems);
        } else {
            abilityInstances.add(isExportHatch ? getExportItems() : this.importItems);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    // region Sidebar Widget Builders

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        int rowSize = (int) Math.sqrt(getInventorySize());
        panelSyncManager.registerSlotGroup("item_inv", rowSize);

        boolean hasGhostCircuit = hasGhostCircuitInventory() && this.circuitInventory != null;

        int backgroundWidth = Math.max(
                9 * 18 + 18 + 14 + 5,   // Player Inv width
                rowSize * 18 + 14);      // Bus Inv width
        int gridTop = 18 + (isExportHatch ? 22 : 0);

        int backgroundHeight = gridTop + rowSize * 18 + 94;
        int sidebarTop = backgroundHeight - 18 * 5 - 7 - 4;

        BooleanSyncValue workingValue = new BooleanSyncValue(() -> workingEnabled, val -> workingEnabled = val);
        BooleanSyncValue collapseValue = new BooleanSyncValue(() -> autoCollapse, val -> autoCollapse = val);

        String workKey = isExportHatch ?
                "gregtech.gui.item_auto_output.tooltip" :
                "gregtech.gui.item_auto_input.tooltip";

        IItemHandlerModifiable handler = isExportHatch ? exportItems : importItems;

        Flow sidebar = Flow.column()
                .pos(backgroundWidth - 7 - 18, sidebarTop)
                .width(18).height(18 * 5 + 4)
                .child(GTGuiTextures.getLogo(getUITheme()).asWidget().size(17).top(18 * 4 + 4))
                .childIf(isExportHatch, () -> empty(18))
                .childIf(hasGhostCircuit, this::ghostCircuitSlot)
                .childIf(!hasGhostCircuit, this::unavailableSlot)
                .child(autoCollapseButton(collapseValue))
                .childIf(!isExportHatch, this::disallowSameItemButton)
                .child(autoWorkButton(workingValue, workKey));

        ModularPanel panel = GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));

        if (isExportHatch && itemFilterContainer != null) {
            Widget<?> filterRow = (Widget<?>) itemFilterContainer.initUI(guiData, panelSyncManager);
            filterRow.left(7).top(18).right(7).height(18);
            panel.child(filterRow);
        }

        panel.child(new Grid()
                        .top(gridTop).height(rowSize * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .alignX(0.5f)
                        .mapTo(rowSize, rowSize * rowSize, index -> new ItemSlot()
                                .slot(SyncHandlers.itemSlot(handler, index)
                                        .slotGroup("item_inv")
                                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                            if (onlyAmountChanged &&
                                                    handler instanceof GTItemStackHandler gtHandler) {
                                                gtHandler.onContentsChanged(index);
                                            }
                                        })
                                        .accessibility(!isExportHatch, true))))
                .child(sidebar);

        return panel;
    }

    private Widget<?> ghostCircuitSlot() {
        return new GhostCircuitSlotWidget()
                .slot(circuitInventory, 0)
                .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY);
    }

    private Widget<?> unavailableSlot() {
        return new Widget<>()
                .background(GTGuiTextures.SLOT, GTGuiTextures.BUTTON_X)
                .tooltip(t -> t.addLine(IKey.lang("gregtech.gui.configurator_slot.unavailable.tooltip")));
    }

    private ToggleButton autoCollapseButton(BooleanSyncValue collapseValue) {
        return new ToggleButton()
                .value(collapseValue)
                .overlay(GTGuiTextures.BUTTON_AUTO_COLLAPSE)
                .tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> tooltip.addLine(collapseValue.getBoolValue() ?
                        IKey.lang("gregtech.gui.item_auto_collapse.tooltip.enabled") :
                        IKey.lang("gregtech.gui.item_auto_collapse.tooltip.disabled")));
    }

    private ToggleButton disallowSameItemButton() {
        BooleanSyncValue disallowValue = new BooleanSyncValue(
                this::isDisallowSameItemInsert, this::setDisallowSameItemInsert);
        return new ToggleButton()
                .value(disallowValue)
                .overlay(GTGuiTextures.BUTTON_LOCK)
                .addTooltip(true, IKey.lang("gregtech.machine.disallow_same_item.enabled"))
                .addTooltip(false, IKey.lang("gregtech.machine.disallow_same_item.disabled"));
    }

    private ToggleButton autoWorkButton(BooleanSyncValue workingValue, String langKey) {
        return new ToggleButton()
                .value(workingValue)
                .overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                .tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> tooltip.addLine(workingValue.getBoolValue() ?
                        IKey.lang(langKey + ".enabled") :
                        IKey.lang(langKey + ".disabled")));
    }

    // endregion

    @Override
    public boolean hasGhostCircuitInventory() {
        return !this.isExportHatch;
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
                if (isExportHatch) {
                    addNotifiedOutput(this.getExportItems());
                } else {
                    addNotifiedInput(super.getImportItems());
                }
            }
            writeCustomData(GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS,
                    packetBuffer -> packetBuffer.writeBoolean(autoCollapse));
            notifyBlockUpdate();
            markDirty();
        }
    }

    public boolean isDisallowSameItemInsert() {
        return disallowSameItemInsert;
    }

    public void setDisallowSameItemInsert(boolean disallowSameItemInsert) {
        this.disallowSameItemInsert = disallowSameItemInsert;
        if (!getWorld().isRemote) {
            // 同步更新底层 handler
            IItemHandlerModifiable handler = isExportHatch ? exportItems : getImportItems();
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
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        if (this.isExportHatch)
            tooltip.add(I18n.format("gregtech.machine.item_bus.export.tooltip"));
        else
            tooltip.add(I18n.format("gregtech.machine.item_bus.import.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getInventorySize()));
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
