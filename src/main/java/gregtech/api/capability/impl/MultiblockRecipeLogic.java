package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleNotifiableHandler;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ParallelLogicType;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeIterator;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.ParallelLogic;
import gregtech.api.recipes.logic.RecipeSlot;
import gregtech.api.recipes.properties.RecipePropertyStorage;

import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Tuple;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import gregtech.api.util.GTQTUtility;
import gregtech.api.capability.IPatternBufferIsolatedHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static gregtech.api.recipes.logic.OverclockingLogic.subTickParallelOC;

public class MultiblockRecipeLogic extends AbstractRecipeLogic {

    // Used for distinct mode
    protected int lastRecipeIndex = 0;
    protected IItemHandlerModifiable currentDistinctInputBus;
    protected List<IItemHandlerModifiable> invalidatedInputList = new ArrayList<>();

    // Cross-recipe parallel scheduler (lazy initialized when ParallelLogicType.CROSS_RECIPE is active)
    @Nullable
    protected CrossRecipeParallelScheduler crossRecipeScheduler;
    protected boolean crossRecipeSchedulerActive = false;

    // Cached last successful recipe for "same recipe first" strategy
    @Nullable
    protected Recipe lastCrossRecipe;

    public MultiblockRecipeLogic(RecipeMapMultiblockController tileEntity) {
        super(tileEntity, tileEntity.recipeMap);
    }

    public MultiblockRecipeLogic(RecipeMapMultiblockController tileEntity, boolean hasPerfectOC) {
        super(tileEntity, tileEntity.recipeMap, hasPerfectOC);
    }

    /**
     * General-purpose constructor for any MetaTileEntity that implements {@link IRecipeMapHolder}.
     * This enables ParametricMultiblockController subclasses to use MultiblockRecipeLogic
     * without inheriting from RecipeMapMultiblockController.
     *
     * @param tileEntity the MTE that also implements IRecipeMapHolder
     * @param recipeMap  the recipe map to use
     */
    public <T extends MetaTileEntity & IRecipeMapHolder> MultiblockRecipeLogic(T tileEntity,
                                                                                RecipeMap<?> recipeMap) {
        super(tileEntity, recipeMap);
    }

    @Override
    public void update() {}

    public void updateWorkable() {
        super.update();
    }

    // ==================== Cross-Recipe Parallel Scheduler Integration ====================

    /**
     * @return the cross-recipe parallel scheduler, creating it lazily if needed
     */
    @NotNull
    protected CrossRecipeParallelScheduler getOrCreateScheduler() {
        if (crossRecipeScheduler == null) {
            crossRecipeScheduler = new CrossRecipeParallelScheduler(getParallelLimit());
        }
        crossRecipeScheduler.setMaxVoltage(getMaximumOverclockVoltage());
        crossRecipeScheduler.setTotalPowerBudget(getTotalPowerBudget());
        crossRecipeScheduler.setParallelLimit(getParallelLimit());
        return crossRecipeScheduler;
    }

