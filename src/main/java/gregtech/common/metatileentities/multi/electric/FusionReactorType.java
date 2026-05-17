package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.mui.GTGuiTextures;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockFusionCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;

import com.cleanroommc.modularui.api.drawable.IDrawable;

public enum FusionReactorType implements IFusionReactorType {

    MK1(GTValues.LuV, "luv",
            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_CASING),
            MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS),
            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.SUPERCONDUCTOR_COIL),
            Textures.FUSION_TEXTURE,
            GTGuiTextures.FUSION_REACTOR_MK1_TITLE,
            1), // energy multiplier

    MK2(GTValues.ZPM, "zpm",
            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_CASING_MK2),
            MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS),
            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_COIL),
            Textures.FUSION_TEXTURE,
            GTGuiTextures.FUSION_REACTOR_MK2_TITLE,
            4),

    MK3(GTValues.UV, "uv",
            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_CASING_MK3),
            MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS),
            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_COIL),
            Textures.FUSION_TEXTURE,
            GTGuiTextures.FUSION_REACTOR_MK3_TITLE,
            16);

    private final int tier;
    private final String name;
    private final IBlockState casingState;
    private final IBlockState glassState;
    private final IBlockState coilState;
    private final ICubeRenderer baseRenderer;
    private final IDrawable uiTitle;
    private final int energyMultiplier;

    FusionReactorType(int tier, String name, IBlockState casingState, IBlockState glassState, IBlockState coilState,
                      ICubeRenderer baseRenderer, IDrawable uiTitle, int energyMultiplier) {
        this.tier = tier;
        this.name = name;
        this.casingState = casingState;
        this.glassState = glassState;
        this.coilState = coilState;
        this.baseRenderer = baseRenderer;
        this.uiTitle = uiTitle;
        this.energyMultiplier = energyMultiplier;
    }

    @Override
    public String getName() {return name;}

    @Override
    public int getTier() {return tier;}

    @Override
    public IBlockState getCasingState() {return casingState;}

    @Override
    public IBlockState getGlassState() {
        return glassState;
    }

    @Override
    public IBlockState getCoilState() {return coilState;}

    @Override
    public ICubeRenderer getBaseRenderer() {return baseRenderer;}

    @Override
    public IDrawable getUITitle() {return uiTitle;}

    @Override
    public int getEnergyMultiplier() {return energyMultiplier;}

}
