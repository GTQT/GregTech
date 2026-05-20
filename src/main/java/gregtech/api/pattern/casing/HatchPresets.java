package gregtech.api.pattern.casing;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;

public final class HatchPresets {

    public static final IHatchPreset MUFFLER_IO = slot -> slot
            .hatch(MultiblockAbility.MAINTENANCE_HATCH, 1)
            .hatch(MultiblockAbility.MUFFLER_HATCH, 1);

    public static final IHatchPreset STANDARD_ITEM_IO = slot -> slot
            .optionalHatch(MultiblockAbility.IMPORT_ITEMS, 4)
            .optionalHatch(MultiblockAbility.EXPORT_ITEMS, 4);

    public static final IHatchPreset STANDARD_FLUID_IO = slot -> slot
            .optionalHatch(MultiblockAbility.IMPORT_FLUIDS, 4)
            .optionalHatch(MultiblockAbility.EXPORT_FLUIDS, 4);

    public static final IHatchPreset STANDARD_IO = slot -> {
        STANDARD_ITEM_IO.apply(slot);
        STANDARD_FLUID_IO.apply(slot);
    };

    public static final IHatchPreset ELECTRIC_STANDARD = slot -> {
        slot.hatch(MultiblockAbility.INPUT_ENERGY, 1, 2);
        MUFFLER_IO.apply(slot);
        STANDARD_IO.apply(slot);
    };

    private HatchPresets() {}
}
