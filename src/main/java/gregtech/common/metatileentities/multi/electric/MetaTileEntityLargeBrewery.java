package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityLargeBrewery extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_brewer", () ->
            DeclarativePatternBuilder.start()
                    .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#", "#####")
                    .aisle("XXXXX", "XCCCX", "XAAAX", "XXAXX", "##X##")
                    .aisle("XXXXX", "XCPCX", "XAPAX", "XAPAX", "#XMX#")
                    .aisle("XXXXX", "XCCCX", "XAAAX", "XXAXX", "##X##")
                    .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#", "#####")
                    .self('S', MetaTileEntityLargeBrewery.class)
                    .casing('X', getCasingState())
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .overclockHatch()
                    .accelerationHatch()
                    .threadHatch()
                    .maintenance()
                    .preset(HatchPresets.STANDARD_IO)
                    .block('C', getCasingState2())
                    .block('P', getCasingState3())
                    .hatch('M', MultiblockAbility.MUFFLER_HATCH)
                    .air('A')
                    .any('#')
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeBrewery(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] { RecipeMaps.BREWING_RECIPES, RecipeMaps.FERMENTING_RECIPES,
                RecipeMaps.FLUID_HEATER_RECIPES });
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING
                .getState(BlockLargeMultiblockCasing.CasingType.CORROSION_PROOF_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.MOLYBDENUM_DISILICIDE_COIL);
    }

    public static IBlockState getCasingState3() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.STEEL_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeBrewery(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.CORROSION_PROOF_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_BREWERY_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }
}
