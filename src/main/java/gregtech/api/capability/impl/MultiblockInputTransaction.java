package gregtech.api.capability.impl;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.util.GTUtility;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Coordinates atomic recipe input planning, masked auxiliary {@link IRecipeMapHolder#checkRecipe(Recipe, boolean)}
 * verification/commit, full-snapshot diff rollback, and channel-isolated pending rollback persistence for
 * {@link MultiblockRecipeLogic}.
 *
 * <p>Architecture:
 * <ol>
 *   <li><b>Stage 1 — Pure In-Memory Joint Planning</b>: Snapshots simulated extractable/drainable capacities and
 *       searches candidate main-recipe allocations while probing {@code holder.checkRecipe(recipe, false)} against
 *       a read-only post-reservation overlay. No physical slots or tanks are mutated during planning.</li>
 *   <li><b>Stage 2 — Masked Two-Phase Commit</b>: Extracts guaranteed-reversible main inputs, invokes
 *       {@code holder.checkRecipe(recipe, true)} with deferred reservations masked on the controller/workable views,
 *       and then verifies &amp; extracts deferred inputs with strict item/fluid + NBT identity checks.</li>
 *   <li><b>Stage 3 — Full-Snapshot Diff Reconciliation</b>: If commit aborts at any point, reconciles every slot and
 *       tank against the pre-commit full inventory snapshot by physical leaf container, preserving any unreturnable
 *       remainder in channel-bound pending rollback entries with unified live and post-reload ownership rules.</li>
 * </ol>
 */
final class MultiblockInputTransaction {

    private static final int MAX_PLAN_PROBES = 64;

    private static final byte ROLLBACK_SOURCE_RAW = 0;
    private static final byte ROLLBACK_SOURCE_ABILITY = 1;
    private static final byte ROLLBACK_SOURCE_DUAL_BUS = 2;
    private static final byte ROLLBACK_SOURCE_CHANNEL_CUSTOM = 3;

    private final MultiblockRecipeLogic logic;
    private final List<PendingItemRollback> pendingRollbackItems = new ArrayList<>();
    private final List<PendingFluidRollback> pendingRollbackFluids = new ArrayList<>();

    private transient long lastPlanSignature = 0L;
    private transient int resumeGroupLeafOffset = 0;
    private transient int resumeLeafOffset = 0;

    @Nullable
    private transient UnaryOperator<IItemHandlerModifiable> activeHolderItemMask;
    @Nullable
    private transient UnaryOperator<IMultipleTankHandler> activeHolderFluidMask;

    MultiblockInputTransaction(@NotNull MultiblockRecipeLogic logic) {
        this.logic = logic;
    }

    IItemHandlerModifiable applyActiveItemMask(@NotNull IItemHandlerModifiable inv) {
        return activeHolderItemMask != null ? activeHolderItemMask.apply(inv) : inv;
    }

    IMultipleTankHandler applyActiveFluidMask(@NotNull IMultipleTankHandler tank) {
        return activeHolderFluidMask != null ? activeHolderFluidMask.apply(tank) : tank;
    }

    boolean commit(@NotNull Recipe recipe,
                   @NotNull IItemHandlerModifiable importInventory,
                   @NotNull IMultipleTankHandler importFluids,
                   int parallelCount,
                   int commitColor) {
        logic.flushPendingRollbacks();
        if (parallelCount <= 0 || !recipe.matches(false, importInventory, importFluids)) {
            return false;
        }

        int slotCount = importInventory.getSlots();
        ItemStack[] fullOriginalSlots = new ItemStack[slotCount];
        int[] extractableSlotCounts = new int[slotCount];
        ItemStack[] slotSnapshots = new ItemStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            ItemStack raw = importInventory.getStackInSlot(i);
            fullOriginalSlots[i] = raw.isEmpty() ? ItemStack.EMPTY : raw.copy();
            int rawCount = raw.isEmpty() ? 0 : raw.getCount();
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

        List<IMultipleTankHandler.ITankEntry> tanks = importFluids.getFluidTanks();
        int tankCount = tanks.size();
        FluidStack[] fullOriginalTanks = new FluidStack[tankCount];
        int[] drainableTankAmounts = new int[tankCount];
        FluidStack[] tankSnapshots = new FluidStack[tankCount];
        for (int t = 0; t < tankCount; t++) {
            FluidStack raw = tanks.get(t).getFluid();
            fullOriginalTanks[t] = (raw == null || raw.amount <= 0) ? null : raw.copy();
            int rawAmount = raw == null ? 0 : Math.max(0, raw.amount);
            if (rawAmount > 0) {
                FluidStack simulated = tanks.get(t).drain(rawAmount, false);
                if (simulated != null && simulated.amount > 0 && raw.isFluidEqual(simulated)) {
                    int drainable = Math.min(rawAmount, simulated.amount);
                    drainableTankAmounts[t] = drainable;
                    tankSnapshots[t] = GTUtility.copy(drainable, simulated);
                }
            }
        }

        List<GTRecipeInput> consumableItemInputs = new ArrayList<>();
        for (GTRecipeInput recipeInput : recipe.getInputs()) {
            if (!recipeInput.isNonConsumable() && recipeInput.getAmount() > 0) {
                consumableItemInputs.add(recipeInput);
            }
        }
        List<GTRecipeInput> consumableFluidInputs = new ArrayList<>();
        for (GTRecipeInput recipeFluidInput : recipe.getFluidInputs()) {
            if (!recipeFluidInput.isNonConsumable() && recipeFluidInput.getAmount() > 0) {
                consumableFluidInputs.add(recipeFluidInput);
            }
        }

        // Stage 1: Pure side-effect-free joint planning with non-consuming auxiliary verification
        JointPlan plan = planJointAllocation(
                recipe, parallelCount,
                importInventory, consumableItemInputs, slotSnapshots, extractableSlotCounts,
                tanks, consumableFluidInputs, tankSnapshots, drainableTankAmounts);
        if (plan == null) {
            logic.ensureConditionFailureReason();
            return false;
        }

        int[] phase1ItemPlan = new int[slotCount];
        int[] phase2ItemPlan = new int[slotCount];
        boolean[] slotRollbackable = new boolean[slotCount];
        for (int i = 0; i < slotCount; i++) {
            int totalForSlot = plan.itemPlan[i];
            if (totalForSlot <= 0) continue;
            slotRollbackable[i] = canGuaranteeItemRollback(
                    importInventory, i, slotSnapshots[i], fullOriginalSlots[i].getCount());
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
                if (!slotRollbackable[i] && plan.itemMatrix[k][i] > 0L) {
                    deferredForInput += plan.itemMatrix[k][i];
                }
            }
            if (deferredForInput > 0L) {
                if (deferredItemInputs == null) {
                    deferredItemInputs = new ArrayList<>();
                }
                deferredItemInputs.add(new DeferredIngredientAmount(consumableItemInputs.get(k), deferredForInput));
            }
        }

        int[] phase1FluidPlan = new int[tankCount];
        int[] phase2FluidPlan = new int[tankCount];
        boolean[] tankRollbackable = new boolean[tankCount];
        for (int t = 0; t < tankCount; t++) {
            int totalForTank = plan.fluidPlan[t];
            if (totalForTank <= 0) continue;
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
                if (!tankRollbackable[t] && plan.fluidMatrix[k][t] > 0L) {
                    deferredForInput += plan.fluidMatrix[k][t];
                }
            }
            if (deferredForInput > 0L) {
                if (deferredFluidInputs == null) {
                    deferredFluidInputs = new ArrayList<>();
                }
                deferredFluidInputs.add(new DeferredIngredientAmount(consumableFluidInputs.get(k), deferredForInput));
            }
        }

        // Stage 2: Extract reversible inputs, run masked holder.checkRecipe(recipe, true), then extract deferred inputs
        List<IItemHandlerModifiable> preItemNotifs = new ArrayList<>(
                logic.getMetaTileEntity().getNotifiedItemInputList());
        List<IFluidHandler> preFluidNotifs = new ArrayList<>(
                logic.getMetaTileEntity().getNotifiedFluidInputList());

        int[] phase1ExtractedCounts = new int[slotCount];
        int[] phase1DrainedAmounts = new int[tankCount];
        boolean extractFailed = false;
        for (int i = 0; i < slotCount; i++) {
            if (phase1ItemPlan[i] <= 0) continue;
            ItemStack extracted = importInventory.extractItem(i, phase1ItemPlan[i], false);
            if (!extracted.isEmpty() && !ItemHandlerHelper.canItemStacksStack(extracted, slotSnapshots[i])) {
                returnUnexpectedExtractedItem(importInventory, i, extracted, commitColor);
                extractFailed = true;
                break;
            }
            phase1ExtractedCounts[i] = extracted.getCount();
            if (extracted.getCount() < phase1ItemPlan[i]) {
                extractFailed = true;
                break;
            }
        }
        if (!extractFailed) {
            for (int t = 0; t < tankCount; t++) {
                if (phase1FluidPlan[t] <= 0) continue;
                FluidStack drained = tanks.get(t).drain(phase1FluidPlan[t], true);
                if (drained != null && (tankSnapshots[t] == null || !drained.isFluidEqual(tankSnapshots[t]))) {
                    returnUnexpectedDrainedFluid(importFluids, tanks.get(t), drained, commitColor);
                    extractFailed = true;
                    break;
                }
                phase1DrainedAmounts[t] = drained == null ? 0 : drained.amount;
                if (drained == null || drained.amount < phase1FluidPlan[t]) {
                    extractFailed = true;
                    break;
                }
            }
        }

        if (extractFailed || !invokeHolderCheckWithOverlay(recipe, true, importInventory, phase2ItemPlan, slotSnapshots,
                tanks, phase2FluidPlan, tankSnapshots)) {
            boolean holderMutated = !extractFailed &&
                    didHolderMutateInputs(importInventory, fullOriginalSlots, phase1ExtractedCounts,
                            tanks, fullOriginalTanks, phase1DrainedAmounts);
            reconcileAndRollbackAll(importInventory, fullOriginalSlots, importFluids, tanks, fullOriginalTanks,
                    commitColor);
            restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
            if (holderMutated) {
                logic.markHolderLossInputInvalidated(importInventory);
            } else if (!extractFailed) {
                logic.ensureConditionFailureReason();
            }
            return false;
        }

        int[] phase2ExtractedCounts = new int[slotCount];
        int[] phase2DrainedAmounts = new int[tankCount];

        if (deferredItemInputs != null) {
            ReplannedItems activeItems = canSimulateItemPlan(importInventory, phase2ItemPlan, slotSnapshots) ?
                    new ReplannedItems(phase2ItemPlan, slotSnapshots) :
                    replanDeferredItems(importInventory, deferredItemInputs);
            if (activeItems == null) {
                abortPhase2(importInventory, fullOriginalSlots, phase1ExtractedCounts, phase2ExtractedCounts,
                        importFluids, tanks, fullOriginalTanks, phase1DrainedAmounts, phase2DrainedAmounts,
                        preItemNotifs, preFluidNotifs, commitColor);
                return false;
            }
            for (int i = 0; i < slotCount; i++) {
                if (activeItems.counts[i] <= 0) continue;
                ItemStack expected = activeItems.expectedSnapshots[i];
                ItemStack extracted = importInventory.extractItem(i, activeItems.counts[i], false);
                boolean matchesExpected = !expected.isEmpty() &&
                        ItemHandlerHelper.canItemStacksStack(extracted, expected);
                if (!matchesExpected) {
                    returnUnexpectedExtractedItem(importInventory, i, extracted, commitColor);
                } else {
                    phase2ExtractedCounts[i] += extracted.getCount();
                }
                if (!matchesExpected || extracted.getCount() < activeItems.counts[i]) {
                    abortPhase2(importInventory, fullOriginalSlots, phase1ExtractedCounts, phase2ExtractedCounts,
                            importFluids, tanks, fullOriginalTanks, phase1DrainedAmounts, phase2DrainedAmounts,
                            preItemNotifs, preFluidNotifs, commitColor);
                    return false;
                }
            }
        }

        if (deferredFluidInputs != null) {
            ReplannedFluids activeFluids = canSimulateFluidPlan(tanks, phase2FluidPlan, tankSnapshots) ?
                    new ReplannedFluids(phase2FluidPlan, tankSnapshots) :
                    replanDeferredFluids(tanks, deferredFluidInputs);
            if (activeFluids == null) {
                abortPhase2(importInventory, fullOriginalSlots, phase1ExtractedCounts, phase2ExtractedCounts,
                        importFluids, tanks, fullOriginalTanks, phase1DrainedAmounts, phase2DrainedAmounts,
                        preItemNotifs, preFluidNotifs, commitColor);
                return false;
            }
            for (int t = 0; t < tankCount; t++) {
                if (activeFluids.amounts[t] <= 0) continue;
                FluidStack expected = activeFluids.expectedSnapshots[t];
                FluidStack drained = tanks.get(t).drain(activeFluids.amounts[t], true);
                boolean matchesExpected = drained != null && expected != null && drained.isFluidEqual(expected);
                if (!matchesExpected) {
                    returnUnexpectedDrainedFluid(importFluids, tanks.get(t), drained, commitColor);
                } else {
                    phase2DrainedAmounts[t] += drained.amount;
                }
                if (!matchesExpected || drained.amount < activeFluids.amounts[t]) {
                    abortPhase2(importInventory, fullOriginalSlots, phase1ExtractedCounts, phase2ExtractedCounts,
                            importFluids, tanks, fullOriginalTanks, phase1DrainedAmounts, phase2DrainedAmounts,
                            preItemNotifs, preFluidNotifs, commitColor);
                    return false;
                }
            }
        }
        return true;
    }

    private void abortPhase2(@NotNull IItemHandlerModifiable importInventory,
                             @NotNull ItemStack[] fullOriginalSlots,
                             @NotNull int[] phase1ExtractedCounts,
                             @NotNull int[] phase2ExtractedCounts,
                             @NotNull IMultipleTankHandler importFluids,
                             @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                             @NotNull FluidStack[] fullOriginalTanks,
                             @NotNull int[] phase1DrainedAmounts,
                             @NotNull int[] phase2DrainedAmounts,
                             @NotNull List<IItemHandlerModifiable> preItemNotifs,
                             @NotNull List<IFluidHandler> preFluidNotifs,
                             int commitColor) {
        int[] combinedItems = new int[phase1ExtractedCounts.length];
        for (int i = 0; i < combinedItems.length; i++) {
            combinedItems[i] = saturatingIntAdd(phase1ExtractedCounts[i], phase2ExtractedCounts[i]);
        }
        int[] combinedFluids = new int[phase1DrainedAmounts.length];
        for (int t = 0; t < combinedFluids.length; t++) {
            combinedFluids[t] = saturatingIntAdd(phase1DrainedAmounts[t], phase2DrainedAmounts[t]);
        }
        boolean holderMutated = didHolderMutateInputs(importInventory, fullOriginalSlots, combinedItems,
                tanks, fullOriginalTanks, combinedFluids);
        reconcileAndRollbackAll(importInventory, fullOriginalSlots, importFluids, tanks, fullOriginalTanks,
                commitColor);
        restorePreCommitInputNotifications(preItemNotifs, preFluidNotifs);
        if (holderMutated) {
            logic.markHolderLossInputInvalidated(importInventory);
        } else {
            logic.ensureConditionFailureReason();
        }
    }

    private static boolean didHolderMutateInputs(@NotNull IItemHandlerModifiable importInventory,
                                                 @NotNull ItemStack[] fullOriginalSlots,
                                                 @NotNull int[] ownExtractedCounts,
                                                 @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                                 @NotNull FluidStack[] fullOriginalTanks,
                                                 @NotNull int[] ownDrainedAmounts) {
        for (int i = 0; i < fullOriginalSlots.length; i++) {
            ItemStack orig = fullOriginalSlots[i];
            if (orig == null || orig.isEmpty()) continue;
            ItemStack now = importInventory.getStackInSlot(i);
            int nowMatching = (!now.isEmpty() && ItemHandlerHelper.canItemStacksStack(now, orig)) ? now.getCount() : 0;
            int expectedRemaining = Math.max(0, orig.getCount() - ownExtractedCounts[i]);
            if (nowMatching < expectedRemaining) {
                return true;
            }
        }
        for (int t = 0; t < fullOriginalTanks.length; t++) {
            FluidStack orig = fullOriginalTanks[t];
            if (orig == null || orig.amount <= 0) continue;
            FluidStack now = tanks.get(t).getFluid();
            int nowMatching = (now != null && now.isFluidEqual(orig)) ? Math.max(0, now.amount) : 0;
            int expectedRemaining = Math.max(0, orig.amount - ownDrainedAmounts[t]);
            if (nowMatching < expectedRemaining) {
                return true;
            }
        }
        return false;
    }

    // ==================== Stage 1: Joint Pure Simulation Planner ====================

    private static final class JointPlan {

        final long[][] itemMatrix;
        final int[] itemPlan;
        final long[][] fluidMatrix;
        final int[] fluidPlan;

        JointPlan(long[][] itemMatrix, int[] itemPlan, long[][] fluidMatrix, int[] fluidPlan) {
            this.itemMatrix = itemMatrix;
            this.itemPlan = itemPlan;
            this.fluidMatrix = fluidMatrix;
            this.fluidPlan = fluidPlan;
        }
    }

    private static final class PlanSearchState {

        final int skipLeaves;
        final int maxProbes;
        int leavesVisited = 0;
        int probes = 0;
        boolean hitProbeCap = false;
        @Nullable
        JointPlan accepted = null;

        PlanSearchState(int skipLeaves, int maxProbes) {
            this.skipLeaves = skipLeaves;
            this.maxProbes = maxProbes;
        }
    }

    private static final class ItemSlotGroup {

        final ItemStack template;
        final List<Integer> slots = new ArrayList<>();
        long totalAvail;

        ItemSlotGroup(@NotNull ItemStack template, int firstSlot, int avail) {
            this.template = template;
            this.slots.add(firstSlot);
            this.totalAvail = avail;
        }
    }

    private static final class FluidTankGroup {

        final FluidStack template;
        final List<Integer> tanks = new ArrayList<>();
        long totalAvail;

        FluidTankGroup(@NotNull FluidStack template, int firstTank, int avail) {
            this.template = template;
            this.tanks.add(firstTank);
            this.totalAvail = avail;
        }
    }

    @Nullable
    private JointPlan planJointAllocation(@NotNull Recipe recipe,
                                          int parallelCount,
                                          @NotNull IItemHandlerModifiable importInventory,
                                          @NotNull List<GTRecipeInput> itemInputs,
                                          @NotNull ItemStack[] slotSnapshots,
                                          @NotNull int[] extractableCounts,
                                          @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                          @NotNull List<GTRecipeInput> fluidInputs,
                                          @NotNull FluidStack[] tankSnapshots,
                                          @NotNull int[] drainableAmounts) {
        int mItems = itemInputs.size();
        int slotCount = slotSnapshots.length;
        int mFluids = fluidInputs.size();
        int tankCount = tankSnapshots.length;

        int[] itemOrder = sortedItemInputOrder(itemInputs, slotSnapshots, extractableCounts);
        int[] fluidOrder = sortedFluidInputOrder(fluidInputs, tankSnapshots, drainableAmounts);

        // 1. Fast Path: single greedy pass in constrained ingredient order
        long[][] fastItems = new long[mItems][slotCount];
        long[][] fastFluids = new long[mFluids][tankCount];
        if (tryGreedyItems(itemInputs, parallelCount, slotSnapshots, extractableCounts, itemOrder, fastItems) &&
                tryGreedyFluids(fluidInputs, parallelCount, tankSnapshots, drainableAmounts, fluidOrder, fastFluids)) {
            int[] iPlan = sumMatrixColumns(fastItems, mItems, slotCount);
            int[] fPlan = sumMatrixColumns(fastFluids, mFluids, tankCount);
            if (canSimulateItemPlan(importInventory, iPlan, slotSnapshots) &&
                    canSimulateFluidPlan(tanks, fPlan, tankSnapshots) &&
                    invokeHolderCheckWithOverlay(recipe, false, importInventory, iPlan, slotSnapshots,
                            tanks, fPlan, tankSnapshots)) {
                this.resumeGroupLeafOffset = 0;
                this.resumeLeafOffset = 0;
                return new JointPlan(fastItems, iPlan, fastFluids, fPlan);
            }
        } else {
            this.resumeGroupLeafOffset = 0;
            this.resumeLeafOffset = 0;
            return null;
        }

        long sig = computePlanSignature(recipe, parallelCount, slotSnapshots, extractableCounts,
                tankSnapshots, drainableAmounts);
        boolean sameSignature = sig == this.lastPlanSignature;
        int startGroupSkip = sameSignature ? this.resumeGroupLeafOffset : 0;
        int startSkip = sameSignature ? this.resumeLeafOffset : 0;
        int remainingProbes = MAX_PLAN_PROBES;

        // 2. Equivalence-Group Backtracking: collapses multi-tank/multi-slot splits of identical stacks/fluids
        List<ItemSlotGroup> itemGroups = buildItemSlotGroups(slotSnapshots, extractableCounts);
        List<FluidTankGroup> fluidGroups = buildFluidTankGroups(tankSnapshots, drainableAmounts);
        if (itemGroups.size() < countPositive(extractableCounts) ||
                fluidGroups.size() < countPositive(drainableAmounts)) {
            PlanSearchState groupState = new PlanSearchState(startGroupSkip, remainingProbes);
            long[][] groupItemMatrix = new long[mItems][itemGroups.size()];
            long[][] groupFluidMatrix = new long[mFluids][fluidGroups.size()];
            long[] availItemGroups = new long[itemGroups.size()];
            for (int g = 0; g < itemGroups.size(); g++) availItemGroups[g] = itemGroups.get(g).totalAvail;
            long[] availFluidGroups = new long[fluidGroups.size()];
            for (int g = 0; g < fluidGroups.size(); g++) availFluidGroups[g] = fluidGroups.get(g).totalAvail;

            if (searchGroupedItems(recipe, parallelCount, importInventory, itemInputs, itemOrder, 0, 0, -1L,
                    itemGroups, slotSnapshots, extractableCounts, availItemGroups, groupItemMatrix,
                    tanks, fluidInputs, fluidOrder, fluidGroups, tankSnapshots, drainableAmounts,
                    availFluidGroups, groupFluidMatrix, groupState) && groupState.accepted != null) {
                this.resumeGroupLeafOffset = 0;
                this.resumeLeafOffset = 0;
                return groupState.accepted;
            }
            if (groupState.hitProbeCap) {
                this.lastPlanSignature = sig;
                this.resumeGroupLeafOffset = groupState.leavesVisited;
                return null;
            }
            this.resumeGroupLeafOffset = 0;
            remainingProbes = Math.max(0, MAX_PLAN_PROBES - groupState.probes);
            if (remainingProbes == 0) {
                this.lastPlanSignature = sig;
                return null;
            }
        }

        // 3. Resumable Per-Slot / Per-Tank Backtracking: continues from last unprobed leaf when inputs are unchanged
        PlanSearchState state = new PlanSearchState(startSkip, remainingProbes);
        long[][] workItemMatrix = new long[mItems][slotCount];
        long[][] workFluidMatrix = new long[mFluids][tankCount];
        int[] availItems = extractableCounts.clone();
        int[] availFluids = drainableAmounts.clone();

        searchItemAllocations(
                recipe, parallelCount,
                importInventory, itemInputs, itemOrder, 0, 0, -1L, slotSnapshots, availItems, workItemMatrix,
                tanks, fluidInputs, fluidOrder, tankSnapshots, availFluids, workFluidMatrix, state);
        if (state.accepted != null) {
            this.resumeGroupLeafOffset = 0;
            this.resumeLeafOffset = 0;
            return state.accepted;
        }
        if (state.hitProbeCap) {
            this.lastPlanSignature = sig;
            this.resumeLeafOffset = state.leavesVisited;
        } else {
            this.lastPlanSignature = sig;
            this.resumeLeafOffset = 0;
        }
        return null;
    }

    private static int countPositive(@NotNull int[] values) {
        int count = 0;
        for (int v : values) {
            if (v > 0) count++;
        }
        return count;
    }

    private static List<ItemSlotGroup> buildItemSlotGroups(@NotNull ItemStack[] snapshots, @NotNull int[] available) {
        List<ItemSlotGroup> groups = new ArrayList<>();
        for (int i = 0; i < snapshots.length; i++) {
            if (available[i] <= 0 || snapshots[i].isEmpty()) continue;
            ItemSlotGroup matched = null;
            for (ItemSlotGroup g : groups) {
                if (ItemHandlerHelper.canItemStacksStack(g.template, snapshots[i])) {
                    matched = g;
                    break;
                }
            }
            if (matched == null) {
                groups.add(new ItemSlotGroup(snapshots[i], i, available[i]));
            } else {
                matched.slots.add(i);
                matched.totalAvail += available[i];
            }
        }
        return groups;
    }

    private static List<FluidTankGroup> buildFluidTankGroups(@NotNull FluidStack[] snapshots,
                                                             @NotNull int[] available) {
        List<FluidTankGroup> groups = new ArrayList<>();
        for (int t = 0; t < snapshots.length; t++) {
            if (available[t] <= 0 || snapshots[t] == null) continue;
            FluidTankGroup matched = null;
            for (FluidTankGroup g : groups) {
                if (g.template.isFluidEqual(snapshots[t])) {
                    matched = g;
                    break;
                }
            }
            if (matched == null) {
                groups.add(new FluidTankGroup(snapshots[t], t, available[t]));
            } else {
                matched.tanks.add(t);
                matched.totalAvail += available[t];
            }
        }
        return groups;
    }

    private boolean searchGroupedItems(@NotNull Recipe recipe,
                                       int parallelCount,
                                       @NotNull IItemHandlerModifiable importInventory,
                                       @NotNull List<GTRecipeInput> itemInputs,
                                       @NotNull int[] itemOrder,
                                       int orderPos,
                                       int groupIdx,
                                       long remaining,
                                       @NotNull List<ItemSlotGroup> itemGroups,
                                       @NotNull ItemStack[] slotSnapshots,
                                       @NotNull int[] extractableCounts,
                                       @NotNull long[] availGroups,
                                       @NotNull long[][] groupItemMatrix,
                                       @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                       @NotNull List<GTRecipeInput> fluidInputs,
                                       @NotNull int[] fluidOrder,
                                       @NotNull List<FluidTankGroup> fluidGroups,
                                       @NotNull FluidStack[] tankSnapshots,
                                       @NotNull int[] drainableAmounts,
                                       @NotNull long[] availFluidGroups,
                                       @NotNull long[][] groupFluidMatrix,
                                       @NotNull PlanSearchState state) {
        if (state.probes >= state.maxProbes) {
            state.hitProbeCap = true;
            return false;
        }
        if (orderPos >= itemOrder.length) {
            return searchGroupedFluids(recipe, parallelCount, importInventory, itemInputs.size(),
                    itemGroups, slotSnapshots, extractableCounts, groupItemMatrix,
                    tanks, fluidInputs, fluidOrder, 0, 0, -1L, fluidGroups, tankSnapshots, drainableAmounts,
                    availFluidGroups, groupFluidMatrix, state);
        }
        int k = itemOrder[orderPos];
        GTRecipeInput input = itemInputs.get(k);
        long rem = remaining < 0L ? (long) input.getAmount() * parallelCount : remaining;
        if (rem <= 0L) {
            return searchGroupedItems(recipe, parallelCount, importInventory, itemInputs, itemOrder,
                    orderPos + 1, 0, -1L, itemGroups, slotSnapshots, extractableCounts, availGroups, groupItemMatrix,
                    tanks, fluidInputs, fluidOrder, fluidGroups, tankSnapshots, drainableAmounts,
                    availFluidGroups, groupFluidMatrix, state);
        }
        int nextGroup = -1;
        for (int g = groupIdx; g < itemGroups.size(); g++) {
            if (availGroups[g] > 0L && input.acceptsStack(itemGroups.get(g).template)) {
                nextGroup = g;
                break;
            }
        }
        if (nextGroup < 0) return false;

        long suffixAfter = 0L;
        for (int j = nextGroup + 1; j < itemGroups.size(); j++) {
            if (availGroups[j] > 0L && input.acceptsStack(itemGroups.get(j).template)) {
                suffixAfter += availGroups[j];
            }
        }
        if (availGroups[nextGroup] + suffixAfter < rem) return false;

        long maxTake = Math.min(rem, availGroups[nextGroup]);
        long minTake = Math.max(0L, rem - suffixAfter);

        availGroups[nextGroup] -= maxTake;
        groupItemMatrix[k][nextGroup] += maxTake;
        boolean ok = searchGroupedItems(recipe, parallelCount, importInventory, itemInputs, itemOrder,
                orderPos, nextGroup + 1, rem - maxTake, itemGroups, slotSnapshots, extractableCounts, availGroups,
                groupItemMatrix, tanks, fluidInputs, fluidOrder, fluidGroups, tankSnapshots, drainableAmounts,
                availFluidGroups, groupFluidMatrix, state);
        groupItemMatrix[k][nextGroup] -= maxTake;
        availGroups[nextGroup] += maxTake;
        if (ok) return true;

        if (minTake < maxTake && state.probes < state.maxProbes) {
            availGroups[nextGroup] -= minTake;
            groupItemMatrix[k][nextGroup] += minTake;
            ok = searchGroupedItems(recipe, parallelCount, importInventory, itemInputs, itemOrder,
                    orderPos, nextGroup + 1, rem - minTake, itemGroups, slotSnapshots, extractableCounts, availGroups,
                    groupItemMatrix, tanks, fluidInputs, fluidOrder, fluidGroups, tankSnapshots, drainableAmounts,
                    availFluidGroups, groupFluidMatrix, state);
            groupItemMatrix[k][nextGroup] -= minTake;
            availGroups[nextGroup] += minTake;
            return ok;
        }
        return false;
    }

    private boolean searchGroupedFluids(@NotNull Recipe recipe,
                                        int parallelCount,
                                        @NotNull IItemHandlerModifiable importInventory,
                                        int mItems,
                                        @NotNull List<ItemSlotGroup> itemGroups,
                                        @NotNull ItemStack[] slotSnapshots,
                                        @NotNull int[] extractableCounts,
                                        @NotNull long[][] groupItemMatrix,
                                        @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                        @NotNull List<GTRecipeInput> fluidInputs,
                                        @NotNull int[] fluidOrder,
                                        int orderPos,
                                        int groupIdx,
                                        long remaining,
                                        @NotNull List<FluidTankGroup> fluidGroups,
                                        @NotNull FluidStack[] tankSnapshots,
                                        @NotNull int[] drainableAmounts,
                                        @NotNull long[] availFluidGroups,
                                        @NotNull long[][] groupFluidMatrix,
                                        @NotNull PlanSearchState state) {
        if (state.probes >= state.maxProbes) {
            state.hitProbeCap = true;
            return false;
        }
        if (orderPos >= fluidOrder.length) {
            long[][] slotMatrix = materializeItemGroupMatrix(
                    mItems, slotSnapshots.length, itemGroups, extractableCounts, groupItemMatrix);
            long[][] tankMatrix = materializeFluidGroupMatrix(
                    fluidInputs.size(), tankSnapshots.length, fluidGroups, drainableAmounts, groupFluidMatrix);
            int[] iPlan = sumMatrixColumns(slotMatrix, mItems, slotSnapshots.length);
            int[] fPlan = sumMatrixColumns(tankMatrix, fluidInputs.size(), tankSnapshots.length);
            if (!canSimulateItemPlan(importInventory, iPlan, slotSnapshots) ||
                    !canSimulateFluidPlan(tanks, fPlan, tankSnapshots)) {
                return false;
            }
            state.leavesVisited++;
            if (state.leavesVisited <= state.skipLeaves) {
                return false;
            }
            state.probes++;
            if (invokeHolderCheckWithOverlay(recipe, false, importInventory, iPlan, slotSnapshots,
                    tanks, fPlan, tankSnapshots)) {
                state.accepted = new JointPlan(slotMatrix, iPlan, tankMatrix, fPlan);
                return true;
            }
            if (state.probes >= state.maxProbes) {
                state.hitProbeCap = true;
            }
            return false;
        }
        int k = fluidOrder[orderPos];
        GTRecipeInput input = fluidInputs.get(k);
        long rem = remaining < 0L ? (long) input.getAmount() * parallelCount : remaining;
        if (rem <= 0L) {
            return searchGroupedFluids(recipe, parallelCount, importInventory, mItems, itemGroups, slotSnapshots,
                    extractableCounts, groupItemMatrix, tanks, fluidInputs, fluidOrder, orderPos + 1, 0, -1L,
                    fluidGroups, tankSnapshots, drainableAmounts, availFluidGroups, groupFluidMatrix, state);
        }
        int nextGroup = -1;
        for (int g = groupIdx; g < fluidGroups.size(); g++) {
            if (availFluidGroups[g] > 0L && input.acceptsFluid(fluidGroups.get(g).template)) {
                nextGroup = g;
                break;
            }
        }
        if (nextGroup < 0) return false;

        long suffixAfter = 0L;
        for (int j = nextGroup + 1; j < fluidGroups.size(); j++) {
            if (availFluidGroups[j] > 0L && input.acceptsFluid(fluidGroups.get(j).template)) {
                suffixAfter += availFluidGroups[j];
            }
        }
        if (availFluidGroups[nextGroup] + suffixAfter < rem) return false;

        long maxTake = Math.min(rem, availFluidGroups[nextGroup]);
        long minTake = Math.max(0L, rem - suffixAfter);

        availFluidGroups[nextGroup] -= maxTake;
        groupFluidMatrix[k][nextGroup] += maxTake;
        boolean ok = searchGroupedFluids(recipe, parallelCount, importInventory, mItems, itemGroups, slotSnapshots,
                extractableCounts, groupItemMatrix, tanks, fluidInputs, fluidOrder, orderPos, nextGroup + 1,
                rem - maxTake, fluidGroups, tankSnapshots, drainableAmounts, availFluidGroups, groupFluidMatrix, state);
        groupFluidMatrix[k][nextGroup] -= maxTake;
        availFluidGroups[nextGroup] += maxTake;
        if (ok) return true;

        if (minTake < maxTake && state.probes < state.maxProbes) {
            availFluidGroups[nextGroup] -= minTake;
            groupFluidMatrix[k][nextGroup] += minTake;
            ok = searchGroupedFluids(recipe, parallelCount, importInventory, mItems, itemGroups, slotSnapshots,
                    extractableCounts, groupItemMatrix, tanks, fluidInputs, fluidOrder, orderPos, nextGroup + 1,
                    rem - minTake, fluidGroups, tankSnapshots, drainableAmounts, availFluidGroups, groupFluidMatrix,
                    state);
            groupFluidMatrix[k][nextGroup] -= minTake;
            availFluidGroups[nextGroup] += minTake;
            return ok;
        }
        return false;
    }

    private static long[][] materializeItemGroupMatrix(int mItems,
                                                       int slotCount,
                                                       @NotNull List<ItemSlotGroup> groups,
                                                       @NotNull int[] extractableCounts,
                                                       @NotNull long[][] groupMatrix) {
        long[][] out = new long[mItems][slotCount];
        int[] remSlot = extractableCounts.clone();
        for (int g = 0; g < groups.size(); g++) {
            List<Integer> memberSlots = groups.get(g).slots;
            for (int k = 0; k < mItems; k++) {
                long need = groupMatrix[k][g];
                for (int idx = 0; idx < memberSlots.size() && need > 0L; idx++) {
                    int s = memberSlots.get(idx);
                    if (remSlot[s] <= 0) continue;
                    int take = (int) Math.min(need, remSlot[s]);
                    remSlot[s] -= take;
                    out[k][s] += take;
                    need -= take;
                }
            }
        }
        return out;
    }

    private static long[][] materializeFluidGroupMatrix(int mFluids,
                                                        int tankCount,
                                                        @NotNull List<FluidTankGroup> groups,
                                                        @NotNull int[] drainableAmounts,
                                                        @NotNull long[][] groupMatrix) {
        long[][] out = new long[mFluids][tankCount];
        int[] remTank = drainableAmounts.clone();
        for (int g = 0; g < groups.size(); g++) {
            List<Integer> memberTanks = groups.get(g).tanks;
            for (int k = 0; k < mFluids; k++) {
                long need = groupMatrix[k][g];
                for (int idx = 0; idx < memberTanks.size() && need > 0L; idx++) {
                    int t = memberTanks.get(idx);
                    if (remTank[t] <= 0) continue;
                    int take = (int) Math.min(need, remTank[t]);
                    remTank[t] -= take;
                    out[k][t] += take;
                    need -= take;
                }
            }
        }
        return out;
    }

    private static long computePlanSignature(@NotNull Recipe recipe,
                                             int parallelCount,
                                             @NotNull ItemStack[] slotSnapshots,
                                             @NotNull int[] extractableCounts,
                                             @NotNull FluidStack[] tankSnapshots,
                                             @NotNull int[] drainableAmounts) {
        long h = recipe.getEUt();
        h = h * 31L + recipe.getDuration();
        h = h * 31L + recipe.getInputs().size();
        for (GTRecipeInput in : recipe.getInputs()) {
            h = h * 31L + in.getAmount();
            h = h * 31L + (in.isNonConsumable() ? 1L : 0L);
        }
        h = h * 31L + recipe.getFluidInputs().size();
        for (GTRecipeInput fin : recipe.getFluidInputs()) {
            h = h * 31L + fin.getAmount();
            h = h * 31L + (fin.isNonConsumable() ? 1L : 0L);
        }
        for (ItemStack out : recipe.getOutputs()) {
            if (!out.isEmpty()) {
                h = h * 31L + System.identityHashCode(out.getItem());
                h = h * 31L + out.getMetadata();
                h = h * 31L + out.getCount();
            }
        }
        for (FluidStack fout : recipe.getFluidOutputs()) {
            if (fout != null) {
                h = h * 31L + System.identityHashCode(fout.getFluid());
                h = h * 31L + fout.amount;
            }
        }
        h = h * 31L + parallelCount;
        h = h * 31L + slotSnapshots.length;
        for (int i = 0; i < slotSnapshots.length; i++) {
            ItemStack s = slotSnapshots[i];
            h = h * 31L + extractableCounts[i];
            if (!s.isEmpty()) {
                h = h * 31L + System.identityHashCode(s.getItem());
                h = h * 31L + s.getMetadata();
                h = h * 31L + (s.getTagCompound() == null ? 0 : s.getTagCompound().hashCode());
            }
        }
        h = h * 31L + tankSnapshots.length;
        for (int t = 0; t < tankSnapshots.length; t++) {
            FluidStack f = tankSnapshots[t];
            h = h * 31L + drainableAmounts[t];
            if (f != null) {
                h = h * 31L + System.identityHashCode(f.getFluid());
                h = h * 31L + (f.tag == null ? 0 : f.tag.hashCode());
            }
        }
        return h;
    }

    private boolean searchItemAllocations(@NotNull Recipe recipe,
                                          int parallelCount,
                                          @NotNull IItemHandlerModifiable importInventory,
                                          @NotNull List<GTRecipeInput> itemInputs,
                                          @NotNull int[] itemOrder,
                                          int orderPos,
                                          int slotIdx,
                                          long remaining,
                                          @NotNull ItemStack[] slotSnapshots,
                                          @NotNull int[] availItems,
                                          @NotNull long[][] itemMatrix,
                                          @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                          @NotNull List<GTRecipeInput> fluidInputs,
                                          @NotNull int[] fluidOrder,
                                          @NotNull FluidStack[] tankSnapshots,
                                          @NotNull int[] availFluids,
                                          @NotNull long[][] fluidMatrix,
                                          @NotNull PlanSearchState state) {
        if (state.probes >= state.maxProbes) {
            state.hitProbeCap = true;
            return false;
        }
        if (orderPos >= itemOrder.length) {
            return searchFluidAllocations(
                    recipe, parallelCount,
                    importInventory, itemInputs.size(), slotSnapshots, itemMatrix,
                    tanks, fluidInputs, fluidOrder, 0, 0, -1L, tankSnapshots, availFluids, fluidMatrix, state);
        }

        int k = itemOrder[orderPos];
        GTRecipeInput input = itemInputs.get(k);
        long rem = remaining < 0L ? (long) input.getAmount() * parallelCount : remaining;
        if (rem <= 0L) {
            return searchItemAllocations(recipe, parallelCount, importInventory, itemInputs, itemOrder,
                    orderPos + 1, 0, -1L, slotSnapshots, availItems, itemMatrix,
                    tanks, fluidInputs, fluidOrder, tankSnapshots, availFluids, fluidMatrix, state);
        }

        int nextSlot = -1;
        for (int i = slotIdx; i < slotSnapshots.length; i++) {
            if (availItems[i] > 0 && !slotSnapshots[i].isEmpty() && input.acceptsStack(slotSnapshots[i])) {
                nextSlot = i;
                break;
            }
        }
        if (nextSlot < 0) return false;

        long suffixAfter = 0L;
        for (int j = nextSlot + 1; j < slotSnapshots.length; j++) {
            if (availItems[j] > 0 && !slotSnapshots[j].isEmpty() && input.acceptsStack(slotSnapshots[j])) {
                suffixAfter += availItems[j];
            }
        }
        if (availItems[nextSlot] + suffixAfter < rem) {
            return false;
        }

        int maxTake = (int) Math.min(rem, availItems[nextSlot]);
        int minTake = (int) Math.max(0L, rem - suffixAfter);

        availItems[nextSlot] -= maxTake;
        itemMatrix[k][nextSlot] += maxTake;
        boolean ok = searchItemAllocations(recipe, parallelCount, importInventory, itemInputs, itemOrder,
                orderPos, nextSlot + 1, rem - maxTake, slotSnapshots, availItems, itemMatrix,
                tanks, fluidInputs, fluidOrder, tankSnapshots, availFluids, fluidMatrix, state);
        itemMatrix[k][nextSlot] -= maxTake;
        availItems[nextSlot] += maxTake;
        if (ok) return true;

        if (minTake < maxTake && state.probes < state.maxProbes) {
            availItems[nextSlot] -= minTake;
            itemMatrix[k][nextSlot] += minTake;
            ok = searchItemAllocations(recipe, parallelCount, importInventory, itemInputs, itemOrder,
                    orderPos, nextSlot + 1, rem - minTake, slotSnapshots, availItems, itemMatrix,
                    tanks, fluidInputs, fluidOrder, tankSnapshots, availFluids, fluidMatrix, state);
            itemMatrix[k][nextSlot] -= minTake;
            availItems[nextSlot] += minTake;
            return ok;
        }
        return false;
    }

    private boolean searchFluidAllocations(@NotNull Recipe recipe,
                                           int parallelCount,
                                           @NotNull IItemHandlerModifiable importInventory,
                                           int mItems,
                                           @NotNull ItemStack[] slotSnapshots,
                                           @NotNull long[][] itemMatrix,
                                           @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                           @NotNull List<GTRecipeInput> fluidInputs,
                                           @NotNull int[] fluidOrder,
                                           int orderPos,
                                           int tankIdx,
                                           long remaining,
                                           @NotNull FluidStack[] tankSnapshots,
                                           @NotNull int[] availFluids,
                                           @NotNull long[][] fluidMatrix,
                                           @NotNull PlanSearchState state) {
        if (state.probes >= state.maxProbes) {
            state.hitProbeCap = true;
            return false;
        }
        if (orderPos >= fluidOrder.length) {
            int[] iPlan = sumMatrixColumns(itemMatrix, mItems, slotSnapshots.length);
            int[] fPlan = sumMatrixColumns(fluidMatrix, fluidInputs.size(), tankSnapshots.length);
            if (!canSimulateItemPlan(importInventory, iPlan, slotSnapshots) ||
                    !canSimulateFluidPlan(tanks, fPlan, tankSnapshots)) {
                return false;
            }
            state.leavesVisited++;
            if (state.leavesVisited <= state.skipLeaves) {
                return false;
            }
            state.probes++;
            if (invokeHolderCheckWithOverlay(recipe, false, importInventory, iPlan, slotSnapshots,
                    tanks, fPlan, tankSnapshots)) {
                state.accepted = new JointPlan(
                        copyMatrix(itemMatrix), iPlan, copyMatrix(fluidMatrix), fPlan);
                return true;
            }
            if (state.probes >= state.maxProbes) {
                state.hitProbeCap = true;
            }
            return false;
        }

        int k = fluidOrder[orderPos];
        GTRecipeInput input = fluidInputs.get(k);
        long rem = remaining < 0L ? (long) input.getAmount() * parallelCount : remaining;
        if (rem <= 0L) {
            return searchFluidAllocations(recipe, parallelCount, importInventory, mItems, slotSnapshots, itemMatrix,
                    tanks, fluidInputs, fluidOrder, orderPos + 1, 0, -1L, tankSnapshots, availFluids, fluidMatrix,
                    state);
        }

        int nextTank = -1;
        for (int t = tankIdx; t < tankSnapshots.length; t++) {
            if (availFluids[t] > 0 && tankSnapshots[t] != null && input.acceptsFluid(tankSnapshots[t])) {
                nextTank = t;
                break;
            }
        }
        if (nextTank < 0) return false;

        long suffixAfter = 0L;
        for (int j = nextTank + 1; j < tankSnapshots.length; j++) {
            if (availFluids[j] > 0 && tankSnapshots[j] != null && input.acceptsFluid(tankSnapshots[j])) {
                suffixAfter += availFluids[j];
            }
        }
        if (availFluids[nextTank] + suffixAfter < rem) {
            return false;
        }

        int maxTake = (int) Math.min(rem, availFluids[nextTank]);
        int minTake = (int) Math.max(0L, rem - suffixAfter);

        availFluids[nextTank] -= maxTake;
        fluidMatrix[k][nextTank] += maxTake;
        boolean ok = searchFluidAllocations(recipe, parallelCount, importInventory, mItems, slotSnapshots, itemMatrix,
                tanks, fluidInputs, fluidOrder, orderPos, nextTank + 1, rem - maxTake, tankSnapshots, availFluids,
                fluidMatrix, state);
        fluidMatrix[k][nextTank] -= maxTake;
        availFluids[nextTank] += maxTake;
        if (ok) return true;

        if (minTake < maxTake && state.probes < state.maxProbes) {
            availFluids[nextTank] -= minTake;
            fluidMatrix[k][nextTank] += minTake;
            ok = searchFluidAllocations(recipe, parallelCount, importInventory, mItems, slotSnapshots, itemMatrix,
                    tanks, fluidInputs, fluidOrder, orderPos, nextTank + 1, rem - minTake, tankSnapshots, availFluids,
                    fluidMatrix, state);
            fluidMatrix[k][nextTank] -= minTake;
            availFluids[nextTank] += minTake;
            return ok;
        }
        return false;
    }

    private static int[] sortedItemInputOrder(@NotNull List<GTRecipeInput> inputs,
                                              @NotNull ItemStack[] snapshots,
                                              @NotNull int[] available) {
        int m = inputs.size();
        List<Integer> order = new ArrayList<>(m);
        for (int k = 0; k < m; k++) order.add(k);
        if (m > 1) {
            order.sort(Comparator
                    .comparingInt((Integer k) -> countMatchingItemSlots(inputs.get(k), snapshots, available))
                    .thenComparingLong(k -> sumMatchingItemCount(inputs.get(k), snapshots, available))
                    .thenComparingInt(k -> k));
        }
        int[] out = new int[m];
        for (int i = 0; i < m; i++) out[i] = order.get(i);
        return out;
    }

    private static int[] sortedFluidInputOrder(@NotNull List<GTRecipeInput> inputs,
                                               @NotNull FluidStack[] snapshots,
                                               @NotNull int[] available) {
        int m = inputs.size();
        List<Integer> order = new ArrayList<>(m);
        for (int k = 0; k < m; k++) order.add(k);
        if (m > 1) {
            order.sort(Comparator
                    .comparingInt((Integer k) -> countMatchingFluidTanks(inputs.get(k), snapshots, available))
                    .thenComparingLong(k -> sumMatchingFluidAmount(inputs.get(k), snapshots, available))
                    .thenComparingInt(k -> k));
        }
        int[] out = new int[m];
        for (int i = 0; i < m; i++) out[i] = order.get(i);
        return out;
    }

    private static boolean tryGreedyItems(@NotNull List<GTRecipeInput> inputs,
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
                if (available[i] <= 0 || snapshots[i].isEmpty() || !input.acceptsStack(snapshots[i])) continue;
                int toTake = (int) Math.min(remaining, available[i]);
                available[i] -= toTake;
                outMatrix[k][i] += toTake;
                remaining -= toTake;
            }
            if (remaining > 0L) return false;
        }
        return true;
    }

    private static boolean tryGreedyFluids(@NotNull List<GTRecipeInput> inputs,
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
                if (available[t] <= 0 || snapshots[t] == null || !input.acceptsFluid(snapshots[t])) continue;
                int toDrain = (int) Math.min(remaining, available[t]);
                available[t] -= toDrain;
                outMatrix[k][t] += toDrain;
                remaining -= toDrain;
            }
            if (remaining > 0L) return false;
        }
        return true;
    }

    private static int[] sumMatrixColumns(@NotNull long[][] matrix, int rows, int cols) {
        int[] sum = new int[cols];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                sum[c] = saturatingIntAdd(sum[c], (int) Math.min(Integer.MAX_VALUE, matrix[r][c]));
            }
        }
        return sum;
    }

    private static long[][] copyMatrix(@NotNull long[][] src) {
        long[][] copy = new long[src.length][];
        for (int r = 0; r < src.length; r++) {
            copy[r] = src[r].clone();
        }
        return copy;
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

    // ==================== Stage 2: Overlay Reservation & Deferred Verification ====================

    private boolean invokeHolderCheckWithOverlay(@NotNull Recipe recipe,
                                                 boolean consumeIfSuccess,
                                                 @NotNull IItemHandlerModifiable importInventory,
                                                 @NotNull int[] reservedItemCounts,
                                                 @NotNull ItemStack[] slotSnapshots,
                                                 @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                                 @NotNull int[] reservedFluidAmounts,
                                                 @NotNull FluidStack[] tankSnapshots) {
        IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> reservedItems = null;
        for (int i = 0; i < reservedItemCounts.length; i++) {
            if (reservedItemCounts[i] <= 0 || slotSnapshots[i] == null || slotSnapshots[i].isEmpty()) continue;
            Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, i);
            if (reservedItems == null) {
                reservedItems = new IdentityHashMap<>();
            }
            Map<Integer, ItemStack> slotMap = reservedItems.computeIfAbsent(leaf.getFirst(), k -> new HashMap<>());
            ItemStack prev = slotMap.get(leaf.getSecond());
            int newReserved = prev == null ? reservedItemCounts[i] :
                    saturatingIntAdd(prev.getCount(), reservedItemCounts[i]);
            slotMap.put(leaf.getSecond(), ItemHandlerHelper.copyStackWithSize(slotSnapshots[i], newReserved));
        }

        IdentityHashMap<IFluidTank, FluidStack> reservedFluids = null;
        for (int t = 0; t < reservedFluidAmounts.length; t++) {
            if (reservedFluidAmounts[t] <= 0 || tankSnapshots[t] == null || tankSnapshots[t].amount <= 0) continue;
            IFluidTank leaf = unwrapFluidTank(tanks.get(t));
            if (reservedFluids == null) {
                reservedFluids = new IdentityHashMap<>();
            }
            FluidStack prev = reservedFluids.get(leaf);
            int newReserved = prev == null ? reservedFluidAmounts[t] :
                    saturatingIntAdd(prev.amount, reservedFluidAmounts[t]);
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
            MetaTileEntity mte = logic.getMetaTileEntity();
            if (mte instanceof RecipeMapMultiblockController controller) {
                return controller.checkRecipeWithReservedInputs(recipe, consumeIfSuccess, itemMask, fluidMask);
            }
            return ((IRecipeMapHolder) mte).checkRecipe(recipe, consumeIfSuccess);
        } finally {
            this.activeHolderItemMask = prevItemMask;
            this.activeHolderFluidMask = prevFluidMask;
        }
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
        int[] order = sortedItemInputOrder(inputs, snapshots, extractable);
        long[][] matrix = new long[m][slotCount];
        int[] available = extractable.clone();
        for (int k : order) {
            DeferredIngredientAmount deferred = deferredInputs.get(k);
            long remaining = deferred.amount;
            for (int i = 0; i < slotCount && remaining > 0L; i++) {
                if (available[i] <= 0 || snapshots[i].isEmpty() || !deferred.input.acceptsStack(snapshots[i])) continue;
                int toTake = (int) Math.min(remaining, available[i]);
                available[i] -= toTake;
                matrix[k][i] += toTake;
                remaining -= toTake;
            }
            if (remaining > 0L) return null;
        }
        int[] plan = sumMatrixColumns(matrix, m, slotCount);
        return canSimulateItemPlan(importInventory, plan, snapshots) ? new ReplannedItems(plan, snapshots) : null;
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
        int[] order = sortedFluidInputOrder(inputs, snapshots, drainable);
        long[][] matrix = new long[m][tankCount];
        int[] available = drainable.clone();
        for (int k : order) {
            DeferredIngredientAmount deferred = deferredInputs.get(k);
            long remaining = deferred.amount;
            for (int t = 0; t < tankCount && remaining > 0L; t++) {
                if (available[t] <= 0 || snapshots[t] == null || !deferred.input.acceptsFluid(snapshots[t])) continue;
                int toDrain = (int) Math.min(remaining, available[t]);
                available[t] -= toDrain;
                matrix[k][t] += toDrain;
                remaining -= toDrain;
            }
            if (remaining > 0L) return null;
        }
        int[] plan = sumMatrixColumns(matrix, m, tankCount);
        return canSimulateFluidPlan(tanks, plan, snapshots) ? new ReplannedFluids(plan, snapshots) : null;
    }

    // ==================== Stage 3: Full-Snapshot Diff Reconciliation & Rollback ====================

    private static final class MissingItemRecord {

        final int viewSlot;
        final IItemHandler leafHandler;
        final int leafSlot;
        final ItemStack originalStack;
        final int missingCount;

        MissingItemRecord(int viewSlot, IItemHandler leafHandler, int leafSlot,
                          ItemStack originalStack, int missingCount) {
            this.viewSlot = viewSlot;
            this.leafHandler = leafHandler;
            this.leafSlot = leafSlot;
            this.originalStack = originalStack;
            this.missingCount = missingCount;
        }
    }

    private static final class MissingFluidRecord {

        final IMultipleTankHandler.ITankEntry entry;
        final IFluidTank leafTank;
        final FluidStack originalFluid;
        final int missingAmount;

        MissingFluidRecord(IMultipleTankHandler.ITankEntry entry, IFluidTank leafTank,
                           FluidStack originalFluid, int missingAmount) {
            this.entry = entry;
            this.leafTank = leafTank;
            this.originalFluid = originalFluid;
            this.missingAmount = missingAmount;
        }
    }

    private static final class UniqueLeafItemSlot {

        final int viewSlot;
        final IItemHandler leafHandler;
        final int leafSlot;
        final int channelColor;
        final ItemStack before;
        final ItemStack after;

        UniqueLeafItemSlot(int viewSlot, IItemHandler leafHandler, int leafSlot, int channelColor,
                           ItemStack before, ItemStack after) {
            this.viewSlot = viewSlot;
            this.leafHandler = leafHandler;
            this.leafSlot = leafSlot;
            this.channelColor = channelColor;
            this.before = before;
            this.after = after;
        }
    }

    private static final class NetItemBucket {

        final int channelColor;
        final ItemStack template;
        long totalBefore;
        long totalAfter;
        long remainingToRestore;

        NetItemBucket(int channelColor, ItemStack template) {
            this.channelColor = channelColor;
            this.template = template;
        }
    }

    private static final class UniqueLeafFluidTank {

        final IMultipleTankHandler.ITankEntry entry;
        final IFluidTank leafTank;
        final int channelColor;
        @Nullable
        final FluidStack before;
        @Nullable
        final FluidStack after;

        UniqueLeafFluidTank(IMultipleTankHandler.ITankEntry entry, IFluidTank leafTank, int channelColor,
                            @Nullable FluidStack before, @Nullable FluidStack after) {
            this.entry = entry;
            this.leafTank = leafTank;
            this.channelColor = channelColor;
            this.before = before;
            this.after = after;
        }
    }

    private static final class NetFluidBucket {

        final int channelColor;
        final FluidStack template;
        long totalBefore;
        long totalAfter;
        long remainingToRestore;

        NetFluidBucket(int channelColor, FluidStack template) {
            this.channelColor = channelColor;
            this.template = template;
        }
    }

    private void reconcileAndRollbackAll(@NotNull IItemHandlerModifiable importInventory,
                                         @NotNull ItemStack[] fullOriginalSlots,
                                         @NotNull IMultipleTankHandler importFluids,
                                         @NotNull List<IMultipleTankHandler.ITankEntry> tanks,
                                         @NotNull FluidStack[] fullOriginalTanks,
                                         int commitColor) {
        // Pass 1a: Collect all unique leaf item slots and compute net decrease per (channelColor, Item+NBT)
        // so items merely moved to another slot within the same channel are never over-refunded.
        List<UniqueLeafItemSlot> uniqueItemSlots = new ArrayList<>(fullOriginalSlots.length);
        IdentityHashMap<IItemHandler, Map<Integer, Boolean>> seenItemLeaves = new IdentityHashMap<>();
        for (int i = 0; i < fullOriginalSlots.length; i++) {
            Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, i);
            Map<Integer, Boolean> seenSlots = seenItemLeaves.computeIfAbsent(leaf.getFirst(), k -> new HashMap<>());
            if (seenSlots.putIfAbsent(leaf.getSecond(), Boolean.TRUE) != null) {
                continue;
            }
            ItemStack orig = fullOriginalSlots[i] == null ? ItemStack.EMPTY : fullOriginalSlots[i];
            ItemStack now = importInventory.getStackInSlot(i);
            uniqueItemSlots.add(new UniqueLeafItemSlot(
                    i, leaf.getFirst(), leaf.getSecond(), logic.colorIndexOf(leaf.getFirst()),
                    orig, now.isEmpty() ? ItemStack.EMPTY : now.copy()));
        }

        List<NetItemBucket> itemBuckets = new ArrayList<>();
        for (UniqueLeafItemSlot u : uniqueItemSlots) {
            if (!u.before.isEmpty()) {
                NetItemBucket b = findOrCreateItemBucket(itemBuckets, u.channelColor, u.before);
                b.totalBefore += u.before.getCount();
            }
            if (!u.after.isEmpty()) {
                NetItemBucket b = findOrCreateItemBucket(itemBuckets, u.channelColor, u.after);
                b.totalAfter += u.after.getCount();
            }
        }
        for (NetItemBucket b : itemBuckets) {
            b.remainingToRestore = Math.max(0L, b.totalBefore - b.totalAfter);
        }

        List<MissingItemRecord> missingItems = new ArrayList<>();
        for (UniqueLeafItemSlot u : uniqueItemSlots) {
            if (u.before.isEmpty()) continue;
            NetItemBucket bucket = findOrCreateItemBucket(itemBuckets, u.channelColor, u.before);
            if (bucket.remainingToRestore <= 0L) continue;
            int nowMatching = (!u.after.isEmpty() && ItemHandlerHelper.canItemStacksStack(u.after, u.before)) ?
                    u.after.getCount() : 0;
            int slotMissing = Math.max(0, u.before.getCount() - nowMatching);
            int cappedMissing = (int) Math.min(slotMissing, bucket.remainingToRestore);
            if (cappedMissing > 0) {
                bucket.remainingToRestore -= cappedMissing;
                missingItems.add(new MissingItemRecord(
                        u.viewSlot, u.leafHandler, u.leafSlot, u.before, cappedMissing));
            }
        }

        // Pass 1b: Collect all unique leaf fluid tanks and compute net decrease per (channelColor, Fluid+NBT)
        List<UniqueLeafFluidTank> uniqueFluidTanks = new ArrayList<>(fullOriginalTanks.length);
        IdentityHashMap<IFluidTank, Boolean> seenFluidLeaves = new IdentityHashMap<>();
        for (int t = 0; t < fullOriginalTanks.length; t++) {
            IMultipleTankHandler.ITankEntry entry = tanks.get(t);
            IFluidTank leaf = unwrapFluidTank(entry);
            if (seenFluidLeaves.putIfAbsent(leaf, Boolean.TRUE) != null) {
                continue;
            }
            int color = leaf instanceof PendingFluidRollback p ? p.sourceColor : logic.colorIndexOf(leaf);
            FluidStack orig = fullOriginalTanks[t];
            FluidStack now = entry.getFluid();
            uniqueFluidTanks.add(new UniqueLeafFluidTank(
                    entry, leaf, color, orig, (now == null || now.amount <= 0) ? null : now.copy()));
        }

        List<NetFluidBucket> fluidBuckets = new ArrayList<>();
        for (UniqueLeafFluidTank v : uniqueFluidTanks) {
            if (v.before != null && v.before.amount > 0) {
                NetFluidBucket b = findOrCreateFluidBucket(fluidBuckets, v.channelColor, v.before);
                b.totalBefore += v.before.amount;
            }
            if (v.after != null && v.after.amount > 0) {
                NetFluidBucket b = findOrCreateFluidBucket(fluidBuckets, v.channelColor, v.after);
                b.totalAfter += v.after.amount;
            }
        }
        for (NetFluidBucket b : fluidBuckets) {
            b.remainingToRestore = Math.max(0L, b.totalBefore - b.totalAfter);
        }

        List<MissingFluidRecord> missingFluids = new ArrayList<>();
        for (UniqueLeafFluidTank v : uniqueFluidTanks) {
            if (v.before == null || v.before.amount <= 0) continue;
            NetFluidBucket bucket = findOrCreateFluidBucket(fluidBuckets, v.channelColor, v.before);
            if (bucket.remainingToRestore <= 0L) continue;
            int nowMatching = (v.after != null && v.after.isFluidEqual(v.before)) ? Math.max(0, v.after.amount) : 0;
            int tankMissing = Math.max(0, v.before.amount - nowMatching);
            int cappedMissing = (int) Math.min(tankMissing, bucket.remainingToRestore);
            if (cappedMissing > 0) {
                bucket.remainingToRestore -= cappedMissing;
                missingFluids.add(new MissingFluidRecord(v.entry, v.leafTank, v.before, cappedMissing));
            }
        }

        // Pass 2: Restore missing items and fluids
        for (MissingItemRecord rec : missingItems) {
            ItemStack remainder = importInventory.insertItem(
                    rec.viewSlot, ItemHandlerHelper.copyStackWithSize(rec.originalStack, rec.missingCount), false);
            if (!remainder.isEmpty() && rec.leafHandler instanceof IItemHandlerModifiable) {
                try {
                    ItemStack current = importInventory.getStackInSlot(rec.viewSlot);
                    if (current.isEmpty()) {
                        int toSet = Math.min(rec.originalStack.getCount(), remainder.getCount());
                        importInventory.setStackInSlot(rec.viewSlot,
                                ItemHandlerHelper.copyStackWithSize(remainder, toSet));
                        ItemStack afterSet = importInventory.getStackInSlot(rec.viewSlot);
                        int restored = afterSet.isEmpty() ? 0 : Math.min(remainder.getCount(), afterSet.getCount());
                        remainder = restored >= remainder.getCount() ? ItemStack.EMPTY :
                                ItemHandlerHelper.copyStackWithSize(remainder, remainder.getCount() - restored);
                    } else if (ItemHandlerHelper.canItemStacksStack(current, remainder) &&
                            current.getCount() < rec.originalStack.getCount()) {
                        int beforeCount = current.getCount();
                        ItemStack merged = current.copy();
                        merged.setCount(Math.min(rec.originalStack.getCount(), beforeCount + remainder.getCount()));
                        importInventory.setStackInSlot(rec.viewSlot, merged);
                        ItemStack afterSet = importInventory.getStackInSlot(rec.viewSlot);
                        int restored = afterSet.isEmpty() ? 0 :
                                Math.max(0, Math.min(remainder.getCount(), afterSet.getCount() - beforeCount));
                        remainder = restored >= remainder.getCount() ? ItemStack.EMPTY :
                                ItemHandlerHelper.copyStackWithSize(remainder, remainder.getCount() - restored);
                    }
                } catch (RuntimeException ignored) {}
            }
            if (!remainder.isEmpty()) {
                remainder = insertSameColorOtherSlot(importInventory, rec.leafHandler, remainder);
            }
            if (!remainder.isEmpty()) {
                enqueuePendingItemRollback(rec.leafHandler, rec.leafSlot, remainder, commitColor);
            }
        }

        for (MissingFluidRecord rec : missingFluids) {
            if (rec.leafTank instanceof PendingFluidRollback existingPending) {
                existingPending.addAmount(rec.missingAmount, rec.originalFluid);
                if (!pendingRollbackFluids.contains(existingPending)) {
                    pendingRollbackFluids.add(existingPending);
                }
                continue;
            }
            int filled = rec.entry.fill(GTUtility.copy(rec.missingAmount, rec.originalFluid), true);
            int unreturned = rec.missingAmount - Math.max(0, filled);
            if (unreturned > 0) {
                int filledElsewhere = fillSameColorOtherTank(
                        importFluids, rec.leafTank, GTUtility.copy(unreturned, rec.originalFluid));
                unreturned -= Math.max(0, filledElsewhere);
            }
            if (unreturned > 0) {
                enqueuePendingFluidRollback(
                        rec.leafTank, GTUtility.copy(unreturned, rec.originalFluid), commitColor);
            }
        }
    }

    private static NetItemBucket findOrCreateItemBucket(@NotNull List<NetItemBucket> buckets,
                                                        int channelColor,
                                                        @NotNull ItemStack stack) {
        for (NetItemBucket b : buckets) {
            if (b.channelColor == channelColor && ItemHandlerHelper.canItemStacksStack(b.template, stack)) {
                return b;
            }
        }
        NetItemBucket created = new NetItemBucket(channelColor, stack.copy());
        buckets.add(created);
        return created;
    }

    private static NetFluidBucket findOrCreateFluidBucket(@NotNull List<NetFluidBucket> buckets,
                                                          int channelColor,
                                                          @NotNull FluidStack fluid) {
        for (NetFluidBucket b : buckets) {
            if (b.channelColor == channelColor && b.template.isFluidEqual(fluid)) {
                return b;
            }
        }
        NetFluidBucket created = new NetFluidBucket(channelColor, fluid.copy());
        buckets.add(created);
        return created;
    }

    private ItemStack insertSameColorOtherSlot(@NotNull IItemHandlerModifiable importInventory,
                                               @NotNull IItemHandler originLeaf,
                                               @NotNull ItemStack remainder) {
        int originColor = logic.colorIndexOf(originLeaf);
        ItemStack rem = remainder;
        for (int s = 0; s < importInventory.getSlots() && !rem.isEmpty(); s++) {
            Tuple<IItemHandler, Integer> candidate = resolveLeafItemSlot(importInventory, s);
            if (logic.colorIndexOf(candidate.getFirst()) != originColor) continue;
            rem = importInventory.insertItem(s, rem, false);
        }
        return rem;
    }

    private int fillSameColorOtherTank(@NotNull IMultipleTankHandler importFluids,
                                       @NotNull IFluidTank originLeaf,
                                       @NotNull FluidStack resource) {
        int originColor = logic.colorIndexOf(originLeaf);
        int remaining = resource.amount;
        for (IMultipleTankHandler.ITankEntry entry : importFluids.getFluidTanks()) {
            IFluidTank candidate = unwrapFluidTank(entry);
            if (candidate == originLeaf || candidate instanceof PendingFluidRollback) continue;
            if (logic.colorIndexOf(candidate) != originColor) continue;
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

    private void returnUnexpectedExtractedItem(@NotNull IItemHandlerModifiable importInventory,
                                               int slot,
                                               @NotNull ItemStack unexpected,
                                               int commitColor) {
        if (unexpected.isEmpty()) return;
        Tuple<IItemHandler, Integer> leaf = resolveLeafItemSlot(importInventory, slot);
        ItemStack rem = importInventory.insertItem(slot, unexpected.copy(), false);
        if (!rem.isEmpty()) {
            rem = insertSameColorOtherSlot(importInventory, leaf.getFirst(), rem);
        }
        if (!rem.isEmpty()) {
            enqueuePendingItemRollback(leaf.getFirst(), leaf.getSecond(), rem, commitColor);
        }
    }

    private void returnUnexpectedDrainedFluid(@NotNull IMultipleTankHandler importFluids,
                                              @NotNull IMultipleTankHandler.ITankEntry tank,
                                              @Nullable FluidStack unexpected,
                                              int commitColor) {
        if (unexpected == null || unexpected.amount <= 0) return;
        IFluidTank leaf = unwrapFluidTank(tank);
        int filled = tank.fill(unexpected.copy(), true);
        int rem = unexpected.amount - Math.max(0, filled);
        if (rem > 0) {
            rem -= Math.max(0, fillSameColorOtherTank(importFluids, leaf, GTUtility.copy(rem, unexpected)));
        }
        if (rem > 0) {
            enqueuePendingFluidRollback(leaf, GTUtility.copy(rem, unexpected), commitColor);
        }
    }

    private void restorePreCommitInputNotifications(@NotNull List<IItemHandlerModifiable> preItemNotifs,
                                                    @NotNull List<IFluidHandler> preFluidNotifs) {
        List<IItemHandlerModifiable> itemNotifs = logic.getMetaTileEntity().getNotifiedItemInputList();
        itemNotifs.clear();
        itemNotifs.addAll(preItemNotifs);
        List<IFluidHandler> fluidNotifs = logic.getMetaTileEntity().getNotifiedFluidInputList();
        fluidNotifs.clear();
        fluidNotifs.addAll(preFluidNotifs);
    }

    // ==================== Pending Rollback Persistence & Unified Target Resolution ====================

    boolean flushPendingRollbacks() {
        if (pendingRollbackItems.isEmpty() && pendingRollbackFluids.isEmpty()) {
            return false;
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
        return restoredAny;
    }

    IMultipleTankHandler withPendingRollbackFluids(@NotNull IMultipleTankHandler base, int channelColor) {
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
                            channelColor == MultiblockRecipeLogic.UNTRACKED_CHANNEL);
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

    void writeToNBT(@NotNull NBTTagCompound compound) {
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
    }

    void readFromNBT(@NotNull NBTTagCompound compound) {
        pendingRollbackItems.clear();
        pendingRollbackFluids.clear();
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
    }

    private void enqueuePendingItemRollback(@NotNull IItemHandler leafHandler,
                                            int leafSlot,
                                            @NotNull ItemStack remainder,
                                            int commitColor) {
        if (remainder.isEmpty()) return;
        for (PendingItemRollback existing : pendingRollbackItems) {
            IItemHandler existingTarget = resolvePendingItemTarget(existing);
            if (existingTarget == leafHandler && existing.targetSlot == leafSlot &&
                    ItemHandlerHelper.canItemStacksStack(existing.stack, remainder)) {
                existing.stack.grow(remainder.getCount());
                return;
            }
        }
        MetaTileEntity owner = logic.ownerOf(leafHandler);
        int detectedColor = logic.colorIndexOf(leafHandler);
        byte kind = ROLLBACK_SOURCE_RAW;
        int busIdx = -1;
        int withinColorIdx = -1;
        MetaTileEntity mte = logic.getMetaTileEntity();
        if (mte instanceof MultiblockControllerBase controller) {
            List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            for (int b = 0; b < buses.size(); b++) {
                IItemHandlerModifiable bus = buses.get(b);
                IItemHandler unwrappedBus = bus instanceof DualHandler dual ? dual.getItemDelegate() : bus;
                if (bus == leafHandler || unwrappedBus == leafHandler) {
                    kind = ROLLBACK_SOURCE_ABILITY;
                    busIdx = b;
                    detectedColor = logic.colorIndexOf(bus);
                    int ord = 0;
                    for (int prev = 0; prev < b; prev++) {
                        if (logic.colorIndexOf(buses.get(prev)) == detectedColor) {
                            ord++;
                        }
                    }
                    withinColorIdx = ord;
                    if (owner == null) owner = logic.ownerOf(bus);
                    break;
                }
            }
        }
        int effectiveColor = detectedColor >= 0 ? detectedColor :
                (owner == null && kind == ROLLBACK_SOURCE_RAW && commitColor >= 0 ?
                        commitColor : MultiblockRecipeLogic.NO_CHANNEL);
        String ownerId = owner != null && owner.metaTileEntityId != null ? owner.metaTileEntityId.toString() : "";
        BlockPos pos = owner != null ? owner.getPos() : null;
        pendingRollbackItems.add(new PendingItemRollback(
                leafHandler, kind, effectiveColor, busIdx, withinColorIdx, leafSlot,
                ownerId, pos != null, pos != null ? pos.toLong() : 0L, remainder.copy()));
    }

    private void enqueuePendingFluidRollback(@NotNull IFluidTank leafTank,
                                             @NotNull FluidStack unreturned,
                                             int commitColor) {
        if (unreturned.amount <= 0) return;
        for (PendingFluidRollback existing : pendingRollbackFluids) {
            IFluidTank existingTarget = resolvePendingFluidTarget(existing);
            if (existingTarget == leafTank && existing.fluid != null && existing.fluid.isFluidEqual(unreturned)) {
                existing.addAmount(unreturned.amount, unreturned);
                return;
            }
        }
        MetaTileEntity owner = logic.ownerOf(leafTank);
        int detectedColor = logic.colorIndexOf(leafTank);
        byte kind = ROLLBACK_SOURCE_RAW;
        int busIdx = -1;
        int tankIdx = 0;
        int withinColorIdx = -1;

        MetaTileEntity mte = logic.getMetaTileEntity();
        if (mte instanceof MultiblockControllerBase controller) {
            List<IFluidTank> flatAbilityTanks = flattenImportFluidAbilities(controller);
            int foundIdx = -1;
            for (int t = 0; t < flatAbilityTanks.size(); t++) {
                IFluidTank candidate = flatAbilityTanks.get(t);
                if (candidate == leafTank) {
                    foundIdx = t;
                    detectedColor = logic.colorIndexOf(candidate);
                    int ord = 0;
                    for (int prev = 0; prev < t; prev++) {
                        if (logic.colorIndexOf(flatAbilityTanks.get(prev)) == detectedColor) {
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
                            detectedColor = logic.colorIndexOf(bus);
                            int ord = 0;
                            for (int prev = 0; prev < b; prev++) {
                                IItemHandlerModifiable prevBus = buses.get(prev);
                                if (prevBus instanceof IMultipleTankHandler &&
                                        logic.colorIndexOf(prevBus) == detectedColor) {
                                    ord++;
                                }
                            }
                            withinColorIdx = ord;
                            if (owner == null) owner = logic.ownerOf(bus);
                            break;
                        }
                    }
                }
            }
        }

        if (kind == ROLLBACK_SOURCE_RAW) {
            List<IMultipleTankHandler.ITankEntry> rawEntries = logic.getRawInputTankInternal().getFluidTanks();
            int rawIdx = indexOfLeafTank(rawEntries, leafTank);
            if (rawIdx >= 0) {
                tankIdx = rawIdx;
            } else if (commitColor >= 0) {
                List<IMultipleTankHandler.ITankEntry> chEntries = logic.getRawInputTankForColorInternal(commitColor)
                        .getFluidTanks();
                int chIdx = indexOfLeafTank(chEntries, leafTank);
                if (chIdx >= 0) {
                    kind = ROLLBACK_SOURCE_CHANNEL_CUSTOM;
                    tankIdx = chIdx;
                    detectedColor = commitColor;
                }
            }
        }

        int effectiveColor = detectedColor >= 0 ? detectedColor :
                (owner == null && kind == ROLLBACK_SOURCE_CHANNEL_CUSTOM && commitColor >= 0 ?
                        commitColor : MultiblockRecipeLogic.NO_CHANNEL);
        String ownerId = owner != null && owner.metaTileEntityId != null ? owner.metaTileEntityId.toString() : "";
        BlockPos pos = owner != null ? owner.getPos() : null;
        pendingRollbackFluids.add(new PendingFluidRollback(
                leafTank, kind, effectiveColor, busIdx, tankIdx, withinColorIdx,
                ownerId, pos != null, pos != null ? pos.toLong() : 0L, unreturned.copy()));
    }

    /**
     * Resolves a pending item rollback entry using identical color &amp; coordinate rules whether in-memory or reloaded:
     * <ol>
     *   <li>Cached {@code targetHandlerRef} is accepted ONLY if it is still attached to this controller AND its current
     *       painting color still matches {@code pending.sourceColor}.</li>
     *   <li>Unique {@code BlockPos} match among same-color candidates takes precedence over ability list indices, so
     *       inserting a new same-color bus ahead of the original bus does not divert items.</li>
     *   <li>Falls back to index/ordinal matching strictly within {@code pending.sourceColor}.</li>
     * </ol>
     */
    @Nullable
    private IItemHandler resolvePendingItemTarget(@NotNull PendingItemRollback pending) {
        MetaTileEntity mte = logic.getMetaTileEntity();
        if (pending.targetHandlerRef != null) {
            IItemHandler live = pending.targetHandlerRef.get();
            if (live != null && isLiveItemTargetValid(mte, live, pending)) {
                return live;
            }
            pending.targetHandlerRef = null;
        }
        IItemHandler rebound = null;
        if (pending.sourceKind == ROLLBACK_SOURCE_ABILITY && mte instanceof MultiblockControllerBase controller) {
            List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            rebound = selectBestBusCandidate(buses, pending.sourceColor, pending.hasOwnerPos, pending.ownerPosLong,
                    pending.ownerMteId, pending.busIndex, pending.withinColorBusIndex, false);
            if (rebound instanceof DualHandler dual) {
                rebound = dual.getItemDelegate();
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_RAW && pending.sourceColor < 0) {
            IItemHandlerModifiable inv = ((IRecipeMapHolder) mte).getInputInventory();
            if (inv.getSlots() > 0 && logic.colorIndexOf(inv) < 0) {
                rebound = inv;
            }
        }
        if (rebound != null) {
            pending.targetHandlerRef = new WeakReference<>(rebound);
        }
        return rebound;
    }

    private boolean isLiveItemTargetValid(@NotNull MetaTileEntity mte,
                                          @NotNull IItemHandler live,
                                          @NotNull PendingItemRollback pending) {
        if (pending.sourceKind == ROLLBACK_SOURCE_ABILITY && mte instanceof MultiblockControllerBase controller) {
            for (IItemHandlerModifiable bus : controller.getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
                IItemHandler unwrapped = bus instanceof DualHandler dual ? dual.getItemDelegate() : bus;
                if (bus == live || unwrapped == live) {
                    return logic.colorIndexOf(bus) == pending.sourceColor;
                }
            }
            return false;
        }
        if (pending.sourceKind == ROLLBACK_SOURCE_RAW && pending.sourceColor < 0) {
            return logic.colorIndexOf(live) < 0;
        }
        return false;
    }

    /**
     * Resolves a pending fluid rollback entry using identical color &amp; coordinate rules whether live or reloaded:
     * <ol>
     *   <li>Cached {@code targetTankRef} is accepted ONLY if it is still attached to this controller AND its current
     *       painting color still matches {@code pending.sourceColor} (repainting a Red hatch Blue invalidates the
     *       cached Red target immediately, matching post-reload behavior).</li>
     *   <li>Unique {@code BlockPos} match among same-color candidates takes precedence over ability indices, so adding
     *       another same-model hatch ahead of the original hatch returns fluid to the original hatch's coordinates.</li>
     *   <li>Falls back to {@code ownerMteId} + index/ordinal, then same-color index/ordinal.</li>
     * </ol>
     */
    @Nullable
    private IFluidTank resolvePendingFluidTarget(@NotNull PendingFluidRollback pending) {
        MetaTileEntity mte = logic.getMetaTileEntity();
        if (pending.targetTankRef != null) {
            IFluidTank live = pending.targetTankRef.get();
            if (live != null && !(live instanceof PendingFluidRollback) && isLiveFluidTargetValid(mte, live, pending)) {
                return live;
            }
            pending.targetTankRef = null;
        }
        IFluidTank rebound = null;
        if (pending.sourceKind == ROLLBACK_SOURCE_ABILITY && mte instanceof MultiblockControllerBase controller) {
            List<IFluidTank> flat = flattenImportFluidAbilities(controller);
            rebound = selectBestTankCandidate(flat, pending);
        } else if (pending.sourceKind == ROLLBACK_SOURCE_DUAL_BUS && mte instanceof MultiblockControllerBase controller) {
            List<IItemHandlerModifiable> buses = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
            IItemHandlerModifiable matchedBus = selectBestBusCandidate(
                    buses, pending.sourceColor, pending.hasOwnerPos, pending.ownerPosLong,
                    pending.ownerMteId, pending.busIndex, pending.withinColorTankIndex, true);
            if (matchedBus instanceof IMultipleTankHandler dualHandler) {
                List<IMultipleTankHandler.ITankEntry> dualTanks = dualHandler.getFluidTanks();
                if (pending.tankIndex >= 0 && pending.tankIndex < dualTanks.size()) {
                    rebound = unwrapFluidTank(dualTanks.get(pending.tankIndex));
                }
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_CHANNEL_CUSTOM && pending.sourceColor >= 0) {
            List<IMultipleTankHandler.ITankEntry> entries = logic.getRawInputTankForColorInternal(pending.sourceColor)
                    .getFluidTanks();
            if (pending.tankIndex >= 0 && pending.tankIndex < entries.size()) {
                IFluidTank candidate = unwrapFluidTank(entries.get(pending.tankIndex));
                int cColor = logic.colorIndexOf(candidate);
                if (cColor < 0 || cColor == pending.sourceColor) {
                    rebound = candidate;
                }
            }
        } else if (pending.sourceKind == ROLLBACK_SOURCE_RAW && pending.sourceColor < 0) {
            List<IMultipleTankHandler.ITankEntry> entries = logic.getRawInputTankInternal().getFluidTanks();
            if (pending.tankIndex >= 0 && pending.tankIndex < entries.size()) {
                IFluidTank candidate = unwrapFluidTank(entries.get(pending.tankIndex));
                if (logic.colorIndexOf(candidate) < 0) {
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

    private boolean isLiveFluidTargetValid(@NotNull MetaTileEntity mte,
                                           @NotNull IFluidTank live,
                                           @NotNull PendingFluidRollback pending) {
        if (pending.sourceKind == ROLLBACK_SOURCE_ABILITY && mte instanceof MultiblockControllerBase controller) {
            List<IFluidTank> flat = flattenImportFluidAbilities(controller);
            return flat.contains(live) && logic.colorIndexOf(live) == pending.sourceColor;
        }
        if (pending.sourceKind == ROLLBACK_SOURCE_DUAL_BUS && mte instanceof MultiblockControllerBase controller) {
            for (IItemHandlerModifiable bus : controller.getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
                if (!(bus instanceof IMultipleTankHandler dualHandler)) continue;
                for (IMultipleTankHandler.ITankEntry entry : dualHandler.getFluidTanks()) {
                    if (unwrapFluidTank(entry) == live) {
                        return logic.colorIndexOf(bus) == pending.sourceColor;
                    }
                }
            }
            return false;
        }
        if (pending.sourceKind == ROLLBACK_SOURCE_CHANNEL_CUSTOM && pending.sourceColor >= 0) {
            int cColor = logic.colorIndexOf(live);
            return (cColor < 0 || cColor == pending.sourceColor) &&
                    containsLeafTank(logic.getRawInputTankForColorInternal(pending.sourceColor).getFluidTanks(), live);
        }
        if (pending.sourceKind == ROLLBACK_SOURCE_RAW && pending.sourceColor < 0) {
            return logic.colorIndexOf(live) < 0 &&
                    containsLeafTank(logic.getRawInputTankInternal().getFluidTanks(), live);
        }
        return false;
    }

    @Nullable
    private IFluidTank selectBestTankCandidate(@NotNull List<IFluidTank> flat,
                                               @NotNull PendingFluidRollback pending) {
        // 1. Unique BlockPos match within pending.sourceColor (immune to hatch insertion/reordering)
        if (pending.hasOwnerPos) {
            IFluidTank uniquePosWithId = null;
            int posWithIdCount = 0;
            IFluidTank uniquePosAnyId = null;
            int posAnyIdCount = 0;
            for (IFluidTank candidate : flat) {
                if (logic.colorIndexOf(candidate) != pending.sourceColor) continue;
                if (matchesPosOnly(candidate, pending.ownerPosLong)) {
                    uniquePosAnyId = candidate;
                    posAnyIdCount++;
                    if (matchesOwnerIdOnly(candidate, pending.ownerMteId)) {
                        uniquePosWithId = candidate;
                        posWithIdCount++;
                    }
                }
            }
            if (posWithIdCount == 1) {
                return uniquePosWithId;
            }
            if (posAnyIdCount == 1) {
                return uniquePosAnyId;
            }
        }
        // 2. Global index + sourceColor + ownerMteId
        if (pending.tankIndex >= 0 && pending.tankIndex < flat.size()) {
            IFluidTank atIdx = flat.get(pending.tankIndex);
            if (logic.colorIndexOf(atIdx) == pending.sourceColor &&
                    matchesOwnerIdOnly(atIdx, pending.ownerMteId)) {
                return atIdx;
            }
        }
        // 3. Within-color ordinal + ownerMteId
        int colorOrdinal = 0;
        for (IFluidTank candidate : flat) {
            if (logic.colorIndexOf(candidate) != pending.sourceColor) continue;
            if (colorOrdinal == pending.withinColorTankIndex &&
                    matchesOwnerIdOnly(candidate, pending.ownerMteId)) {
                return candidate;
            }
            colorOrdinal++;
        }
        // 4. Same-color index/ordinal fallback (e.g. upgraded hatch tier or fresh headless MTE ID)
        if (pending.tankIndex >= 0 && pending.tankIndex < flat.size() &&
                logic.colorIndexOf(flat.get(pending.tankIndex)) == pending.sourceColor) {
            return flat.get(pending.tankIndex);
        }
        colorOrdinal = 0;
        IFluidTank firstSameColor = null;
        for (IFluidTank candidate : flat) {
            if (logic.colorIndexOf(candidate) != pending.sourceColor) continue;
            if (firstSameColor == null) firstSameColor = candidate;
            if (colorOrdinal == pending.withinColorTankIndex) {
                return candidate;
            }
            colorOrdinal++;
        }
        return firstSameColor;
    }

    @Nullable
    private IItemHandlerModifiable selectBestBusCandidate(@NotNull List<IItemHandlerModifiable> buses,
                                                          int expectedColor,
                                                          boolean hasOwnerPos,
                                                          long ownerPosLong,
                                                          @NotNull String expectedOwnerId,
                                                          int busIndex,
                                                          int withinColorIndex,
                                                          boolean requireDualFluid) {
        if (hasOwnerPos) {
            IItemHandlerModifiable uniquePosWithId = null;
            int posWithIdCount = 0;
            IItemHandlerModifiable uniquePosAnyId = null;
            int posAnyIdCount = 0;
            for (IItemHandlerModifiable bus : buses) {
                if (requireDualFluid && !(bus instanceof IMultipleTankHandler)) continue;
                if (logic.colorIndexOf(bus) != expectedColor) continue;
                if (matchesPosOnly(bus, ownerPosLong)) {
                    uniquePosAnyId = bus;
                    posAnyIdCount++;
                    if (matchesOwnerIdOnly(bus, expectedOwnerId)) {
                        uniquePosWithId = bus;
                        posWithIdCount++;
                    }
                }
            }
            if (posWithIdCount == 1) return uniquePosWithId;
            if (posAnyIdCount == 1) return uniquePosAnyId;
        }
        if (busIndex >= 0 && busIndex < buses.size()) {
            IItemHandlerModifiable atIdx = buses.get(busIndex);
            if ((!requireDualFluid || atIdx instanceof IMultipleTankHandler) &&
                    logic.colorIndexOf(atIdx) == expectedColor &&
                    matchesOwnerIdOnly(atIdx, expectedOwnerId)) {
                return atIdx;
            }
        }
        int colorOrdinal = 0;
        for (IItemHandlerModifiable bus : buses) {
            if (requireDualFluid && !(bus instanceof IMultipleTankHandler)) continue;
            if (logic.colorIndexOf(bus) != expectedColor) continue;
            if (colorOrdinal == withinColorIndex && matchesOwnerIdOnly(bus, expectedOwnerId)) {
                return bus;
            }
            colorOrdinal++;
        }
        if (busIndex >= 0 && busIndex < buses.size()) {
            IItemHandlerModifiable atIdx = buses.get(busIndex);
            if ((!requireDualFluid || atIdx instanceof IMultipleTankHandler) &&
                    logic.colorIndexOf(atIdx) == expectedColor) {
                return atIdx;
            }
        }
        colorOrdinal = 0;
        IItemHandlerModifiable firstSameColor = null;
        for (IItemHandlerModifiable bus : buses) {
            if (requireDualFluid && !(bus instanceof IMultipleTankHandler)) continue;
            if (logic.colorIndexOf(bus) != expectedColor) continue;
            if (firstSameColor == null) firstSameColor = bus;
            if (colorOrdinal == withinColorIndex) {
                return bus;
            }
            colorOrdinal++;
        }
        return firstSameColor;
    }

    private boolean matchesPosOnly(@NotNull Object handler, long expectedPosLong) {
        MetaTileEntity owner = logic.ownerOf(handler);
        if (owner == null) return false;
        BlockPos pos = owner.getPos();
        return pos != null && pos.toLong() == expectedPosLong;
    }

    private boolean matchesOwnerIdOnly(@NotNull Object handler, @NotNull String expectedOwnerId) {
        if (expectedOwnerId.isEmpty()) return true;
        MetaTileEntity owner = logic.ownerOf(handler);
        if (owner == null || owner.metaTileEntityId == null) return false;
        return expectedOwnerId.equals(owner.metaTileEntityId.toString());
    }

    // ==================== Capability / Container Introspection & Overlay Views ====================

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

    private static boolean containsLeafTank(@NotNull List<IMultipleTankHandler.ITankEntry> entries,
                                            @NotNull IFluidTank target) {
        for (IMultipleTankHandler.ITankEntry entry : entries) {
            if (unwrapFluidTank(entry) == target) {
                return true;
            }
        }
        return false;
    }

    static IFluidTank unwrapFluidTank(@NotNull IFluidTank tank) {
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

    private static Tuple<IItemHandler, Integer> resolveLeafItemSlot(@NotNull IItemHandler handler, int slot) {
        IItemHandler current = handler;
        int currentSlot = slot;
        while (true) {
            if (current instanceof ReservedItemHandlerView reservedView) {
                current = reservedView.delegate;
                continue;
            }
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

    private static int saturatingIntAdd(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, sum);
    }

    // ==================== Internal Data Records & View Adapters ====================

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

    private static final class ReservedItemHandlerView implements IItemHandlerModifiable {

        final IItemHandlerModifiable delegate;
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
            int color = tag.hasKey("sourceColor") ? tag.getInteger("sourceColor") : MultiblockRecipeLogic.NO_CHANNEL;
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

    static final class PendingFluidRollback implements IFluidTank {

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
            int color = tag.hasKey("sourceColor") ? tag.getInteger("sourceColor") : MultiblockRecipeLogic.NO_CHANNEL;
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
}
