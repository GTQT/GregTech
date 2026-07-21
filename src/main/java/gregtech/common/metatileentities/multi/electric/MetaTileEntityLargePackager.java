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
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargePackager extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:large_packager", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("XXX", "XXX", "XXX")
                            .aisle("XXX", "XAX", "XXX")
                            .aisle("XXX", "XAX", "XXX")
                            .aisle("XXX", "XAX", "XXX")
                            .aisle("XXX", "XSX", "XXX")
                            .self('S', MetaTileEntityLargePackager.class)
                            .casing('X', getCasingState())
                            .energyInput(1, 2)
                            .tieredHatch()
                            .parallelHatch()
                            .threadHatch()
                            .overclockHatch()
                            .accelerationHatch()
                            .preset(HatchPresets.STANDARD_IO)
                            .preset(HatchPresets.MUFFLER_IO)
                            .air('A')
                            .buildStructureDefinition()
    );

    public MetaTileEntityLargePackager(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] { RecipeMaps.PACKER_RECIPES, RecipeMaps.UNPACKER_RECIPES });
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargePackager(this.metaTileEntityId);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.SOLID_STEEL_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_PACKAGER_OVERLAY;
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean isTiered() {
        return false;
    }
}
