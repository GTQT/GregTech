package gregtech.common.metatileentities.multi.electric.godforge.module;

import net.minecraft.util.ResourceLocation;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.common.blocks.BlockGodforgeCasing;

public class MTESmeltingModule extends MTEBaseModule {

    private boolean furnaceMode;

    public MTESmeltingModule(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GodforgeRecipeMaps.GODFORGE_SMELTING_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MTESmeltingModule(metaTileEntityId);
    }

    @Override
    protected TraceabilityPredicate getCoilBlockPredicate() {
        return states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    public boolean isFurnaceModeOn() {
        return furnaceMode;
    }

    public void setFurnaceMode(boolean furnaceMode) {
        this.furnaceMode = furnaceMode;
    }
}
