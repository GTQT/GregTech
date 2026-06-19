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
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.util.GTLog;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pattern mapping slave with its own pattern slots and AE connection.
 * <p>
 * Linked via data stick to a master provider and forwards injected items to master.
 * Main behavior:
 * - Owns local pattern slots and registers patterns to AE.
 * - Links to MetaTileEntityMEPatternProvider (or a proxy target).
 * - Forwards push operations to master buffer pool.
 * - Delegates isBusy() to master.
 *
 */
public class MetaTileEntityPatternProviderMappingSlave extends MetaTileEntityAECraftingPart
        implements IMEPatternProviderPart {

    // ==================== Pattern slots ====================
    protected final int patternSlotCount;

    // ==================== Link to master ====================
    private MetaTileEntityMEPatternProvider master;
    private BlockPos masterPos;
    private boolean masterSet = false;
    private boolean checkForMaster = true;

    public MetaTileEntityPatternProviderMappingSlave(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, false);
        this.patternSlotCount = tier * tier;
        patternDetails = new ArrayList<>(Collections.nCopies(getPatternSlotCount(), null));
        initializeInventory();
    }

    /**
     * Pattern slot count = tier × tier. Subclasses can override to increase capacity.
     */
    protected int getPatternSlotCount() {
        return patternSlotCount;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityPatternProviderMappingSlave(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.patternSlot = new ItemStackHandler(getPatternSlotCount()) {

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

        // Mapping slave does not keep local input buffers; materials are forwarded to master.
        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                java.util.Arrays.asList(this.importItems, this.circuitInventory));
        dualHandler = new DualHandler(this.actualImportItems, getImportFluids(), false);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, null, false);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false,
                new IFluidTank[] { new NotifiableFluidTank(8000, null, false) });
    }

    // ==================== Master link ====================

    @Nullable
    private MetaTileEntityMEPatternProvider getMaster() {
        return master;
    }

    @Nullable
    private MetaTileEntityMEPatternProvider getResolvedMasterForLink() {
        if ((master == null || !master.isValid()) && masterPos != null) {
            tryToSetMaster();
        }
        return master != null && master.isValid() ? master : null;
    }

    private boolean hasMaster() {
        return getResolvedMasterForLink() != null;
    }

    private void tryToSetMaster() {
        MetaTileEntityMEPatternProvider resolved = MasterNodeResolver.resolve(getWorld(), masterPos);
        if (resolved != null) {
            setMasterAndRegister(resolved);
        } else {
            this.master = null;
            this.checkForMaster = true;
        }
    }

    private void setMasterAndRegister(MetaTileEntityMEPatternProvider newMaster) {
        if (this.master != null && this.master != newMaster) {
            this.master.removeMappingSlave(this);
        }
        this.master = newMaster;
        this.master.addMappingSlave(this);
        this.checkForMaster = false;
    }

    /**
     * Called by master when it is being removed from the world.
     */
    public void onMasterRemoved() {
        this.master = null;
        this.checkForMaster = true;
    }

    // ==================== Data stick interaction ====================

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        // Mapping slave does not write data stick content.
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null || !tag.hasKey("BudgetCRIB")) return false;

        NBTTagCompound cribTag = tag.getCompoundTag("BudgetCRIB");
        // Unregister from old master before switching
        if (this.master != null) {
            this.master.removeMappingSlave(this);
        }
        this.masterPos = new BlockPos(
                cribTag.getInteger("MainX"),
                cribTag.getInteger("MainY"),
                cribTag.getInteger("MainZ"));
        this.masterSet = true;
        this.master = null;
        this.checkForMaster = true;

        player.sendStatusMessage(new TextComponentTranslation(
                "gregtech.machine.pattern_mapping_slave.data_stick_use",
                TextFormattingUtil.formatNumbers(masterPos.getX()),
                TextFormattingUtil.formatNumbers(masterPos.getY()),
                TextFormattingUtil.formatNumbers(masterPos.getZ())), true);

        tryToSetMaster();
        return true;
    }

    // ==================== AE2 push forwarding to master ====================

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting inventoryCrafting) {
        if (!isActive()) {
            return false;
        }
        MetaTileEntityMEPatternProvider resolvedMaster = getResolvedMasterForLink();
        if (resolvedMaster == null) {
            GTLog.logger.debug("Mapping slave has no master, rejecting pattern");
            return false;
        }
        // Forward materials to the master buffer pool.
        return resolvedMaster.pushToBuffer(inventoryCrafting);
    }

    @Override
    public boolean isBusy() {
        MetaTileEntityMEPatternProvider resolvedMaster = getResolvedMasterForLink();
        if (resolvedMaster == null) return true;
        return resolvedMaster.isBusy();
    }

    // ==================== No multiblock ability registration ====================

    @Override
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return null;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        // Mapping slave is not exposed as a multiblock input ability.
    }

    // ==================== update ====================

    @Override
    public void update() {
        super.update();
        if (getWorld() != null && !getWorld().isRemote) {
            if (isWorkingEnabled() && isOnline && shouldSyncME()) {
                if (isNeedPatternSync()) setNeedPatternSync(MEPatternChange());
            }
        }

        if (getWorld() != null && getOffsetTimer() % 20 == 0) {
            if (checkForMaster && !hasMaster()) {
                tryToSetMaster();
            }
        }
    }

    // ==================== Rendering ====================

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.ME_BUFFER_HATCH_PROXY_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    // ==================== NBT ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("Pattern", this.patternSlot.serializeNBT());

        if (masterSet && masterPos != null) {
            data.setBoolean("HasMaster", true);
            data.setInteger("MasterX", masterPos.getX());
            data.setInteger("MasterY", masterPos.getY());
            data.setInteger("MasterZ", masterPos.getZ());
        } else {
            data.setBoolean("HasMaster", false);
        }

        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.patternSlot.deserializeNBT(data.getCompoundTag("Pattern"));
        setPatternDetails();

        if (data.getBoolean("HasMaster")) {
            this.masterPos = new BlockPos(
                    data.getInteger("MasterX"),
                    data.getInteger("MasterY"),
                    data.getInteger("MasterZ"));
            this.masterSet = true;
            tryToSetMaster();
        }

        if (this.circuitInventory != null) {
            this.circuitInventory.read(data);
        }
    }

    @Override
    public void onRemoval() {
        if (this.master != null) {
            this.master.removeMappingSlave(this);
        }
        removeFromGridCache();
        super.onRemoval();
        if (patternSlot != null) {
            gregtech.api.util.GTTransferUtils.dropInventoryItems(getWorld(), getPos(), patternSlot);
        }
    }

    // ==================== Sync ====================

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);

        if (masterSet && masterPos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(masterPos);
        } else {
            buf.writeBoolean(false);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);

        if (buf.readBoolean()) {
            masterPos = buf.readBlockPos();
            masterSet = true;
            tryToSetMaster();
        }
    }

    // ==================== GUI ====================

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = (int) Math.ceil(Math.sqrt(getPatternSlotCount()));
        guiSyncManager.registerSlotGroup("pattern_inv", rowSize);

        int backgroundWidth = Math.max(9 * 18 + 18 + 14 + 5 + 18, (rowSize + 1) * 18 + 14 + 18);
        int backgroundHeight = 18 + 18 * Math.max(4, rowSize) + 94;

        // Pattern page
        List<List<IWidget>> widgetsPattern = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgetsPattern.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;
                widgetsPattern.get(i)
                        .add(new ItemSlot()
                                .slot(SyncHandlers.itemSlot(patternSlot, index)
                                        .slotGroup("pattern_inv")
                                        .accessibility(true, true))
                                .background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY));
            }
        }

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
                                .addTooltipLine(IKey.lang("gregtech.machine.pattern_mapping_slave.ui.tab.patterns"))
                                .overlay(HATCH))
                        .child(new PageButton(1, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("gregtech.machine.pattern_mapping_slave.ui.tab.status"))
                                .overlay(TERMINAL))
                )
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new PagedWidget<>()
                        .top(18)
                        .margin(0)
                        .widthRel(1f)
                        .controller(controller)
                        // Pattern page
                        .addPage(
                                new Grid()
                                        .top(0)
                                        .height(rowSize * 18)
                                        .minElementMargin(0, 0)
                                        .minColWidth(18)
                                        .minRowHeight(18)
                                        .leftRel(0.5f)
                                        .matrix(widgetsPattern))
                        // Status page
                        .addPage(
                                Flow.column()
                                        .top(0)
                                        .widthRel(1f)
                                        .leftRel(0.5f)
                                        .margin(5, 0)
                                        .child(new TextWidget<>(IKey.lang("gregtech.machine.pattern_mapping_slave.ui.title")))
                                        .child(new TextWidget<>(IKey.dynamic(() -> {
                                            if (hasMaster() && masterPos != null) {
                                                return I18n.format("gregtech.machine.pattern_mapping_slave.ui.status.linked",
                                                        masterPos.getX(), masterPos.getY(), masterPos.getZ());
                                            }
                                            if (masterSet && masterPos != null) {
                                                return I18n.format("gregtech.machine.pattern_mapping_slave.ui.status.waiting",
                                                        masterPos.getX(), masterPos.getY(), masterPos.getZ());
                                            }
                                            return I18n.format("gregtech.machine.pattern_mapping_slave.ui.status.none");
                                        })))
                                        .child(new TextWidget<>(IKey.dynamic(() -> {
                                            MetaTileEntityMEPatternProvider resolvedMaster = getResolvedMasterForLink();
                                            if (resolvedMaster != null) {
                                                int usedBuffers = 0;
                                                for (MetaTileEntityMEPatternProvider.PatternBuffer buffer :
                                                        resolvedMaster.getBufferPool()) {
                                                    if (!buffer.isEmpty()) usedBuffers++;
                                                }
                                                return I18n.format("gregtech.machine.pattern_mapping_slave.ui.status.buffers",
                                                        usedBuffers, resolvedMaster.getBufferCount());
                                            }
                                            return "";
                                        })))
                                        .child(new TextWidget<>(
                                                IKey.lang("gregtech.machine.pattern_mapping_slave.ui.hint")))
                        )
                )
                .child(Flow.column()
                        .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 * 4 - 7 - 5)
                        .width(18).height(18 * 4 + 5)
                        .child(GTGuiTextures.getLogo(getUITheme()).asWidget()
                                .top(18 * 3 + 5)
                                .size(17))
                );
    }

    // ==================== Tooltip ====================

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.1",patternSlotCount));
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.3"));
    }
}

