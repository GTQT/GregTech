package gregicality.multiblocks.api.capability;

public interface IParallelHatch {

    /**
     *
     * @return the current maximum amount of parallelization provided
     */
    int getCurrentParallel();

    void setCurrentParallel(int parallelAmount);

    int getMaxParallel();
}
