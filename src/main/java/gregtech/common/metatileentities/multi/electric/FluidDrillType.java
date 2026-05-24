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
 * Enum encapsulating all variant-specific configuration for Fluid Drilling Rigs. Each constant fully describes a fluid
 * drill variant's tier, structure materials, renderers, and operational parameters.
 */
public enum FluidDrillType implements IFluidDrillType {

    BASIC("mv", GTValues.MV,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID),
            Materials.Steel,
            Textures.SOLID_STEEL_CASING,
            1, 1),

    NORMAL("hv", GTValues.HV,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.TITANIUM_STABLE),
            Materials.Titanium,
            Textures.STABLE_TITANIUM_CASING,
            16, 2),

    ADVANCED("ev", GTValues.EV,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.TUNGSTENSTEEL_ROBUST),
            Materials.TungstenSteel,
            Textures.ROBUST_TUNGSTENSTEEL_CASING,
            64, 8);

    // Registration Data
    private final String name;

    // Tier Data
    private final int tier;

    // Structure Data
    private final IBlockState casingType;
    private final Material frameMaterial;

    // Rendering Data
    private final ICubeRenderer casingRenderer;

    // Operational Data
    private final int rigMultiplier;
    private final int depletionChance;

    FluidDrillType(String name, int tier,
                   IBlockState casingType,
                   Material frameMaterial,
                   ICubeRenderer casingRenderer,
                   int rigMultiplier, int depletionChance) {
        this.name = name;
        this.tier = tier;
        this.casingType = casingType;
        this.frameMaterial = frameMaterial;
        this.casingRenderer = casingRenderer;
        this.rigMultiplier = rigMultiplier;
        this.depletionChance = depletionChance;
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
    public IBlockState getCasingState() {
        return casingType;
    }

    @Override
    public Material getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    public ICubeRenderer getCasingRenderer() {
        return casingRenderer;
    }

    @Override
    public int getRigMultiplier() {
        return rigMultiplier;
    }

    @Override
    public int getDepletionChance() {
        return depletionChance;
    }
}
