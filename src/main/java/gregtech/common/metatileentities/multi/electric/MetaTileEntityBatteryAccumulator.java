package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.category.ICategoryOverride;
import gregtech.api.unification.material.Materials;

import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Battery Accumulator multiblock controller.
 *
 * <p>This multiblock charges and discharges electrolyte fluids used in the
 * disposable battery crafting chain. It has two working modes:
 * <ul>
 *   <li><b>CHARGE</b> — consumes EU from energy input hatches to convert
 *       uncharged electrolyte fluid into charged electrolyte fluid</li>
 *   <li><b>DISCHARGE</b> — consumes charged electrolyte fluid to output EU
 *       through dynamo (energy output) hatches, producing uncharged fluid</li>
 * </ul>
 *
 * <p>Both modes have a default 10% energy loss, resulting in 81% round-trip
 * efficiency. The loss rate is configurable.
 */
public class MetaTileEntityBatteryAccumulator extends MultiblockWithDisplayBase
        implements IControllable, ICategoryOverride, ProgressBarMultiblock {

    // -----------------------------------------------------------------
    // Working mode
    // -----------------------------------------------------------------

    /** Working modes for the Battery Accumulator. */
    public enum WorkingMode {
        CHARGE,
        DISCHARGE;

        /** Next mode in the cycle. */
        public WorkingMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // -----------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------

    /** Default energy loss ratio (0.10 = 10%). */
    public static final double DEFAULT_LOSS_RATIO = 0.10;

    /** NBT keys. */
    private static final String NBT_WORKING_MODE = "WorkingMode";
    private static final String NBT_IS_WORKING_ENABLED = "IsWorkingEnabled";
    private static final String NBT_CHARGE_PROGRESS = "ChargeProgress";

    

    // -----------------------------------------------------------------
    // Instance fields
    // -----------------------------------------------------------------

    /** Current working mode (charge or discharge). */
    private WorkingMode workingMode = WorkingMode.CHARGE;

    /** Whether the machine is allowed to work. */
    private boolean isWorkingEnabled = true;

    /** Whether the machine is currently active (processing fluids). */
    private boolean isActive = false;

    /** Accumulated EU progress toward converting 1 mB of fluid. */
    private long chargeProgress = 0;

    /** Total EU needed per mB for the current electrolyte (including loss). */
    private long currentEuNeededPerMb = 0;

    /** Energy input hatch list (used in CHARGE mode). */
    private EnergyContainerList inputEnergyHatches;

    /** Energy output hatch list (used in DISCHARGE mode). */
    private EnergyContainerList outputEnergyHatches;

    /** Fluid input tanks. */
    private FluidTankList inputFluidTanks;

    /** Fluid output tanks. */
    private FluidTankList outputFluidTanks;

    /** Energy loss ratio (0.0 to 1.0). */
    private final double lossRatio;

    // -----------------------------------------------------------------
    // Structure template
    //
    // Real-world inspired: battery energy storage system
    //
    // Layout (5×5 cross-section, 4 layers fixed):
    //
    //   Layer 1 (base / electrical panel):
    //     XXXXX      X = lead block (hatches go here)
    //     XXXXX
    //     XXXXX
    //     XXXXX
    //     XXSXX      S = controller
    //
    //   Layer 2 (battery module rack):
    //     GGGGG      G = glass (fire isolation window)
    //     GBFFG      B = lead frame (battery rack pillar)
    //     GBFFG      F = lead frame (battery module shelf)
    //     GBFFG
    //     GGGGG
    //
    //   Layer 3 (battery module rack):
    //     GGGGG      Same as layer 2
    //     GBFFG
    //     GBFFG
    //     GBFFG
    //     GGGGG
    //
    //   Layer 4 (top / thermal management):
    //     XXXXX      X = lead block
    //     XEEEX     E = steel solid casing (heat sink / ventilation)
    //     XEEEX
    //     XEEEX
    //     XXXXX
    //
    // -----------------------------------------------------------------

    private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
            .register("gregtech:battery_accumulator", () ->
                    DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                            // Base layer — electrical panel and controller
                            .aisle("XXSXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
                            // Battery module layer 1
                            .aisle("GGGGG", "GBFFG", "GBFFG", "GBFFG", "GGGGG")
                            // Battery module layer 2
                            .aisle("GGGGG", "GBFFG", "GBFFG", "GBFFG", "GGGGG")
                            // Top layer — thermal management / heat sinks
                            .aisle("XXXXX", "XEEEX", "XEEEX", "XEEEX", "XXXXX")
                            .where('S', selfPredicate(MetaTileEntityBatteryAccumulator.class))
                            .where('G', states(getGlassState()))
                            .where('B', frames(Materials.Lead))
                            .where('F', frames(Materials.Lead))
                            .where('E', states(getHeatSinkState()))
                            .casing('X', CasingDefinition.simple(getCasingState()))
                                    .maintenance()
                                    .energyInput(1, 4)
                                    .energyOutput(1, 4)
                                    .fluidInput(1, 4)
                                    .fluidOutput(1, 4)
                            .buildTemplate()
            );

    // -----------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------

    public MetaTileEntityBatteryAccumulator(ResourceLocation metaTileEntityId) {
        this(metaTileEntityId, DEFAULT_LOSS_RATIO);
    }

    public MetaTileEntityBatteryAccumulator(ResourceLocation metaTileEntityId, double lossRatio) {
        super(metaTileEntityId);
        this.lossRatio = lossRatio;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityBatteryAccumulator(metaTileEntityId, lossRatio);
    }

    // -----------------------------------------------------------------
    // Structure
    // -----------------------------------------------------------------

    @NotNull
    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    protected static IBlockState getCasingState() {
        return MetaBlocks.COMPRESSED.get(Materials.Lead).getBlock(Materials.Lead);
    }

    /** Glass — fire isolation windows between battery modules. */
    protected static IBlockState getGlassState() {
        return Blocks.GLASS.getDefaultState();
    }

    /** Steel solid casing — heat sink / ventilation blocks on top. */
    protected static IBlockState getHeatSinkState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.SOLID_STEEL_CASING;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.POWER_SUBSTATION_OVERLAY;
    }

    // -----------------------------------------------------------------
    // Structure formation / invalidation
    // -----------------------------------------------------------------

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);

        // Collect energy input hatches
        List<IEnergyContainer> inputs = new ArrayList<>();
        inputs.addAll(getAbilities(MultiblockAbility.INPUT_ENERGY));
        inputs.addAll(getAbilities(MultiblockAbility.SUBSTATION_INPUT_ENERGY));
        this.inputEnergyHatches = new EnergyContainerList(inputs);

        // Collect energy output hatches (dynamo hatches)
        List<IEnergyContainer> outputs = new ArrayList<>();
        outputs.addAll(getAbilities(MultiblockAbility.OUTPUT_ENERGY));
        outputs.addAll(getAbilities(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY));
        this.outputEnergyHatches = new EnergyContainerList(outputs);

        // Collect fluid tanks
        this.inputFluidTanks = new FluidTankList(false,
                getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.outputFluidTanks = new FluidTankList(false,
                getAbilities(MultiblockAbility.EXPORT_FLUIDS));
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.inputEnergyHatches = null;
        this.outputEnergyHatches = null;
        this.inputFluidTanks = null;
        this.outputFluidTanks = null;
        this.isActive = false;
        this.currentEuNeededPerMb = 0;
    }

    // -----------------------------------------------------------------
    // Per-tick logic
    // -----------------------------------------------------------------

    @Override
    protected void updateFormedValid() {
        if (getWorld().isRemote || !isWorkingEnabled) return;

        boolean wasActive = this.isActive;
        this.isActive = false;

        switch (workingMode) {
            case CHARGE -> tickChargeMode();
            case DISCHARGE -> tickDischargeMode();
        }

        // Update active state for rendering
        if (wasActive != this.isActive) {
            setActive(this.isActive);
        }
    }

    /**
     * Charge mode: consume EU from energy input hatches to convert
     * uncharged electrolyte → charged electrolyte.
     */
    private void tickChargeMode() {
        if (inputEnergyHatches == null || inputFluidTanks == null || outputFluidTanks == null) return;

        currentEuNeededPerMb = 0;

        // Find a valid uncharged electrolyte in the input tanks
        BatteryAccumulatorFluidMapping mapping = null;
        int availableMb = 0;
        for (IFluidTank tank : inputFluidTanks.getFluidTanks()) {
            FluidStack drainSim = tank.drain(1, false);
            if (drainSim != null && drainSim.amount > 0) {
                BatteryAccumulatorFluidMapping found = BatteryAccumulatorFluidMapping.fromFluidStack(drainSim);
                if (found != null && found.getChargedFluidStack(1) != null) {
                    // Verify this is an uncharged fluid (not a charged one)
                    if (drainSim.getFluid() == found.getUnchargedFluid().getFluid()) {
                        mapping = found;
                    }
                }
            }
        }

        if (mapping == null) return;

        // Calculate EU needed to charge 1 mB of this electrolyte
        long euPerMb = mapping.getEuPerBucket() / 1000;
        if (euPerMb <= 0) return;

        // Apply loss: need more EU to charge (loss is taken from input)
        long euNeededPerMb = (long) (euPerMb / (1.0 - lossRatio));
        currentEuNeededPerMb = euNeededPerMb;

        // Count total available uncharged fluid across all input tanks
        for (IFluidTank tank : inputFluidTanks.getFluidTanks()) {
            FluidStack fluid = tank.getFluid();
            if (fluid != null && fluid.getFluid() == mapping.getUnchargedFluid().getFluid()) {
                availableMb += fluid.amount;
            }
        }

        // Draw energy from input hatches — limited by voltage × amperage per tick
        long maxInputPerTick = inputEnergyHatches.getInputVoltage() * inputEnergyHatches.getInputAmperage();
        long availableEnergy = inputEnergyHatches.getEnergyStored();
        if (availableEnergy <= 0 && chargeProgress < euNeededPerMb) return;

        long maxEnergyByAvailableMb = (long) availableMb * euNeededPerMb;
        long maxEnergyToDraw = Math.min(
                Math.min(maxEnergyByAvailableMb - chargeProgress, maxInputPerTick),
                Long.MAX_VALUE - chargeProgress);
        long energyToDraw = Math.min(availableEnergy, Math.max(0, maxEnergyToDraw));

        if (energyToDraw > 0) {
            inputEnergyHatches.changeEnergy(-energyToDraw);
            chargeProgress += energyToDraw;
        }

        this.isActive = true;

        // Convert as many mB as we have progress for
        while (chargeProgress >= euNeededPerMb) {
            if (!convertChargeFluid(mapping, 1)) break;
            chargeProgress -= euNeededPerMb;
        }
    }

    /**
     * Discharge mode: consume charged electrolyte to output EU
     * through dynamo hatches, producing uncharged electrolyte.
     */
    private void tickDischargeMode() {
        if (outputEnergyHatches == null || inputFluidTanks == null || outputFluidTanks == null) return;

        // Find a valid charged electrolyte in the input tanks
        BatteryAccumulatorFluidMapping mapping = null;
        for (IFluidTank tank : inputFluidTanks.getFluidTanks()) {
            FluidStack drainSim = tank.drain(1, false);
            if (drainSim != null && drainSim.amount > 0) {
                BatteryAccumulatorFluidMapping found = BatteryAccumulatorFluidMapping.fromFluidStack(drainSim);
                if (found != null && found.getUnchargedFluidStack(1) != null) {
                    // Verify this is a charged fluid
                    if (drainSim.getFluid() == found.getChargedFluid().getFluid()) {
                        mapping = found;
                        break;
                    }
                }
            }
        }

        if (mapping == null) return;

        // Calculate EU released per 1 mB of charged fluid (with loss)
        long euPerMb = mapping.getEuPerBucket() / 1000;
        long euReleased = (long) (euPerMb * (1.0 - lossRatio));
        if (euReleased <= 0) return;

        // Limit output by voltage × amperage per tick and available space
        long maxOutputPerTick = outputEnergyHatches.getOutputVoltage() * outputEnergyHatches.getOutputAmperage();
        long capacityAvailable = outputEnergyHatches.getEnergyCapacity() - outputEnergyHatches.getEnergyStored();
        long maxOutputEnergyThisTick = Math.min(maxOutputPerTick, capacityAvailable);
        if (maxOutputEnergyThisTick <= 0) return;

        // Determine how many mB we can process this tick
        int mbToProcess = (int) Math.min((long) Integer.MAX_VALUE, maxOutputEnergyThisTick / euReleased);
        if (mbToProcess <= 0) return;

        // Try to drain that many mB from input
        FluidStack drained = inputFluidTanks.drain(mapping.getChargedFluidStack(mbToProcess), true);
        if (drained == null || drained.amount <= 0) return;

        int actualProcessed = drained.amount;

        // Output uncharged fluid
        FluidStack unchargedOutput = mapping.getUnchargedFluidStack(actualProcessed);
        if (unchargedOutput != null) {
            int filled = outputFluidTanks.fill(unchargedOutput, true);
            // If we can't output the uncharged fluid, we still discharge but void the excess
            // This prevents the machine from getting stuck
        }

        // Output EU to dynamo hatches (with loss)
        long totalEuOutput = euReleased * actualProcessed;
        outputEnergyHatches.changeEnergy(totalEuOutput);
        this.isActive = true;
    }

    /**
     * Converts 1 mB of uncharged electrolyte to charged electrolyte.
     *
     * @return true if conversion succeeded
     */
    private boolean convertChargeFluid(BatteryAccumulatorFluidMapping mapping, int amountMb) {
        // Drain uncharged fluid from input
        FluidStack unchargedDrain = mapping.getUnchargedFluidStack(amountMb);
        FluidStack drained = inputFluidTanks.drain(unchargedDrain, true);
        if (drained == null || drained.amount < amountMb) return false;

        // Produce charged fluid to output
        FluidStack chargedOutput = mapping.getChargedFluidStack(amountMb);
        if (chargedOutput == null) return false;

        int filled = outputFluidTanks.fill(chargedOutput, false);
        if (filled < amountMb) return false;

        outputFluidTanks.fill(chargedOutput, true);
        return true;
    }

    // -----------------------------------------------------------------
    // Active state
    // -----------------------------------------------------------------

    private void setActive(boolean active) {
        this.isActive = active;
        markDirty();
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKABLE_ACTIVE, buf -> buf.writeBoolean(active));
        }
    }

    @Override
    public boolean isActive() {
        return super.isActive() && this.isActive;
    }

    // -----------------------------------------------------------------
    // IControllable
    // -----------------------------------------------------------------

    @Override
    public boolean isWorkingEnabled() {
        return isWorkingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        this.isWorkingEnabled = isWorkingAllowed;
        markDirty();
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(isWorkingEnabled));
        }
    }

    // -----------------------------------------------------------------
    // Working mode switching
    // -----------------------------------------------------------------

    public WorkingMode getWorkingMode() {
        return workingMode;
    }

    public void setWorkingMode(WorkingMode mode) {
        this.workingMode = mode;
        this.chargeProgress = 0;
        markDirty();
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_MODE, buf -> buf.writeByte(mode.ordinal()));
        }
    }

    public void cycleWorkingMode() {
        setWorkingMode(workingMode.next());
    }

    // -----------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger(NBT_WORKING_MODE, workingMode.ordinal());
        data.setBoolean(NBT_IS_WORKING_ENABLED, isWorkingEnabled);
        data.setLong(NBT_CHARGE_PROGRESS, chargeProgress);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(NBT_WORKING_MODE)) {
            int modeIndex = data.getInteger(NBT_WORKING_MODE);
            if (modeIndex >= 0 && modeIndex < WorkingMode.values().length) {
                workingMode = WorkingMode.values()[modeIndex];
            }
        }
        isWorkingEnabled = data.getBoolean(NBT_IS_WORKING_ENABLED);
        chargeProgress = data.getLong(NBT_CHARGE_PROGRESS);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(workingMode.ordinal());
        buf.writeBoolean(isWorkingEnabled);
        buf.writeBoolean(isActive);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        int modeIndex = buf.readByte();
        if (modeIndex >= 0 && modeIndex < WorkingMode.values().length) {
            workingMode = WorkingMode.values()[modeIndex];
        }
        isWorkingEnabled = buf.readBoolean();
        isActive = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.WORKABLE_ACTIVE) {
            isActive = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            isWorkingEnabled = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.WORKING_MODE) {
            int modeIndex = buf.readByte();
            if (modeIndex >= 0 && modeIndex < WorkingMode.values().length) {
                workingMode = WorkingMode.values()[modeIndex];
            }
        }
    }

    // -----------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation,
                                     IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                this.isActive(), this.isWorkingEnabled());
    }

    // -----------------------------------------------------------------
    // ProgressBarMultiblock
    // -----------------------------------------------------------------

    private long getChargeProgress() {
        return chargeProgress;
    }

    private long getEuNeededPerMb() {
        return currentEuNeededPerMb;
    }

    @Override
    public int getProgressBarCount() {
        return 1;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        LongSyncValue chargeProgressValue = new LongSyncValue(this::getChargeProgress);
        LongSyncValue euNeededValue = new LongSyncValue(this::getEuNeededPerMb);
        syncManager.syncValue("charge_progress", chargeProgressValue);
        syncManager.syncValue("eu_needed", euNeededValue);

        bars.add(b -> b
                .progress(() -> {
                    long needed = euNeededValue.getValue();
                    if (needed <= 0) return 0;
                    return Math.min(1.0, (double) chargeProgressValue.getValue() / needed);
                })
                .texture(GTGuiTextures.PROGRESS_BAR_FLUID_RIG_DEPLETION)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        long progress = chargeProgressValue.getValue();
                        long needed = euNeededValue.getValue();
                        if (needed > 0) {
                            t.addLine(IKey.lang(
                                    "gregtech.machine.battery_accumulator.charge_progress",
                                    TextFormattingUtil.formatNumbers(progress),
                                    TextFormattingUtil.formatNumbers(needed)));
                        } else if (workingMode == WorkingMode.CHARGE) {
                            t.addLine(IKey.lang(
                                    "gregtech.machine.battery_accumulator.charge_progress_idle"));
                        } else {
                            t.addLine(IKey.lang(
                                    "gregtech.machine.battery_accumulator.charge_progress_discharge"));
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    // -----------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.structureFormed(isStructureFormed());
        builder.setWorkingStatus(isWorkingEnabled(), isActive());

        builder.addCustom((richText, syncer) -> {
            if (!isStructureFormed()) return;

            // Working mode display
            String modeKey = workingMode == WorkingMode.CHARGE
                    ? "gregtech.machine.battery_accumulator.mode_charge"
                    : "gregtech.machine.battery_accumulator.mode_discharge";
            TextFormatting modeColor = workingMode == WorkingMode.CHARGE
                    ? TextFormatting.GREEN : TextFormatting.AQUA;
            richText.add(KeyUtil.lang(modeColor, modeKey));

            // Loss rate display
            richText.add(KeyUtil.lang(TextFormatting.YELLOW,
                    "gregtech.machine.battery_accumulator.loss_rate",
                    String.format("%.0f%%", lossRatio * 100)));

            // Mode-specific info
            if (workingMode == WorkingMode.CHARGE && isActive()) {
                richText.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.machine.battery_accumulator.charging"));
            } else if (workingMode == WorkingMode.DISCHARGE && isActive()) {
                richText.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.machine.battery_accumulator.discharging"));
            }
        });

        builder.addWorkingStatusLine();
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return super.createUIFactory()
                .createFlexButton((guiData, syncManager) -> {
                    IntSyncValue modeIndex = new IntSyncValue(
                            () -> workingMode.ordinal(),
                            idx -> {
                                if (idx >= 0 && idx < WorkingMode.values().length) {
                                    setWorkingMode(WorkingMode.values()[idx]);
                                }
                            });
                    syncManager.syncValue("accumulator_mode", modeIndex);

                    return new CycleButtonWidget()
                            .overlay(GTGuiTextures.BUTTON_MULTI_MAP)
                            .background(GTGuiTextures.BUTTON)
                            .disableHoverBackground()
                            .value(modeIndex)
                            .length(WorkingMode.values().length)
                            .tooltipBuilder(t -> {
                                WorkingMode current = WorkingMode.values()[modeIndex.getIntValue()];
                                String key = current == WorkingMode.CHARGE
                                        ? "gregtech.machine.battery_accumulator.mode_charge"
                                        : "gregtech.machine.battery_accumulator.mode_discharge";
                                t.addLine(IKey.lang("gregtech.machine.battery_accumulator.mode_switch",
                                        IKey.lang(key)));
                            });
                });
    }

    // -----------------------------------------------------------------
    // Tooltip
    // -----------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world,
                               @NotNull List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.battery_accumulator.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.battery_accumulator.tooltip.2",
                String.format("%.0f%%", lossRatio * 100)));
        tooltip.add(I18n.format("gregtech.machine.battery_accumulator.tooltip.3",
                String.format("%.0f%%", (1.0 - lossRatio) * (1.0 - lossRatio) * 100)));
    }

    // -----------------------------------------------------------------
    // ICategoryOverride — JEI recipe map association
    // -----------------------------------------------------------------

    @Override
    public boolean shouldOverride() {
        return true;
    }

    @Override
    public boolean shouldReplace() {
        // Do not replace; allow the multiblock info category to also show
        return false;
    }

    @NotNull
    @Override
    public RecipeMap<?> @NotNull [] getJEIRecipeMapCategoryOverrides() {
        return new RecipeMap<?>[] { RecipeMaps.BATTERY_ACCUMULATOR_RECIPES };
    }
}
