package gregtech.api.capability;

public interface IAccelerateHatch {

    /**
     * @return the current speed multiplier as a percentage (e.g., 50 = 50% = 0.50x duration).
     */
    int getCurrentPercentage();

    void setCurrentPercentage(int percentage);

    /**
     * @return the minimum percentage this hatch can provide (lower = faster).
     */
    int getMinPercentage();

    /**
     * @return the tier of this hatch, used for voltage penalty calculation.
     */
    int getHatchTier();

    /**
     * Calculate the effective speed multiplier considering voltage penalty.
     * <p>
     * When {@code recipeTier <= hatchTier}, full acceleration applies (percentage / 100).<br>
     * When {@code recipeTier > hatchTier}, each extra tier adds 0.2 to the multiplier, capped at 1.0.
     *
     * @param recipeTier the voltage tier of the recipe being processed
     * @return the effective multiplier (0.24 ~ 1.0)
     */
    default float getEffectiveMultiplier(int recipeTier) {
        float base = getCurrentPercentage() / 100.0f;
        int tierDiff = Math.max(0, recipeTier - getHatchTier());
        return Math.min(1.0f, base + tierDiff * 0.2f);
    }
}
