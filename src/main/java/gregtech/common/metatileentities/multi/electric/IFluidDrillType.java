package gregtech.common.metatileentities.multi.electric;

import gregtech.api.unification.material.Material;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;

/**
 * Interface for fluid drill type variants, providing access to tier, structure materials, renderers, and operational
 * parameters.
 */
public interface IFluidDrillType {

    String getName();

    int getTier();

    IBlockState getCasingState();

    Material getFrameMaterial();

    ICubeRenderer getCasingRenderer();

    int getRigMultiplier();

    int getDepletionChance();
}
