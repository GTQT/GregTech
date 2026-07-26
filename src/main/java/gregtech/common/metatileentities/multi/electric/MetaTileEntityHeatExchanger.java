package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.NoEnergyMultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.NoEnergyMultiblockController;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.textures.SCTextures;
import gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityHeatExchanger extends NoEnergyMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:heat_exchanger", () -> DeclarativePatternBuilder.start()
                    .aisle("CCC", "BCB", "ACA")
                    .aisleRepeated(7, "CCC", "CDC", "ACA")
                    .aisle("CCC", "BSB", "AEA")
                    .self('S', MetaTileEntityHeatExchanger.class)
                    .frames('A', Materials.Steel)
                    .casing('B', getCasingState())
                    .fluidInput(1, 3)
                    .fluidOutput(1, 3)
                    .done()
                    .casing('C', getCasingState())
                    .maintenance()
                    .done()
                    .block('D', MetaBlocks.BOILER_CASING.getState(BoilerCasingType.STEEL_PIPE))
                    .casing('E', getCasingState())
                    .itemInput(1, 3)
                    .done()
                    .buildStructureDefinition()
    );

    public static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.STEEL_SOLID);
    }

    public MetaTileEntityHeatExchanger(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.HEAT_EXCHANGER_RECIPES);
        this.recipeMapWorkable = new NoEnergyMultiblockRecipeLogic(this);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityHeatExchanger(metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.SOLID_STEEL_CASING;
    }

    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return SCTextures.HEAT_EXCHANGER_OVERLAY;
    }
}
