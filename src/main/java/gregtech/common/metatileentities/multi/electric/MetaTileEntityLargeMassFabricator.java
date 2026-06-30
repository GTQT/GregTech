package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockFusionCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeMassFabricator extends GCYMRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_mass_fabricator", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXXXX", "XGGGX", "XGVGX", "XGGGX", "XXXXX")
                    .aisle("XXXXX", "GAAAG", "GAKAG", "GAAAG", "XXXXX")
                    .aisle("XXVXX", "GAKAG", "VKKKV", "GAKAG", "XXVXX")
                    .aisle("XXXXX", "GAAAG", "GAKAG", "GAAAG", "XXXXX")
                    .aisle("XXXXX", "XGGGX", "XGSGX", "XGGGX", "XXXXX")
                    .self('S', MetaTileEntityLargeMassFabricator.class)
                    .casing('X', getCasingState())
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .block('G', getCasingState2())
                    .block('V', getCasingState3())
                    .block('K', getCasingState4())
                    .air('A')
                    .any('#')
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeMassFabricator(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.MASS_FABRICATOR_RECIPES);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.ATOMIC_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS);
    }

    public static IBlockState getCasingState3() {
        return MetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.HEAT_VENT);
    }

    public static IBlockState getCasingState4() {
        return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_COIL);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeMassFabricator(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.ATOMIC_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_MASS_FABRICATOR_OVERLAY;
    }
}
