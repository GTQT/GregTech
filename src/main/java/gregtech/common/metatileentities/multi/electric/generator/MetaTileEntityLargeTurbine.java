package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.IRotorHolder;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ParametricFuelController;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.sync.FixedIntArraySyncValue;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public class MetaTileEntityLargeTurbine extends ParametricFuelController<LargeTurbineType>
        implements ProgressBarMultiblock {

    // Static template cache: one SoftTemplate per LargeTurbineType variant
    private static final Map<LargeTurbineType, SoftTemplate> TEMPLATES = TemplatePool.buildEnumCache(
            "gregtech:large_turbine", LargeTurbineType.class,
            type -> () -> buildTemplate(type));

    @Override
    @NotNull
    protected Map<LargeTurbineType, SoftTemplate> getTemplateCache() {
        return TEMPLATES;
    }

    @Override
    @NotNull
    protected String getVariantTranslationPrefix() {
        return "gregtech.machine.large_turbine";
    }

    private static BlockPatternTemplate buildTemplate(LargeTurbineType type) {
        return DeclarativePatternBuilder.start()
                .aisle("CCCC", "CHHC", "CCCC")
                .aisle("CHHC", "RGGR", "CHHC")
                .aisle("CCCC", "CSHC", "CCCC")
                .where('S', selfPredicateByClass(MetaTileEntityLargeTurbine.class))
                .where('G', states(type.getGearboxState()))
                .where('C', states(type.getCasingState()))
                .where('R', metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.ROTOR_HOLDER).stream()
                        .filter(mte -> (mte instanceof ITieredMetaTileEntity) &&
                                (((ITieredMetaTileEntity) mte).getTier() >= type.getTier()))
                        .toArray(MetaTileEntity[]::new))
                        .addTooltips("gregtech.multiblock.pattern.clear_amount_3")
                        .addTooltip("gregtech.multiblock.pattern.error.limited.1", GTValues.VN[type.getTier()])
                        .setExactLimit(1)
                        .or(abilities(MultiblockAbility.OUTPUT_ENERGY)).setExactLimit(1))
                .casing('H', CasingDefinition.simple(type.getCasingState(),
                        "gregtech.machine.casing.turbine"))
                    .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
                    .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 4)
                    .withOptionalHatches(MultiblockAbility.EXPORT_FLUIDS, 4)
                    .withOptionalHatches(MultiblockAbility.MUFFLER_HATCH, type.hasMufflerHatch() ? 1 : 0)
                .buildTemplate();
    }

    private static final int MIN_DURABILITY_TO_WARN = 10;

    public IFluidHandler exportFluidHandler;

    // Primary constructor: single-ID with variant
    public MetaTileEntityLargeTurbine(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, LargeTurbineType.class, LargeTurbineType.STEAM,
                LargeTurbineType.STEAM.getRecipeMap(), LargeTurbineType.STEAM.getTier());
    }

    // Variant-specific constructor for direct instantiation
    public MetaTileEntityLargeTurbine(ResourceLocation metaTileEntityId, LargeTurbineType turbineType) {
        super(metaTileEntityId, LargeTurbineType.class, turbineType,
                turbineType.getRecipeMap(), turbineType.getTier());
    }

    @Override
    @NotNull
    protected MultiblockRecipeLogic createWorkable() {
        LargeTurbineType type = getVariant();
        return new LargeTurbineWorkableHandler(this, getRecipeMapForVariant(type), type.getTier());
    }

    @Override
    @NotNull
    protected RecipeMap<?> getRecipeMapForVariant(@NotNull LargeTurbineType variant) {
        return variant.getRecipeMap();
    }

    @Override
    protected void onVariantChanged() {
        LargeTurbineType type = getVariant();
        this.tier = type.getTier();
        this.recipeMapWorkable = createWorkable();
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[tier]);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeTurbine(metaTileEntityId, getVariant());
    }

    // region Turbine Logic

    public IRotorHolder getRotorHolder() {
        List<IRotorHolder> abilities = getAbilities(MultiblockAbility.ROTOR_HOLDER);
        if (abilities.isEmpty())
            return null;
        return abilities.get(0);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.exportFluidHandler = null;
    }

    /**
     * @return true if turbine is formed and it's face is free and contains
     *         only air blocks in front of rotor holder
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isRotorFaceFree() {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder != null)
            return isStructureFormed() && getRotorHolder().isFrontFaceFree();
        return false;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        this.exportFluidHandler = new FluidTankList(true, getAbilities(MultiblockAbility.EXPORT_FLUIDS));
        ((LargeTurbineWorkableHandler) this.recipeMapWorkable).updateTanks();
    }

    @Override
    protected long getMaxVoltage() {
        long maxProduction = recipeMapWorkable.getMaxVoltage();
        long currentProduction = ((LargeTurbineWorkableHandler) recipeMapWorkable).boostProduction(maxProduction);
        if (isActive() && currentProduction <= maxProduction) {
            return currentProduction;
        } else {
            return 0L;
        }
    }

    // endregion

    // region Display

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        MultiblockFuelRecipeLogic recipeLogic = (MultiblockFuelRecipeLogic) recipeMapWorkable;
        builder.setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive())
                .addEnergyProductionLine(getMaxVoltage(), recipeLogic.getRecipeEUt())
                .addCustom((keyList, syncer) -> {
                    if (!isStructureFormed()) return;

                    int rotorEfficiency = syncer.syncInt(() -> getRotorHolder().getRotorEfficiency());
                    int totalEfficiency = syncer.syncInt(() -> getRotorHolder().getTotalEfficiency());

                    if (rotorEfficiency > 0) {
                        IKey efficiencyInfo = KeyUtil.number(TextFormatting.AQUA,
                                totalEfficiency, "%");
                        keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.turbine.efficiency",
                                efficiencyInfo));
                    }
                })
                .addFuelNeededLine(recipeLogic.getRecipeFluidInputInfo(), recipeLogic.getPreviousRecipeDuration())
                .addWorkingStatusLine();
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addCustom((keyList, syncer) -> {
            if (!isStructureFormed() || syncer.syncBoolean(() -> getRotorHolder() == null))
                return;

            int rotorEfficiency = syncer.syncInt(() -> getRotorHolder().getRotorEfficiency());
            int rotorDurability = syncer.syncInt(() -> getRotorHolder().getRotorDurabilityPercent());

            if (rotorEfficiency > 0 && rotorDurability <= MIN_DURABILITY_TO_WARN) {
                keyList.add(KeyUtil.lang(TextFormatting.YELLOW,
                        "gregtech.multiblock.turbine.rotor_durability_low"));
            }
        });
        super.configureWarningText(builder);
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        builder.addCustom((keyList, syncer) -> {
            if (!isStructureFormed() || syncer.syncBoolean(() -> getRotorHolder() == null))
                return;

            if (syncer.syncBoolean(!isRotorFaceFree())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.turbine.obstructed"));
                keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.turbine.obstructed.desc"));
            }
            int rotorEfficiency = syncer.syncInt(() -> getRotorHolder().getRotorEfficiency());

            if (rotorEfficiency <= 0) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.turbine.no_rotor"));
            }
        });
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.base_production_eut", GTValues.V[tier] * 2));
        tooltip.add(I18n.format("gregtech.multiblock.turbine.efficiency_tooltip", GTValues.VNF[tier]));
    }

    // endregion

    // region Structure

    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATES.get(getVariant()).get();
    }

    @Override
    public String[] getDescription() {
        return new String[] { I18n.format("gregtech.multiblock.large_turbine.description") };
    }

    public IBlockState getCasingState() {
        return getVariant().getCasingState();
    }

    public IBlockState getGearBoxState() {
        return getVariant().getGearboxState();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return getVariant().getCasingRenderer();
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return getVariant().getFrontOverlay();
    }

    @Override
    public boolean hasMufflerMechanics() {
        return getVariant().hasMufflerHatch();
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || !isRotorFaceFree();
    }

    // endregion

    // region Voiding

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return true;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return true;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    // endregion

    // region Progress Bars

    @Override
    public int getProgressBarCount() {
        return 3;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        // Fuel amount sync (int array for tooltip raw values)
        FixedIntArraySyncValue fuelValue = new FixedIntArraySyncValue(this::getFuelAmount);
        StringSyncValue fuelNameValue = new StringSyncValue(() -> {
            FluidStack stack = ((MultiblockFuelRecipeLogic) recipeMapWorkable).getInputFluidStack();
            if (stack == null) {
                return null;
            }
            Fluid fluid = stack.getFluid();
            if (fluid == null) {
                return null;
            }
            return fluid.getName();
        });
        syncManager.syncValue("fuel_amount", fuelValue);
        syncManager.syncValue("fuel_name", fuelNameValue);

        // Fuel progress as DoubleSyncValue for reliable client rendering
        DoubleSyncValue fuelProgressValue = new DoubleSyncValue(() -> {
            int[] fuel = getFuelAmount();
            return fuel[1] == 0 ? 0 : 1.0 * fuel[0] / fuel[1];
        });
        syncManager.syncValue("fuel_progress", fuelProgressValue);

        // Rotor speed sync (int array for tooltip raw values)
        FixedIntArraySyncValue rotorSpeedValue = new FixedIntArraySyncValue(this::getRotorSpeedData);
        syncManager.syncValue("rotor_speed", rotorSpeedValue);

        // Rotor speed progress as DoubleSyncValue for reliable client rendering
        DoubleSyncValue rotorSpeedProgressValue = new DoubleSyncValue(() -> {
            int[] data = getRotorSpeedData();
            return data[1] == 0 ? 0 : 1.0 * data[0] / data[1];
        });
        syncManager.syncValue("rotor_speed_progress", rotorSpeedProgressValue);

        // Rotor durability and efficiency (for tooltip)
        IntSyncValue durabilityValue = new IntSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0;
            }
            return rotorHolder.getRotorDurabilityPercent();
        });
        IntSyncValue efficiencyValue = new IntSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0;
            }
            return rotorHolder.getRotorEfficiency();
        });
        syncManager.syncValue("rotor_durability", durabilityValue);
        syncManager.syncValue("rotor_efficiency", efficiencyValue);

        // Rotor durability progress as DoubleSyncValue for reliable client rendering
        DoubleSyncValue durabilityProgressValue = new DoubleSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0.0;
            }
            return rotorHolder.getRotorDurabilityPercent() / 100.0;
        });
        syncManager.syncValue("rotor_durability_progress", durabilityProgressValue);

        // Fuel bar — uses DoubleSyncValue directly as progress source
        bars.add(barTest -> barTest
                .value(fuelProgressValue)
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_FUEL)
                .tooltipBuilder(t -> createFuelTooltip(t, fuelValue, fuelNameValue)));

        // Rotor speed bar — uses DoubleSyncValue directly as progress source
        bars.add(barTest -> barTest
                .value(rotorSpeedProgressValue)
                .texture(GTGuiTextures.PROGRESS_BAR_TURBINE_ROTOR_SPEED)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        int speed = rotorSpeedValue.getValue(0);
                        int maxSpeed = rotorSpeedValue.getValue(1);

                        t.addLine(KeyUtil.lang("gregtech.multiblock.turbine.rotor_speed",
                                getSpeedFormat(maxSpeed, speed), speed, maxSpeed));
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));

        // Rotor durability bar — uses DoubleSyncValue directly as progress source
        bars.add(barTest -> barTest
                .value(durabilityProgressValue)
                .texture(GTGuiTextures.PROGRESS_BAR_TURBINE_ROTOR_DURABILITY)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (efficiencyValue.getIntValue() <= 0) {
                            t.addLine(IKey.lang("gregtech.multiblock.turbine.no_rotor"));
                        } else {
                            int durability = durabilityValue.getIntValue();
                            // TODO working dynamic color substitutions into IKey.lang
                            if (durability > 40) {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.high",
                                        durability));
                            } else if (durability > MIN_DURABILITY_TO_WARN) {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.medium",
                                        durability));
                            } else {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.low",
                                        durability));
                            }
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    private @NotNull TextFormatting getSpeedFormat(int maxSpeed, int speed) {
        float percent = maxSpeed == 0 ? 0 : 1.0f * speed / maxSpeed;

        if (percent < 0.4) {
            return TextFormatting.RED;
        } else if (percent < 0.8) {
            return TextFormatting.YELLOW;
        } else {
            return TextFormatting.GREEN;
        }
    }

    /**
     * @return an array of [rotor speed, rotor max speed]
     */
    private int[] getRotorSpeedData() {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder == null) {
            return new int[2];
        }
        return new int[] { rotorHolder.getRotorSpeed(), rotorHolder.getMaxRotorHolderSpeed() };
    }

    /**
     * @return an array of [fuel stored, fuel capacity]
     */
    private int[] getFuelAmount() {
        if (getInputFluidInventory() != null) {
            MultiblockFuelRecipeLogic recipeLogic = (MultiblockFuelRecipeLogic) recipeMapWorkable;
            if (recipeLogic.getInputFluidStack() != null) {
                FluidStack testStack = recipeLogic.getInputFluidStack().copy();
                testStack.amount = Integer.MAX_VALUE;
                return getTotalFluidAmount(testStack, getInputFluidInventory());
            }
        }
        return new int[2];
    }

    // endregion
}
