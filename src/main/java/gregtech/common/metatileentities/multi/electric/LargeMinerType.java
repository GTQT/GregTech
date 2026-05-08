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
 * Enum encapsulating all variant-specific configuration for Large Miners.
 * Each constant fully describes a large miner variant's tier, structure materials,
 * renderers, and operational parameters.
 */
public enum LargeMinerType {

    BASIC("ev", GTValues.EV, 16, 3, 4,
            Materials.Steel,
            BlockMetalCasing.MetalCasingType.STEEL_SOLID,
            Textures.SOLID_STEEL_CASING,
            Textures.LARGE_MINER_OVERLAY_BASIC,
            8),

    NORMAL("iv", GTValues.IV, 4, 5, 5,
            Materials.Titanium,
            BlockMetalCasing.MetalCasingType.TITANIUM_STABLE,
            Textures.STABLE_TITANIUM_CASING,
            Textures.LARGE_MINER_OVERLAY_ADVANCED,
            16),

    ADVANCED("luv", GTValues.LuV, 1, 7, 6,
            Materials.TungstenSteel,
            BlockMetalCasing.MetalCasingType.TUNGSTENSTEEL_ROBUST,
            Textures.ROBUST_TUNGSTENSTEEL_CASING,
            Textures.LARGE_MINER_OVERLAY_ADVANCED_2,
            32);

    // Registration Data
    private final String name;

    // Tier Data
    private final int tier;

    // Miner Logic Data
    private final int speed;
    private final int maximumChunkDiameter;
    private final int fortune;

    // Structure Data
    private final Material frameMaterial;
    private final BlockMetalCasing.MetalCasingType casingType;

    // Rendering Data
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;

    // Operational Data
    private final int drillingFluidConsumePerTick;

    LargeMinerType(String name, int tier, int speed, int maximumChunkDiameter, int fortune,
                   Material frameMaterial, BlockMetalCasing.MetalCasingType casingType,
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

    public String getName() {
        return name;
    }

    public int getTier() {
        return tier;
    }

    public int getSpeed() {
        return speed;
    }

    public int getMaximumChunkDiameter() {
        return maximumChunkDiameter;
    }

    public int getFortune() {
        return fortune;
    }

    public Material getFrameMaterial() {
        return frameMaterial;
    }

    public BlockMetalCasing.MetalCasingType getCasingType() {
        return casingType;
    }

    public IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(casingType);
    }

    public ICubeRenderer getCasingRenderer() {
        return casingRenderer;
    }

    public ICubeRenderer getFrontOverlay() {
        return frontOverlay;
    }

    public int getDrillingFluidConsumePerTick() {
        return drillingFluidConsumePerTick;
    }
}
