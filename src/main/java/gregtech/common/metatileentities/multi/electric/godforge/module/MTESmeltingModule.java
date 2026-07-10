package gregtech.common.metatileentities.multi.electric.godforge.module;

import gregtech.api.capability.IRecipeMapBoundInput;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.util.GTLog;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.mui.multiblock.godforge.MTEBaseModuleGui;
import gregtech.common.mui.multiblock.godforge.MTESmeltingModuleGui;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;
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
    private boolean recipeMapPatternRoutingEnabled;

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
        if (((GodforgeModuleRecipeLogic) recipeMapWorkable).getRecipeMap() == RecipeMaps.FURNACE_RECIPES) {
            // Furnace recipes have no temperature requirement
            return true;
        }
        // Check that module heat meets recipe temperature requirement
        int recipeTemp = recipe.getProperty(TemperatureProperty.getInstance(), 0);
        return getHeat() >= recipeTemp;
    }

    // ==================== IMultipleRecipeMaps ====================

    @Override
    public boolean supportsRecipeMapPatternRouting() {
        return recipeMapPatternRoutingEnabled;
    }

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
        if (recipeMapPatternRoutingEnabled) return;
        this.furnaceMode = furnaceMode;
        if (getWorld() != null && !getWorld().isRemote) {
            // Use lazy invalidation instead of synchronous forceRecipeRecheck() to avoid
            // lag spikes. The next updateWorkable() tick will search from the new RecipeMap.
            ((GodforgeModuleRecipeLogic) this.recipeMapWorkable).invalidateForRecipeMapChange();
            markDirty();
        }
    }

    public boolean isRecipeMapPatternRoutingEnabled() {
        return recipeMapPatternRoutingEnabled;
    }

    public void setRecipeMapPatternRoutingEnabled(boolean enabled) {
        if (recipeMapPatternRoutingEnabled == enabled) return;
        if (recipeMapWorkable.isActive() || hasRecipeMapBoundInputs()) {
            GTLog.logger.info("Godforge smelting module at {} rejected RecipeMap routing toggle while work is queued",
                    getPos());
            return;
        }
        recipeMapPatternRoutingEnabled = enabled;
        ((GodforgeModuleRecipeLogic) recipeMapWorkable).invalidateForRecipeMapChange();
        GTLog.logger.info("Godforge smelting module at {} {} RecipeMap pattern routing", getPos(),
                enabled ? "enabled" : "disabled");
        markDirty();
    }

    private boolean hasRecipeMapBoundInputs() {
        for (IItemHandlerModifiable input : getAbilities(MultiblockAbility.IMPORT_ITEMS)) {
            if (!(input instanceof IRecipeMapBoundInput)) continue;
            for (int slot = 0; slot < input.getSlots(); slot++) {
                if (!input.getStackInSlot(slot).isEmpty()) return true;
            }
        }
        return false;
    }

    // ==================== NBT Persistence ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("furnaceMode", furnaceMode);
        data.setBoolean("recipeMapPatternRoutingEnabled", recipeMapPatternRoutingEnabled);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        furnaceMode = data.getBoolean("furnaceMode");
        recipeMapPatternRoutingEnabled = data.getBoolean("recipeMapPatternRoutingEnabled");
    }
}
