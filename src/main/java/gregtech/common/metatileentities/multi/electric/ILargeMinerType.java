package gregtech.common.metatileentities.multi.electric;

import gregtech.api.unification.material.Material;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;

/**
 * Interface for large miner type variants, providing access to tier, structure materials, renderers, and operational
 * parameters.
 */
public interface ILargeMinerType {

    String getName();

    int getTier();

    int getSpeed();

    int getMaximumChunkDiameter();

    int getFortune();

    Material getFrameMaterial();

    IBlockState getCasingState();

    ICubeRenderer getCasingRenderer();

    ICubeRenderer getFrontOverlay();

    int getDrillingFluidConsumePerTick();
}
