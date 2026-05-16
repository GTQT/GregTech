package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.recipes.RecipeMap;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Large turbine variants are now provided by {@link LargeTurbineVariants}.
 *             This enum remains as a source-compatible bridge for older addons.
 */
@Deprecated
public enum LargeTurbineType {

    STEAM(LargeTurbineVariants.STEAM,
            TurbineCasingType.STEEL_TURBINE_CASING,
            TurbineCasingType.STEEL_GEARBOX),

    GAS(LargeTurbineVariants.GAS,
            TurbineCasingType.STAINLESS_TURBINE_CASING,
            TurbineCasingType.STAINLESS_STEEL_GEARBOX),

    PLASMA(LargeTurbineVariants.PLASMA,
            TurbineCasingType.TUNGSTENSTEEL_TURBINE_CASING,
            TurbineCasingType.TUNGSTENSTEEL_GEARBOX);

    private final LargeTurbineVariant variant;
    private final TurbineCasingType casingType;
    private final TurbineCasingType gearboxType;

    LargeTurbineType(@NotNull LargeTurbineVariant variant,
                     @NotNull TurbineCasingType casingType,
                     @NotNull TurbineCasingType gearboxType) {
        this.variant = variant;
        this.casingType = casingType;
        this.gearboxType = gearboxType;
    }

    @NotNull
    public LargeTurbineVariant getVariant() {
        return variant;
    }

    @NotNull
    public String getName() {
        return variant.getId().getPath();
    }

    @NotNull
    public RecipeMap<?> getRecipeMap() {
        return variant.getRecipeMap();
    }

    public int getTier() {
        return variant.getTier();
    }

    @NotNull
    public TurbineCasingType getCasingType() {
        return casingType;
    }

    @NotNull
    public TurbineCasingType getGearboxType() {
        return gearboxType;
    }

    @NotNull
    public IBlockState getCasingState() {
        return MetaBlocks.TURBINE_CASING.getState(casingType);
    }

    @NotNull
    public IBlockState getGearboxState() {
        return MetaBlocks.TURBINE_CASING.getState(gearboxType);
    }

    @NotNull
    public ICubeRenderer getCasingRenderer() {
        return variant.getCasingRenderer();
    }

    @NotNull
    public ICubeRenderer getFrontOverlay() {
        return variant.getFrontOverlay();
    }
}
