package gregtech.api.capability;

/**
 * Bridge interface for multiblock controllers that support {@link IAccelerateHatch}.
 * <p>
 * The controller reads the AccelerateHatch value and provides it to the recipe logic.
 */
public interface IAccelerateMultiblock {

    /**
     * Whether this multiblock takes its duration scaling from an {@link IAccelerateHatch}, mirroring
     * {@link IParallelMultiblock#isParallel()}: it states that the machine is accelerate-hatch-capable, while the
     * hatch supplies the multiplier.
     */
    boolean isAccelerate();

    /**
     * Get the effective speed multiplier from the AccelerateHatch,
     * considering voltage penalty (recipe tier vs hatch tier).
     *
     * @param recipeTier the voltage tier of the recipe being processed
     * @return the effective multiplier (0.24 ~ 1.0), or 1.0 if no AccelerateHatch is installed
     */
    float getAccelerateMultiplier(int recipeTier);
}
