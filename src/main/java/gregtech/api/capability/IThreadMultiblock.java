package gregtech.api.capability;

public interface IThreadMultiblock {

    /**
     * Whether this multiblock runs several recipes at once. Mirrors {@link IParallelMultiblock#isParallel()}: it
     * states that the machine is thread-capable, while the thread hatch supplies how many threads that is.
     */
    boolean isThread();

    int getThread();

    void setThread(int ThreadAmount);

    int getMaxThread();
}
