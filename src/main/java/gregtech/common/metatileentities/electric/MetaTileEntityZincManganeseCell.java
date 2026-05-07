package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * A0 — Zinc-Manganese Dry Cell Block (LV tier).
 *
 * <p>Chemistry: Zn + MnO₂ + KOH (alkaline dry cell, Leclanché cell variant).
 * Capacity: 20 000 000 EU (≥ 2 hours at 128 EU/t full load).
 * Byproduct on depletion: small piles of ZnO (Zincite) dust.
 */
public class MetaTileEntityZincManganeseCell extends MetaTileEntityDisposableBatteryBase {

    /** Total EU capacity: 20 M EU (~2.2 h at 128 EU/t). */
    private static final long MAX_EU = 20_000_000L;

    public MetaTileEntityZincManganeseCell(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.LV, MAX_EU);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(
            gregtech.api.metatileentity.interfaces.IGregTechTileEntity tileEntity) {
        return new MetaTileEntityZincManganeseCell(metaTileEntityId);
    }

    /**
     * Spawns ZnO (Zincite) small dust piles as the discharge byproduct and removes the block.
     * Represents zinc electrode oxidation: Zn → ZnO during galvanic discharge.
     */
    @Override
    protected void onDepleted() {
        ItemStack zinciteByproduct = OreDictUnifier.get(OrePrefix.dustSmall, Materials.Zincite, 4);
        depleteAndDrop(zinciteByproduct);
    }
}
