package gregtech.api.pattern.casing;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;

/**
 * Standard hatch presets for common multiblock configurations.
 *
 * <p>Each preset encapsulates a reusable set of hatch declarations that can be applied to a
 * {@link DeclarativePatternBuilder.CasingSlot} via
 * {@link DeclarativePatternBuilder.CasingSlot#applyPreset(IHatchPreset)}.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * DeclarativePatternBuilder.start()
 *     .aisle("XXX", "XXX", "XXX")
 *     .aisle("XXX", "X#X", "XXX")
 *     .aisle("XXX", "XSX", "XXX")
 *     .where('S', selfPredicate(...))
 *     .where('#', air())
 *     .casing('X', casingDef)
 *         .applyPreset(HatchPresets.ELECTRIC_STANDARD)
 *     .buildTemplate();
 * }</pre>
 *
 * <h3>Available Presets:</h3>
 * <ul>
 *   <li>{@link #ELECTRIC_STANDARD} — Energy(1-2) + Maintenance + IO(4 each)</li>
 *   <li>{@link #ELECTRIC_MUFFLER} — Energy(1-2) + Maintenance + Muffler + IO(4 each)</li>
 *   <li>{@link #MUFFLER_IO} — Maintenance + Muffler + IO(4 each), no energy</li>
 *   <li>{@link #STANDARD_IO} — Items(4 each) + Fluids(4 each)</li>
 *   <li>{@link #STANDARD_ITEM_IO} — Items(4 import, 4 export)</li>
 *   <li>{@link #STANDARD_FLUID_IO} — Fluids(4 import, 4 export)</li>
 * </ul>
 *
 * <h3>Custom Presets:</h3>
 * Addons can create their own presets:
 * <pre>{@code
 * public static final IHatchPreset MY_PRESET = slot -> slot
 *         .withHatches(MultiblockAbility.INPUT_ENERGY, 1, 4)
 *         .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
 *         .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 8);
 * }</pre>
 *
 * @see IHatchPreset
 * @see DeclarativePatternBuilder.CasingSlot
 */
public final class HatchPresets {

    private HatchPresets() {}

    // --- IO presets ---

    /**
     * Standard item IO: optional import(4) + export(4) item bus.
     */
    public static final IHatchPreset STANDARD_ITEM_IO = slot -> slot
            .withOptionalHatches(MultiblockAbility.IMPORT_ITEMS, 4)
            .withOptionalHatches(MultiblockAbility.EXPORT_ITEMS, 4);

    /**
     * Standard fluid IO: optional import(4) + export(4) fluid hatch.
     */
    public static final IHatchPreset STANDARD_FLUID_IO = slot -> slot
            .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 4)
            .withOptionalHatches(MultiblockAbility.EXPORT_FLUIDS, 4);

    /**
     * Standard full IO: item buses(4 each) + fluid hatches(4 each), all optional.
     */
    public static final IHatchPreset STANDARD_IO = slot -> {
        STANDARD_ITEM_IO.apply(slot);
        STANDARD_FLUID_IO.apply(slot);
    };

    // --- Electric multiblock presets ---

    /**
     * Standard electric multiblock hatches (no muffler):
     * <ul>
     *   <li>Energy input: required, 1-2</li>
     *   <li>Maintenance: optional, 0-1</li>
     *   <li>Item IO: optional, 0-4 each</li>
     *   <li>Fluid IO: optional, 0-4 each</li>
     * </ul>
     */
    public static final IHatchPreset ELECTRIC_STANDARD = slot -> {
        slot.withHatches(MultiblockAbility.INPUT_ENERGY, 1, 2)
                .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1);
        STANDARD_IO.apply(slot);
    };

    /**
     * Standard electric multiblock hatches with muffler:
     * <ul>
     *   <li>Energy input: required, 1-2</li>
     *   <li>Maintenance: optional, 0-1</li>
     *   <li>Muffler: optional, 0-1</li>
     *   <li>Item IO: optional, 0-4 each</li>
     *   <li>Fluid IO: optional, 0-4 each</li>
     * </ul>
     */
    public static final IHatchPreset ELECTRIC_MUFFLER = slot -> {
        slot.withHatches(MultiblockAbility.INPUT_ENERGY, 1, 2)
                .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
                .withOptionalHatches(MultiblockAbility.MUFFLER_HATCH, 1);
        STANDARD_IO.apply(slot);
    };

    // --- Utility presets (no energy) ---

    /**
     * Maintenance + Muffler + standard IO, without energy input.
     * Useful for casings on multiblocks where energy is handled separately
     * (e.g. through a dedicated dynamo hatch character or fuel input).
     * <ul>
     *   <li>Maintenance: optional, 0-1</li>
     *   <li>Muffler: optional, 0-1</li>
     *   <li>Item IO: optional, 0-4 each</li>
     *   <li>Fluid IO: optional, 0-4 each</li>
     * </ul>
     */
    public static final IHatchPreset MUFFLER_IO = slot -> {
        slot.withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
                .withOptionalHatches(MultiblockAbility.MUFFLER_HATCH, 1);
        STANDARD_IO.apply(slot);
    };
}
