package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IQuantumController;
import gregtech.api.capability.IQuantumStorage;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.IUpgradeSlots;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.client.utils.PipelineUtil;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public class MetaTileEntityEnergyHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IEnergyContainer>,
                   IQuantumStorage<IEnergyContainer>,
                   IUpgradeSlots {

    protected final boolean isExportHatch;
    /** 仓室额定电流，同时也是可调电流的上限。 */
    protected final int amperage;
    /** 升级插件槽位；界面入口由 {@link MetaTileEntity#createUpgradeSlotsButton} 挂到侧边栏上。 */
    protected final GTItemStackHandler upgradeInventory;

    /** 可调电压，范围 1 ~ {@link #getMaxVoltage()}。 */
    private long voltage;
    /** 可调电流，范围 1 ~ {@link #getMaxAmperage()}。 */
    private int currentAmperage;

    @Nullable
    protected EnergyContainerHandler energyContainer;

    /** not synced, server only. lazily initialized from pos */
    private WeakReference<IQuantumController> controller = new WeakReference<>(null);

    /** synced, server and client */
    private BlockPos controllerPos;

    public MetaTileEntityEnergyHatch(ResourceLocation metaTileEntityId, int tier, int amperage, boolean isExportHatch) {
        super(metaTileEntityId, tier);
        this.isExportHatch = isExportHatch;
        this.amperage = amperage;
        this.upgradeInventory = new GTItemStackHandler(this, IUpgradeSlots.UPGRADE_SLOT_COUNT);
        this.voltage = GTValues.V[tier];
        this.currentAmperage = amperage;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityEnergyHatch(metaTileEntityId, getTier(), amperage, isExportHatch);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            getOverlay().renderSided(getFrontFacing(), renderState, translation,
                    PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
        }
    }

    @Override
    public void update() {
        checkWeatherOrTerrainExplosion(getTier(), getTier() * 10, getEnergyContainer());
        super.update();
    }

    @NotNull
    private SimpleOverlayRenderer getOverlay() {
        if (isExportHatch) {
            if (amperage <= 2) {
                return Textures.ENERGY_OUT_MULTI;
            } else if (amperage <= 4) {
                return Textures.ENERGY_OUT_HI;
            } else if (amperage <= 16) {
                return Textures.ENERGY_OUT_ULTRA;
            } else {
                return Textures.ENERGY_OUT_MAX;
            }
        } else {
            if (amperage <= 2) {
                return Textures.ENERGY_IN_MULTI;
            } else if (amperage <= 4) {
                return Textures.ENERGY_IN_HI;
            } else if (amperage <= 16) {
                return Textures.ENERGY_IN_ULTRA;
            } else {
                return Textures.ENERGY_IN_MAX;
            }
        }
    }

    @Override
    public MultiblockAbility<IEnergyContainer> getAbility() {
        return isExportHatch ? MultiblockAbility.OUTPUT_ENERGY : MultiblockAbility.INPUT_ENERGY;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(getEnergyContainer());
    }

    // region 可调电压/电流

    /** 该仓室允许设置的最高电压，由其等级决定。 */
    public long getMaxVoltage() {
        return GTValues.V[getTier()];
    }

    /** 该仓室允许设置的最高电流，即额定电流。 */
    public int getMaxAmperage() {
        return this.amperage;
    }

    /** 当前设置的电压。 */
    public long getVoltage() {
        return this.voltage;
    }

    /**
     * 设置电压，超出范围会被夹到 {@code [1, }{@link #getMaxVoltage()}{@code ]}。
     *
     * @param voltage 目标电压
     */
    public void setVoltage(long voltage) {
        long clamped = Math.max(1L, Math.min(voltage, getMaxVoltage()));
        if (clamped == this.voltage) {
            return;
        }
        this.voltage = clamped;
        onConfigurationChanged();
    }

    /** 当前设置的电流。 */
    public int getCurrentAmperage() {
        return this.currentAmperage;
    }

    /**
     * 设置电流，超出范围会被夹到 {@code [1, }{@link #getMaxAmperage()}{@code ]}。
     *
     * @param amperage 目标电流
     */
    public void setCurrentAmperage(int amperage) {
        int clamped = MathHelper.clamp(amperage, 1, getMaxAmperage());
        if (clamped == this.currentAmperage) {
            return;
        }
        this.currentAmperage = clamped;
        onConfigurationChanged();
    }

    protected void onConfigurationChanged() {
        if (getWorld() == null || getWorld().isRemote) {
            return;
        }
        // 数值本身不用通知别人：容器与多方块的 EnergyContainerList 都是实时读取当前配置的
        markDirty();
    }

    /**
     * 能量容器的储能上限：只由等级电压与额定电流决定，与可调的电压/电流无关。
     */
    public long getEnergyCapacity() {
        return isExportHatch ?
                GTValues.V[getTier()] * 64L * amperage :
                GTValues.V[getTier()] * 16L * amperage;
    }

    @NotNull
    public EnergyContainerHandler getEnergyContainer() {
        EnergyContainerHandler container = this.energyContainer;
        if (container == null) {
            container = createEnergyContainer();
            this.energyContainer = container;
        }
        return container;
    }

    @NotNull
    protected EnergyContainerHandler createEnergyContainer() {
        EnergyContainerHandler container = new HatchEnergyContainer(this, getEnergyCapacity());
        if (isExportHatch) {
            container.setSideOutputCondition(s -> s == getFrontFacing());
        }
        return container;
    }

    private static final class HatchEnergyContainer extends EnergyContainerHandler {

        private final MetaTileEntityEnergyHatch hatch;

        private HatchEnergyContainer(@NotNull MetaTileEntityEnergyHatch hatch, long maxCapacity) {
            super(hatch, maxCapacity,
                    hatch.isExportHatch ? 0L : hatch.voltage,
                    hatch.isExportHatch ? 0L : hatch.currentAmperage,
                    hatch.isExportHatch ? hatch.voltage : 0L,
                    hatch.isExportHatch ? hatch.currentAmperage : 0L);
            this.hatch = hatch;
        }

        @Override
        public long getInputVoltage() {
            return hatch.isExportHatch ? 0L : hatch.voltage;
        }

        @Override
        public long getInputAmperage() {
            return hatch.isExportHatch ? 0L : hatch.currentAmperage;
        }

        @Override
        protected long getMaxSafeInputVoltage() {
            // 可调电压只是对外声明/节流，仓室硬件仍按自身等级耐压：UV 仓调到 ZPM 也照样接 UV 电不炸
            return hatch.isExportHatch ? 0L : hatch.getMaxVoltage();
        }

        @Override
        public long getOutputVoltage() {
            return hatch.isExportHatch ? hatch.voltage : 0L;
        }

        @Override
        public long getOutputAmperage() {
            return hatch.isExportHatch ? hatch.currentAmperage : 0L;
        }
    }

    @Override
    public @NotNull IItemHandlerModifiable getUpgradeInventory() {
        return this.upgradeInventory;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        LongSyncValue voltageValue = new LongSyncValue(this::getVoltage, this::setVoltage);
        guiSyncManager.syncValue("hatchVoltage", voltageValue);
        IntSyncValue voltageTierValue = new IntSyncValue(
                () -> GTUtility.getFloorTierByVoltage(getVoltage()),
                tier -> voltageValue.setLongValue(GTValues.V[MathHelper.clamp(tier, 0, getTier())]));
        guiSyncManager.syncValue("hatchVoltageTier", voltageTierValue);

        IntSyncValue amperageValue = new IntSyncValue(this::getCurrentAmperage, this::setCurrentAmperage);
        guiSyncManager.syncValue("hatchAmperage", amperageValue);

        return GTGuis.createPanel(this, 176, 205)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(Flow.column()
                        .margin(7, 18)
                        .coverChildrenHeight()
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .childPadding(4)
                        .child(new SliderWidget()
                                .widthRel(1.0f)
                                .sliderWidth(30)
                                .bounds(0, Math.max(1, getTier()))
                                .stopper(1)
                                .value(voltageTierValue)
                                .background(GTGuiTextures.FLUID_SLOT.asIcon().margin(7, 0))
                                .sliderTexture(IDrawable.of(GuiTextures.BUTTON_CLEAN,
                                        IKey.dynamic(() -> GTValues.VNF[voltageTierValue.getIntValue()]))))
                        .child(IKey.lang("gregtech.machine.energy_hatch.voltage").asWidget())
                        .child(new TextFieldWidget()
                                .widthRel(1.0f)
                                .height(16)
                                .value(voltageValue)
                                .setNumbersLong(() -> 1L, this::getMaxVoltage)
                                .setMaxLength(19)
                                .background(GTGuiTextures.DISPLAY))
                        .child(IKey.lang("gregtech.machine.energy_hatch.amperage").asWidget())
                        .child(Flow.row()
                                .widthRel(1.0f)
                                .coverChildrenHeight()
                                .child(new ButtonWidget<>()
                                        .size(20)
                                        .overlay(KeyUtil.createMultiplierKey(false))
                                        .tooltip(tooltip -> tooltip
                                                .addLine(IKey.lang(
                                                        "gregtech.machine.energy_hatch.amperage.decrease")))
                                        .onMousePressed(mouse -> {
                                            amperageValue.setIntValue(Math.max(1,
                                                    amperageValue.getIntValue() -
                                                            GTUtility.getButtonIncrementValue()));
                                            return true;
                                        }))
                                .child(new TextFieldWidget()
                                        .height(20)
                                        .expanded()
                                        .margin(4, 0)
                                        .value(amperageValue)
                                        .setNumbersLong(() -> 1L, () -> (long) getMaxAmperage())
                                        .setMaxLength(9)
                                        .background(GTGuiTextures.DISPLAY))
                                .child(new ButtonWidget<>()
                                        .size(20)
                                        .overlay(KeyUtil.createMultiplierKey(true))
                                        .tooltip(tooltip -> tooltip
                                                .addLine(IKey.lang(
                                                        "gregtech.machine.energy_hatch.amperage.increase")))
                                        .onMousePressed(mouse -> {
                                            amperageValue.setIntValue(Math.min(getMaxAmperage(),
                                                    amperageValue.getIntValue() +
                                                            GTUtility.getButtonIncrementValue()));
                                            return true;
                                        })))
                        .child(IKey.lang("gregtech.machine.energy_hatch.throughput",
                                () -> new Object[] {
                                        TextFormattingUtil.formatNumbers(getVoltage() * getCurrentAmperage()) })
                                .asWidget()))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        super.clearMachineInventory(itemBuffer);
        clearInventory(itemBuffer, upgradeInventory);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip, boolean advanced) {
        String tierName = GTValues.VNF[getTier()];
        addDescriptorTooltip(stack, world, tooltip, advanced);

        if (isExportHatch) {
            tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out", getMaxVoltage(), tierName));
            tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_out_till", getMaxAmperage()));
            tooltip.add(I18n.format("gregtech.universal.tooltip.throughput", getMaxVoltage() * getMaxAmperage()));
        } else {
            tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_in", getMaxVoltage(), tierName));
            tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_in_till", getMaxAmperage()));
            tooltip.add(I18n.format("gregtech.universal.tooltip.throughput", getMaxVoltage() * getMaxAmperage()));
        }
        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity", getEnergyCapacity()));
        tooltip.add(I18n.format("gregtech.machine.energy_hatch.adjustable.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.energy_hatch.adjustable.tooltip.2"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    protected void addDescriptorTooltip(ItemStack stack, @Nullable World world, List<String> tooltip,
                                        boolean advanced) {
        if (isExportHatch) {
            if (amperage > 2) {
                tooltip.add(I18n.format("gregtech.machine.energy_hatch.output_hi_amp.tooltip"));
            } else {
                tooltip.add(I18n.format("gregtech.machine.energy_hatch.output.tooltip"));
            }
        } else {
            if (amperage > 2) {
                tooltip.add(I18n.format("gregtech.machine.energy_hatch.input_hi_amp.tooltip"));
            } else {
                tooltip.add(I18n.format("gregtech.machine.energy_hatch.input.tooltip"));
            }
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public boolean canRenderFrontFaceX() {
        return isExportHatch;
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        // override here is gross, but keeps things in order despite
        // IDs being out of order, due to EV 4A and UEV+ 4A+ hatches being added later
        if (this == MetaTileEntities.ENERGY_INPUT_HATCH[0]) {
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_INPUT_HATCH) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_OUTPUT_HATCH) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_INPUT_HATCH_4A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_INPUT_HATCH_16A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_INPUT_HATCH_64A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_OUTPUT_HATCH_4A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_OUTPUT_HATCH_16A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.ENERGY_OUTPUT_HATCH_64A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.SUBSTATION_ENERGY_INPUT_HATCH) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.SUBSTATION_ENERGY_INPUT_HATCH_256A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.SUBSTATION_ENERGY_OUTPUT_HATCH) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
            for (MetaTileEntityEnergyHatch hatch : MetaTileEntities.SUBSTATION_ENERGY_OUTPUT_HATCH_256A) {
                if (hatch != null) subItems.add(hatch.getStackForm());
            }
        } else if (this.getClass() != MetaTileEntityEnergyHatch.class &&
                this.getClass() != MetaTileEntitySubstationEnergyHatch.class) {
            // let subclasses fall through this override
            super.getSubItems(creativeTab, subItems);
        }
    }

    @Override
    public void doExplosion(float explosionPower) {
        if (getController() != null)
            getController().explodeMultiblock(explosionPower);
        else {
            super.doExplosion(explosionPower);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (!getWorld().isRemote && isConnected()) {
            IQuantumController controller = getQuantumController();
            if (controller != null) controller.rebuildNetwork();
        }
    }

    @Override
    public void onPlacement(@Nullable EntityLivingBase placer) {
        super.onPlacement(placer);
        if (getWorld() == null || getWorld().isRemote || isExportHatch)
            return;

        // add to the network if an adjacent block is part of a network
        // use whatever we find first, merging networks is not supported
        tryFindNetwork();
    }

    @Override
    public Type getType() {
        return Type.ENERGY;
    }

    @Override
    public ICubeRenderer getBaseTexture() {
        if (isConnected()) {
            return Textures.QUANTUM_CASING;
        }
        return super.getBaseTexture();
    }

    @Override
    public void setConnected(IQuantumController controller) {
        if (getWorld().isRemote) return;
        if (isExportHatch) return;

        if (!controller.getPos().equals(controllerPos)) {
            this.controller = new WeakReference<>(controller);
            this.controllerPos = controller.getPos();
            writeCustomData(GregtechDataCodes.UPDATE_CONTROLLER_POS, buf -> buf.writeBlockPos(controllerPos));
            markDirty();
        }
    }

    @Override
    public void setDisconnected() {
        if (getWorld().isRemote) return;

        controller.clear();
        controllerPos = null;
        writeCustomData(GregtechDataCodes.REMOVE_CONTROLLER, buf -> {});
        markDirty();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_CONTROLLER_POS) {
            this.controllerPos = buf.readBlockPos();
            this.controller.clear();
            scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.REMOVE_CONTROLLER) {
            this.controllerPos = null;
            this.controller.clear();
            scheduleRenderUpdate();
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        // 先建好容器，保证 trait 表两端一致（容器的初始同步数据为空，但网络 id 要对上）
        getEnergyContainer();
        super.writeInitialSyncData(buf);
        buf.writeBoolean(controllerPos != null);
        if (controllerPos != null) {
            buf.writeBlockPos(controllerPos);
        }
        buf.writeLong(this.voltage);
        buf.writeInt(this.currentAmperage);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        getEnergyContainer();
        super.receiveInitialSyncData(buf);
        if (buf.readBoolean()) {
            controllerPos = buf.readBlockPos();
            scheduleRenderUpdate();
        }
        this.voltage = buf.readLong();
        this.currentAmperage = buf.readInt();
    }

    // use this to make sure controller is properly initialized
    @Override
    public final IQuantumController getQuantumController() {
        if (isConnected()) {
            if (controller.get() != null) return controller.get();
            MetaTileEntity mte = GTUtility.getMetaTileEntity(getWorld(), controllerPos);
            if (mte instanceof IQuantumController quantumController) {
                controller = new WeakReference<>(quantumController);
                return quantumController;
            } else {
                // controller is no longer there for some reason, need to disconnect
                setDisconnected();
                tryFindNetwork();
            }
        }
        return null;
    }

    @Override
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Override
    public IEnergyContainer getTypeValue() {
        return getEnergyContainer();
    }

    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing side) {
        if (capability == GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER) {
            getEnergyContainer();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tagCompound = super.writeToNBT(data);
        tagCompound.setBoolean("HasController", controllerPos != null);
        if (controllerPos != null) {
            tagCompound.setLong("ControllerPos", controllerPos.toLong());
        }
        tagCompound.setTag("UpgradeInventory", upgradeInventory.serializeNBT());
        tagCompound.setLong("Voltage", this.voltage);
        tagCompound.setInteger("Amperage", this.currentAmperage);
        return tagCompound;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        this.voltage = Math.max(1L, Math.min(data.getLong("Voltage"), getMaxVoltage()));
        this.currentAmperage = MathHelper.clamp(data.getInteger("Amperage"), 1, getMaxAmperage());
        getEnergyContainer();
        super.readFromNBT(data);
        if (data.getBoolean("HasController")) {
            this.controllerPos = BlockPos.fromLong(data.getLong("ControllerPos"));
        }
        this.upgradeInventory.deserializeNBT(data.getCompoundTag("UpgradeInventory"));
    }
}
