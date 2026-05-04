package gregtech.common.metatileentities.multi.electric.godforge.data;

import net.minecraftforge.fluids.FluidStack;

import com.google.common.base.Supplier;

import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;

public enum Fuels {

    // TODO: When DTR, RawStarMatter, MHDCSM materials are added, uncomment below
    // RESIDUE(() -> Materials.DTR.getFluid(1)),
    // STELLAR(() -> Materials.RawStarMatter.getFluid(1)),
    // MHDCSM(() -> Materials.MHDCSM.getMolten(1));

    RESIDUE(() -> null),
    STELLAR(() -> null),
    MHDCSM(() -> null);

    public static final Fuels[] VALUES = values();

    private final Supplier<FluidStack> fluidSupplier;

    Fuels(Supplier<FluidStack> fluidSupplier) {
        this.fluidSupplier = fluidSupplier;
    }

    public FluidStack getFluid() {
        return fluidSupplier.get();
    }

    public void select(ForgeOfGodsData data) {
        data.setSelectedFuelType(ordinal());
    }

    public static Fuels getFromData(ForgeOfGodsData data) {
        return VALUES[data.getSelectedFuelType()];
    }
}
