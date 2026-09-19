package gregtech.api.capability;

public interface IParallelMultiblock {

    boolean isParallel();

    /**
     * Whether the installed parallel hatch is the cross-recipe variant, i.e. the machine should let its slots
     * share one elastic parallel budget instead of capping each slot at {@link #getParallel()}.
     */
    boolean isCrossParallel();

    int getParallel();

    void setParallel(int ParallelAmount);

    int getMaxParallel();
}
