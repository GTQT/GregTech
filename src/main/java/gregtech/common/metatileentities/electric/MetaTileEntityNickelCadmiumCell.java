package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * A2 — Nickel-Cadmium Battery Block (HV tier).
 *
 * <p>Chemistry: sealed NiCd alkaline secondary cell (used here as primary / single-discharge).
 *   Anode:   Cd + 2 OH⁻ → Cd(OH)₂ + 2 e⁻
 *   Cathode: 2 NiOOH + 2 H₂O + 2 e⁻ → 2 Ni(OH)₂ + 2 OH⁻
 * Capacity: 320 000 000 EU (≥ 2 hours at 2 048 EU/t full HV load).
 * Byproducts on depletion: small piles of CadmiumSulfate (Cd residue) + NickelOxide (NiO).
 */
public class MetaTileEntityNickelCadmiumCell extends MetaTileEntityDisposableBatteryBase {

    /** Total EU capacity: 320 M EU (~2.2 h at 2 048 EU/t). */
    private static final long MAX_EU = 320_000_000L;

    public MetaTileEntityNickelCadmiumCell(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.HV, MAX_EU);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(
            gregtech.api.metatileentity.interfaces.IGregTechTileEntity tileEntity) {
        return new MetaTileEntityNickelCadmiumCell(metaTileEntityId);
    }

    /**
     * Spawns cadmium (Cd anode residue) and NickelOxide (NiO cathode residue) small dust piles.
     * Represents the solid discharge products that remain after a full NiCd discharge cycle.
     */
    @Override
    protected void onDepleted() {
        ItemStack cadmiumByproduct =
                OreDictUnifier.get(OrePrefix.dustSmall, Materials.Cadmium, 4);
        ItemStack nickelOxideByproduct =
                OreDictUnifier.get(OrePrefix.dustSmall, Materials.NickelOxide, 4);
        depleteAndDrop(cadmiumByproduct, nickelOxideByproduct);
    }
}
