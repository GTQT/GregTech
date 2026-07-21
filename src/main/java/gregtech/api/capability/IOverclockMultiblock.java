package gregtech.api.capability;

/**
 * Bridge interface for multiblock controllers that support {@link IOverclockHatch}.
 * <p>
 * The controller reads the OverclockHatch value and provides it to the recipe logic.
 */
public interface IOverclockMultiblock {

    /**
     * @return the overclock duration divisor from the OverclockHatch,
     *         or 0 if no OverclockHatch is installed (falls back to default behavior)
     */
    int getOverclockDurationDivisor();
}
