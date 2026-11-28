package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleNotifiableHandler;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IPatternBufferIsolatedHandler;
import gregtech.api.capability.IRecipeMapBoundInput;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.items.metaitem.stats.UpgradeKitType;
import gregtech.api.metatileentity.IUpgradeSlots;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
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
import gregtech.api.recipes.logic.IRecipeCondition;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.ParallelLogic;
import gregtech.api.recipes.logic.RecipeExecutionContext;
import gregtech.api.recipes.logic.RecipeSlot;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.util.ColorUtil;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTQTUtility;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import static gregtech.api.recipes.logic.OverclockingLogic.subTickParallelOC;

public class MultiblockRecipeLogic extends AbstractRecipeLogic {

    private static final long BOUND_INPUT_FAILURE_LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static final long BOUND_INPUT_RETRY_INTERVAL_TICKS = 100L;
    private static final long BOUND_CROSS_RECIPE_START_LOG_INTERVAL_NANOS = 30_000_000_000L;
    private static final Map<String, Long> LAST_BOUND_INPUT_FAILURE_LOG_NANOS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_BOUND_CROSS_RECIPE_START_LOG_NANOS = new ConcurrentHashMap<>();

    // Used for distinct mode
    protected int lastRecipeIndex = 0;
    protected IItemHandlerModifiable currentDistinctInputBus;
    protected List<IItemHandlerModifiable> invalidatedInputList = new ArrayList<>();

    /**
     * 最近一次成功启动配方的颜色通道,用于跨通道轮转。{@link #NO_CHANNEL} 表示还没成功过。
     */
    protected static final int NO_CHANNEL = -1;
    protected static final int UNTRACKED_CHANNEL = Integer.MIN_VALUE;
    protected int lastColorChannel = NO_CHANNEL;
    @Nullable
    private IdentityHashMap<Object, MetaTileEntity> cachedHandlerOwners;
    @Nullable
    private transient IItemHandlerModifiable activeCheckInventory;
    @Nullable
    private transient IMultipleTankHandler activeCheckFluids;

    @Nullable
    private RecipeMap<?> routedRecipeMap;
    @Nullable
    private RecipeMap<?> previousDistinctRecipeMap;

    // Cross-recipe parallel scheduler (lazy initialized when ParallelLogicType.CROSS_RECIPE is active)
    @Nullable
    protected CrossRecipeParallelScheduler crossRecipeScheduler;
    protected boolean crossRecipeSchedulerActive = false;
    private transient boolean preserveAbortWhyFailed = false;
    private transient boolean deferAbortWhyFailedClearOnce = false;
    private transient long refillEnergyCredit = 0L;
    private transient boolean quotingEnergyDraw = false;
    private transient long quotedStoredProbe = 0L;
    private transient boolean hasSearchVoltageSnapshot = false;
    private transient long lastSearchMaxVoltage = 0L;
    private transient long lastSearchPowerBudget = 0L;
    @Nullable
    private transient AllocationBudget lastCombinedAllocationBudget;
    private transient int activeCommitColor = UNTRACKED_CHANNEL;
    @Nullable
    private transient UnaryOperator<IItemHandlerModifiable> activeHolderItemMask;
    @Nullable
    private transient UnaryOperator<IMultipleTankHandler> activeHolderFluidMask;
    private final List<PendingItemRollback> pendingRollbackItems = new ArrayList<>();
    private final List<PendingFluidRollback> pendingRollbackFluids = new ArrayList<>();

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

    @Nullable
    /**
     * Returns the controller's currently selected recipe map before any per-input routing is applied.
     * Controllers whose map is determined by runtime state may override this without changing the
     * generic bound-input routing logic.
     */
    protected RecipeMap<?> getConfiguredRecipeMap() {
        if (metaTileEntity instanceof IMultipleRecipeMaps) {
            return ((IMultipleRecipeMaps) metaTileEntity).getCurrentRecipeMap();
        }
        return super.getRecipeMap();
    }

    @Nullable
    protected RecipeMap<?> getRecipeMapForInput(IItemHandler input) {
        if (!(input instanceof IRecipeMapBoundInput) || !(metaTileEntity instanceof IMultipleRecipeMaps)) {
            return getConfiguredRecipeMap();
        }
        IMultipleRecipeMaps controller = (IMultipleRecipeMaps) metaTileEntity;
        IRecipeMapBoundInput boundInput = (IRecipeMapBoundInput) input;
        if (!controller.supportsRecipeMapPatternRouting()) {
            // Standard multi-map controllers may execute bound inputs for their selected map, but cannot route
            // them across modes unless they explicitly opt in to RecipeMap pattern routing.
            RecipeMap<?> currentMap = getConfiguredRecipeMap();
            String boundMapName = boundInput.getBoundRecipeMapName();
            if (boundMapName == null ||
                    (currentMap != null && boundMapName.equals(currentMap.getUnlocalizedName()))) {
                return currentMap;
            }
            return null;
        }

        String mapName = boundInput.getBoundRecipeMapName();
        if (mapName == null) return null;
        for (RecipeMap<?> recipeMap : controller.getAvailableRecipeMaps()) {
            if (recipeMap != null && mapName.equals(recipeMap.getUnlocalizedName())) return recipeMap;
        }
        return null;
    }

    @Nullable
    protected Recipe findRecipe(@Nullable RecipeMap<?> recipeMap, long maxVoltage,
                                IItemHandlerModifiable inputs, IMultipleTankHandler fluidInputs) {
        if (recipeMap == null || !isRecipeMapValid(recipeMap)) return null;
        if (getLockedRecipe() != null) {
            return matchLockedRecipe(recipeMap, maxVoltage, inputs, fluidInputs);
        }
        return recipeMap.findRecipe(maxVoltage, inputs, fluidInputs);
    }

    @Override
    public void update() {}

    public void updateWorkable() {
        flushPendingRollbacks();
        super.update();
    }

