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
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeElectrolyzer extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_electrolyzer", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXXXX", "XXXXX", "XXXXX")
                    .aisle("XXXXX", "XCCCX", "XCCCX")
                    .aisle("XXXXX", "XCCCX", "XCCCX")
                    .aisle("XXXXX", "XXSXX", "XXXXX")
                    .self('S', MetaTileEntityLargeElectrolyzer.class)
                    .casing('X', getCasingState())
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .overclockHatch()
                    .accelerationHatch()
                    .threadHatch()
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .block('C', getCasingState2())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeElectrolyzer(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] {
                RecipeMaps.ELECTROLYZER_RECIPES,
                RecipeMaps.LIGHTNING_PROCESSOR_RECIPES
        });
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING
                .getState(BlockLargeMultiblockCasing.CasingType.NONCONDUCTING_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.ELECTROLYTIC_CELL);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeElectrolyzer(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.NONCONDUCTING_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_ELECTROLYZER_OVERLAY;
    }
}
