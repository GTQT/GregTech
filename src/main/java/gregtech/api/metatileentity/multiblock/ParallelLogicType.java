package gregtech.api.metatileentity.multiblock;

public enum ParallelLogicType {

    /**
     * @deprecated Use {@link #CROSS_RECIPE} instead. MULTIPLY only duplicates a single recipe;
     * CROSS_RECIPE subsumes this behavior with its "same recipe first" strategy.
     */
    @Deprecated
    MULTIPLY,

    /**
     * @deprecated Use {@link #CROSS_RECIPE} instead. APPEND_ITEMS merges different item recipes
     * into one combined execution; CROSS_RECIPE handles them as independent slots with better control.
     */
    @Deprecated
    APPEND_ITEMS,

    /**
     * @deprecated Use {@link #CROSS_RECIPE} instead. APPEND_FLUIDS merges different fluid recipes
     * into one combined execution; CROSS_RECIPE handles them as independent slots with better control.
     */
    @Deprecated
    APPEND_FLUIDS,

    /**
     * @deprecated Use {@link #CROSS_RECIPE} instead. APPEND_ALL merges item and fluid recipes
     * into one combined execution; CROSS_RECIPE handles them as independent slots with better control.
     */
    @Deprecated
    APPEND_ALL,

    /**
     * Cross-recipe parallel mode: multiple different recipes can run concurrently in separate execution slots.
     * Each slot has independent progress/duration/outputs. Uses a shared power pool and unified execution chain:
     * <ol>
     *   <li>MULTIPLY: determine parallel count from available inputs</li>
     *   <li>Overclock: reduce duration</li>
     *   <li>1tOC: if duration reaches 1 tick, remaining OCs become sub-tick parallel</li>
     * </ol>
     */
    CROSS_RECIPE,
}
