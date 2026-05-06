package gregtech.common.metatileentities.multi.electric.godforge.data;

import net.minecraftforge.fluids.FluidStack;

import com.google.common.base.Supplier;

import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.unification.material.Materials;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;

public enum Fuels {

    // Dimensionally Transcendent Residue (primary fuel)
    RESIDUE(() -> Materials.DimensionallyTranscendentResidue.getFluid(1)),
    // Raw Star Matter (secondary fuel)
    STELLAR(() -> Materials.RawStarMatter.getFluid(1)),
    // Magneto-Hydrodynamically Constrained Star Matter (tertiary fuel, molten form)
    MHDCSM(() -> Materials.MagnetoHydrodynamicallyConstrainedStarMatter.getFluid(FluidStorageKeys.LIQUID, 1));

    public static final Fuels[] VALUES = values();

    private final Supplier<FluidStack> fluidSupplier;

    Fuels(Supplier<FluidStack> fluidSupplier) {
        this.fluidSupplier = fluidSupplier;
    }

    public FluidStack getFluid() {
        return fluidSupplier.get();
    }

    public FluidStack getFluid(int amount) {
        FluidStack base = fluidSupplier.get();
        if (base == null) return null;
        FluidStack copy = base.copy();
        copy.amount = amount;
        return copy;
    }

    public void select(ForgeOfGodsData data) {
        data.setSelectedFuelType(ordinal());
    }

    public static Fuels getFromData(ForgeOfGodsData data) {
        return VALUES[data.getSelectedFuelType()];
    }
}
