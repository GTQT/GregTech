package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.IDistillationTower;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.DistillationTowerLogicHandler;
import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
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
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.TextComponentUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Requires an unfortunate amount of copy-pasted logic from
 * {@link gregtech.common.metatileentities.multi.electric.MetaTileEntityDistillationTower}
 */
public class MetaTileEntityLargeDistillery extends GCYMRecipeMapMultiblockController implements IDistillationTower {

    private static final String PIECE_BODY = "body";
    private static final StructurePieceKey BODY_PIECE = StructurePieceKey.of(PIECE_BODY);
    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:large_distillery", () ->
                    DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                            .piece("bottom")
                            .aisle("#YSY#", "YYYYY", "YYYYY", "YYYYY", "#YYY#")
                            .piece("controller")
                            .aisle("#YYY#", "YAAAY", "YAAAY", "YAAAY", "#YYY#")
                            .repeatablePiece(PIECE_BODY, 1, 12)
                            .aisle("##X##", "#XAX#", "XAPAX", "#XAX#", "##X##")
                            .withAisleChannel(GTStructureChannels.STRUCTURE_HEIGHT.getName())
                            .piece("top")
                            .aisle("#####", "#ZZZ#", "#ZCZ#", "#ZZZ#", "#####")
                            .self('S', MetaTileEntityLargeDistillery.class)
                            .casing('Y', getCasingState())
                            .energyInput(1,2)
                            .fluidInput(1,2)
                            .itemInput(1,2)
                            .tieredHatch()
                            .parallelHatch()
                            .maintenance()
                            .casing('X', getCasingState())
                            .custom(Elements.abilitiesPerLayer(0, 1, 1, MultiblockAbility.EXPORT_FLUIDS), 12)
                            .where('Z',states(getCasingState()))
                            .where('P', states(getCasingState2()))
                            .where('C', abilities(MultiblockAbility.MUFFLER_HATCH))
                            .where('A', air())
                            .where('#', any())
                            .buildStructureDefinition()
    );
    protected final DistillationTowerLogicHandler handler;

    public MetaTileEntityLargeDistillery(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] { RecipeMaps.DISTILLATION_RECIPES, RecipeMaps.DISTILLERY_RECIPES });
        this.recipeMapWorkable = new LargeDistilleryRecipeLogic(this);
        this.handler = new DistillationTowerLogicHandler(this);
    }

    private static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.STEEL_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeDistillery(this.metaTileEntityId);
    }

    /**
     * Used if MultiblockPart Abilities need to be sorted a certain way, like Distillation Tower and Assembly Line.
     * <br>
     * <br>
     * There will be <i>consequences</i> if this is changed. Make sure to set the logic handler to one with a properly
     * overriden {@link DistillationTowerLogicHandler#determineOrderedFluidOutputs()}
     */
    @Override
    protected Function<BlockPos, Integer> multiblockPartSorter() {
        return RelativeDirection.UP.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped());
    }

    /**
     * Whether this multi can be rotated or face upwards. <br>
     * <br>
     * There will be <i>consequences</i> if this returns true. Make sure to set the logic handler to one with a properly
     * overriden {@link DistillationTowerLogicHandler#determineOrderedFluidOutputs()}
     */
    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        if (isStructureFormed()) {
            FluidStack stackInTank = importFluids.drain(Integer.MAX_VALUE, false);
            if (stackInTank != null && stackInTank.amount > 0) {
                ITextComponent fluidName = TextComponentUtil.setColor(GTUtility.getFluidTranslation(stackInTank),
                        TextFormatting.AQUA);
                textList.add(TextComponentUtil.translationWithColor(
                        TextFormatting.GRAY,
                        "gregtech.multiblock.distillation_tower.distilling_fluid",
                        fluidName));
            }
        }
        super.addDisplayText(textList);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        if (this.handler == null) return;
        handler.determineLayerCountFromReps(formed.getPieceRepeat(BODY_PIECE, 0));
        handler.determineOrderedFluidOutputs();
    }

    protected boolean usesAdvHatchLogic() {
        return getCurrentRecipeMap() == RecipeMaps.DISTILLATION_RECIPES;
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        if (usesAdvHatchLogic())
            this.handler.invalidate();
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean allowSameFluidFillForOutputs() {
        return !usesAdvHatchLogic();
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.WATERTIGHT_CASING;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_DISTILLERY_OVERLAY;
    }

    @Override
    public int getFluidOutputLimit() {
        if (usesAdvHatchLogic()) return this.handler.getLayerCount();
        else return super.getFluidOutputLimit();
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean isTiered() {
        return false;
    }

    private class LargeDistilleryRecipeLogic extends GCYMMultiblockRecipeLogic {

        public LargeDistilleryRecipeLogic(RecipeMapMultiblockController tileEntity) {
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
