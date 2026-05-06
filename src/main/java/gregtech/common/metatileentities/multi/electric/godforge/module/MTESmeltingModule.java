package gregtech.common.metatileentities.multi.electric.godforge.module;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.common.blocks.BlockGodforgeCasing;

/**
 * Godforge Smelting Module.
 * Normal mode: runs Electric Blast Furnace recipes (temperature check applies).
 * Furnace mode: runs Arc Furnace recipes (no temperature requirement).
 * The module's heat value is set by {@code GodforgeMath.calculateMaxHeatForModules()},
 * allowing access to recipes that require extreme temperatures unreachable by normal EBFs.
 */
public class MTESmeltingModule extends MTEBaseModule implements IMultipleRecipeMaps {

    private static final RecipeMap<?>[] AVAILABLE_MAPS = {
            RecipeMaps.BLAST_RECIPES,
            RecipeMaps.ARC_FURNACE_RECIPES
    };

    private boolean furnaceMode;

    public MTESmeltingModule(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.BLAST_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MTESmeltingModule(metaTileEntityId);
    }

    @Override
    protected TraceabilityPredicate getCoilBlockPredicate() {
        return states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    // ==================== Temperature Check ====================

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        if (furnaceMode) {
            // Arc furnace recipes have no temperature requirement
            return true;
        }
        // Check that module heat meets recipe temperature requirement
        int recipeTemp = recipe.getProperty(TemperatureProperty.getInstance(), 0);
        return getHeat() >= recipeTemp;
    }

    // ==================== IMultipleRecipeMaps ====================

    @Override
    public RecipeMap<?>[] getAvailableRecipeMaps() {
        return AVAILABLE_MAPS;
    }

    @Override
    public RecipeMap<?> getCurrentRecipeMap() {
        return furnaceMode ? RecipeMaps.ARC_FURNACE_RECIPES : RecipeMaps.BLAST_RECIPES;
    }

    @Override
    public int getRecipeMapIndex() {
        return furnaceMode ? 1 : 0;
    }

    @Override
    public void setRecipeMapIndex(int index) {
        setFurnaceMode(index == 1);
    }

    // ==================== Mode ====================

    public boolean isFurnaceModeOn() {
        return furnaceMode;
    }

    public void setFurnaceMode(boolean furnaceMode) {
        this.furnaceMode = furnaceMode;
    }
}
