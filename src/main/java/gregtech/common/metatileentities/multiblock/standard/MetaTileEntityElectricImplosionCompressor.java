package gregtech.common.metatileentities.multiblock.standard;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.client.renderer.texture.GCYMTextures;
import gregtech.common.blocks.GCYMMetaBlocks;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class MetaTileEntityElectricImplosionCompressor extends GCYMRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:electric_implosion_compressor", () ->
            DeclarativePatternBuilder.start()
                    .aisle("               ", "F F         F F", "F F         F F", "F F         F F", "F F         F F", "F F         F F", "               ")
                    .aisle("F F         F F", "F F   FFF   F F", "FBF  FFAFF  FBF", "FBF  AAAAA  FBF", "FBF  FFAFF  FBF", "F F   FFF   F F", "F F         F F")
                    .aisle("F F         F F", "FBF  FFCFF  FBF", "F FFFEEEEEFFF F", "F FAA     AAF F", "F FFFEEEEEFFF F", "FBF  FFCFF  FBF", "F F         F F")
                    .aisle("F F         F F", "FBFFFFCCCFFFFBF", "F FBBBEDEBBBF F", "F F         F F", "F FBBBEDEBBBF F", "FBFFFFCCCFFFFBF", "F F         F F")
                    .aisle("F F         F F", "FBF  FFCFF  FBF", "F FFFEEEEEFFF F", "F FAA     AAF F", "F FFFEEEEEFFF F", "FBF  FFCFF  FBF", "F F         F F")
                    .aisle("F F         F F", "F F   F~F   F F", "FBF  FFAFF  FBF", "FBF  AAAAA  FBF", "FBF  FFAFF  FBF", "F F   FFF   F F", "F F         F F")
                    .aisle("               ", "F F         F F", "F F         F F", "F F         F F", "F F         F F", "F F         F F", "               ")
                    .where('~', selfPredicate(MetaTileEntityElectricImplosionCompressor.class))
                    .where('A', states(geGlassState()))
                    .where('B', states(getCasingState()))
                    .where('C', states(getPipeState()))
                    .where('D', frames(Materials.Naquadah))
                    .where('E', air())
                    .casing('F', CasingDefinition.simple(getStructureState()))
                    .energyInput(1,4)
                    .maintenance()
                    .preset(HatchPresets.STANDARD_IO)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .where(' ', any())
                    .buildStructureDefinition()
    );

    public MetaTileEntityElectricImplosionCompressor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.ELECTRIC_IMPLOSION_RECIPES);
    }

    private static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.TUNGSTENSTEEL_ROBUST);
    }

    private static IBlockState getStructureState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.NAQUADAH_REINFORCED_CASING);
    }

    private static IBlockState getPipeState() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    private static IBlockState geGlassState() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.TEMPERED_GLASS);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityElectricImplosionCompressor(this.metaTileEntityId);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.NAQUADAH_REINFORCED_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.ELECTRIC_IMPLOSION_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }
}
