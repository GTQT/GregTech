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
import gregtech.common.item.behaviors.NuclearComponentBehavior;
import gregtech.common.items.behaviors.AbstractMaterialPartBehavior;
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
import static gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.NuclearAbility.STOP_WORK;
import static net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND;

public class MetaTileEntityNuclearReactor extends MetaTileEntityBaseWithControl implements ProgressBarMultiblock {

    private static final int UPDATE_TICK_RATE = 20;
    private static final int BASE_HEAT_CAPACITY = 10000;
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
                    .hatch(SCMultiblockAbility.REACTOR_EXTEND_HATCH, 0, 1)
                    .done()
                    .any(' ')
                    .buildStructureDefinition()
    );
    @Getter
    private final int reactorWidth = 9;
    @Getter
    private final int reactorHeight = 6;
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
        this.componentHandler = createComponentHandler(width, height);
    }

    private GTItemStackHandler createComponentHandler(int width, int height) {
        return new GTItemStackHandler(this, width * height) {
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
                if (!getWorld().isRemote && isStructureFormed()) {
                    markDirty();
                }
            }
        };
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityNuclearReactor(metaTileEntityId);
    }

    public List<INuclearExtend> getExtendHatch() {
        List<INuclearExtend> abilities = getAbilities(SCMultiblockAbility.REACTOR_EXTEND_HATCH);
        return abilities.isEmpty() ? null : abilities;
    }

    @Override
    protected void formStructure(FormedStructureView formed) {
        super.formStructure(formed);
        List<INuclearExtend> extendHatches = getExtendHatch();
        if (getExtendHatch() != null) extendCount = extendHatches.size();
    }

    public boolean haveAbilities(NuclearAbility ability) {
        if (extendCount > 0) {
            for (INuclearExtend extend : getExtendHatch()) {
                if (extend.getUpdateAbilities().contains(ability)) return true;
            }
        }
        return false;
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
        if (getInputInventory().getSlots() > 0) {
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

            syncInventoryToSimulator();

            boolean success = reactorSimulator.simulateTick();

            if (!success) {
                hasMeltdown = true;
                handleMeltdown();
                return;
            }

            updateLocalCache();

            syncSimulatorToInventory();

            markDirty();
            writeCustomData(SYNC_REACTOR_STATE, buf -> {
                buf.writeInt(currentHeat);
                buf.writeInt(maxHeatCapacity);
                buf.writeLong(currentOutput);
                buf.writeBoolean(isReactorActive);
                buf.writeBoolean(hasMeltdown);
            });
        }
        outputEnergy();

        if (haveAbilities(STOP_WORK) && reactorSimulator.isOverHeat()) {
            setWorkingEnabled(false);
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

    private void outputEnergy() {
        if (currentOutput > 0 && !hasMeltdown) {
            long energyPerTick = currentOutput;

            if (outEnergyContainer != null) {
                outEnergyContainer.addEnergy(energyPerTick);
            }
        }
    }

    private void handleMeltdown() {
        isReactorActive = false;
        currentOutput = 0;

        for (int slot = 0; slot < componentHandler.getSlots(); slot++) {
            componentHandler.setStackInSlot(slot, ItemStack.EMPTY);
        }

        float explosionPower = calculateExplosionPower();
        doExplosion(explosionPower);

        syncInventoryToSimulator();
    }

    private float calculateExplosionPower() {
        float basePower = 20.0f;

        int heat = reactorSimulator.getCurrentHeat();
        int fuelRods = reactorSimulator.getTotalFuelRods();

        float heatFactor = Math.min(heat / 10000.0f, 2.0f);
        float fuelFactor = Math.min(fuelRods / 5.0f, 2.0f);

        return basePower * (1.0f + heatFactor + fuelFactor);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
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

        initializeReactor(reactorWidth, reactorHeight);

        if (data.hasKey("ComponentInventory")) {
            try {
                componentHandler.deserializeNBT(data.getCompoundTag("ComponentInventory"));
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

        int slotCount = buf.readVarInt();
        for (int slot = 0; slot < Math.min(slotCount, componentHandler.getSlots()); slot++) {
            try {
                ItemStack stack = buf.readItemStack();
                componentHandler.setStackInSlot(slot, stack);
            } catch (IOException e) {
                GTLog.logger.error("Error reading inventory from network", e);
            }
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
        IntSyncValue reflectorsValue = new IntSyncValue(() -> reactorSimulator.getTotalReflectors());

        syncManager.syncValue("heat", heatValue);
        syncManager.syncValue("max_heat", maxHeatValue);
        syncManager.syncValue("efficiency", efficiencyValue);
        syncManager.syncValue("fuel_rods", fuelRodsValue);
        syncManager.syncValue("heat_vents", heatVentsValue);
        syncManager.syncValue("coolant_cells", coolantCellsValue);
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
                        tooltip.addLine(IKey.str("基础输出: " + currentOutput + " EU/t"));

                        if (reflectorsValue.getIntValue() > 0) {
                            tooltip.addLine(IKey.str("反射板加成: +" + (reflectorsValue.getIntValue() * 20) + "%"));
                        }
                        if (fuelRodsValue.getIntValue() > 1) {
                            tooltip.addLine(IKey.str("燃料棒邻接加成"));
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

                    // 添加热量信息
                    int heatPercent = maxHeat > 0 ? (heat * 100) / maxHeat : 0;
                    richText.add(IKey.str(TextFormatting.GRAY + "热量: " +
                            TextFormatting.WHITE + heat + "/" + maxHeat + " HU" +
                            TextFormatting.GRAY + " (" + heatPercent + "%)"));

                    richText.add(IKey.str(TextFormatting.GRAY + "输出: " +
                            TextFormatting.WHITE + output + " EU/t"));
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
            list.add(new TextComponentString("能量输出: " + currentOutput + " EU/t"));
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
        tooltip.add("通过安装燃料拓展仓来扩展核反应堆的功能属性。");
        tooltip.add(TextFormatting.GREEN + I18n.format("-组件功能："));
        tooltip.add("燃料棒-反应堆的核心能源来源，基础输出功率取决于燃料类型，相邻燃料棒会产生额外的链式反应加成");
        tooltip.add("散热片-被动散热组件，每tick移除固定量热量，分为普通散热片(冷却自身)和元件散热片(冷却相邻燃料棒)");
        tooltip.add("冷却单元-高效主动冷却组件，能大量吸收热量但会随使用逐渐消耗，需要定期更换以维持反应堆安全");
        tooltip.add("中子反射板-每个相邻反射板为燃料棒提供20%效率加成，但同样增加20%热量产生，是效率与风险的平衡选择");
        tooltip.add("反应堆隔板-强化反应堆结构，每块隔板增加热容量和爆炸抗性，是防止熔毁的关键安全组件");
        tooltip.add(TextFormatting.GREEN + I18n.format("-IO功能："));
        tooltip.add("为核反应堆安装输入/输出总线后");
        tooltip.add("可自动将输入总线内的部件填充至反应堆空缺处");
        tooltip.add("可自动将反应堆内用尽部件输出至输出总线");
        tooltip.add(TextFormatting.GREEN + I18n.format("-重要警告："));
        tooltip.add("反应堆熔毁将释放毁灭性爆炸，其威力与内部热量和燃料数量成正比，可能摧毁整个基地! 务必安装应急冷却系统并在设计中预留安全冗余。");
    }
}
