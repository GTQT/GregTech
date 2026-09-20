package gregtech.common.pipelike.fiber;

/** Node data of a fiber optic line. Fiber carries no per-block state: no material, no attenuation. */
public class FiberPipeProperties {

    public static final FiberPipeProperties INSTANCE = new FiberPipeProperties();

    private FiberPipeProperties() {}
}
