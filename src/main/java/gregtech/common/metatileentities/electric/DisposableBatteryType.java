package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.function.Supplier;

/**
 * Registry of all A-series disposable (single-use) battery block variants.
 *
 * <p>Each enum constant fully defines one battery type: voltage tier, EU capacity,
 * and a lambda that produces the chemical byproduct stacks dropped on depletion. The base MTE class
 * {@link MetaTileEntityDisposableBatteryBase} reads these at construction and depletion time, eliminating the need for
 * per-variant subclasses.
 */
public enum DisposableBatteryType {

    // A0 — Zinc-Manganese Dry Cell (LV)
    // Byproduct: ZnO (Zincite) small dust — zinc electrode oxidation residue
    ZINC_MANGANESE(GTValues.LV, 20_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Zincite, 4)
            }),

    // A1 — Lithium-Manganese Button Cell (MV)
    // Byproduct: MnO₂ small dust — cathode residue
    LITHIUM_MANGANESE(GTValues.MV, 80_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Pyrolusite, 4)
            }),

    // A2 — Nickel-Cadmium Cell (HV)
    // Byproducts: NiO small dust + Cd small dust — electrode degradation residues
    NICKEL_CADMIUM(GTValues.HV, 320_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.NickelOxide, 4),
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Cadmium, 4)
            }),

    // A3 — Lead-Acid Battery (EV)
    // Byproducts: Pb small dust + Diluted Sulfuric Acid bucket — spent electrodes + electrolyte
    LEAD_ACID(GTValues.EV, 1_200_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Lead, 8),
                    FluidUtil.getFilledBucket(
                            new FluidStack(Materials.DilutedSulfuricAcid.getFluid(), 1000))
            }),

    // A4 — Vanadium Redox Flow Battery (IV)
    // Byproducts: V₂O₅ small dust + Diluted Sulfuric Acid bucket
    VANADIUM_FLOW(GTValues.IV, 5_000_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.VanadiumPentoxide, 4),
                    FluidUtil.getFilledBucket(
                            new FluidStack(Materials.DilutedSulfuricAcid.getFluid(), 1000))
            }),

    // A5 — Lithium Iron Phosphate / LFP Battery (LuV)
    // Byproducts: Li small dust + FePO₄ small dust — anode + delithiated cathode
    LFP(GTValues.LuV, 20_000_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Lithium, 4),
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.IronIIIPhosphate, 4)
            }),

    // A6 — Lithium Cobalt Oxide / LCO Battery (ZPM)
    // Byproducts: Co small dust + LiCoO₂ small dust — degraded cathode residues
    LCO(GTValues.ZPM, 80_000_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Cobalt, 4),
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.LithiumCobaltOxide, 4)
            }),

    // A7 — NMC Ternary Lithium Battery (UV)
    // Byproducts: NMC cathode powder small dust + Li small dust
    NMC(GTValues.UV, 320_000_000_000L,
            () -> new ItemStack[] {
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.NMCCathodePowder, 4),
                    OreDictUnifier.get(OrePrefix.dustSmall, Materials.Lithium, 4)
            });

    private final int tier;
    private final long maxStoredEU;
    private final Supplier<ItemStack[]> byproductsSupplier;

    DisposableBatteryType(int tier, long maxStoredEU,
                          Supplier<ItemStack[]> byproductsSupplier) {
        this.tier = tier;
        this.maxStoredEU = maxStoredEU;
        this.byproductsSupplier = byproductsSupplier;
    }

    /** GT voltage tier constant (e.g. {@link GTValues#LV}). */
    public int getTier() {
        return tier;
    }

    /** Total EU this battery type can supply over its lifetime. */
    public long getMaxStoredEU() {
        return maxStoredEU;
    }

    /**
     * Creates a fresh array of byproduct {@link ItemStack}s to drop on depletion. A new array is returned each call so
     * callers may mutate it safely.
     */
    public ItemStack[] createByproducts() {
        return byproductsSupplier.get();
    }
}
