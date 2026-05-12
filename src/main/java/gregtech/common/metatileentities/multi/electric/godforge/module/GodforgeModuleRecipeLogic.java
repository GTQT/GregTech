package gregtech.common.metatileentities.multi.electric.godforge.module;

import java.math.BigInteger;
import java.util.UUID;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.RecipeSlot;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

public class GodforgeModuleRecipeLogic extends MultiblockRecipeLogic {

    private static final int HEAT_OVERCLOCK_TEMPERATURE =
            OverclockingLogic.COIL_EUT_DISCOUNT_TEMPERATURE * 2;

    private final MTEBaseModule module;

    public GodforgeModuleRecipeLogic(MTEBaseModule module) {
        super(module);
        this.module = module;
    }

    @Override
    public boolean prepareRecipe(Recipe recipe, IItemHandlerModifiable inputInventory,
                                 IMultipleTankHandler inputFluidInventory) {
        return super.prepareRecipe(applyGodforgeModifiers(recipe), inputInventory, inputFluidInventory);
    }

    @Override
    protected boolean prepareRecipeDistinct(Recipe recipe) {
        return super.prepareRecipeDistinct(applyGodforgeModifiers(recipe));
    }

    @Override
    protected boolean setupSlotWithRecipe(@NotNull RecipeSlot slot, @NotNull Recipe recipe,
                                          @NotNull RecipeMap<?> recipeMap,
                                          @NotNull IItemHandlerModifiable importInventory,
                                          @NotNull IMultipleTankHandler importFluids,
                                          long remainingPower, int maxParallelBudget) {
        return super.setupSlotWithRecipe(slot, applyGodforgeModifiers(recipe), recipeMap, importInventory, importFluids,
                remainingPower, maxParallelBudget);
    }

    private Recipe applyGodforgeModifiers(@NotNull Recipe recipe) {
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) {
            return recipe;
        }

        long eut = applyEnergyMultiplier(recipe.getEUt(), getEnergyMultiplier(recipe.propertyStorage()));
        int duration = applyDurationMultiplier(recipe.getDuration(), module.getSpeedBonus());
        if (eut == recipe.getEUt() && duration == recipe.getDuration()) {
            return recipe;
        }

