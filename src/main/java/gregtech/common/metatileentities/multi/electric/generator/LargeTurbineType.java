package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;

/**
 * Enum encapsulating all variant-specific configuration for Large Turbines.
 * Each constant fully describes a turbine variant's recipe map, tier,
 * structure block states, renderers, and behavioral flags.
 */
public enum LargeTurbineType {

    STEAM("steam", RecipeMaps.STEAM_TURBINE_FUELS, GTValues.HV,
            TurbineCasingType.STEEL_TURBINE_CASING,
            TurbineCasingType.STEEL_GEARBOX,
            Textures.TURBINE_STEEL_CASING, false, Textures.LARGE_STEAM_TURBINE_OVERLAY),

    GAS("gas", RecipeMaps.GAS_TURBINE_FUELS, GTValues.EV,
            TurbineCasingType.STAINLESS_TURBINE_CASING,
            TurbineCasingType.STAINLESS_STEEL_GEARBOX,
            Textures.TURBINE_STAINLESS_STEEL_CASING, true, Textures.LARGE_GAS_TURBINE_OVERLAY),

    PLASMA("plasma", RecipeMaps.PLASMA_GENERATOR_FUELS, GTValues.IV,
            TurbineCasingType.TUNGSTENSTEEL_TURBINE_CASING,
            TurbineCasingType.TUNGSTENSTEEL_GEARBOX,
            Textures.TURBINE_TUNGSTENSTEEL_CASING, false, Textures.LARGE_PLASMA_TURBINE_OVERLAY);

    // Registration Data
    private final String name;

    // Recipe Data
    private final RecipeMap<?> recipeMap;
    private final int tier;

    // Structure Data
    private final TurbineCasingType casingType;
    private final TurbineCasingType gearboxType;

    // Rendering Data
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;

    // Behavioral Data
    private final boolean hasMufflerHatch;

    LargeTurbineType(String name, RecipeMap<?> recipeMap, int tier,
                     TurbineCasingType casingType, TurbineCasingType gearboxType,
                     ICubeRenderer casingRenderer, boolean hasMufflerHatch,
                     ICubeRenderer frontOverlay) {
        this.name = name;
        this.recipeMap = recipeMap;
        this.tier = tier;
        this.casingType = casingType;
        this.gearboxType = gearboxType;
        this.casingRenderer = casingRenderer;
        this.hasMufflerHatch = hasMufflerHatch;
        this.frontOverlay = frontOverlay;
    }

    public String getName() {
        return name;
    }

    public RecipeMap<?> getRecipeMap() {
        return recipeMap;
    }

    public int getTier() {
        return tier;
    }

    public TurbineCasingType getCasingType() {
        return casingType;
    }

    public TurbineCasingType getGearboxType() {
        return gearboxType;
    }

    public IBlockState getCasingState() {
        return MetaBlocks.TURBINE_CASING.getState(casingType);
    }

    public IBlockState getGearboxState() {
        return MetaBlocks.TURBINE_CASING.getState(gearboxType);
    }

    public ICubeRenderer getCasingRenderer() {
        return casingRenderer;
    }

    public boolean hasMufflerHatch() {
        return hasMufflerHatch;
    }

    public ICubeRenderer getFrontOverlay() {
        return frontOverlay;
    }
}
