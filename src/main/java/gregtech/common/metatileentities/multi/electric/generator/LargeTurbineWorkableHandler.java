package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.capability.IRotorHolder;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class LargeTurbineWorkableHandler extends MultiblockFuelRecipeLogic {

    private final int BASE_EU_OUTPUT;
    private long excessVoltage;

    /**
     * @deprecated Use the general-purpose constructor with {@link IRecipeMapHolder} + {@link gregtech.api.capability.IGenerator}.
     */
    @Deprecated
    public LargeTurbineWorkableHandler(FuelMultiblockController metaTileEntity, int tier) {
        super(metaTileEntity);
        this.BASE_EU_OUTPUT = (int) GTValues.V[tier] * 2;
    }

    /**
     * General-purpose constructor for any MetaTileEntity that provides rotor holder access.
     */
    public <T extends MetaTileEntity & IRecipeMapHolder & gregtech.api.capability.IGenerator> LargeTurbineWorkableHandler(
            T metaTileEntity, RecipeMap<?> recipeMap, int tier) {
        super(metaTileEntity, recipeMap);
        this.BASE_EU_OUTPUT = (int) GTValues.V[tier] * 2;
    }

    /**
     * @return the first rotor holder from the multiblock's abilities, or null if none
     */
    @Nullable
    private IRotorHolder getRotorHolder() {
        MultiblockControllerBase controller = (MultiblockControllerBase) getMetaTileEntity();
        List<IRotorHolder> abilities = controller.getAbilities(MultiblockAbility.ROTOR_HOLDER);
        return abilities.isEmpty() ? null : abilities.get(0);
    }

    @Override
    protected void updateRecipeProgress() {
        if (canRecipeProgress) {
            drawEnergy(recipeEUt, false);
            if (++progressTime > maxProgressTime) {
                completeRecipe();
            }
        }
    }

    public FluidStack getInputFluidStack() {
        if (previousRecipe == null) {
            Recipe recipe = super.findRecipe(Integer.MAX_VALUE, getInputInventory(), getInputTank());
            return recipe == null ? null : getInputTank().drain(
                    new FluidStack(recipe.getFluidInputs().get(0).getInputFluidStack().getFluid(), Integer.MAX_VALUE),
                    false);
        }
        FluidStack fuelStack = previousRecipe.getFluidInputs().get(0).getInputFluidStack();
        return getInputTank().drain(new FluidStack(fuelStack.getFluid(), Integer.MAX_VALUE), false);
    }

    @Override
    public long getMaxVoltage() {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder != null && rotorHolder.hasRotor())
            return (long) BASE_EU_OUTPUT * rotorHolder.getTotalPower() / 100;
        return 0;
    }

    @Override
    protected long boostProduction(long production) {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder != null && rotorHolder.hasRotor()) {
            int maxSpeed = rotorHolder.getMaxRotorHolderSpeed();
            int currentSpeed = rotorHolder.getRotorSpeed();
            if (currentSpeed >= maxSpeed)
                return production;
            if (maxSpeed == 0) return production;
            return (long) (production * Math.pow(1.0 * currentSpeed / maxSpeed, 2));
        }
        return 0;
    }

    private int getParallel(@NotNull Recipe recipe, double totalHolderEfficiencyCoefficient, long turbineMaxVoltage) {
        long recipeEUt = Math.abs(recipe.getEUt());
        if (recipeEUt == 0) return 0;

        long numerator = turbineMaxVoltage - this.excessVoltage;
        if (numerator <= 0) return 0;

        return (int) Math.ceil(numerator / (recipeEUt * totalHolderEfficiencyCoefficient));
    }

    private boolean canDoRecipeWithParallel(Recipe recipe) {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder == null || !rotorHolder.hasRotor())
            return false;

        double totalHolderEfficiencyCoefficient = rotorHolder.getTotalEfficiency() / 100.0;
        long turbineMaxVoltage = getMaxVoltage();
        int parallel = getParallel(recipe, totalHolderEfficiencyCoefficient, turbineMaxVoltage);

        if (parallel <= 0) return false;

        FluidStack recipeFluidStack = recipe.getFluidInputs().get(0).getInputFluidStack();
        FluidStack inputFluid = getInputTank().drain(
                new FluidStack(recipeFluidStack.getFluid(), Integer.MAX_VALUE),
                false);
        return inputFluid != null && inputFluid.amount >= recipeFluidStack.amount * parallel;
    }

    @Override
    protected boolean checkPreviousRecipe() {
        return super.checkPreviousRecipe() && canDoRecipeWithParallel(this.previousRecipe);
    }

    @Override
    protected @Nullable Recipe findRecipe(long maxVoltage, IItemHandlerModifiable inputs,
                                          IMultipleTankHandler fluidInputs) {
        RecipeMap<?> map = getRecipeMap();
        if (map == null || !isRecipeMapValid(map)) {
            return null;
        }

        final List<ItemStack> items = GTUtility.itemHandlerToList(inputs).stream().filter(s -> !s.isEmpty()).collect(
                Collectors.toList());
        final List<FluidStack> fluids = GTUtility.fluidHandlerToList(fluidInputs).stream()
                .filter(f -> f != null && f.amount != 0)
                .collect(Collectors.toList());

        return map.find(items, fluids, recipe -> {
            // 修改 2: 使用绝对值比较电压
            if (Math.abs(recipe.getEUt()) > maxVoltage) return false;
            return recipe.matches(false, inputs, fluidInputs) && this.canDoRecipeWithParallel(recipe);
        });
    }

    @Override
    public boolean prepareRecipe(Recipe recipe) {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder == null || !rotorHolder.hasRotor())
            return false;

        long turbineMaxVoltage = getMaxVoltage();
        FluidStack recipeFluidStack = recipe.getFluidInputs().get(0).getInputFluidStack();
        int parallel = 0;

        if (this.excessVoltage >= turbineMaxVoltage) {
            this.excessVoltage -= turbineMaxVoltage;
        } else {
            double holderEfficiency = rotorHolder.getTotalEfficiency() / 100.0;
            parallel = getParallel(recipe, holderEfficiency, turbineMaxVoltage);

            if (parallel <= 0) return false;

            // Refresh fluid state before consumption
            IRecipeMapHolder holder = (IRecipeMapHolder) getMetaTileEntity();
            holder.refreshAllBeforeConsumption();

            FluidStack inputFluid = getInputFluidStack();
            if (inputFluid == null || inputFluid.amount < recipeFluidStack.amount * parallel) {
                return false;
            }

            double producedVoltage = (long) parallel * Math.abs(recipe.getEUt()) * holderEfficiency;
            this.excessVoltage += (long) (producedVoltage - turbineMaxVoltage);
        }

        RecipeBuilder<?> recipeBuilder = getRecipeMap().recipeBuilder();
        recipeBuilder.append(recipe, parallel, false)
                .EUt(turbineMaxVoltage);
        applyParallelBonus(recipeBuilder);
        Recipe builtRecipe = recipeBuilder.build().getResult();

        if (builtRecipe != null) {
            builtRecipe = setupAndConsumeRecipeInputs(builtRecipe, getInputInventory());
            if (builtRecipe != null) {
                setupRecipe(builtRecipe);
                return true;
            }
        }
        return false;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        excessVoltage = 0;
    }

    public void updateTanks() {
        MultiblockControllerBase controller = (MultiblockControllerBase) getMetaTileEntity();
        List<IFluidHandler> tanks = controller.getNotifiedFluidInputList();
        for (IFluidTank tank : controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS)) {
            tanks.add((IFluidHandler) tank);
        }
    }
}
