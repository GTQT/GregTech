package gregtech.common.metatileentities.multi.electric.generator.nuclearReactor;

import gregtech.api.capability.INuclearExtend;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MetaTileEntityBaseWithControl;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.SCMultiblockAbility;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.nuclear.ic.NuclearReactorSimulator;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.tooltips.InformationHandler;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockNuclearReactorCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.behaviors.AbstractMaterialPartBehavior;
import gregtech.common.items.behaviors.nuclear.NuclearComponentBehavior;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;

import static gregtech.api.capability.GregtechDataCodes.SYNC_REACTOR_STATE;
import static net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND;

public class MetaTileEntityNuclearReactor extends MetaTileEntityBaseWithControl implements ProgressBarMultiblock {

    private static final int UPDATE_TICK_RATE = NuclearReactorSimulator.TICKS_PER_STEP;
    private static final int BASE_HEAT_CAPACITY = NuclearReactorSimulator.BASE_HEAT_CAPACITY;
    /** Width of the internal component grid without any extension hatch installed. */
    private static final int BASE_REACTOR_WIDTH = 3;
    /** Width of the internal component grid with every extension hatch installed (9x6 = 54 slots). */
    private static final int MAX_REACTOR_WIDTH = 9;
    /** Height of the internal component grid. Fixed; only the width is expanded. */
    private static final int REACTOR_HEIGHT = 6;
    /** Every extension hatch widens the internal grid by one column, so this is also the hatch limit. */
    public static final int MAX_EXTEND_HATCHES = MAX_REACTOR_WIDTH - BASE_REACTOR_WIDTH;
    @NotNull
    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:nuclear_reactor", () -> DeclarativePatternBuilder.start()
                    .aisle("YYY", "YYY", "YYY")
                    .aisle("YYY", "YYY", "YYY")
                    .aisle("YYY", "YSY", "YYY")
                    .self('S', MetaTileEntityNuclearReactor.class)
                    .casing('Y', getCasingState())
                    .energyOutput(1, 3)
                    .optionalItemOutput(2)
                    .optionalFluidOutput(2)
                    .hatch(SCMultiblockAbility.REACTOR_EXTEND_HATCH, 0, MAX_EXTEND_HATCHES)
                    .done()
                    .any(' ')
                    .buildStructureDefinition()
    );
    /**
     * Width of the internal component grid, in cells. Installed {@link MetaTileEntityNuclearExtend} hatches expand
     * it from {@value #BASE_REACTOR_WIDTH} up to {@value #MAX_REACTOR_WIDTH} columns.
     */
    @Getter
    private int reactorWidth = BASE_REACTOR_WIDTH;
    @Getter
    private int reactorHeight = REACTOR_HEIGHT;
    @Getter
    private int extendCount = 0;
    @Getter
    private NuclearReactorSimulator reactorSimulator;
    @Getter
    private GTItemStackHandler componentHandler;
    private int tickCounter = 0;
    private int updateTimer = 0;
    private boolean isReactorActive = false;
    private boolean hasMeltdown = false;
    @Getter
    private int currentHeat = 0;
    @Getter
    private int maxHeatCapacity = BASE_HEAT_CAPACITY;
    @Getter
    private long currentOutput = 0;
    private float efficiency = 1.0f;

    public MetaTileEntityNuclearReactor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
        initializeReactor(reactorWidth, reactorHeight);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.NUCLEAR_REACTOR_CASING.getState(BlockNuclearReactorCasing.NuclearReactorType.NUCLEAR_REACTOR_CASING);
    }

    private void initializeReactor(int width, int height) {
        this.reactorSimulator = new NuclearReactorSimulator(width, height);
        this.componentHandler = createComponentHandler(width * height);
    }

    private GTItemStackHandler createComponentHandler(int slotCount) {
        return new GTItemStackHandler(this, slotCount) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return NuclearComponentBehavior.getInstanceFor(stack) != null;
            }

            @Override
            public void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                if (getWorld() != null && !getWorld().isRemote && isStructureFormed()) {
                    markDirty();
                }
            }
        };
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityNuclearReactor(metaTileEntityId);
    }

    /**
     * @return the installed extension hatches, or {@code null} when there is none.
     */
    public List<INuclearExtend> getExtendHatch() {
        List<INuclearExtend> abilities = getAbilities(SCMultiblockAbility.REACTOR_EXTEND_HATCH);
        return abilities.isEmpty() ? null : abilities;
    }

    @Override
    protected void formStructure(FormedStructureView formed) {
        super.formStructure(formed);
        refreshReactorSize();
    }

    /**
     * Derives the size of the internal component grid from the installed extension hatches. Every hatch adds one
     * column to the reactor interior: the grid starts at {@value #BASE_REACTOR_WIDTH}x{@value #REACTOR_HEIGHT}
     * (18 slots) and tops out at {@value #MAX_REACTOR_WIDTH}x{@value #REACTOR_HEIGHT} (54 slots).
     */
    private void refreshReactorSize() {
        List<INuclearExtend> extendHatches = getExtendHatch();
        int hatchCount = extendHatches == null ? 0 : Math.min(extendHatches.size(), MAX_EXTEND_HATCHES);
        this.extendCount = hatchCount;
        applyReactorSize(BASE_REACTOR_WIDTH + hatchCount);
    }

    private void applyReactorSize(int width) {
        int clampedWidth = clampWidth(width);
        if (clampedWidth == reactorWidth && componentHandler.getSlots() == clampedWidth * reactorHeight) {
            return;
        }

        reactorWidth = clampedWidth;
        reactorHeight = REACTOR_HEIGHT;

        if (getWorld() != null && !getWorld().isRemote) {
            rebuildGrid();
            // The slot layout changed, so push the new grid (and its contents) to the clients right away.
            syncReactorState(true);
        } else {
            ensureGridCapacity();
        }
    }

    /**
     * Server side resize. The simulator keeps its state and every component that still fits the new grid; components
     * that fall outside of it are queued for the output bus instead of being voided.
     */
    private void rebuildGrid() {
        int previousSlots = componentHandler.getSlots();
        int previousWidth = Math.max(1, previousSlots / reactorHeight);

        ItemStack[][] previous = new ItemStack[previousWidth][reactorHeight];
        for (int y = 0; y < reactorHeight; y++) {
            for (int x = 0; x < previousWidth; x++) {
                previous[x][y] = componentHandler.getStackInSlot(y * previousWidth + x);
            }
        }

        reactorSimulator.resize(reactorWidth, reactorHeight);
        componentHandler = createComponentHandler(reactorWidth * reactorHeight);

        for (int y = 0; y < reactorHeight; y++) {
            for (int x = 0; x < previousWidth; x++) {
                ItemStack stack = previous[x][y];
                if (stack.isEmpty()) continue;

                if (x < reactorWidth) {
                    componentHandler.setStackInSlot(y * reactorWidth + x, stack);
                } else {
                    queueForOutput(stack);
                }
            }
        }

        syncInventoryToSimulator();
    }

    /**
     * Client side counterpart of {@link #rebuildGrid}. The client only renders the grid, so its handler is grown when
     * needed and never shrunk - an already open component panel may still reference the larger layout.
     */
    private void ensureGridCapacity() {
        reactorSimulator.resize(reactorWidth, reactorHeight);

        int required = reactorWidth * reactorHeight;
        if (componentHandler.getSlots() >= required) {
            return;
        }

        GTItemStackHandler previous = componentHandler;
        componentHandler = createComponentHandler(required);
        for (int slot = 0; slot < previous.getSlots(); slot++) {
            componentHandler.setStackInSlot(slot, previous.getStackInSlot(slot));
        }
    }

    /** Queues a component for the output bus, keeping it until an output hatch has room for it. */
    private void queueForOutput(@NotNull ItemStack stack) {
        if (stack.isEmpty()) return;

        reactorSimulator.getListToTransfer().add(stack);
        reactorSimulator.setTransOut(true);
    }

    private static int clampWidth(int width) {
        return Math.max(BASE_REACTOR_WIDTH, Math.min(width, MAX_REACTOR_WIDTH));
    }

    /** @return the grid width implied by a component slot count, used to migrate saves from the fixed 9x6 grid. */
    private static int widthFromSlotCount(int slotCount) {
        return clampWidth(slotCount <= 0 ? BASE_REACTOR_WIDTH : slotCount / REACTOR_HEIGHT);
    }

    @Override
    protected void updateFormedValid() {
        if (getWorld().isRemote) return;

        if (reactorSimulator.isTransOut() && getOutputInventory().getSlots() > 0) {
            List<ItemStack> recoveryItems = reactorSimulator.getListToTransfer();
            for (Iterator<ItemStack> iterator = recoveryItems.iterator(); iterator.hasNext(); ) {
                ItemStack stack = iterator.next();
                ItemStack exist = GTTransferUtils.insertItem(getOutputInventory(), stack, true);
                if (exist.isEmpty()) {
                    GTTransferUtils.insertItem(getOutputInventory(), stack, false);
                    iterator.remove();
                }
            }
            if (reactorSimulator.getListToTransfer().isEmpty()) {
                reactorSimulator.setTransOut(false);
            }
        }
        if (!hasMeltdown && getInputInventory().getSlots() > 0) {
            for (int i = 0; i < getInputInventory().getSlots(); i++) {
                ItemStack stack = getInputInventory().getStackInSlot(i);
                if (!stack.isEmpty() && NuclearComponentBehavior.getInstanceFor(stack) != null) {
                    reactorSimulator.getListToAdd().add(stack);
                    getInputInventory().setStackInSlot(i, ItemStack.EMPTY);
                    reactorSimulator.setTransIn(true);
                }
            }
        }

        if (!isWorkingEnabled()) return;

        tickCounter++;
        updateTimer++;

        if (updateTimer >= UPDATE_TICK_RATE) {
            updateTimer = 0;

            // Follow the installed extension hatches even when the structure payload itself is not re-evaluated.
            refreshReactorSize();

            syncInventoryToSimulator();

            if (!reactorSimulator.simulateTick()) {
                performMeltdown();
                return;
            }

            updateLocalCache();

            syncSimulatorToInventory();

            markDirty();
            syncReactorState(false);
        }
        outputEnergy();
    }

    private void syncReactorState(boolean includeComponentGrid) {
        writeCustomData(SYNC_REACTOR_STATE, buf -> writeReactorState(buf, includeComponentGrid));
    }

    private void writeReactorState(@NotNull PacketBuffer buf, boolean includeComponentGrid) {
        buf.writeInt(currentHeat);
        buf.writeInt(maxHeatCapacity);
        buf.writeLong(currentOutput);
        buf.writeBoolean(isReactorActive);
        buf.writeBoolean(hasMeltdown);
        buf.writeInt(reactorWidth);
        buf.writeBoolean(includeComponentGrid);

        if (includeComponentGrid) {
            buf.writeVarInt(componentHandler.getSlots());
            for (int slot = 0; slot < componentHandler.getSlots(); slot++) {
                buf.writeItemStack(componentHandler.getStackInSlot(slot));
            }
        }
    }

    private void syncInventoryToSimulator() {
        for (int slot = 0; slot < componentHandler.getSlots(); slot++) {
            int x = slot % reactorWidth;
            int y = slot / reactorWidth;

            ItemStack stack = componentHandler.getStackInSlot(slot);
            ItemStack simulatorStack = reactorSimulator.getComponent(x, y);
            if (!ItemStack.areItemStacksEqual(stack, simulatorStack)) {
                reactorSimulator.removeComponent(x, y);
                if (!stack.isEmpty()) {
                    reactorSimulator.placeComponent(x, y, stack);
                }
            }
        }
    }

    private void syncSimulatorToInventory() {
        for (int x = 0; x < reactorWidth; x++) {
            for (int y = 0; y < reactorHeight; y++) {
                int slot = y * reactorWidth + x;
                ItemStack simulatorStack = reactorSimulator.getComponent(x, y);
                ItemStack currentStack = componentHandler.getStackInSlot(slot);
                if (!ItemStack.areItemStacksEqual(currentStack, simulatorStack)) {
                    componentHandler.setStackInSlot(slot, simulatorStack.copy());
                }
            }
        }
    }

    private void updateLocalCache() {
        currentHeat = reactorSimulator.getCurrentHeat();
        maxHeatCapacity = reactorSimulator.getMaxHeatCapacity();
        currentOutput = reactorSimulator.getCurrentOutput();
        isReactorActive = reactorSimulator.isActive() && !hasMeltdown;
        efficiency = reactorSimulator.getEfficiency();
    }

    /**
     * Credits the power of the last simulation step to the output hatches, once per tick.
     *
     * <p>Fuel rods are defined in EU/t, so {@code currentOutput} is credited every tick as is. A simulation step
     * covers {@link #UPDATE_TICK_RATE} ticks, which means one step credits {@code currentOutput * UPDATE_TICK_RATE}
     * EU in total (see {@link NuclearReactorSimulator#getEnergyPerStep()}); handing it over tick by tick also keeps a
     * small output hatch buffer from voiding part of the production.</p>
     */
    private void outputEnergy() {
        if (hasMeltdown || outEnergyContainer == null) return;
        if (currentOutput <= 0) return;

        outEnergyContainer.addEnergy(currentOutput);
    }

    /** @return the energy one full simulation step (one second) produces, in EU. */
    private long getOutputPerSecond() {
        return currentOutput * UPDATE_TICK_RATE;
    }

    /**
     * Handles the (irreversible) meltdown exactly once: the simulator keeps returning a failed tick afterwards, so
     * this must not restart the countdown or re-destroy the component grid on every following simulation tick.
     */
    private void performMeltdown() {
        if (hasMeltdown) return;
        hasMeltdown = true;

        isReactorActive = false;
        currentOutput = 0;

        for (int slot = 0; slot < componentHandler.getSlots(); slot++) {
            componentHandler.setStackInSlot(slot, ItemStack.EMPTY);
        }

        doExplosion(calculateExplosionPower());

        syncInventoryToSimulator();
        syncReactorState(true);
    }

    private float calculateExplosionPower() {
        float basePower = 20.0f;

        int heat = reactorSimulator.getCurrentHeat();
        int fuelRods = reactorSimulator.getTotalFuelRods();

        float heatFactor = Math.min(heat / 10000.0f, 2.0f);
        float fuelFactor = Math.min(fuelRods / 5.0f, 2.0f);
        // Reactor plating absorbs part of the blast.
        float containment = 1.0f - reactorSimulator.getExplosionResistance();

        return basePower * (1.0f + heatFactor + fuelFactor) * containment;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("ReactorWidth", reactorWidth);
        data.setInteger("ReactorHeight", reactorHeight);
        data.setInteger("ExtendCount", extendCount);
        data.setTag("ComponentInventory", componentHandler.serializeNBT());

        NBTTagCompound simulatorNBT = new NBTTagCompound();
        if (reactorSimulator != null) {
            reactorSimulator.writeToNBT(simulatorNBT);
            data.setTag("ReactorSimulator", simulatorNBT);
        }

        data.setBoolean("HasMeltdown", hasMeltdown);
        data.setInteger("TickCounter", tickCounter);
        data.setInteger("UpdateTimer", updateTimer);
        data.setInteger("CurrentHeat", currentHeat);
        data.setInteger("MaxHeatCapacity", maxHeatCapacity);
        data.setLong("CurrentOutput", currentOutput);
        data.setFloat("Efficiency", efficiency);
        data.setBoolean("IsReactorActive", isReactorActive);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        // The grid size has to be known before the handler and the simulator are created, since both are sized
        // from it and the stored component grid is laid out for it.
        NBTTagCompound componentData = data.getCompoundTag("ComponentInventory");
        if (data.hasKey("ReactorWidth")) {
            reactorWidth = clampWidth(data.getInteger("ReactorWidth"));
        } else {
            // Saves from the era of the fixed 9x6 grid only carry the slot count.
            reactorWidth = widthFromSlotCount(componentData.getInteger("Size"));
        }
        reactorHeight = REACTOR_HEIGHT;
        extendCount = Math.max(0, Math.min(data.getInteger("ExtendCount"), MAX_EXTEND_HATCHES));
        initializeReactor(reactorWidth, reactorHeight);

        if (data.hasKey("ComponentInventory")) {
            try {
                componentHandler.deserializeNBT(componentData);
            } catch (Exception e) {
                GTLog.logger.error("Error loading component inventory", e);
            }
        }

        hasMeltdown = data.getBoolean("HasMeltdown");
        tickCounter = data.getInteger("TickCounter");
        updateTimer = data.getInteger("UpdateTimer");
        currentHeat = data.getInteger("CurrentHeat");
        maxHeatCapacity = data.getInteger("MaxHeatCapacity");
        currentOutput = data.getLong("CurrentOutput");
        efficiency = data.getFloat("Efficiency");
        isReactorActive = data.getBoolean("IsReactorActive");

        if (data.hasKey("ReactorSimulator", TAG_COMPOUND)) {
            try {
                NBTTagCompound simulatorNBT = data.getCompoundTag("ReactorSimulator");
                reactorSimulator.readFromNBT(simulatorNBT);
            } catch (Exception e) {
                GTLog.logger.error("Error loading reactor simulator data", e);
                reactorSimulator.setHeat(currentHeat);
            }
        } else {
            reactorSimulator.setHeat(currentHeat);
        }

        if (getWorld() != null && !getWorld().isRemote && isStructureFormed()) {
            syncInventoryToSimulator();
            updateLocalCache();
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);

        buf.writeInt(currentHeat);
        buf.writeInt(maxHeatCapacity);
        buf.writeLong(currentOutput);
        buf.writeBoolean(isReactorActive);
        buf.writeBoolean(hasMeltdown);
        buf.writeInt(reactorWidth);

        buf.writeVarInt(componentHandler.getSlots());
        for (int slot = 0; slot < componentHandler.getSlots(); slot++) {
            buf.writeItemStack(componentHandler.getStackInSlot(slot));
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);

        currentHeat = buf.readInt();
        maxHeatCapacity = buf.readInt();
        currentOutput = buf.readLong();
        isReactorActive = buf.readBoolean();
        hasMeltdown = buf.readBoolean();
        reactorWidth = clampWidth(buf.readInt());
        reactorHeight = REACTOR_HEIGHT;
        ensureGridCapacity();

        readComponentGrid(buf);
    }

    /**
     * Reads a component grid payload: the slot count followed by that many item stacks. Slots beyond the payload are
     * cleared, so a grid that shrank does not keep rendering stale components on the client.
     */
    private void readComponentGrid(@NotNull PacketBuffer buf) {
        int slotCount = buf.readVarInt();
        int capacity = componentHandler.getSlots();

        for (int slot = 0; slot < slotCount && slot < capacity; slot++) {
            try {
                componentHandler.setStackInSlot(slot, buf.readItemStack());
            } catch (IOException e) {
                GTLog.logger.error("Error reading nuclear reactor components from network", e);
                return;
            }
        }
        for (int slot = slotCount; slot < capacity; slot++) {
            componentHandler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);

        if (dataId == SYNC_REACTOR_STATE) {
            currentHeat = buf.readInt();
            maxHeatCapacity = buf.readInt();
            currentOutput = buf.readLong();
            isReactorActive = buf.readBoolean();
            hasMeltdown = buf.readBoolean();
            reactorWidth = clampWidth(buf.readInt());
            reactorHeight = REACTOR_HEIGHT;
            ensureGridCapacity();

            if (buf.readBoolean()) {
                readComponentGrid(buf);
            }
        }
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return super.createUIFactory()
                .createFlexButton((guiData, syncManager) -> {
                    var componentPanel = syncManager.syncedPanel("component_panel", true, this::makeComponentPanel);
                    return new ButtonWidget<>()
                            .size(18)
                            .overlay(GTGuiTextures.FILTER_SETTINGS_OVERLAY.asIcon().size(16))
                            .addTooltipLine(IKey.str("§7核电组件管理"))
                            .onMousePressed(i -> {
                                if (componentPanel.isPanelOpen()) {
                                    componentPanel.closePanel();
                                } else {
                                    componentPanel.openPanel();
                                }
                                return true;
                            });
                });
    }

    private ModularPanel makeComponentPanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        syncManager.registerSlotGroup("reactor_inventory", reactorWidth);

        int panelHeight = 4 + 20 + (reactorHeight * 18) + 4;
        int panelWidth = 4 + (reactorWidth * 18) + 4;

        return GTGuis.createPopupPanel("nuclear_components", panelWidth, panelHeight)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.str("核电组件 (" + reactorWidth + "×" + reactorHeight + ")")
                                .asWidget()
                                .heightRel(1.0f)))
                .child(Flow.column()
                        .top(24)
                        .left(4)
                        .width(reactorWidth * 18)
                        .height(reactorHeight * 18)
                        .child(createComponentGrid(reactorWidth, reactorHeight)));
    }

    private Grid createComponentGrid(int width, int height) {
        int slotCount = width * height;

        return new Grid()
                .minElementMargin(0, 0)
                .minColWidth(18).minRowHeight(18)
                .alignX(0.5f)
                .mapTo(width, slotCount, index -> new ItemSlot()
                        .slot(SyncHandlers.itemSlot(componentHandler, index)
                                .slotGroup("reactor_inventory")
                                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                    if (onlyAmountChanged &&
                                            componentHandler instanceof GTItemStackHandler) {
                                        componentHandler.onContentsChanged(index);
                                    }

                                    if (!client && isStructureFormed()) {
                                        syncInventoryToSimulator();
                                        markDirty();
                                    }
                                })
                                .accessibility(true, true)));
    }

    @Override
    public int getProgressBarCount() {
        return 2;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        IntSyncValue heatValue = new IntSyncValue(() -> currentHeat);
        IntSyncValue maxHeatValue = new IntSyncValue(() -> maxHeatCapacity);
        FloatSyncValue efficiencyValue = new FloatSyncValue(() -> efficiency);
        IntSyncValue fuelRodsValue = new IntSyncValue(() -> reactorSimulator.getTotalFuelRods());
        IntSyncValue heatVentsValue = new IntSyncValue(() -> reactorSimulator.getTotalHeatVents());
        IntSyncValue coolantCellsValue = new IntSyncValue(() -> reactorSimulator.getTotalCoolantCells());
        IntSyncValue heatExchangersValue = new IntSyncValue(() -> reactorSimulator.getTotalHeatExchangers());
        IntSyncValue reflectorsValue = new IntSyncValue(() -> reactorSimulator.getTotalReflectors());

        syncManager.syncValue("heat", heatValue);
        syncManager.syncValue("max_heat", maxHeatValue);
        syncManager.syncValue("efficiency", efficiencyValue);
        syncManager.syncValue("fuel_rods", fuelRodsValue);
        syncManager.syncValue("heat_vents", heatVentsValue);
        syncManager.syncValue("coolant_cells", coolantCellsValue);
        syncManager.syncValue("heat_exchangers", heatExchangersValue);
        syncManager.syncValue("reflectors", reflectorsValue);

        bars.add(barBuilder -> barBuilder
                .progress(() -> maxHeatValue.getIntValue() > 0 ?
                        Math.min(1.0, (double) heatValue.getIntValue() / maxHeatValue.getIntValue()) : 0.0)
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_HEAT)
                .tooltipBuilder(tooltip -> {
                    if (isStructureFormed()) {
                        int heat = heatValue.getIntValue();
                        int maxHeat = maxHeatValue.getIntValue();
                        int heatPercent = maxHeat > 0 ? (heat * 100) / maxHeat : 0;

                        TextFormatting color;
                        String status;
                        if (heatPercent >= 90) {
                            color = TextFormatting.RED;
                            status = "危险";
                        } else if (heatPercent >= 70) {
                            color = TextFormatting.YELLOW;
                            status = "警告";
                        } else if (heatPercent >= 40) {
                            color = TextFormatting.GOLD;
                            status = "正常";
                        } else {
                            color = TextFormatting.GREEN;
                            status = "安全";
                        }

                        tooltip.addLine(IKey.str(color + "热量: " + heat + " / " + maxHeat + " HU (" + heatPercent + "%)"));
                        tooltip.addLine(IKey.str(color + "状态: " + status));

                        if (heatPercent >= 85) {
                            tooltip.addLine(IKey.str(TextFormatting.RED + "警告: 接近熔毁阈值!"));
                        }
                    } else {
                        tooltip.addLine(IKey.str(TextFormatting.RED + "结构不完整"));
                    }
                }));

        bars.add(barBuilder -> barBuilder
                .progress(() -> Math.min(1.0, efficiencyValue.getFloatValue() / 2.0))
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_ENERGY)
                .tooltipBuilder(tooltip -> {
                    if (isStructureFormed()) {
                        float eff = efficiencyValue.getFloatValue();
                        int effPercent = (int) (eff * 100);

                        tooltip.addLine(IKey.str("效率: " + effPercent + "%"));
                        tooltip.addLine(IKey.str("输出: " + currentOutput + " EU/t (" +
                                getOutputPerSecond() + " EU/s)"));

                        if (reflectorsValue.getIntValue() > 0) {
                            int reflectorBonus = Math.round((eff - 1.0f) * 100.0f);
                            tooltip.addLine(IKey.str("反射板: " + reflectorsValue.getIntValue() +
                                    " (+" + reflectorBonus + "%)"));
                        }
                        if (fuelRodsValue.getIntValue() > 0) {
                            tooltip.addLine(IKey.str("燃料棒: " + fuelRodsValue.getIntValue()));
                        }
                        if (heatVentsValue.getIntValue() > 0 || coolantCellsValue.getIntValue() > 0 ||
                                heatExchangersValue.getIntValue() > 0) {
                            tooltip.addLine(IKey.str("散热片: " + heatVentsValue.getIntValue() +
                                    "  冷却单元: " + coolantCellsValue.getIntValue() +
                                    "  热交换器: " + heatExchangersValue.getIntValue()));
                        }
                    } else {
                        tooltip.addLine(IKey.str(TextFormatting.RED + "结构不完整"));
                    }
                }));
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder
                .setWorkingStatus(isReactorActive && !hasMeltdown, isReactorActive && !hasMeltdown)
                .addEnergyUsageLine(getEnergyContainer())
                .addCustom((richText, syncer) -> {
                    if (!isStructureFormed()) return;

                    int heat = syncer.syncInt(currentHeat);
                    int maxHeat = syncer.syncInt(maxHeatCapacity);
                    long output = syncer.syncLong(currentOutput);
                    int fuelRods = syncer.syncInt(reactorSimulator.getTotalFuelRods());
                    int heatVents = syncer.syncInt(reactorSimulator.getTotalHeatVents());
                    int coolantCells = syncer.syncInt(reactorSimulator.getTotalCoolantCells());
                    int heatExchangers = syncer.syncInt(reactorSimulator.getTotalHeatExchangers());
                    int reflectors = syncer.syncInt(reactorSimulator.getTotalReflectors());
                    int plating = syncer.syncInt(reactorSimulator.getTotalPlating());
                    boolean meltdown = syncer.syncBoolean(hasMeltdown);
                    boolean active = syncer.syncBoolean(isReactorActive);
                    boolean working = syncer.syncBoolean(isWorkingEnabled());


                    if (meltdown) {
                        richText.add(IKey.str(TextFormatting.DARK_RED + "✗ 反应堆熔毁"));
                    } else if (active && working) {
                        richText.add(IKey.str(TextFormatting.GREEN + "✓ 运行中"));
                    } else {
                        richText.add(IKey.str(TextFormatting.GRAY + "○ 待机"));
                    }

                    richText.add(IKey.str(TextFormatting.GRAY + "大小: " + reactorWidth + "×" + reactorHeight));

                    richText.add(IKey.str(TextFormatting.GRAY + "燃料棒: " +
                            TextFormatting.WHITE + fuelRods +
                            TextFormatting.GRAY + "  散热片: " +
                            TextFormatting.WHITE + heatVents));

                    richText.add(IKey.str(TextFormatting.GRAY + "冷却单元: " +
                            TextFormatting.WHITE + coolantCells +
                            TextFormatting.GRAY + "  反射板: " +
                            TextFormatting.WHITE + reflectors));

                    if (plating > 0) {
                        int heatBoost = maxHeat - BASE_HEAT_CAPACITY;
                        richText.add(IKey.str(TextFormatting.GRAY + "隔板: " +
                                TextFormatting.WHITE + plating +
                                TextFormatting.GRAY + " (+" + heatBoost + " HU)"));
                    }

                    if (heatExchangers > 0) {
                        richText.add(IKey.str(TextFormatting.GRAY + "热交换器: " +
                                TextFormatting.WHITE + heatExchangers +
                                TextFormatting.GRAY + " (需紧邻散热片/冷却单元)"));
                    }

                    // 添加热量信息
                    int heatPercent = maxHeat > 0 ? (heat * 100) / maxHeat : 0;
                    richText.add(IKey.str(TextFormatting.GRAY + "热量: " +
                            TextFormatting.WHITE + heat + "/" + maxHeat + " HU" +
                            TextFormatting.GRAY + " (" + heatPercent + "%)"));

                    richText.add(IKey.str(TextFormatting.GRAY + "输出: " +
                            TextFormatting.WHITE + output + " EU/t" +
                            TextFormatting.GRAY + " (" + TextFormatting.WHITE + output * UPDATE_TICK_RATE +
                            " EU/s" + TextFormatting.GRAY + ")"));

                    richText.add(IKey.str(TextFormatting.GRAY + "运行: " +
                            TextFormatting.WHITE + syncer.syncInt(reactorSimulator.getTickCount()) + " s" +
                            TextFormatting.GRAY + "  累计发电: " +
                            TextFormatting.WHITE + syncer.syncLong(reactorSimulator.getTotalEnergyProduced()) +
                            " EU"));
                })
                .addWorkingStatusLine();
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        builder.addCustom((list, syncer) -> {
            if (isStructureFormed()) {
                boolean meltdown = syncer.syncBoolean(hasMeltdown);

                if (meltdown) {
                    list.add(IKey.str("§4✗ 反应堆已熔毁！"));
                }
            }
        });
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        super.configureWarningText(builder);

        builder.addCustom((list, syncer) -> {
            if (isStructureFormed()) {
                int heat = syncer.syncInt(currentHeat);
                int maxHeat = syncer.syncInt(maxHeatCapacity);
                long output = syncer.syncLong(currentOutput);
                int fuelRods = syncer.syncInt(reactorSimulator.getTotalFuelRods());
                int heatVents = syncer.syncInt(reactorSimulator.getTotalHeatVents());
                int coolantCells = syncer.syncInt(reactorSimulator.getTotalCoolantCells());

                int heatPercent = maxHeat > 0 ? (heat * 100) / maxHeat : 0;

                if (heatPercent >= 90) {
                    list.add(IKey.str("§c热量危险：" + heatPercent + "% - 即将熔毁！"));
                } else if (heatPercent >= 80) {
                    list.add(IKey.str("§6热量警告：" + heatPercent + "% - 请立即冷却！"));
                } else if (heatPercent >= 70) {
                    list.add(IKey.str("§e热量偏高：" + heatPercent + "%"));
                }

                int depletedComponents = syncer.syncInt(countDepletedComponents());
                if (depletedComponents > 0) {
                    list.add(IKey.str("§e" + depletedComponents + "个组件已耗尽"));
                }

                boolean noCooling = syncer.syncBoolean(
                        heat > 1000 && heatVents == 0 && coolantCells == 0);

                if (noCooling) {
                    list.add(IKey.str("§e未检测到冷却系统"));
                }

                boolean noFuel = syncer.syncBoolean(
                        fuelRods == 0 && output > 0);

                if (noFuel) {
                    list.add(IKey.str("§e未检测到燃料棒"));
                }
            }
        });
    }

    private int countDepletedComponents() {
        int count = 0;
        for (int slot = 0; slot < componentHandler.getSlots(); slot++) {
            ItemStack stack = componentHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                NuclearComponentBehavior behavior = NuclearComponentBehavior.getInstanceFor(stack);
                if (behavior != null) {
                    int damage = AbstractMaterialPartBehavior.getPartDamage(stack);
                    int maxDurability = behavior.getPartMaxDurability(stack);
                    if (damage >= maxDurability) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.NUCLEAR_REACTOR_CASING;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), isReactorActive,
                isWorkingEnabled());
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.NUCLEAR_REACTOR_OVERLAY;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @Override
    public @NotNull List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();

        if (isStructureFormed()) {
            list.add(new TextComponentString("大小: " + reactorWidth + "×" + reactorHeight));
            list.add(new TextComponentString("热量: " + currentHeat + " / " + maxHeatCapacity + " HU"));
            list.add(new TextComponentString("能量输出: " + currentOutput + " EU/t (" +
                    getOutputPerSecond() + " EU/s)"));
            list.add(new TextComponentString("效率: " + String.format("%.1f", efficiency * 100) + "%"));

            if (hasMeltdown) {
                list.add(new TextComponentString(TextFormatting.RED + "熔毁状态！"));
            } else if (isReactorActive) {
                list.add(new TextComponentString(TextFormatting.GREEN + "运行中"));
            } else {
                list.add(new TextComponentString(TextFormatting.GRAY + "待机"));
            }

            list.add(new TextComponentString("燃料棒: " + reactorSimulator.getTotalFuelRods()));
            list.add(new TextComponentString("散热片: " + reactorSimulator.getTotalHeatVents()));
            list.add(new TextComponentString("冷却单元: " + reactorSimulator.getTotalCoolantCells()));
            list.add(new TextComponentString("热交换器: " + reactorSimulator.getTotalHeatExchangers()));
            list.add(new TextComponentString("反射板: " + reactorSimulator.getTotalReflectors()));
        }

        return list;
    }

    @Override
    public int getProgress() {
        if (maxHeatCapacity > 0) {
            return (currentHeat * 100) / maxHeatCapacity;
        }
        return 0;
    }

    @Override
    public int getMaxProgress() {
        return 100;
    }

    public boolean hasMeltdown() {
        return hasMeltdown;
    }

    public boolean isReactorActive() {
        return isReactorActive && !hasMeltdown;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public SoundEvent getSound() {
        return GTSoundEvents.FURNACE;
    }

    @Override
    public void addInformation(ItemStack stack, World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        InformationHandler.topTooltips("核电之星", tooltip);
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add("多方块核裂变反应堆-通过受控核裂变链式反应产生大量能源");
        tooltip.add(TextFormatting.GREEN + I18n.format("-工作原理："));
        tooltip.add("反应堆核心通过铀/钚燃料棒的核裂变过程产生能量，每次裂变释放大量热能和中子，中子撞击其他燃料原子引发链式反应");
        tooltip.add("产生的热能需要通过散热系统持续移除，否则热量累积将导致反应堆过热甚至熔毁，同时热能会按比例转换为EU电力输出");
        tooltip.add("中子反射板将逃逸的中子反射回燃料棒，提高裂变效率但同时增加热量产生，需要精密的热平衡设计");
        tooltip.add("当热量超过9500HU阈值时，反应堆将发生不可逆的熔毁，摧毁所有内部组件并对周围环境造成严重破坏");
        tooltip.add(TextFormatting.GREEN + I18n.format("-拓展升级："));
        tooltip.add("通过安装燃料拓展仓来扩展核反应堆的内部空间。");
        tooltip.add("每安装一个燃料拓展仓，内部组件空间在X方向增加1格：初始" +
                BASE_REACTOR_WIDTH + "×" + REACTOR_HEIGHT + "，最多" +
                MAX_REACTOR_WIDTH + "×" + REACTOR_HEIGHT + "（" + MAX_REACTOR_WIDTH * REACTOR_HEIGHT +
                "个槽位，需" + MAX_EXTEND_HATCHES + "个拓展仓）。");
        tooltip.add(TextFormatting.GREEN + I18n.format("-组件功能："));
        tooltip.add("燃料棒-反应堆的核心能源来源，基础输出功率取决于燃料类型，相邻燃料棒会产生额外的链式反应加成");
        tooltip.add("散热片-被动散热组件，每秒移除固定量热量，分为普通散热片(冷却自身)和元件散热片(冷却相邻燃料棒)");
        tooltip.add("冷却单元-高效主动冷却组件，能大量吸收热量但会随使用逐渐消耗，需要定期更换以维持反应堆安全");
        tooltip.add("热交换器-自身不排热，必须紧邻散热片或冷却单元才能工作，可把堆芯热量(或元件热交换器把相邻燃料棒热量)搬运给热沉");
        tooltip.add("中子反射板-每个相邻反射板按其反射效率提供最多20%效率加成，同样增加热量产生，是效率与风险的平衡选择");
        tooltip.add("反应堆隔板-强化反应堆结构，每块隔板增加热容量和爆炸抗性，是防止熔毁的关键安全组件");
        tooltip.add("耐久说明-所有组件仅在真正工作时消耗耐久（燃料棒持续燃烧、散热片/冷却单元在排热、反射板在反射中子），闲置时不损耗");
        tooltip.add(TextFormatting.GREEN + I18n.format("-IO功能："));
        tooltip.add("为核反应堆安装输入/输出总线后");
        tooltip.add("可自动将输入总线内的部件填充至反应堆空缺处");
        tooltip.add("可自动将反应堆内用尽部件输出至输出总线");
        tooltip.add(TextFormatting.GREEN + I18n.format("-重要警告："));
        tooltip.add("反应堆熔毁将释放毁灭性爆炸，其威力与内部热量和燃料数量成正比，可能摧毁整个基地! 务必安装应急冷却系统并在设计中预留安全冗余。");
    }
}
