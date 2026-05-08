package gregtech.common.metatileentities.multi.electric.godforge.module;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.properties.impl.FogMultiStepProperty;
import gregtech.api.recipes.properties.impl.FogPlasmaTierProperty;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.mui.multiblock.godforge.MTEBaseModuleGui;
import gregtech.common.mui.multiblock.godforge.MTEPlasmaModuleGui;

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
    protected MTEBaseModuleGui<?> createModuleGui() {
        return new MTEPlasmaModuleGui(this);
    }

    @Override
    protected TraceabilityPredicate getCoilBlockPredicate() {
        return states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    // ==================== Recipe Filtering ====================

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        // Check plasma tier: module must have sufficient tier from upgrades (SEDS/EE)
        int recipeTier = recipe.getProperty(FogPlasmaTierProperty.getInstance(), 0);
        if (getPlasmaTier() < recipeTier) {
            return false;
        }

        // Check multi-step capability: requires TPTP upgrade
        boolean recipeMultiStep = recipe.getProperty(FogMultiStepProperty.getInstance(), false);
        if (recipeMultiStep && !isMultiStepPlasma()) {
            return false;
        }

        return true;
    }

    public int getInputMaxParallel() {
        return inputMaxParallel;
    }

    public void setInputMaxParallel(int inputMaxParallel) {
        this.inputMaxParallel = inputMaxParallel;
    }
}