    /**
     * 控制并行是否将电力需求乘以并行数。
     * {@link #overclockAndStartSlot}。
     */
    protected boolean shouldParallelMultiplyPower() {
        return true;
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

        // The thread limit caps how many slots may run at once, and the parallel limit caps each slot.
        // Cross-recipe parallel drops both caps and lets the slots share one elastic budget instead.
        int threads = Math.max(1, getThreadLimit());
        int parallel = Math.max(1, getParallelLimit());
        boolean crossRecipe = isCrossRecipeParallelEnabled();
        boolean threaded = !crossRecipe && threads > 1;

        // Widening to long first: MultiblockFuelRecipeLogic reports Integer.MAX_VALUE as its parallel limit.
        long totalParallel = (long) parallel * threads;
        crossRecipeScheduler.setParallelLimit(threaded ?
                (int) Math.min(Integer.MAX_VALUE, totalParallel) : parallel);
        crossRecipeScheduler.setMaxSlots(crossRecipe ? 0 : threads);
        crossRecipeScheduler.setPerSlotParallelCap(threaded ? parallel : 0);
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
     * Whether the parallel scheduler drives this machine's progress instead of the plain single-recipe path.
     * True whenever there is more than one operation in flight: several parallel copies (parallel limit > 1),
     * several recipes at once (thread limit > 1), or an unbounded mix of recipes (cross-recipe parallel).
     *
     * @return true if progress and energy are managed by the scheduler
     */
    public boolean usesParallelScheduler() {
        return getParallelLimit() > 1 || getThreadLimit() > 1 || isCrossRecipeParallelEnabled();
    }

    @Nullable
    public CrossRecipeParallelScheduler getCrossRecipeScheduler() {
        return crossRecipeScheduler;
    }

    @Override
    protected void updateRecipeProgress() {
        boolean runScheduler = crossRecipeSchedulerActive ||
                (usesParallelScheduler() && crossRecipeScheduler != null && crossRecipeScheduler.hasActiveSlots());
        if (!runScheduler) {
            super.updateRecipeProgress();
            return;
        }

        // In CROSS_RECIPE mode, the scheduler manages all progress and energy
        CrossRecipeParallelScheduler scheduler = getOrCreateScheduler();
        IMultipleTankHandler outputTank = getOutputTank();

        this.whyFailed = "";
        long[] actualPaidInTick = { 0L };
        int completedParallel = scheduler.tickSlots(
                getOutputInventory(),
                outputTank,
                (amount, simulate) -> {
                    if (simulate) {
                        return drawEnergy(amount, true);
                    }
                    long storedBefore = getEnergyStored();
                    boolean drawn = drawEnergy(amount, false);
                    if (drawn && consumesEnergy()) {
                        actualPaidInTick[0] = Math.max(0L, storedBefore - getEnergyStored());
                    }
                    return drawn;
                },
                metaTileEntity.canVoidRecipeItemOutputs(),
                metaTileEntity.canVoidRecipeFluidOutputs(),
                getRecipeConditions(),
                (fluids, simulate) -> {
                    if (simulate) {
                        return checkOutputFluidSpace(fluids, outputTank);
                    }
                    commitFluidOutputs(fluids, outputTank);
                    return true;
                }
        );
        if (completedParallel > 0) {
            onCrossRecipeSlotsCompleted(completedParallel);
        }

        boolean workedInTick = scheduler.hasWorkedThisTick();
        long consumedEUtInTick = scheduler.getLastTickConsumedEUt();
        boolean inFlightLowEnergy = scheduler.isHasNotEnoughEnergy();
        String inFlightWhyFailed = this.whyFailed;
        if (inFlightWhyFailed.isEmpty() && !inFlightLowEnergy &&
                !scheduler.hasWorkingSlots() && scheduler.hasOutputBlockedSlots()) {
            inFlightWhyFailed = "输出仓已满，产物无法继续输出（清空或设置销毁产物）";
        }

        // If scheduler can accept more recipes (has remaining parallel budget), try to fill
        this.whyFailed = "";
        this.refillEnergyCredit = computeStillRunningPaidCredit(
                actualPaidInTick[0], consumedEUtInTick, scheduler.getPaidStillRunningEUt());
        try {
            if (!inFlightLowEnergy && usesParallelScheduler() && scheduler.canAcceptMoreRecipes()) {
                refillScheduler(scheduler);
            }
        } finally {
            this.refillEnergyCredit = 0L;
            scheduler.clearPaidStillRunningEUt();
        }
        String candidateWhyFailed = this.whyFailed;

        // Reconcile whole-machine telemetry from actual in-flight slot states so candidate refill rejections
        // cannot mark working slots as low-power or overwrite an in-flight paused slot's failure reason.
        boolean workingNow = scheduler.hasWorkingSlots();
        this.hasNotEnoughEnergy = !workingNow && inFlightLowEnergy;
        this.recipeEUt = !workingNow ? 0L :
                (workedInTick ? consumedEUtInTick : scheduler.getTotalEnergyConsumption());
        this.parallelRecipesPerformed = scheduler.getTotalParallelCount();

        // Update active state based on whether any slots are still running
        if (!scheduler.hasActiveSlots()) {
            crossRecipeSchedulerActive = false;
            this.hasNotEnoughEnergy = false;
            this.isPaused = false;
            this.canRecipeProgress = true;
            this.progressTime = 0;
            setMaxProgress(0);
            this.recipeEUt = 0;
            this.parallelRecipesPerformed = 0;
            this.wasActiveAndNeedsUpdate = true;
            if (!inFlightWhyFailed.isEmpty()) {
                this.whyFailed = inFlightWhyFailed;
                this.preserveAbortWhyFailed = true;
                this.deferAbortWhyFailedClearOnce = hasNotifiedInputs();
            } else {
                this.whyFailed = candidateWhyFailed;
                clearPreservedAbortReason();
            }
        } else {
            crossRecipeSchedulerActive = true;
            clearPreservedAbortReason();
            this.isPaused = !this.hasNotEnoughEnergy && !workingNow;
            // Keep progressTime > 0 so the main loop continues calling updateRecipeProgress()
            this.progressTime = 1;
            if (workingNow) {
                this.whyFailed = "";
            } else if (!inFlightWhyFailed.isEmpty()) {
                this.whyFailed = inFlightWhyFailed;
            } else if (this.hasNotEnoughEnergy) {
                this.whyFailed = "";
            } else {
                this.whyFailed = candidateWhyFailed;
            }
        }
    }

    protected static final class AllocationBudget {

        long remainingBasePower;
        int remainingParallel;
        boolean exhausted;
        boolean hasAnyInput;
        boolean matchedAnyRecipe;
        final List<SlotAllocation> allocations = new ArrayList<>();
        final Set<Recipe> allAllocatedRecipes = new HashSet<>();
        final Set<IItemHandlerModifiable> allocatedPublicBuses = new HashSet<>();
        final Set<IItemHandlerModifiable> conditionBlockedPublicBuses = new HashSet<>();
        final Set<IItemHandlerModifiable> failedPublicBuses = new HashSet<>();
        final Set<Recipe> allocatedPublicRecipes = new HashSet<>();

        AllocationBudget(long remainingBasePower, int remainingParallel) {
            this.remainingBasePower = remainingBasePower;
            this.remainingParallel = remainingParallel;
        }
    }

    /**
     * Dispatches scheduler slot filling to the appropriate method based on Distinct mode and color channels.
     */
    protected void refillScheduler(@NotNull CrossRecipeParallelScheduler scheduler) {
        short mask = getHatchColorsMask();
        if (shouldUseDistinctInputBuses()) {
            if (mask != 0) {
                fillSchedulerSlotsDistinctByColorChannels(scheduler, mask, false);
            } else {
                fillSchedulerSlotsDistinct(scheduler);
            }
        } else {
            if (mask != 0) {
                fillSchedulerSlotsByColorChannels(scheduler, mask);
            } else {
                fillSchedulerSlots(scheduler);
            }
        }
    }

    /**
     * Fills scheduler with recipes from distinct buses using two-phase allocation.
     * Phase 1: Allocate parallel for each bus's recipe using base-EUt power budget.
     * Phase 2: Distribute surplus power proportionally and overclock all slots.
     */
    protected void fillSchedulerSlotsDistinct(@NotNull CrossRecipeParallelScheduler scheduler) {
        fillSchedulerSlotsDistinctInternal(scheduler, false);
    }

    protected int fillSchedulerSlotsDistinctInternal(@NotNull CrossRecipeParallelScheduler scheduler,
                                                     boolean skipInvalidated) {
        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        AllocationBudget budget = new AllocationBudget(totalPowerBudget, scheduler.getRemainingParallelBudget());
        collectDistinctSchedulerAllocations(scheduler, getInputBuses(), null, UNTRACKED_CHANNEL,
                skipInvalidated, budget, new HashSet<>());
        return distributeAndOverclock(budget.allocations, totalPowerBudget, scheduler);
    }

    protected int fillSchedulerSlotsDistinctByColorChannels(@NotNull CrossRecipeParallelScheduler scheduler,
                                                            short mask,
                                                            boolean skipInvalidated) {
        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        AllocationBudget budget = new AllocationBudget(totalPowerBudget, scheduler.getRemainingParallelBudget());
        int[] channelOrder = getChannelOrder(mask);
        for (int i = 0; i < channelOrder.length; i++) {
            if (budget.remainingParallel <= 0 || budget.remainingBasePower <= 0) {
                budget.exhausted = true;
                break;
            }
            int color = channelOrder[i];
            List<IItemHandlerModifiable> channelBuses = getInputBusesForColor(color);
            if (channelBuses.isEmpty()) continue;
            IMultipleTankHandler channelFluids = getInputTankForColor(color);
            collectDistinctSchedulerAllocations(scheduler, channelBuses, channelFluids, color,
                    skipInvalidated, budget, new HashSet<>());
            if (budget.exhausted) break;
        }
        if (skipInvalidated && !budget.exhausted) {
            for (IItemHandlerModifiable bus : budget.failedPublicBuses) {
                if (!budget.allocatedPublicBuses.contains(bus) &&
                        !budget.conditionBlockedPublicBuses.contains(bus) &&
                        !invalidatedInputList.contains(bus)) {
                    logBoundInputNoRecipe(bus, getRecipeMapForInput(bus), "NO_RECIPE_MATCHED_BUFFER");
                    invalidatedInputList.add(bus);
                }
            }
        }
        return distributeAndOverclock(budget.allocations, totalPowerBudget, scheduler);
    }

    private void collectDistinctSchedulerAllocations(@NotNull CrossRecipeParallelScheduler scheduler,
                                                     @NotNull List<IItemHandlerModifiable> buses,
                                                     @Nullable IMultipleTankHandler channelFluids,
                                                     int colorChannel,
                                                     boolean skipInvalidated,
                                                     @NotNull AllocationBudget budget,
                                                     @NotNull Set<Recipe> channelUsedRecipes) {
        for (IItemHandlerModifiable bus : buses) {
            if (budget.remainingParallel <= 0 || budget.remainingBasePower <= 0) {
                budget.exhausted = true;
                break;
            }
            if (skipInvalidated && invalidatedInputList.contains(bus)) continue;

            boolean isPublicBus = colorChannel != UNTRACKED_CHANNEL && colorIndexOf(bus) < 0;
            if (isPublicBus && budget.allocatedPublicBuses.contains(bus)) {
                continue;
            }

            IMultipleTankHandler busFluidTank = channelFluids == null ?
                    getInputTank(bus) : getBusFluidsInChannel(bus, channelFluids);
            boolean foundFromBus = false;

            RecipeMap<?> recipeMap = getRecipeMapForInput(bus);
            if (recipeMap == null) {
                if (skipInvalidated) {
                    logBoundInputNoRecipe(bus, null, "BOUND_RECIPE_MAP_NOT_SELECTED");
                    invalidatedInputList.add(bus);
                }
                continue;
            }
            routedRecipeMap = recipeMap;
            long maxVoltage = getMaxVoltage();
            long searchVoltage = Math.min(maxVoltage, budget.remainingBasePower);
            Recipe recipe = findRecipe(recipeMap, searchVoltage, bus, busFluidTank);
            boolean skippedDuplicate = false;
            boolean blockedByConditionOrBudget = false;
            if (recipe == null && searchVoltage < maxVoltage) {
                Recipe fullVoltageRecipe = findRecipe(recipeMap, maxVoltage, bus, busFluidTank);
                if (fullVoltageRecipe != null) {
                    if (!checkRecipeDiagnostic(fullVoltageRecipe, bus, busFluidTank)) {
                        ensureConditionFailureReason();
                    } else {
                        ensurePowerBudgetFailureReason();
                    }
                    blockedByConditionOrBudget = true;
                }
            } else if (recipe != null) {
                if (colorChannel == NO_CHANNEL && budget.allAllocatedRecipes.contains(recipe)) {
                    skippedDuplicate = true;
                } else if (channelUsedRecipes.contains(recipe)) {
                    skippedDuplicate = true;
                } else {
                    RecipeSlot slot = scheduler.acquireSlot();
                    if (slot == null) {
                        budget.exhausted = true;
                        routedRecipeMap = null;
                        break;
                    }
                    if (!checkRecipe(recipe, bus, busFluidTank)) {
                        ensureConditionFailureReason();
                        blockedByConditionOrBudget = true;
                        scheduler.releaseSlot(slot);
                    } else {
                        channelUsedRecipes.add(recipe);
                        boolean wasOutputsFull = this.isOutputsFull;
                        this.isOutputsFull = false;
                        SlotAllocation alloc = allocateSlotParallel(slot, recipe, recipeMap, bus, busFluidTank,
                                budget.remainingBasePower, budget.remainingParallel, scheduler.getPerSlotParallelCap());
                        if (alloc != null) {
                            this.isOutputsFull = wasOutputsFull;
                            alloc.colorChannel = colorChannel;
                            budget.allocations.add(alloc);
                            budget.allAllocatedRecipes.add(recipe);
                            if (isPublicBus) {
                                budget.allocatedPublicBuses.add(bus);
                            }
                            budget.remainingBasePower -= alloc.basePowerDemand;
                            budget.remainingParallel -= alloc.inputParallel;
                            foundFromBus = true;
                            this.previousRecipe = recipe;
                            this.previousDistinctRecipeMap = recipeMap;
                            lastCrossRecipe = recipe;
                        } else {
                            boolean failedByFullOutput = this.isOutputsFull;
                            this.isOutputsFull = wasOutputsFull || failedByFullOutput;
                            if (!failedByFullOutput) {
                                blockedByConditionOrBudget = true;
                            }
                            scheduler.releaseSlot(slot);
                        }
                    }
                }
            }
            routedRecipeMap = null;

            if (!foundFromBus && skipInvalidated) {
                if (blockedByConditionOrBudget || skippedDuplicate) {
                    if (isPublicBus) {
                        budget.conditionBlockedPublicBuses.add(bus);
                    }
                } else if (isPublicBus) {
                    budget.failedPublicBuses.add(bus);
                } else {
                    if (recipe == null) {
                        logBoundInputNoRecipe(bus, recipeMap, "NO_RECIPE_MATCHED_BUFFER");
                    }
                    invalidatedInputList.add(bus);
                }
            }
        }
    }

    private void ensureConditionFailureReason() {
        if (this.whyFailed.isEmpty()) {
            this.whyFailed = "配方的特殊条件不满足（例如温度，算力等额外条件）";
        }
    }

    private void ensurePowerBudgetFailureReason() {
        if (this.whyFailed.isEmpty()) {
            this.whyFailed = "电力不足，无法满足当前配方的运行需求";
        }
    }

    private static boolean hasAnyHandlerInput(@NotNull IItemHandlerModifiable importInventory,
                                              @NotNull IMultipleTankHandler importFluids) {
        for (int i = 0; i < importInventory.getSlots(); i++) {
            if (!importInventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        for (IMultipleTankHandler.ITankEntry tank : importFluids.getFluidTanks()) {
            FluidStack fluid = tank.getFluid();
            if (fluid != null && fluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills scheduler with recipes from the combined input inventory using two-phase allocation.
     */
    protected int fillSchedulerSlots(@NotNull CrossRecipeParallelScheduler scheduler) {
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return 0;

        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        AllocationBudget budget = new AllocationBudget(totalPowerBudget, scheduler.getRemainingParallelBudget());
        this.lastCombinedAllocationBudget = budget;
        collectSchedulerAllocations(scheduler, recipeMap, getInputInventory(), getInputTank(),
                null, null, UNTRACKED_CHANNEL, budget);
        return distributeAndOverclock(budget.allocations, totalPowerBudget, scheduler);
    }

    /**
     * Fills scheduler with recipes per color channel so items/fluids from different painted colors never mix,
     * while still allowing multiple color channels to run concurrently in scheduler slots.
     */
    protected int fillSchedulerSlotsByColorChannels(@NotNull CrossRecipeParallelScheduler scheduler, short mask) {
        RecipeMap<?> recipeMap = getRecipeMap();
        if (recipeMap == null) return 0;

        long totalPowerBudget = scheduler.getRemainingPowerBudget();
        AllocationBudget budget = new AllocationBudget(totalPowerBudget, scheduler.getRemainingParallelBudget());
        this.lastCombinedAllocationBudget = budget;
        if (totalPowerBudget <= 0 || budget.remainingParallel <= 0) {
            budget.hasAnyInput = hasAnyHandlerInput(getInputInventory(), getInputTank());
            return 0;
        }
        IItemHandlerModifiable publicInventory = new ItemHandlerList(getInputBusesForColor(NO_CHANNEL));
        IMultipleTankHandler publicFluids = getInputTankForColor(NO_CHANNEL);
        int[] channelOrder = getChannelOrder(mask);
        for (int i = 0; i < channelOrder.length; i++) {
            if (budget.remainingParallel <= 0 || budget.remainingBasePower <= 0) break;
            int color = channelOrder[i];
            IItemHandlerModifiable channelInventory = new ItemHandlerList(getInputBusesForColor(color));
            IMultipleTankHandler channelFluids = getInputTankForColor(color);
            collectSchedulerAllocations(scheduler, recipeMap, channelInventory, channelFluids,
                    publicInventory, publicFluids, color, budget);
        }
        return distributeAndOverclock(budget.allocations, totalPowerBudget, scheduler);
    }

    private static boolean isPublicSharedRecipe(@NotNull Recipe recipe,
                                                @Nullable IItemHandlerModifiable publicInventory,
                                                @Nullable IMultipleTankHandler publicFluids) {
        return publicInventory != null && publicFluids != null &&
                recipe.matches(false, publicInventory, publicFluids);
    }

    private void collectSchedulerAllocations(@NotNull CrossRecipeParallelScheduler scheduler,
                                             @NotNull RecipeMap<?> recipeMap,
                                             @NotNull IItemHandlerModifiable importInventory,
                                             @NotNull IMultipleTankHandler importFluids,
                                             @Nullable IItemHandlerModifiable publicInventory,
                                             @Nullable IMultipleTankHandler publicFluids,
                                             int colorChannel,
                                             @NotNull AllocationBudget budget) {
        if (hasAnyHandlerInput(importInventory, importFluids)) {
            budget.hasAnyInput = true;
        }
        if (budget.remainingParallel <= 0 || budget.remainingBasePower <= 0) return;

        long maxVoltage = getMaxVoltage();
        Set<Recipe> channelUsedRecipes = new HashSet<>();
        if (colorChannel == NO_CHANNEL) {
            channelUsedRecipes.addAll(budget.allAllocatedRecipes);
        }

        Recipe locked = getLockedRecipe();
        if (locked != null) {
            if (recipeMap.ownsRecipe(locked) &&
                    locked.getEUt() <= maxVoltage &&
                    locked.matches(false, importInventory, importFluids)) {
                budget.matchedAnyRecipe = true;
                if (!channelUsedRecipes.contains(locked) &&
                        !(isPublicSharedRecipe(locked, publicInventory, publicFluids) &&
                                budget.allocatedPublicRecipes.contains(locked))) {
                    if (locked.getEUt() > budget.remainingBasePower) {
                        if (!checkRecipeDiagnostic(locked, importInventory, importFluids)) {
                            ensureConditionFailureReason();
                        } else {
                            ensurePowerBudgetFailureReason();
                        }
                    } else {
                        RecipeSlot slot = scheduler.acquireSlot();
                        if (slot != null) {
                            if (!checkRecipe(locked, importInventory, importFluids)) {
                                ensureConditionFailureReason();
                                scheduler.releaseSlot(slot);
                            } else {
                                SlotAllocation alloc = allocateSlotParallel(slot, locked, recipeMap,
                                        importInventory, importFluids, budget.remainingBasePower,
                                        budget.remainingParallel, scheduler.getPerSlotParallelCap());
                                if (alloc != null) {
                                    alloc.colorChannel = colorChannel;
                                    budget.allocations.add(alloc);
                                    budget.allAllocatedRecipes.add(locked);
                                    if (isPublicSharedRecipe(locked, publicInventory, publicFluids)) {
                                        budget.allocatedPublicRecipes.add(locked);
                                    }
                                    channelUsedRecipes.add(locked);
                                    budget.remainingBasePower -= alloc.basePowerDemand;
                                    budget.remainingParallel -= alloc.inputParallel;
                                    this.previousRecipe = locked;
                                    lastCrossRecipe = locked;
                                } else {
                                    scheduler.releaseSlot(slot);
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        // Phase 1a: Try cached recipe first (fast path, avoids full recipe search)
        if (lastCrossRecipe != null &&
                recipeMap.ownsRecipe(lastCrossRecipe) &&
                lastCrossRecipe.getEUt() <= maxVoltage &&
                lastCrossRecipe.matches(false, importInventory, importFluids)) {
            budget.matchedAnyRecipe = true;
            if (!channelUsedRecipes.contains(lastCrossRecipe) &&
                    !(isPublicSharedRecipe(lastCrossRecipe, publicInventory, publicFluids) &&
                            budget.allocatedPublicRecipes.contains(lastCrossRecipe))) {
                if (lastCrossRecipe.getEUt() > budget.remainingBasePower) {
                    if (!checkRecipeDiagnostic(lastCrossRecipe, importInventory, importFluids)) {
                        ensureConditionFailureReason();
                    } else {
                        ensurePowerBudgetFailureReason();
                    }
                    channelUsedRecipes.add(lastCrossRecipe);
                } else {
                    RecipeSlot slot = scheduler.acquireSlot();
                    if (slot != null) {
                        if (!checkRecipe(lastCrossRecipe, importInventory, importFluids)) {
                            ensureConditionFailureReason();
                            channelUsedRecipes.add(lastCrossRecipe);
                            scheduler.releaseSlot(slot);
                        } else {
                            SlotAllocation alloc = allocateSlotParallel(slot, lastCrossRecipe, recipeMap,
                                    importInventory, importFluids, budget.remainingBasePower, budget.remainingParallel,
                                    scheduler.getPerSlotParallelCap());
                            if (alloc != null) {
                                alloc.colorChannel = colorChannel;
                                budget.allocations.add(alloc);
                                budget.allAllocatedRecipes.add(lastCrossRecipe);
                                if (isPublicSharedRecipe(lastCrossRecipe, publicInventory, publicFluids)) {
                                    budget.allocatedPublicRecipes.add(lastCrossRecipe);
                                }
                                channelUsedRecipes.add(lastCrossRecipe);
                                budget.remainingBasePower -= alloc.basePowerDemand;
                                budget.remainingParallel -= alloc.inputParallel;
                                this.previousRecipe = lastCrossRecipe;
                                if (isRecipeLockEnable()) {
                                    return;
                                }
                            } else {
                                scheduler.releaseSlot(slot);
                            }
                        }
                    }
                }
            }
        }

        // Phase 1b: Use RecipeIterator to find all distinct recipes in one pass
        if (budget.remainingParallel > 0 && budget.remainingBasePower > 0) {
            List<ItemStack> items = GTUtility.itemHandlerToList(importInventory);
            List<FluidStack> fluids = GTUtility.fluidHandlerToList(importFluids);
            List<ItemStack> filteredItems = items.stream()
                    .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
            List<FluidStack> filteredFluids = fluids.stream()
                    .filter(f -> f != null && f.amount != 0).collect(java.util.stream.Collectors.toList());

            RecipeIterator iterator = recipeMap.findRecipeIterator(filteredItems, filteredFluids,
                    recipe -> recipe.getEUt() <= maxVoltage &&
                            !channelUsedRecipes.contains(recipe) &&
                            !(isPublicSharedRecipe(recipe, publicInventory, publicFluids) &&
                                    budget.allocatedPublicRecipes.contains(recipe)) &&
                            recipe.matches(false, importInventory, importFluids));

            if (iterator != null) {
                for (Recipe excluded : channelUsedRecipes) {
                    iterator.exclude(excluded);
                }
                for (Recipe excluded : budget.allocatedPublicRecipes) {
                    iterator.exclude(excluded);
                }

                while (iterator.hasNext() && budget.remainingParallel > 0 && budget.remainingBasePower > 0) {
                    Recipe recipe = iterator.next();
                    if (recipe == null || !channelUsedRecipes.add(recipe)) continue;
                    budget.matchedAnyRecipe = true;
                    if (isPublicSharedRecipe(recipe, publicInventory, publicFluids) &&
                            budget.allocatedPublicRecipes.contains(recipe)) {
                        iterator.exclude(recipe);
                        continue;
                    }
                    if (recipe.getEUt() > budget.remainingBasePower) {
                        if (!checkRecipeDiagnostic(recipe, importInventory, importFluids)) {
                            ensureConditionFailureReason();
                        } else {
                            ensurePowerBudgetFailureReason();
                        }
                        iterator.exclude(recipe);
                        continue;
                    }

                    RecipeSlot slot = scheduler.acquireSlot();
                    if (slot == null) break;

                    if (!checkRecipe(recipe, importInventory, importFluids)) {
                        ensureConditionFailureReason();
                        scheduler.releaseSlot(slot);
                        iterator.exclude(recipe);
                        continue;
                    }

                    SlotAllocation alloc = allocateSlotParallel(slot, recipe, recipeMap,
                            importInventory, importFluids, budget.remainingBasePower, budget.remainingParallel,
                            scheduler.getPerSlotParallelCap());
                    if (alloc != null) {
                        alloc.colorChannel = colorChannel;
                        budget.allocations.add(alloc);
                        budget.allAllocatedRecipes.add(recipe);
                        if (isPublicSharedRecipe(recipe, publicInventory, publicFluids)) {
                            budget.allocatedPublicRecipes.add(recipe);
                        }
                        budget.remainingBasePower -= alloc.basePowerDemand;
                        budget.remainingParallel -= alloc.inputParallel;
                        this.previousRecipe = recipe;
                        lastCrossRecipe = recipe;
                        if (isRecipeLockEnable()) {
                            break;
                        }
                    } else {
                        scheduler.releaseSlot(slot);
                    }
                    iterator.exclude(recipe);
                }
            }
        }
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
        int colorChannel = UNTRACKED_CHANNEL;

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
                                                  int maxParallelBudget,
                                                  int perSlotParallelCap) {
        // Trim recipe outputs
        Recipe trimmed = Recipe.trimRecipeOutputs(recipe, recipeMap, metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());
        if (trimmed == null) {
            whyFailed = "配方输出数量超过机器限制（物品输出槽:" + metaTileEntity.getItemOutputLimit() +
                    ", 流体输出槽:" + metaTileEntity.getFluidOutputLimit() + "）";
            return null;
        }

        if (!canStartConditions(trimmed, importInventory, importFluids)) {
            ensureConditionFailureReason();
            return null;
        }

        long baseEUt = trimmed.getEUt();
        int baseDuration = trimmed.getDuration();

        // Power check: remaining base power must be enough for at least 1 recipe
        if (remainingBasePower < baseEUt) {
            ensurePowerBudgetFailureReason();
            return null;
        }

        int effectiveParallelLimit = Math.min(
                Math.max(1, getEffectiveParallelLimit(trimmed, importInventory, importFluids)),
                Math.max(1, getMaxRecipeOperations(trimmed)));
        int boundedParallelBudget = Math.min(maxParallelBudget, effectiveParallelLimit);

        // Parallel is limited by: min(parallelBudget, effectiveParallelLimit, remainingBasePower / baseEUt)
        // When shouldParallelMultiplyPower() is false, power does not constrain parallel
        int maxInputParallel;
        if (shouldParallelMultiplyPower()) {
            maxInputParallel = (int) Math.min(boundedParallelBudget, remainingBasePower / Math.max(1, baseEUt));
        } else {
            maxInputParallel = boundedParallelBudget;
        }
        // Applied after the branch above, because that branch hands back maxParallelBudget untouched and
        // would otherwise let one recipe claim the whole parallel budget for its slot.
        if (perSlotParallelCap > 0) {
            maxInputParallel = Math.min(maxInputParallel, perSlotParallelCap);
        }
        maxInputParallel = Math.max(1, maxInputParallel);

        int inputParallel = ParallelLogic.getMaxRecipeMultiplier(
                trimmed, importInventory, importFluids, maxInputParallel);
        if (inputParallel == 0) return null;

        // Limit by output space
        int outputParallel = limitOutputParallel(trimmed, getOutputInventory(), getOutputTank(),
                inputParallel,
                metaTileEntity.canVoidRecipeItemOutputs(),
                metaTileEntity.canVoidRecipeFluidOutputs());
        if (outputParallel == 0) {
            this.isOutputsFull = true;
            if (this.whyFailed.isEmpty()) {
                this.whyFailed = "输出仓已满，产物无法继续输出（清空或设置销毁产物）";
            }
            return null;
        }
        inputParallel = outputParallel;

        SlotAllocation alloc = new SlotAllocation(slot, trimmed, recipeMap, importInventory, importFluids,
                baseEUt, baseDuration, inputParallel);
        // When power doesn't multiply with parallel, reserve only single-recipe EU/t
        if (!shouldParallelMultiplyPower()) {
            alloc.basePowerDemand = baseEUt;
        }
        return alloc;
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
        // When shouldParallelMultiplyPower() is false, OC starts from single-recipe EU/t
        // and uses the machine's real max voltage instead of the inflated power budget
        long totalBaseEUt = shouldParallelMultiplyPower() ? baseEUt * inputParallel : baseEUt;
        OCParams params = new OCParams();
        OCResult result = new OCResult();
        params.initialize(totalBaseEUt, baseDuration, getNumberOfOCs(baseEUt));
        modifyOverclockPre(params, trimmed.propertyStorage());

        if (params.ocAmount() <= 0) {
            result.init(params.eut(), params.duration());
        } else {
            runOverclockingLogic(params, result, trimmed.propertyStorage(),
                    shouldParallelMultiplyPower() ? ocPowerBudget : getMaximumOverclockVoltage());
        }
        modifyOverclockPost(result, trimmed.propertyStorage());

        long overclockedEUt = result.eut();
        int overclockedDuration = result.duration();
        int maxOps = Math.max(1, getMaxRecipeOperations(trimmed));
        inputParallel = Math.min(inputParallel, maxOps);
        int rawSubTickParallel = Math.max(1, result.parallel());
        int maxSubTickByOps = Math.max(1, maxOps / inputParallel);
        int subTickParallel = Math.min(rawSubTickParallel, maxSubTickByOps);

        // --- Calculate parallel and total operations ---
        // A sub-tick OC can repeat a complete input-parallel group, but it must not turn a partial
        // pattern-buffer request into a larger consumption request. The ordinary recipe path first builds the
        // input-parallel recipe and then repeats that whole recipe for sub-tick OC; mirror that grouping here.
        long plannedTotalOperations = (long) inputParallel * subTickParallel;

        long plannedSlotEUt = subTickParallel < rawSubTickParallel ?
                saturatingMultiply(overclockedEUt, subTickParallel) :
                (result.parallelEUt() > 0 ? result.parallelEUt() : overclockedEUt);

        // Clamp totalSlotEUt to the allocated power budget (only when power multiplies with parallel)
        if (shouldParallelMultiplyPower() && plannedSlotEUt > ocPowerBudget) {
            long perParallelEUt = Math.max(1, plannedSlotEUt / inputParallel);
            inputParallel = (int) (ocPowerBudget / Math.max(1, perParallelEUt));
            if (inputParallel <= 0) return false;
            plannedSlotEUt = perParallelEUt * inputParallel;
            plannedTotalOperations = (long) inputParallel * subTickParallel;
        }

        int maximumPlannedOperations = saturatingOperationCount(plannedTotalOperations);
        int availableOperations = ParallelLogic.getMaxRecipeMultiplier(trimmed, alloc.importInventory,
                alloc.importFluids, maximumPlannedOperations);
        int subTickGroups = Math.min(subTickParallel, availableOperations / inputParallel);
        if (subTickGroups <= 0) {
            return false;
        }

        int outputOperations = limitOutputParallel(trimmed, getOutputInventory(), getOutputTank(),
                saturatingOperationCount((long) inputParallel * subTickGroups),
                metaTileEntity.canVoidRecipeItemOutputs(), metaTileEntity.canVoidRecipeFluidOutputs());
        subTickGroups = Math.min(subTickGroups, outputOperations / inputParallel);
        if (subTickGroups <= 0) {
            this.isOutputsFull = true;
            if (this.whyFailed.isEmpty()) {
                this.whyFailed = "输出仓已满，产物无法继续输出（清空或设置销毁产物）";
            }
            return false;
        }

        int totalParallelBasis = saturatingOperationCount((long) inputParallel * subTickGroups);
        int finalParallel = inputParallel;
        // result.eut() is the EU/t of one complete input-parallel group. Batch processing extends duration,
        // while sub-tick groups run in the same tick and therefore add their EU/t.
        long totalSlotEUt = saturatingMultiply(overclockedEUt, subTickGroups);

        // --- Batch processing ---
        int batchMultiplier = 1;
        if (isBatchEnable() && canBatchRecipe(trimmed) && totalParallelBasis < maxOps &&
                overclockedDuration <= 64 && overclockedDuration > 0) {
            int maxBatch = Math.min((int) Math.floor(128.0 / overclockedDuration),
                    Math.max(1, maxOps / totalParallelBasis));
            int bestBatch = 1;
            int lo = 1, hi = maxBatch;
            while (lo <= hi) {
                int mid = lo + (hi - lo) / 2;
                int neededParallel = (int) Math.min(Integer.MAX_VALUE, (long) totalParallelBasis * mid);
                int available = ParallelLogic.getMaxRecipeMultiplier(
                        trimmed, alloc.importInventory, alloc.importFluids, neededParallel);
                int outputCapacity = limitOutputParallel(trimmed, getOutputInventory(), getOutputTank(),
                        neededParallel, metaTileEntity.canVoidRecipeItemOutputs(),
                        metaTileEntity.canVoidRecipeFluidOutputs());
                if (available >= neededParallel && outputCapacity >= neededParallel) {
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

        int totalOperations = (int) Math.min(Integer.MAX_VALUE, (long) totalParallelBasis * batchMultiplier);
        int finalDuration = overclockedDuration * batchMultiplier;

        // A cross-recipe slot normally draws energy on its next tick. Check the total demand now so inputs are not
        // consumed for a slot that would immediately stall when the shared energy source is empty.
        if (!canStartCrossRecipeSlot(totalSlotEUt)) {
            return false;
        }

        if (!canStartConditions(trimmed, alloc.importInventory, alloc.importFluids)) {
            ensureConditionFailureReason();
            return false;
        }

        // --- Consume inputs ---
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        if (!holder.checkRecipe(trimmed, false)) {
            ensureConditionFailureReason();
            return false;
        }
        int prevCommitColor = this.activeCommitColor;
        this.activeCommitColor = alloc.colorChannel;
        boolean consumed;
        try {
            consumed = consumeRecipeInputs(trimmed, alloc.importInventory, alloc.importFluids, totalOperations);
        } finally {
            this.activeCommitColor = prevCommitColor;
        }
        if (!consumed) {
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
        List<IRecipeCondition> conditions = getRecipeConditions();
        if (!conditions.isEmpty()) {
            RecipeExecutionContext ctx = RecipeExecutionContext.forSlot(
                    alloc.slot, alloc.importInventory, alloc.importFluids);
            for (IRecipeCondition condition : conditions) {
                condition.onRecipeStart(ctx);
            }
        }
        logBoundCrossRecipeSlotStarted(alloc, totalOperations, finalDuration, totalSlotEUt, recipeDisplayName);
        return true;
    }

    private static int saturatingOperationCount(long operations) {
        if (operations <= 0) return 0;
        return operations > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) operations;
    }

    private static long saturatingMultiply(long value, int multiplier) {
        if (value == 0 || multiplier == 0) return 0;
        if (value > 0 && value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        if (value < 0 && value < Long.MIN_VALUE / multiplier) return Long.MIN_VALUE;
        return value * multiplier;
    }

    protected final long getRefillEnergyCredit() {
        return refillEnergyCredit;
    }

    protected final boolean isQuotingEnergyDraw() {
        return quotingEnergyDraw;
    }

    protected final long getQuotedStoredProbe() {
        return quotedStoredProbe;
    }

    @Override
    protected long getEnergyStored() {
        if (quotingEnergyDraw) {
            return quotedStoredProbe;
        }
        long base = super.getEnergyStored();
        if (refillEnergyCredit <= 0L) {
            return base;
        }
        return Math.min(getEnergyCapacity(), saturatingAdd(base, refillEnergyCredit));
    }

    /**
     * Side-effect-free quote of how much actual EU {@link #drawEnergy(long, boolean)} requires for {@code recipeEUt},
     * bounded above by {@code maxQuoteEU}. Always invoked first when splitting in-tick paid energy between completed
     * and still-running slots; default implementation probes {@link #drawEnergy(long, boolean)} via virtual
     * {@link #getEnergyStored()} and conservatively returns {@code maxQuoteEU} (zero credit) if {@code drawEnergy}
     * bypasses {@link #getEnergyStored()} without overriding this hook.
     */
    protected long quoteEnergyDraw(long recipeEUt, long maxQuoteEU) {
        if (!consumesEnergy() || recipeEUt <= 0L || maxQuoteEU <= 0L) {
            return 0L;
        }
        if (!probeDrawEnergy(recipeEUt, -1L)) {
            if (probeDrawEnergy(recipeEUt, 0L)) {
                return 0L;
            }
            if (probeDrawEnergy(recipeEUt, maxQuoteEU)) {
                long lo = 1L;
                long hi = maxQuoteEU;
                while (lo < hi) {
                    long mid = (lo + hi) >>> 1;
                    if (probeDrawEnergy(recipeEUt, mid)) {
                        hi = mid;
                    } else {
                        lo = mid + 1L;
                    }
                }
                return lo;
            }
        }
        return maxQuoteEU;
    }

    private boolean probeDrawEnergy(long recipeEUt, long simulatedStoredEU) {
        boolean prevQuoting = this.quotingEnergyDraw;
        long prevProbe = this.quotedStoredProbe;
        this.quotingEnergyDraw = true;
        this.quotedStoredProbe = simulatedStoredEU;
        try {
            return drawEnergy(recipeEUt, true);
        } finally {
            this.quotingEnergyDraw = prevQuoting;
            this.quotedStoredProbe = prevProbe;
        }
    }

    private long computeStillRunningPaidCredit(long actualPaidEU,
                                               long totalNominalPaidEUt,
                                               long stillRunningNominalEUt) {
        if (actualPaidEU <= 0L || totalNominalPaidEUt <= 0L || stillRunningNominalEUt <= 0L) {
            return 0L;
        }
        if (stillRunningNominalEUt >= totalNominalPaidEUt) {
            return actualPaidEU;
        }
        long completedNominalEUt = totalNominalPaidEUt - stillRunningNominalEUt;
        long completedPaidEU = Math.min(actualPaidEU,
                Math.max(0L, quoteEnergyDraw(completedNominalEUt, actualPaidEU)));
        return Math.max(0L, actualPaidEU - completedPaidEU);
    }

    /**
     * Verifies that the shared energy source can support the existing running slots and this candidate slot together.
     * The check intentionally mirrors the aggregate draw performed by {@link CrossRecipeParallelScheduler#tickSlots}
     * on the next tick.
     */
    protected boolean canStartCrossRecipeSlot(long candidateEUt) {
        if (candidateEUt <= 0) {
            return true;
        }

        long runningEUt = crossRecipeScheduler == null ? 0L : crossRecipeScheduler.getTotalScheduledEUt();
        long totalEUt = saturatingAdd(runningEUt, candidateEUt);
        if (drawEnergy(totalEUt, true)) {
            return true;
        }

        this.whyFailed = "电力不足，无法满足当前配方的运行需求";
        return false;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
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
                if (alloc.colorChannel != UNTRACKED_CHANNEL) {
                    this.lastColorChannel = alloc.colorChannel;
                }
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
        return commitInputsAndHolderCheck(recipe, importInventory, importFluids, parallelCount);
    }

    private static final byte ROLLBACK_SOURCE_RAW = 0;
    private static final byte ROLLBACK_SOURCE_ABILITY = 1;
    private static final byte ROLLBACK_SOURCE_DUAL_BUS = 2;
    private static final byte ROLLBACK_SOURCE_CHANNEL_CUSTOM = 3;

    private static final class DeferredIngredientAmount {

        final GTRecipeInput input;
        final long amount;

        DeferredIngredientAmount(@NotNull GTRecipeInput input, long amount) {
            this.input = input;
            this.amount = amount;
        }
    }

    private static final class ReplannedItems {

        final int[] counts;
        final ItemStack[] expectedSnapshots;

        ReplannedItems(@NotNull int[] counts, @NotNull ItemStack[] expectedSnapshots) {
            this.counts = counts;
            this.expectedSnapshots = expectedSnapshots;
        }
    }

    private static final class ReplannedFluids {

        final int[] amounts;
        final FluidStack[] expectedSnapshots;

        ReplannedFluids(@NotNull int[] amounts, @NotNull FluidStack[] expectedSnapshots) {
            this.amounts = amounts;
            this.expectedSnapshots = expectedSnapshots;
        }
    }

    private static final class PendingItemRollback {

        @Nullable
        WeakReference<IItemHandler> targetHandlerRef;
        final byte sourceKind;
        final int sourceColor;
        final int busIndex;
        final int withinColorBusIndex;
        final int targetSlot;
        @NotNull
        final String ownerMteId;
        final boolean hasOwnerPos;
        final long ownerPosLong;
        @NotNull
        ItemStack stack;

        PendingItemRollback(@Nullable IItemHandler targetHandler,
                            byte sourceKind,
                            int sourceColor,
                            int busIndex,
                            int withinColorBusIndex,
                            int targetSlot,
                            @NotNull String ownerMteId,
                            boolean hasOwnerPos,
                            long ownerPosLong,
                            @NotNull ItemStack stack) {
            this.targetHandlerRef = targetHandler == null ? null : new WeakReference<>(targetHandler);
            this.sourceKind = sourceKind;
            this.sourceColor = sourceColor;
            this.busIndex = busIndex;
            this.withinColorBusIndex = withinColorBusIndex;
            this.targetSlot = targetSlot;
            this.ownerMteId = ownerMteId;
            this.hasOwnerPos = hasOwnerPos;
            this.ownerPosLong = ownerPosLong;
            this.stack = stack;
        }

        NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("stack", stack.writeToNBT(new NBTTagCompound()));
            tag.setByte("sourceKind", sourceKind);
            tag.setInteger("sourceColor", sourceColor);
            tag.setInteger("busIndex", busIndex);
            tag.setInteger("withinColorBusIndex", withinColorBusIndex);
            tag.setInteger("targetSlot", targetSlot);
            if (!ownerMteId.isEmpty()) {
                tag.setString("ownerMteId", ownerMteId);
            }
            tag.setBoolean("hasOwnerPos", hasOwnerPos);
            if (hasOwnerPos) {
                tag.setLong("ownerPos", ownerPosLong);
            }
            return tag;
        }

        @Nullable
        static PendingItemRollback readFromNBT(@NotNull NBTTagCompound tag) {
            NBTTagCompound stackTag = tag.hasKey("stack", Constants.NBT.TAG_COMPOUND) ?
                    tag.getCompoundTag("stack") : tag;
            ItemStack loaded = new ItemStack(stackTag);
            if (loaded.isEmpty()) {
                return null;
            }
            byte kind = tag.hasKey("sourceKind") ? tag.getByte("sourceKind") : ROLLBACK_SOURCE_RAW;
            int color = tag.hasKey("sourceColor") ? tag.getInteger("sourceColor") : NO_CHANNEL;
            int busIdx = tag.hasKey("busIndex") ? tag.getInteger("busIndex") : -1;
            int colorBusIdx = tag.hasKey("withinColorBusIndex") ? tag.getInteger("withinColorBusIndex") : -1;
            int slot = tag.hasKey("targetSlot") ? tag.getInteger("targetSlot") : -1;
            String ownerId = tag.getString("ownerMteId");
            boolean hasPos = tag.getBoolean("hasOwnerPos");
            long posLong = hasPos ? tag.getLong("ownerPos") : 0L;
            return new PendingItemRollback(null, kind, color, busIdx, colorBusIdx, slot, ownerId, hasPos, posLong,
                    loaded);
        }
    }

    private static final class PendingFluidRollback implements IFluidTank {

        @Nullable
        WeakReference<IFluidTank> targetTankRef;
        final byte sourceKind;
        final int sourceColor;
        final int busIndex;
        final int tankIndex;
        final int withinColorTankIndex;
        @NotNull
        final String ownerMteId;
        final boolean hasOwnerPos;
        final long ownerPosLong;
        @Nullable
        FluidStack fluid;

        PendingFluidRollback(@Nullable IFluidTank targetTank,
                             byte sourceKind,
                             int sourceColor,
                             int busIndex,
                             int tankIndex,
                             int withinColorTankIndex,
                             @NotNull String ownerMteId,
                             boolean hasOwnerPos,
                             long ownerPosLong,
                             @NotNull FluidStack fluid) {
            this.targetTankRef = targetTank == null ? null : new WeakReference<>(targetTank);
            this.sourceKind = sourceKind;
            this.sourceColor = sourceColor;
            this.busIndex = busIndex;
            this.tankIndex = tankIndex;
            this.withinColorTankIndex = withinColorTankIndex;
            this.ownerMteId = ownerMteId;
            this.hasOwnerPos = hasOwnerPos;
            this.ownerPosLong = ownerPosLong;
            this.fluid = fluid;
        }

        void addAmount(int amount, @NotNull FluidStack template) {
            if (amount <= 0) return;
            if (fluid == null || fluid.amount <= 0) {
                fluid = GTUtility.copy(amount, template);
            } else {
                fluid.amount = saturatingIntAdd(fluid.amount, amount);
            }
        }

        NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            if (fluid != null && fluid.amount > 0) {
                tag.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
            }
            tag.setByte("sourceKind", sourceKind);
            tag.setInteger("sourceColor", sourceColor);
            tag.setInteger("busIndex", busIndex);
            tag.setInteger("tankIndex", tankIndex);
            tag.setInteger("withinColorTankIndex", withinColorTankIndex);
            if (!ownerMteId.isEmpty()) {
                tag.setString("ownerMteId", ownerMteId);
            }
            tag.setBoolean("hasOwnerPos", hasOwnerPos);
            if (hasOwnerPos) {
                tag.setLong("ownerPos", ownerPosLong);
            }
            return tag;
        }

        @Nullable
        static PendingFluidRollback readFromNBT(@NotNull NBTTagCompound tag) {
            NBTTagCompound fluidTag = tag.hasKey("fluid", Constants.NBT.TAG_COMPOUND) ?
                    tag.getCompoundTag("fluid") : tag;
            FluidStack loaded = FluidStack.loadFluidStackFromNBT(fluidTag);
            if (loaded == null || loaded.amount <= 0) {
                return null;
            }
            byte kind = tag.hasKey("sourceKind") ? tag.getByte("sourceKind") : ROLLBACK_SOURCE_RAW;
            int color = tag.hasKey("sourceColor") ? tag.getInteger("sourceColor") : NO_CHANNEL;
            int busIdx = tag.hasKey("busIndex") ? tag.getInteger("busIndex") : -1;
            int tankIdx = tag.hasKey("tankIndex") ? tag.getInteger("tankIndex") : 0;
            int colorTankIdx = tag.hasKey("withinColorTankIndex") ? tag.getInteger("withinColorTankIndex") : -1;
            String ownerId = tag.getString("ownerMteId");
            boolean hasPos = tag.getBoolean("hasOwnerPos");
            long posLong = hasPos ? tag.getLong("ownerPos") : 0L;
            return new PendingFluidRollback(null, kind, color, busIdx, tankIdx, colorTankIdx, ownerId, hasPos, posLong,
                    loaded);
        }

        @Override
        public @Nullable FluidStack getFluid() {
            return fluid == null || fluid.amount <= 0 ? null : fluid;
        }

        @Override
        public int getFluidAmount() {
            return fluid == null ? 0 : Math.max(0, fluid.amount);
        }

        @Override
        public int getCapacity() {
            return Math.max(1, getFluidAmount());
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(getFluid(), getCapacity());
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            if (fluid == null || fluid.amount <= 0 || maxDrain <= 0) {
                return null;
            }
            int drained = Math.min(maxDrain, fluid.amount);
            FluidStack out = GTUtility.copy(drained, fluid);
            if (doDrain) {
                fluid.amount -= drained;
                if (fluid.amount <= 0) {
                    fluid = null;
                }
            }
            return out;
        }
    }

    private static int saturatingIntAdd(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, sum);
    }

    private boolean commitInputsAndHolderCheck(@NotNull Recipe recipe,
                                               @NotNull IItemHandlerModifiable importInventory,
                                               @NotNull IMultipleTankHandler importFluids,
                                               int parallelCount) {
        flushPendingRollbacks();
        if (parallelCount <= 0 || !recipe.matches(false, importInventory, importFluids)) {
            return false;
        }

        int slotCount = importInventory.getSlots();
        int[] originalSlotCounts = new int[slotCount];
        int[] extractableSlotCounts = new int[slotCount];
        ItemStack[] slotSnapshots = new ItemStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            ItemStack raw = importInventory.getStackInSlot(i);
            int rawCount = raw.isEmpty() ? 0 : raw.getCount();
            originalSlotCounts[i] = rawCount;
            if (rawCount > 0) {
                ItemStack simulated = importInventory.extractItem(i, rawCount, true);
                if (!simulated.isEmpty() && ItemHandlerHelper.canItemStacksStack(raw, simulated)) {
                    int extractable = Math.min(rawCount, simulated.getCount());
                    extractableSlotCounts[i] = extractable;
                    slotSnapshots[i] = simulated.copy();
                } else {
                    slotSnapshots[i] = ItemStack.EMPTY;
                }
            } else {
                slotSnapshots[i] = ItemStack.EMPTY;
            }
        }

        List<GTRecipeInput> consumableItemInputs = new ArrayList<>();
        for (GTRecipeInput recipeInput : recipe.getInputs()) {
            if (!recipeInput.isNonConsumable() && recipeInput.getAmount() > 0) {
                consumableItemInputs.add(recipeInput);
            }
        }
        long[][] itemAllocMatrix = allocateItemIngredientMatrix(
                consumableItemInputs, parallelCount, slotSnapshots, extractableSlotCounts);
        if (itemAllocMatrix == null) {
            return false;
        }

        int[] phase1ItemPlan = new int[slotCount];
        int[] phase2ItemPlan = new int[slotCount];
        boolean[] slotRollbackable = new boolean[slotCount];
        for (int i = 0; i < slotCount; i++) {
            int totalForSlot = 0;
            for (int k = 0; k < consumableItemInputs.size(); k++) {
                totalForSlot += (int) itemAllocMatrix[k][i];
            }
            if (totalForSlot <= 0) continue;
            ItemStack sim = importInventory.extractItem(i, totalForSlot, true);
            if (sim.getCount() < totalForSlot || !ItemHandlerHelper.canItemStacksStack(sim, slotSnapshots[i])) {
                return false;
            }
            slotRollbackable[i] = canGuaranteeItemRollback(
                    importInventory, i, slotSnapshots[i], originalSlotCounts[i]);
            if (slotRollbackable[i]) {
                phase1ItemPlan[i] = totalForSlot;
            } else {
                phase2ItemPlan[i] = totalForSlot;
            }
        }
        List<DeferredIngredientAmount> deferredItemInputs = null;
        for (int k = 0; k < consumableItemInputs.size(); k++) {
            long deferredForInput = 0L;
            for (int i = 0; i < slotCount; i++) {
                if (!slotRollbackable[i] && itemAllocMatrix[k][i] > 0L) {
                    deferredForInput += itemAllocMatrix[k][i];
                }
            }
            if (deferredForInput > 0L) {
                if (deferredItemInputs == null) {
                    deferredItemInputs = new ArrayList<>();
                }
                deferredItemInputs.add(new DeferredIngredientAmount(consumableItemInputs.get(k), deferredForInput));
            }
        }

        List<IMultipleTankHandler.ITankEntry> tanks = importFluids.getFluidTanks();
        int tankCount = tanks.size();
        int[] originalTankAmounts = new int[tankCount];
        int[] drainableTankAmounts = new int[tankCount];
        FluidStack[] tankSnapshots = new FluidStack[tankCount];
        for (int t = 0; t < tankCount; t++) {
            FluidStack raw = tanks.get(t).getFluid();
            int rawAmount = raw == null ? 0 : Math.max(0, raw.amount);
            originalTankAmounts[t] = rawAmount;
            if (rawAmount > 0) {
                FluidStack simulated = tanks.get(t).drain(rawAmount, false);
                if (simulated != null && simulated.amount > 0 && raw.isFluidEqual(simulated)) {
                    int drainable = Math.min(rawAmount, simulated.amount);
                    drainableTankAmounts[t] = drainable;
                    tankSnapshots[t] = GTUtility.copy(drainable, simulated);
                }
            }
        }

        List<GTRecipeInput> consumableFluidInputs = new ArrayList<>();
        for (GTRecipeInput recipeFluidInput : recipe.getFluidInputs()) {
            if (!recipeFluidInput.isNonConsumable() && recipeFluidInput.getAmount() > 0) {
                consumableFluidInputs.add(recipeFluidInput);
            }
        }
        long[][] fluidAllocMatrix = allocateFluidIngredientMatrix(
                consumableFluidInputs, parallelCount, tankSnapshots, drainableTankAmounts);
        if (fluidAllocMatrix == null) {
            return false;
        }

        int[] phase1FluidPlan = new int[tankCount];
        int[] phase2FluidPlan = new int[tankCount];
        boolean[] tankRollbackable = new boolean[tankCount];
        for (int t = 0; t < tankCount; t++) {
            int totalForTank = 0;
            for (int k = 0; k < consumableFluidInputs.size(); k++) {
                totalForTank += (int) fluidAllocMatrix[k][t];
            }
            if (totalForTank <= 0) continue;
            FluidStack sim = tanks.get(t).drain(totalForTank, false);
            if (sim == null || sim.amount < totalForTank || tankSnapshots[t] == null ||
                    !sim.isFluidEqual(tankSnapshots[t])) {
                return false;
            }
            tankRollbackable[t] = canGuaranteeFluidRollbackViaFill(tanks.get(t), tankSnapshots[t]);
            if (tankRollbackable[t]) {
                phase1FluidPlan[t] = totalForTank;
            } else {
                phase2FluidPlan[t] = totalForTank;
            }
        }
        List<DeferredIngredientAmount> deferredFluidInputs = null;
        for (int k = 0; k < consumableFluidInputs.size(); k++) {
            long deferredForInput = 0L;
            for (int t = 0; t < tankCount; t++) {
                if (!tankRollbackable[t] && fluidAllocMatrix[k][t] > 0L) {
                    deferredForInput += fluidAllocMatrix[k][t];
                }
            }
            if (deferredForInput > 0L) {
                if (deferredFluidInputs == null) {
                    deferredFluidInputs = new ArrayList<>();
                }
                deferredFluidInputs.add(new DeferredIngredientAmount(consumableFluidInputs.get(k), deferredForInput));
            }
        }

        // Phase 1: extract only inputs whose rollback can be guaranteed before holder.checkRecipe(..., true)
        List<IItemHandlerModifiable> preItemNotifs = new ArrayList<>(metaTileEntity.getNotifiedItemInputList());
        List<IFluidHandler> preFluidNotifs = new ArrayList<>(metaTileEntity.getNotifiedFluidInputList());
        ItemStack[] extractedItems = new ItemStack[slotCount];
        FluidStack[] drainedFluids = new FluidStack[tankCount];
        boolean extractFailed = false;
        for (int i = 0; i < slotCount; i++) {
            if (phase1ItemPlan[i] <= 0) continue;
            ItemStack extracted = importInventory.extractItem(i, phase1ItemPlan[i], false);
            extractedItems[i] = extracted;
            if (extracted.getCount() < phase1ItemPlan[i] ||
                    !ItemHandlerHelper.canItemStacksStack(extracted, slotSnapshots[i])) {
                extractFailed = true;
                break;
            }
        }
        if (!extractFailed) {
            for (int t = 0; t < tankCount; t++) {
                if (phase1FluidPlan[t] <= 0) continue;
                FluidStack drained = tanks.get(t).drain(phase1FluidPlan[t], true);
                drainedFluids[t] = drained;
                if (drained == null || drained.amount < phase1FluidPlan[t] ||
                        tankSnapshots[t] == null || !drained.isFluidEqual(tankSnapshots[t])) {
                    extractFailed = true;
                    break;
                }
            }
        }

        if (extractFailed || !invokeHolderCommitCheck(recipe, importInventory, phase2ItemPlan, slotSnapshots,
                tanks, phase2FluidPlan, tankSnapshots)) {
            rollbackExtractedInputs(importInventory, originalSlotCounts, extractedItems,
                    importFluids, tanks, originalTankAmounts, drainedFluids);
            restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
            if (!extractFailed) {
                ensureConditionFailureReason();
            }
            return false;
        }

        // Phase 2: holder auxiliary commit succeeded; re-verify planned item/fluid identity + NBT before extracting
        if (deferredItemInputs != null) {
            ReplannedItems activeItems = canSimulateItemPlan(importInventory, phase2ItemPlan, slotSnapshots) ?
                    new ReplannedItems(phase2ItemPlan, slotSnapshots) :
                    replanDeferredItems(importInventory, deferredItemInputs);
            if (activeItems == null) {
                boolean lost = recordAbortedHolderLosses(importInventory, originalSlotCounts, slotSnapshots,
                        phase1ItemPlan, phase2ItemPlan, extractedItems, tanks, originalTankAmounts, tankSnapshots,
                        phase1FluidPlan, phase2FluidPlan, drainedFluids);
                rollbackExtractedInputs(importInventory, originalSlotCounts, extractedItems,
                        importFluids, tanks, originalTankAmounts, drainedFluids);
                restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
                if (lost) {
                    markHolderLossInputInvalidated(importInventory);
                }
                return false;
            }
            for (int i = 0; i < slotCount; i++) {
                if (activeItems.counts[i] <= 0) continue;
                ItemStack expected = activeItems.expectedSnapshots[i];
                ItemStack extracted = importInventory.extractItem(i, activeItems.counts[i], false);
                boolean matchesExpected = !expected.isEmpty() &&
                        ItemHandlerHelper.canItemStacksStack(extracted, expected);
                if (!matchesExpected) {
                    returnUnexpectedExtractedItem(importInventory, i, extracted);
                } else {
                    mergeExtractedItem(extractedItems, i, extracted);
                }
                if (!matchesExpected || extracted.getCount() < activeItems.counts[i]) {
                    boolean lost = recordAbortedHolderLosses(importInventory, originalSlotCounts, slotSnapshots,
                            phase1ItemPlan, phase2ItemPlan, extractedItems, tanks, originalTankAmounts, tankSnapshots,
                            phase1FluidPlan, phase2FluidPlan, drainedFluids);
                    rollbackExtractedInputs(importInventory, originalSlotCounts, extractedItems,
                            importFluids, tanks, originalTankAmounts, drainedFluids);
                    restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
                    if (lost) {
                        markHolderLossInputInvalidated(importInventory);
                    }
                    return false;
                }
            }
        }
        if (deferredFluidInputs != null) {
            ReplannedFluids activeFluids = canSimulateFluidPlan(tanks, phase2FluidPlan, tankSnapshots) ?
                    new ReplannedFluids(phase2FluidPlan, tankSnapshots) :
                    replanDeferredFluids(tanks, deferredFluidInputs);
            if (activeFluids == null) {
                boolean lost = recordAbortedHolderLosses(importInventory, originalSlotCounts, slotSnapshots,
                        phase1ItemPlan, phase2ItemPlan, extractedItems, tanks, originalTankAmounts, tankSnapshots,
                        phase1FluidPlan, phase2FluidPlan, drainedFluids);
                rollbackExtractedInputs(importInventory, originalSlotCounts, extractedItems,
                        importFluids, tanks, originalTankAmounts, drainedFluids);
                restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
                if (lost) {
                    markHolderLossInputInvalidated(importInventory);
                }
                return false;
            }
            for (int t = 0; t < tankCount; t++) {
                if (activeFluids.amounts[t] <= 0) continue;
                FluidStack expected = activeFluids.expectedSnapshots[t];
                FluidStack drained = tanks.get(t).drain(activeFluids.amounts[t], true);
                boolean matchesExpected = drained != null && expected != null && drained.isFluidEqual(expected);
                if (!matchesExpected) {
                    returnUnexpectedDrainedFluid(importFluids, tanks.get(t), drained);
                } else {
                    mergeDrainedFluid(drainedFluids, t, drained);
                }
                if (!matchesExpected || drained.amount < activeFluids.amounts[t]) {
                    boolean lost = recordAbortedHolderLosses(importInventory, originalSlotCounts, slotSnapshots,
                            phase1ItemPlan, phase2ItemPlan, extractedItems, tanks, originalTankAmounts, tankSnapshots,
                            phase1FluidPlan, phase2FluidPlan, drainedFluids);
                    rollbackExtractedInputs(importInventory, originalSlotCounts, extractedItems,
                            importFluids, tanks, originalTankAmounts, drainedFluids);
                    restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
                    if (lost) {
                        markHolderLossInputInvalidated(importInventory);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static long[][] allocateItemIngredientMatrix(@NotNull List<GTRecipeInput> inputs,
                                                         int parallelCount,
                                                         @NotNull ItemStack[] snapshots,
                                                         @NotNull int[] extractableCounts) {
        int m = inputs.size();
        int slotCount = snapshots.length;
        long[][] matrix = new long[m][slotCount];
        if (m == 0) {
            return matrix;
        }
        int[] naturalOrder = new int[m];
        for (int k = 0; k < m; k++) naturalOrder[k] = k;
        if (tryAllocateItemsByOrder(inputs, parallelCount, snapshots, extractableCounts, naturalOrder, matrix)) {
            return matrix;
        }
        if (m > 1) {
            List<Integer> constrainedOrder = new ArrayList<>(m);
            for (int k = 0; k < m; k++) constrainedOrder.add(k);
            constrainedOrder.sort(Comparator
                    .comparingInt((Integer k) -> countMatchingItemSlots(inputs.get(k), snapshots, extractableCounts))
                    .thenComparingLong(k -> sumMatchingItemCount(inputs.get(k), snapshots, extractableCounts)));
            int[] retryOrder = new int[m];
            for (int idx = 0; idx < m; idx++) retryOrder[idx] = constrainedOrder.get(idx);
            matrix = new long[m][slotCount];
            if (tryAllocateItemsByOrder(inputs, parallelCount, snapshots, extractableCounts, retryOrder, matrix)) {
                return matrix;
            }
        }
        return null;
    }

    private static boolean tryAllocateItemsByOrder(@NotNull List<GTRecipeInput> inputs,
                                                   int parallelCount,
                                                   @NotNull ItemStack[] snapshots,
                                                   @NotNull int[] extractableCounts,
                                                   @NotNull int[] inputOrder,
                                                   @NotNull long[][] outMatrix) {
        int[] available = extractableCounts.clone();
        for (int k : inputOrder) {
            GTRecipeInput input = inputs.get(k);
            long remaining = (long) input.getAmount() * parallelCount;
            for (int i = 0; i < snapshots.length && remaining > 0L; i++) {
                if (available[i] <= 0) continue;
                ItemStack s = snapshots[i];
                if (s.isEmpty() || !input.acceptsStack(s)) continue;
                int toTake = (int) Math.min(remaining, available[i]);
                available[i] -= toTake;
                outMatrix[k][i] += toTake;
                remaining -= toTake;
            }
            if (remaining > 0L) {
                return false;
            }
        }
        return true;
    }

    private static int countMatchingItemSlots(@NotNull GTRecipeInput input,
                                              @NotNull ItemStack[] snapshots,
                                              @NotNull int[] available) {
        int matches = 0;
        for (int i = 0; i < snapshots.length; i++) {
            if (available[i] > 0 && !snapshots[i].isEmpty() && input.acceptsStack(snapshots[i])) {
                matches++;
            }
        }
        return matches;
    }

    private static long sumMatchingItemCount(@NotNull GTRecipeInput input,
                                             @NotNull ItemStack[] snapshots,
                                             @NotNull int[] available) {
        long sum = 0L;
        for (int i = 0; i < snapshots.length; i++) {
            if (available[i] > 0 && !snapshots[i].isEmpty() && input.acceptsStack(snapshots[i])) {
                sum += available[i];
            }
        }
        return sum;
    }

    @Nullable
    private static long[][] allocateFluidIngredientMatrix(@NotNull List<GTRecipeInput> inputs,
                                                          int parallelCount,
                                                          @NotNull FluidStack[] snapshots,
                                                          @NotNull int[] drainableAmounts) {
        int m = inputs.size();
        int tankCount = snapshots.length;
        long[][] matrix = new long[m][tankCount];
        if (m == 0) {
            return matrix;
        }
        int[] naturalOrder = new int[m];
        for (int k = 0; k < m; k++) naturalOrder[k] = k;
        if (tryAllocateFluidsByOrder(inputs, parallelCount, snapshots, drainableAmounts, naturalOrder, matrix)) {
            return matrix;
        }
        if (m > 1) {
            List<Integer> constrainedOrder = new ArrayList<>(m);
            for (int k = 0; k < m; k++) constrainedOrder.add(k);
            constrainedOrder.sort(Comparator
                    .comparingInt((Integer k) -> countMatchingFluidTanks(inputs.get(k), snapshots, drainableAmounts))
                    .thenComparingLong(k -> sumMatchingFluidAmount(inputs.get(k), snapshots, drainableAmounts)));
            int[] retryOrder = new int[m];
            for (int idx = 0; idx < m; idx++) retryOrder[idx] = constrainedOrder.get(idx);
            matrix = new long[m][tankCount];
            if (tryAllocateFluidsByOrder(inputs, parallelCount, snapshots, drainableAmounts, retryOrder, matrix)) {
                return matrix;
            }
        }
        return null;
    }

    private static boolean tryAllocateFluidsByOrder(@NotNull List<GTRecipeInput> inputs,
                                                    int parallelCount,
                                                    @NotNull FluidStack[] snapshots,
                                                    @NotNull int[] drainableAmounts,
                                                    @NotNull int[] inputOrder,
                                                    @NotNull long[][] outMatrix) {
        int[] available = drainableAmounts.clone();
        for (int k : inputOrder) {
            GTRecipeInput input = inputs.get(k);
            long remaining = (long) input.getAmount() * parallelCount;
            for (int t = 0; t < snapshots.length && remaining > 0L; t++) {
                if (available[t] <= 0) continue;
                FluidStack f = snapshots[t];
                if (f == null || !input.acceptsFluid(f)) continue;
                int toDrain = (int) Math.min(remaining, available[t]);
                available[t] -= toDrain;
                outMatrix[k][t] += toDrain;
                remaining -= toDrain;
            }
            if (remaining > 0L) {
                return false;
            }
        }
        return true;
    }

    private static int countMatchingFluidTanks(@NotNull GTRecipeInput input,
                                               @NotNull FluidStack[] snapshots,
                                               @NotNull int[] available) {
        int matches = 0;
        for (int t = 0; t < snapshots.length; t++) {
            if (available[t] > 0 && snapshots[t] != null && input.acceptsFluid(snapshots[t])) {
                matches++;
            }
        }
        return matches;
    }

    private static long sumMatchingFluidAmount(@NotNull GTRecipeInput input,
                                              @NotNull FluidStack[] snapshots,
                                              @NotNull int[] available) {
        long sum = 0L;
        for (int t = 0; t < snapshots.length; t++) {
            if (available[t] > 0 && snapshots[t] != null && input.acceptsFluid(snapshots[t])) {
                sum += available[t];
            }
        }
        return sum;
    }

    private static boolean canSimulateItemPlan(@NotNull IItemHandlerModifiable importInventory,
                                               @NotNull int[] plan,
                                               @NotNull ItemStack[] expectedSnapshots) {
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] > 0) {
                ItemStack expected = expectedSnapshots[i];
                if (expected == null || expected.isEmpty()) return false;
                ItemStack current = importInventory.getStackInSlot(i);
                if (current.isEmpty() || !ItemHandlerHelper.canItemStacksStack(current, expected)) {
                    return false;
                }
                ItemStack simulated = importInventory.extractItem(i, plan[i], true);
                if (simulated.getCount() < plan[i] || !ItemHandlerHelper.canItemStacksStack(simulated, expected)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static ReplannedItems replanDeferredItems(@NotNull IItemHandlerModifiable importInventory,
                                                      @NotNull List<DeferredIngredientAmount> deferredInputs) {
        int slotCount = importInventory.getSlots();
        int[] extractable = new int[slotCount];
        ItemStack[] snapshots = new ItemStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            ItemStack raw = importInventory.getStackInSlot(i);
            int rawCount = raw.isEmpty() ? 0 : raw.getCount();
            if (rawCount > 0) {
                ItemStack sim = importInventory.extractItem(i, rawCount, true);
                if (!sim.isEmpty() && ItemHandlerHelper.canItemStacksStack(raw, sim)) {
                    extractable[i] = Math.min(rawCount, sim.getCount());
                    snapshots[i] = sim.copy();
                } else {
                    snapshots[i] = ItemStack.EMPTY;
                }
            } else {
                snapshots[i] = ItemStack.EMPTY;
            }
        }
        int m = deferredInputs.size();
        List<GTRecipeInput> inputs = new ArrayList<>(m);
        for (DeferredIngredientAmount d : deferredInputs) inputs.add(d.input);
        long[][] matrix = new long[m][slotCount];
        int[] order = new int[m];
        for (int k = 0; k < m; k++) order[k] = k;
        if (!tryAllocateDeferredItems(deferredInputs, snapshots, extractable, order, matrix)) {
            if (m <= 1) return null;
            List<Integer> constrained = new ArrayList<>(m);
            for (int k = 0; k < m; k++) constrained.add(k);
            constrained.sort(Comparator
                    .comparingInt((Integer k) -> countMatchingItemSlots(inputs.get(k), snapshots, extractable))
                    .thenComparingLong(k -> sumMatchingItemCount(inputs.get(k), snapshots, extractable)));
            for (int idx = 0; idx < m; idx++) order[idx] = constrained.get(idx);
            matrix = new long[m][slotCount];
            if (!tryAllocateDeferredItems(deferredInputs, snapshots, extractable, order, matrix)) {
                return null;
            }
        }
        int[] plan = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            for (int k = 0; k < m; k++) plan[i] += (int) matrix[k][i];
        }
        return canSimulateItemPlan(importInventory, plan, snapshots) ? new ReplannedItems(plan, snapshots) : null;
    }

    private static boolean tryAllocateDeferredItems(@NotNull List<DeferredIngredientAmount> deferredInputs,
                                                    @NotNull ItemStack[] snapshots,
                                                    @NotNull int[] extractable,
                                                    @NotNull int[] order,
                                                    @NotNull long[][] outMatrix) {
        int[] available = extractable.clone();
        for (int k : order) {
            DeferredIngredientAmount deferred = deferredInputs.get(k);
            long remaining = deferred.amount;
            for (int i = 0; i < snapshots.length && remaining > 0L; i++) {
                if (available[i] <= 0) continue;
                ItemStack s = snapshots[i];
                if (s.isEmpty() || !deferred.input.acceptsStack(s)) continue;
                int toTake = (int) Math.min(remaining, available[i]);
                available[i] -= toTake;
                outMatrix[k][i] += toTake;
                remaining -= toTake;
            }
            if (remaining > 0L) return false;
        }
        return true;
    }

    private static boolean canSimulateFluidPlan(@NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                                @NotNull int[] plan,
                                                @NotNull FluidStack[] expectedSnapshots) {
        for (int t = 0; t < plan.length; t++) {
            if (plan[t] > 0) {
                FluidStack expected = expectedSnapshots[t];
                if (expected == null || expected.amount <= 0) return false;
                FluidStack current = tanks.get(t).getFluid();
                if (current == null || !current.isFluidEqual(expected)) {
                    return false;
                }
                FluidStack simulated = tanks.get(t).drain(plan[t], false);
                if (simulated == null || simulated.amount < plan[t] || !simulated.isFluidEqual(expected)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static ReplannedFluids replanDeferredFluids(@NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                                        @NotNull List<DeferredIngredientAmount> deferredInputs) {
        int tankCount = tanks.size();
        int[] drainable = new int[tankCount];
        FluidStack[] snapshots = new FluidStack[tankCount];
        for (int t = 0; t < tankCount; t++) {
            FluidStack raw = tanks.get(t).getFluid();
            int rawAmount = raw == null ? 0 : Math.max(0, raw.amount);
            if (rawAmount > 0) {
                FluidStack sim = tanks.get(t).drain(rawAmount, false);
                if (sim != null && sim.amount > 0 && raw.isFluidEqual(sim)) {
                    int amt = Math.min(rawAmount, sim.amount);
                    drainable[t] = amt;
                    snapshots[t] = GTUtility.copy(amt, sim);
                }
            }
        }
        int m = deferredInputs.size();
        List<GTRecipeInput> inputs = new ArrayList<>(m);
        for (DeferredIngredientAmount d : deferredInputs) inputs.add(d.input);
        long[][] matrix = new long[m][tankCount];
        int[] order = new int[m];
        for (int k = 0; k < m; k++) order[k] = k;
        if (!tryAllocateDeferredFluids(deferredInputs, snapshots, drainable, order, matrix)) {
            if (m <= 1) return null;
            List<Integer> constrained = new ArrayList<>(m);
            for (int k = 0; k < m; k++) constrained.add(k);
            constrained.sort(Comparator
                    .comparingInt((Integer k) -> countMatchingFluidTanks(inputs.get(k), snapshots, drainable))
                    .thenComparingLong(k -> sumMatchingFluidAmount(inputs.get(k), snapshots, drainable)));
            for (int idx = 0; idx < m; idx++) order[idx] = constrained.get(idx);
            matrix = new long[m][tankCount];
            if (!tryAllocateDeferredFluids(deferredInputs, snapshots, drainable, order, matrix)) {
                return null;
            }
        }
        int[] plan = new int[tankCount];
        for (int t = 0; t < tankCount; t++) {
            for (int k = 0; k < m; k++) plan[t] += (int) matrix[k][t];
        }
        return canSimulateFluidPlan(tanks, plan, snapshots) ? new ReplannedFluids(plan, snapshots) : null;
    }

    private static boolean tryAllocateDeferredFluids(@NotNull List<DeferredIngredientAmount> deferredInputs,
                                                     @NotNull FluidStack[] snapshots,
                                                     @NotNull int[] drainable,
                                                     @NotNull int[] order,
                                                     @NotNull long[][] outMatrix) {
        int[] available = drainable.clone();
        for (int k : order) {
            DeferredIngredientAmount deferred = deferredInputs.get(k);
            long remaining = deferred.amount;
            for (int t = 0; t < snapshots.length && remaining > 0L; t++) {
                if (available[t] <= 0) continue;
                FluidStack f = snapshots[t];
                if (f == null || !deferred.input.acceptsFluid(f)) continue;
                int toDrain = (int) Math.min(remaining, available[t]);
                available[t] -= toDrain;
                outMatrix[k][t] += toDrain;
                remaining -= toDrain;
            }
            if (remaining > 0L) return false;
        }
        return true;
    }

    private boolean recordAbortedHolderLosses(@NotNull IItemHandlerModifiable importInventory,
                                              @NotNull int[] originalSlotCounts,
                                              @NotNull ItemStack[] slotSnapshots,
                                              @NotNull int[] phase1ItemPlan,
                                              @NotNull int[] phase2ItemPlan,
                                              @NotNull ItemStack[] extractedItems,
                                              @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                              @NotNull int[] originalTankAmounts,
                                              @NotNull FluidStack[] tankSnapshots,
                                              @NotNull int[] phase1FluidPlan,
                                              @NotNull int[] phase2FluidPlan,
                                              @NotNull FluidStack[] drainedFluids) {
        boolean anyLost = false;
        for (int i = 0; i < phase2ItemPlan.length; i++) {
            if (phase2ItemPlan[i] <= 0 || slotSnapshots[i] == null || slotSnapshots[i].isEmpty()) continue;
            int alreadyTracked = extractedItems[i] == null ? 0 : extractedItems[i].getCount();
            ItemStack now = importInventory.getStackInSlot(i);
            int nowMatching = (!now.isEmpty() && ItemHandlerHelper.canItemStacksStack(now, slotSnapshots[i])) ?
                    now.getCount() : 0;
            int expectedBeforePhase2 = Math.max(0, originalSlotCounts[i] - phase1ItemPlan[i]);
            int lostToHolder = Math.min(phase2ItemPlan[i],
                    Math.max(0, expectedBeforePhase2 - nowMatching - Math.max(0, alreadyTracked - phase1ItemPlan[i])));
            if (lostToHolder > 0) {
                mergeExtractedItem(extractedItems, i, ItemHandlerHelper.copyStackWithSize(slotSnapshots[i], lostToHolder));
                anyLost = true;
            }
        }
        for (int t = 0; t < phase2FluidPlan.length; t++) {
            if (phase2FluidPlan[t] <= 0 || tankSnapshots[t] == null || tankSnapshots[t].amount <= 0) continue;
            int alreadyTracked = drainedFluids[t] == null ? 0 : drainedFluids[t].amount;
            FluidStack now = tanks.get(t).getFluid();
            int nowMatching = (now != null && now.isFluidEqual(tankSnapshots[t])) ? Math.max(0, now.amount) : 0;
            int expectedBeforePhase2 = Math.max(0, originalTankAmounts[t] - phase1FluidPlan[t]);
            int lostToHolder = Math.min(phase2FluidPlan[t],
                    Math.max(0, expectedBeforePhase2 - nowMatching - Math.max(0, alreadyTracked - phase1FluidPlan[t])));
            if (lostToHolder > 0) {
                mergeDrainedFluid(drainedFluids, t, GTUtility.copy(lostToHolder, tankSnapshots[t]));
                anyLost = true;
            }
        }
        return anyLost;
    }

    private void restorePreCommitInputNotifications(@NotNull List<IItemHandlerModifiable> preItemNotifs,
                                                    @NotNull List<IFluidHandler> preFluidNotifs) {
        List<IItemHandlerModifiable> itemNotifs = metaTileEntity.getNotifiedItemInputList();
        itemNotifs.clear();
        itemNotifs.addAll(preItemNotifs);
        List<IFluidHandler> fluidNotifs = metaTileEntity.getNotifiedFluidInputList();
        fluidNotifs.clear();
        fluidNotifs.addAll(preFluidNotifs);
    }

    private void markHolderLossInputInvalidated(@NotNull IItemHandlerModifiable importInventory) {
        if (shouldUseDistinctInputBuses()) {
            IItemHandlerModifiable bus = currentDistinctInputBus != null ? currentDistinctInputBus : importInventory;
            if (!invalidatedInputList.contains(bus)) {
                invalidatedInputList.add(bus);
            }
        } else {
            this.invalidInputsForRecipes = true;
        }
        ensureConditionFailureReason();
    }

    private void returnUnexpectedExtractedItem(@NotNull IItemHandlerModifiable importInventory,
                                               int slot,
                                               @NotNull ItemStack unexpected) {
        if (unexpected.isEmpty()) return;
        ItemStack rem = importInventory.insertItem(slot, unexpected.copy(), false);
        if (!rem.isEmpty()) {
            rem = ItemHandlerHelper.insertItemStacked(importInventory, rem, false);
        }
        if (!rem.isEmpty()) {
            Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, slot);
            enqueuePendingItemRollback(leaf.getFirst(), leaf.getSecond(), rem);
        }
    }

    private void returnUnexpectedDrainedFluid(@NotNull IMultipleTankHandler importFluids,
                                              @NotNull IMultipleTankHandler.ITankEntry tank,
                                              @Nullable FluidStack unexpected) {
        if (unexpected == null || unexpected.amount <= 0) return;
        int filled = tank.fill(unexpected.copy(), true);
        int rem = unexpected.amount - Math.max(0, filled);
        if (rem > 0) {
            rem -= Math.max(0, importFluids.fill(GTUtility.copy(rem, unexpected), true));
        }
        if (rem > 0) {
            enqueuePendingFluidRollback(unwrapFluidTank(tank), GTUtility.copy(rem, unexpected));
        }
    }

    private static void mergeExtractedItem(@NotNull ItemStack[] extractedItems, int slot, @NotNull ItemStack added) {
        if (added.isEmpty()) return;
        ItemStack existing = extractedItems[slot];
        if (existing == null || existing.isEmpty()) {
            extractedItems[slot] = added;
        } else {
            ItemStack merged = existing.copy();
            merged.grow(added.getCount());
            extractedItems[slot] = merged;
        }
    }

    private static void mergeDrainedFluid(@NotNull FluidStack[] drainedFluids, int tankIdx, @Nullable FluidStack added) {
        if (added == null || added.amount <= 0) return;
        FluidStack existing = drainedFluids[tankIdx];
        if (existing == null || existing.amount <= 0) {
            drainedFluids[tankIdx] = added;
        } else {
            drainedFluids[tankIdx] = GTUtility.copy(existing.amount + added.amount, existing);
        }
    }

    private boolean invokeHolderCommitCheck(@NotNull Recipe recipe,
                                            @NotNull IItemHandlerModifiable importInventory,
                                            @NotNull int[] phase2ItemPlan,
                                            @NotNull ItemStack[] slotSnapshots,
                                            @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                            @NotNull int[] phase2FluidPlan,
                                            @NotNull FluidStack[] tankSnapshots) {
        IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> reservedItems = null;
        for (int i = 0; i < phase2ItemPlan.length; i++) {
            if (phase2ItemPlan[i] <= 0 || slotSnapshots[i] == null || slotSnapshots[i].isEmpty()) continue;
            Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, i);
            if (reservedItems == null) {
                reservedItems = new IdentityHashMap<>();
            }
            Map<Integer, ItemStack> slotMap = reservedItems.computeIfAbsent(leaf.getFirst(), k -> new HashMap<>());
            ItemStack prev = slotMap.get(leaf.getSecond());
            int newReserved = prev == null ? phase2ItemPlan[i] : saturatingIntAdd(prev.getCount(), phase2ItemPlan[i]);
            slotMap.put(leaf.getSecond(), ItemHandlerHelper.copyStackWithSize(slotSnapshots[i], newReserved));
        }

        IdentityHashMap<IFluidTank, FluidStack> reservedFluids = null;
        for (int t = 0; t < phase2FluidPlan.length; t++) {
            if (phase2FluidPlan[t] <= 0 || tankSnapshots[t] == null || tankSnapshots[t].amount <= 0) continue;
            IFluidTank leaf = unwrapFluidTank(tanks.get(t));
            if (reservedFluids == null) {
                reservedFluids = new IdentityHashMap<>();
            }
            FluidStack prev = reservedFluids.get(leaf);
            int newReserved = prev == null ? phase2FluidPlan[t] : saturatingIntAdd(prev.amount, phase2FluidPlan[t]);
            reservedFluids.put(leaf, GTUtility.copy(newReserved, tankSnapshots[t]));
        }

        final IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> finalItemReservations = reservedItems;
        final IdentityHashMap<IFluidTank, FluidStack> finalFluidReservations = reservedFluids;
        UnaryOperator<IItemHandlerModifiable> itemMask = finalItemReservations == null ? null :
                base -> maskReservedItemSlots(base, finalItemReservations);
        UnaryOperator<IMultipleTankHandler> fluidMask = finalFluidReservations == null ? null :
                base -> maskReservedFluidTanks(base, finalFluidReservations);

        UnaryOperator<IItemHandlerModifiable> prevItemMask = this.activeHolderItemMask;
        UnaryOperator<IMultipleTankHandler> prevFluidMask = this.activeHolderFluidMask;
        this.activeHolderItemMask = itemMask;
        this.activeHolderFluidMask = fluidMask;
        try {
            if (metaTileEntity instanceof RecipeMapMultiblockController controller) {
                return controller.checkRecipeWithReservedInputs(recipe, itemMask, fluidMask);
            }
            return ((IRecipeMapHolder) metaTileEntity).checkRecipe(recipe, true);
        } finally {
            this.activeHolderItemMask = prevItemMask;
            this.activeHolderFluidMask = prevFluidMask;
        }
    }

    private static IItemHandlerModifiable maskReservedItemSlots(
            @NotNull IItemHandlerModifiable base,
            @NotNull IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> reservedByLeaf) {
        if (containsReservedItemView(base)) {
            return base;
        }
        return new ReservedItemHandlerView(base, reservedByLeaf);
    }

    private static boolean containsReservedItemView(@NotNull IItemHandler handler) {
        if (handler instanceof ReservedItemHandlerView) {
            return true;
        }
        if (handler instanceof DualHandler dual) {
            return containsReservedItemView(dual.getItemDelegate());
        }
        if (handler instanceof ItemHandlerList list) {
            for (IItemHandler sub : list.getBackingHandlers()) {
                if (containsReservedItemView(sub)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static IMultipleTankHandler maskReservedFluidTanks(
            @NotNull IMultipleTankHandler base,
            @NotNull IdentityHashMap<IFluidTank, FluidStack> reservedByLeaf) {
        List<IMultipleTankHandler.ITankEntry> entries = base.getFluidTanks();
        List<IFluidTank> wrapped = null;
        for (int i = 0; i < entries.size(); i++) {
            IMultipleTankHandler.ITankEntry entry = entries.get(i);
            if (isAlreadyReservedFluidEntry(entry)) {
                if (wrapped != null) {
                    wrapped.add(entry);
                }
                continue;
            }
            IFluidTank leaf = unwrapFluidTank(entry);
            FluidStack reserved = reservedByLeaf.get(leaf);
            if (reserved != null && reserved.amount > 0) {
                if (wrapped == null) {
                    wrapped = new ArrayList<>(entries.size());
                    for (int j = 0; j < i; j++) {
                        wrapped.add(entries.get(j));
                    }
                }
                wrapped.add(new ReservedFluidTank(entry, reserved));
            } else if (wrapped != null) {
                wrapped.add(entry);
            }
        }
        return wrapped == null ? base : new FluidTankList(base.allowSameFluidFill(), wrapped);
    }

    private static boolean isAlreadyReservedFluidEntry(@NotNull IFluidTank tank) {
        IFluidTank cur = tank;
        while (true) {
            if (cur instanceof ReservedFluidTank) {
                return true;
            }
            if (cur instanceof IMultipleTankHandler.ITankEntry entry) {
                IFluidTank next = entry.getDelegate();
                if (next == cur) return false;
                cur = next;
                continue;
            }
            return false;
        }
    }

    private static final class ReservedItemHandlerView implements IItemHandlerModifiable {

        private final IItemHandlerModifiable delegate;
        private final IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> reservedByLeaf;

        ReservedItemHandlerView(@NotNull IItemHandlerModifiable delegate,
                                @NotNull IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> reservedByLeaf) {
            this.delegate = delegate;
            this.reservedByLeaf = reservedByLeaf;
        }

        @Nullable
        private ItemStack reservedForSlot(int slot) {
            Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(delegate, slot);
            Map<Integer, ItemStack> slots = reservedByLeaf.get(leaf.getFirst());
            return slots == null ? null : slots.get(leaf.getSecond());
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack actual = delegate.getStackInSlot(slot);
            ItemStack reserved = reservedForSlot(slot);
            if (reserved == null || reserved.isEmpty() || actual.isEmpty() ||
                    !ItemHandlerHelper.canItemStacksStack(actual, reserved)) {
                return actual;
            }
            int avail = Math.max(0, actual.getCount() - reserved.getCount());
            return avail <= 0 ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(actual, avail);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            ItemStack actual = delegate.getStackInSlot(slot);
            ItemStack reserved = reservedForSlot(slot);
            if (reserved != null && !reserved.isEmpty() && !actual.isEmpty() &&
                    ItemHandlerHelper.canItemStacksStack(actual, reserved)) {
                int cap = Math.min(amount, Math.max(0, actual.getCount() - reserved.getCount()));
                return cap <= 0 ? ItemStack.EMPTY : delegate.extractItem(slot, cap, simulate);
            }
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            ItemStack actual = delegate.getStackInSlot(slot);
            ItemStack reserved = reservedForSlot(slot);
            if (reserved != null && !reserved.isEmpty() && !actual.isEmpty() &&
                    ItemHandlerHelper.canItemStacksStack(actual, reserved)) {
                int floor = Math.min(actual.getCount(), reserved.getCount());
                if (stack.isEmpty()) {
                    delegate.setStackInSlot(slot, ItemHandlerHelper.copyStackWithSize(actual, floor));
                    return;
                }
                if (ItemHandlerHelper.canItemStacksStack(stack, reserved)) {
                    delegate.setStackInSlot(slot,
                            ItemHandlerHelper.copyStackWithSize(stack, saturatingIntAdd(stack.getCount(), floor)));
                    return;
                }
            }
            delegate.setStackInSlot(slot, stack);
        }
    }

    private static final class ReservedFluidTank implements IFluidTank, IFluidHandler {

        final IMultipleTankHandler.ITankEntry delegate;
        private final FluidStack reserved;

        ReservedFluidTank(@NotNull IMultipleTankHandler.ITankEntry delegate, @NotNull FluidStack reserved) {
            this.delegate = delegate;
            this.reserved = reserved;
        }

        @Nullable
        private FluidStack availableStack() {
            FluidStack actual = delegate.getFluid();
            if (actual == null || actual.amount <= 0) {
                return null;
            }
            if (!actual.isFluidEqual(reserved)) {
                return actual.copy();
            }
            int avail = Math.max(0, actual.amount - reserved.amount);
            return avail <= 0 ? null : GTUtility.copy(avail, actual);
        }

        @Nullable
        @Override
        public FluidStack getFluid() {
            return availableStack();
        }

        @Override
        public int getFluidAmount() {
            FluidStack avail = availableStack();
            return avail == null ? 0 : avail.amount;
        }

        @Override
        public int getCapacity() {
            return delegate.getCapacity();
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(getFluid(), getCapacity());
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            IFluidTankProperties[] rawProps = delegate.getTankProperties();
            boolean canFill = false;
            boolean canDrain = false;
            if (rawProps != null) {
                for (IFluidTankProperties p : rawProps) {
                    if (p != null) {
                        canFill |= p.canFill();
                        canDrain |= p.canDrain();
                    }
                }
            }
            final boolean fCanFill = canFill;
            final boolean fCanDrain = canDrain;
            return new IFluidTankProperties[] {
                    new IFluidTankProperties() {

                        @Nullable
                        @Override
                        public FluidStack getContents() {
                            return getFluid();
                        }

                        @Override
                        public int getCapacity() {
                            return delegate.getCapacity();
                        }

                        @Override
                        public boolean canFill() {
                            return fCanFill;
                        }

                        @Override
                        public boolean canDrain() {
                            return fCanDrain;
                        }

                        @Override
                        public boolean canFillFluidType(FluidStack fluidStack) {
                            if (rawProps == null) return false;
                            for (IFluidTankProperties p : rawProps) {
                                if (p != null && p.canFillFluidType(fluidStack)) return true;
                            }
                            return false;
                        }

                        @Override
                        public boolean canDrainFluidType(FluidStack fluidStack) {
                            if (rawProps == null) return false;
                            for (IFluidTankProperties p : rawProps) {
                                if (p != null && p.canDrainFluidType(fluidStack)) return true;
                            }
                            return false;
                        }
                    }
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return delegate.fill(resource, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) return null;
            FluidStack actual = delegate.getFluid();
            if (actual == null || actual.amount <= 0) return null;
            if (actual.isFluidEqual(reserved)) {
                if (!actual.isFluidEqual(resource)) return null;
                int cap = Math.min(resource.amount, Math.max(0, actual.amount - reserved.amount));
                return cap <= 0 ? null : delegate.drain(GTUtility.copy(cap, resource), doDrain);
            }
            return delegate.drain(resource, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain <= 0) return null;
            FluidStack actual = delegate.getFluid();
            if (actual == null || actual.amount <= 0) return null;
            if (actual.isFluidEqual(reserved)) {
                int cap = Math.min(maxDrain, Math.max(0, actual.amount - reserved.amount));
                return cap <= 0 ? null : delegate.drain(cap, doDrain);
            }
            return delegate.drain(maxDrain, doDrain);
        }
    }

    private static IFluidTank unwrapFluidTank(@NotNull IFluidTank tank) {
        while (true) {
            if (tank instanceof ReservedFluidTank reservedTank) {
                tank = reservedTank.delegate;
                continue;
            }
            if (tank instanceof IMultipleTankHandler.ITankEntry entry) {
                IFluidTank delegate = entry.getDelegate();
                if (delegate == tank) break;
                tank = delegate;
                continue;
            }
            break;
        }
        return tank;
    }

    private static boolean canGuaranteeFluidRollbackViaFill(@NotNull IMultipleTankHandler.ITankEntry tank,
                                                            @Nullable FluidStack snapshot) {
        if (snapshot == null || snapshot.amount <= 0) {
            return false;
        }
        IFluidTank leaf = unwrapFluidTank(tank);
        if (leaf instanceof PendingFluidRollback) {
            return true;
        }
        if (leaf instanceof IFluidHandler) {
            IFluidTankProperties[] props = tank.getTankProperties();
            if (props == null || props.length == 0) {
                return false;
            }
            boolean anyCanFill = false;
            for (IFluidTankProperties prop : props) {
                if (prop != null && prop.canFill() && prop.canFillFluidType(snapshot)) {
                    anyCanFill = true;
                    break;
                }
            }
            if (!anyCanFill) {
                return false;
            }
            if (tank.getFluidAmount() < tank.getCapacity()) {
                return tank.fill(GTUtility.copy(1, snapshot), false) == 1;
            }
            return true;
        }
        if (tank.getFluidAmount() < tank.getCapacity()) {
            return tank.fill(GTUtility.copy(1, snapshot), false) == 1;
        }
        return false;
    }

    private static Tuple<IItemHandler, Integer> resolveLeafItemSlot(@NotNull IItemHandler handler, int slot) {
        IItemHandler current = handler;
        int currentSlot = slot;
        while (true) {
            if (current instanceof DualHandler dual) {
                current = dual.getItemDelegate();
                continue;
            }
            if (current instanceof ItemHandlerList list) {
                IItemHandler matched = null;
                int matchedOffset = 0;
                for (IItemHandler sub : list.getBackingHandlers()) {
                    int offset = list.getIndexOffset(sub);
                    int subSlots = sub.getSlots();
                    if (offset >= 0 && currentSlot >= offset && currentSlot < offset + subSlots) {
                        matched = sub;
                        matchedOffset = offset;
                        break;
                    }
                }
                if (matched != null && matched != current) {
                    current = matched;
                    currentSlot -= matchedOffset;
                    continue;
                }
            }
            return new Tuple<>(current, currentSlot);
        }
    }

    private static boolean canGuaranteeItemRollback(@NotNull IItemHandlerModifiable importInventory,
                                                    int slot,
                                                    @NotNull ItemStack snapshot,
                                                    int originalCount) {
        if (snapshot.isEmpty() || originalCount <= 0) {
            return false;
        }
        Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, slot);
        IItemHandler leafHandler = leaf.getFirst();
        int leafSlot = leaf.getSecond();
        int maxStack = Math.min(importInventory.getSlotLimit(slot), snapshot.getMaxStackSize());
        if (originalCount < maxStack) {
            return importInventory.insertItem(slot, ItemHandlerHelper.copyStackWithSize(snapshot, 1), true).isEmpty();
        }
        return leafHandler instanceof ItemStackHandler && leafHandler.isItemValid(leafSlot, snapshot);
    }

    private void rollbackExtractedInputs(@NotNull IItemHandlerModifiable importInventory,
                                         @NotNull int[] originalSlotCounts,
                                         @NotNull ItemStack[] extractedItems,
                                         @NotNull IMultipleTankHandler importFluids,
                                         @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                         @NotNull int[] originalTankAmounts,
                                         @NotNull FluidStack[] drainedFluids) {
        for (int i = 0; i < extractedItems.length; i++) {
            ItemStack extracted = extractedItems[i];
            if (extracted == null || extracted.isEmpty()) continue;
            ItemStack currentAfter = importInventory.getStackInSlot(i);
            int currentCount = (!currentAfter.isEmpty() &&
                    ItemHandlerHelper.canItemStacksStack(currentAfter, extracted)) ? currentAfter.getCount() : 0;
            int missing = Math.min(extracted.getCount(), Math.max(0, originalSlotCounts[i] - currentCount));
            if (missing <= 0) {
                continue;
            }
            Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, i);
            ItemStack remainder = importInventory.insertItem(
                    i, ItemHandlerHelper.copyStackWithSize(extracted, missing), false);
            if (!remainder.isEmpty() && leaf.getFirst() instanceof IItemHandlerModifiable) {
                try {
                    ItemStack current = importInventory.getStackInSlot(i);
                    if (current.isEmpty()) {
                        int toSet = Math.min(originalSlotCounts[i], remainder.getCount());
                        importInventory.setStackInSlot(i, ItemHandlerHelper.copyStackWithSize(remainder, toSet));
                        ItemStack afterSet = importInventory.getStackInSlot(i);
                        int restored = afterSet.isEmpty() ? 0 : Math.min(remainder.getCount(), afterSet.getCount());
                        remainder = restored >= remainder.getCount() ? ItemStack.EMPTY :
                                ItemHandlerHelper.copyStackWithSize(remainder, remainder.getCount() - restored);
                    } else if (ItemHandlerHelper.canItemStacksStack(current, remainder) &&
                            current.getCount() < originalSlotCounts[i]) {
                        int beforeCount = current.getCount();
                        ItemStack merged = current.copy();
                        merged.setCount(Math.min(originalSlotCounts[i], beforeCount + remainder.getCount()));
                        importInventory.setStackInSlot(i, merged);
                        ItemStack afterSet = importInventory.getStackInSlot(i);
                        int restored = afterSet.isEmpty() ? 0 :
                                Math.max(0, Math.min(remainder.getCount(), afterSet.getCount() - beforeCount));
                        remainder = restored >= remainder.getCount() ? ItemStack.EMPTY :
                                ItemHandlerHelper.copyStackWithSize(remainder, remainder.getCount() - restored);
                    }
                } catch (RuntimeException ignored) {}
            }
            if (!remainder.isEmpty()) {
                remainder = ItemHandlerHelper.insertItemStacked(importInventory, remainder, false);
            }
            if (!remainder.isEmpty()) {
                enqueuePendingItemRollback(leaf.getFirst(), leaf.getSecond(), remainder);
            }
        }
        for (int t = 0; t < drainedFluids.length; t++) {
            FluidStack drained = drainedFluids[t];
            if (drained == null || drained.amount <= 0) continue;
            IMultipleTankHandler.ITankEntry tank = tanks.get(t);
            IFluidTank leaf = unwrapFluidTank(tank);
            if (leaf instanceof PendingFluidRollback existingPending) {
                int currentAmount = existingPending.getFluidAmount();
                int missing = Math.min(drained.amount, Math.max(0, originalTankAmounts[t] - currentAmount));
                if (missing > 0) {
                    existingPending.addAmount(missing, drained);
                    if (!pendingRollbackFluids.contains(existingPending)) {
                        pendingRollbackFluids.add(existingPending);
                    }
                }
                continue;
            }
            FluidStack currentFluid = tank.getFluid();
            int currentAmount = (currentFluid != null && currentFluid.isFluidEqual(drained)) ?
                    Math.max(0, currentFluid.amount) : 0;
            int missing = Math.min(drained.amount, Math.max(0, originalTankAmounts[t] - currentAmount));
            if (missing <= 0) {
                continue;
            }
            int filled = tank.fill(GTUtility.copy(missing, drained), true);
            int unreturned = missing - Math.max(0, filled);
            if (unreturned > 0) {
                int filledElsewhere = fillSameColorOtherTank(importFluids, leaf, GTUtility.copy(unreturned, drained));
                unreturned -= Math.max(0, filledElsewhere);
            }
            if (unreturned > 0) {
                enqueuePendingFluidRollback(leaf, GTUtility.copy(unreturned, drained));
            }
        }
    }

    private int fillSameColorOtherTank(@NotNull IMultipleTankHandler importFluids,
                                       @NotNull IFluidTank originLeaf,
                                       @NotNull FluidStack resource) {
        int originColor = colorIndexOf(originLeaf);
        int remaining = resource.amount;
        for (IMultipleTankHandler.ITankEntry entry : importFluids.getFluidTanks()) {
            IFluidTank candidate = unwrapFluidTank(entry);
            if (candidate == originLeaf || candidate instanceof PendingFluidRollback) continue;
            if (colorIndexOf(candidate) != originColor) continue;
            int filled = entry.fill(GTUtility.copy(remaining, resource), true);
            if (filled > 0) {
                remaining -= Math.min(remaining, filled);
                if (remaining <= 0) {
                    break;
                }
            }
        }
        return resource.amount - remaining;
    }

    private void enqueuePendingItemRollback(@NotNull IItemHandler leafHandler,
                                            int leafSlot,
                                            @NotNull ItemStack remainder) {
        if (remainder.isEmpty()) return;
        for (PendingItemRollback existing : pendingRollbackItems) {
            IItemHandler existingTarget = existing.targetHandlerRef == null ? null : existing.targetHandlerRef.get();
            if (existingTarget == leafHandler && existing.targetSlot == leafSlot &&
                    ItemHandlerHelper.canItemStacksStack(existing.stack, remainder)) {
                existing.stack.grow(remainder.getCount());
                return;
            }
        }
        MetaTileEntity owner = ownerOf(leafHandler);
        int detectedColor = colorIndexOf(leafHandler);
        byte kind = ROLLBACK_SOURCE_RAW;
        int busIdx = -1;
        int withinColorIdx = -1;
        if (metaTileEntity instanceof MultiblockControllerBase controller) {
            List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            for (int b = 0; b < buses.size(); b++) {
                IItemHandlerModifiable bus = buses.get(b);
                IItemHandler unwrappedBus = bus instanceof DualHandler dual ? dual.getItemDelegate() : bus;
                if (bus == leafHandler || unwrappedBus == leafHandler) {
                    kind = ROLLBACK_SOURCE_ABILITY;
                    busIdx = b;
                    detectedColor = colorIndexOf(bus);
                    int ord = 0;
                    for (int prev = 0; prev < b; prev++) {
                        if (colorIndexOf(buses.get(prev)) == detectedColor) {
                            ord++;
                        }
                    }
                    withinColorIdx = ord;
                    if (owner == null) owner = ownerOf(bus);
                    break;
                }
            }
        }
        int effectiveColor = detectedColor >= 0 ? detectedColor :
                (owner == null && kind == ROLLBACK_SOURCE_RAW && activeCommitColor >= 0 ?
                        activeCommitColor : NO_CHANNEL);
        String ownerId = owner != null && owner.metaTileEntityId != null ? owner.metaTileEntityId.toString() : "";
        BlockPos pos = owner != null ? owner.getPos() : null;
        pendingRollbackItems.add(new PendingItemRollback(
                leafHandler, kind, effectiveColor, busIdx, withinColorIdx, leafSlot,
                ownerId, pos != null, pos != null ? pos.toLong() : 0L, remainder.copy()));
    }

    private void enqueuePendingFluidRollback(@NotNull IFluidTank leafTank, @NotNull FluidStack unreturned) {
        if (unreturned.amount <= 0) return;
        for (PendingFluidRollback existing : pendingRollbackFluids) {
            IFluidTank existingTarget = existing.targetTankRef == null ? null : existing.targetTankRef.get();
            if (existingTarget == leafTank && existing.fluid != null && existing.fluid.isFluidEqual(unreturned)) {
                existing.addAmount(unreturned.amount, unreturned);
                return;
            }
        }
        MetaTileEntity owner = ownerOf(leafTank);
        int detectedColor = colorIndexOf(leafTank);
        byte kind = ROLLBACK_SOURCE_RAW;
        int busIdx = -1;
        int tankIdx = 0;
        int withinColorIdx = -1;

        if (metaTileEntity instanceof MultiblockControllerBase controller) {
            List<IFluidTank> flatAbilityTanks = flattenImportFluidAbilities(controller);
            int foundIdx = -1;
            for (int t = 0; t < flatAbilityTanks.size(); t++) {
                IFluidTank candidate = flatAbilityTanks.get(t);
                if (candidate == leafTank) {
                    foundIdx = t;
                    detectedColor = colorIndexOf(candidate);
                    int ord = 0;
                    for (int prev = 0; prev < t; prev++) {
                        if (colorIndexOf(flatAbilityTanks.get(prev)) == detectedColor) {
                            ord++;
                        }
                    }
                    withinColorIdx = ord;
                    break;
                }
            }
            if (foundIdx >= 0) {
                kind = ROLLBACK_SOURCE_ABILITY;
                tankIdx = foundIdx;
            } else {
                List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
                for (int b = 0; b < buses.size() && kind == ROLLBACK_SOURCE_RAW; b++) {
                    IItemHandlerModifiable bus = buses.get(b);
                    if (!(bus instanceof IMultipleTankHandler dualHandler)) continue;
                    List<IMultipleTankHandler.ITankEntry> dualTanks = dualHandler.getFluidTanks();
                    for (int dt = 0; dt < dualTanks.size(); dt++) {
                        if (unwrapFluidTank(dualTanks.get(dt)) == leafTank) {
                            kind = ROLLBACK_SOURCE_DUAL_BUS;
                            busIdx = b;
                            tankIdx = dt;
                            detectedColor = colorIndexOf(bus);
                            int ord = 0;
                            for (int prev = 0; prev < b; prev++) {
                                IItemHandlerModifiable prevBus = buses.get(prev);
                                if (prevBus instanceof IMultipleTankHandler &&
                                        colorIndexOf(prevBus) == detectedColor) {
                                    ord++;
                                }
                            }
                            withinColorIdx = ord;
                            if (owner == null) owner = ownerOf(bus);
                            break;
                        }
                    }
                }
            }
        }

        if (kind == ROLLBACK_SOURCE_RAW) {
            List<IMultipleTankHandler.ITankEntry> rawEntries = getRawInputTank().getFluidTanks();
            int rawIdx = indexOfLeafTank(rawEntries, leafTank);
            if (rawIdx >= 0) {
                tankIdx = rawIdx;
            } else if (activeCommitColor >= 0) {
                List<IMultipleTankHandler.ITankEntry> chEntries = getRawInputTankForColor(activeCommitColor)
                        .getFluidTanks();
                int chIdx = indexOfLeafTank(chEntries, leafTank);
                if (chIdx >= 0) {
                    kind = ROLLBACK_SOURCE_CHANNEL_CUSTOM;
                    tankIdx = chIdx;
                    detectedColor = activeCommitColor;
                }
            }
        }

        int effectiveColor = detectedColor >= 0 ? detectedColor :
                (owner == null && kind == ROLLBACK_SOURCE_CHANNEL_CUSTOM && activeCommitColor >= 0 ?
                        activeCommitColor : NO_CHANNEL);
        String ownerId = owner != null && owner.metaTileEntityId != null ? owner.metaTileEntityId.toString() : "";
        BlockPos pos = owner != null ? owner.getPos() : null;
        pendingRollbackFluids.add(new PendingFluidRollback(
                leafTank, kind, effectiveColor, busIdx, tankIdx, withinColorIdx,
                ownerId, pos != null, pos != null ? pos.toLong() : 0L, unreturned.copy()));
    }

    private static List<IFluidTank> flattenImportFluidAbilities(@NotNull MultiblockControllerBase controller) {
        List<IFluidTank> result = new ArrayList<>();
        for (Object tankObj : controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS)) {
            if (tankObj instanceof IMultipleTankHandler multiTank) {
                for (IMultipleTankHandler.ITankEntry entry : multiTank.getFluidTanks()) {
                    IFluidTank leaf = unwrapFluidTank(entry);
                    if (!(leaf instanceof PendingFluidRollback)) {
                        result.add(leaf);
                    }
                }
            } else if (tankObj instanceof IFluidTank tank) {
                IFluidTank leaf = unwrapFluidTank(tank);
                if (!(leaf instanceof PendingFluidRollback)) {
                    result.add(leaf);
                }
            }
        }
        return result;
    }

    private static int indexOfLeafTank(@NotNull List<IMultipleTankHandler.ITankEntry> entries,
                                       @NotNull IFluidTank target) {
        for (int i = 0; i < entries.size(); i++) {
            if (unwrapFluidTank(entries.get(i)) == target) {
                return i;
            }
        }
        return -1;
    }

    private boolean matchesOwnerMetadata(@Nullable Object handler,
                                         @NotNull String expectedOwnerId,
                                         boolean checkPos,
                                         long expectedPosLong) {
        if (expectedOwnerId.isEmpty() && !checkPos) {
            return true;
        }
        MetaTileEntity owner = ownerOf(handler);
        if (owner == null) {
            return expectedOwnerId.isEmpty() && !checkPos;
        }
        if (!expectedOwnerId.isEmpty()) {
            String actualId = owner.metaTileEntityId != null ? owner.metaTileEntityId.toString() : "";
            if (!expectedOwnerId.equals(actualId)) {
                return false;
            }
        }
        if (checkPos) {
            BlockPos pos = owner.getPos();
            if (pos == null || pos.toLong() != expectedPosLong) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private IItemHandler resolvePendingItemTarget(@NotNull PendingItemRollback pending) {
        if (pending.targetHandlerRef != null) {
            IItemHandler live = pending.targetHandlerRef.get();
            if (live != null) {
                return live;
            }
        }
        IItemHandler rebound = null;
        if (pending.sourceKind == ROLLBACK_SOURCE_ABILITY &&
                metaTileEntity instanceof MultiblockControllerBase controller) {
            List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            if (pending.busIndex >= 0 && pending.busIndex < buses.size()) {
                IItemHandlerModifiable atIdx = buses.get(pending.busIndex);
                if (colorIndexOf(atIdx) == pending.sourceColor &&
                        matchesOwnerMetadata(atIdx, pending.ownerMteId, false, 0L)) {
                    rebound = atIdx instanceof DualHandler dual ? dual.getItemDelegate() : atIdx;
                }
            }
            if (rebound == null) {
                int colorOrdinal = 0;
                for (IItemHandlerModifiable bus : buses) {
                    if (colorIndexOf(bus) != pending.sourceColor) continue;
                    if (pending.hasOwnerPos && pending.ownerPosLong != 0L &&
                            matchesOwnerMetadata(bus, pending.ownerMteId, true, pending.ownerPosLong)) {
                        rebound = bus instanceof DualHandler dual ? dual.getItemDelegate() : bus;
                        break;
                    }
                    if (colorOrdinal == pending.withinColorBusIndex &&
                            matchesOwnerMetadata(bus, pending.ownerMteId, false, 0L)) {
                        rebound = bus instanceof DualHandler dual ? dual.getItemDelegate() : bus;
                        break;
                    }
                    colorOrdinal++;
                }
            }
            if (rebound == null) {
                if (pending.busIndex >= 0 && pending.busIndex < buses.size() &&
                        colorIndexOf(buses.get(pending.busIndex)) == pending.sourceColor) {
                    IItemHandlerModifiable atIdx = buses.get(pending.busIndex);
                    rebound = atIdx instanceof DualHandler dual ? dual.getItemDelegate() : atIdx;
                } else {
                    int colorOrdinal = 0;
                    for (IItemHandlerModifiable bus : buses) {
                        if (colorIndexOf(bus) != pending.sourceColor) continue;
                        if (colorOrdinal == pending.withinColorBusIndex) {
                            rebound = bus instanceof DualHandler dual ? dual.getItemDelegate() : bus;
                            break;
                        }
                        colorOrdinal++;
                    }
                }
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_RAW && pending.sourceColor < 0) {
            IItemHandlerModifiable inv = getInputInventory();
            if (inv.getSlots() > 0 && colorIndexOf(inv) < 0) {
                rebound = inv;
            }
        }
        if (rebound != null) {
            pending.targetHandlerRef = new WeakReference<>(rebound);
        }
        return rebound;
    }

    @Nullable
    private IFluidTank resolvePendingFluidTarget(@NotNull PendingFluidRollback pending) {
        if (pending.targetTankRef != null) {
            IFluidTank live = pending.targetTankRef.get();
            if (live != null) {
                return live;
            }
        }
        IFluidTank rebound = null;
        if (pending.sourceKind == ROLLBACK_SOURCE_ABILITY &&
                metaTileEntity instanceof MultiblockControllerBase controller) {
            List<IFluidTank> flat = flattenImportFluidAbilities(controller);
            if (pending.tankIndex >= 0 && pending.tankIndex < flat.size()) {
                IFluidTank atIdx = flat.get(pending.tankIndex);
                if (colorIndexOf(atIdx) == pending.sourceColor &&
                        matchesOwnerMetadata(atIdx, pending.ownerMteId, false, 0L)) {
                    rebound = atIdx;
                }
            }
            if (rebound == null) {
                int colorOrdinal = 0;
                for (IFluidTank candidate : flat) {
                    if (colorIndexOf(candidate) != pending.sourceColor) continue;
                    if (pending.hasOwnerPos && pending.ownerPosLong != 0L &&
                            matchesOwnerMetadata(candidate, pending.ownerMteId, true, pending.ownerPosLong)) {
                        rebound = candidate;
                        break;
                    }
                    if (colorOrdinal == pending.withinColorTankIndex &&
                            matchesOwnerMetadata(candidate, pending.ownerMteId, false, 0L)) {
                        rebound = candidate;
                        break;
                    }
                    colorOrdinal++;
                }
            }
            if (rebound == null) {
                if (pending.tankIndex >= 0 && pending.tankIndex < flat.size() &&
                        colorIndexOf(flat.get(pending.tankIndex)) == pending.sourceColor) {
                    rebound = flat.get(pending.tankIndex);
                } else {
                    int colorOrdinal = 0;
                    for (IFluidTank candidate : flat) {
                        if (colorIndexOf(candidate) != pending.sourceColor) continue;
                        if (colorOrdinal == pending.withinColorTankIndex) {
                            rebound = candidate;
                            break;
                        }
                        colorOrdinal++;
                    }
                }
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_DUAL_BUS &&
                metaTileEntity instanceof MultiblockControllerBase controller) {
            List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            IItemHandlerModifiable matchedBus = null;
            if (pending.busIndex >= 0 && pending.busIndex < buses.size()) {
                IItemHandlerModifiable atIdx = buses.get(pending.busIndex);
                if (atIdx instanceof IMultipleTankHandler && colorIndexOf(atIdx) == pending.sourceColor &&
                        matchesOwnerMetadata(atIdx, pending.ownerMteId, false, 0L)) {
                    matchedBus = atIdx;
                }
            }
            if (matchedBus == null) {
                int colorOrdinal = 0;
                for (IItemHandlerModifiable bus : buses) {
                    if (!(bus instanceof IMultipleTankHandler) || colorIndexOf(bus) != pending.sourceColor) continue;
                    if ((pending.hasOwnerPos &&
                            matchesOwnerMetadata(bus, pending.ownerMteId, true, pending.ownerPosLong)) ||
                            (colorOrdinal == pending.withinColorTankIndex &&
                                    matchesOwnerMetadata(bus, pending.ownerMteId, false, 0L))) {
                        matchedBus = bus;
                        break;
                    }
                    colorOrdinal++;
                }
            }
            if (matchedBus instanceof IMultipleTankHandler dualHandler) {
                List<IMultipleTankHandler.ITankEntry> dualTanks = dualHandler.getFluidTanks();
                if (pending.tankIndex >= 0 && pending.tankIndex < dualTanks.size()) {
                    rebound = unwrapFluidTank(dualTanks.get(pending.tankIndex));
                }
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_CHANNEL_CUSTOM && pending.sourceColor >= 0) {
            List<IMultipleTankHandler.ITankEntry> entries = getRawInputTankForColor(pending.sourceColor).getFluidTanks();
            if (pending.tankIndex >= 0 && pending.tankIndex < entries.size()) {
                IFluidTank candidate = unwrapFluidTank(entries.get(pending.tankIndex));
                int cColor = colorIndexOf(candidate);
                if (cColor < 0 || cColor == pending.sourceColor) {
                    rebound = candidate;
                }
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_RAW && pending.sourceColor < 0) {
            List<IMultipleTankHandler.ITankEntry> entries = getRawInputTank().getFluidTanks();
            if (pending.tankIndex >= 0 && pending.tankIndex < entries.size()) {
                IFluidTank candidate = unwrapFluidTank(entries.get(pending.tankIndex));
                if (colorIndexOf(candidate) < 0) {
                    rebound = candidate;
                }
            }
        }
        if (rebound != null && !(rebound instanceof PendingFluidRollback)) {
            pending.targetTankRef = new WeakReference<>(rebound);
            return rebound;
        }
        return null;
    }

    protected void flushPendingRollbacks() {
        if (pendingRollbackItems.isEmpty() && pendingRollbackFluids.isEmpty()) {
            return;
        }
        boolean restoredAny = false;
        Iterator<PendingItemRollback> itemIter = pendingRollbackItems.iterator();
        while (itemIter.hasNext()) {
            PendingItemRollback pending = itemIter.next();
            if (pending.stack.isEmpty()) {
                itemIter.remove();
                continue;
            }
            int before = pending.stack.getCount();
            IItemHandler target = resolvePendingItemTarget(pending);
            if (target != null) {
                if (pending.targetSlot >= 0 && pending.targetSlot < target.getSlots()) {
                    pending.stack = target.insertItem(pending.targetSlot, pending.stack, false);
                }
                if (!pending.stack.isEmpty()) {
                    pending.stack = ItemHandlerHelper.insertItemStacked(target, pending.stack, false);
                }
            }
            if (pending.stack.getCount() < before) {
                restoredAny = true;
            }
            if (pending.stack.isEmpty()) {
                itemIter.remove();
            }
        }
        Iterator<PendingFluidRollback> fluidIter = pendingRollbackFluids.iterator();
        while (fluidIter.hasNext()) {
            PendingFluidRollback pending = fluidIter.next();
            if (pending.fluid == null || pending.fluid.amount <= 0) {
                fluidIter.remove();
                continue;
            }
            int filled = 0;
            IFluidTank target = resolvePendingFluidTarget(pending);
            if (target != null) {
                filled = target.fill(pending.fluid.copy(), true);
            }
            if (filled > 0) {
                pending.fluid.amount -= Math.min(pending.fluid.amount, filled);
                restoredAny = true;
            }
            if (pending.fluid.amount <= 0) {
                pending.fluid = null;
                fluidIter.remove();
            }
        }
        if (restoredAny) {
            this.invalidInputsForRecipes = false;
            this.invalidatedInputList.clear();
        }
    }

    private IMultipleTankHandler withPendingRollbackFluids(@NotNull IMultipleTankHandler base, int channelColor) {
        if (pendingRollbackFluids.isEmpty()) {
            return base;
        }
        List<IFluidTank> matchingPending = null;
        List<IMultipleTankHandler.ITankEntry> baseEntries = base.getFluidTanks();
        Iterator<PendingFluidRollback> iter = pendingRollbackFluids.iterator();
        while (iter.hasNext()) {
            PendingFluidRollback pending = iter.next();
            if (pending.fluid == null || pending.fluid.amount <= 0) {
                iter.remove();
                continue;
            }
            if (containsLeafTank(baseEntries, pending)) {
                continue;
            }
            IFluidTank target = resolvePendingFluidTarget(pending);
            boolean include = target != null &&
                    containsLeafTank(baseEntries, target) &&
                    (pending.sourceColor < 0 || channelColor == pending.sourceColor ||
                            channelColor == UNTRACKED_CHANNEL);
            if (include) {
                if (matchingPending == null) {
                    matchingPending = new ArrayList<>();
                }
                matchingPending.add(pending);
            }
        }
        if (matchingPending == null) {
            return base;
        }
        List<IFluidTank> combined = new ArrayList<>(matchingPending.size() + baseEntries.size());
        combined.addAll(matchingPending);
        combined.addAll(baseEntries);
        return new FluidTankList(base.allowSameFluidFill(), combined);
    }

    private static boolean containsLeafTank(@NotNull List<IMultipleTankHandler.ITankEntry> entries,
                                            @NotNull IFluidTank target) {
        for (IMultipleTankHandler.ITankEntry entry : entries) {
            if (unwrapFluidTank(entry) == target) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canProgressRecipe() {
        if (crossRecipeSchedulerActive) {
            return !((IMultiblockController) metaTileEntity).isStructureObstructed();
        }
        return super.canProgressRecipe() && !((IMultiblockController) metaTileEntity).isStructureObstructed();
    }

    private void clearPreservedAbortReason() {
        this.preserveAbortWhyFailed = false;
        this.deferAbortWhyFailedClearOnce = false;
    }

    private void captureSearchVoltageSnapshot() {
        this.lastSearchMaxVoltage = getMaxVoltage();
        this.lastSearchPowerBudget = usesParallelScheduler() ? getTotalPowerBudget() : this.lastSearchMaxVoltage;
        this.hasSearchVoltageSnapshot = true;
    }

    private void refreshInvalidatedInputsOnVoltageChange() {
        if (!this.hasSearchVoltageSnapshot ||
                (!this.invalidInputsForRecipes && this.invalidatedInputList.isEmpty())) {
            return;
        }
        long currentMaxVoltage = getMaxVoltage();
        long currentPowerBudget = usesParallelScheduler() ? getTotalPowerBudget() : currentMaxVoltage;
        if (currentMaxVoltage != this.lastSearchMaxVoltage ||
                currentPowerBudget != this.lastSearchPowerBudget) {
            resetInvalidatedInputs();
        }
    }

    @Override
    protected void resetInvalidatedInputs() {
        super.resetInvalidatedInputs();
        this.invalidatedInputList.clear();
        this.hasSearchVoltageSnapshot = false;
        clearPreservedAbortReason();
    }

    @Override
    public void forceRecipeRecheck() {
        this.lastCrossRecipe = null;
        this.previousDistinctRecipeMap = null;
        clearPreservedAbortReason();
        super.forceRecipeRecheck();
    }

    /**
     * Used to reset cached values in the Recipe Logic on structure deform
     */
    @Override
    public void invalidate() {
        if (crossRecipeSchedulerActive) {
            this.progressTime = 0;
        }
        super.invalidate();
        lastRecipeIndex = 0;
        lastColorChannel = NO_CHANNEL;
        invalidatedInputList.clear();
        cachedHandlerOwners = null;
        if (crossRecipeScheduler != null) {
            crossRecipeScheduler.invalidateAll(getRecipeConditions());
            crossRecipeSchedulerActive = false;
        }
        lastCrossRecipe = null;
        routedRecipeMap = null;
        previousDistinctRecipeMap = null;
        hasSearchVoltageSnapshot = false;
        clearPreservedAbortReason();
    }

    public void onDistinctChanged() {
        this.lastRecipeIndex = 0;
        resetInvalidatedInputs();
    }

    /**
     * Called when an import part's painting color changes. Lazily resets recipe input caches and
     * color-channel rotation without interrupting an actively running recipe.
     */
    public void onHatchColorChanged() {
        resetInvalidatedInputs();
        this.lastColorChannel = NO_CHANNEL;
    }

    public IEnergyContainer getEnergyContainer() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        return holder.getEnergyContainer();
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        IItemHandlerModifiable inv = holder.getInputInventory();
        return activeHolderItemMask != null ? activeHolderItemMask.apply(inv) : inv;
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

    private void logInvalidatedIsolatedInputs() {
        int bufferedInputs = 0;
        for (IItemHandlerModifiable input : invalidatedInputList) {
            if (input instanceof IPatternBufferIsolatedHandler && hasBufferedInputs(input)) {
                bufferedInputs++;
            }
        }
        if (bufferedInputs == 0) return;

        logBoundInputDiagnostic("INVALIDATED_ISOLATED_INPUTS_WAITING_FOR_NOTIFICATION", null, bufferedInputs);
    }

    /**
     * Buffered pattern inputs can become runnable without receiving an inventory notification, for example after
     * dynamic recipe registrations settle. Recheck only nonempty isolated buffers at a bounded cadence; ordinary
     * distinct buses keep the existing notification-driven behavior.
     */
    private boolean retryInvalidatedIsolatedInputs() {
        if (metaTileEntity.getOffsetTimer() % BOUND_INPUT_RETRY_INTERVAL_TICKS != 0L) return false;
        return invalidatedInputList.removeIf(input -> input instanceof IPatternBufferIsolatedHandler &&
                hasBufferedInputs(input));
    }

    private void logBoundInputNoRecipe(IItemHandlerModifiable input, @Nullable RecipeMap<?> recipeMap, String phase) {
        if (!(input instanceof IRecipeMapBoundInput) || !hasBufferedInputs(input)) return;
        logBoundInputDiagnostic(phase, recipeMap, 1);
    }

    private static boolean hasBufferedInputs(IItemHandler input) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (!input.getStackInSlot(slot).isEmpty()) return true;
        }
        if (input instanceof IMultipleTankHandler tankHandler) {
            for (int tank = 0; tank < tankHandler.getTanks(); tank++) {
                if (tankHandler.getTankAt(tank).getFluidAmount() > 0) return true;
            }
        }
        return false;
    }

    private void logBoundInputDiagnostic(String phase, @Nullable RecipeMap<?> recipeMap, int bufferedInputs) {
        RecipeMap<?> selectedMap = getConfiguredRecipeMap();
        String controller = metaTileEntity.getClass().getSimpleName() + '@' + metaTileEntity.getPos();
        String key = controller + ':' + phase;
        long now = System.nanoTime();
        Long previous = LAST_BOUND_INPUT_FAILURE_LOG_NANOS.put(key, now);
        if (previous != null && now - previous < BOUND_INPUT_FAILURE_LOG_INTERVAL_NANOS) return;

        GTLog.logger.warn("Map-bound isolated input diagnostic controller={} phase={} selectedRecipeMap={} " +
                        "resolvedRecipeMap={} nonemptyInvalidatedBuffers={} totalInputBuses={}",
                controller, phase, selectedMap == null ? "<none>" : selectedMap.getUnlocalizedName(),
                recipeMap == null ? "<none>" : recipeMap.getUnlocalizedName(), bufferedInputs, getInputBuses().size());
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
        IMultipleTankHandler tank = withPendingRollbackFluids(getRawInputTank(), UNTRACKED_CHANNEL);
        return activeHolderFluidMask != null ? activeHolderFluidMask.apply(tank) : tank;
    }

    private IMultipleTankHandler getRawInputTank() {
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
        if (items instanceof IPatternBufferIsolatedHandler) {
            // A pattern buffer is a complete recipe transaction. Do not let it borrow a shared fluid hatch,
            // otherwise a second request may make an incomplete buffer appear runnable.
            if (items instanceof IMultipleTankHandler tankHandler) {
                return withPendingRollbackFluids(tankHandler, colorIndexOf(items));
            }
            return new FluidTankList(false);
        }
        IMultipleTankHandler baseInputTank = getInputTank();
        var tanks = new ArrayList<>(baseInputTank.getFluidTanks());
        if (items instanceof IMultipleTankHandler tankHandler) {
            tanks.addAll(tankHandler.getFluidTanks());
        }
        return withPendingRollbackFluids(new FluidTankList(baseInputTank.allowSameFluidFill(), tanks),
                UNTRACKED_CHANNEL);
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
        refreshInvalidatedInputsOnVoltageChange();
        if (this.preserveAbortWhyFailed && this.progressTime == 0 && !this.crossRecipeSchedulerActive &&
                hasNotifiedInputs()) {
            if (this.deferAbortWhyFailedClearOnce) {
                this.deferAbortWhyFailedClearOnce = false;
            } else {
                clearPreservedAbortReason();
            }
        }
        if (shouldUseDistinctInputBuses()) {
                boolean canWork = false;
                if (invalidatedInputList.isEmpty()) {
                    metaTileEntity.getNotifiedFluidInputList().clear();
                    metaTileEntity.getNotifiedItemInputList().clear();
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
                if (retryInvalidatedIsolatedInputs()) {
                    canWork = true;
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
                if (!canWork) {
                    logInvalidatedIsolatedInputs();
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
        captureSearchVoltageSnapshot();

        if (shouldUseDistinctInputBuses()) {
            short mask = getHatchColorsMask();
            if (mask != 0) {
                // 通道轮转状态由方法内部在启动成功时记录
                trySearchNewRecipeDistinctByColorChannels(mask);
                return;
            }
            trySearchNewRecipeDistinct();
            return;
        }

        short mask = getHatchColorsMask();
        if (mask != 0) {
            trySearchNewRecipeByColorChannels(mask);
            return;
        }

        trySearchNewRecipeCombined();
    }

    /**
     * 本次通道遍历顺序:从上次成功通道的下一个开始循环,避免低编号通道持续有料时饿死高编号通道。
     * 通道集合每次重算(至多 16 项),因此染色变化无需额外的缓存失效处理。
     *
     * @return 长度至多 17 的通道顺位;{@code order[order.length - 1]} 恒为未染色通道
     *         ({@link #NO_CHANNEL}),因为未染色仓是公共输入,排在末位兜底
     */
    protected int[] getChannelOrder(short mask) {
        TreeSet<Integer> channels = new TreeSet<>();
        for (int color = 0; color < 16; color++) {
            if ((mask & (1 << color)) != 0) channels.add(color);
        }
        if (channels.isEmpty()) return new int[] { NO_CHANNEL };

        int[] order = new int[channels.size() + 1];
        // 上次成功通道的下一个为起点;higher() 为空说明上次已是最大号,则从最小号开始回绕。
        // NO_CHANNEL 是最小值,未成功过时 higher() 直接给出最小号通道。
        // 中间失败过的通道不影响起点——取的是 lastColorChannel,不是"上次尝试过的通道"。
        Integer pivot = channels.higher(lastColorChannel);
        int start = pivot != null ? pivot : channels.first();
        int index = 0;
        for (int color : channels.tailSet(start, true)) order[index++] = color;
        for (int color : channels.headSet(start, false)) order[index++] = color;

        order[index] = NO_CHANNEL;
        return order;
    }

    /**
     * 所有输入仓颜色的位掩码(0-15 每色一位);未染色不占位,全部未染色时返回 0。
     */
    protected short getHatchColorsMask() {
        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        short mask = 0;
        for (IItemHandlerModifiable bus : controller.getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
            int idx = colorIndexOf(bus);
            if (idx >= 0) mask |= (short) (1 << idx);
        }
        for (Object tankObj : controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS)) {
            if (tankObj instanceof IMultipleTankHandler multiTank) {
                for (IMultipleTankHandler.ITankEntry entry : multiTank.getFluidTanks()) {
                    int idx = colorIndexOf(entry);
                    if (idx >= 0) mask |= (short) (1 << idx);
                }
            } else {
                int idx = colorIndexOf(tankObj);
                if (idx >= 0) mask |= (short) (1 << idx);
            }
        }
        return mask;
    }

    /**
     * 输入仓的染料色索引(0-15);未染色或不可判色返回 -1(公共输入,不占位)。
     */
    protected int colorIndexOf(Object handler) {
        MetaTileEntity mte = ownerOf(handler);
        if (mte == null || !mte.isPainted()) return -1;
        EnumDyeColor dye = ColorUtil.getDyeColorFromRGB(mte.getPaintingColor());
        return dye == null ? -1 : dye.getMetadata();
    }

    protected @Nullable MetaTileEntity ownerOf(@Nullable Object handler) {
        if (handler == null) return null;
        MetaTileEntity unwrapped = unwrapHandlerOwner(handler);
        if (unwrapped != null) return unwrapped;
        return getHandlerOwnerMap().get(handler);
    }

    private static @Nullable MetaTileEntity unwrapHandlerOwner(@Nullable Object handler) {
        if (handler instanceof MetaTileEntity mte) {
            return mte;
        }
        if (handler instanceof GTItemStackHandler itemHandler && itemHandler.getMetaTileEntity() != null) {
            return itemHandler.getMetaTileEntity();
        }
        if (handler instanceof DualHandler dualHandler) {
            MetaTileEntity owner = unwrapHandlerOwner(dualHandler.getItemDelegate());
            if (owner != null) return owner;
            return unwrapHandlerOwner(dualHandler.getFluidDelegate());
        }
        if (handler instanceof ItemHandlerList list) {
            for (IItemHandler backing : list.getBackingHandlers()) {
                MetaTileEntity owner = unwrapHandlerOwner(backing);
                if (owner != null) return owner;
            }
            return null;
        }
        if (handler instanceof IMultipleTankHandler.ITankEntry entry) {
            return unwrapHandlerOwner(entry.getDelegate());
        }
        if (handler instanceof NotifiableFluidTank tank) {
            for (MetaTileEntity entity : tank.notifiableEntities) {
                if (entity != null && !(entity instanceof MultiblockControllerBase)) {
                    return entity;
                }
            }
            return null;
        }
        if (handler instanceof NotifiableItemStackHandler itemHandler) {
            for (MetaTileEntity entity : itemHandler.notifiableEntities) {
                if (entity != null && !(entity instanceof MultiblockControllerBase)) {
                    return entity;
                }
            }
            return null;
        }
        return null;
    }

    private @NotNull Map<Object, MetaTileEntity> getHandlerOwnerMap() {
        if (cachedHandlerOwners != null) {
            return cachedHandlerOwners;
        }
        IdentityHashMap<Object, MetaTileEntity> owners = new IdentityHashMap<>();
        if (metaTileEntity instanceof MultiblockControllerBase controller) {
            for (IMultiblockPart part : controller.getMultiblockParts()) {
                if (!(part instanceof MetaTileEntity partMte) ||
                        !(part instanceof IMultiblockAbilityPart<?> abilityPart)) {
                    continue;
                }
                for (MultiblockAbility<?> ability : abilityPart.getAbilities()) {
                    if (ability != MultiblockAbility.IMPORT_ITEMS &&
                            ability != MultiblockAbility.IMPORT_FLUIDS) {
                        continue;
                    }
                    AbilityInstances instances = new AbilityInstances(ability);
                    abilityPart.registerAbilities(instances);
                    for (Object registered : instances) {
                        registerHandlerOwner(owners, registered, partMte);
                    }
                }
            }
        }
        if (!owners.isEmpty()) {
            cachedHandlerOwners = owners;
        }
        return owners;
    }

    private static void registerHandlerOwner(@NotNull Map<Object, MetaTileEntity> owners,
                                             @Nullable Object handler,
                                             @NotNull MetaTileEntity owner) {
        if (handler == null) return;
        owners.putIfAbsent(handler, owner);
        if (handler instanceof IMultipleTankHandler tankHandler) {
            for (IMultipleTankHandler.ITankEntry entry : tankHandler.getFluidTanks()) {
                owners.putIfAbsent(entry, owner);
                owners.putIfAbsent(entry.getDelegate(), owner);
            }
        } else if (handler instanceof IMultipleTankHandler.ITankEntry entry) {
            owners.putIfAbsent(entry.getDelegate(), owner);
        } else if (handler instanceof ItemHandlerList list) {
            for (IItemHandler backing : list.getBackingHandlers()) {
                owners.putIfAbsent(backing, owner);
            }
        } else if (handler instanceof DualHandler dual) {
            registerHandlerOwner(owners, dual.getItemDelegate(), owner);
            registerHandlerOwner(owners, dual.getFluidDelegate(), owner);
        }
    }

    protected List<IItemHandlerModifiable> getInputBusesForColor(int color) {
        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        List<IItemHandlerModifiable> result = new ArrayList<>();
        for (IItemHandlerModifiable bus : controller.getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
            int idx = colorIndexOf(bus);
            if (idx < 0 || idx == color) result.add(bus);
        }
        return result;
    }

    /**
     * 通道流体:同色(含未染色)流体仓 + 同色总线上的 DualHandler 流体槽(非 distinct 模式)。
     * 语义与 {@link #getInputTank()} 一致,仅把"全部仓"收缩为该通道的仓。
     */
    protected IMultipleTankHandler getInputTankForColor(int color) {
        IMultipleTankHandler tank = withPendingRollbackFluids(getRawInputTankForColor(color), color);
        return activeHolderFluidMask != null ? activeHolderFluidMask.apply(tank) : tank;
    }

    private IMultipleTankHandler getRawInputTankForColor(int color) {
        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        boolean allowMerge = ((IRecipeMapHolder) metaTileEntity).getInputFluidInventory().allowSameFluidFill();
        List<IFluidTank> channelTanks = new ArrayList<>();
        for (Object tankObj : controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS)) {
            if (tankObj instanceof IMultipleTankHandler multiTank) {
                for (IMultipleTankHandler.ITankEntry entry : multiTank.getFluidTanks()) {
                    int idx = colorIndexOf(entry);
                    if (idx < 0 || idx == color) channelTanks.add(entry);
                }
            } else if (tankObj instanceof IFluidTank tank) {
                int idx = colorIndexOf(tank);
                if (idx < 0 || idx == color) channelTanks.add(tank);
            }
        }
        List<IMultipleTankHandler> inputFluids = new ArrayList<>();
        inputFluids.add(new FluidTankList(allowMerge, channelTanks));
        if (!shouldUseDistinctInputBuses()) {
            for (IItemHandlerModifiable bus : getInputBusesForColor(color)) {
                if (bus instanceof IPatternBufferIsolatedHandler) continue;
                if (bus instanceof IMultipleTankHandler dualHandler) inputFluids.add(dualHandler);
            }
        }
        return GTQTUtility.mergeTankHandlers(inputFluids, allowMerge);
    }

    private transient boolean matchedChannelRecipe;

    private void runIdleSchedulerSearch(@NotNull ToIntFunction<CrossRecipeParallelScheduler> filler) {
        captureSearchVoltageSnapshot();
        String retainedAbortReason = this.preserveAbortWhyFailed ? this.whyFailed : "";
        this.whyFailed = "";
        this.lastCombinedAllocationBudget = null;
        CrossRecipeParallelScheduler scheduler = getOrCreateScheduler();
        int filled = filler.applyAsInt(scheduler);
        AllocationBudget combinedBudget = this.lastCombinedAllocationBudget;
        this.lastCombinedAllocationBudget = null;

        if (filled > 0) {
            clearPreservedAbortReason();
            activateSchedulerIfFilled(scheduler, filled);
            return;
        }

        if (combinedBudget != null) {
            if (!combinedBudget.matchedAnyRecipe) {
                this.invalidInputsForRecipes = true;
                if (combinedBudget.hasAnyInput && retainedAbortReason.isEmpty()) {
                    this.whyFailed = getLockedRecipe() != null ? "无法运行锁定的配方" : "NoneRecipes";
                    return;
                }
            } else if (this.whyFailed.isEmpty()) {
                ensureConditionFailureReason();
            }
        }

        if (!this.whyFailed.isEmpty()) {
            clearPreservedAbortReason();
        } else if (!retainedAbortReason.isEmpty()) {
            this.whyFailed = retainedAbortReason;
        }
    }

    /**
     * 按颜色掩码逐通道查找,每通道只使用同色(含未染色)仓的输入,第一个成功启动的通道即返回。
     */
    protected void trySearchNewRecipeByColorChannels(short mask) {
        if (usesParallelScheduler()) {
            runIdleSchedulerSearch(scheduler -> fillSchedulerSlotsByColorChannels(scheduler, mask));
            return;
        }
        long maxVoltage = getMaxVoltage();
        int[] channelOrder = getChannelOrder(mask);
        boolean matchedAnyRecipe = false;
        this.whyFailed = "";
        for (int i = 0; i < channelOrder.length; i++) {
            int color = channelOrder[i];
            List<IItemHandlerModifiable> channelBuses = getInputBusesForColor(color);
            IItemHandlerModifiable channelInventory = new ItemHandlerList(channelBuses);
            IMultipleTankHandler channelFluids = getInputTankForColor(color);
            this.matchedChannelRecipe = false;
            if (tryFindRecipeForChannel(maxVoltage, channelInventory, channelFluids, color)) return;
            if (this.matchedChannelRecipe) {
                matchedAnyRecipe = true;
            }
        }
        if (!matchedAnyRecipe) {
            this.invalidInputsForRecipes = true;
            this.whyFailed = getLockedRecipe() != null ? "无法运行锁定的配方" : "NoneRecipes";
        } else if (this.whyFailed.isEmpty()) {
            this.whyFailed = "配方的特殊条件不满足（例如温度，算力等额外条件）";
        }
    }

    /**
     * 单个颜色通道内查找并准备配方(仿 {@link AbstractRecipeLogic#trySearchNewRecipe()} 的非并行部分)。
     *
     * @param color 该通道的颜色索引,启动成功时记入 {@link #lastColorChannel} 供轮转使用
     * @return 配方成功启动返回 true
     */
    protected boolean tryFindRecipeForChannel(long maxVoltage, IItemHandlerModifiable channelInventory,
                                              IMultipleTankHandler channelFluids, int color) {
        Recipe currentRecipe = null;
        if (checkPreviousRecipeForChannel(channelInventory, channelFluids)) {
            currentRecipe = this.previousRecipe;
        }
        if (currentRecipe == null) {
            currentRecipe = findRecipe(maxVoltage, channelInventory, channelFluids);
        }

        if (currentRecipe != null) {
            this.matchedChannelRecipe = true;
            this.previousRecipe = currentRecipe;
            int prevCommitColor = this.activeCommitColor;
            this.activeCommitColor = color;
            try {
                if (checkRecipe(currentRecipe, channelInventory, channelFluids) &&
                        prepareRecipe(currentRecipe, channelInventory, channelFluids)) {
                    lastColorChannel = color;
                    return true;
                }
            } finally {
                this.activeCommitColor = prevCommitColor;
            }
        }
        return false;
    }

    protected boolean checkPreviousRecipeForChannel(IItemHandlerModifiable channelInventory,
                                                    IMultipleTankHandler channelFluids) {
        if (this.previousRecipe == null) return false;
        RecipeMap<?> map = getRecipeMap();
        if (map == null || !isRecipeMapValid(map) || !map.ownsRecipe(this.previousRecipe)) return false;
        if (this.previousRecipe.getEUt() > this.getMaxVoltage()) return false;
        return this.previousRecipe.matches(false, channelInventory, channelFluids);
    }

    /**
     * 颜色通道版 distinct:外层按颜色通道,内层同色组内逐仓独立查找;
     * 流体侧每通道共享通道流体(与 distinct 的"流体共享"语义一致,只是收缩到通道内)。
     */
    protected void trySearchNewRecipeDistinctByColorChannels(short mask) {
        if (usesParallelScheduler()) {
            runIdleSchedulerSearch(scheduler -> fillSchedulerSlotsDistinctByColorChannels(scheduler, mask, true));
            return;
        }
        long maxVoltage = getMaxVoltage();
        int[] channelOrder = getChannelOrder(mask);
        Set<IItemHandlerModifiable> failedPublicBuses = null;
        Set<IItemHandlerModifiable> matchedPublicBuses = null;
        for (int i = 0; i < channelOrder.length; i++) {
            int color = channelOrder[i];
            List<IItemHandlerModifiable> channelBuses = getInputBusesForColor(color);
            if (channelBuses.isEmpty()) continue;
            IMultipleTankHandler channelFluids = getInputTankForColor(color);
            for (IItemHandlerModifiable bus : channelBuses) {
                if (invalidatedInputList.contains(bus)) continue;
                RecipeMap<?> recipeMap = getRecipeMapForInput(bus);
                if (recipeMap == null) {
                    logBoundInputNoRecipe(bus, null, "BOUND_RECIPE_MAP_NOT_SELECTED");
                    invalidatedInputList.add(bus);
                    continue;
                }
                IMultipleTankHandler busFluids = getBusFluidsInChannel(bus, channelFluids);
                Recipe currentRecipe = findRecipe(recipeMap, maxVoltage, bus, busFluids);
                if (currentRecipe != null) {
                    if (colorIndexOf(bus) < 0) {
                        if (matchedPublicBuses == null) {
                            matchedPublicBuses = new HashSet<>();
                        }
                        matchedPublicBuses.add(bus);
                    }
                    routedRecipeMap = recipeMap;
                    currentDistinctInputBus = bus;
                    this.previousRecipe = currentRecipe;
                    this.previousDistinctRecipeMap = recipeMap;
                    int prevCommitColor = this.activeCommitColor;
                    this.activeCommitColor = color;
                    try {
                        if (checkRecipe(currentRecipe, bus, busFluids) &&
                                prepareRecipeDistinct(currentRecipe, bus, busFluids)) {
                            lastColorChannel = color;
                            return;
                        }
                    } finally {
                        this.activeCommitColor = prevCommitColor;
                    }
                    routedRecipeMap = null;
                }
                if (currentRecipe == null) {
                    if (colorIndexOf(bus) < 0) {
                        if (failedPublicBuses == null) {
                            failedPublicBuses = new HashSet<>();
                        }
                        failedPublicBuses.add(bus);
                    } else {
                        logBoundInputNoRecipe(bus, recipeMap, "NO_RECIPE_MATCHED_BUFFER");
                        invalidatedInputList.add(bus);
                    }
                }
            }
            routedRecipeMap = null;
        }
        if (failedPublicBuses != null) {
            for (IItemHandlerModifiable bus : failedPublicBuses) {
                if ((matchedPublicBuses == null || !matchedPublicBuses.contains(bus)) &&
                        !invalidatedInputList.contains(bus)) {
                    logBoundInputNoRecipe(bus, getRecipeMapForInput(bus), "NO_RECIPE_MATCHED_BUFFER");
                    invalidatedInputList.add(bus);
                }
            }
        }
    }

    protected IMultipleTankHandler getBusFluidsInChannel(IItemHandlerModifiable bus,
                                                         IMultipleTankHandler channelFluids) {
        if (bus instanceof IPatternBufferIsolatedHandler) {
            if (bus instanceof IMultipleTankHandler tankHandler) {
                return withPendingRollbackFluids(tankHandler, colorIndexOf(bus));
            }
            return new FluidTankList(false);
        }
        if (bus instanceof IMultipleTankHandler tankHandler) {
            var tanks = new ArrayList<>(channelFluids.getFluidTanks());
            tanks.addAll(tankHandler.getFluidTanks());
            return withPendingRollbackFluids(new FluidTankList(channelFluids.allowSameFluidFill(), tanks),
                    colorIndexOf(bus));
        }
        return channelFluids;
    }

    protected void activateSchedulerIfFilled(@NotNull CrossRecipeParallelScheduler scheduler, int filled) {
        if (filled > 0) {
            this.progressTime = 1;
            this.canRecipeProgress = true;
            this.hasNotEnoughEnergy = false;
            clearPreservedAbortReason();
            setMaxProgress(Integer.MAX_VALUE);
            this.recipeEUt = scheduler.getTotalEnergyConsumption();
            this.parallelRecipesPerformed = scheduler.getTotalParallelCount();
            this.isPaused = !scheduler.hasWorkingSlots();
            this.whyFailed = "";
            crossRecipeSchedulerActive = true;
            if (this.wasActiveAndNeedsUpdate) {
                this.wasActiveAndNeedsUpdate = false;
            } else {
                this.setActive(true);
            }
        }
    }

    /**
     * Put into place so multiblocks can override {@link AbstractRecipeLogic#trySearchNewRecipe()} without having to
     * deal with the maintenance and distinct logic in {@link MultiblockRecipeLogic#trySearchNewRecipe()}
     */
    protected void trySearchNewRecipeCombined() {
        if (usesParallelScheduler()) {
            runIdleSchedulerSearch(this::fillSchedulerSlots);
            return;
        }

        super.trySearchNewRecipe();
    }

    protected void trySearchNewRecipeDistinct() {
        if (usesParallelScheduler()) {
            trySearchNewRecipeDistinctCrossRecipe();
            return;
        }

        long maxVoltage = getMaxVoltage();
        Recipe currentRecipe;
        List<IItemHandlerModifiable> importInventory = getInputBuses();
        routedRecipeMap = null;

        // Our caching implementation
        // This guarantees that if we get a recipe cache hit, our efficiency is no different from other machines
        IItemHandlerModifiable previousBus = importInventory.get(lastRecipeIndex);
        RecipeMap<?> previousMap = getRecipeMapForInput(previousBus);
        IMultipleTankHandler previousFluids = getInputTank(previousBus);
        if (previousRecipe != null && previousMap != null && previousMap == previousDistinctRecipeMap &&
                previousMap.ownsRecipe(previousRecipe) && checkPreviousRecipeDistinct(previousBus)) {
            routedRecipeMap = previousMap;
            currentRecipe = previousRecipe;
            currentDistinctInputBus = previousBus;
            if (checkRecipe(currentRecipe, previousBus, previousFluids) &&
                    prepareRecipeDistinct(currentRecipe, previousBus, previousFluids)) {
                // No need to cache the previous recipe here, as it is not null and matched by the current recipe,
                // so it will always be the same
                return;
            }
            logBoundInputStartFailure(previousBus, previousMap, currentRecipe, "CACHED_RECIPE_START_FAILED");
            routedRecipeMap = null;
        }

        // On a cache miss, our efficiency is much worse, as it will check
        // each bus individually instead of the combined inventory all at once.
        for (int i = 0; i < importInventory.size(); i++) {
            IItemHandlerModifiable bus = importInventory.get(i);
            // Skip this bus if no recipe was found last time
            if (invalidatedInputList.contains(bus)) {
                continue;
            }
            RecipeMap<?> recipeMap = getRecipeMapForInput(bus);
            if (recipeMap == null) {
                logBoundInputNoRecipe(bus, null, "BOUND_RECIPE_MAP_NOT_SELECTED");
                invalidatedInputList.add(bus);
                continue;
            }
            // Look for a new recipe after a cache miss
            IMultipleTankHandler busFluids = getInputTank(bus);
            currentRecipe = findRecipe(recipeMap, maxVoltage, bus, busFluids);
            // Cache the current recipe, if one is found
            if (currentRecipe != null) {
                routedRecipeMap = recipeMap;
                if (checkRecipe(currentRecipe, bus, busFluids)) {
                    this.previousRecipe = currentRecipe;
                    this.previousDistinctRecipeMap = recipeMap;
                    currentDistinctInputBus = bus;
                    if (prepareRecipeDistinct(currentRecipe, bus, busFluids)) {
                        lastRecipeIndex = i;
                        return;
                    }
                    logBoundInputStartFailure(bus, recipeMap, currentRecipe, "RECIPE_START_FAILED");
                } else {
                    logBoundInputStartFailure(bus, recipeMap, currentRecipe, "CONTROLLER_REJECTED_RECIPE");
                }
                routedRecipeMap = null;
            }
            if (currentRecipe == null) {
                logBoundInputNoRecipe(bus, recipeMap, "NO_RECIPE_MATCHED_BUFFER");
                // no valid recipe found, invalidate this bus
                invalidatedInputList.add(bus);
            }
        }
    }

    /**
     * Cross-recipe parallel version of distinct bus recipe search.
     */
    protected void trySearchNewRecipeDistinctCrossRecipe() {
        runIdleSchedulerSearch(scheduler -> fillSchedulerSlotsDistinctInternal(scheduler, true));
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
        return previousRecipe != null && previousRecipe.getEUt() <= getMaxVoltage() &&
                previousRecipe.matches(false, previousBus, getInputTank(previousBus));
    }

    protected boolean prepareRecipeDistinct(Recipe recipe) {
        return prepareRecipeDistinct(recipe, currentDistinctInputBus, getInputTank(currentDistinctInputBus));
    }

    /**
     * 指定输入总线与流体的 distinct 配方准备,消耗从给定的输入(而非全量仓)提取。
     * 颜色通道版 distinct 传入通道流体,保证不消耗其他颜色通道的输入。
     */
    protected boolean prepareRecipeDistinct(Recipe recipe, IItemHandlerModifiable inputBus,
                                            IMultipleTankHandler inputFluid) {
        recipe = Recipe.trimRecipeOutputs(recipe, getRecipeMap(), metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());
        if (recipe == null) {
            whyFailed = "配方输出数量超过机器限制（物品输出槽:" + metaTileEntity.getItemOutputLimit() +
                    ", 流体输出槽:" + metaTileEntity.getFluidOutputLimit() + "）";
            return false;
        }

        if (!canStartConditions(recipe, inputBus, inputFluid)) {
            if (whyFailed.isEmpty()) {
                whyFailed = "配方的特殊条件不满足（例如温度，算力等额外条件）";
            }
            return false;
        }

        int effectiveParallelLimit = Math.min(
                getEffectiveParallelLimit(recipe, inputBus, inputFluid),
                Math.max(1, getMaxRecipeOperations(recipe)));
        setParallelRecipesPerformed(0);
        if (effectiveParallelLimit > 1) {
            int fluidCap = limitFluidOutputParallel(recipe, getOutputTank(), effectiveParallelLimit,
                    metaTileEntity.canVoidRecipeFluidOutputs());
            effectiveParallelLimit = fluidCap == 0 ? 1 : Math.min(effectiveParallelLimit, fluidCap);
        }
        if (effectiveParallelLimit > 1) {
            Recipe originalRecipe = recipe;
            recipe = findParallelRecipe(
                    recipe,
                    inputBus,
                    inputFluid,
                    getOutputInventory(),
                    getOutputTank(),
                    getMaxParallelVoltage(),
                    effectiveParallelLimit);
            if (recipe == null) {
                whyFailed = "无法进行并行处理，按无并行继续流程！";
                setParallelRecipesPerformed(0);
                recipe = originalRecipe;
            }
        }

        if (recipe != null) {
            recipe = setupAndConsumeRecipeInputs(recipe, inputBus, inputFluid);
            if (recipe != null) {
                this.activeCheckInventory = inputBus;
                this.activeCheckFluids = inputFluid;
                try {
                    setupRecipe(recipe);
                } finally {
                    this.activeCheckInventory = null;
                    this.activeCheckFluids = null;
                }
                return true;
            }
        }

        return false;
    }

    /**
     * A map-bound buffer has supplied a complete recipe request. If it matches but cannot start, retain a
     * rate-limited diagnostic so output, energy, and controller constraints are distinguishable from an input mismatch.
     */
    private void logBoundInputStartFailure(IItemHandlerModifiable input, @Nullable RecipeMap<?> recipeMap,
                                           @Nullable Recipe recipe, String phase) {
        if (!(input instanceof IRecipeMapBoundInput boundInput)) return;

        String reason = getWhyFailed();
        if (reason == null || reason.isEmpty()) reason = "recipe preparation returned false without a reason";
        String controller = metaTileEntity.getClass().getSimpleName() + '@' + metaTileEntity.getPos();
        String key = controller + ':' + phase + ':' + reason;
        long now = System.nanoTime();
        Long previous = LAST_BOUND_INPUT_FAILURE_LOG_NANOS.put(key, now);
        if (previous != null && now - previous < BOUND_INPUT_FAILURE_LOG_INTERVAL_NANOS) return;

        GTLog.logger.warn("Map-bound isolated input could not start controller={} selectedRecipeMap={} " +
                        "boundRecipeMap={} phase={} reason={} recipeEUt={} itemOutputSlots={} fluidOutputTanks={}",
                controller, recipeMap == null ? "<none>" : recipeMap.getUnlocalizedName(),
                boundInput.getBoundRecipeMapName(), phase, reason, recipe == null ? "<none>" : recipe.getEUt(),
                getOutputInventory().getSlots(), getOutputTank().getTanks());
    }

    /**
     * A bounded start record distinguishes a real execution (where consumable inputs have already moved into a slot)
     * from a pattern-buffer insertion failure without producing per-tick scheduler noise.
     */
    private void logBoundCrossRecipeSlotStarted(SlotAllocation allocation, int totalOperations, int duration,
                                                long eut, String recipeDisplayName) {
        if (!(allocation.importInventory instanceof IRecipeMapBoundInput boundInput)) return;

        String controller = metaTileEntity.getClass().getSimpleName() + '@' + metaTileEntity.getPos();
        String key = controller + ':' + boundInput.getBoundRecipeMapName() + ':' + recipeDisplayName;
        long now = System.nanoTime();
        Long previous = LAST_BOUND_CROSS_RECIPE_START_LOG_NANOS.put(key, now);
        if (previous != null && now - previous < BOUND_CROSS_RECIPE_START_LOG_INTERVAL_NANOS) return;

        GTLog.logger.info("Map-bound cross-recipe slot started controller={} boundRecipeMap={} slot={} recipe={} " +
                        "operations={} duration={}t eut={} itemOutputs={} fluidOutputs={}",
                controller, boundInput.getBoundRecipeMapName(), allocation.slot.getSlotIndex(), recipeDisplayName,
                totalOperations, duration, eut, allocation.slot.getItemOutputs().size(),
                allocation.slot.getFluidOutputs().size());
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
    protected @NotNull RecipeExecutionContext createSingleExecutionContext(@Nullable Recipe recipe) {
        if (activeCheckInventory != null || activeCheckFluids != null) {
            int parallel = Math.max(1, parallelRecipesPerformed);
            return RecipeExecutionContext.forSingle(
                    recipe,
                    parallel,
                    Math.max(parallel, singleTotalOperations),
                    progressTime,
                    maxProgressTime,
                    recipeEUt,
                    singleRecipeExtraData,
                    itemOutputs,
                    fluidOutputs,
                    activeCheckInventory != null ? activeCheckInventory : getInputInventory(),
                    activeCheckFluids != null ? activeCheckFluids : getInputTank()
            );
        }
        return super.createSingleExecutionContext(recipe);
    }

    @Override
    protected boolean consumeSingleRecipeInputs(@NotNull Recipe recipe,
                                                @NotNull IItemHandlerModifiable importInventory,
                                                @NotNull IMultipleTankHandler importFluids) {
        return commitInputsAndHolderCheck(recipe, importInventory, importFluids, 1);
    }

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe) {
        IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
        if (holder.checkRecipe(recipe, false)) {
            IItemHandlerModifiable checkInv = activeCheckInventory != null ? activeCheckInventory : getInputInventory();
            IMultipleTankHandler checkFluids = activeCheckFluids != null ? activeCheckFluids : getInputTank();
            return checkDimensionRequirement(recipe) &&
                    canStartConditions(recipe, checkInv, checkFluids);
        }
        return false;
    }

    protected boolean checkRecipeDiagnostic(@NotNull Recipe recipe,
                                            @NotNull IItemHandlerModifiable importInventory,
                                            @NotNull IMultipleTankHandler importFluids) {
        this.activeCheckInventory = importInventory;
        this.activeCheckFluids = importFluids;
        try {
            IRecipeMapHolder holder = (IRecipeMapHolder) metaTileEntity;
            return holder.checkRecipe(recipe, false) &&
                    checkDimensionRequirement(recipe) &&
                    canStartConditions(recipe, importInventory, importFluids);
        } finally {
            this.activeCheckInventory = null;
            this.activeCheckFluids = null;
        }
    }

    protected boolean checkRecipe(@NotNull Recipe recipe,
                                  @NotNull IItemHandlerModifiable importInventory,
                                  @NotNull IMultipleTankHandler importFluids) {
        this.activeCheckInventory = importInventory;
        this.activeCheckFluids = importFluids;
        try {
            return checkRecipe(recipe);
        } finally {
            this.activeCheckInventory = null;
            this.activeCheckFluids = null;
        }
    }

    @Override
    protected void completeRecipe() {
        performMufflerOperations();
        super.completeRecipe();
        routedRecipeMap = null;
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
        if (!pendingRollbackItems.isEmpty()) {
            NBTTagList itemList = new NBTTagList();
            for (PendingItemRollback pending : pendingRollbackItems) {
                if (!pending.stack.isEmpty()) {
                    itemList.appendTag(pending.writeToNBT());
                }
            }
            if (itemList.tagCount() > 0) {
                compound.setTag("pendingRollbackItems", itemList);
            }
        }
        if (!pendingRollbackFluids.isEmpty()) {
            NBTTagList fluidList = new NBTTagList();
            for (PendingFluidRollback pending : pendingRollbackFluids) {
                if (pending.fluid != null && pending.fluid.amount > 0) {
                    fluidList.appendTag(pending.writeToNBT());
                }
            }
            if (fluidList.tagCount() > 0) {
                compound.setTag("pendingRollbackFluids", fluidList);
            }
        }
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound compound) {
        super.deserializeNBT(compound);
        this.hasSearchVoltageSnapshot = false;
        clearPreservedAbortReason();
        this.pendingRollbackItems.clear();
        this.pendingRollbackFluids.clear();
        if (compound.hasKey("pendingRollbackItems", Constants.NBT.TAG_LIST)) {
            NBTTagList itemList = compound.getTagList("pendingRollbackItems", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < itemList.tagCount(); i++) {
                PendingItemRollback loaded = PendingItemRollback.readFromNBT(itemList.getCompoundTagAt(i));
                if (loaded != null) {
                    pendingRollbackItems.add(loaded);
                }
            }
        }
        if (compound.hasKey("pendingRollbackFluids", Constants.NBT.TAG_LIST)) {
            NBTTagList fluidList = compound.getTagList("pendingRollbackFluids", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < fluidList.tagCount(); i++) {
                PendingFluidRollback loaded = PendingFluidRollback.readFromNBT(fluidList.getCompoundTagAt(i));
                if (loaded != null) {
                    pendingRollbackFluids.add(loaded);
                }
            }
        }
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

    /**
     * Only routes {@code IParallelableRecipeLogic#findParallelRecipe} between the legacy builder strategies, which
     * all resolve to the same branch here. Whether the parallel scheduler runs at all is decided by
     * {@link #usesParallelScheduler()} and {@link AbstractRecipeLogic#isCrossRecipeParallelEnabled()}.
     */
    @Override
    @NotNull
    public ParallelLogicType getParallelLogicType() {
        return ParallelLogicType.CROSS_RECIPE;
    }

    // ==================== Cross-Recipe Progress Display Overrides ====================

    /**
     * In cross-recipe mode, returns the display slot's progress time for external consumers
     * (TOP, GUI progress bar, etc.) instead of the internal sentinel value (1).
     */
    @Override
    public int getProgress() {
        if (crossRecipeSchedulerActive && crossRecipeScheduler != null && crossRecipeScheduler.hasActiveSlots()) {
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
        if (crossRecipeSchedulerActive && crossRecipeScheduler != null && crossRecipeScheduler.hasActiveSlots()) {
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

    @Override
    protected void decreaseProgress(boolean energyShortage) {
        // 备用电容：跳电时消耗一枚，保住配方（只把进度归零）
        if (energyShortage && ConfigHolder.machines.resetRecipeOnPowerLoss && protectRecipeOnPowerLoss()) {
            return;
        }
        super.decreaseProgress(energyShortage);
    }

    /** 任意一个输入能源仓装了备用电容就从它消耗一枚，并把进度归零；同一次跳电只消耗一枚。 */
    private boolean protectRecipeOnPowerLoss() {
        if (this.progressTime == 0) {
            return true; // 本次跳电已经保过（运行中的配方进度至少为 1）
        }
        if (!(metaTileEntity instanceof MultiblockControllerBase controller)) {
            return false;
        }
        List<IEnergyContainer> hatches = new ArrayList<>(controller.getAbilities(MultiblockAbility.INPUT_ENERGY));
        hatches.addAll(controller.getAbilities(MultiblockAbility.SUBSTATION_INPUT_ENERGY));
        for (IEnergyContainer container : hatches) {
            if (container instanceof IUpgradeSlots hatch &&
                    hatch.consumeUpgradeKit(UpgradeKitType.BACKUP_CAPACITOR)) {
                this.progressTime = 0;
                metaTileEntity.noticePlayer("[跳电保护]位于" + metaTileEntity.getPos() + "的" +
                        metaTileEntity.getStackForm().getDisplayName() + "消耗了备用电容");
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public RecipeMap<?> getRecipeMap() {
        if (routedRecipeMap != null) return routedRecipeMap;
        // if the multiblock has more than one RecipeMap, return the currently selected one
        return getConfiguredRecipeMap();
    }
}
