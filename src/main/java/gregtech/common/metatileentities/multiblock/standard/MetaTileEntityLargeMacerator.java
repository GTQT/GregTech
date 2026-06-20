package gregtech.common.metatileentities.multiblock.standard;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.client.renderer.texture.GCYMTextures;
import gregtech.common.blocks.GCYMMetaBlocks;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
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
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeMacerator extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_macerator", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXXXX", "XXXXX", "XXXXX", "XXXXX")
                    .aisle("XXXXX", "XCCCX", "XCCCX", "X###X")
                    .aisle("XXXXX", "XCCCX", "XCCCX", "X###X")
                    .aisle("XXXXX", "XCCCX", "XCCCX", "X###X")
                    .aisle("XXXXX", "XXSXX", "XXXXX", "XXXXX")
                    .where('S', selfPredicate(MetaTileEntityLargeMacerator.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1, 2)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .custom(threadCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('C', states(getCasingState2()))
                    .where('#', air())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeMacerator(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[]{
                RecipeMaps.MACERATOR_RECIPES,
                RecipeMaps.RECYCLER_RECIPES
        });
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.MACERATOR_CASING);
    }

    private static IBlockState getCasingState2() {
        return GCYMMetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.CRUSHING_WHEELS);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeMacerator(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.MACERATOR_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.LARGE_MACERATOR_OVERLAY;
    }
}
