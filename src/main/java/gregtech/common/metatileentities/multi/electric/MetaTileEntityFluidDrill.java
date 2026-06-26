package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidDrillLogic;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.SoftReferenceHolder;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.util.tooltips.AbstractTooltipComponent;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class MetaTileEntityFluidDrill extends MultiblockWithDisplayBase
        implements ITieredMetaTileEntity, IWorkable, ProgressBarMultiblock {

    private static final Map<String, SoftReferenceHolder<? extends StructureDefinition<?>>> STRUCTURE_DEFINITIONS =
            new HashMap<>();

    static {
        STRUCTURE_DEFINITIONS.put("mv", TemplatePool.getInstance()
                .registerStructure(structurePoolKey(FluidDrillType.BASIC),
                        () -> buildStructureDefinition(FluidDrillType.BASIC)));
        STRUCTURE_DEFINITIONS.put("hv", TemplatePool.getInstance()
                .registerStructure(structurePoolKey(FluidDrillType.NORMAL),
                        () -> buildStructureDefinition(FluidDrillType.NORMAL)));
        STRUCTURE_DEFINITIONS.put("ev", TemplatePool.getInstance()
                .registerStructure(structurePoolKey(FluidDrillType.ADVANCED),
                        () -> buildStructureDefinition(FluidDrillType.ADVANCED)));
    }

    private final FluidDrillLogic minerLogic;
    private final IFluidDrillType type;
    protected IMultipleTankHandler inputFluidInventory;
    protected IMultipleTankHandler outputFluidInventory;
    protected IEnergyContainer energyContainer;

    public MetaTileEntityFluidDrill(ResourceLocation metaTileEntityId, IFluidDrillType type) {
        super(metaTileEntityId);
        this.type = type;
        this.minerLogic = new FluidDrillLogic(this);
    }

    public static void registerFluidDrillType(String key, Supplier<BlockPatternTemplate> templateSupplier) {
        STRUCTURE_DEFINITIONS.put(key, TemplatePool.getInstance()
                .registerStructure(key, () -> StructureDefinition.fromTemplate(templateSupplier.get())));
    }

    public static BlockPatternTemplate buildTemplate(IFluidDrillType type) {
        return primaryTemplate(pooledStructureDefinition(type), type.getName());
    }

    private static StructureDefinition<?> pooledStructureDefinition(IFluidDrillType type) {
        SoftReferenceHolder<? extends StructureDefinition<?>> definition = TemplatePool.getInstance()
                .registerStructure(structurePoolKey(type), () -> buildStructureDefinition(type));
        return definition.get();
    }

    private static String structurePoolKey(IFluidDrillType type) {
        return "gregtech:fluid_drilling_rig." + type.getName();
    }

    private static StructureDefinition<?> buildStructureDefinition(IFluidDrillType type) {
        return DeclarativePatternBuilder.start()
                .aisle("XXX", "#F#", "#F#", "#F#", "###", "###", "###")
                .aisle("XXX", "FCF", "FCF", "FCF", "#F#", "#F#", "#F#")
                .aisle("XSX", "#F#", "#F#", "#F#", "###", "###", "###")
                .self('S', MetaTileEntityFluidDrill.class)
                .block('C', type.getCasingState())
                .frames('F', type.getFrameMaterial())
                .any('#')
                .casing('X', type.getCasingState())
                .energyInput(1, 3)
                .fluidOutput(1)
                .buildStructureDefinition();
    }

    private static BlockPatternTemplate primaryTemplate(StructureDefinition<?> definition, String key) {
        BlockPatternTemplate template = definition.getPrimaryTemplate();
        if (template == null) {
            throw new IllegalStateException("Fluid drill type '" + key + "' is not a single-piece structure");
        }
        return template;
    }

    private static @NotNull String getDepletionLang(IntSyncValue operationsValue) {
        int percent = (int) Math.round(100.0 * operationsValue.getIntValue() /
                BedrockFluidVeinHandler.MAXIMUM_VEIN_OPERATIONS);
        if (percent > 40) {
            return TextFormatting.GREEN + IKey
                    .lang("gregtech.multiblock.fluid_rig.vein_depletion.high", percent).get();
        } else if (percent > 10) {
            return TextFormatting.YELLOW + IKey
                    .lang("gregtech.multiblock.fluid_rig.vein_depletion.medium", percent).get();
        } else {
            return TextFormatting.RED + IKey
                    .lang("gregtech.multiblock.fluid_rig.vein_depletion.low", percent).get();
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityFluidDrill(metaTileEntityId, type);
    }

    protected void initializeAbilities() {
        this.inputFluidInventory = new FluidTankList(true, getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.outputFluidInventory = new FluidTankList(true, getAbilities(MultiblockAbility.EXPORT_FLUIDS));
        this.energyContainer = new EnergyContainerList(getAbilities(MultiblockAbility.INPUT_ENERGY));
    }

    private void resetTileAbilities() {
        this.inputFluidInventory = new FluidTankList(true);
        this.outputFluidInventory = new FluidTankList(true);
        this.energyContainer = new EnergyContainerList(Lists.newArrayList());
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        initializeAbilities();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        resetTileAbilities();
    }

    @Override
    protected void updateFormedValid() {
        this.minerLogic.performDrilling();
        if (!getWorld().isRemote && this.minerLogic.wasActiveAndNeedsUpdate()) {
            this.minerLogic.setWasActiveAndNeedsUpdate(false);
            this.minerLogic.setActive(false);
        }
    }

    @NotNull
    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        SoftReferenceHolder<? extends StructureDefinition<?>> definition = STRUCTURE_DEFINITIONS.get(type.getName());
        if (definition == null) {
            throw new IllegalStateException("Unknown fluid drill type: " + type.getName());
        }
        return definition.get();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return type.getCasingRenderer();
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(minerLogic.isWorkingEnabled(), minerLogic.isActive())
                .setWorkingStatusKeys(
                        "gregtech.multiblock.idling",
                        "gregtech.multiblock.work_paused",
                        "gregtech.multiblock.miner.drilling")
                .addEnergyUsageLine(energyContainer)
                .addCustom((keyManager, syncer) -> {
                    if (!isStructureFormed()) return;

                    // Fluid name
                    Fluid drilledFluid = syncer.syncFluid(minerLogic.getDrilledFluid());
                    if (drilledFluid == null) {
                        IKey noFluid = KeyUtil.lang(TextFormatting.RED,
                                "gregtech.multiblock.fluid_rig.no_fluid_in_area");

                        keyManager.add(KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.fluid_rig.drilled_fluid",
                                noFluid));
                        return;
                    }

                    IKey fluidInfo = KeyUtil.fluid(drilledFluid).style(TextFormatting.GREEN);

                    keyManager.add(KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.fluid_rig.drilled_fluid",
                            fluidInfo));

                    int fluidProduce = syncer.syncInt(minerLogic.getFluidToProduce());

                    IKey amountInfo = KeyUtil.number(TextFormatting.BLUE,
                            fluidProduce * 20L / FluidDrillLogic.MAX_PROGRESS, " L/s");

                    keyManager.add(KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.fluid_rig.fluid_amount",
                            amountInfo));
                })
                .addProgressLine(minerLogic.getProgressTime(), FluidDrillLogic.MAX_PROGRESS)
                .addWorkingStatusLine();
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addLowPowerLine(() -> isStructureFormed() && !drainEnergy(true))
                .addCustom((list, syncer) -> {
                    if (isStructureFormed() && syncer.syncBoolean(minerLogic.isInventoryFull())) {
                        list.add(KeyUtil.lang(TextFormatting.YELLOW, "gregtech.machine.miner.invfull"));
                    }
                });
        super.configureWarningText(builder);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        TooltipBuilder.create().add(new DrillInformation(type.getTier())).build(this, tooltip);
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public int getTier() {
        return this.type.getTier();
    }

    public int getRigMultiplier() {
        return type.getRigMultiplier();
    }

    public int getDepletionChance() {
        return type.getDepletionChance();
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.FLUID_RIG_OVERLAY;
    }

    @Override
    public IBlockState getCasingBlock() {
        return type.getCasingState();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                this.minerLogic.isActive(), this.minerLogic.isWorkingEnabled());
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.minerLogic.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isActivationAllowed) {
        this.minerLogic.setWorkingEnabled(isActivationAllowed);
    }

    public boolean fillTanks(FluidStack stack, boolean simulate) {
        return GTTransferUtils.addFluidsToFluidHandler(outputFluidInventory, simulate,
                Collections.singletonList(stack));
    }

    public int getEnergyTier() {
        if (energyContainer == null) return this.type.getTier();
        return Math.min(this.type.getTier() + 1,
                Math.max(this.type.getTier(), GTUtility.getFloorTierByVoltage(energyContainer.getInputVoltage())));
    }

    public long getEnergyInputPerSecond() {
        return energyContainer.getInputPerSec();
    }

    public boolean drainEnergy(boolean simulate) {
        long energyToDrain = GTValues.VA[getEnergyTier()];
        long resultEnergy = energyContainer.getEnergyStored() - energyToDrain;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.changeEnergy(-energyToDrain);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        return this.minerLogic.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.minerLogic.readFromNBT(data);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        this.minerLogic.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.minerLogic.receiveInitialSyncData(buf);
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        this.minerLogic.receiveCustomData(dataId, buf);
    }

    @Override
    public int getProgress() {
        return minerLogic.getProgressTime();
    }

    @Override
    public int getMaxProgress() {
        return FluidDrillLogic.MAX_PROGRESS;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_WORKABLE)
            return GregtechTileCapabilities.CAPABILITY_WORKABLE.cast(this);
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE)
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        return super.getCapability(capability, side);
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    public int getProgressBarCount() {
        // only show for T2/3 fluid rigs
        return type.getTier() > GTValues.MV ? 1 : 0;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        IntSyncValue operationsValue = new IntSyncValue(() -> BedrockFluidVeinHandler.getOperationsRemaining(getWorld(),
                minerLogic.getChunkX(), minerLogic.getChunkZ()));
        syncManager.syncValue("operations_remaining", operationsValue);

        bars.add(bar -> bar
                .progress(() -> operationsValue.getIntValue() * 1.0 / BedrockFluidVeinHandler.MAXIMUM_VEIN_OPERATIONS)
                .texture(GTGuiTextures.PROGRESS_BAR_FLUID_RIG_DEPLETION)
                .tooltipBuilder(t -> {
                    if (!isStructureFormed()) {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                        return;
                    }

                    if (operationsValue.getIntValue() == 0) {
                        t.addLine(IKey.lang("gregtech.multiblock.fluid_rig.vein_depleted"));
                        return;
                    }

                    t.addLine(KeyUtil.string(() -> getDepletionLang(operationsValue)));
                }));
    }

    public class DrillInformation extends AbstractTooltipComponent {

        private final int tier;

        public DrillInformation(int tier) {this.tier = tier;}

        @Override
        public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
            tooltip.add(I18n.format("gregtech.machine.fluid_drilling_rig.description"));
            if (getDepletionChance() > 0) {
                tooltip.add(I18n.format("gregtech.machine.fluid_drilling_rig.depletion",
                        TextFormattingUtil.formatNumbers(100.0 / getDepletionChance())));
            } else {
                tooltip.add(I18n.format("gregtech.machine.fluid_drilling_rig.depletion", 0));
            }
            tooltip.add(I18n.format("gregtech.universal.tooltip.energy_tier_range", GTValues.VNF[this.tier],
                    GTValues.VNF[this.tier + 1]));
            tooltip.add(I18n.format("gregtech.machine.fluid_drilling_rig.production", getRigMultiplier(),
                    TextFormattingUtil.formatNumbers(getRigMultiplier() * 1.5)));
            if (tier > GTValues.MV) {
                tooltip.add(I18n.format("gregtech.machine.fluid_drilling_rig.shows_depletion"));
            }
        }
    }
}
