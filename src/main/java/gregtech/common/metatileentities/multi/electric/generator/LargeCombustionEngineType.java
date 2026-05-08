package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.BlockMultiblockCasing.MultiblockCasingType;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;

/**
 * Enum encapsulating all variant-specific configuration for Large Combustion Engines.
 * Each constant fully describes a combustion engine variant's tier, structure block
 * states, renderers, and behavioral parameters.
 */
public enum LargeCombustionEngineType {

    REGULAR("large_combustion_engine", GTValues.EV,
            MetalCasingType.TITANIUM_STABLE,
            TurbineCasingType.TITANIUM_GEARBOX,
            MultiblockCasingType.ENGINE_INTAKE_CASING,
            Textures.STABLE_TITANIUM_CASING,
            Textures.LARGE_COMBUSTION_ENGINE_OVERLAY,
            false),

    EXTREME("extreme_combustion_engine", GTValues.IV,
            MetalCasingType.TUNGSTENSTEEL_ROBUST,
            TurbineCasingType.TUNGSTENSTEEL_GEARBOX,
            MultiblockCasingType.EXTREME_ENGINE_INTAKE_CASING,
            Textures.ROBUST_TUNGSTENSTEEL_CASING,
            Textures.EXTREME_COMBUSTION_ENGINE_OVERLAY,
            true);

    // Registration Data
    private final String name;

    // Tier Data
    private final int tier;

    // Structure Data
    private final MetalCasingType casingType;
    private final TurbineCasingType gearboxType;
    private final MultiblockCasingType intakeType;

    // Rendering Data
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;

    // Behavioral Data
    private final boolean isExtreme;

    LargeCombustionEngineType(String name, int tier,
                              MetalCasingType casingType, TurbineCasingType gearboxType,
                              MultiblockCasingType intakeType,
                              ICubeRenderer casingRenderer, ICubeRenderer frontOverlay,
                              boolean isExtreme) {
        this.name = name;
        this.tier = tier;
        this.casingType = casingType;
        this.gearboxType = gearboxType;
        this.intakeType = intakeType;
        this.casingRenderer = casingRenderer;
        this.frontOverlay = frontOverlay;
        this.isExtreme = isExtreme;
    }

    public String getName() {
        return name;
    }

    public int getTier() {
        return tier;
    }

    public boolean isExtreme() {
        return isExtreme;
    }

    public IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(casingType);
    }

    public IBlockState getGearboxState() {
        return MetaBlocks.TURBINE_CASING.getState(gearboxType);
    }

    public IBlockState getIntakeState() {
        return MetaBlocks.MULTIBLOCK_CASING.getState(intakeType);
    }

    public ICubeRenderer getCasingRenderer() {
        return casingRenderer;
    }

    public ICubeRenderer getFrontOverlay() {
        return frontOverlay;
    }
}
