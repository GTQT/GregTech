package gregtech.api.capability;

public interface IParallelHatch {

    /**
     *
     * @return the current maximum amount of parallelization provided
     */
    int getCurrentParallel();

    void setCurrentParallel(int parallelAmount);

    int getMaxParallel();

    /**
     * Whether this hatch asks for cross-recipe parallel rather than the ordinary per-recipe kind.
     * Only the cross-parallel hatch overrides this; every other parallel hatch keeps the default.
     */
    default boolean isCrossParallel() {
        return false;
    }
}
