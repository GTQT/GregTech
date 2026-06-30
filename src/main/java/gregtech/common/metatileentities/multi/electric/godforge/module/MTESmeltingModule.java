package gregtech.common.metatileentities.multi.electric.godforge.module;

import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.mui.multiblock.godforge.MTEBaseModuleGui;
import gregtech.common.mui.multiblock.godforge.MTESmeltingModuleGui;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

/**
 * Godforge Smelting Module. Normal mode: runs Electric Blast Furnace recipes (temperature check applies). Furnace mode:
 * runs Furnace recipes (no temperature requirement). The module's heat value is set by
 * {@code GodforgeMath.calculateMaxHeatForModules()}, allowing access to recipes that require extreme temperatures
 * unreachable by normal EBFs.
 */
public class MTESmeltingModule extends MTEBaseModule implements IMultipleRecipeMaps {

    private static final RecipeMap<?>[] AVAILABLE_MAPS = {
            RecipeMaps.BLAST_RECIPES,
            RecipeMaps.FURNACE_RECIPES
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
    protected MTEBaseModuleGui<?> createModuleGui() {
        return new MTESmeltingModuleGui(this);
    }

    @Override
    protected IStructureElement getCoilBlockElement() {
        return Elements.block(getCasingState(
                BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    // ==================== Temperature Check ====================

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        if (furnaceMode) {
            // Furnace recipes have no temperature requirement
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
        return furnaceMode ? RecipeMaps.FURNACE_RECIPES : RecipeMaps.BLAST_RECIPES;
    }

    @Override
    public int getRecipeMapIndex() {
        return furnaceMode ? 1 : 0;
    }

    @Override
    public void setRecipeMapIndex(int index) {
        setFurnaceMode(index == 1);
    }

    // ==================== Tooltip ====================

    @Override
    @SideOnly(Side.CLIENT)
    public String recipeMapsToString() {
        RecipeMap<?>[] recipeMaps = getAvailableRecipeMaps();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipeMaps.length; i++) {
            sb.append(recipeMaps[i].getLocalizedName());
            if (i < recipeMaps.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // ==================== Mode ====================

    public boolean isFurnaceModeOn() {
        return furnaceMode;
    }

    public void setFurnaceMode(boolean furnaceMode) {
        this.furnaceMode = furnaceMode;
        if (getWorld() != null && !getWorld().isRemote) {
            // Use lazy invalidation instead of synchronous forceRecipeRecheck() to avoid
            // lag spikes. The next updateWorkable() tick will search from the new RecipeMap.
            ((GodforgeModuleRecipeLogic) this.recipeMapWorkable).invalidateForRecipeMapChange();
            markDirty();
        }
    }

    // ==================== NBT Persistence ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("furnaceMode", furnaceMode);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        furnaceMode = data.getBoolean("furnaceMode");
    }
}
