package gregtech.api.recipes.logic;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Domain-agnostic execution condition attached to an
 * {@link gregtech.api.capability.impl.AbstractRecipeLogic}.
 *
 * <p>Conditions participate in the full recipe lifecycle across both single-recipe execution
 * and {@link CrossRecipeParallelScheduler} multi-slot execution, using a strict two-phase
 * simulate-then-commit model per tick ({@link #testTick} followed by {@link #consumeTick}).
 * Any per-recipe or per-slot state that must survive save/load should be stored in
 * {@link RecipeExecutionContext#getConditionData(String)} keyed by {@link #getConditionId()}.
 */
public interface IRecipeCondition {

    /**
     * Action to take when a recipe or slot fails a tick condition or energy check.
     */
    enum TickFailureAction {
        /**
         * Skip advancing progress for this tick without losing accumulated progress.
         */
        PAUSE,
        /**
         * Decrease or reset progress according to the machine's power-loss configuration.
         */
        DECREASE_PROGRESS
    }

    /**
     * @return a stable, unique identifier for this condition, used to namespace persisted
     *         per-recipe/per-slot data in {@link RecipeExecutionContext#getConditionData(String)}.
     */
    @NotNull
    String getConditionId();

    /**
     * Checks whether a candidate recipe can start with the provided input handlers.
     * Must be side-effect free.
     */
    default boolean canStart(@NotNull Recipe recipe,
                             @NotNull IItemHandlerModifiable importInventory,
                             @NotNull IMultipleTankHandler importFluids) {
        return true;
    }

    /**
     * Invoked when a recipe or scheduler slot starts (after inputs have been consumed and
     * initial output lists have been populated). Implementations may snapshot recipe parameters
     * into {@link RecipeExecutionContext#getConditionData(String)} and adjust mutable output lists.
     */
    default void onRecipeStart(@NotNull RecipeExecutionContext context) {}

    /**
     * Invoked once per server tick before any {@link #testTick(RecipeExecutionContext)} simulation
     * runs, allowing conditions that share machine-wide finite pools to reset per-tick reservations.
     */
    default void beginTick() {}

    /**
     * Phase 1 (simulate) per-tick check. Must not mutate world state or consume resources.
     *
     * @return {@code true} if this condition permits advancing progress this tick
     */
    default boolean testTick(@NotNull RecipeExecutionContext context) {
        return true;
    }

    /**
     * Invoked only after every condition's {@link #testTick(RecipeExecutionContext)} has returned
     * {@code true} for a slot or recipe, allowing shared-pool conditions to reserve this slot's share
     * before subsequent slots are simulated in the same tick.
     */
    default void reserveTick(@NotNull RecipeExecutionContext context) {}

    /**
     * Phase 2 (commit) per-tick execution. Called only after energy draw and all conditions'
     * {@link #testTick(RecipeExecutionContext)} checks have succeeded.
     */
    default void consumeTick(@NotNull RecipeExecutionContext context) {}

    /**
     * Whether this condition participates in {@link #onTickFailed(RecipeExecutionContext, boolean)}
     * when {@code energyShortage == true}. Passive property checks (such as {@link CleanroomRecipeCondition})
     * return {@code false} so ordinary recipes keep baseline scheduler power-loss semantics.
     */
    default boolean handlesEnergyShortage() {
        return true;
    }

    /**
     * Invoked when a tick fails either because energy draw failed ({@code energyShortage == true})
     * or because this condition's {@link #testTick(RecipeExecutionContext)} returned {@code false}.
     *
     * @param context        the execution context of the failing recipe or slot
     * @param energyShortage {@code true} if the failure was caused by insufficient energy
     * @return whether the recipe/slot should pause or decrease/reset its progress
     */
    @NotNull
    default TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context, boolean energyShortage) {
        return TickFailureAction.DECREASE_PROGRESS;
    }

    /**
     * Invoked when a recipe or scheduler slot completes and its outputs are committed,
     * immediately before the execution state / slot is cleared.
     */
    default void onRecipeComplete(@NotNull RecipeExecutionContext context) {}

    /**
     * Invoked when a recipe or slot is invalidated (structure deformation, power-loss reset, etc.).
     *
     * @param context the execution context being invalidated, or {@code null} when no recipe is active
     */
    default void onInvalidate(@Nullable RecipeExecutionContext context) {}
}
