package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.capability.impl.LargeSlotItemStackHandler;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
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
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.util.text.TextFormatting.GREEN;

public class MetaTileEntityHugeComplexDualHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<DualHandler>, IControllable, IGhostSlotConfigurable {
    //item
    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    @Nullable
    private IItemHandlerModifiable actualImportItems;
    @Nullable
    protected LargeSlotItemStackHandler commonItems;
    @Nullable
    protected FluidTankList commonFluids;

    private boolean workingEnabled;
    private boolean autoCollapse;

    public MetaTileEntityHugeComplexDualHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.workingEnabled = true;
        initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHugeComplexDualHatch(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        this.importItems = createImportItemHandler();
        this.exportItems = createExportItemHandler();

        this.circuitInventory = new GhostCircuitItemStackHandler(this);

        this.commonItems = createCommonItemHandler();
        this.importItems = commonItems;
        this.exportItems = commonItems;
        this.itemInventory = new ItemHandlerProxy(importItems, exportItems);

        this.commonFluids = new FluidTankList(false, createTanks());
        this.importFluids = commonFluids;
        this.exportFluids = commonFluids;
        this.fluidInventory = new FluidHandlerProxy(importFluids, exportFluids);

        this.actualImportItems = new ItemHandlerList(Arrays.asList(this.commonItems, this.circuitInventory));
    }

    protected IFluidTank[] createTanks() {
        int size = getTankSize();
        IFluidTank[] tanks = new IFluidTank[size];
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = new NotifiableFluidTank(getTankCapacity(), null, false);
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

    protected LargeSlotItemStackHandler createCommonItemHandler() {
        return new LargeSlotItemStackHandler(this, getItemSize(), null, false);
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return this.actualImportItems == null ? super.getImportItems() : this.actualImportItems;
    }
    @Override
    public IItemHandlerModifiable getExportItems() {
        return commonItems;
    }
    @Override
    public FluidTankList getImportFluids() {
        return commonFluids;
    }
    @Override
    public FluidTankList getExportFluids() {
        return commonFluids;
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
                IItemHandlerModifiable inventory = commonItems;
                if (!isAttachedToMultiBlock() || (this.getNotifiedItemInputList().contains(inventory))) {
                    GTUtility.collapseInventorySlotContents(inventory);
                }
            }
        }
    }


    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
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
    public int getGhostCircuitConfig() {
        if (this.circuitInventory == null) {
            return 0;
        }
        return this.circuitInventory.getCircuitValue();
    }


    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return Arrays.asList(
                MultiblockAbility.IMPORT_FLUIDS,
                MultiblockAbility.IMPORT_ITEMS,
                MultiblockAbility.EXPORT_FLUIDS,
                MultiblockAbility.EXPORT_ITEMS
        );
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_ITEMS))
        {
            abilityInstances.add(this.commonItems);
        }
        if (abilityInstances.isKey(MultiblockAbility.IMPORT_ITEMS)) {
            abilityInstances.add(this.actualImportItems);
        }
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_FLUIDS) || abilityInstances.isKey(MultiblockAbility.IMPORT_FLUIDS)) {
            abilityInstances.add(this.commonFluids);
        }
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
                9 * 18 + 18 + 14 + 5,   // Player Inv width
                (rowSize + 1) * 18 + 14); // Bus Inv width
        int backgroundHeight = 18 + 18 * rowSize + 94;

        List<List<IWidget>> widgets = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgets.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;
                IItemHandlerModifiable handler = commonItems;
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
                                                    handler instanceof LargeSlotItemStackHandler gtHandler) {
                                                gtHandler.onContentsChanged(index);
                                            }
                                        })
                                        .accessibility(true, true)));
            }
            widgets.get(i).add(new GTFluidSlot()
                    .syncHandler(GTFluidSlot.sync(commonFluids.getTankAt(i))
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
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
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
                                .slot(circuitInventory, 0)
                                .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY))
                        .childIf(!hasGhostCircuit, new Widget<>()
                                .background(GTGuiTextures.SLOT, GTGuiTextures.BUTTON_X)
                                .tooltip(t -> t.addLine(
                                        IKey.lang("gregtech.gui.configurator_slot.unavailable.tooltip"))))
                );
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.COMPLEX_DUAL_HATCH;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }


    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
        buf.writeBoolean(autoCollapse);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
        this.autoCollapse = buf.readBoolean();
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
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.commonItems);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("workingEnabled", workingEnabled);
        data.setBoolean("autoCollapse", autoCollapse);
        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }

        this.commonItems.deserializeNBT(data.getCompoundTag("commonItems"));
        data.setTag("commonFluids", commonFluids.serializeNBT());

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
        if (this.circuitInventory != null) {
            this.circuitInventory.read(data);
        }

        this.commonItems.deserializeNBT(data.getCompoundTag("commonItems"));
        commonFluids.deserializeNBT(data.getCompoundTag("commonFluids"));
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
                addNotifiedInput(commonItems);
                addNotifiedOutput(commonItems);
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

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.complex_dual_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.complex_dual_hatch.tooltip.2"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getItemSize()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", getTankSize(), getTankCapacity()));
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
        if (this == MetaTileEntities.HUGE_COMPLEX_DUAL_HATCH[0]) {
            for (var hatch : MetaTileEntities.HUGE_COMPLEX_DUAL_HATCH) {
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
        GTTransferUtils.dropInventoryItems(getWorld(),getPos(), commonItems);
    }
}
