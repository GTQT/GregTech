package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.IDistillationTower;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.DistillationTowerLogicHandler;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.StructurePieceKey;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Distillation Tower multiblock controller.
 * Uses the new {@link StructureDefinition} system via
 * {@link DeclarativePatternBuilder#buildStructureDefinition()}.
 */
public class MetaTileEntityDistillationTower extends RecipeMapMultiblockController implements IDistillationTower {

    /** Piece name for the repeatable body section */
    private static final String PIECE_BODY = "body";
    private static final StructurePieceKey BODY_PIECE = StructurePieceKey.of(PIECE_BODY);

    /** Structure definition registered via TemplatePool for soft-reference caching */
    private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:distillation_tower", () ->
                    DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                            .piece("bottom")
                                .aisle("YSY", "YYY", "YYY")
                            .repeatablePiece(PIECE_BODY, 1, 11)
                                .aisle("XXX", "X#X", "XXX")
                                .withAisleChannel(GTStructureChannels.STRUCTURE_HEIGHT.getName())
                            .piece("top")
                                .aisle("XXX", "XXX", "XXX")
                            .self('S', MetaTileEntityDistillationTower.class)
                            .air('#')
                            .casing('Y', getCasingState())
                                .optionalItemOutput(1)
                                .energyInput(1, 3)
                                .fluidInput(1)
                            .casing('X', getCasingState())
                                .custom(Elements.abilitiesPerLayer(0, 1, 1, MultiblockAbility.EXPORT_FLUIDS), 11)
                                .maintenance()
                            .buildStructureDefinition()
    );

    protected DistillationTowerLogicHandler handler;

    @SuppressWarnings("unused") // backwards compatibility
    public MetaTileEntityDistillationTower(ResourceLocation metaTileEntityId) {
        this(metaTileEntityId, false);
    }

    public MetaTileEntityDistillationTower(ResourceLocation metaTileEntityId, boolean useAdvHatchLogic) {
        super(metaTileEntityId, RecipeMaps.DISTILLATION_RECIPES);
        if (useAdvHatchLogic) {
            this.recipeMapWorkable = new DistillationTowerRecipeLogic(this);
            this.handler = new DistillationTowerLogicHandler(this);
        } else this.handler = null;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityDistillationTower(metaTileEntityId, this.handler != null);
    }

    /**
     * Used if MultiblockPart Abilities need to be sorted a certain way, like
     * Distillation Tower and Assembly Line. <br>
     * <br>
     * There will be <i>consequences</i> if this is changed. Make sure to set the logic handler to one with
     * a properly overriden {@link DistillationTowerLogicHandler#determineOrderedFluidOutputs()}
     */
    @Override
    protected Function<BlockPos, Integer> multiblockPartSorter() {
        return RelativeDirection.UP.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped());
    }

    /**
     * Whether this multi can be rotated or face upwards. <br>
     * <br>
     * There will be <i>consequences</i> if this returns true. Make sure to set the logic handler to one with
     * a properly overriden {@link DistillationTowerLogicHandler#determineOrderedFluidOutputs()}
     */
    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        if (this.handler == null) return;

        // Determine layer count from the body piece repeat count. The structure
        // is always multi-piece (top / body / bottom), so the multiblockState
        // single-piece path has been removed — it was dead code after the
        // aisleRepeatable → repeatablePiece migration.
        handler.determineLayerCountFromReps(formed.getPieceRepeat(BODY_PIECE, 0));
        handler.determineOrderedFluidOutputs();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        if (this.handler != null) handler.invalidate();
    }

    @Nullable
    @Override
    protected StructureDefinition createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean allowSameFluidFillForOutputs() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    protected static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.DISTILLATION_TOWER_OVERLAY;
    }

    @Override
    public int getFluidOutputLimit() {
        if (this.handler != null) return this.handler.getLayerCount();
        else return super.getFluidOutputLimit();
    }

    protected class DistillationTowerRecipeLogic extends MultiblockRecipeLogic {

        public DistillationTowerRecipeLogic(MetaTileEntityDistillationTower tileEntity) {
            super(tileEntity);
        }

        @Override
        protected void outputRecipeOutputs() {
            GTTransferUtils.addItemsToItemHandler(getOutputInventory(), false, itemOutputs);
            handler.applyFluidToOutputs(fluidOutputs, true);
        }

        @Override
        protected boolean checkOutputSpaceFluids(@NotNull Recipe recipe, @NotNull IMultipleTankHandler exportFluids) {
            // We have already trimmed fluid outputs at this time
            if (!metaTileEntity.canVoidRecipeFluidOutputs() &&
                    !handler.applyFluidToOutputs(recipe.getAllFluidOutputs(), false)) {
                this.isOutputsFull = true;
                return false;
            }
            return true;
        }

        @Override
        protected IMultipleTankHandler getOutputTank() {
            return handler.getFluidTanks();
        }
    }
}
