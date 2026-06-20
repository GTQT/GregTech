package gregicality.multiblocks.common.metatileentities.multiblock.standard;

import gregicality.multiblocks.api.GCYMValues;
import gregicality.multiblocks.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregicality.multiblocks.api.render.GCYMTextures;
import gregicality.multiblocks.common.block.GCYMMetaBlocks;
import gregicality.multiblocks.common.block.blocks.BlockLargeMultiblockCasing;
import gregicality.multiblocks.common.block.blocks.BlockUniqueCasing;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import org.jetbrains.annotations.NotNull;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityLargeCutter extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_cutter", () ->
            DeclarativePatternBuilder.start(FRONT, UP, RIGHT)
                    .aisle("XXXX", "XXXX", "XXXX", "####")
                    .aisle("XXXX", "SXAX", "XXXX", "####")
                    .aisle("XXXX", "XXXX", "XXXX", "XXXX")
                    .aisleRepeatable(3, 3, "XXXX", "GCCX", "GAAX", "XXXX")
                    .aisle("XXXX", "XXXX", "XXXX", "XXXX")
                    .where('S', selfPredicate(MetaTileEntityLargeCutter.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1, 2)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .custom(threadCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('G', states(getCasingState2()))
                    .where('C', states(getCasingState3()))
                    .where('A', air())
                    .where('#', any())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeCutter(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, determineRecipeMaps());
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.CUTTER_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.TEMPERED_GLASS);
    }

    private static IBlockState getCasingState3() {
        return GCYMMetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.SLICING_BLADES);
    }

    private static @NotNull RecipeMap<?> @NotNull [] determineRecipeMaps() {
        RecipeMap<?> slicerMap = RecipeMap.getByName("slicer");
        if (Loader.isModLoaded(GCYMValues.GTFO_MODID) && slicerMap != null) {
            return new RecipeMap<?>[]{RecipeMaps.CUTTER_RECIPES, RecipeMaps.LATHE_RECIPES, RecipeMaps.POLISHER_RECIPES, RecipeMaps.SAWMILL_RECIPES, slicerMap};
        }
        return new RecipeMap<?>[]{RecipeMaps.CUTTER_RECIPES, RecipeMaps.LATHE_RECIPES, RecipeMaps.POLISHER_RECIPES, RecipeMaps.SAWMILL_RECIPES};
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeCutter(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.CUTTER_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.LARGE_CUTTER_OVERLAY;
    }
}
