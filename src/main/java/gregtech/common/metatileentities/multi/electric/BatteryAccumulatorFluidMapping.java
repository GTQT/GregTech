package gregtech.common.metatileentities.multi.electric;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;

import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Registry of all electrolyte fluid pairs supported by the Battery Accumulator multiblock.
 *
 * <p>Each enum constant maps an uncharged electrolyte fluid (used in disposable battery
 * crafting) to its charged variant, along with the EU energy capacity per 1000 mB (one bucket).
 * The accumulator charges uncharged fluid + EU → charged fluid, and discharges
 * charged fluid → uncharged fluid + EU, with a configurable loss rate.
 *
 * <p>EU per bucket is derived from the corresponding {@link DisposableBatteryType}'s
 * total EU capacity divided by the fluid amount consumed per battery in the Canner step.
 */
public enum BatteryAccumulatorFluidMapping {

    // A0 — Zinc-Manganese Dry Cell (LV)
    // 20M EU / 10000 mB = 2,000,000 EU per 1000 mB
    ZINC_MANGANESE(
            Materials.ZincManganesePaste,
            Materials.ChargedZincManganesePaste,
            2_000_000L),

    // A1 — Lithium-Manganese Button Cell (MV)
    // 80M EU / 10000 mB = 8,000,000 EU per 1000 mB
    LITHIUM_MANGANESE(
            Materials.LithiumManganesePaste,
            Materials.ChargedLithiumManganesePaste,
            8_000_000L),

    // A2 — Nickel-Cadmium Cell (HV)
    // 320M EU / 10000 mB = 32,000,000 EU per 1000 mB
    NICKEL_CADMIUM(
            Materials.NickelCadmiumElectrolyte,
            Materials.ChargedNickelCadmiumElectrolyte,
            32_000_000L),

    // A3 — Lead-Acid Battery (EV)
    // 1.2B EU / 10000 mB = 120,000,000 EU per 1000 mB
    LEAD_ACID(
            Materials.LeadAcidElectrolyte,
            Materials.ChargedLeadAcidElectrolyte,
            120_000_000L),

    // A4 — Vanadium Redox Flow Battery (IV)
    // 5B EU / 10000 mB = 500,000,000 EU per 1000 mB
    VANADIUM_FLOW(
            Materials.VanadiumElectrolyte,
            Materials.ChargedVanadiumElectrolyte,
            500_000_000L),

    // A5 — Lithium Iron Phosphate / LFP Battery (LuV)
    // 20B EU / 10000 mB = 2,000,000,000 EU per 1000 mB
    LFP(
            Materials.Polybenzimidazole,
            Materials.ChargedPolybenzimidazole,
            2_000_000_000L),

    // A6 — Lithium Cobalt Oxide / LCO Battery (ZPM)
    // 80B EU / 10000 mB = 8,000,000,000 EU per 1000 mB
    LCO(
            Materials.PVDF,
            Materials.ChargedPVDF,
            8_000_000_000L),

    // A7 — NMC Ternary Lithium Battery (UV)
    // 320B EU / 10000 mB = 32,000,000,000 EU per 1000 mB
    NMC(
            Materials.LithiumHexafluorophosphate,
            Materials.ChargedLithiumHexafluorophosphate,
            32_000_000_000L);

    /** The uncharged electrolyte fluid material. */
    private final Material unchargedFluid;

    /** The charged electrolyte fluid material. */
    private final Material chargedFluid;

    /** EU energy stored per 1000 mB (one bucket) of charged fluid. */
    private final long euPerBucket;

    BatteryAccumulatorFluidMapping(Material unchargedFluid, Material chargedFluid, long euPerBucket) {
        this.unchargedFluid = unchargedFluid;
        this.chargedFluid = chargedFluid;
        this.euPerBucket = euPerBucket;
    }

    public Material getUnchargedFluid() {
        return unchargedFluid;
    }

