package gregicality.multiblocks.api.capability;

public interface IParallelMultiblock {

    boolean isParallel();

    int getParallel();

    void setParallel(int ParallelAmount);

    int getMaxParallel();
}
