package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.capability.IHeatingCoil;
import gregtech.api.capability.impl.HeatingCoilRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockDisplayText;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.GCYMRecipeMaps;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextComponentUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.BlockWireCoil;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityAlloyBlastSmelter extends RecipeMapMultiblockController implements IHeatingCoil {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:alloy_blast_smelter", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("#XXX#", "#CCC#", "#GGG#", "#CCC#", "#XXX#")
                            .aisle("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXXXX")
                            .aisle("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXMXX")
                            .aisle("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXXXX")
                            .aisle("#XSX#", "#CCC#", "#GGG#", "#CCC#", "#XXX#")
                            .self('S', MetaTileEntityAlloyBlastSmelter.class)
                            .casing('X', getCasingState())
                            .optionalEnergyInput(8)
                            .optionalLaserInput(1)
                            .maintenance()
                            .preset(HatchPresets.STANDARD_IO)
                            .tieredCasing('C', GTCasingGroups.heatingCoils().group())
                            .withChannel(GTCasingGroups.heatingCoils().channel())
                            .block('G', getCasingState2())
                            .hatch('M', MultiblockAbility.MUFFLER_HATCH)
                            .air('A')
                            .any('#')
                            .buildStructureDefinition()
    );
    private int blastFurnaceTemperature;

    public MetaTileEntityAlloyBlastSmelter(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GCYMRecipeMaps.ALLOY_BLAST_RECIPES);
        this.recipeMapWorkable = new HeatingCoilRecipeLogic(this);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(
                BlockLargeMultiblockCasing.CasingType.HIGH_TEMPERATURE_CASING);
    }

    public static IBlockState getCasingState2() {
        return MetaBlocks.UNIQUE_CASING.getState(BlockUniqueCasing.UniqueCasingType.HEAT_VENT);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityAlloyBlastSmelter(this.metaTileEntityId);
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        MultiblockDisplayText.builder(textList, isStructureFormed())
                .setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addCustom(tl -> {
                    // Coil heat capacity line
                    if (isStructureFormed()) {
                        ITextComponent heatString = TextComponentUtil.stringWithColor(
                                TextFormatting.RED,
                                TextFormattingUtil.formatNumbers(blastFurnaceTemperature) + "K");

                        tl.add(TextComponentUtil.translationWithColor(
                                TextFormatting.GRAY,
                                "gregtech.multiblock.blast_furnace.max_temperature",
                                heatString));
                    }
                })
                .addParallelsLine(recipeMapWorkable.getParallelLimit())
                .addWorkingStatusLine()
                .addProgressLine(recipeMapWorkable.getProgressPercent());
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



    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addBlast().build(this, tooltip);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.BLAST_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.ALLOY_BLAST_SMELTER_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public int getCurrentTemperature() {
        return this.blastFurnaceTemperature;
    }
}
