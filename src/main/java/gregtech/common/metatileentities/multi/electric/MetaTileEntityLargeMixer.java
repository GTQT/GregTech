package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeMixer extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_mixer", () ->
            DeclarativePatternBuilder.start()
                    .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#", "#XXX#", "##F##")
                    .aisle("XXXXX", "XACAX", "XAAAX", "XACAX", "XAAAX", "##F##")
                    .aisle("XXXXX", "XCPCX", "XAPAX", "XCPCX", "XAPAX", "FFFFF")
                    .aisle("XXXXX", "XACAX", "XAAAX", "XACAX", "XAAAX", "##F##")
                    .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#", "#XXX#", "##F##")
                    .self('S', MetaTileEntityLargeMixer.class)
                    .casing('X', getCasingState())
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .threadHatch()
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .block('P', getCasingState2())
                    .block('C', getCasingState3())
                    .frames('F', Materials.HastelloyX)
                    .air('A')
                    .any('#')
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeMixer(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] { RecipeMaps.MIXER_RECIPES, RecipeMaps.LARGE_MIXER_RECIPES });
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.MIXER_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    public static IBlockState getCasingState3() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeMixer(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.MIXER_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_MIXER_OVERLAY;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }
}
