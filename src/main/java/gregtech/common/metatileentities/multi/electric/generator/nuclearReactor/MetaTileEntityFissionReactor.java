package gregtech.common.metatileentities.multi.electric.generator.nuclearReactor;

import gregtech.SCValues;
import gregtech.api.capability.ICoolantHandler;
import gregtech.api.capability.IFuelRodHandler;
import gregtech.api.capability.IMaintenanceHatch;
import gregtech.api.cover.ICustomEnergyCover;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IFissionReactorHatch;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.IProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.SCMultiblockAbility;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.nuclear.fission.CoolantRegistry;
import gregtech.api.nuclear.fission.FissionFuelRegistry;
import gregtech.api.nuclear.fission.FissionReactor;
import gregtech.api.nuclear.fission.ICoolantStats;
import gregtech.api.nuclear.fission.IModeratorStats;
import gregtech.api.nuclear.fission.ModeratorRegistry;
import gregtech.api.nuclear.fission.components.ControlRod;
import gregtech.api.nuclear.fission.components.CoolantChannel;
import gregtech.api.nuclear.fission.components.FuelRod;
import gregtech.api.nuclear.fission.components.Moderator;
import gregtech.api.pattern.CountLimitError;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.PatternStringError;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureHintResult;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.IFissionFuelStats;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.SCUtility;
import gregtech.api.util.TextComponentUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.textures.SCTextures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockFissionCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityControlRodPort;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityModeratorPort;
import gregtech.common.mui.widget.ScrollableTextWidget;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenCustomHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MetaTileEntityFissionReactor extends MultiblockWithDisplayBase
        implements IDataInfoProvider, IProgressBarMultiblock, ICustomEnergyCover {

    private static final int MIN_REACTOR_DIAMETER = 5;
    private static final int MAX_REACTOR_DIAMETER = 15;
    private static final int MIN_REACTOR_EXTENSION = 1;
    private static final int MAX_REACTOR_EXTENSION = 7;
    private static final int TOOLTIP_LINE_COUNT = 29;
    // These textures have always been packaged under the gregtech namespace for the legacy GUI.
    private static final UITexture FISSION_HEAT_BAR = GTGuiTextures.fullImage(
            "textures/gui/progress_bar/progress_bar_fission_heat.png", null);
    private static final UITexture FISSION_PRESSURE_BAR = GTGuiTextures.fullImage(
            "textures/gui/progress_bar/progress_bar_fission_pressure.png", null);
    private static final UITexture FISSION_ENERGY_BAR = GTGuiTextures.fullImage(
            "textures/gui/progress_bar/progress_bar_fission_energy.png", null);
    private static final UITexture FISSION_CONTROL_ROD_BUTTON = GTGuiTextures.fullImage(
            "textures/gui/widget/button_control_rod_helper.png", null);
    private static final UITexture FISSION_SLIDER_BACKGROUND = GTGuiTextures.fullImage(
            "textures/gui/widget/dark_slider_background.png", null);
    private static final UITexture FISSION_SLIDER_ICON = GTGuiTextures.fullImage(
            "textures/gui/widget/dark_slider.png", null);

    @Override
    public boolean usesMui2() {
        return true;
    }

    private FissionReactor fissionReactor;
    private int diameter;
    private int heightTop;
    private int heightBottom;
    private int height;
    // Used for maintenance mechanics
    private boolean isFlowingCorrectly = true;
    private LockingState lockingState = LockingState.UNLOCKED;

    private double kEff;

    @Getter
    private double totalDepletion;
    @Getter
    private double controlRodInsertion;
    @Getter
    private double temperature;
    @Getter
    private double maxTemperature;
    @Getter
    private double pressure;
    @Getter
    private double maxPressure;
    @Getter
    private double power;
    @Getter
    private double maxPower;

    private NBTTagCompound transientData;

    public MetaTileEntityFissionReactor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @NotNull
    protected static IBlockState getFuelChannelState() {
        return MetaBlocks.FISSION_CASING.getState(BlockFissionCasing.FissionCasingType.FUEL_CHANNEL);
    }

    protected static IStructureElement<?> moderatorElement() {
        return Elements.blockPredicate(state -> ModeratorRegistry.getModerator(state) != null);
    }

    @NotNull
    protected static IBlockState getControlRodChannelState() {
        return MetaBlocks.FISSION_CASING.getState(BlockFissionCasing.FissionCasingType.CONTROL_ROD_CHANNEL);
    }

    @NotNull
    protected static IBlockState getCoolantChannelState() {
        return MetaBlocks.FISSION_CASING.getState(BlockFissionCasing.FissionCasingType.COOLANT_CHANNEL);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityFissionReactor(metaTileEntityId);
    }

    /** Preserve the legacy into-structure orientation of this template. */
    @Override
    public EnumFacing getFrontFacingForStructure() {
        return getFrontFacing().getOpposite();
    }

    @NotNull
    public static IBlockState getCasingState() {
        return MetaBlocks.FISSION_CASING.getState(BlockFissionCasing.FissionCasingType.REACTOR_VESSEL);
    }

    @Override
    public double getFillPercentage(int index) {
        if (index == 0) {
            return this.temperature / this.maxTemperature;
        } else if (index == 1) {
            return this.pressure / this.maxPressure;
        } else {
            if (this.maxPower / this.power > Math.exp(9)) {
                return 0;
            }
            return (Math.log(this.power / this.maxPower) + 9) / 9;
        }
    }

    /**
     * Public for OC integration, use it if you want ig
     */
    public void toggleControlRodRegulation(boolean enabled) {
        if (getWorld() == null || getWorld().isRemote || fissionReactor == null ||
                fissionReactor.controlRodRegulationOn == enabled) {
            return;
        }

        fissionReactor.controlRodRegulationOn = enabled;
        markDirty();
        GTLog.logger.info("[Fission] Control rod regulation {} at {}", enabled ? "enabled" : "disabled", getPos());
    }

    public boolean areControlRodsRegulated() {
        return fissionReactor != null && this.fissionReactor.controlRodRegulationOn;
    }

    public void setControlRodInsertion(float value) {
        this.controlRodInsertion = value;
        if (fissionReactor != null)
            fissionReactor.updateControlRodInsertion(controlRodInsertion);
    }

    public boolean isLocked() {
        return lockingState == LockingState.LOCKED;
    }

    private void tryLocking(boolean lock) {
        if (getWorld() == null || getWorld().isRemote) {
            return;
        }

        // V3 checks are synchronous. Recheck on an explicit start request so the UI can immediately render the
        // current failure trace instead of silently returning with an old or empty diagnostic.
        if (lock) {
            checkStructurePattern();
        }
        if (!isStructureFormed()) {
            if (lock) {
                logStartupFailure("structure check");
            }
            return;
        }

        if (lock) {
            lockAndPrepareReactor();
            if (lockingState != LockingState.LOCKED) {
                logStartupFailure("component validation");
            }
        } else {
            unlockAll();
        }
    }

    @Override
    public void addBarHoverText(List<ITextComponent> list, int index) {
        if (index == 0) {
            list.add(new TextComponentTranslation("gregtech.gui.fission.temperature",
                    String.format("%.1f", this.temperature) + " / " + String.format("%.1f", this.maxTemperature)));
        } else if (index == 1) {
            list.add(new TextComponentTranslation("gregtech.gui.fission.pressure",
                    String.format("%.0f", this.pressure) + " / " + String.format("%.0f", this.maxPressure)));
        } else {
            list.add(new TextComponentTranslation("gregtech.gui.fission.power", String.format("%.1f", this.power),
                    String.format("%.1f", this.maxPower)));
        }
    }

    @Override
    protected void addErrorText(List<ITextComponent> list) {
        if (lockingState != LockingState.LOCKED && lockingState != LockingState.UNLOCKED) {
            list.add(
                    new TextComponentTranslation(
                            "gregtech.gui.fission.lock." + lockingState.toString().toLowerCase()));
        }
    }

    @Override
    protected void addDisplayText(List<ITextComponent> list) {
        if (!isStructureFormed()) {
            addStructureErrorText(list);
            return;
        }

        super.addDisplayText(list);
        list.add(
                TextComponentUtil.setColor(new TextComponentTranslation(
                                "gregtech.gui.fission.lock." + lockingState.toString().toLowerCase()),
                        getLockedTextColor()));
        list.add(new TextComponentTranslation("gregtech.gui.fission.k_eff", String.format("%.4f", this.kEff)));
    }

    /** The reactor retains its specialized legacy UI, so mirror the V3 failure trace into its text panel. */
    private void addStructureErrorText(@NotNull List<ITextComponent> list) {
        StructureFailureTrace failure = getStructureRuntime() == null ? null :
                getStructureRuntime().getLastFailure();
        PatternError error = failure == null ? getLastStructureError() : failure.getError();

        String detailKey = "";
        int detailNumber = 0;
        if (error instanceof PatternStringError stringError) {
            detailKey = stringError.translateKey;
        } else if (error instanceof CountLimitError countError) {
            detailKey = "gregtech.multiblock.pattern.error.limited." + countError.getKind().getIndex();
            detailNumber = countError.getLimit();
        }

        if (detailKey.isEmpty()) {
            list.add(new TextComponentTranslation("gregtech.multiblock.invalid_structure")
                    .setStyle(new Style().setColor(TextFormatting.RED)));
        } else {
            list.add(new TextComponentTranslation(detailKey, detailNumber)
                    .setStyle(new Style().setColor(TextFormatting.RED)));
        }

        String position = getFailurePosition(failure, error);
        String expected = failure == null ? "" : nullToEmpty(failure.getExpected());
        if (expected.isEmpty()) {
            expected = getFirstCandidateName(error);
        }

        if (!expected.isEmpty()) {
            list.add(new TextComponentTranslation("gregtech.multiblock.pattern.error", expected, position)
                    .setStyle(new Style().setColor(TextFormatting.RED)));
        } else if (!position.isEmpty()) {
            list.add(new TextComponentTranslation("gregtech.gui.fission.structure.location", position)
                    .setStyle(new Style().setColor(TextFormatting.RED)));
        } else {
            list.add(new TextComponentTranslation("gregtech.gui.fission.structure.no_trace")
                    .setStyle(new Style().setColor(TextFormatting.RED)));
        }
    }

    @NotNull
    private static String getFailurePosition(@Nullable StructureFailureTrace failure, @Nullable PatternError error) {
        BlockPos position = failure == null ? null : failure.getErrorPos();
        if (position == null && error != null) {
            try {
                position = error.getPos();
            } catch (RuntimeException ignored) {
                // Third-party preview entries can expose an incomplete PatternError.
            }
        }
        return position == null ? "" : "[X:" + position.getX() + " Y:" + position.getY() +
                " Z:" + position.getZ() + "]";
    }

    @NotNull
    private static String getFirstCandidateName(@Nullable PatternError error) {
        if (error == null) {
            return "";
        }
        try {
            for (List<ItemStack> group : error.getCandidates()) {
                if (group != null && !group.isEmpty() && !group.get(0).isEmpty()) {
                    return group.get(0).getDisplayName();
                }
            }
        } catch (RuntimeException ignored) {
            // Keep the legacy UI usable even when a third-party block has an invalid preview entry.
        }
        return "";
    }

    @NotNull
    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private void logStartupFailure(@NotNull String phase) {
        StructureFailureTrace failure = getStructureRuntime() == null ? null :
                getStructureRuntime().getLastFailure();
        GTLog.logger.warn("[Fission] Startup {} at {}: formed={}, lockingState={}, diameter={}, top={}, bottom={}, " +
                        "trace={}",
                phase, getPos(), isStructureFormed(), lockingState, diameter, heightTop, heightBottom,
                failure == null ? "none" : failure.describeForCommand());
    }

    protected EnumFacing getUp() {
        return RelativeDirection.UP.getRelativeFacing(frontFacing, upwardsFacing, isFlipped);
    }

    protected EnumFacing getRight() {
        return RelativeDirection.RIGHT.getRelativeFacing(frontFacing, upwardsFacing, isFlipped);
    }

    /**
     * Uses the upper layer to determine the diameter of the structure
     */
    protected int findDiameter(int heightTop) {
        int i = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.getPos());
        pos.move(getUp(), heightTop);
        while (i <= 15) {
            if (this.isBlockEdge(this.getWorld(), pos,
                    this.getFrontFacing().getOpposite(),
                    i))
                break;
            i++;
        }
        return i;
    }

    /**
     * Uses the center layer to determine the diameter of the structure
     */
    protected int findDiameter() {
        int i = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.getPos());
        while (this.getWorld().getBlockState(pos) !=
                MetaBlocks.FISSION_CASING.getState(BlockFissionCasing.FissionCasingType.REACTOR_VESSEL) && i <= 15) {
            pos.move(this.getFrontFacing().getOpposite());
            MetaTileEntity potentialTile = GTUtility.getMetaTileEntity(this.getWorld(), pos);
            if (potentialTile instanceof IFissionReactorHatch || potentialTile instanceof IMaintenanceHatch) {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Checks for casings on top or bottom of the controller to determine the height of the reactor
     */
    protected int findHeight(boolean top) {
        int i = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.getPos());
        while (i <= 15) {
            if (this.isBlockEdge(this.getWorld(), pos, top ? getUp() : getUp().getOpposite(), i))
                break;
            i++;
        }
        return i - 1;
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return new FissionReactorUIFactory();
    }

    /**
     * Uses the V3 text synchronizer, including its coordinates/candidate information, inside the original fission
     * display panel. The old GUI action channel no longer reaches the server once GT uses MUI2 containers.
     */
    private void configureFissionDisplayText(@NotNull MultiblockUIBuilder builder) {
        builder.structureFormed(isStructureFormed())
                .addMissingStructureAbilities(getMissingStructureAbilities())
                .addStructureError(getLastStructureError())
                .addCustom((keys, sync) -> {
                    if (!sync.syncBoolean(this::isStructureFormed)) {
                        return;
                    }

                    String lockKey = sync.syncString("gregtech.gui.fission.lock." +
                            lockingState.toString().toLowerCase(Locale.ROOT));
                    String multiplicationFactor = sync.syncString(String.format(Locale.ROOT, "%.4f", kEff));
                    keys.add(IKey.lang(lockKey).style(getLockedTextColor()));
                    keys.add(IKey.lang("gregtech.gui.fission.k_eff", multiplicationFactor));
                });
    }

    private double getSafeFillPercentage(int index) {
        double percentage = getFillPercentage(index);
        return Double.isFinite(percentage) ? Math.max(0.0, Math.min(1.0, percentage)) : 0.0;
    }

    /**
     * Keeps the established 240x208 fission layout while using MUI2 controls, so actions are routed through V3's
     * synced values rather than the legacy PacketUIClientAction path.
     */
    private final class FissionReactorUIFactory extends MultiblockUIFactory {

        private FissionReactorUIFactory() {
            super(MetaTileEntityFissionReactor.this);
        }

        @Override
        public @NotNull ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager) {
            MultiblockUIBuilder displayText = MultiblockUIFactory.builder("fission_display", panelSyncManager);
            displayText.setAction(MetaTileEntityFissionReactor.this::configureFissionDisplayText);

            DoubleSyncValue heatFill = new DoubleSyncValue(() -> getSafeFillPercentage(0));
            DoubleSyncValue pressureFill = new DoubleSyncValue(() -> getSafeFillPercentage(1));
            DoubleSyncValue powerFill = new DoubleSyncValue(() -> getSafeFillPercentage(2));
            DoubleSyncValue temperatureValue = new DoubleSyncValue(MetaTileEntityFissionReactor.this::getTemperature);
            DoubleSyncValue pressureValue = new DoubleSyncValue(MetaTileEntityFissionReactor.this::getPressure);
            DoubleSyncValue powerValue = new DoubleSyncValue(MetaTileEntityFissionReactor.this::getPower);
            DoubleSyncValue maxPowerValue = new DoubleSyncValue(MetaTileEntityFissionReactor.this::getMaxPower);
            DoubleSyncValue controlRodValue = new DoubleSyncValue(MetaTileEntityFissionReactor.this::getControlRodInsertion,
                    value -> setControlRodInsertion((float) value));

            return GTGuis.createPanel(MetaTileEntityFissionReactor.this, 240, 208)
                    .child(new ParentWidget<>()
                            .pos(4, 4)
                            .size(232, 109)
                            .background(GTGuiTextures.DISPLAY)
                            .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                            .child(new ScrollableTextWidget()
                                    .pos(5, 18)
                                    .size(222, 38)
                                    .alignment(Alignment.TopLeft)
                                    .autoUpdate(true)
                                    .textBuilder(displayText::build))
                            .child(new ParentWidget<>()
                                    .pos(6, 56)
                                    .size(220, 18)
                                    .child(new SliderWidget()
                                            .sizeRel(1.0f)
                                            .bounds(0.0, 1.0)
                                            .value(controlRodValue)
                                            .background(FISSION_SLIDER_BACKGROUND)
                                            .sliderTexture(FISSION_SLIDER_ICON)
                                            .sliderSize(4, 18)
                                            .tooltipAutoUpdate(true)
                                            .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(
                                                    "gregtech.gui.fission.control_rod_insertion",
                                                    (int) Math.round(controlRodValue.getDoubleValue() * 100.0)))))
                                    .child(IKey.dynamic(() -> String.format(Locale.ROOT, "%.2f%%",
                                                    controlRodValue.getDoubleValue() * 100.0))
                                            .style(TextFormatting.WHITE)
                                            .alignment(Alignment.CENTER)
                                            .asWidget()
                                            .sizeRel(1.0f))))
                    .child(createBar(heatFill, FISSION_HEAT_BAR, 0, temperatureValue, null).pos(4, 115))
                    .child(createBar(pressureFill, FISSION_PRESSURE_BAR, 1, pressureValue, null).pos(82, 115))
                    .child(createBar(powerFill, FISSION_ENERGY_BAR, 2, powerValue, maxPowerValue).pos(160, 115))
                    .child(SlotGroupWidget.playerInventory(false).pos(4, 125))
                    .child(new ToggleButton()
                            .name("fission_control_rod_regulation")
                            .pos(215, 125)
                            .size(18)
                            .value(new BooleanSyncValue(MetaTileEntityFissionReactor.this::areControlRodsRegulated,
                                    MetaTileEntityFissionReactor.this::toggleControlRodRegulation))
                            .background(GTGuiTextures.BUTTON)
                            .selectedBackground(GTGuiTextures.MC_BUTTON_DISABLED)
                            .overlay(FISSION_CONTROL_ROD_BUTTON)
                            .addTooltip(false, IKey.lang("gregtech.gui.fission.helper.disabled"))
                            .addTooltip(true, IKey.lang("gregtech.gui.fission.helper.enabled")))
                    .child(createUnavailableButton(GTGuiTextures.OVERLAY_DISTINCT_BUSES[0],
                            "gregtech.multiblock.universal.distinct_not_supported", 215, 143))
                    .child(createUnavailableButton(GTGuiTextures.OVERLAY_VOID_NONE,
                            "gregtech.gui.multiblock_voiding_not_supported", 215, 161))
                    .child(new ToggleButton()
                            .name("fission_lock")
                            .pos(215, 183)
                            .size(18)
                            .value(new BooleanSyncValue(MetaTileEntityFissionReactor.this::isLocked,
                                    MetaTileEntityFissionReactor.this::tryLocking))
                            .overlay(GTGuiTextures.BUTTON_LOCK)
                            .addTooltip(false, IKey.lang("gregtech.gui.fission.lock.disabled"))
                            .addTooltip(true, IKey.lang("gregtech.gui.fission.lock.enabled")))
                    .child(GTGuiTextures.BUTTON_POWER_DETAIL.asWidget().pos(215, 201).size(18, 6));
        }

        private ProgressWidget createBar(DoubleSyncValue fill, UITexture texture, int index,
                                         DoubleSyncValue current, @Nullable DoubleSyncValue maximum) {
            return new ProgressWidget()
                    .size(76, 7)
                    .value(fill)
                    .texture(texture, -1)
                    .direction(ProgressWidget.Direction.RIGHT)
                    .tooltipAutoUpdate(true)
                    .tooltipBuilder(tooltip -> {
                        if (index == 0) {
                            tooltip.addLine(IKey.lang("gregtech.gui.fission.temperature",
                                    current.getDoubleValue()));
                        } else if (index == 1) {
                            tooltip.addLine(IKey.lang("gregtech.gui.fission.pressure",
                                    current.getDoubleValue()));
                        } else {
                            tooltip.addLine(IKey.lang("gregtech.gui.fission.power",
                                    current.getDoubleValue(), maximum == null ? 0.0 : maximum.getDoubleValue()));
                        }
                    });
        }

        private ButtonWidget<?> createUnavailableButton(UITexture overlay, String tooltip, int x, int y) {
            return new ButtonWidget<>()
                    .pos(x, y)
                    .size(18)
                    .background(GTGuiTextures.BUTTON)
                    .overlay(overlay)
                    .addTooltipLine(IKey.lang(tooltip));
        }
    }

    private TextFormatting getLockedTextColor() {
        return switch (lockingState) {
            case LOCKED -> TextFormatting.GREEN;
            case UNLOCKED -> TextFormatting.DARK_AQUA;
            case INVALID_COMPONENT -> TextFormatting.RED;
            case SHOULD_LOCK -> TextFormatting.BLACK;
            default -> getWorld().getWorldTime() % 4 >= 2 ? TextFormatting.RED : TextFormatting.YELLOW;
        };
    }

    protected void performPrimaryExplosion() {
        this.unlockAll();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.getPos());
        pos = pos.move(this.getFrontFacing().getOpposite(), diameter / 2);
        this.getWorld().createExplosion(null, pos.getX(), pos.getY() + heightTop, pos.getZ(), 4.f, true);
    }

    protected void performSecondaryExplosion(double accumulatedHydrogen) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.getPos());
        pos = pos.move(this.getFrontFacing().getOpposite(), diameter / 2);
        this.getWorld().newExplosion(null, pos.getX(), pos.getY() + heightTop + 3, pos.getZ(),
                5.f + (float) Math.log(accumulatedHydrogen), true, true);
    }

    protected boolean isBlockEdge(@NotNull World world, @NotNull BlockPos.MutableBlockPos pos,
                                  @NotNull EnumFacing direction,
                                  int steps) {
        pos.move(direction, steps);

        if (world.getBlockState(pos).getBlock() == MetaBlocks.FISSION_CASING) {
            pos.move(direction.getOpposite(), steps);
            return false;
        }

        MetaTileEntity potentialTile = GTUtility.getMetaTileEntity(world, pos);
        pos.move(direction.getOpposite(), steps);
        if (potentialTile == null) {
            return true;
        }

        return !(potentialTile instanceof IFissionReactorHatch || potentialTile instanceof IMaintenanceHatch);
    }

    protected void shouldLockLogic() {
        if (fissionReactor == null) {
            this.fissionReactor = new FissionReactor(this.diameter - 2, this.height - 2, controlRodInsertion);
        }
        this.lockAndPrepareReactor();
        this.fissionReactor.deserializeNBT(transientData);
    }


    @Override
    public void updateFormedValid() {
        // Take in coolant, take in fuel, update reactor, output steam

        if (!this.getWorld().isRemote && this.getOffsetTimer() % 20 == 0) {
            if (this.lockingState == LockingState.SHOULD_LOCK) {
                shouldLockLogic();
            }
            if (this.lockingState == LockingState.LOCKED) {
                // Coolant handling
                if (this.getOffsetTimer() % 100 == 0) {
                    if (isFlowingCorrectly) {
                        if (getWorld().rand.nextDouble() > (1 - 0.01 * this.getNumMaintenanceProblems())) {
                            isFlowingCorrectly = false;
                        }
                    } else {
                        if (getWorld().rand.nextDouble() > 0.12 * this.getNumMaintenanceProblems()) {
                            isFlowingCorrectly = true;
                        }
                    }
                }

                // Fuel handling
                boolean canWork = true;
                for (IFuelRodHandler fuelImport : this.getAbilities(SCMultiblockAbility.IMPORT_FUEL_ROD)) {
                    if (fuelImport.isDepleted(this.fissionReactor.fuelDepletion)) {
                        // There are a few things that could cause the reactor to stop working when a fuel rod becomes
                        // depleted:
                        // The output is blocked
                        // The input is missing
                        // We simulate both of these things, and if it fails, we unlock the entire reactor.
                        if (!fuelImport.getOutputStackHandler(this.height - 1)
                                .insertItem(0, fuelImport.getDepletedFuel(), true)
                                .isEmpty()) {
                            canWork = false;
                            this.setLockingState(LockingState.FUEL_CLOGGED);
                            break;
                        }
                        fuelImport.getOutputStackHandler(this.height - 1).insertItem(0,
                                fuelImport.getDepletedFuel(), false);
                        fuelImport.markUndepleted();
                        if (fuelImport.getInputStackHandler().extractItem(0, 1, true).isEmpty()) {
                            canWork = false;
                            fuelImport.setPartialFuel(null); // Clear the partial fuel; it wouldn't have existed
                            this.setLockingState(LockingState.MISSING_FUEL);
                            break;
                        }
                        fuelImport.getInputStackHandler().extractItem(0, 1, false);
                    }
                }

                if (!canWork) {
                    this.unlockAll();
                }
            }
            this.updateReactorState();

            this.syncReactorStats();

            if (!ConfigHolder.nuclear.enableMeltdown) {
                return;
            }
            boolean melts = this.fissionReactor.checkForMeltdown();
            boolean explodes = this.fissionReactor.checkForExplosion();
            double hydrogen = this.fissionReactor.accumulatedHydrogen;
            if (melts) {
                this.performMeltdownEffects();
            }
            if (explodes) {
                this.performPrimaryExplosion();
                if (hydrogen > 1) {
                    this.performSecondaryExplosion(hydrogen);
                }
            }
        }
    }

    @NotNull
    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(new TextComponentTranslation("gregtech.multiblock.fission_reactor.diameter",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(this.diameter) + "m")
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        list.add(new TextComponentTranslation("gregtech.multiblock.fission_reactor.height",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(this.height) + "m")
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        return list;
    }

    protected void performMeltdownEffects() {
        this.unlockAll();
        StructureRuntime structureRuntime = getStructureRuntime();
        if (structureRuntime == null) {
            return;
        }
        Map<BlockPos, BlockInfo> cache = structureRuntime.iterateSingle(
                StructureOperationRequest.iterate(getWorld(), getPos(), StructureOrientation.fromController(this), this));
        Map<BlockPos, Boolean> meltsDown = new Object2BooleanOpenCustomHashMap<>(
                new Hash.Strategy<>() {

                    @Override
                    public int hashCode(BlockPos o) {
                        return o.getX() << 16 + o.getZ();
                    }

                    @Override
                    public boolean equals(BlockPos a, BlockPos b) {
                        if (a == null || b == null) {
                            return false;
                        }
                        return a.getX() == b.getX() && a.getZ() == b.getZ();
                    }
                });
        cache.forEach((pos, info) -> {
            if (meltsDown.containsKey(pos) && meltsDown.get(pos)) { // Already melted; not worrying about if it was
                // above or not
                return;
            }
            int chance = 10;
            if (pos.getY() == this.getPos().getY() - this.heightBottom) {
                chance = 1;
            } else if (info.getTileEntity() instanceof IGregTechTileEntity mteHolder) {
                if (mteHolder.getMetaTileEntity() instanceof IFuelRodHandler) {
                    chance = 1;
                }
            }
            if (getWorld().rand.nextInt(chance) == 0) {
                meltsDown.put(pos, true);
            }
        });
        for (BlockPos immutPos : meltsDown.keySet()) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(immutPos);
            while (pos.getY() >= this.getPos().getY() - this.heightBottom) {
                this.getWorld().setBlockState(pos, Materials.Corium.getFluid().getBlock().getDefaultState());
                pos.move(EnumFacing.DOWN);
            }
        }
    }

    @NotNull
    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        this.heightTop = Math.max(Math.min(this.getWorld() != null ? this.findHeight(true) : MIN_REACTOR_EXTENSION,
                MAX_REACTOR_EXTENSION), MIN_REACTOR_EXTENSION);
        this.heightBottom = Math.max(Math.min(this.getWorld() != null ? this.findHeight(false) : MIN_REACTOR_EXTENSION,
                MAX_REACTOR_EXTENSION), MIN_REACTOR_EXTENSION);

        this.height = heightTop + heightBottom + 1;

        this.diameter = this.getWorld() != null ? Math.max(Math.min(this.findDiameter(), MAX_REACTOR_DIAMETER),
                MIN_REACTOR_DIAMETER) : MIN_REACTOR_DIAMETER;

        String key = "gregtech:fission_reactor." + diameter + "." + heightTop + "." + heightBottom;
        return StructureDefinition.getOrBuild(key,
                () -> buildFissionStructureDefinition(createFissionAisles(diameter, heightTop, heightBottom)));
    }

    @NotNull
    private static String[][] createFissionAisles(int diameter, int heightTop, int heightBottom) {
        int radius = diameter % 2 == 0 ? (int) Math.floor(diameter / 2.f) :
                Math.round((diameter - 1) / 2.f);

        StringBuilder interiorBuilder = new StringBuilder();

        String[] interiorSlice = new String[diameter];
        String[] controllerSlice;
        String[] topSlice;
        String[] bottomSlice;

        // First loop over the matrix
        for (int i = 0; i < diameter; i++) {
            for (int j = 0; j < diameter; j++) {

                if (Math.pow(i - Math.floor(diameter / 2.), 2) + Math.pow(j - Math.floor(diameter / 2.), 2) <
                        Math.pow(radius + 0.5f, 2)) {
                    interiorBuilder.append('A');
                } else {
                    interiorBuilder.append(' ');
                }
            }

            interiorSlice[i] = interiorBuilder.toString();
            interiorBuilder.setLength(0);
        }

        // Second loop is to detect where to put walls, the controller and I/O, two fewer iterations are needed because
        // two strings always represent two walls on opposite sides
        interiorSlice[diameter - 1] = interiorSlice[0] = interiorSlice[0].replace('A', 'B');
        for (int i = 1; i < diameter - 1; i++) {
            for (int j = 0; j < diameter; j++) {
                if (interiorSlice[i].charAt(j) != 'A') {
                    continue;
                }

                // The integer division is fine here, since we want an odd diameter (say, 5) to go to the middle value
                // (2 in this case)
                int outerI = i + (int) Math.signum(i - (diameter / 2));

                if (Math.pow(outerI - Math.floor(diameter / 2.), 2) +
                        Math.pow(j - Math.floor(diameter / 2.), 2) >
                        Math.pow(radius + 0.5f, 2)) {
                    interiorSlice[i] = SCUtility.replace(interiorSlice[i], j, 'B');
                }

                int outerJ = j + (int) Math.signum(j - (diameter / 2));
                if (Math.pow(i - Math.floor(diameter / 2.), 2) +
                        Math.pow(outerJ - Math.floor(diameter / 2.), 2) >
                        Math.pow(radius + 0.5f, 2)) {
                    interiorSlice[i] = SCUtility.replace(interiorSlice[i], j, 'B');
                }
            }
        }

        controllerSlice = interiorSlice.clone();
        topSlice = interiorSlice.clone();
        bottomSlice = interiorSlice.clone();
        controllerSlice[0] = controllerSlice[0].substring(0, (int) Math.floor(diameter / 2.)) + 'S' +
                controllerSlice[0].substring((int) Math.floor(diameter / 2.) + 1);
        for (int i = 0; i < diameter; i++) {
            topSlice[i] = topSlice[i].replace('A', 'I');
            bottomSlice[i] = bottomSlice[i].replace('A', 'O');
        }

        List<String[]> aisles = new ArrayList<>();
        aisles.add(bottomSlice);
        for (int i = 0; i < heightBottom - 1; i++) {
            aisles.add(interiorSlice);
        }
        aisles.add(controllerSlice);
        for (int i = 0; i < heightTop - 1; i++) {
            aisles.add(interiorSlice);
        }
        aisles.add(topSlice);

        return aisles.toArray(new String[0][]);
    }

    private static StructureDefinition<MetaTileEntityFissionReactor> buildFissionStructureDefinition(String[][] aisles) {
        return StructureDefinition.<MetaTileEntityFissionReactor>builder(
                        RelativeDirection.RIGHT, RelativeDirection.FRONT, RelativeDirection.UP)
                .globalAbilityLimit(MultiblockAbility.MAINTENANCE_HATCH, 1, 1)
                .piece("main", aisles, new Vec3i(0, 0, 0))
                .where('S', Elements.self(MetaTileEntityFissionReactor.class))
                .where('A', Elements.chain(
                        Elements.blocks(getFuelChannelState(), getControlRodChannelState(), getCoolantChannelState()),
                        Elements.air(),
                        moderatorElement()))
                .where('I', Elements.chain(Elements.block(getCasingState()), importHatchElement()))
                .where('O', Elements.chain(
                        Elements.block(getCasingState()),
                        Elements.abilities(SCMultiblockAbility.EXPORT_COOLANT,
                                SCMultiblockAbility.EXPORT_FUEL_ROD)))
                .where('B', Elements.chain(
                        Elements.block(getCasingState()),
                        Elements.hatch(MultiblockAbility.MAINTENANCE_HATCH)))
                .where(' ', Elements.any())
                .build();
    }

    private static IStructureElement<Object> importHatchElement() {
        IStructureElement<Object> allowedHatch = importHatchAbilityElement();
        return new ITypedStructureElement<Object>() {

            @Override
            public boolean check(StructureEvaluationContext<Object> context) {
                return context.transaction(transactionContext -> {
                    if (!allowedHatch.match(transactionContext)) {
                        return false;
                    }
                    if (!(transactionContext.getTileEntity() instanceof IGregTechTileEntity)) {
                        return false;
                    }
                    MetaTileEntity metaTileEntity =
                            ((IGregTechTileEntity) transactionContext.getTileEntity()).getMetaTileEntity();
                    if (!(metaTileEntity instanceof IFissionReactorHatch)) {
                        return false;
                    }
                    if (!(transactionContext.getController() instanceof MetaTileEntityFissionReactor)) {
                        return false;
                    }
                    IFissionReactorHatch hatch = (IFissionReactorHatch) metaTileEntity;
                    MetaTileEntityFissionReactor controller =
                            (MetaTileEntityFissionReactor) transactionContext.getController();
                    if (!hatch.checkValidity(controller.height - 1)) {
                        transactionContext.setError(
                                new PatternStringError("gregtech.multiblock.pattern.error.hatch_invalid"));
                        return false;
                    }
                    return true;
                });
            }

            @Override
            public BlockInfo[] getCandidates() {
                return allowedHatch.getCandidates();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<Object> importHatchAbilityElement() {
        return Elements.abilities(SCMultiblockAbility.IMPORT_COOLANT,
                SCMultiblockAbility.IMPORT_FUEL_ROD,
                SCMultiblockAbility.CONTROL_ROD_PORT,
                SCMultiblockAbility.MODERATOR_PORT);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return SCTextures.FISSION_REACTOR_TEXTURE;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return SCTextures.FISSION_REACTOR_OVERLAY;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);

        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), isActive(),
                true);
    }

    @Override
    public boolean isActive() {
        return isStructureFormed() && lockingState == LockingState.LOCKED;
    }

    @Override
    public void checkStructurePattern() {
        if (!this.isStructureFormed()) {
            reinitializeStructurePattern();
        }
        super.checkStructurePattern();
    }

    @Override
    public void invalidateStructure() {
        this.unlockAll();
        this.fissionReactor = null;
        this.temperature = 273;
        this.maxTemperature = 273;
        this.power = 0;
        this.kEff = 0;
        this.pressure = 0;
        this.maxPressure = 0;
        this.maxPower = 0;
        super.invalidateStructure();
    }

    @Override
    protected void formStructure(FormedStructureView formed) {
        super.formStructure(formed);
        if (fissionReactor == null) {
            fissionReactor = new FissionReactor(this.diameter - 2, this.height - 2, controlRodInsertion);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setInteger("diameter", this.diameter);
        data.setInteger("heightTop", this.heightTop);
        data.setInteger("heightBottom", this.heightBottom);
        data.setDouble("controlRodInsertion", this.controlRodInsertion);
        data.setBoolean("locked", this.lockingState == LockingState.LOCKED || this.lockingState == LockingState.SHOULD_LOCK);
        data.setDouble("kEff", this.kEff);
        if (fissionReactor != null) {
            data.setTag("transientData", this.fissionReactor.serializeNBT());
        }

        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.diameter = data.getInteger("diameter");
        this.heightTop = data.getInteger("heightTop");
        this.heightBottom = data.getInteger("heightBottom");
        this.controlRodInsertion = data.getDouble("controlRodInsertion");
        this.height = this.heightTop + this.heightBottom + 1;
        this.kEff = data.getDouble("kEff");
        if (data.getBoolean("locked") && this.lockingState != LockingState.LOCKED) {
            this.lockingState = LockingState.SHOULD_LOCK;
        }
        if (data.hasKey("transientData")) {
            transientData = data.getCompoundTag("transientData");
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.diameter);
        buf.writeInt(this.heightTop);
        buf.writeInt(this.heightBottom);
        buf.writeDouble(this.controlRodInsertion);
        buf.writeBoolean(this.lockingState == LockingState.LOCKED || this.lockingState == LockingState.SHOULD_LOCK);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.diameter = buf.readInt();
        this.heightTop = buf.readInt();
        this.heightBottom = buf.readInt();
        this.controlRodInsertion = buf.readDouble();
        if (buf.readBoolean()) {
            this.lockingState = LockingState.LOCKED;
        }
    }

    public void syncReactorStats() {
        this.temperature = this.fissionReactor.temperature;
        this.maxTemperature = this.fissionReactor.maxTemperature;
        this.pressure = this.fissionReactor.pressure;
        this.maxPressure = this.fissionReactor.maxPressure;
        this.power = this.fissionReactor.power;
        this.maxPower = this.fissionReactor.maxPower;
        this.kEff = this.fissionReactor.kEff;
        this.controlRodInsertion = this.fissionReactor.controlRodInsertion;
        this.totalDepletion = this.fissionReactor.fuelDepletion;
        writeCustomData(SCValues.SYNC_REACTOR_STATS, (packetBuffer -> {
            packetBuffer.writeDouble(this.temperature);
            packetBuffer.writeDouble(this.maxTemperature);
            packetBuffer.writeDouble(this.pressure);
            packetBuffer.writeDouble(this.maxPressure);
            packetBuffer.writeDouble(this.power);
            packetBuffer.writeDouble(this.maxPower);
            packetBuffer.writeDouble(this.kEff);
            packetBuffer.writeDouble(this.controlRodInsertion);
            packetBuffer.writeDouble(this.totalDepletion);
        }));
        this.markDirty();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);

        if (dataId == SCValues.SYNC_REACTOR_STATS) {
            this.temperature = buf.readDouble();
            this.maxTemperature = buf.readDouble();
            this.pressure = buf.readDouble();
            this.maxPressure = buf.readDouble();
            this.power = buf.readDouble();
            this.maxPower = buf.readDouble();
            this.kEff = buf.readDouble();
            this.controlRodInsertion = buf.readDouble();
            this.totalDepletion = buf.readDouble();
        } else if (dataId == SCValues.SYNC_LOCKING_STATE) {
            this.lockingState = buf.readEnumValue(LockingState.class);
            this.scheduleRenderUpdate();
        }
    }

    protected void lockAll() {
        for (ICoolantHandler handler : this.getAbilities(SCMultiblockAbility.IMPORT_COOLANT)) {
            handler.setLock(true);
        }
        for (IFuelRodHandler handler : this.getAbilities(SCMultiblockAbility.IMPORT_FUEL_ROD)) {
            handler.setLock(true);
        }
    }

    protected void unlockAll() {
        for (ICoolantHandler handler : this.getAbilities(SCMultiblockAbility.IMPORT_COOLANT)) {
            handler.setLock(false);
        }
        for (IFuelRodHandler handler : this.getAbilities(SCMultiblockAbility.IMPORT_FUEL_ROD)) {
            handler.resetDepletion(this.fissionReactor.fuelDepletion); // Must come first
            handler.setLock(false);
        }
        if (this.fissionReactor != null) {
            this.fissionReactor.turnOff();
            this.fissionReactor.resetFuelDepletion();
        }
        if (this.lockingState == LockingState.LOCKED) { // Don't remove warnings
            this.setLockingState(LockingState.UNLOCKED);
        }
    }

    private void lockAndPrepareReactor() {
        if (!verifyCorrectness()) {
            return;
        }
        this.lockAll();
        this.addReactorComponents();
        fissionReactor.prepareThermalProperties();
        fissionReactor.computeGeometry();
        GTLog.logger.info("[Fission] Control rod setup at {}: installed={}, effective={}, moderated={}, baseK={}",
                getPos(), fissionReactor.getControlRodCount(), fissionReactor.getEffectiveControlRodCount(),
                fissionReactor.getModeratorTippedControlRodCount(),
                String.format(Locale.ROOT, "%.4f", fissionReactor.getBaseK()));
        setLockingState(LockingState.LOCKED);
    }

    private boolean verifyCorrectness() {
        boolean foundFuel = false;
        int radius = this.diameter / 2;
        BlockPos.MutableBlockPos reactorOrigin = new BlockPos.MutableBlockPos(this.getPos());
        reactorOrigin.move(this.frontFacing.getOpposite(), radius);

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (Math.pow(i, 2) + Math.pow(j, 2) > Math.pow(radius, 2) + radius)         // (radius + .5)^2 =
                    // radius^2 + radius + .25
                    continue;
                BlockPos currentPos = reactorOrigin.offset(this.getRight(), i)
                        .offset(this.frontFacing.getOpposite(), j).offset(getUp(), heightTop);
                if (getWorld().getTileEntity(currentPos) instanceof IGregTechTileEntity gtTe) {
                    MetaTileEntity mte = gtTe.getMetaTileEntity();
                    if (mte instanceof ICoolantHandler coolantIn) {
                        Fluid lockedFluid = coolantIn.getLockedObject();
                        if (lockedFluid != null) {
                            ICoolantStats stats = CoolantRegistry.getCoolant(lockedFluid);
                            if (stats != null) {
                                continue;
                            }
                        }
                        this.unlockAll();
                        setLockingState(LockingState.MISSING_COOLANT);
                        return false;
                    } else if (mte instanceof IFuelRodHandler fuelIn) {
                        ItemStack lockedFuel = fuelIn.getInputStackHandler().getStackInSlot(0);
                        if (!lockedFuel.isEmpty()) {
                            IFissionFuelStats stats = FissionFuelRegistry.getFissionFuel(lockedFuel);
                            if (stats != null) {
                                foundFuel = true;
                                continue;
                            }
                        } else if (fuelIn.getPartialFuel() != null) {
                            foundFuel = true;
                            continue;
                        }
                        this.unlockAll();
                        setLockingState(LockingState.MISSING_FUEL);
                        return false;
                    }
                }
            }
        }
        if (!foundFuel) {
            this.unlockAll();
            setLockingState(LockingState.NO_FUEL_CHANNELS);
            return false;
        }
        return true;
    }

    private void addReactorComponents() {
        int radius = this.diameter / 2;     // This is the floor of the radius, the actual radius is 0.5 blocks
        // larger
        BlockPos.MutableBlockPos reactorOrigin = new BlockPos.MutableBlockPos(this.getPos());
        reactorOrigin.move(this.frontFacing.getOpposite(), radius);
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (Math.pow(i, 2) + Math.pow(j, 2) > Math.pow(radius, 2) + radius)         // (radius + .5)^2 =
                    // radius^2 + radius + .25
                    continue;
                BlockPos currentPos = reactorOrigin.offset(this.getRight(), i)
                        .offset(this.frontFacing.getOpposite(), j).offset(getUp(), heightTop);
                if (getWorld().getTileEntity(currentPos) instanceof IGregTechTileEntity gtTe) {
                    MetaTileEntity mte = gtTe.getMetaTileEntity();
                    if (mte instanceof ICoolantHandler coolantIn) {
                        Fluid lockedFluid = coolantIn.getLockedObject();
                        ICoolantStats stats = CoolantRegistry.getCoolant(lockedFluid);
                        coolantIn.setCoolant(stats);
                        coolantIn.getOutputHandler().setCoolant(stats);
                        CoolantChannel component = new CoolantChannel(100050, 0, stats, 1000, coolantIn,
                                coolantIn.getOutputHandler());
                        fissionReactor.addComponent(component, i + radius - 1, j + radius - 1);
                    } else if (mte instanceof IFuelRodHandler fuelIn) {
                        ItemStack lockedFuel = fuelIn.getInputStackHandler().getStackInSlot(0);
                        IFissionFuelStats stats = FissionFuelRegistry.getFissionFuel(lockedFuel);
                        FuelRod component;
                        fuelIn.setFuel(stats);
                        if (fuelIn.getDepletionPoint() == 0 || fuelIn.getPartialFuel() == null) {
                            fuelIn.setPartialFuel(stats);
                            component = new FuelRod(stats.getMaxTemperature(), 1, stats, 650);
                            fuelIn.getInputStackHandler().extractItem(0, 1, false); // Consume the fuel
                            fuelIn.markUndepleted(); // Set the depletion point
                        } else {
                            // It's guaranteed to have this property (if the implementation is correct).
                            IFissionFuelStats partialProp = fuelIn.getPartialFuel();
                            component = new FuelRod(partialProp.getMaxTemperature(), 1, partialProp, 650);
                        }
                        fuelIn.setInternalFuelRod(component);
                        fissionReactor.addComponent(component, i + radius - 1, j + radius - 1);
                    } else if (mte instanceof MetaTileEntityControlRodPort controlIn) {
                        ControlRod component = new ControlRod(100000, controlIn.hasModeratorTip(), 1, 800);
                        fissionReactor.addComponent(component, i + radius - 1, j + radius - 1);
                    } else if (mte instanceof MetaTileEntityModeratorPort moderatorIn) {
                        IModeratorStats moderator = moderatorIn.getModerator();
                        Moderator component = new Moderator(moderator, 0.5, 800);
                        fissionReactor.addComponent(component, i + radius - 1, j + radius - 1);
                    }
                }
            }
        }
    }

    private void updateReactorState() {
        this.fissionReactor.updatePower();
        this.fissionReactor.updateTemperature();
        this.fissionReactor.updatePressure();
        this.fissionReactor.updateNeutronPoisoning();
        this.fissionReactor.regulateControlRods();
    }

    protected void setLockingState(LockingState lockingState) {
        if (this.lockingState != lockingState) {
            writeCustomData(SCValues.SYNC_LOCKING_STATE, (buf) -> buf.writeEnumValue(lockingState));
        }
        this.lockingState = lockingState;
    }

    @Override
    public long getCoverCapacity() {
        // power is in MW
        return (long) (this.maxPower * 1e6);
    }

    @Override
    public long getCoverStored() {
        // power is in MW
        return (long) (this.power * 1e6);
    }

    /**
     * Tooling dimensions are explicit so that JEI's prototype controller and a placed controller never derive
     * different shapes from world-scanned state. Width is the reactor diameter, height is the upper extension,
     * and length is the lower extension.
     */
    @NotNull
    @Override
    public List<StructureChannel> getSupportedChannels() {
        return Arrays.asList(
                GTStructureChannels.STRUCTURE_WIDTH,
                GTStructureChannels.STRUCTURE_HEIGHT,
                GTStructureChannels.STRUCTURE_LENGTH);
    }

    @Override
    public int[] getChannelRange(@NotNull StructureChannel channel) {
        if (channel == GTStructureChannels.STRUCTURE_WIDTH) {
            return new int[] { MIN_REACTOR_DIAMETER, MAX_REACTOR_DIAMETER };
        }
        if (channel == GTStructureChannels.STRUCTURE_HEIGHT ||
                channel == GTStructureChannels.STRUCTURE_LENGTH) {
            return new int[] { MIN_REACTOR_EXTENSION, MAX_REACTOR_EXTENSION };
        }
        return super.getChannelRange(channel);
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes() {
        return getMatchingShapes(Collections.emptyMap());
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes(@Nullable Map<String, Integer> channelValues) {
        StructureRuntime runtime = createFissionToolingRuntime(channelValues);
        return Collections.singletonList(runtime.previewMultiPiece(
                StructureOperationRequest.previewMultiPiece(channelValues, this)).getShape());
    }

    @NotNull
    @Override
    public Map<BlockPos, StructureElementPreviewEntry> buildStructurePreviewEntries(
            @Nullable Map<String, Integer> channelValues) {
        return createFissionToolingRuntime(channelValues).previewMultiPiece(
                StructureOperationRequest.previewMultiPiece(channelValues, this)).getPreviewEntries();
    }

    @Override
    public boolean autoBuildStructure(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        createFissionToolingRuntime(request.getChannelValues()).buildAllPieces(request);
        return true;
    }

    @Override
    public void spawnStructureHints(@NotNull StructureOperationRequest request) {
        hintStructure(request);
    }

    @NotNull
    @Override
    public StructureHintResult hintStructure(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        return createFissionToolingRuntime(request.getChannelValues()).hintAllPieces(request);
    }

    @NotNull
    private StructureRuntime createFissionToolingRuntime(@Nullable Map<String, Integer> channelValues) {
        int previewDiameter = resolvePreviewDimension(channelValues, GTStructureChannels.STRUCTURE_WIDTH,
                MIN_REACTOR_DIAMETER, MIN_REACTOR_DIAMETER, MAX_REACTOR_DIAMETER);
        int previewHeightTop = resolvePreviewDimension(channelValues, GTStructureChannels.STRUCTURE_HEIGHT,
                MIN_REACTOR_EXTENSION, MIN_REACTOR_EXTENSION, MAX_REACTOR_EXTENSION);
        int previewHeightBottom = resolvePreviewDimension(channelValues, GTStructureChannels.STRUCTURE_LENGTH,
                MIN_REACTOR_EXTENSION, MIN_REACTOR_EXTENSION, MAX_REACTOR_EXTENSION);
        return createDynamicStructureRuntime(buildFissionStructureDefinition(
                createFissionAisles(previewDiameter, previewHeightTop, previewHeightBottom)));
    }

    @NotNull
    @Override
    protected StructureRuntime createToolingPreviewRuntime(
            @Nullable Map<String, Integer> channelValues) {
        return createFissionToolingRuntime(channelValues);
    }

    private static int resolvePreviewDimension(@Nullable Map<String, Integer> channelValues,
                                               @NotNull StructureChannel channel,
                                               int defaultValue, int min, int max) {
        if (channelValues == null) {
            return defaultValue;
        }
        int value = channelValues.getOrDefault(channel.getName(), defaultValue);
        return value >= min && value <= max ? value : defaultValue;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public String[] getDescription() {
        return new String[] {
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.title"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.dimensions"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.layout"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.row_1"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.row_2"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.row_3"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.row_4"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.row_5"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.top"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.middle"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.bottom"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.shell"),
                I18n.format("gregtech.multiblock.fission_reactor.description.minimal.operation"),
                I18n.format("gregtech.multiblock.fission_reactor.description")
        };
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        for (int line = 1; line <= TOOLTIP_LINE_COUNT; line++) {
            tooltip.add(I18n.format("gregtech.machine.fission_reactor.tooltip." + line));
        }
    }

    @Override
    public boolean allowsExtendedFacing() {
        return ConfigHolder.nuclearMisc.allowExtendedFacingForFissionReactor;
    }

    public enum LockingState {
        // The reactor is locked
        LOCKED,
        // The reactor is unlocked
        UNLOCKED,
        // The reactor is supposed to be locked, but the locking logic is yet to run
        SHOULD_LOCK,
        // The reactor can't lock because it is missing fuel in a fuel channel
        MISSING_FUEL,
        // The reactor can't lock because it is missing coolant in a coolant channel
        MISSING_COOLANT,
        // The reactor can't lock because a fuel output is clogged
        FUEL_CLOGGED,
        // There are no fuel channels at all!
        NO_FUEL_CHANNELS,
        // The reactor can't lock because components are flagged as invalid
        INVALID_COMPONENT
    }
}
