package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;

public enum LargeTurbineType implements ILargeTurbineType {

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

    private final RecipeMap<?> recipeMap;
    private final int tier;
    private final TurbineCasingType casingType;
    private final TurbineCasingType gearboxType;
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;
    private final String name;
    private final boolean hasMufflerHatch;

    LargeTurbineType(String name, RecipeMap<?> recipeMap, int tier,
                     TurbineCasingType casingType, TurbineCasingType gearboxType,
                     ICubeRenderer casingRenderer, boolean hasMufflerHatch, ICubeRenderer frontOverlay) {
        this.name = name;
        this.recipeMap = recipeMap;
        this.tier = tier;
        this.casingType = casingType;
        this.gearboxType = gearboxType;
        this.casingRenderer = casingRenderer;
        this.hasMufflerHatch = hasMufflerHatch;
        this.frontOverlay = frontOverlay;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull RecipeMap<?> getRecipeMap() {return recipeMap;}

    @Override
    public int getTier() {return tier;}

    @Override
    public @NotNull TurbineCasingType getCasingType() {return casingType;}

    @Override
    public @NotNull TurbineCasingType getGearboxType() {return gearboxType;}

    @Override
    public @NotNull IBlockState getCasingState() {
        return MetaBlocks.TURBINE_CASING.getState(casingType);
    }

    @Override
    public @NotNull IBlockState getGearboxState() {
        return MetaBlocks.TURBINE_CASING.getState(gearboxType);
    }

    @Override
    public @NotNull ICubeRenderer getCasingRenderer() {return casingRenderer;}

    @Override
    public boolean hasMufflerHatch() {
        return hasMufflerHatch;
    }

    @Override
    public @NotNull ICubeRenderer getFrontOverlay() {return frontOverlay;}
}
