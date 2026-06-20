package gregtech.common.metatileentities.multiblock.standard;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
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
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeAutoclave extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_autoclave", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("XXX", "XCX", "XXX")
                    .aisle("XXX", "XCX", "XXX")
                    .aisle("XXX", "XCX", "XXX")
                    .aisle("XXX", "XSX", "XXX")
                    .where('S', selfPredicate(MetaTileEntityLargeAutoclave.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1,2)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .custom(threadCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('C', states(getCasingState2()))
                    .where('A', air())
                    .where('#', any())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeAutoclave(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.AUTOCLAVE_RECIPES);
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.STEEL_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeAutoclave(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.WATERTIGHT_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.LARGE_AUTOCLAVE_OVERLAY;
    }
}
