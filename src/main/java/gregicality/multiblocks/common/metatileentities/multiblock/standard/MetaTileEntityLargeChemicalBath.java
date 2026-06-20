package gregicality.multiblocks.common.metatileentities.multiblock.standard;

import gregicality.multiblocks.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregicality.multiblocks.api.render.GCYMTextures;
import gregicality.multiblocks.common.block.GCYMMetaBlocks;
import gregicality.multiblocks.common.block.blocks.BlockLargeMultiblockCasing;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.pattern.FluidTraceability.*;
import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityLargeChemicalBath extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_chemical_bath", () ->
            DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                    .aisle("XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
                    .aisle("XXSXX", "XCCCX", "XAAAX", "XAAAX", "XAAAX", "XCCCX", "XXXXX")
                    .aisle("XXXXX", "XAAAX", "XAAAX", "XAAAX", "XAAAX", "XAAAX", "XXXXX")
                    .where('S', selfPredicate(MetaTileEntityLargeChemicalBath.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1, 2)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .custom(threadCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('C', states(getCasingState2()))
                    .where('A', fluidElement(FluidRegistry.WATER))
                    .where('#', any())
                    .buildStructureDefinition()
    );
    private boolean waterFilled;
    private List<BlockPos> waterPositions;

    public MetaTileEntityLargeChemicalBath(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[]{RecipeMaps.CHEMICAL_BATH_RECIPES, RecipeMaps.ORE_WASHER_RECIPES});
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.TITANIUM_PIPE);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formAdvancedRecipeMapStructure(formed);
        List<BlockPos> pendingPositions = formed.getAggregate(FLUID_BLOCK_POSITIONS);
        this.waterPositions = pendingPositions == null ? new ArrayList<>() : new ArrayList<>(pendingPositions);
        this.waterFilled = waterPositions.isEmpty();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.waterPositions = null; // Clear water fill data when the structure is invalidated
        this.waterFilled = false;
    }

    @Override
    protected void updateFormedValid() {
        super.updateFormedValid();
        if (!waterFilled && getOffsetTimer() % 5 == 0) {
            fillFluid(this, this.waterPositions, FluidRegistry.WATER);
            if (this.waterPositions.isEmpty()) {
                this.waterFilled = true;
            }
        }
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || !waterFilled;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeChemicalBath(this.metaTileEntityId);
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
        return GCYMTextures.LARGE_CHEMICAL_BATH_OVERLAY;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }
}
