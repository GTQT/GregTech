package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
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
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityLargeSifter extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:large_sifter", () ->
                    DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                            .aisle("#X#X#", "XXXXX", "#XXX#", "XXXXX", "#X#X#")
                            .aisle("#X#X#", "XAXAX", "#XXX#", "XAXAX", "#X#X#")
                            .aisle("#XXX#", "XCCCX", "XCCCX", "XCCCX", "#XXX#")
                            .aisle("#XSX#", "XCCCX", "XCCCX", "XCCCX", "#XXX#")
                            .aisle("#XXX#", "X###X", "X###X", "X###X", "#XXX#")
                            .self('S', MetaTileEntityLargeSifter.class)
                            .casing('X', CasingDefinition.simple(getCasingState()))
                            .energyInput(1, 2)
                            .tieredHatch()
                            .parallelHatch()
                            .threadHatch()
                            .preset(HatchPresets.STANDARD_IO)
                            .preset(HatchPresets.MUFFLER_IO)
                            .where('C', states(getCasingState2()))
                            .where('A', air())
                            .where('#', any())
                            .buildStructureDefinition()
    );

    public MetaTileEntityLargeSifter(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, determineRecipeMaps());
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING
                .getState(BlockLargeMultiblockCasing.CasingType.VIBRATION_SAFE_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.MULTIBLOCK_CASING.getState(BlockMultiblockCasing.MultiblockCasingType.GRATE_CASING);
    }

    private static @NotNull RecipeMap<?> @NotNull [] determineRecipeMaps() {
        RecipeMap<?> sieveMap = RecipeMap.getByName("electric_sieve");
        if (sieveMap != null) {
            return new RecipeMap<?>[] { RecipeMaps.SIFTER_RECIPES, sieveMap };
        }
        return new RecipeMap<?>[] { RecipeMaps.SIFTER_RECIPES };
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeSifter(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.VIBRATION_SAFE_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_SIFTER_OVERLAY;
    }
}
