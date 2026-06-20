package gregtech.common.metatileentities.multiblock.standard;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.client.renderer.texture.GCYMTextures;
import gregtech.common.blocks.GCYMMetaBlocks;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeArcFurnace extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_arc_furnace", () ->
            DeclarativePatternBuilder.start()
                    .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#")
                    .aisle("XXXXX", "XCACX", "XCACX", "XXXXX")
                    .aisle("XXXXX", "XAAAX", "XAAAX", "XXMXX")
                    .aisle("XXXXX", "XACAX", "XACAX", "XXXXX")
                    .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#")
                    .where('S', selfPredicate(MetaTileEntityLargeArcFurnace.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1,4)
                    .maintenance()
                    .preset(HatchPresets.STANDARD_IO)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .custom(threadCasing(), 1)
                    .where('C', states(getCasingState2()))
                    .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                    .where('A', air())
                    .where('#', any())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeArcFurnace(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.ARC_FURNACE_RECIPES);
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING
                .getState(BlockLargeMultiblockCasing.CasingType.HIGH_TEMPERATURE_CASING);
    }

    private static IBlockState getCasingState2() {
        return GCYMMetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.MOLYBDENUM_DISILICIDE_COIL);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeArcFurnace(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.BLAST_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.LARGE_ARC_FURNACE_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }
}
