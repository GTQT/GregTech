package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ParametricFuelController;
import gregtech.api.metatileentity.multiblock.ParametricVariantRegistries;
import gregtech.api.metatileentity.multiblock.ParametricVariantRegistry;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.sync.FixedIntArraySyncValue;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

public class MetaTileEntityLargeCombustionEngine extends ParametricFuelController<LargeCombustionEngineType>
        implements ProgressBarMultiblock {

    private static final ParametricVariantRegistry<LargeCombustionEngineType> VARIANTS =
            ParametricVariantRegistries.enumRegistry("gregtech", LargeCombustionEngineType.class,
                    LargeCombustionEngineType.REGULAR);

    @Override
    @NotNull
    protected String getVariantTranslationPrefix() {
        return "gregtech.machine";
    }

    @Override
    @NotNull
    protected String getVariantName(@NotNull LargeCombustionEngineType variant) {
        return variant.getName();
    }

    private static BlockPatternTemplate buildTemplate(LargeCombustionEngineType type) {
        return DeclarativePatternBuilder.start()
                .aisle("XXX", "XDX", "XXX")
                .aisle("XCX", "CGC", "XCX")
                .aisle("XCX", "CGC", "XCX")
                .aisle("AAA", "AYA", "AAA")
                .where('X', states(type.getCasingState()))
                .where('G', states(type.getGearboxState()))
                .where('D', metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.OUTPUT_ENERGY).stream()
                        .filter(mte -> {
                            IEnergyContainer container = mte
                                    .getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null);
                            return container != null &&
                                    container.getOutputVoltage() * container.getOutputAmperage() >= GTValues.V[type.getTier()];
                        })
                        .toArray(MetaTileEntity[]::new))
                        .addTooltip("gregtech.multiblock.pattern.error.limited.1", GTValues.VN[type.getTier()]))
                .where('A', states(type.getIntakeState()).addTooltips("gregtech.multiblock.pattern.clear_amount_1"))
                .where('Y', selfPredicateByClass(MetaTileEntityLargeCombustionEngine.class))
                .casing('C', CasingDefinition.simple(type.getCasingState(),
                        "gregtech.machine.casing." + (type.isExtreme() ? "tungstensteel_robust" : "titanium_stable")))
                    .applyPreset(HatchPresets.MUFFLER_IO)
                .buildTemplate();
    }

    private boolean boostAllowed;

    // Primary constructor: single-ID with variant
    public MetaTileEntityLargeCombustionEngine(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, VARIANTS, RecipeMaps.COMBUSTION_GENERATOR_FUELS,
                LargeCombustionEngineType.REGULAR.getTier());
    }

    // Variant-specific constructor for direct instantiation
    public MetaTileEntityLargeCombustionEngine(ResourceLocation metaTileEntityId,
                                               LargeCombustionEngineType engineType) {
        super(metaTileEntityId, VARIANTS, RecipeMaps.COMBUSTION_GENERATOR_FUELS, engineType.getTier());
        setVariant(engineType);
    }

    @Override
    @NotNull
    protected MultiblockRecipeLogic createWorkable() {
        return new LargeCombustionEngineWorkableHandler(this, getVariant().isExtreme());
    }

    @Override
    @NotNull
    protected RecipeMap<?> getRecipeMapForVariant(@NotNull LargeCombustionEngineType variant) {
        return RecipeMaps.COMBUSTION_GENERATOR_FUELS;
    }

    @Override
    protected void onVariantChanged() {
        LargeCombustionEngineType type = getVariant();
        this.tier = type.getTier();
        this.recipeMapWorkable = createWorkable();
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[tier]);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeCombustionEngine(metaTileEntityId, getVariant());
    }

    private boolean isExtreme() {
        return getVariant().isExtreme();
    }

    // region Display

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        var recipeLogic = (LargeCombustionEngineWorkableHandler) recipeMapWorkable;

        builder.setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive() && !isDynamoFull());

        if (isExtreme()) {
            builder.addEnergyProductionLine(GTValues.V[tier + 1], recipeLogic.getRecipeEUt());
        } else {
            builder.addEnergyProductionAmpsLine(GTValues.V[tier] * 3, 3);
        }

        builder.addFuelNeededLine(recipeLogic.getRecipeFluidInputInfo(), recipeLogic.getPreviousRecipeDuration())
                .addCustom((richText, syncer) -> {
                    if (isStructureFormed() && syncer.syncBoolean(recipeLogic.isOxygenBoosted)) {
                        String key = isExtreme() ?
                                "gregtech.multiblock.large_combustion_engine.liquid_oxygen_boosted" :
                                "gregtech.multiblock.large_combustion_engine.oxygen_boosted";
                        richText.add(KeyUtil.lang(TextFormatting.AQUA, key));
                    }
                })
                .addWorkingStatusLine();
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        var recipeLogic = (LargeCombustionEngineWorkableHandler) recipeMapWorkable;

        builder.addCustom((keyList, syncer) -> {
            if (!isStructureFormed()) return;

            if (syncer.syncBoolean(checkIntakesObstructed())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.large_combustion_engine.obstructed"));
                keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.large_combustion_engine.obstructed.desc"));
            }

            if (syncer.syncBoolean(!recipeLogic.checkLubricant())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.large_combustion_engine.no_lubricant"));
            }
        });
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        LargeCombustionEngineType type = getVariantFromStack(stack);
        int variantTier = type.getTier();
        tooltip.add(I18n.format("gregtech.universal.tooltip.base_production_eut", GTValues.V[variantTier]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.uses_per_hour_lubricant", 1000));
        if (type.isExtreme()) {
            tooltip.add(I18n.format("gregtech.machine.large_combustion_engine.tooltip.boost_extreme",
                    GTValues.V[variantTier] * 4));
        } else {
            tooltip.add(I18n.format("gregtech.machine.large_combustion_engine.tooltip.boost_regular",
                    GTValues.V[variantTier] * 3));
        }
    }

    // endregion

    // region Structure

    @Override
    @NotNull
    protected BlockPatternTemplate buildStructureTemplate(@NotNull LargeCombustionEngineType variantValue) {
        return buildTemplate(variantValue);
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
        return true;
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || checkIntakesObstructed();
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        IEnergyContainer energyContainer = getEnergyContainer();
        this.boostAllowed = energyContainer != null && energyContainer.getOutputVoltage() >= GTValues.V[this.tier + 1];
    }

    private boolean checkIntakesObstructed() {
        for (int left = -1; left <= 1; left++) {
            for (int up = -1; up <= 1; up++) {
                if (left == 0 && up == 0) {
                    continue;
                }

                final BlockPos checkPos = RelativeDirection.offsetPos(
                        getPos(), getFrontFacing(), getUpwardsFacing(), isFlipped(), up, left, 1);
                final IBlockState state = getWorld().getBlockState(checkPos);
                if (!state.getBlock().isAir(state, getWorld(), checkPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    // endregion

    // region Config

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    public boolean isBoostAllowed() {
        return boostAllowed;
    }

    @Override
    public gasType getGasType() {
        return gasType.LOW;
    }

    @Override
    public double getPollutionAmount() {
        return isExtreme() ? 0.025 : 0.02;
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
        syncManager.syncValue("fuel_amount", fuelValue);
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
        syncManager.syncValue("fuel_name", fuelNameValue);

        // Lubricant and oxygen amount sync (int array for tooltip raw values)
        FixedIntArraySyncValue lubricantValue = new FixedIntArraySyncValue(this::getLubricantAmount);
        syncManager.syncValue("lubricant_amount", lubricantValue);
        FixedIntArraySyncValue oxygenValue = new FixedIntArraySyncValue(this::getOxygenAmount);
        syncManager.syncValue("oxygen_amount", oxygenValue);
        BooleanSyncValue boostValue = new BooleanSyncValue(this::isBoostAllowed);
        syncManager.syncValue("boost_allowed", boostValue);

        // Progress DoubleSyncValues for reliable client rendering
        DoubleSyncValue fuelProgressValue = new DoubleSyncValue(() -> {
            int[] fuel = getFuelAmount();
            return fuel[1] == 0 ? 0 : 1.0 * fuel[0] / fuel[1];
        });
        syncManager.syncValue("fuel_progress", fuelProgressValue);

        DoubleSyncValue lubricantProgressValue = new DoubleSyncValue(() -> {
            int[] lub = getLubricantAmount();
            return lub[1] == 0 ? 0 : 1.0 * lub[0] / lub[1];
        });
        syncManager.syncValue("lubricant_progress", lubricantProgressValue);

        DoubleSyncValue oxygenProgressValue = new DoubleSyncValue(() -> {
            int[] oxy = getOxygenAmount();
            return oxy[1] == 0 ? 0 : 1.0 * oxy[0] / oxy[1];
        });
        syncManager.syncValue("oxygen_progress", oxygenProgressValue);

        // Fuel bar — uses DoubleSyncValue directly as progress source
        bars.add(barTest -> barTest
                .value(fuelProgressValue)
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_FUEL)
                .tooltipBuilder(t -> createFuelTooltip(t, fuelValue, fuelNameValue)));

        // Lubricant bar — uses DoubleSyncValue directly as progress source
        bars.add(barTest -> barTest
                .value(lubricantProgressValue)
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_LUBRICANT)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (lubricantValue.getValue(0) == 0) {
                            t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.no_lubricant"));
                        } else {
                            t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.lubricant_amount",
                                    lubricantValue.getValue(0), lubricantValue.getValue(1)));
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));

        // Oxygen bar — uses DoubleSyncValue directly as progress source
        bars.add(barTest -> barTest
                .value(oxygenProgressValue)
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_OXYGEN)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (boostValue.getBoolValue()) {
                            if (oxygenValue.getValue(0) == 0) {
                                t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.oxygen_none"));
                            } else if (isExtreme()) {
                                t.addLine(IKey.lang(
                                        "gregtech.multiblock.large_combustion_engine.liquid_oxygen_amount",
                                        oxygenValue.getValue(0), oxygenValue.getValue(1)));
                            } else {
                                t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.oxygen_amount",
                                        oxygenValue.getValue(0), oxygenValue.getValue(1)));
                            }
                        } else if (isExtreme()) {
                            t.addLine(IKey.lang(
                                    "gregtech.multiblock.large_combustion_engine.liquid_oxygen_boost_disallowed"));
                        } else {
                            t.addLine(IKey.lang(
                                    "gregtech.multiblock.large_combustion_engine.oxygen_boost_disallowed"));
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

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

    private int[] getLubricantAmount() {
        if (getInputFluidInventory() != null) {
            return getTotalFluidAmount(Materials.Lubricant.getFluid(Integer.MAX_VALUE),
                    getInputFluidInventory());
        }
        return new int[2];
    }

    private int[] getOxygenAmount() {
        if (getInputFluidInventory() != null) {
            if (isBoostAllowed()) {
                FluidStack oxygenStack = isExtreme() ?
                        Materials.Oxygen.getFluid(FluidStorageKeys.LIQUID, Integer.MAX_VALUE) :
                        Materials.Oxygen.getFluid(Integer.MAX_VALUE);
                return getTotalFluidAmount(oxygenStack, getInputFluidInventory());
            }
        }
        return new int[2];
    }

    // endregion

    // region Inner Workable Handler

    static class LargeCombustionEngineWorkableHandler extends MultiblockFuelRecipeLogic {

        boolean isOxygenBoosted = false;

        private final boolean isExtreme;
        private final int tier;

        private static final FluidStack OXYGEN_STACK = Materials.Oxygen.getFluid(20);
        private static final FluidStack LIQUID_OXYGEN_STACK = Materials.Oxygen.getFluid(FluidStorageKeys.LIQUID, 80);
        private static final FluidStack LUBRICANT_STACK = Materials.Lubricant.getFluid(1);

        public LargeCombustionEngineWorkableHandler(MetaTileEntityLargeCombustionEngine tileEntity,
                                                    boolean isExtreme) {
            super(tileEntity, RecipeMaps.COMBUSTION_GENERATOR_FUELS);
            this.isExtreme = isExtreme;
            this.tier = isExtreme ? GTValues.IV : GTValues.EV;
        }

        private MetaTileEntityLargeCombustionEngine getCombustionEngine() {
            return (MetaTileEntityLargeCombustionEngine) getMetaTileEntity();
        }

        @Override
        protected void updateRecipeProgress() {
            if (canRecipeProgress && drawEnergy(recipeEUt, true)) {
                drainLubricant();
                drainOxygen();
                drawEnergy(recipeEUt, false);

                if (++progressTime > maxProgressTime) {
                    completeRecipe();
                }
            }
        }

        protected void checkOxygen() {
            MetaTileEntityLargeCombustionEngine engine = getCombustionEngine();
            if (engine.isBoostAllowed()) {
                IMultipleTankHandler inputTank = engine.getInputFluidInventory();
                FluidStack boosterStack = isExtreme ? LIQUID_OXYGEN_STACK : OXYGEN_STACK;
                isOxygenBoosted = boosterStack.isFluidStackIdentical(inputTank.drain(boosterStack, false));
            }
        }

        protected void drainOxygen() {
            if (isOxygenBoosted && totalContinuousRunningTime % 20 == 0) {
                FluidStack boosterStack = isExtreme ? LIQUID_OXYGEN_STACK : OXYGEN_STACK;
                getCombustionEngine().getInputFluidInventory().drain(boosterStack, true);
            }
        }

        boolean checkLubricant() {
            IMultipleTankHandler inputTank = getCombustionEngine().getInputFluidInventory();
            if (LUBRICANT_STACK.isFluidStackIdentical(inputTank.drain(LUBRICANT_STACK, false))) {
                return true;
            } else {
                invalidate();
                return false;
            }
        }

        protected void drainLubricant() {
            if (totalContinuousRunningTime == 1 || totalContinuousRunningTime % 72 == 0) {
                IMultipleTankHandler inputTank = getCombustionEngine().getInputFluidInventory();
                inputTank.drain(LUBRICANT_STACK, true);
            }
        }

        @Override
        protected boolean shouldSearchForRecipes() {
            checkOxygen();
            return super.shouldSearchForRecipes() && checkLubricant();
        }

        @Override
        protected boolean canProgressRecipe() {
            return super.canProgressRecipe() && checkLubricant();
        }

        @Override
        public long getMaxVoltage() {
            if (isOxygenBoosted)
                return GTValues.V[tier] * 2;
            else
                return GTValues.V[tier];
        }

        @Override
        protected long boostProduction(long production) {
            if (isOxygenBoosted)
                if (!isExtreme)
                    return production * 3 / 2;
                else
                    return production * 2;
            return production;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            isOxygenBoosted = false;
        }
    }

    // endregion
}
