package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * A1 — Lithium-Manganese Battery Block (MV tier).
 *
 * <p>Chemistry: Li + MnO₂ → LiMnO₂ (CR-series primary lithium cell).
 * Capacity: 80 000 000 EU (≥ 2 hours at 512 EU/t full load).
 * Byproducts on depletion: small piles of LithiumChloride dust + Pyrolusite (MnO₂) dust.
 */
public class MetaTileEntityLithiumManganeseCell extends MetaTileEntityDisposableBatteryBase {

    /** Total EU capacity: 80 M EU (~2.2 h at 512 EU/t). */
    private static final long MAX_EU = 80_000_000L;

    public MetaTileEntityLithiumManganeseCell(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.MV, MAX_EU);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(
            gregtech.api.metatileentity.interfaces.IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLithiumManganeseCell(metaTileEntityId);
    }

    /**
     * Spawns LithiumChloride and Pyrolusite (MnO₂) small dust piles as discharge byproducts
     * and removes the block.
     * Represents: Li → LiCl (anode oxidation) and residual MnO₂ cathode material.
     */
    @Override
    protected void onDepleted() {
        ItemStack lithiumChlorideByproduct =
                OreDictUnifier.get(OrePrefix.dustSmall, Materials.LithiumChloride, 2);
        ItemStack pyrolusiteByproduct =
                OreDictUnifier.get(OrePrefix.dustSmall, Materials.Pyrolusite, 4);
        depleteAndDrop(lithiumChlorideByproduct, pyrolusiteByproduct);
    }
}
