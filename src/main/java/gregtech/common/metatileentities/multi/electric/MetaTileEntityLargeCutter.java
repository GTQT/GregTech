package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GCYMValues;
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
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeCutter extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:large_cutter", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("XXXXXXX", "XXXXXXX", "XXXXXXX", "##XXXXX")
                            .aisle("XXXXXXX", "XAXCCCX", "XXXAAAX", "##XXXXX")
                            .aisle("XXXXXXX", "XXXCCCX", "XXXAAAX", "##XXXXX")
                            .aisle("XXXXXXX", "CSCGGGX", "XXXGGGX", "##XXXXX")
                            .self('S', MetaTileEntityLargeCutter.class)
                            .casing('X', getCasingState())
                            .energyInput(1, 2)
                            .tieredHatch()
                            .parallelHatch()
                            .threadHatch()
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

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.CUTTER_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.TEMPERED_GLASS);
    }

    public static IBlockState getCasingState3() {
        return MetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.SLICING_BLADES);
    }

    private static @NotNull RecipeMap<?> @NotNull [] determineRecipeMaps() {
        RecipeMap<?> slicerMap = RecipeMap.getByName("slicer");
        if (Loader.isModLoaded(GCYMValues.GTFO_MODID) && slicerMap != null) {
            return new RecipeMap<?>[] { RecipeMaps.CUTTER_RECIPES, RecipeMaps.LATHE_RECIPES,
                    RecipeMaps.POLISHER_RECIPES, RecipeMaps.SAWMILL_RECIPES, slicerMap };
        }
        return new RecipeMap<?>[] { RecipeMaps.CUTTER_RECIPES, RecipeMaps.LATHE_RECIPES, RecipeMaps.POLISHER_RECIPES,
                RecipeMaps.SAWMILL_RECIPES };
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
        return Textures.CUTTER_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_CUTTER_OVERLAY;
    }
}
