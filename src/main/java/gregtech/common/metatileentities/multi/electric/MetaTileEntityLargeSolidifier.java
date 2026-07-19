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
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityLargeSolidifier extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_solidifier", () ->
            DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                    .aisle("#XXX#", "XXXXX", "XXXXX", "XXXXX", "#XXX#")
                    .aisle("#XSX#", "XCCCX", "XAAAX", "XCACX", "#XXX#")
                    .aisle("#XXX#", "XCACX", "XAAAX", "XCACX", "#XXX#")
                    .aisle("#XXX#", "XXXXX", "XXXXX", "XXXXX", "#XXX#")
                    .self('S', MetaTileEntityLargeSolidifier.class)
                    .casing('X', getCasingState())
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .threadHatch()
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .block('C', getCasingState2())
                    .air('A')
                    .any('#')
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeSolidifier(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] {
                RecipeMaps.FLUID_SOLIDFICATION_RECIPES,
                RecipeMaps.TOOL_CASTER_RECIPES,
                RecipeMaps.LAMINATOR_RECIPES,
                RecipeMaps.VULCANIZING_PRESS_RECIPES
        });
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.STEEL_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeSolidifier(this.metaTileEntityId);
    }

    @Override
    @NotNull
    public EnumFacing getPreviewFrontFacing() {
        return EnumFacing.SOUTH;
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
        return Textures.LARGE_SOLIDIFIER_OVERLAY;
    }
}
