package gregicality.multiblocks.common.metatileentities.multiblock.standard;

import gregicality.multiblocks.api.capability.impl.GCYMHeatCoilRecipeLogic;
import gregicality.multiblocks.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregicality.multiblocks.api.recipes.GCYMRecipeMaps;
import gregicality.multiblocks.api.render.GCYMTextures;
import gregicality.multiblocks.common.block.GCYMMetaBlocks;
import gregicality.multiblocks.common.block.blocks.BlockLargeMultiblockCasing;
import gregicality.multiblocks.common.block.blocks.BlockUniqueCasing;
import gregtech.api.GTValues;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.capability.IHeatingCoil;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.*;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.tooltips.InformationHandler;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockWireCoil;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

//这是与GCYM的转底炉 巨冰箱同一系列的设备
//此系列设备不给多线程
public class MetaTileEntityMegaAlloyBlastSmelter extends GCYMRecipeMapMultiblockController implements IHeatingCoil {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:mega_alloy_blast_smelter", () ->
            DeclarativePatternBuilder.start()
                    .aisle("   BBBBB   ", "   CCCCC   ", "   CCCCC   ", "   CCCCC   ", "   BBBBB   ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ")
                    .aisle("  BDDDDDB  ", "  G     G  ", "  G     G  ", "  G     G  ", "  BDDDDDB  ", "   DDDDD   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   DDDDD   ", "   DDDDD   ", "           ")
                    .aisle(" BDDHHHDDB ", " G       G ", " G       G ", " G       G ", " BDDHHHDDB ", "  DVVVVVD  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  DDDDDDD  ", "  DDDDDDD  ", "   DDDDD   ")
                    .aisle("BDDDDDDDDDB", "C  VWWWV  C", "C  VBBBV  C", "C  VBBBV  C", "BDDDDDDDDDB", " DVVVVVVVD ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " DDDDDDDDD ", " DDDDDDDDD ", "  DDDDDDD  ")
                    .aisle("BDHDDDDDHDB", "C  W   W  C", "C  B   B  C", "C  B   B  C", "BDHDDDDDHDB", " DVVVVVVVD ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " DDDDDDDDD ", " DDDDDDDDD ", "  DDDDDDD  ")
                    .aisle("BDHDDDDDHDB", "C  W V W  C", "C  B V B  C", "C  B V B  C", "BDHDDVDDHDB", " DVVVVVVVD ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " GW  V  WG ", " DDDDDDDDD ", " DDDDDDDDD ", "  DDDMDDD  ")
                    .aisle("BDHDDDDDHDB", "C  W   W  C", "C  B   B  C", "C  B   B  C", "BDHDDDDDHDB", " DVVVVVVVD ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " GW     WG ", " DDDDDDDDD ", " DDDDDDDDD ", "  DDDDDDD  ")
                    .aisle("BDDDDDDDDDB", "C  VWWWV  C", "C  VBBBV  C", "C  VBBBV  C", "BDDDDDDDDDB", " DVVVVVVVD ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " GWW   WWG ", " DDDDDDDDD ", " DDDDDDDDD ", "  DDDDDDD  ")
                    .aisle(" BDDHHHDDB ", " G       G ", " G       G ", " G       G ", " BDDHHHDDB ", "  DVVVVVD  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  GWWWWWG  ", "  DDDDDDD  ", "  DDDDDDD  ", "   DDDDD   ")
                    .aisle("  BDDDDDB  ", "  G     G  ", "  G     G  ", "  G     G  ", "  BDDDDDB  ", "   DDDDD   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   GGGGG   ", "   DDDDD   ", "   DDDDD   ", "           ")
                    .aisle("   BBBBB   ", "   CCCCC   ", "   CCSCC   ", "   CCCCC   ", "   BBBBB   ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ")
                    .where('S', selfPredicate(MetaTileEntityMegaAlloyBlastSmelter.class))
                    .where('B', states(getUniqueCasingState()))
                    .where('D', states(getCasingState()))
                    .where('G', states(getGlassState()))
                    .where('H', states(getUniqueCasingState()))
                    .where('V', states(getVentState()))
                    .tieredCasing('W', GTCasingGroups.heatingCoils().group())
                    .withChannel(GTCasingGroups.heatingCoils().channel())
                    .casing('C', CasingDefinition.simple(getCasingState()))
                    .optionalEnergyInput(8)
                    .optionalLaserInput(1)
                    .maintenance()
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                    .where(' ', any())
                    .buildStructureDefinition()
    );

    private int blastFurnaceTemperature;

    public MetaTileEntityMegaAlloyBlastSmelter(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[]{
                GCYMRecipeMaps.ALLOY_BLAST_RECIPES,
                RecipeMaps.ALLOY_SMELTER_RECIPES
        });
        this.recipeMapWorkable = new GCYMHeatCoilRecipeLogic(this);
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.HIGH_TEMPERATURE_CASING);
    }

    private static IBlockState getVentState() {
        return GCYMMetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.HEAT_VENT);
    }

    private static IBlockState getUniqueCasingState() {
        return GCYMMetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.HEAT_VENT);
    }

    private static IBlockState getGlassState() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS);
    }

    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityMegaAlloyBlastSmelter(this.metaTileEntityId);
    }

    @Override
    public void addCustomCapacity(KeyManager keyManager, UISyncer syncer) {
        if (isStructureFormed()) {
            var heatString = KeyUtil.number(TextFormatting.RED,
                    syncer.syncInt(getCurrentTemperature()), "K");

            keyManager.add(KeyUtil.lang(TextFormatting.GRAY,
                    "gregtech.multiblock.blast_furnace.max_temperature", heatString));
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip, boolean advanced) {
        InformationHandler.topTooltips("最强合金王", tooltip);
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addBlast().addLaser().build(this, tooltip);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        // Retrieve coil stats from the channel's matched ICasing
        ICasing matchedCoil = GTCasingGroups.heatingCoils().channel().getMatchedCasing(formed);
        IHeatingCoilBlockStats type = matchedCoil != null ?
                matchedCoil.getPayloadAs(IHeatingCoilBlockStats.class) : null;
        if (type == null) {
            type = BlockWireCoil.CoilType.CUPRONICKEL;
        }
        this.blastFurnaceTemperature = type.getCoilTemperature();
        // the subtracted tier gives the starting level (exclusive) of the +100K heat bonus
        this.blastFurnaceTemperature += 100 *
                Math.max(0, GTUtility.getFloorTierByVoltage(getEnergyContainer().getInputVoltage()) - GTValues.MV);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.blastFurnaceTemperature = 0;
    }

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        int recipeTemp = recipe.getProperty(TemperatureProperty.getInstance(), 0);
        if (this.blastFurnaceTemperature >= recipeTemp)
            return true;
        recipeMapWorkable.setWhyFailed("线圈温度过低，配方需求至少 " + recipeTemp + " K温度");
        return false;
    }

    public int getCurrentTemperature() {
        return this.blastFurnaceTemperature;
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.BLAST_CASING;
    }

    @NotNull
    @Override
    protected OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.ALLOY_BLAST_SMELTER_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }
}
