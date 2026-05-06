package gregtech.common.metatileentities.multi.electric.godforge.module;

import net.minecraft.util.ResourceLocation;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.common.blocks.BlockGodforgeCasing;

public class MTEPlasmaModule extends MTEBaseModule {

    private int inputMaxParallel;

    public MTEPlasmaModule(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GodforgeRecipeMaps.GODFORGE_PLASMA_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MTEPlasmaModule(metaTileEntityId);
    }

    @Override
    protected TraceabilityPredicate getCoilBlockPredicate() {
        return states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    public int getInputMaxParallel() {
        return inputMaxParallel;
    }

    public void setInputMaxParallel(int inputMaxParallel) {
        this.inputMaxParallel = inputMaxParallel;
    }
}
