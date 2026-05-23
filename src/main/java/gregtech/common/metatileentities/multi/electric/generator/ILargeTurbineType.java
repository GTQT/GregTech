package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.recipes.RecipeMap;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;

public interface ILargeTurbineType {

    @NotNull
    String getName();

    @NotNull
    RecipeMap<?> getRecipeMap();

    int getTier();

    @NotNull
    IBlockState getCasingState();

    @NotNull
    IBlockState getGearboxState();

    @NotNull
    ICubeRenderer getCasingRenderer();

    boolean hasMufflerHatch();

    @NotNull
    ICubeRenderer getFrontOverlay();
}
