package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
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
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargePolymerization extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:large_polymerization", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("F   F", "XXXXX", "XXXXX", "XXXXX")
                            .aisle("     ", "XXXXX", "XPPPX", "XXXXX")
                            .aisle("F   F", "XXXXX", "XSXXX", "XXXXX")
                            .self('S', MetaTileEntityLargePolymerization.class)
                            .casing('X', CasingDefinition.simple(getCasingState()))
                            .energyInput(1, 2)
                            .tieredHatch()
                            .parallelHatch()
                            .threadHatch()
                            .preset(HatchPresets.STANDARD_IO)
                            .preset(HatchPresets.MUFFLER_IO)
                            .where('P', states(getCasingState2()))
                            .where('F', states(getFrameState()))
                            .buildStructureDefinition()
    );

    public MetaTileEntityLargePolymerization(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.POLYMERIZATION_RECIPES);
    }

    private static IBlockState getFrameState() {
        return MetaBlocks.FRAMES.get(Materials.WatertightSteel).getBlock(Materials.WatertightSteel);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.TITANIUM_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargePolymerization(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.WATERTIGHT_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.MEGA_CHEMICAL_REACTOR;
    }
}