    public Material getChargedFluid() {
        return chargedFluid;
    }

    public long getEuPerBucket() {
        return euPerBucket;
    }

    /**
     * Returns the EU stored in a given amount of charged fluid.
     *
     * @param amountMb fluid amount in millibuckets
     * @return EU equivalent
     */
    public long getEuForAmount(int amountMb) {
        return euPerBucket * amountMb / 1000;
    }

    /**
     * Returns the fluid amount (mB) that represents the given EU.
     *
     * @param eu energy in EU
     * @return fluid amount in millibuckets
     */
    public int getAmountForEu(long eu) {
        if (euPerBucket == 0) return 0;
        return (int) (eu * 1000 / euPerBucket);
    }

    /**
     * Creates a FluidStack of the uncharged electrolyte.
     *
     * @param amount fluid amount in millibuckets
     * @return the uncharged FluidStack, or null if the fluid is not registered
     */
    @Nullable
    public FluidStack getUnchargedFluidStack(int amount) {
        if (unchargedFluid.getFluid() == null) return null;
        return unchargedFluid.getFluid(amount);
    }

    /**
     * Creates a FluidStack of the charged electrolyte.
     *
     * @param amount fluid amount in millibuckets
     * @return the charged FluidStack, or null if the fluid is not registered
     */
    @Nullable
    public FluidStack getChargedFluidStack(int amount) {
        if (chargedFluid.getFluid() == null) return null;
        return chargedFluid.getFluid(amount);
    }

    /**
     * Finds the mapping for a given uncharged fluid material.
     *
     * @param fluidMaterial the uncharged fluid material to look up
     * @return the corresponding mapping, or null if not found
     */
    @Nullable
    public static BatteryAccumulatorFluidMapping fromUnchargedFluid(Material fluidMaterial) {
        for (BatteryAccumulatorFluidMapping mapping : values()) {
            if (mapping.unchargedFluid == fluidMaterial) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Finds the mapping for a given charged fluid material.
     *
     * @param fluidMaterial the charged fluid material to look up
     * @return the corresponding mapping, or null if not found
     */
    @Nullable
    public static BatteryAccumulatorFluidMapping fromChargedFluid(Material fluidMaterial) {
        for (BatteryAccumulatorFluidMapping mapping : values()) {
            if (mapping.chargedFluid == fluidMaterial) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Finds the mapping for a given FluidStack by checking both uncharged and charged fluids.
     *
     * @param fluidStack the fluid stack to look up
     * @return the corresponding mapping, or null if not found
     */
    @Nullable
    public static BatteryAccumulatorFluidMapping fromFluidStack(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return null;
        for (BatteryAccumulatorFluidMapping mapping : values()) {
            if (mapping.unchargedFluid.getFluid() == fluidStack.getFluid() ||
                    mapping.chargedFluid.getFluid() == fluidStack.getFluid()) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Checks whether the given FluidStack is an uncharged electrolyte supported by the accumulator.
     */
    public static boolean isUnchargedFluid(FluidStack fluidStack) {
        return fromUnchargedFluid(getMaterialFromFluid(fluidStack)) != null;
    }

    /**
     * Checks whether the given FluidStack is a charged electrolyte supported by the accumulator.
     */
    public static boolean isChargedFluid(FluidStack fluidStack) {
        return fromChargedFluid(getMaterialFromFluid(fluidStack)) != null;
    }

    /**
     * Attempts to resolve a FluidStack back to its GT Material.
     * This relies on the fluid being registered via GT's material system.
     */
    @Nullable
    private static Material getMaterialFromFluid(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return null;
        for (BatteryAccumulatorFluidMapping mapping : values()) {
            if (mapping.unchargedFluid.getFluid() == fluidStack.getFluid()) {
                return mapping.unchargedFluid;
            }
            if (mapping.chargedFluid.getFluid() == fluidStack.getFluid()) {
                return mapping.chargedFluid;
            }
        }
        return null;
    }
}
