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
 * <p><b>Known limitation — Slot Fragmentation (Temporal Drift):</b>
 * <br>When the power budget cannot perfectly divide by a recipe's base EUt (i.e.,
 * {@code maxVoltage / baseEUt < parallelLimit}), the first slot will take slightly fewer
 * parallels than the full budget, leaving a small remainder. This remainder is filled by a
 * secondary slot with very few parallels (often just 1).
 *
 * <p>These two slots will typically have <b>different durations</b>: the large slot's total EUt
 * ({@code baseEUt × parallelCount}) is too high to overclock within the voltage budget, so it
 * keeps the original duration. The small slot's total EUt is much lower, allowing it to overclock
 * successfully, resulting in a shorter duration (e.g., half). This duration mismatch causes the
 * slots to complete at different times, triggering a cascading fragmentation effect:
 * <ol>
 *   <li>The small slot completes first (shorter duration). {@code refillScheduler} is called,
 *       but the large slot is still running, occupying most of the parallel and power budget.</li>
 *   <li>A new small slot is created with the remaining budget, again getting a different
 *       (shorter) duration due to overclocking.</li>
 *   <li>When the large slot eventually completes, the small slot(s) are still running, so
 *       the new large slot gets fewer parallels ({@code parallelLimit - runningSmallSlots}).</li>
 *   <li>Over many cycles, the primary slot's parallel count gradually decreases while the number
 *       of trailing small slots increases.</li>
 * </ol>
 * This fragmentation has <b>negligible performance impact</b> — each slot's per-tick cost is just
 * an integer increment and comparison. The total throughput remains correct; only the visual
 * representation in the UI shows multiple slots instead of one unified slot. The display layer
 * merges slots with the same recipe name and duration via {@link #getMergedDisplaySlots()} to
 * reduce visual clutter.
 *
 * @see RecipeSlot
 */
public class CrossRecipeParallelScheduler {

    // --- Configuration ---
    private int parallelLimit;
    // Overclock reference voltage (used for OC tier calculation, from getMaximumOverclockVoltage())
    private long maxVoltage;
    // Total power budget = sum of each energy hatch's (voltage × amperage), used for parallel/power limiting
    private long totalPowerBudget;

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
     * Sets the overclock reference voltage for this scheduler.
     * This is used as the ceiling for OC tier calculation (from getMaximumOverclockVoltage()),
     * NOT for power budget limiting.
     *
     * @param maxVoltage the overclock reference voltage
     */
    public void setMaxVoltage(long maxVoltage) {
        this.maxVoltage = maxVoltage;
    }

    /**
     * Sets the total power budget for this scheduler.
     * Calculated as the raw sum of each energy hatch's voltage × amperage.
     * This limits the total EU/t that all active slots can consume simultaneously.
     *
     * @param totalPowerBudget the total power budget in EU/t
     */
    public void setTotalPowerBudget(long totalPowerBudget) {
        this.totalPowerBudget = totalPowerBudget;
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
                    completedParallel += Math.max(1, slot.getTotalOperations());
                    outputSlotResults(slot, outputInventory, outputFluids);
                    it.remove();
                    returnSlotToPool(slot);
                }
            } else if (slot.isCompleted()) {
                // Shouldn't normally reach here, but handle gracefully
                completedParallel += Math.max(1, slot.getTotalOperations());
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
     * @return the remaining power budget available for new recipes (based on totalPowerBudget)
     */
    public long getRemainingPowerBudget() {
        return totalPowerBudget - getTotalEnergyConsumption();
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

    public long getTotalPowerBudget() {
        return totalPowerBudget;
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

    // ==================== Display Merging ====================

    /**
     * Immutable snapshot of merged slot data for display purposes.
     * Slots running the same recipe (same {@code recipeDisplayName}) with the same
     * {@code maxProgressTime} are merged into a single entry, with parallel counts summed
     * and progress taken from the slot closest to completion.
     *
     * <p>This avoids the visual fragmentation caused by temporal drift
     * (see class Javadoc "Slot Fragmentation" section).
     */
    public static class MergedSlotDisplay {

        public final int slotIndex;
        @NotNull
        public final String recipeName;
        public final int totalParallelCount;
        public final int totalOperations;
        public final int progress;
        public final int maxProgress;
        public final long totalEUt;

        public MergedSlotDisplay(int slotIndex, @NotNull String recipeName, int totalParallelCount,
                                 int totalOperations, int progress, int maxProgress, long totalEUt) {
            this.slotIndex = slotIndex;
            this.recipeName = recipeName;
            this.totalParallelCount = totalParallelCount;
            this.totalOperations = totalOperations;
            this.progress = progress;
            this.maxProgress = maxProgress;
            this.totalEUt = totalEUt;
        }
    }

    /**
     * Returns a merged view of active slots for display purposes.
     * Slots with the same {@code recipeDisplayName} and {@code maxProgressTime} are combined:
     * <ul>
     *   <li>Parallel counts are summed.</li>
     *   <li>EUt values are summed.</li>
     *   <li>Progress is taken from the slot closest to completion (highest progressTime).</li>
     *   <li>Slot index is taken from the first slot in the group.</li>
     * </ul>
     *
     * @return a list of merged display entries, one per unique (recipeName, maxProgressTime) pair
     */
    @NotNull
    public List<MergedSlotDisplay> getMergedDisplaySlots() {
        if (activeSlots.isEmpty()) return Collections.emptyList();

        List<MergedSlotDisplay> result = new ArrayList<>();

        for (RecipeSlot slot : activeSlots) {
            if (!slot.isRunning()) continue;

            String name = slot.getRecipeDisplayName();
            int maxProg = slot.getMaxProgressTime();
            boolean merged = false;

            for (int i = 0; i < result.size(); i++) {
                MergedSlotDisplay existing = result.get(i);
                if (existing.recipeName.equals(name) && existing.maxProgress == maxProg) {
                    result.set(i, new MergedSlotDisplay(
                            existing.slotIndex,
                            existing.recipeName,
                            existing.totalParallelCount + slot.getParallelCount(),
                            existing.totalOperations + slot.getTotalOperations(),
                            Math.max(existing.progress, slot.getProgressTime()),
                            existing.maxProgress,
                            existing.totalEUt + slot.getRecipeEUt()));
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                result.add(new MergedSlotDisplay(
                        slot.getSlotIndex(),
                        name,
                        slot.getParallelCount(),
                        slot.getTotalOperations(),
                        slot.getProgressTime(),
                        maxProg,
                        slot.getRecipeEUt()));
            }
        }

        return result;
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
        tag.setLong("totalPowerBudget", totalPowerBudget);

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
        this.totalPowerBudget = tag.getLong("totalPowerBudget");

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
