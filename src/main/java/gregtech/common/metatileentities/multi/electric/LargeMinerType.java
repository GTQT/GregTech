package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;

/**
 * Enum encapsulating all variant-specific configuration for Large Miners. Each constant fully describes a large miner
 * variant's tier, structure materials, renderers, and operational parameters.
 */
public enum LargeMinerType implements ILargeMinerType {

    BASIC("ev", GTValues.EV, 16, 3, 4,
            Materials.Steel,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID),
            Textures.SOLID_STEEL_CASING,
            Textures.LARGE_MINER_OVERLAY_BASIC,
            8),

    NORMAL("iv", GTValues.IV, 4, 5, 5,
            Materials.Titanium,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.TITANIUM_STABLE),
            Textures.STABLE_TITANIUM_CASING,
            Textures.LARGE_MINER_OVERLAY_ADVANCED,
            16),

    ADVANCED("luv", GTValues.LuV, 1, 7, 6,
            Materials.TungstenSteel,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.TUNGSTENSTEEL_ROBUST),
            Textures.ROBUST_TUNGSTENSTEEL_CASING,
            Textures.LARGE_MINER_OVERLAY_ADVANCED_2,
            32);

    private final String name;
    private final int tier;
    private final int speed;
    private final int maximumChunkDiameter;
    private final int fortune;
    private final Material frameMaterial;
    private final IBlockState casingType;
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;
    private final int drillingFluidConsumePerTick;

    LargeMinerType(String name, int tier, int speed, int maximumChunkDiameter, int fortune,
                   Material frameMaterial, IBlockState casingType,
                   ICubeRenderer casingRenderer, ICubeRenderer frontOverlay,
                   int drillingFluidConsumePerTick) {
        this.name = name;
        this.tier = tier;
        this.speed = speed;
        this.maximumChunkDiameter = maximumChunkDiameter;
        this.fortune = fortune;
        this.frameMaterial = frameMaterial;
        this.casingType = casingType;
        this.casingRenderer = casingRenderer;
        this.frontOverlay = frontOverlay;
        this.drillingFluidConsumePerTick = drillingFluidConsumePerTick;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public int getSpeed() {
        return speed;
    }

    @Override
    public int getMaximumChunkDiameter() {
        return maximumChunkDiameter;
    }

    @Override
    public int getFortune() {
        return fortune;
    }

    @Override
    public Material getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    public IBlockState getCasingState() {
        return casingType;
    }

    @Override
    public ICubeRenderer getCasingRenderer() {
        return casingRenderer;
    }

    @Override
    public ICubeRenderer getFrontOverlay() {
        return frontOverlay;
    }

    @Override
    public int getDrillingFluidConsumePerTick() {
        return drillingFluidConsumePerTick;
    }
}