    /**
     * Calculates the total power budget by directly summing each energy container's
     * voltage × amperage without any tier compression.
     * This gives the raw maximum EU/t throughput for parallel/power limiting.
     *
     * @return the total power budget in EU/t
     */
    protected long getTotalPowerBudget() {
        IEnergyContainer energyContainer = getEnergyContainer();
        if (energyContainer instanceof EnergyContainerList) {
            // Directly sum V×A from the EnergyContainerList's computed values
            long voltage;
            long amperage;
            if (energyContainer.getInputVoltage() > energyContainer.getOutputVoltage()) {
                voltage = energyContainer.getInputVoltage();
                amperage = energyContainer.getInputAmperage();
            } else {
                voltage = energyContainer.getOutputVoltage();
                amperage = energyContainer.getOutputAmperage();
            }
            return voltage * amperage;
        }
        // Single energy container: voltage × amperage
        long voltage = Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage());
        long amperage = Math.max(energyContainer.getInputAmperage(), energyContainer.getOutputAmperage());
        return voltage * Math.max(1, amperage);
    }

    /**
     * @return true if the cross-recipe parallel mode is active and being used
     */
    public boolean isCrossRecipeMode() {
        return getParallelLogicType() == ParallelLogicType.CROSS_RECIPE && getParallelLimit() > 1;
    }

    @Nullable
    public CrossRecipeParallelScheduler getCrossRecipeScheduler() {
        return crossRecipeScheduler;
    }

    @Override
    protected void updateRecipeProgress() {
        if (!isCrossRecipeMode()) {
            super.updateRecipeProgress();
            return;
        }

        // In CROSS_RECIPE mode, the scheduler manages all progress and energy
        CrossRecipeParallelScheduler scheduler = getOrCreateScheduler();

        int completedParallel = scheduler.tickSlots(
                getOutputInventory(),
                getOutputTank(),
                (amount, simulate) -> drawEnergy(amount, simulate)
        );
        if (completedParallel > 0) {
            onCrossRecipeSlotsCompleted(completedParallel);
        }

        if (scheduler.isHasNotEnoughEnergy()) {
            this.hasNotEnoughEnergy = true;
        } else {
            this.hasNotEnoughEnergy = false;
        }

        // If scheduler can accept more recipes (has remaining parallel budget), try to fill
        if (scheduler.canAcceptMoreRecipes()) {
            refillScheduler(scheduler);
        }

        // Update the parent's recipeEUt to reflect current total consumption (for display purposes)
        this.recipeEUt = scheduler.getTotalEnergyConsumption();
        this.parallelRecipesPerformed = scheduler.getTotalParallelCount();

        // Update active state based on whether any slots are still running
        if (!scheduler.hasActiveSlots()) {
            crossRecipeSchedulerActive = false;
            this.progressTime = 0;
            setMaxProgress(0);
            this.recipeEUt = 0;
            this.parallelRecipesPerformed = 0;
            this.wasActiveAndNeedsUpdate = true;
        } else {
            crossRecipeSchedulerActive = true;
            // Keep progressTime > 0 so the main loop continues calling updateRecipeProgress()
            this.progressTime = 1;
        }
    }

    /**
     * Dispatches scheduler slot filling to the appropriate method based on whether Distinct mode is active.
     *
     * <p><b>Note:</b> This method is called every tick when slots have remaining budget, including
     * immediately after a slot completes. If slots were created with a 1-tick offset (e.g., the primary
     * slot in Phase 1 and a remainder slot in Phase 2 or a subsequent refill), they will complete on
     * different ticks. When the larger slot completes first, the still-running smaller slot(s) consume
     * part of the parallel and power budgets, causing the newly created slot to receive fewer parallels.
     * This leads to gradual slot fragmentation over time. See {@link CrossRecipeParallelScheduler} class
     * Javadoc for full details on this known limitation.
     */
    protected void refillScheduler(@NotNull CrossRecipeParallelScheduler scheduler) {
        if (shouldUseDistinctInputBuses()) {
            fillSchedulerSlotsDistinct(scheduler);
        } else {
            fillSchedulerSlots(scheduler);
        }
    }

    /**
     * Fills scheduler with recipes from distinct buses using two-phase allocation.
     * Phase 1: Allocate parallel for each bus's recipe using base-EUt power budget.
     * Phase 2: Distribute surplus power proportionally and overclock all slots.
     */
    protected void fillSchedulerSlotsDistinct(@NotNull CrossRecipeParallelScheduler scheduler) {
        List<IItemHandlerModifiable> importInventory = getInputBuses();
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return;

        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        long remainingBasePower = totalPowerBudget;
        int remainingParallel = scheduler.getRemainingParallelBudget();
        List<SlotAllocation> allocations = new ArrayList<>();

        // Phase 1: Allocate parallel for all matching recipes (no overclock, no input consumption)
        for (int i = 0; i < importInventory.size(); i++) {
            if (remainingParallel <= 0 || remainingBasePower <= 0) break;

            IItemHandlerModifiable bus = importInventory.get(i);
            IMultipleTankHandler busFluidTank = getInputTank(bus);

            Recipe recipe = findRecipe(remainingBasePower, bus, busFluidTank);
            if (recipe == null || !checkRecipe(recipe)) continue;

            RecipeSlot slot = scheduler.acquireSlot();
            SlotAllocation alloc = allocateSlotParallel(slot, recipe, recipeMap, bus, busFluidTank,
                    remainingBasePower, remainingParallel);
            if (alloc != null) {
                allocations.add(alloc);
                remainingBasePower -= alloc.basePowerDemand;
                remainingParallel -= alloc.inputParallel;
                lastCrossRecipe = recipe;
            } else {
                scheduler.releaseSlot(slot);
            }
        }

        // Phase 2: Distribute surplus power proportionally and overclock all slots
        distributeAndOverclock(allocations, totalPowerBudget, scheduler);
    }

    /**
     * Fills scheduler with recipes from the combined input inventory using two-phase allocation.
     * <p>
     * Phase 1 (Parallel Allocation): Find all matching recipes and allocate parallel counts
     * using only base-EUt power budget (no overclock inflation).
     * <ol>
     *   <li>Phase 1a (fast path): Try cached recipe first</li>
     *   <li>Phase 1b (iterator): Use RecipeIterator to find all distinct matchable recipes</li>
     * </ol>
     * <p>
     * Phase 2 (Overclock + Start): Distribute surplus power proportionally among all allocated
     * slots based on their base power demand, then overclock and start each slot.
     *
     * @param scheduler the scheduler instance
     * @return number of slots successfully created
     */
    protected int fillSchedulerSlots(@NotNull CrossRecipeParallelScheduler scheduler) {
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return 0;

        IItemHandlerModifiable importInventory = getInputInventory();
        IMultipleTankHandler importFluids = getInputTank();

        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        long remainingBasePower = totalPowerBudget;
        int remainingParallel = scheduler.getRemainingParallelBudget();
        List<SlotAllocation> allocations = new ArrayList<>();

        // Phase 1a: Try cached recipe first (fast path, avoids full recipe search)
        if (lastCrossRecipe != null && remainingParallel > 0 && remainingBasePower > 0) {
            if (lastCrossRecipe.matches(false, importInventory, importFluids)) {
                RecipeSlot slot = scheduler.acquireSlot();
                SlotAllocation alloc = allocateSlotParallel(slot, lastCrossRecipe, recipeMap,
                        importInventory, importFluids, remainingBasePower, remainingParallel);
                if (alloc != null) {
                    allocations.add(alloc);
                    remainingBasePower -= alloc.basePowerDemand;
                    remainingParallel -= alloc.inputParallel;
                } else {
                    scheduler.releaseSlot(slot);
                }
            }
        }

        // Phase 1b: Use RecipeIterator to find all distinct recipes in one pass
        if (remainingParallel > 0 && remainingBasePower > 0) {
            List<ItemStack> items = GTUtility.itemHandlerToList(importInventory);
            List<FluidStack> fluids = GTUtility.fluidHandlerToList(importFluids);
            List<ItemStack> filteredItems = items.stream()
                    .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
            List<FluidStack> filteredFluids = fluids.stream()
                    .filter(f -> f != null && f.amount != 0).collect(java.util.stream.Collectors.toList());

            final long powerBudgetSnapshot = remainingBasePower;
            RecipeIterator iterator = recipeMap.findRecipeIterator(filteredItems, filteredFluids,
                    recipe -> recipe.getEUt() <= powerBudgetSnapshot &&
                            recipe.matches(false, importInventory, importFluids));

            if (iterator != null) {
                // Exclude the cached recipe since Phase 1a already handled it
                if (lastCrossRecipe != null) {
                    iterator.exclude(lastCrossRecipe);
                }

                while (iterator.hasNext() && remainingParallel > 0 && remainingBasePower > 0) {
                    Recipe recipe = iterator.next();
                    if (recipe == null || !checkRecipe(recipe)) continue;

                    RecipeSlot slot = scheduler.acquireSlot();
                    SlotAllocation alloc = allocateSlotParallel(slot, recipe, recipeMap,
                            importInventory, importFluids, remainingBasePower, remainingParallel);
                    if (alloc != null) {
                        allocations.add(alloc);
                        remainingBasePower -= alloc.basePowerDemand;
                        remainingParallel -= alloc.inputParallel;
                        lastCrossRecipe = recipe;
                        iterator.exclude(recipe);
                    } else {
                        scheduler.releaseSlot(slot);
                        iterator.exclude(recipe);
                    }
                }
            }
        }

        // Phase 2: Distribute surplus power proportionally and overclock all slots
        return distributeAndOverclock(allocations, totalPowerBudget, scheduler);
    }

    // ==================== Two-Phase Slot Setup (Parallel-first, then Overclock) ====================

    /**
     * Intermediate data holder for the two-phase slot setup.
     * Phase 1 ({@link #allocateSlotParallel}) populates this with the parallel allocation result.
     * Phase 2 ({@link #overclockAndStartSlot}) uses this to perform overclocking, consume inputs,
     * and start the slot.
     */
    protected static class SlotAllocation {

        final RecipeSlot slot;
        final Recipe trimmed;
        final RecipeMap<?> recipeMap;
        final IItemHandlerModifiable importInventory;
        final IMultipleTankHandler importFluids;

        // Phase 1 results
        final long baseEUt;
        final int baseDuration;
        int inputParallel;

        /** Base power demand = baseEUt × inputParallel (before overclock). */
        long basePowerDemand;

        SlotAllocation(@NotNull RecipeSlot slot, @NotNull Recipe trimmed, @NotNull RecipeMap<?> recipeMap,
                       @NotNull IItemHandlerModifiable importInventory, @NotNull IMultipleTankHandler importFluids,
                       long baseEUt, int baseDuration, int inputParallel) {
            this.slot = slot;
            this.trimmed = trimmed;
            this.recipeMap = recipeMap;
            this.importInventory = importInventory;
            this.importFluids = importFluids;
            this.baseEUt = baseEUt;
            this.baseDuration = baseDuration;
            this.inputParallel = inputParallel;
            this.basePowerDemand = baseEUt * inputParallel;
        }
    }

    /**
     * Phase 1: Allocates parallel count for a recipe without performing overclocking or consuming inputs.
     * The parallel is limited by available inputs, output space, and the base-EUt power budget
     * (remainingBasePower / baseEUt), ensuring no overclock inflation affects other slots' budgets.
     *
     * @param slot                the slot to allocate for
     * @param recipe              the recipe to use
     * @param recipeMap           the recipe map
     * @param importInventory     the input inventory
     * @param importFluids        the input fluid tanks
     * @param remainingBasePower  the remaining *base* power budget (sum of un-overclocked baseEUt × parallel)
     * @param maxParallelBudget   the remaining parallel budget from the scheduler
     * @return a SlotAllocation if successful, or null if the recipe cannot be allocated
     */
    @Nullable
    protected SlotAllocation allocateSlotParallel(@NotNull RecipeSlot slot,
                                                  @NotNull Recipe recipe,
                                                  @NotNull RecipeMap<?> recipeMap,
                                                  @NotNull IItemHandlerModifiable importInventory,
                                                  @NotNull IMultipleTankHandler importFluids,
                                                  long remainingBasePower,
                                                  int maxParallelBudget) {
        // Trim recipe outputs
        Recipe trimmed = Recipe.trimRecipeOutputs(recipe, recipeMap, metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());

        long baseEUt = trimmed.getEUt();
        int baseDuration = trimmed.getDuration();

        // Power check: remaining base power must be enough for at least 1 recipe
        if (remainingBasePower < baseEUt) return null;

        // Parallel is limited by: min(parallelBudget, remainingBasePower / baseEUt)
        int maxInputParallel = (int) Math.min(maxParallelBudget, remainingBasePower / Math.max(1, baseEUt));
        maxInputParallel = Math.max(1, maxInputParallel);

        int inputParallel = ParallelLogic.getMaxRecipeMultiplier(
                trimmed, importInventory, importFluids, maxInputParallel);
        if (inputParallel == 0) return null;

        // Limit by output space
        int outputParallel = ParallelLogic.limitByOutputMerging(trimmed, getOutputInventory(), getOutputTank(),
                inputParallel,
                metaTileEntity.canVoidRecipeItemOutputs(),
                metaTileEntity.canVoidRecipeFluidOutputs());
        if (outputParallel == 0) {
            this.isOutputsFull = true;
            return null;
        }
        inputParallel = outputParallel;

        return new SlotAllocation(slot, trimmed, recipeMap, importInventory, importFluids,
                baseEUt, baseDuration, inputParallel);
    }

    /**
     * Phase 2: Performs overclocking, consumes inputs, and starts the slot.
     * Called after all slots have been allocated in Phase 1, so each slot receives a fair
     * share of the overclock power budget.
     *
     * @param alloc          the allocation from Phase 1
     * @param ocPowerBudget  the power budget for this slot's overclocking (base demand + proportional share of surplus)
     * @return true if the slot was successfully started
     */
    protected boolean overclockAndStartSlot(@NotNull SlotAllocation alloc, long ocPowerBudget) {
        Recipe trimmed = alloc.trimmed;
        long baseEUt = alloc.baseEUt;
        int baseDuration = alloc.baseDuration;
        int inputParallel = alloc.inputParallel;

        // --- Overclock ---
        // OC tier count is based on single-recipe baseEUt vs getMaximumOverclockVoltage()
        // OC execution is bounded by the allocated power budget for this slot
        long totalBaseEUt = baseEUt * inputParallel;
        OCParams params = new OCParams();
        OCResult result = new OCResult();
        params.initialize(totalBaseEUt, baseDuration, getNumberOfOCs(baseEUt));
        modifyOverclockPre(params, trimmed.propertyStorage());

        if (params.ocAmount() <= 0) {
            result.init(params.eut(), params.duration());
        } else {
            runOverclockingLogic(params, result, trimmed.propertyStorage(), ocPowerBudget);
        }
        modifyOverclockPost(result, trimmed.propertyStorage());

        long overclockedEUt = result.eut();
        int overclockedDuration = result.duration();
        int subTickParallel = Math.max(1, result.parallel());

        // --- Calculate parallel and total operations ---
        long totalParallelBasis = (long) inputParallel * subTickParallel;

        long totalSlotEUt = result.parallelEUt() > 0 ? result.parallelEUt() : overclockedEUt;

        // Clamp totalSlotEUt to the allocated power budget
        if (totalSlotEUt > ocPowerBudget) {
            long perParallelEUt = Math.max(1, totalSlotEUt / inputParallel);
            inputParallel = (int) (ocPowerBudget / Math.max(1, perParallelEUt));
            if (inputParallel <= 0) return false;
            totalSlotEUt = perParallelEUt * inputParallel;
            totalParallelBasis = (long) inputParallel * subTickParallel;
        }

        // Re-check output space against total operations including sub-tick OC
        int outputParallel = alloc.inputParallel; // original from phase 1
        if (totalParallelBasis > outputParallel) {
            outputParallel = ParallelLogic.limitByOutputMerging(trimmed, getOutputInventory(), getOutputTank(),
                    (int) Math.min(Integer.MAX_VALUE, totalParallelBasis),
                    metaTileEntity.canVoidRecipeItemOutputs(),
                    metaTileEntity.canVoidRecipeFluidOutputs());
            if (outputParallel == 0) {
                this.isOutputsFull = true;
                return false;
            }
            totalParallelBasis = outputParallel;
            inputParallel = (int) Math.max(1, totalParallelBasis / subTickParallel);
            totalParallelBasis = (long) inputParallel * subTickParallel;
        }

        int finalParallel = inputParallel;

        // --- Batch processing ---
        int batchMultiplier = 1;
        if (isBatchEnable() && overclockedDuration <= 64 && overclockedDuration > 0) {
            int maxBatch = (int) Math.floor(128.0 / overclockedDuration);
            int bestBatch = 1;
            int lo = 1, hi = maxBatch;
            while (lo <= hi) {
                int mid = lo + (hi - lo) / 2;
                int neededParallel = (int) Math.min(Integer.MAX_VALUE, totalParallelBasis * mid);
                int available = ParallelLogic.getMaxRecipeMultiplier(
                        trimmed, alloc.importInventory, alloc.importFluids, neededParallel);
                if (available >= neededParallel) {
                    bestBatch = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (bestBatch > 1) {
                batchMultiplier = bestBatch;
            }
        }

        int totalOperations = (int) Math.min(Integer.MAX_VALUE, totalParallelBasis * batchMultiplier);
        int finalDuration = overclockedDuration * batchMultiplier;

        // --- Consume inputs ---
        if (!consumeRecipeInputs(trimmed, alloc.importInventory, alloc.importFluids, totalOperations)) {
            return false;
        }
        this.metaTileEntity.addNotifiedInput(alloc.importInventory);
        this.isOutputsFull = false;

        // --- Calculate outputs ---
        int recipeTier = GTUtility.getTierByVoltage(baseEUt);
        int machineTier = getOverclockForTier(getMaximumOverclockVoltage());
        List<ItemStack> itemOutputs = GTUtility.copyStackList(
                trimmed.getResultItemOutputs(recipeTier, machineTier, alloc.recipeMap));
        List<FluidStack> fluidOutputs = GTUtility.copyFluidList(
                trimmed.getResultFluidOutputs(recipeTier, machineTier, alloc.recipeMap));

        if (totalOperations > 1) {
            multiplyOutputs(itemOutputs, fluidOutputs, totalOperations);
        }

        // --- Start the slot ---
        String recipeDisplayName = getRecipeDisplayName(trimmed, alloc.recipeMap, itemOutputs, fluidOutputs);
        alloc.slot.startRecipe(trimmed, finalDuration, totalSlotEUt, itemOutputs, fluidOutputs, finalParallel,
                totalOperations, recipeDisplayName, alloc.basePowerDemand);
        return true;
    }

    /**
     * Distributes the surplus power budget proportionally among allocated slots based on
     * each slot's base power demand, then performs overclocking and starts each slot.
     *
     * @param allocations     the list of slot allocations from Phase 1
     * @param totalPowerBudget the total power budget available for all slots
     * @param scheduler       the scheduler (for releasing failed slots)
     * @return the number of slots successfully started
     */
    protected int distributeAndOverclock(@NotNull List<SlotAllocation> allocations,
                                         long totalPowerBudget,
                                         @NotNull CrossRecipeParallelScheduler scheduler) {
        if (allocations.isEmpty()) return 0;

        // Calculate total base power demand across all allocations
        long totalBaseDemand = 0;
        for (SlotAllocation alloc : allocations) {
            totalBaseDemand += alloc.basePowerDemand;
        }

        // Surplus power available for overclocking (beyond base demands)
        long surplusPower = Math.max(0, totalPowerBudget - totalBaseDemand);

        int started = 0;
        for (SlotAllocation alloc : allocations) {
            // Each slot gets its base demand + proportional share of surplus
            long ocBudget;
            if (totalBaseDemand > 0) {
                ocBudget = alloc.basePowerDemand +
                        (long) ((double) surplusPower * alloc.basePowerDemand / totalBaseDemand);
            } else {
                ocBudget = totalPowerBudget / allocations.size();
            }
            // Ensure at least base demand
            ocBudget = Math.max(ocBudget, alloc.basePowerDemand);

            if (overclockAndStartSlot(alloc, ocBudget)) {
                started++;
            } else {
                // Overclock/start failed, release the slot back to pool
                scheduler.releaseSlot(alloc.slot);
            }
        }
        return started;
    }

    /**
     * Multiplies recipe outputs by the given parallel count.
     */
    protected void multiplyOutputs(@NotNull List<ItemStack> itemOutputs,
                                   @NotNull List<FluidStack> fluidOutputs,
                                   int parallelCount) {
        for (ItemStack stack : itemOutputs) {
            stack.setCount(stack.getCount() * parallelCount);
        }
        for (FluidStack fluid : fluidOutputs) {
            fluid.amount *= parallelCount;
        }
    }

    @NotNull
    protected String getRecipeDisplayName(@NotNull Recipe recipe,
                                          @NotNull RecipeMap<?> recipeMap,
                                          @NotNull List<ItemStack> itemOutputs,
                                          @NotNull List<FluidStack> fluidOutputs) {
        for (ItemStack stack : itemOutputs) {
            if (!stack.isEmpty()) {
                return stack.getDisplayName();
            }
        }
        for (FluidStack fluid : fluidOutputs) {
            if (fluid != null && fluid.amount > 0) {
                return fluid.getLocalizedName();
            }
        }
        for (GTRecipeInput input : recipe.getInputs()) {
            for (ItemStack stack : input.getInputStacks()) {
                if (!stack.isEmpty()) {
                    return stack.getDisplayName();
                }
            }
        }
        for (GTRecipeInput input : recipe.getFluidInputs()) {
            FluidStack fluid = input.getInputFluidStack();
            if (fluid != null && fluid.amount > 0) {
                return fluid.getLocalizedName();
            }
        }
        return recipeMap.getLocalizedName();
    }

    /**
     * Consumes recipe inputs multiplied by parallelCount from the given inventories.
     * Uses the recipe's ingredient matching to identify which slots to consume from,
     * then consumes parallelCount × the recipe's required amount.
     *
     * @param recipe          the recipe whose inputs to consume
     * @param importInventory the item input inventory
     * @param importFluids    the fluid input tanks
     * @param parallelCount   the number of times to consume the recipe's inputs
     * @return true if all inputs were successfully consumed
     */
    protected boolean consumeRecipeInputs(@NotNull Recipe recipe,
                                          @NotNull IItemHandlerModifiable importInventory,
                                          @NotNull IMultipleTankHandler importFluids,
                                          int parallelCount) {
        if (parallelCount <= 0) return false;

        // For single parallel, just use the standard matches(true) which handles consumption
        if (parallelCount == 1) {
            return recipe.matches(true, importInventory, importFluids);
        }

        // For multiple parallels, first verify the recipe matches (without consuming)
        if (!recipe.matches(false, importInventory, importFluids)) {
            return false;
        }

        // Consume item inputs: parallelCount × each input's amount
        for (GTRecipeInput recipeInput : recipe.getInputs()) {
            if (recipeInput.isNonConsumable()) {
                continue;
            }
            int totalRequired = recipeInput.getAmount() * parallelCount;
            int remaining = totalRequired;

            for (int i = 0; i < importInventory.getSlots() && remaining > 0; i++) {
                ItemStack stackInSlot = importInventory.getStackInSlot(i);
                if (stackInSlot.isEmpty()) continue;
                if (!recipeInput.acceptsStack(stackInSlot)) continue;

                int toExtract = Math.min(remaining, stackInSlot.getCount());
                importInventory.extractItem(i, toExtract, false);
                remaining -= toExtract;
            }

            if (remaining > 0) return false; // Should not happen since getMaxRecipeMultiplier passed
        }

        // Consume fluid inputs: parallelCount × each fluid input's amount
        for (GTRecipeInput recipeFluidInput : recipe.getFluidInputs()) {
            if (recipeFluidInput.isNonConsumable()) {
                continue;
            }
            int totalRequired = recipeFluidInput.getAmount() * parallelCount;
            int remaining = totalRequired;

            for (IMultipleTankHandler.ITankEntry tank : importFluids.getFluidTanks()) {
                if (remaining <= 0) break;
                FluidStack fluidInTank = tank.getFluid();
                if (fluidInTank == null) continue;
                if (!recipeFluidInput.acceptsFluid(fluidInTank)) continue;

                int toDrain = Math.min(remaining, fluidInTank.amount);
                tank.drain(toDrain, true);
                remaining -= toDrain;
            }

            if (remaining > 0) return false; // Should not happen since getMaxRecipeMultiplier passed
        }

        return true;
    }

    @Override
    protected boolean canProgressRecipe() {
        return super.canProgressRecipe() && !((IMultiblockController) metaTileEntity).isStructureObstructed();
    }

    /**
     * Used to reset cached values in the Recipe Logic on structure deform
     */
    @Override
    public void invalidate() {
        super.invalidate();
        lastRecipeIndex = 0;
        invalidatedInputList.clear();
        if (crossRecipeScheduler != null) {
            crossRecipeScheduler.invalidateAll();
            crossRecipeSchedulerActive = false;
        }
        lastCrossRecipe = null;
    }

    public void onDistinctChanged() {
        this.lastRecipeIndex = 0;
    }

    public IEnergyContainer getEnergyContainer() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        return holder.getEnergyContainer();
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        return holder.getInputInventory();
    }

    // Used for distinct bus recipe checking
    protected List<IItemHandlerModifiable> getInputBuses() {
        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        return controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
    }

    protected boolean shouldUseIsolatedInputBuses() {
        if (!(metaTileEntity instanceof MultiblockControllerBase controller)) return false;
        for (IItemHandlerModifiable bus : controller.getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
            if (bus instanceof IPatternBufferIsolatedHandler) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldUseDistinctInputBuses() {
        if (shouldUseIsolatedInputBuses()) return true;
        return metaTileEntity instanceof IDistinctBusController distinctCtrl &&
                distinctCtrl.canBeDistinct() && distinctCtrl.isDistinct() &&
                getInputInventory().getSlots() > 0;
    }

    @Override
    protected IItemHandlerModifiable getOutputInventory() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        return holder.getOutputInventory();
    }

    @Override
    protected IMultipleTankHandler getInputTank() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        if (shouldUseDistinctInputBuses()) {
            return holder.getInputFluidInventory();
        }

        //检查总成，如果有合并流体
        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        List<IItemHandlerModifiable> itemHandlers = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
        List<IMultipleTankHandler> inputFluids = new ArrayList<>();
        boolean allowMerge = holder.getInputFluidInventory().allowSameFluidFill();
        inputFluids.add(holder.getInputFluidInventory());
        // 遍历所有物品总线，检查是否是 DualHandler
        for (IItemHandlerModifiable bus : itemHandlers) {
            if (bus instanceof IPatternBufferIsolatedHandler) continue;
            if (bus instanceof IMultipleTankHandler dualHandler) {
                // 将 DualHandler 的流体槽添加到总列表中
                inputFluids.add(dualHandler);
            }
        }
        return GTQTUtility.mergeTankHandlers(inputFluids, allowMerge);
    }

    /**
     * Overload of {@link #getInputTank()} to gather extra fluid tanks that could exist in a distinct item handler (such
     * as a {@link DualHandler})
     *
     * @param items Handler to gather fluid tanks from
     * @return a new FluidTankList with extra fluid tanks on top of the existing fluid tanks
     */
    protected IMultipleTankHandler getInputTank(IItemHandler items) {
        IMultipleTankHandler baseInputTank = getInputTank();
        var tanks = new ArrayList<>(baseInputTank.getFluidTanks());
        if (items instanceof IMultipleTankHandler tankHandler) {
            tanks.addAll(tankHandler.getFluidTanks());
        }
        return new FluidTankList(baseInputTank.allowSameFluidFill(), tanks);
    }

    @Override
    protected IMultipleTankHandler getOutputTank() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        //检查总成，如果有合并流体
        List<IItemHandlerModifiable> itemHandlers = controller.getAbilities(MultiblockAbility.EXPORT_ITEMS);
        List<IMultipleTankHandler> outputFluids = new ArrayList<>();
        boolean allowMerge = holder.getOutputFluidInventory().allowSameFluidFill();
        outputFluids.add(holder.getOutputFluidInventory());
        // 遍历所有物品总线，检查是否是 DualHandler
        for (IItemHandlerModifiable bus : itemHandlers) {
            if (bus instanceof IMultipleTankHandler dualHandler) {
                // 将 DualHandler 的流体槽添加到总列表中
                outputFluids.add(dualHandler);
            }
        }
        return GTQTUtility.mergeTankHandlers(outputFluids, allowMerge);
    }

    @Override
    protected boolean canWorkWithInputs() {
        if (shouldUseDistinctInputBuses()) {
                boolean canWork = false;
                if (invalidatedInputList.isEmpty()) {
                    return true;
                }
                if (!metaTileEntity.getNotifiedFluidInputList().isEmpty()) {
                    canWork = true;
                    invalidatedInputList.clear();
                    metaTileEntity.getNotifiedFluidInputList().clear();
                    metaTileEntity.getNotifiedItemInputList().clear();
                } else {
                    Iterator<IItemHandlerModifiable> notifiedIter = metaTileEntity.getNotifiedItemInputList()
                            .iterator();
                    while (notifiedIter.hasNext()) {
                        IItemHandlerModifiable bus = notifiedIter.next();
                        Iterator<IItemHandlerModifiable> invalidatedIter = invalidatedInputList.iterator();
                        while (invalidatedIter.hasNext()) {
                            IItemHandler invalidatedHandler = invalidatedIter.next();
                            if (invalidatedHandler instanceof IMultipleNotifiableHandler multipleNotifiableHandler) {
                                for (var notifiableHandler : multipleNotifiableHandler.getBackingNotifiers()) {
                                    if (notifiableHandler == bus) {
                                        canWork = true;
                                        invalidatedIter.remove();
                                        break;
                                    }
                                }
                            } else if (invalidatedHandler == bus) {
                                canWork = true;
                                invalidatedIter.remove();
                            }
                        }
                        notifiedIter.remove();
                    }
                }
                ArrayList<IItemHandler> flattenedHandlers = new ArrayList<>();
                for (IItemHandler ih : getInputBuses()) {
                    if (ih instanceof ItemHandlerList) {
                        flattenedHandlers.addAll(((ItemHandlerList) ih).getBackingHandlers());
                    }
                    flattenedHandlers.add(ih);
                }

                if (!invalidatedInputList.containsAll(flattenedHandlers)) {
                    canWork = true;
                }
                return canWork;
        }
        return super.canWorkWithInputs();
    }

    @Override
    protected void trySearchNewRecipe() {
        // do not run recipes when there are more than 5 maintenance problems
        // Maintenance can apply to all multiblocks, so cast to a base multiblock class
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (ConfigHolder.machines.enableMaintenance && controller.hasMaintenanceMechanics() &&
                controller.getNumMaintenanceProblems() > 5) {
            return;
        }

        if (shouldUseDistinctInputBuses()) {
            trySearchNewRecipeDistinct();
            return;
        }

        trySearchNewRecipeCombined();
    }

    /**
     * Put into place so multiblocks can override {@link AbstractRecipeLogic#trySearchNewRecipe()} without having to
     * deal with the maintenance and distinct logic in {@link MultiblockRecipeLogic#trySearchNewRecipe()}
     */
    protected void trySearchNewRecipeCombined() {
        if (isCrossRecipeMode()) {
            CrossRecipeParallelScheduler scheduler = getOrCreateScheduler();
            int filled = fillSchedulerSlots(scheduler);

            if (filled > 0) {
                this.progressTime = 1;
                setMaxProgress(Integer.MAX_VALUE);
                this.recipeEUt = scheduler.getTotalEnergyConsumption();
                this.parallelRecipesPerformed = scheduler.getTotalParallelCount();
                crossRecipeSchedulerActive = true;
                if (this.wasActiveAndNeedsUpdate) {
                    this.wasActiveAndNeedsUpdate = false;
                } else {
                    this.setActive(true);
                }
            }
            return;
        }

        super.trySearchNewRecipe();
    }

    protected void trySearchNewRecipeDistinct() {
        if (isCrossRecipeMode()) {
            trySearchNewRecipeDistinctCrossRecipe();
            return;
        }

        long maxVoltage = getMaxVoltage();
        Recipe currentRecipe;
        List<IItemHandlerModifiable> importInventory = getInputBuses();

        // Our caching implementation
        // This guarantees that if we get a recipe cache hit, our efficiency is no different from other machines
        if (checkPreviousRecipeDistinct(importInventory.get(lastRecipeIndex)) && checkRecipe(previousRecipe)) {
            currentRecipe = previousRecipe;
            currentDistinctInputBus = importInventory.get(lastRecipeIndex);
            if (prepareRecipeDistinct(currentRecipe)) {
                // No need to cache the previous recipe here, as it is not null and matched by the current recipe,
                // so it will always be the same
                return;
            }
        }

        // On a cache miss, our efficiency is much worse, as it will check
        // each bus individually instead of the combined inventory all at once.
        for (int i = 0; i < importInventory.size(); i++) {
            IItemHandlerModifiable bus = importInventory.get(i);
            // Skip this bus if no recipe was found last time
            if (invalidatedInputList.contains(bus)) {
                continue;
            }
            // Look for a new recipe after a cache miss
            currentRecipe = findRecipe(maxVoltage, bus, getInputTank(bus));
            // Cache the current recipe, if one is found
            if (currentRecipe != null && checkRecipe(currentRecipe)) {
                this.previousRecipe = currentRecipe;
                currentDistinctInputBus = bus;
                if (prepareRecipeDistinct(currentRecipe)) {
                    lastRecipeIndex = i;
                    return;
                }
            }
            if (currentRecipe == null) {
                // no valid recipe found, invalidate this bus
                invalidatedInputList.add(bus);
            }
        }
    }

    /**
     * Cross-recipe parallel version of distinct bus recipe search.
     * Iterates all distinct input buses and fills idle scheduler slots from each bus.
     * Each bus can contribute different recipes to different slots.
     * Uses two-phase allocation: parallel first, then proportional overclock.
     */
    protected void trySearchNewRecipeDistinctCrossRecipe() {
        CrossRecipeParallelScheduler scheduler = getOrCreateScheduler();
        List<IItemHandlerModifiable> importInventory = getInputBuses();
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return;

        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        long remainingBasePower = totalPowerBudget;
        int remainingParallel = scheduler.getRemainingParallelBudget();
        List<SlotAllocation> allocations = new ArrayList<>();

        // Phase 1: Allocate parallel for each bus (no overclock, no input consumption)
        for (int i = 0; i < importInventory.size(); i++) {
            if (remainingParallel <= 0 || remainingBasePower <= 0) break;

            IItemHandlerModifiable bus = importInventory.get(i);
            if (invalidatedInputList.contains(bus)) continue;

            IMultipleTankHandler busFluidTank = getInputTank(bus);
            boolean foundFromBus = false;

            Recipe recipe = findRecipe(remainingBasePower, bus, busFluidTank);
            if (recipe != null && checkRecipe(recipe)) {
                RecipeSlot slot = scheduler.acquireSlot();
                SlotAllocation alloc = allocateSlotParallel(slot, recipe, recipeMap, bus, busFluidTank,
                        remainingBasePower, remainingParallel);
                if (alloc != null) {
                    allocations.add(alloc);
                    remainingBasePower -= alloc.basePowerDemand;
                    remainingParallel -= alloc.inputParallel;
                    foundFromBus = true;
                    lastCrossRecipe = recipe;
                } else {
                    scheduler.releaseSlot(slot);
                }
            }

            if (!foundFromBus) {
                invalidatedInputList.add(bus);
            }
        }

        // Phase 2: Distribute surplus power proportionally and overclock all slots
        int totalFilled = distributeAndOverclock(allocations, totalPowerBudget, scheduler);

        if (totalFilled > 0) {
            this.progressTime = 1;
            setMaxProgress(Integer.MAX_VALUE);
            this.recipeEUt = scheduler.getTotalEnergyConsumption();
            this.parallelRecipesPerformed = scheduler.getTotalParallelCount();
            crossRecipeSchedulerActive = true;
            if (this.wasActiveAndNeedsUpdate) {
                this.wasActiveAndNeedsUpdate = false;
            } else {
                this.setActive(true);
            }
        }
    }

    @Override
    public void invalidateInputs() {
        if (shouldUseDistinctInputBuses() && currentDistinctInputBus != null) {
            invalidatedInputList.add(currentDistinctInputBus);
        } else {
            super.invalidateInputs();
        }
    }

    protected boolean checkPreviousRecipeDistinct(IItemHandlerModifiable previousBus) {
        return previousRecipe != null && previousRecipe.matches(false, previousBus, getInputTank(previousBus));
    }

    protected boolean prepareRecipeDistinct(Recipe recipe) {
        recipe = Recipe.trimRecipeOutputs(recipe, getRecipeMap(), metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());

        recipe = findParallelRecipe(
                recipe,
                currentDistinctInputBus,
                getInputTank(currentDistinctInputBus),
                getOutputInventory(),
                getOutputTank(),
                getMaxParallelVoltage(),
                getParallelLimit());

        if (recipe != null) {
            recipe = setupAndConsumeRecipeInputs(recipe, currentDistinctInputBus,
                    getInputTank(currentDistinctInputBus));
            if (recipe != null) {
                setupRecipe(recipe);
                return true;
            }
        }

        return false;
    }

    @Override
    protected void modifyOverclockPre(@NotNull OCParams ocParams, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPre(ocParams, storage);

        // apply maintenance bonuses
        Tuple<Integer, Double> maintenanceValues = getMaintenanceValues();

        // duration bonus
        if (maintenanceValues.getSecond() != 1.0) {
            ocParams.setDuration((int) Math.round(ocParams.duration() * maintenanceValues.getSecond()));
        }
    }

    @Override
    protected void runOverclockingLogic(@NotNull OCParams ocParams, @NotNull OCResult ocResult,
                                        @NotNull RecipePropertyStorage propertyStorage, long maxVoltage) {
        subTickParallelOC(ocParams, ocResult, maxVoltage, getOverclockingDurationFactor(),
                getOverclockingVoltageFactor());
    }

    @Override
    protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPost(ocResult, storage);

        // apply maintenance penalties
        Tuple<Integer, Double> maintenanceValues = getMaintenanceValues();

        // duration penalty
        if (maintenanceValues.getFirst() > 0) {
            ocResult.setDuration((int) (ocResult.duration() * (1 + 0.1 * maintenanceValues.getFirst())));
        }
    }

    @Override
    public long getMaximumOverclockVoltage() {
        IEnergyContainer energyContainer = getEnergyContainer();
        if (energyContainer instanceof EnergyContainerList) {
            long voltage;
            long amperage;
            if (energyContainer.getInputVoltage() > energyContainer.getOutputVoltage()) {
                voltage = energyContainer.getInputVoltage();
                amperage = energyContainer.getInputAmperage();
            } else {
                voltage = energyContainer.getOutputVoltage();
                amperage = energyContainer.getOutputAmperage();
            }

            if (amperage == 1) {
                // amperage is 1 when the energy is not exactly on a tier

                // the voltage for recipe search is always on tier, so take the closest lower tier
                return GTValues.VOC[GTUtility.getFloorTierByVoltage(voltage)];
            } else {
                // amperage != 1 means the voltage is exactly on a tier
                // ignore amperage, since only the voltage is relevant for recipe search
                // amps are never > 3 in an EnergyContainerList
                return voltage;
            }
        }
        return Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage());
    }

    @NotNull
    protected Tuple<Integer, Double> getMaintenanceValues() {
        MultiblockWithDisplayBase displayBase = this.metaTileEntity instanceof MultiblockWithDisplayBase ?
                (MultiblockWithDisplayBase) metaTileEntity : null;
        int numMaintenanceProblems = displayBase == null || !displayBase.hasMaintenanceMechanics() ||
                !ConfigHolder.machines.enableMaintenance ? 0 : displayBase.getNumMaintenanceProblems();
        double durationMultiplier = 1.0D;
        if (displayBase != null && displayBase.hasMaintenanceMechanics() && ConfigHolder.machines.enableMaintenance) {
            durationMultiplier = displayBase.getMaintenanceDurationMultiplier();
        }
        return new Tuple<>(numMaintenanceProblems, durationMultiplier);
    }

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe) {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        if (holder.checkRecipe(recipe, false)) {
            holder.checkRecipe(recipe, true);
            return super.checkRecipe(recipe);
        }
        return false;
    }

    @Override
    protected void completeRecipe() {
        performMufflerOperations();
        super.completeRecipe();
    }

    // ==================== Serialization for Cross-Recipe Scheduler ====================

    @NotNull
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound compound = super.serializeNBT();
        compound.setBoolean("crossRecipeActive", crossRecipeSchedulerActive);
        if (crossRecipeSchedulerActive && crossRecipeScheduler != null) {
            compound.setTag("crossRecipeScheduler", crossRecipeScheduler.serializeNBT());
        }
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound compound) {
        super.deserializeNBT(compound);
        this.crossRecipeSchedulerActive = compound.getBoolean("crossRecipeActive");
        if (crossRecipeSchedulerActive && compound.hasKey("crossRecipeScheduler")) {
            crossRecipeScheduler = new CrossRecipeParallelScheduler(getParallelLimit());
            crossRecipeScheduler.deserializeNBT(compound.getCompoundTag("crossRecipeScheduler"));
        }
    }

    protected void performMufflerOperations() {
        if (metaTileEntity instanceof MultiblockWithDisplayBase controller) {
            // output muffler items
            if (controller.hasMufflerMechanics()) {
                controller.outputRecoveryItems(Math.max(parallelRecipesPerformed, 1));
            }
        }
    }

    protected void onCrossRecipeSlotsCompleted(int completedParallel) {
        int previousParallel = parallelRecipesPerformed;
        parallelRecipesPerformed = completedParallel;
        performMufflerOperations();
        parallelRecipesPerformed = previousParallel;
    }

    @Override
    @NotNull
    public ParallelLogicType getParallelLogicType() {
        return ParallelLogicType.CROSS_RECIPE;
    }

    // ==================== Cross-Recipe Progress Display Overrides ====================

    /**
     * In cross-recipe mode, returns the display slot's progress time for external consumers
     * (TOP, HWYLA, GUI progress bar, etc.) instead of the internal sentinel value (1).
     */
    @Override
    public int getProgress() {
        if (isCrossRecipeMode() && crossRecipeScheduler != null && crossRecipeScheduler.hasActiveSlots()) {
            return crossRecipeScheduler.getDisplayProgressTime();
        }
        return super.getProgress();
    }

    /**
     * In cross-recipe mode, returns the display slot's max progress time for external consumers
     * instead of the internal sentinel value (Integer.MAX_VALUE).
     */
    @Override
    public int getMaxProgress() {
        if (isCrossRecipeMode() && crossRecipeScheduler != null && crossRecipeScheduler.hasActiveSlots()) {
            return crossRecipeScheduler.getDisplayMaxProgressTime();
        }
        return super.getMaxProgress();
    }

    @Override
    public long getMaxVoltage() {
        IEnergyContainer energyContainer = getEnergyContainer();
        if (!consumesEnergy()) {
            // Generator Multiblocks
            long voltage = energyContainer.getOutputVoltage();
            long amperage = energyContainer.getOutputAmperage();
            if (energyContainer instanceof EnergyContainerList && amperage == 1) {
                // Amperage is 1 when the energy is not exactly on a tier.
                // The voltage for recipe search is always on tier, so take the closest lower tier.
                // List check is done because single hatches will always be a "clean voltage," no need
                // for any additional checks.
                return GTValues.VOC[GTUtility.getFloorTierByVoltage(voltage)];
            }
            return voltage;
        } else {
            // Machine Multiblocks
            if (energyContainer instanceof EnergyContainerList energyList) {
                long highestVoltage = energyList.getHighestInputVoltage();
                if (energyList.getNumHighestInputContainers() > 1) {
                    // allow tier + 1 if there are multiple hatches present at the highest tier
                    int tier = GTUtility.getTierByVoltage(highestVoltage);
                    return GTValues.V[Math.min(tier + 1, GTValues.MAX)];
                } else {
                    return highestVoltage;
                }
            } else {
                return energyContainer.getInputVoltage();
            }
        }
    }

    @Override
    protected long getMaxParallelVoltage() {
        return getMaximumOverclockVoltage();
    }

    @Nullable
    @Override
    public RecipeMap<?> getRecipeMap() {
        // if the multiblock has more than one RecipeMap, return the currently selected one
        if (metaTileEntity instanceof IMultipleRecipeMaps)
            return ((IMultipleRecipeMaps) metaTileEntity).getCurrentRecipeMap();
        return super.getRecipeMap();
    }
}
