package gregtech.api.recipes.logic;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.IVoidable;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTTransferUtils;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Cross-Recipe Parallel Scheduler: manages dynamically created {@link RecipeSlot}s that can each run
 * a different recipe concurrently.
 *
 * <p>Slot management:
 * <ul>
 *   <li>Slots are created dynamically when a recipe is found, and destroyed when complete.</li>
 *   <li>Each slot represents one recipe type × N parallel copies (single-slot maximization).</li>
 *   <li>Total parallel budget: sum of all slots' parallelCount ≤ parallelLimit.</li>
 *   <li>Slot count = number of distinct recipes currently running (dynamic).</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><b>Shared Power Pool</b>: All active slots share the machine's total power budget.
 *       The sum of all slots' EUt must not exceed maxVoltage.</li>
 *   <li><b>Same Recipe First</b>: When filling, the scheduler first tries to maximize the first recipe's
 *       parallel count, using all remaining parallel budget. Then searches for different recipes.</li>
 *   <li><b>Immediate Refill</b>: When a slot completes and outputs are extracted, the scheduler
 *       immediately attempts to find a new recipe on the next tick.</li>
 *   <li><b>Independent Duration</b>: Each slot has its own progress timer, allowing recipes with
 *       different durations to coexist.</li>
 * </ul>
 *
 * <p>This scheduler is designed to be used as a component within an {@link gregtech.api.capability.impl.AbstractRecipeLogic}
 * subclass. The owning RecipeLogic delegates its tick/search/output logic to this scheduler when
 * {@link gregtech.api.metatileentity.multiblock.ParallelLogicType#CROSS_RECIPE} is active.
 *
 * @see RecipeSlot
 */
public class CrossRecipeParallelScheduler {

    // --- Configuration ---
    private int parallelLimit;
    private long maxVoltage;

    // --- Active execution slots (dynamically managed) ---
    @NotNull
    private List<RecipeSlot> activeSlots = new ArrayList<>();

    // --- Object pool for slot reuse (avoids GC pressure from frequent creation/destruction) ---
    @NotNull
    private List<RecipeSlot> slotPool = new ArrayList<>();

    // --- State tracking ---
    private boolean hasNotEnoughEnergy = false;

    public CrossRecipeParallelScheduler(int parallelLimit) {
        this.parallelLimit = parallelLimit;
    }

    // ==================== Configuration ====================

    /**
     * Sets the total parallel budget for this scheduler.
     * Sum of all active slots' parallelCount must not exceed this value.
     *
     * @param parallelLimit the maximum total parallel count across all slots
     */
    public void setParallelLimit(int parallelLimit) {
        this.parallelLimit = parallelLimit;
    }

    /**
     * Sets the maximum voltage (total power budget) for this scheduler.
     *
     * @param maxVoltage the maximum EU/t available
     */
    public void setMaxVoltage(long maxVoltage) {
        this.maxVoltage = maxVoltage;
    }

    // ==================== Slot Lifecycle ====================

    /**
     * Acquires an execution slot (from the pool or newly created) and adds it to the active list.
     * Called by the owning RecipeLogic when a recipe has been found and configured.
     *
     * @return the slot ready to be configured with a recipe
     */
    @NotNull
    public RecipeSlot acquireSlot() {
        RecipeSlot slot;
        if (!slotPool.isEmpty()) {
            slot = slotPool.remove(slotPool.size() - 1);
            slot.reset();
        } else {
            slot = new RecipeSlot(activeSlots.size());
        }
        activeSlots.add(slot);
        return slot;
    }

    /**
     * Releases a completed slot: removes it from active list and returns it to the pool.
     *
     * @param slot the slot to release
     */
    public void releaseSlot(@NotNull RecipeSlot slot) {
        activeSlots.remove(slot);
        returnSlotToPool(slot);
    }

    private void returnSlotToPool(@NotNull RecipeSlot slot) {
        slot.reset();
        slotPool.add(slot);
    }

    // ==================== Core Tick Logic ====================

    /**
     * Main tick method. Should be called once per server tick by the owning RecipeLogic.
     *
     * <p>This method:
     * <ol>
     *   <li>Draws energy for all running slots</li>
     *   <li>Advances progress on all running slots</li>
     *   <li>Outputs results from completed slots and removes them</li>
     * </ol>
     *
     * @param outputInventory the item output inventory
     * @param outputFluids    the fluid output tank handler
     * @param energyDrawer    a function that attempts to draw the specified amount of energy.
     *                        Returns true if the draw was successful.
     */
    public int tickSlots(@NotNull IItemHandlerModifiable outputInventory,
                         @NotNull IMultipleTankHandler outputFluids,
                         @NotNull EnergyDrawer energyDrawer) {
        long totalEUt = getTotalEnergyConsumption();
        int completedParallel = 0;

        // Attempt to draw energy for all active slots
        if (totalEUt > 0) {
            if (energyDrawer.drawEnergy(totalEUt, true)) {
                energyDrawer.drawEnergy(totalEUt, false);
                hasNotEnoughEnergy = false;
            } else {
                hasNotEnoughEnergy = true;
                return 0;
            }
        }

        // Tick all running slots and collect completed ones
        Iterator<RecipeSlot> it = activeSlots.iterator();
        while (it.hasNext()) {
            RecipeSlot slot = it.next();
            if (slot.isRunning()) {
                if (slot.tick()) {
                    // Slot just completed - output results and return to pool
                    completedParallel += Math.max(1, slot.getParallelCount());
                    outputSlotResults(slot, outputInventory, outputFluids);
                    it.remove();
                    returnSlotToPool(slot);
                }
            } else if (slot.isCompleted()) {
                // Shouldn't normally reach here, but handle gracefully
                completedParallel += Math.max(1, slot.getParallelCount());
                outputSlotResults(slot, outputInventory, outputFluids);
                it.remove();
                returnSlotToPool(slot);
            }
        }
        return completedParallel;
    }

    // ==================== Query Methods ====================

    /**
     * @return the total EU/t being consumed by all active slots
     */
    public long getTotalEnergyConsumption() {
        long total = 0;
        for (RecipeSlot slot : activeSlots) {
            if (slot.isRunning()) {
                total += slot.getRecipeEUt();
            }
        }
        return total;
    }

    /**
     * @return the remaining parallel budget available for new recipes
     */
    public int getRemainingParallelBudget() {
        return parallelLimit - getTotalParallelCount();
    }

    /**
     * @return the remaining power budget available for new recipes
     */
    public long getRemainingPowerBudget() {
        return maxVoltage - getTotalEnergyConsumption();
    }

    /**
     * @return true if at least one slot is running
     */
    public boolean hasActiveSlots() {
        return !activeSlots.isEmpty();
    }

    /**
     * @return true if there is remaining parallel budget for new recipes
     */
    public boolean canAcceptMoreRecipes() {
        return getRemainingParallelBudget() > 0 && getRemainingPowerBudget() > 0;
    }

    /**
     * @return the number of currently active (running) slots
     */
    public int getActiveSlotCount() {
        return activeSlots.size();
    }

    /**
     * @return the total number of parallel recipes being performed across all slots
     */
    public int getTotalParallelCount() {
        int total = 0;
        for (RecipeSlot slot : activeSlots) {
            total += slot.getParallelCount();
        }
        return total;
    }

    public boolean isHasNotEnoughEnergy() {
        return hasNotEnoughEnergy;
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public long getMaxVoltage() {
        return maxVoltage;
    }

    @NotNull
    public List<RecipeSlot> getActiveSlots() {
        return Collections.unmodifiableList(activeSlots);
    }

    @Nullable
    public RecipeSlot getDisplaySlot() {
        RecipeSlot displaySlot = null;
        int bestRemaining = Integer.MAX_VALUE;
        double bestProgress = 0.0;

        for (RecipeSlot slot : activeSlots) {
            if (!slot.isRunning()) continue;

            int remaining = Math.max(0, slot.getMaxProgressTime() - slot.getProgressTime());
            double progress = slot.getProgressPercent();
            if (displaySlot == null ||
                    remaining < bestRemaining ||
                    (remaining == bestRemaining && progress > bestProgress)) {
                displaySlot = slot;
                bestRemaining = remaining;
                bestProgress = progress;
            }
        }

        return displaySlot;
    }

    public int getDisplayProgressTime() {
        RecipeSlot slot = getDisplaySlot();
        return slot == null ? 0 : Math.min(slot.getProgressTime(), slot.getMaxProgressTime());
    }

    public int getDisplayMaxProgressTime() {
        RecipeSlot slot = getDisplaySlot();
        return slot == null ? 0 : slot.getMaxProgressTime();
    }

    public double getDisplayProgressPercent() {
        RecipeSlot slot = getDisplaySlot();
        return slot == null ? 0.0 : Math.min(1.0, Math.max(0.0, slot.getProgressPercent()));
    }

    // ==================== Internal Helpers ====================

    /**
     * Outputs the results of a completed slot to the machine's export inventories.
     */
    private void outputSlotResults(@NotNull RecipeSlot slot,
                                   @NotNull IItemHandlerModifiable outputInventory,
                                   @NotNull IMultipleTankHandler outputFluids) {
        GTTransferUtils.addItemsToItemHandler(outputInventory, false, slot.getItemOutputs());
        GTTransferUtils.addFluidsToFluidHandler(outputFluids, false, slot.getFluidOutputs());
    }

    // ==================== Reset / Invalidate ====================

    /**
     * Removes all active slots and returns them to the pool. Called when the multiblock is invalidated.
     */
    public void invalidateAll() {
        for (RecipeSlot slot : activeSlots) {
            slot.reset();
            slotPool.add(slot);
        }
        activeSlots.clear();
        hasNotEnoughEnergy = false;
    }

    // ==================== Serialization ====================

    @NotNull
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("parallelLimit", parallelLimit);
        tag.setLong("maxVoltage", maxVoltage);

        NBTTagList slotList = new NBTTagList();
        for (RecipeSlot slot : activeSlots) {
            slotList.appendTag(slot.serializeNBT());
        }
        tag.setTag("slots", slotList);

        return tag;
    }

    public void deserializeNBT(@NotNull NBTTagCompound tag) {
        this.parallelLimit = tag.getInteger("parallelLimit");
        this.maxVoltage = tag.getLong("maxVoltage");

        NBTTagList slotList = tag.getTagList("slots", Constants.NBT.TAG_COMPOUND);
        this.activeSlots = new ArrayList<>(slotList.tagCount());
        for (int i = 0; i < slotList.tagCount(); i++) {
            RecipeSlot slot = new RecipeSlot(i);
            slot.deserializeNBT(slotList.getCompoundTagAt(i));
            // Only restore running slots (completed ones would have been output on save)
            if (slot.isRunning()) {
                activeSlots.add(slot);
            }
        }
    }

    // ==================== Functional Interfaces ====================

    /**
     * Functional interface for energy drawing operations.
     * The owning RecipeLogic provides its concrete implementation.
     */
    @FunctionalInterface
    public interface EnergyDrawer {

        /**
         * Attempts to draw energy from the machine's energy container.
         *
         * @param amount   the amount of EU to draw
         * @param simulate if true, only simulates the draw without actually consuming energy
         * @return true if the draw was successful (or would be successful in simulation)
         */
        boolean drawEnergy(long amount, boolean simulate);
    }

    /**
     * @deprecated Use the direct slot creation via {@link #createSlot()} and setup in RecipeLogic instead.
     */
    @Deprecated
    @FunctionalInterface
    public interface SlotRecipeFinder {

        boolean findAndSetupRecipe(@NotNull RecipeSlot slot,
                                   @NotNull RecipeMap<?> recipeMap,
                                   @NotNull IItemHandlerModifiable importInventory,
                                   @NotNull IMultipleTankHandler importFluids,
                                   @NotNull IItemHandlerModifiable exportInventory,
                                   @NotNull IMultipleTankHandler exportFluids,
                                   long maxVoltage,
                                   @NotNull IVoidable voidable);
    }
}
