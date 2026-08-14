package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IQCComponentHatch;
import gregtech.api.capability.IQCUncertaintyHatch;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockComputerCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 量子计算机 (Quantum Computer)。
 * <p>
 * MetaTileEntityHPCA 的上位：2 列宽的可变长度结构（GT5U 原版布局，3~14 层，
 * 每层并排 2 个 Rack 舱），组件为物品形态（量子电路，属性由矿辞 circuit + tier 解析，
 * 见 {@link gregtech.api.capability.QCComponentRegistry}）。
 * 数据输出机制与 HPCA 完全一致 —— 实现 {@link IOpticalComputationProvider}（CWU/t 请求-分配），
 * 下游（光学发射舱/网络交换机/研究站/无线云舱）零改动即可消费。
 * <p>
 * 温度模型为 HPCA 式整体温度：升温 ∝ 实际分配算力比例 × 总产热需求，
 * 由流体舱注入的 PCBCoolant 主动水冷抵消（每 Rack 固定冷却能力），
 * 超温（≥1200）时概率销毁 Rack 内计算组件。不确定性舱小游戏全部焦点配平
 * （status == 0）才允许产出算力，解析模式由结构层数决定（1~5）。
 */
public class MetaTileEntityQuantumComputer extends MultiblockWithDisplayBase
        implements IOpticalComputationProvider, IControllable, ProgressBarMultiblock {

    private static final double IDLE_TEMPERATURE = 200;
    private static final double DAMAGE_TEMPERATURE = 1200;

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:quantum_computer", () -> DeclarativePatternBuilder.start()
                    .piece("top")
                    .aisle("BB", "BB", "BB", "BB")
                    .aisle("BB", "CB", "CB", "BB")
                    .repeatablePiece("body", 3, 14)
                    .aisle("DB", "EB", "EB", "DB")
                    .withAisleChannel(GTStructureChannels.STRUCTURE_LENGTH.getName())
                    // body 无 S 且 aisle 数(1)与含 S 的 bottom(2)不同，必须显式指定避免继承 reference 错位；
                    // x/y 与 bottom 中 S 的坐标对齐，z = 本 piece 内中心 aisle 索引(单 aisle = 0)
                    .centerOffset(0, 1, 0)
                    .piece("bottom")
                    .aisle("BB", "CB", "CB", "BB")
                    .aisle("BB", "SB", "BB", "BB")
                    // 含 S 的 piece：centerOffset 由 initializeCenterOffsets 自动计算，无需手动指定
                    .self('S', MetaTileEntityQuantumComputer.class)
                    .block('C', getAdvancedState())
                    .block('D', getVentState())
                    .where('E', Elements.withDefaultCandidate(
                            Elements.abilities(MultiblockAbility.QC_COMPONENT),
                            () -> MetaTileEntities.QC_RACK))
                    .casing('C', getAdvancedState())
                    .casing('B', getCasingState())
                    .hatch(MultiblockAbility.MAINTENANCE_HATCH, 1, 1,
                            () -> MetaTileEntities.MAINTENANCE_HATCH)
                    .hatch(MultiblockAbility.INPUT_ENERGY, 1, 3,
                            () -> MetaTileEntities.ENERGY_INPUT_HATCH[GTValues.UV])
                    .hatch(MultiblockAbility.COMPUTATION_DATA_TRANSMISSION, 1, 1,
                            () -> MetaTileEntities.COMPUTATION_HATCH_TRANSMITTER[GTValues.UV])
                    .hatch(MultiblockAbility.IMPORT_FLUIDS, 0, 1,
                            () -> MetaTileEntities.FLUID_IMPORT_HATCH[GTValues.LV])
                    .hatch(MultiblockAbility.QC_UNCERTAINTY, 1, 1,
                            () -> MetaTileEntities.QC_UNCERTAINTY_HATCH)
                    .buildStructureDefinition());

    private final QCGridHandler qcHandler;
    private IEnergyContainer energyContainer;
    private IFluidHandler coolantHandler;
    private boolean isActive;
    private boolean isWorkingEnabled = true;
    private boolean hasNotEnoughEnergy;
    private double temperature = IDLE_TEMPERATURE;

    public MetaTileEntityQuantumComputer(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
        this.coolantHandler = new FluidTankList(false, new ArrayList<>());
        this.qcHandler = new QCGridHandler(this);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_CASING);
    }

    private static @NotNull IBlockState getAdvancedState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.ADVANCED_COMPUTER_CASING);
    }

    private static @NotNull IBlockState getVentState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_HEAT_VENT);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQuantumComputer(metaTileEntityId);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        this.energyContainer = new EnergyContainerList(getAbilities(MultiblockAbility.INPUT_ENERGY));
        this.coolantHandler = new FluidTankList(false, getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.qcHandler.onStructureForm(getAbilities(MultiblockAbility.QC_COMPONENT),
                getAbilities(MultiblockAbility.QC_UNCERTAINTY));
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
        this.coolantHandler = new FluidTankList(false, new ArrayList<>());
        this.qcHandler.onStructureInvalidate();
    }

    // region IOpticalComputationProvider —— 与 HPCA 同构的 CWU/t 请求-分配

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return isActive() && isWorkingEnabled() && !hasNotEnoughEnergy && qcHandler.isResolved()
                ? qcHandler.allocateCWUt(cwut, simulate)
                : 0;
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return isActive() && isWorkingEnabled() && qcHandler.isResolved() ? qcHandler.getMaxCWUt() : 0;
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        // 量子计算机无桥接组件，结构成型即可接入网络交换机
        return !isStructureFormed();
    }

    // endregion

    @Override
    protected void updateFormedValid() {
        // 档位上限由层数决定：maxMode = min(5, max(1, rackCount / 3))
        if (isWorkingEnabled()) {
            consumeEnergy();
        }
        if (isActive()) {
            double temperatureChange = qcHandler.calculateTemperatureChange(coolantHandler) / 2.0;
            if (temperature + temperatureChange <= IDLE_TEMPERATURE) {
                temperature = IDLE_TEMPERATURE;
            } else {
                temperature += temperatureChange;
            }
            if (temperature >= DAMAGE_TEMPERATURE) {
                // 1% 概率每 tick 销毁一个 Rack 内计算物品（平均 10 秒一次）
                if (GTValues.RNG.nextInt(200) == 0) {
                    qcHandler.destroyRandomComputingComponent();
                }
            }
            qcHandler.tick();
        } else {
            qcHandler.clearComputationCache();
            // 停机自然降温
            temperature = Math.max(IDLE_TEMPERATURE, temperature - 0.25);
        }
    }

    private void consumeEnergy() {
        long energyToConsume = qcHandler.getCurrentEUt();
        boolean hasMaintenance = ConfigHolder.machines.enableMaintenance && hasMaintenanceMechanics();
        if (hasMaintenance) {
            // 10% more energy per maintenance problem
            energyToConsume += getNumMaintenanceProblems() * energyToConsume / 10;
        }

        if (this.hasNotEnoughEnergy && energyContainer.getInputPerSec() > 19L * energyToConsume) {
            this.hasNotEnoughEnergy = false;
        }

        if (this.energyContainer.getEnergyStored() >= energyToConsume) {
            if (!hasNotEnoughEnergy) {
                long consumed = this.energyContainer.removeEnergy(energyToConsume);
                if (consumed == -energyToConsume) {
                    setActive(true);
                } else {
                    this.hasNotEnoughEnergy = true;
                    setActive(false);
                }
            }
        } else {
            this.hasNotEnoughEnergy = true;
            setActive(false);
        }
    }

    // region structure & rendering

    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        if (sourcePart == null) {
            return Textures.ADVANCED_COMPUTER_CASING; // controller
        }
        return Textures.COMPUTER_CASING; // multiblock parts
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.HPCA_OVERLAY; // 占位：后续可换 quantum_computer 专属贴图
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), this.isActive(),
                this.isWorkingEnabled());
    }

    // endregion

    @Override
    public boolean isActive() {
        return super.isActive() && this.isActive;
    }

    public void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
            markDirty();
            if (getWorld() != null && !getWorld().isRemote) {
                writeCustomData(GregtechDataCodes.WORKABLE_ACTIVE, buf -> buf.writeBoolean(active));
            }
        }
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.isWorkingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            markDirty();
            if (getWorld() != null && !getWorld().isRemote) {
                writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(isWorkingEnabled));
            }
        }
    }

    public double getTemperature() {
        return temperature;
    }

    // region UI

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(true, qcHandler.getAllocatedCWUt() > 0)
                .setWorkingStatusKeys(
                        "gregtech.multiblock.idling",
                        "gregtech.multiblock.idling",
                        "gregtech.multiblock.data_bank.providing")
                .addCustom((manager, syncer) -> {
                    if (!isStructureFormed()) return;

                    // Energy Usage
                    String voltageName = syncer
                            .syncString(GTValues.VNF[GTUtility.getTierByVoltage(qcHandler.getMaxEUt())]);
                    manager.add(KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.quantum_computer.energy",
                            KeyUtil.number(syncer.syncLong(qcHandler.cachedEUt)),
                            KeyUtil.number(syncer.syncLong(qcHandler.getMaxEUt())),
                            IKey.str(voltageName)));

                    // Provided Computation
                    manager.add(KeyUtil.lang("gregtech.multiblock.quantum_computer.computation",
                            syncer.syncInt(qcHandler.cachedCWUt),
                            syncer.syncInt(qcHandler.getMaxCWUt())));

                    // Uncertainty mode（由结构层数决定）
                    manager.add(KeyUtil.lang("gregtech.multiblock.quantum_computer.mode",
                            syncer.syncInt(qcHandler.getMaxUncertaintyMode()),
                            syncer.syncInt(qcHandler.getUncertaintyMode())));
                })
                .addWorkingStatusLine();
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addLowPowerLine(hasNotEnoughEnergy)
                .addCustom((manager, syncer) -> {
                    if (!isStructureFormed()) return;

                    if (syncer.syncDouble(temperature) > 500) {
                        manager.add(KeyUtil.lang(TextFormatting.YELLOW,
                                "gregtech.multiblock.quantum_computer.warning_temperature"));
                    }

                    if (!qcHandler.isResolved()) {
                        manager.add(KeyUtil.lang(TextFormatting.YELLOW,
                                "gregtech.multiblock.quantum_computer.warning_unresolved"));
                    }

                    qcHandler.addWarnings(manager, syncer);
                });
        super.configureWarningText(builder);
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        builder.addCustom((manager, syncer) -> {
            if (!isStructureFormed()) return;

            if (syncer.syncDouble(temperature) > 1000) {
                manager.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.quantum_computer.error_temperature"));
            }
        });
    }

    @Override
    public int getProgressBarCount() {
        return 2;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        IntSyncValue currentCWUtValue = new IntSyncValue(() -> qcHandler.cachedCWUt);
        IntSyncValue maxCWUtValue = new IntSyncValue(qcHandler::getMaxCWUt);
        syncManager.syncValue("current_cwut", currentCWUtValue);
        syncManager.syncValue("max_cwut", maxCWUtValue);
        DoubleSyncValue temperatureValue = new DoubleSyncValue(() -> temperature);
        syncManager.syncValue("temperature", temperatureValue);

        bars.add(barTest -> barTest
                .progress(() -> 1.0 * currentCWUtValue.getIntValue() / maxCWUtValue.getIntValue())
                .texture(GTGuiTextures.PROGRESS_BAR_HPCA_COMPUTATION)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        t.addLine(IKey.lang("gregtech.multiblock.quantum_computer.computation",
                                currentCWUtValue.getIntValue(), maxCWUtValue.getIntValue()));
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));

        bars.add(barTest -> barTest
                .progress(() -> Math.min(1.0, temperatureValue.getDoubleValue() / DAMAGE_TEMPERATURE))
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_HEAT)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        double temp = temperatureValue.getDoubleValue();
                        int degrees = (int) Math.round(temp / 10.0);

                        TextFormatting color;
                        if (temp < 500) {
                            color = TextFormatting.GREEN;
                        } else if (temp < 750) {
                            color = TextFormatting.YELLOW;
                        } else {
                            color = TextFormatting.RED;
                        }

                        t.addLine(IKey.lang("gregtech.multiblock.quantum_computer.temperature", degrees)
                                .style(color));
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    // endregion

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.quantum_computer.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.quantum_computer.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.quantum_computer.tooltip.3"));
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public SoundEvent getSound() {
        return GTSoundEvents.COMPUTATION;
    }

    // region NBT & sync

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isActive", this.isActive);
        data.setBoolean("isWorkingEnabled", this.isWorkingEnabled);
        data.setDouble("temperature", this.temperature);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.isActive = data.getBoolean("isActive");
        this.isWorkingEnabled = data.getBoolean("isWorkingEnabled");
        this.temperature = data.getDouble("temperature");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(this.isActive);
        buf.writeBoolean(this.isWorkingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.isActive = buf.readBoolean();
        this.isWorkingEnabled = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.WORKABLE_ACTIVE) {
            this.isActive = buf.readBoolean();
            scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            this.isWorkingEnabled = buf.readBoolean();
            scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.CACHED_CWU) {
            qcHandler.cachedCWUt = buf.readInt();
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    // endregion

    /**
     * 量子计算机的组件网格处理：统计来源为 Rack 舱内物品，输出机制与 HPCA 的 HPCAGridHandler 同构。
     */
    public static class QCGridHandler {

        /** 每 Rack 的主动水冷能力：抵消 16 产热/ tick。 */
        private static final int COOLING_PER_RACK = 16;
        /** 每 Rack 每 tick 的冷却液消耗（L/t）。 */
        private static final int COOLANT_PER_RACK = 64;

        @Nullable
        private final MetaTileEntityQuantumComputer controller;

        // structure info
        private final List<IQCComponentHatch> racks = new ArrayList<>();
        private final List<IQCUncertaintyHatch> uncertaintyHatches = new ArrayList<>();

        // transaction info
        private int allocatedCWUt;

        // cached gui info
        private long cachedEUt;
        private int cachedCWUt;

        public QCGridHandler(@Nullable MetaTileEntityQuantumComputer controller) {
            this.controller = controller;
        }

        public void onStructureForm(Collection<IQCComponentHatch> racks, Collection<IQCUncertaintyHatch> uncertainty) {
            reset();
            this.racks.addAll(racks);
            this.uncertaintyHatches.addAll(uncertainty);
            // 解析模式由结构层数决定（GT5U eCertainMode 精神），写入舱并重新生成矩阵
            int mode = getMaxUncertaintyMode();
            for (IQCUncertaintyHatch hatch : uncertaintyHatches) {
                hatch.updateUncertaintyMode(mode);
            }
        }

        private void onStructureInvalidate() {
            reset();
        }

        private void reset() {
            clearComputationCache();
            racks.clear();
            uncertaintyHatches.clear();
        }

        public void clearComputationCache() {
            allocatedCWUt = 0;
        }

        public void tick() {
            if (cachedCWUt != allocatedCWUt) {
                cachedCWUt = allocatedCWUt;
                if (controller != null) {
                    controller.writeCustomData(GregtechDataCodes.CACHED_CWU, buf -> buf.writeInt(cachedCWUt));
                }
            }
            cachedEUt = getCurrentEUt();
            if (allocatedCWUt != 0) {
                allocatedCWUt = 0;
            }
        }

        // region 不确定性解析

        /** 当前解析模式（由结构层数决定，玩家不可自选）。 */
        public int getUncertaintyMode() {
            return uncertaintyHatches.isEmpty() ? 0 : uncertaintyHatches.get(0).getUncertaintyMode();
        }

        /**
         * 结构允许的最高解析模式（GT5U eCertainMode = totalLen / 3）。
         * 每层 2 个 Rack：总层数 = rackCount / 2 + 2（top + body + bottom）。
         */
        public int getMaxUncertaintyMode() {
            int totalLength = racks.size() / 2 + 2;
            return Math.max(1, Math.min(5, Math.max(1, totalLength / 3)));
        }

        /** 不确定性是否已解析（矩阵完全平衡），未解析时机器不产算。 */
        public boolean isResolved() {
            return !uncertaintyHatches.isEmpty() && uncertaintyHatches.get(0).isResolved();
        }

        // endregion

        // region 算力 / 热量 / 能耗

        /** 当前最大 CWU/t（全部计算组件之和）。 */
        public int getMaxCWUt() {
            int maxCWUt = 0;
            for (IQCComponentHatch rack : racks) {
                for (var stats : rack.getComputingStats()) {
                    maxCWUt += stats.computation();
                }
            }
            return maxCWUt;
        }

        /** 满负载产热需求（全部计算组件的 heatConstant 之和）。 */
        public int getMaxCoolingDemand() {
            int demand = 0;
            for (IQCComponentHatch rack : racks) {
                for (var stats : rack.getComputingStats()) {
                    demand += stats.heatConstant();
                }
            }
            return demand;
        }

        /** 最大主动水冷能力：每 Rack 一个冷却单元。 */
        public int getMaxCoolingAmount() {
            return racks.size() * COOLING_PER_RACK;
        }

        /** 最大冷却液消耗：每 Rack 每 tick 需 COOLANT_PER_RACK L。 */
        public int getMaxCoolantDemand() {
            return racks.size() * COOLANT_PER_RACK;
        }

        /**
         * 计算本 tick 温度变化并消耗冷却液（主动水冷，PCBCoolant）。
         * 升温 ∝ 实际分配算力比例 × 总产热需求；降温 = 冷却液按实际抽水量线性补偿。
         */
        public double calculateTemperatureChange(IFluidHandler coolantTank) {
            int maxCWUt = Math.max(1, getMaxCWUt());
            int temperatureIncrease = (int) Math.round(1.0 * getMaxCoolingDemand() * allocatedCWUt / maxCWUt);

            // 没有需要抵消的热量就不浪费冷却液
            if (temperatureIncrease <= 0) {
                return temperatureIncrease;
            }

            int maxCoolantDrain = getMaxCoolantDemand();
            FluidStack coolantStack = coolantTank.drain(getCoolantStack(maxCoolantDrain), true);
            if (coolantStack == null) {
                return temperatureIncrease; // 无冷却液，全量升温
            }
            int coolantDrained = coolantStack.amount;
            if (coolantDrained == maxCoolantDrain) {
                // 冷却液需求完全满足
                return temperatureIncrease - getMaxCoolingAmount();
            }
            // 冷却液部分满足，降温按比例
            return temperatureIncrease - getMaxCoolingAmount() * (1.0 * coolantDrained / maxCoolantDrain);
        }

        private FluidStack getCoolantStack(int amount) {
            return new FluidStack(getCoolant(), amount);
        }

        private Fluid getCoolant() {
            return Materials.PCBCoolant.getFluid();
        }

        /** 最大 EU/t（每 Rack 一个 UHV 级计算单元）。 */
        public long getMaxEUt() {
            long maxEUt = 0;
            for (int i = 0; i < racks.size(); i++) {
                maxEUt += GTValues.VA[GTValues.UHV];
            }
            return maxEUt;
        }

        /** 维持耗电（无算力分配时）。 */
        public long getUpkeepEUt() {
            long upkeep = 0;
            for (int i = 0; i < racks.size(); i++) {
                upkeep += GTValues.VA[GTValues.LuV];
            }
            return upkeep;
        }

        /** 当前 EU/t：维持 + 与分配算力成正比的部分（HPCA 线性式）。 */
        public long getCurrentEUt() {
            int maximumCWUt = Math.max(1, getMaxCWUt());
            long maximumEUt = getMaxEUt();
            long upkeepEUt = getUpkeepEUt();

            if (maximumEUt == upkeepEUt) {
                return maximumEUt;
            }

            return upkeepEUt + ((maximumEUt - upkeepEUt) * allocatedCWUt / maximumCWUt);
        }

        // endregion

        /** 分配 CWU/t（每 tick 记账，simulate=true 仅试算）。 */
        public int allocateCWUt(int cwut, boolean simulate) {
            int availableCWUt = getMaxCWUt() - this.allocatedCWUt;
            int toAllocate = Math.min(cwut, Math.max(0, availableCWUt));
            if (!simulate) {
                this.allocatedCWUt += toAllocate;
            }
            return toAllocate;
        }

        /** 本 tick 已分配的 CWU。 */
        public int getAllocatedCWUt() {
            return allocatedCWUt;
        }

        /** 超温惩罚：随机销毁一个 Rack 内的计算物品。 */
        public void destroyRandomComputingComponent() {
            List<IQCComponentHatch> candidates = new ArrayList<>();
            for (IQCComponentHatch rack : racks) {
                if (!rack.getComputingStats().isEmpty()) {
                    candidates.add(rack);
                }
            }
            if (!candidates.isEmpty()) {
                candidates.get(GTValues.RNG.nextInt(candidates.size())).destroyRandomComputingComponent();
            }
        }

        public void addWarnings(KeyManager keyManager, UISyncer syncer) {
            List<IKey> warnings = new ArrayList<>();
            if (syncer.syncBoolean(getMaxCWUt() <= 0)) {
                warnings.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.quantum_computer.warning_no_computation"));
            }
            if (syncer.syncBoolean(getMaxCoolingDemand() > getMaxCoolingAmount())) {
                warnings.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.quantum_computer.warning_low_cooling"));
            }
            if (!warnings.isEmpty()) {
                keyManager.add(KeyUtil.lang(TextFormatting.YELLOW,
                        "gregtech.multiblock.quantum_computer.warning_structure_header"));
                keyManager.addAll(warnings);
            }
        }
    }
}
