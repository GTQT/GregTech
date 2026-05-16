package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IGenerator;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.mui.sync.FixedIntArraySyncValue;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for parametric (variant-based) fuel multiblocks.
 * Combines the variant system of {@link ParametricMultiblockController} with fuel-producing
 * recipe logic, analogous to how {@link FuelMultiblockController} extends
 * {@link RecipeMapMultiblockController}.
 *
 * <p>This enables variant-based fuel multiblocks (e.g., LargeCombustionEngine variants)
 * to be consolidated into a single MTE ID while retaining full generator behavior.
 *
 * @param <V> the variant value type
 * @see ParametricRecipeMapController
 * @see FuelMultiblockController
 * @see IGenerator
 */

public abstract class ParametricFuelController<V extends Enum<V>> extends ParametricRecipeMapController<V>
        implements IGenerator, ITieredMetaTileEntity, IDataInfoProvider {

    protected int tier;

    protected ParametricFuelController(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> variantRegistry,
                                       @NotNull RecipeMap<?> recipeMap,
                                       int tier) {
        super(metaTileEntityId, variantRegistry, recipeMap);
        this.tier = tier;
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[tier]);
    }

    /**
     * @deprecated Prefer passing a {@link ParametricVariantRegistry}. This constructor keeps enum-backed
     *             parametric multiblocks source-compatible while the base class moves to open registries.
     */
    @Deprecated
    protected ParametricFuelController(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull Class<V> variantClass,
                                       @NotNull V defaultVariant,
                                       @NotNull RecipeMap<?> recipeMap,
                                       int tier) {
        super(metaTileEntityId, variantClass, defaultVariant, recipeMap);
        this.tier = tier;
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[tier]);
    }

    // region Recipe Logic

    @Override
    @NotNull
    protected MultiblockRecipeLogic createWorkable() {
        return new MultiblockFuelRecipeLogic(this, getRecipeMapForVariant(getVariant()));
    }

    @Override
    protected void initializeAbilities() {
        super.initializeAbilities();
        List<IEnergyContainer> outputEnergy = new ArrayList<>(getAbilities(MultiblockAbility.OUTPUT_ENERGY));
        outputEnergy.addAll(getAbilities(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY));
        outputEnergy.addAll(getAbilities(MultiblockAbility.OUTPUT_LASER));
        abilityManager.setEnergyContainer(new EnergyContainerList(outputEnergy));
    }

    // endregion

    // region IGenerator

    @Override
    public boolean isDynamoFull() {
        return getEnergyContainer().getEnergyCanBeInserted() < recipeMapWorkable.getRecipeEUt();
    }

    @Override
    public boolean isEnergyOverFlow() {
        return recipeMapWorkable.isOverflowMode();
    }

    @Override
    public void setEnergyOverFlowMode(boolean enable) {
        recipeMapWorkable.setOverflowMode(enable);
    }

    // endregion

    // region Exhaust Gas

    /**
     * Exhaust gas type for muffler output. Subclasses override to specify gas type.
     */
    public enum gasType {

        NONE(null),
        LOW(Materials.ExhaustGas),
        MEDIUM(Materials.HighPressureExhaustGas),
        HIGH(Materials.SupercriticalExhaustGas);

        final Material exhaustGas;

        gasType(Material exhaustGas) {
            this.exhaustGas = exhaustGas;
        }

        public Material getExhaustGas() {
            return exhaustGas;
        }
    }

    /**
     * @return the exhaust gas type for this fuel multiblock. Override in subclasses.
     */
    public gasType getGasType() {
        return gasType.NONE;
    }

    /**
     * Outputs recovery fluid through muffler hatches based on gas type.
     */
    public void outputRecoveryFluid(int progress) {
        if (getGasType() == gasType.NONE) return;
        for (IMufflerHatch muffler : getAbilities(MultiblockAbility.MUFFLER_HATCH)) {
            if (muffler.mufflerWaste()) {
                muffler.recoverFluidsTable(getGasType().getExhaustGas().getFluid(5 * progress));
            }
        }
    }

    // endregion

    // region ITieredMetaTileEntity

    @Override
    public int getTier() {
        return tier;
    }

    // endregion

    // region Display

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        MultiblockFuelRecipeLogic recipeLogic = (MultiblockFuelRecipeLogic) recipeMapWorkable;

        builder.setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive())
                .addEnergyProductionLine(getMaxVoltage(), recipeLogic.getRecipeEUt())
                .addFuelNeededLine(recipeLogic.getRecipeFluidInputInfo(), recipeLogic.getPreviousRecipeDuration())
                .addWorkingStatusLine();
    }

    protected long getMaxVoltage() {
        IEnergyContainer energyContainer = recipeMapWorkable.getEnergyContainer();
        if (energyContainer != null && energyContainer.getEnergyCapacity() > 0) {
            return Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage());
        } else {
            return 0L;
        }
    }

    protected boolean isDynamoTierTooLow() {
        if (isStructureFormed()) {
            IEnergyContainer energyContainer = recipeMapWorkable.getEnergyContainer();
            if (energyContainer != null && energyContainer.getEnergyCapacity() > 0) {
                long maxVoltage = Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage());
                return maxVoltage < recipeMapWorkable.getRecipeEUt();
            }
        }
        return false;
    }

    /**
     * Creates a standard fuel tooltip for progress bar display.
     *
     * @param tooltip       the tooltip to populate
     * @param amounts       the sync value containing an array of [fuel stored, fuel capacity]
     * @param fuelNameValue the name of the fuel
     */
    protected void createFuelTooltip(@NotNull RichTooltip tooltip,
                                     @NotNull FixedIntArraySyncValue amounts,
                                     @NotNull StringSyncValue fuelNameValue) {
        if (isStructureFormed()) {
            Fluid fluid = fuelNameValue.getStringValue() == null ? null :
                    FluidRegistry.getFluid(fuelNameValue.getStringValue());
            if (fluid == null) {
                tooltip.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.fuel_none"));
            } else {
                tooltip.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.fuel_amount",
                        amounts.getValue(0), amounts.getValue(1),
                        fluid.getLocalizedName(new FluidStack(fluid, 1))));
            }
        } else {
            tooltip.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
        }
    }

    // endregion

    // region IDataInfoProvider

    @NotNull
    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();
        IEnergyContainer energy = getEnergyContainer();

        if (recipeMapWorkable.getMaxProgress() > 0) {
            list.add(new TextComponentTranslation("behavior.tricorder.workable_progress",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(recipeMapWorkable.getProgress() / 20))
                            .setStyle(new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getMaxProgress() / 20))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        }

        if (energy != null) {
            list.add(new TextComponentTranslation("behavior.tricorder.energy_container_storage",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(energy.getEnergyStored()))
                            .setStyle(new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(energy.getEnergyCapacity()))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        }

        if (!recipeMapWorkable.consumesEnergy()) {
            list.add(new TextComponentTranslation("behavior.tricorder.workable_production",
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(Math.abs(recipeMapWorkable.getInfoProviderEUt())))
                            .setStyle(new Style().setColor(TextFormatting.RED)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getInfoProviderEUt() == 0 ? 0 : 1))
                            .setStyle(new Style().setColor(TextFormatting.RED))));

            if (energy != null) {
                list.add(new TextComponentTranslation("behavior.tricorder.multiblock_energy_output",
                        new TextComponentTranslation(TextFormattingUtil.formatNumbers(energy.getOutputVoltage()))
                                .setStyle(new Style().setColor(TextFormatting.YELLOW)),
                        new TextComponentTranslation(
                                GTValues.VN[GTUtility.getTierByVoltage(energy.getOutputVoltage())])
                                .setStyle(new Style().setColor(TextFormatting.YELLOW))));
            }
        }

        if (ConfigHolder.machines.enableMaintenance && hasMaintenanceMechanics()) {
            list.add(new TextComponentTranslation("behavior.tricorder.multiblock_maintenance",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(getNumMaintenanceProblems()))
                            .setStyle(new Style().setColor(TextFormatting.RED))));
        }

        return list;
    }

    // endregion

    // region Fluid Utilities

    protected int[] getTotalFluidAmount(net.minecraftforge.fluids.FluidStack testStack,
                                        IMultipleTankHandler multiTank) {
        int fluidAmount = 0;
        int fluidCapacity = 0;
        for (var tank : multiTank) {
            if (tank != null) {
                net.minecraftforge.fluids.FluidStack drainStack = tank.drain(testStack, false);
                if (drainStack != null && drainStack.amount > 0) {
                    fluidAmount += drainStack.amount;
                    fluidCapacity += tank.getCapacity();
                }
            }
        }
        return new int[] { fluidAmount, fluidCapacity };
    }

    // endregion
}
