package gregtech.api.pattern.casing;

/**
 * Functional interface for applying a set of hatch declarations to a {@link DeclarativePatternBuilder.CasingSlot}.
 *
 * <p>Implementations define reusable hatch combinations that can be applied via
 * {@link DeclarativePatternBuilder.CasingSlot#applyPreset(IHatchPreset)}.
 *
 * <p>Standard presets are available in {@link HatchPresets}. Addons can define their own
 * by implementing this interface:
 * <pre>{@code
 * IHatchPreset MY_PRESET = slot -> slot
 *         .withHatches(MultiblockAbility.INPUT_ENERGY, 1, 2)
 *         .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1);
 * }</pre>
 *
 * @see HatchPresets
 * @see DeclarativePatternBuilder.CasingSlot#applyPreset(IHatchPreset)
 */
@FunctionalInterface
public interface IHatchPreset {

    /**
     * Apply hatch declarations to the given casing slot.
     *
     * @param slot the casing slot to configure
     */
    void apply(DeclarativePatternBuilder.CasingSlot slot);
}