        Recipe modified = new RecipeBuilder<>(recipe, recipeMap)
                .EUt(eut)
                .duration(duration)
                .build()
                .getResult();
        return modified == null ? recipe : modified;
    }

    private long applyEnergyMultiplier(long eut, double multiplier) {
        if (eut == 0 || multiplier <= 0) {
            return eut;
        }

        long modified = Math.round(eut * multiplier);
        if (eut > 0) {
            return Math.max(1L, modified);
        }
        return Math.min(-1L, modified);
    }

    private int applyDurationMultiplier(int duration, double multiplier) {
        if (duration <= 0 || multiplier <= 0) {
            return duration;
        }
        return Math.max(1, (int) Math.round(duration * multiplier));
    }

    private double getEnergyMultiplier(@NotNull RecipePropertyStorage storage) {
        double multiplier = module.getEnergyDiscount() > 0 ? module.getEnergyDiscount() : 1;
        int recipeHeat = getRecipeHeat(storage);
        if (recipeHeat >= OverclockingLogic.COIL_EUT_DISCOUNT_TEMPERATURE) {
            int machineHeat = Math.max(recipeHeat, module.getHeatForOC());
            int discounts = Math.max(0,
                    (machineHeat - recipeHeat) / OverclockingLogic.COIL_EUT_DISCOUNT_TEMPERATURE);
            if (discounts > 0) {
                multiplier *= Math.pow(module.getHeatEnergyDiscount(), discounts);
            }
        }
        return multiplier;
    }

    @Override
    protected long getEnergyStored() {
        UUID uuid = module.getOwnerGT();
        if (uuid == null) return 0;
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return 0;
        BigInteger eu = service.getView(uuid).getStored();
        long clamped = eu.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        return Math.max(clamped, 0);
    }

    @Override
    protected long getEnergyCapacity() {
        return Long.MAX_VALUE;
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        recipeEUt = appendEfficiency(recipeEUt);
        if (!consumesEnergy()) return true;

        UUID uuid = module.getOwnerGT();
        if (uuid == null) return false;

        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return false;

        if (simulate) {
            WirelessNetworkView view = service.getView(uuid);
            return view.getStored().compareTo(BigInteger.valueOf(recipeEUt)) >= 0;
        }

        TransferResult result = service.extract(uuid, recipeEUt, TransferContext.MACHINE);
        if (result.isSuccess()) {
            module.addToPowerTally(BigInteger.valueOf(result.getAmountLong()));
            return true;
        }
        return false;
    }

    @Override
    public long getMaxVoltage() {
        return module.getProcessingVoltage();
    }

    @Override
    public long getMaximumOverclockVoltage() {
        return module.getProcessingVoltage();
    }

    @Override
    public int getParallelLimit() {
        return module.getActualParallel();
    }

    @Override
    protected double getOverclockingDurationFactor() {
        double divisor = module.getOverclockTimeFactor();
        return divisor > 0 ? 1.0 / divisor : super.getOverclockingDurationFactor();
    }

    @Override
    protected void runOverclockingLogic(@NotNull OCParams ocParams, @NotNull OCResult ocResult,
                                        @NotNull RecipePropertyStorage propertyStorage, long maxVoltage) {
        runGodforgeOverclocking(ocParams, ocResult, maxVoltage, getRecipeHeat(propertyStorage));
    }

    private void runGodforgeOverclocking(@NotNull OCParams params, @NotNull OCResult result, long maxVoltage,
                                         int recipeHeat) {
        double duration = params.duration();
        double eut = params.eut();
        int ocAmount = params.ocAmount();
        double parallel = 1;
        int parallelIterAmount = 0;
        boolean shouldParallel = false;
        int heatOverclocks = getHeatOverclockAmount(recipeHeat);
        double voltageFactor = getOverclockingVoltageFactor();

        while (ocAmount-- > 0) {
            double potentialEUt = eut * voltageFactor;
            if (potentialEUt > maxVoltage || potentialEUt < 1) {
                break;
            }
            eut = potentialEUt;

            double durationFactor = heatOverclocks-- > 0 ?
                    OverclockingLogic.PERFECT_DURATION_FACTOR : getOverclockingDurationFactor();

            if (shouldParallel) {
                parallel /= durationFactor;
                parallelIterAmount++;
            } else {
                double potentialDuration = duration * durationFactor;
                if (potentialDuration < 1) {
                    parallel /= durationFactor;
                    parallelIterAmount++;
                    shouldParallel = true;
                } else {
                    duration = potentialDuration;
                }
            }
        }

        result.init((long) (eut / Math.pow(voltageFactor, parallelIterAmount)),
                Math.max(1, (int) duration),
                Math.max(1, (int) parallel),
                (long) eut);
    }

    private int getHeatOverclockAmount(int recipeHeat) {
        if (recipeHeat <= 0) {
            return 0;
        }
        int machineHeat = Math.max(recipeHeat, module.getHeatForOC());
        return Math.max(0, (machineHeat - recipeHeat) / HEAT_OVERCLOCK_TEMPERATURE);
    }

    @Override
    protected void setupRecipe(@NotNull Recipe recipe) {
        module.setCurrentRecipeHeat(getRecipeHeat(recipe.propertyStorage()));
        super.setupRecipe(recipe);
    }

    @Override
    protected void completeRecipe() {
        module.addToRecipeTally(Math.max(1, getParallelRecipesPerformed()));
        super.completeRecipe();
    }

    @Override
    protected void onCrossRecipeSlotsCompleted(int completedParallel) {
        module.addToRecipeTally(Math.max(1, completedParallel));
        super.onCrossRecipeSlotsCompleted(completedParallel);
    }

    private int getRecipeHeat(@NotNull RecipePropertyStorage storage) {
        return storage.get(TemperatureProperty.getInstance(), 0);
    }

    // ==================== Recipe Map Switch Support ====================

    /**
     * Called when the module's recipe map changes (e.g. furnace mode toggle).
     * Clears cached recipe references so that new recipe searches use the updated RecipeMap.
     * Does NOT stop currently running recipes in the scheduler — they are allowed to complete
     * naturally. The refillScheduler() path will automatically use the new RecipeMap for any
     * subsequent recipe searches.
     */
    public void invalidateForRecipeMapChange() {
        this.previousRecipe = null;
        this.lastCrossRecipe = null;
        this.invalidInputsForRecipes = false;
        this.isOutputsFull = false;
    }

    @Override
    public IEnergyContainer getEnergyContainer() {
        return IEnergyContainer.DEFAULT;
    }
}
