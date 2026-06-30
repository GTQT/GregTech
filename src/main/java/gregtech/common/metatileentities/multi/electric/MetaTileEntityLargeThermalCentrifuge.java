package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
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

public class MetaTileEntityLargeThermalCentrifuge extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_thermal_centrifuge", () ->
            DeclarativePatternBuilder.start()
                    .aisle("#XXX#", "#XXX#", "#####", "#####", "#####", "#####", "#XXX#", "#XXX#")
                    .aisle("XXXXX", "XCCCX", "X#W#X", "##F##", "##F##", "X#W#X", "XCCCX", "XXXXX")
                    .aisle("XXXXX", "XCCCX", "XW#WX", "XF#FX", "XF#FX", "XW#WX", "XCCCX", "XXXXX")
                    .aisle("XXXXX", "XCCCX", "X#W#X", "##F##", "##F##", "X#W#X", "XCCCX", "XXXXX")
                    .aisle("#XXX#", "#XSX#", "#####", "#####", "#####", "#####", "#XXX#", "#XXX#")
                    .self('S', MetaTileEntityLargeThermalCentrifuge.class)
                    .casing('X', getCasingState())
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .threadHatch()
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .block('C', getCasingState2())
                    .tieredCasing('W', GTCasingGroups.heatingCoils().group())
                    .withChannel(GTCasingGroups.heatingCoils().channel())
                    .frames('F', Materials.RedSteel)
                    .air('A')
                    .any('#')
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeThermalCentrifuge(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.THERMAL_CENTRIFUGE_RECIPES);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING
                .getState(BlockLargeMultiblockCasing.CasingType.THERMAL_PROCESSING_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.STEEL_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeThermalCentrifuge(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.THERMAL_PROCESS_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_THERMAL_CENTRIFUGE_OVERLAY;
    }
}
