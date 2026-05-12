package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleNotifiableHandler;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
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

import gtqt.api.util.GTQTUtility;
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
        crossRecipeScheduler.setParallelLimit(getParallelLimit());
        return crossRecipeScheduler;
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
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (controller instanceof RecipeMapMultiblockController distinctController &&
                distinctController.canBeDistinct() && distinctController.isDistinct() &&
                getInputInventory().getSlots() > 0) {
            fillSchedulerSlotsDistinct(scheduler);
        } else {
            fillSchedulerSlots(scheduler);
        }
    }

    /**
     * Fills scheduler with recipes from distinct buses. Each bus is searched in order,
     * creating new slots as recipes are found.
     */
    protected void fillSchedulerSlotsDistinct(@NotNull CrossRecipeParallelScheduler scheduler) {
        List<IItemHandlerModifiable> importInventory = getInputBuses();
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return;

        for (int i = 0; i < importInventory.size(); i++) {
            if (!scheduler.canAcceptMoreRecipes()) break;

            IItemHandlerModifiable bus = importInventory.get(i);
            IMultipleTankHandler busFluidTank = getInputTank(bus);

            // Search for a recipe from this bus and create a slot for it
            Recipe recipe = findRecipe(scheduler.getRemainingPowerBudget(), bus, busFluidTank);
            if (recipe == null || !checkRecipe(recipe)) continue;

            RecipeSlot slot = scheduler.acquireSlot();
            if (setupSlotWithRecipe(slot, recipe, recipeMap, bus, busFluidTank,
                    scheduler.getRemainingPowerBudget(), scheduler.getRemainingParallelBudget())) {
                lastCrossRecipe = recipe;
            } else {
                // Setup failed, release the slot back to pool
                scheduler.releaseSlot(slot);
            }
        }
    }

    /**
     * Fills scheduler with recipes from the combined input inventory.
     * Uses an optimized search strategy:
     * <ol>
     *   <li>Phase 1 (fast path): Try cached recipe first to avoid full tree traversal</li>
     *   <li>Phase 2 (iterator): Use RecipeIterator to find all distinct matchable recipes
     *       from a single prepareRecipeFind pass, avoiding redundant tree traversals
     *       and duplicate recipe hits via exclusion set</li>
     * </ol>
     *
     * @param scheduler the scheduler instance
     * @return number of slots successfully created
     */
    protected int fillSchedulerSlots(@NotNull CrossRecipeParallelScheduler scheduler) {
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return 0;

        IItemHandlerModifiable importInventory = getInputInventory();
        IMultipleTankHandler importFluids = getInputTank();

        int filled = 0;

        // Phase 1: Try cached recipe first (fast path, avoids full recipe search)
        if (lastCrossRecipe != null && scheduler.canAcceptMoreRecipes()) {
            if (lastCrossRecipe.matches(false, importInventory, importFluids)) {
                RecipeSlot slot = scheduler.acquireSlot();
                if (setupSlotWithRecipe(slot, lastCrossRecipe, recipeMap, importInventory, importFluids,
                        scheduler.getRemainingPowerBudget(), scheduler.getRemainingParallelBudget())) {
                    filled++;
                } else {
                    scheduler.releaseSlot(slot);
                }
            }
        }

        // Phase 2: Use RecipeIterator to find all distinct recipes in one pass
        if (scheduler.canAcceptMoreRecipes()) {
            long remainingPower = scheduler.getRemainingPowerBudget();
            if (remainingPower > 0) {
                List<ItemStack> items = GTUtility.itemHandlerToList(importInventory);
                List<FluidStack> fluids = GTUtility.fluidHandlerToList(importFluids);
                List<ItemStack> filteredItems = items.stream()
                        .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
                List<FluidStack> filteredFluids = fluids.stream()
                        .filter(f -> f != null && f.amount != 0).collect(java.util.stream.Collectors.toList());

                final long powerBudgetSnapshot = remainingPower;
                RecipeIterator iterator = recipeMap.findRecipeIterator(filteredItems, filteredFluids,
                        recipe -> recipe.getEUt() <= powerBudgetSnapshot &&
                                recipe.matches(false, importInventory, importFluids));

                if (iterator != null) {
                    // Exclude the cached recipe since Phase 1 already handled it
                    if (lastCrossRecipe != null) {
                        iterator.exclude(lastCrossRecipe);
                    }

                    while (iterator.hasNext() && scheduler.canAcceptMoreRecipes()) {
                        Recipe recipe = iterator.next();
                        if (recipe == null || !checkRecipe(recipe)) continue;

                        RecipeSlot slot = scheduler.acquireSlot();
                        if (setupSlotWithRecipe(slot, recipe, recipeMap, importInventory, importFluids,
                                scheduler.getRemainingPowerBudget(), scheduler.getRemainingParallelBudget())) {
                            lastCrossRecipe = recipe;
                            iterator.exclude(recipe);
                            filled++;
                        } else {
                            scheduler.releaseSlot(slot);
                            // Exclude failed recipe to avoid retrying it
                            iterator.exclude(recipe);
                        }
                    }
                }
            }
        }

        return filled;
    }

    /**
     * Sets up a specific slot with a known recipe.
     * Unified execution order: Parallel (MULTIPLY) → Overclock → 1tOC (Sub-tick Parallel) → Consume → Output.
     *
     * <p>Flow:
     * <ol>
     *   <li>MULTIPLY: Determine how many copies of this recipe can run based on input availability</li>
     *   <li>Overclock: Reduce duration using available voltage</li>
     *   <li>1tOC: If duration reaches 1 tick and still has OC budget, convert remaining to sub-tick parallel</li>
     *   <li>Final parallel = inputParallel × subTickParallel</li>
     *   <li>Consume totalParallel × inputs, produce totalParallel × outputs</li>
     * </ol>
     *
     * @param slot              the slot to set up
     * @param recipe            the recipe to use
     * @param recipeMap         the recipe map
     * @param importInventory   the input inventory
     * @param importFluids      the input fluid tanks
     * @param slotMaxVoltage    the maximum voltage for this slot
     * @param maxParallelBudget the remaining parallel budget from the scheduler
     * @return true if the slot was successfully started
     */
    protected boolean setupSlotWithRecipe(@NotNull RecipeSlot slot,
                                          @NotNull Recipe recipe,
                                          @NotNull RecipeMap<?> recipeMap,
                                          @NotNull IItemHandlerModifiable importInventory,
                                          @NotNull IMultipleTankHandler importFluids,
                                          long slotMaxVoltage,
                                          int maxParallelBudget) {
        // Trim recipe outputs
        Recipe trimmed = Recipe.trimRecipeOutputs(recipe, recipeMap, metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());

        long baseEUt = trimmed.getEUt();
        int baseDuration = trimmed.getDuration();

        // --- Step 1: MULTIPLY parallel (from inputs) ---
        // Cap parallel by: min(parallelBudget, slotMaxVoltage / baseEUt)
        int maxInputParallel = (int) Math.min(maxParallelBudget, slotMaxVoltage / Math.max(1, baseEUt));
        maxInputParallel = Math.max(1, maxInputParallel);

        int inputParallel = ParallelLogic.getMaxRecipeMultiplier(
                trimmed, importInventory, importFluids, maxInputParallel);
        if (inputParallel == 0) return false;

        // Limit by output space (using inputParallel as upper bound initially)
        int outputParallel = ParallelLogic.limitByOutputMerging(trimmed, getOutputInventory(), getOutputTank(),
                inputParallel,
                metaTileEntity.canVoidRecipeItemOutputs(),
                metaTileEntity.canVoidRecipeFluidOutputs());
        if (outputParallel == 0) {
            this.isOutputsFull = true;
            return false;
        }
        inputParallel = outputParallel;

        // --- Step 2: Overclock the whole paralleled batch ---
        // GT5-style: overclock is applied to the total paralleled EUt (baseEUt × inputParallel).
        // This ensures parallel consumes power budget first, and only leftover budget is used for OC.
        // getNumberOfOCs uses single-recipe EUt to determine the tier-based OC count,
        // while the actual OC algorithm runs on the total EUt and is bounded by slotMaxVoltage.
        long totalBaseEUt = baseEUt * inputParallel;
        OCParams params = new OCParams();
        OCResult result = new OCResult();
        params.initialize(totalBaseEUt, baseDuration, getNumberOfOCs(baseEUt));
        modifyOverclockPre(params, trimmed.propertyStorage());

        if (params.ocAmount() <= 0) {
            result.init(params.eut(), params.duration());
        } else {
            // OC the whole batch: EUt is the total paralleled EUt, maxVoltage is slotMaxVoltage.
            // Delegates to runOverclockingLogic so subclasses (e.g. Godforge heat OC) can override.
            runOverclockingLogic(params, result, trimmed.propertyStorage(), slotMaxVoltage);
        }
        modifyOverclockPost(result, trimmed.propertyStorage());

        long overclockedEUt = result.eut();
        int overclockedDuration = result.duration();
        int subTickParallel = Math.max(1, result.parallel());

        // --- Step 3: Calculate total parallel ---
        // Total parallel = inputParallel (from MULTIPLY) × subTickParallel (from 1tOC)
        long totalParallel = (long) inputParallel * subTickParallel;

        // Total EUt is the overclocked whole-batch EUt (already includes inputParallel).
        // For sub-tick OC, parallelEUt is the actual total power draw.
        long totalSlotEUt = result.parallelEUt() > 0 ? result.parallelEUt() : overclockedEUt;

        if (totalSlotEUt > slotMaxVoltage) {
            // Power budget exceeded after OC — should not normally happen since OC is bounded by
            // slotMaxVoltage, but handle gracefully by reducing inputParallel.
            long perParallelEUt = Math.max(1, totalSlotEUt / inputParallel);
            inputParallel = (int) (slotMaxVoltage / Math.max(1, perParallelEUt));
            if (inputParallel <= 0) return false;
            totalSlotEUt = perParallelEUt * inputParallel;
            totalParallel = (long) inputParallel * subTickParallel;
        }

        // Re-check output space with final total parallel
        if (totalParallel > outputParallel) {
            outputParallel = ParallelLogic.limitByOutputMerging(trimmed, getOutputInventory(), getOutputTank(),
                    (int) Math.min(Integer.MAX_VALUE, totalParallel),
                    metaTileEntity.canVoidRecipeItemOutputs(),
                    metaTileEntity.canVoidRecipeFluidOutputs());
            if (outputParallel == 0) {
                this.isOutputsFull = true;
                return false;
            }
            totalParallel = outputParallel;
            // Recalculate inputParallel based on reduced total
            inputParallel = (int) Math.max(1, totalParallel / subTickParallel);
            totalParallel = (long) inputParallel * subTickParallel;
        }

        int finalParallel = (int) Math.min(Integer.MAX_VALUE, totalParallel);

        // --- Step 3.5: Batch processing (if enabled and duration is short enough) ---
        int batchMultiplier = 1;
        int finalDuration = overclockedDuration;
        if (isBatchEnable() && overclockedDuration <= 64 && overclockedDuration > 0) {
            int maxBatch = (int) Math.floor(128.0 / overclockedDuration);
            // Find the maximum batch multiplier that inputs can sustain
            // Total inputs needed = inputParallel × batchMultiplier
            int batchInputParallel = inputParallel;
            int bestBatch = 1;
            int lo = 1, hi = maxBatch;
            while (lo <= hi) {
                int mid = lo + (hi - lo) / 2;
                int neededParallel = batchInputParallel * mid;
                // Check if this many inputs are available
                int available = ParallelLogic.getMaxRecipeMultiplier(
                        trimmed, importInventory, importFluids, neededParallel);
                if (available >= neededParallel) {
                    bestBatch = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (bestBatch > 1) {
                batchMultiplier = bestBatch;
                finalDuration = overclockedDuration * batchMultiplier;
            }
        }

        // Total input consumption = inputParallel × batchMultiplier
        int totalInputConsume = inputParallel * batchMultiplier;

        // --- Step 4: Consume inputs (inputParallel × batchMultiplier copies) ---
        if (!consumeRecipeInputs(trimmed, importInventory, importFluids, totalInputConsume)) {
            return false;
        }
        this.metaTileEntity.addNotifiedInput(importInventory);
        this.isOutputsFull = false;

        // --- Step 5: Calculate outputs (multiplied by finalParallel × batchMultiplier) ---
        int recipeTier = GTUtility.getTierByVoltage(baseEUt);
        int machineTier = getOverclockForTier(getMaximumOverclockVoltage());
        List<ItemStack> itemOutputs = GTUtility.copyStackList(
                trimmed.getResultItemOutputs(recipeTier, machineTier, recipeMap));
        List<FluidStack> fluidOutputs = GTUtility.copyFluidList(
                trimmed.getResultFluidOutputs(recipeTier, machineTier, recipeMap));

        int outputMultiplier = finalParallel * batchMultiplier;
        if (outputMultiplier > 1) {
            multiplyOutputs(itemOutputs, fluidOutputs, outputMultiplier);
        }

        // --- Step 6: Start the slot ---
        String recipeDisplayName = getRecipeDisplayName(trimmed, recipeMap, itemOutputs, fluidOutputs);
        slot.startRecipe(trimmed, finalDuration, totalSlotEUt, itemOutputs, fluidOutputs, finalParallel,
                recipeDisplayName);
        return true;
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
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        return controller.getEnergyContainer();
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        return controller.getInputInventory();
    }

    // Used for distinct bus recipe checking
    protected List<IItemHandlerModifiable> getInputBuses() {
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        return controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
    }

    @Override
    protected IItemHandlerModifiable getOutputInventory() {
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        return controller.getOutputInventory();
    }

    @Override
    protected IMultipleTankHandler getInputTank() {
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        if (controller.canBeDistinct() && controller.isDistinct() && getInputInventory().getSlots() > 0) {
            return controller.getInputFluidInventory();
        }

        //检查总成，如果有合并流体
        List<IItemHandlerModifiable> itemHandlers = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
        List<IMultipleTankHandler> inputFluids = new ArrayList<>();
        boolean allowMerge = controller.getInputFluidInventory().allowSameFluidFill();
        inputFluids.add(controller.getInputFluidInventory());
        // 遍历所有物品总线，检查是否是 DualHandler
        for (IItemHandlerModifiable bus : itemHandlers) {
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
        var tanks = new ArrayList<>(getInputTank().getFluidTanks());
        if (items instanceof IMultipleTankHandler tankHandler) {
            tanks.addAll(tankHandler.getFluidTanks());
        }
        return new FluidTankList(getInputTank().allowSameFluidFill(), tanks);
    }

    @Override
    protected IMultipleTankHandler getOutputTank() {
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        //检查总成，如果有合并流体
        List<IItemHandlerModifiable> itemHandlers = controller.getAbilities(MultiblockAbility.EXPORT_ITEMS);
        List<IMultipleTankHandler> outputFluids = new ArrayList<>();
        boolean allowMerge = controller.getOutputFluidInventory().allowSameFluidFill();
        outputFluids.add(controller.getOutputFluidInventory());
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
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (controller instanceof RecipeMapMultiblockController distinctController) {

            if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                    getInputInventory().getSlots() > 0) {
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

        // Distinct buses only apply to some multiblocks, so check the controller against a lower class
        if (controller instanceof RecipeMapMultiblockController distinctController) {

            if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                    getInputInventory().getSlots() > 0) {
                trySearchNewRecipeDistinct();
                return;
            }
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
     */
    protected void trySearchNewRecipeDistinctCrossRecipe() {
        CrossRecipeParallelScheduler scheduler = getOrCreateScheduler();
        List<IItemHandlerModifiable> importInventory = getInputBuses();
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return;

        int totalFilled = 0;

        for (int i = 0; i < importInventory.size(); i++) {
            if (!scheduler.canAcceptMoreRecipes()) break;

            IItemHandlerModifiable bus = importInventory.get(i);
            if (invalidatedInputList.contains(bus)) continue;

            IMultipleTankHandler busFluidTank = getInputTank(bus);
            boolean filledFromBus = false;

            // Search for a recipe from this bus and create a slot
            Recipe recipe = findRecipe(scheduler.getRemainingPowerBudget(), bus, busFluidTank);
            if (recipe != null && checkRecipe(recipe)) {
                RecipeSlot slot = scheduler.acquireSlot();
                if (setupSlotWithRecipe(slot, recipe, recipeMap, bus, busFluidTank,
                        scheduler.getRemainingPowerBudget(), scheduler.getRemainingParallelBudget())) {
                    totalFilled++;
                    filledFromBus = true;
                    lastCrossRecipe = recipe;
                } else {
                    scheduler.releaseSlot(slot);
                }
            }

            if (!filledFromBus) {
                invalidatedInputList.add(bus);
            }
        }

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
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        RecipeMapMultiblockController distinctController = (RecipeMapMultiblockController) controller;
        if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                getInputInventory().getSlots() > 0) {
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
        RecipeMapMultiblockController controller = (RecipeMapMultiblockController) metaTileEntity;
        if (controller.checkRecipe(recipe, false)) {
            controller.checkRecipe(recipe, true);
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
