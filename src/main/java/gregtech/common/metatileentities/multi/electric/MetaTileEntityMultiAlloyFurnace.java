package gregtech.common.metatileentities.multi.electric;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;

import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.BlockWireCoil.CoilType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;

import static gregtech.api.recipes.logic.OverclockingLogic.standardOC;

public class MetaTileEntityMultiAlloyFurnace extends RecipeMapMultiblockController {

    private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register("gregtech:multi_alloy_furnace", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXX", "CCC", "XXX")
                    .aisle("XXX", "C#C", "XMX")
                    .aisle("XSX", "CCC", "XXX")
                    .where('S', selfPredicate(MetaTileEntityMultiAlloyFurnace.class))
                    .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                    .where('#', air())
                    .casing('X', CasingDefinition.simple(
                            MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF)))
                        .preset(HatchPresets.ELECTRIC_STANDARD)
                    .tieredCasing('C', GTCasingGroups.heatingCoils().group())
                        .withChannel(GTCasingGroups.heatingCoils().channel())
                    .buildTemplate()
    );

    protected int heatingCoilLevel;
    protected int heatingCoilDiscount;

    public MetaTileEntityMultiAlloyFurnace(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.ALLOY_SMELTER_RECIPES);
        this.recipeMapWorkable = new MultiSmelterWorkable(this);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMultiAlloyFurnace(metaTileEntityId);
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addCustom((richText, syncer) -> {
                    if (!isStructureFormed()) return;

                    int discount = syncer.syncInt(heatingCoilDiscount);
                    if (discount > 1) {
                        IKey coilDiscount = KeyUtil.number(TextFormatting.AQUA,
                                (long) (100.0 / discount), "%");

                        IKey base = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.multi_furnace.heating_coil_discount",
                                coilDiscount);

                        IKey hoverText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.multi_furnace.heating_coil_discount_hover");

                        richText.add(KeyUtil.setHover(base, hoverText));
                    }

                    int pLimit = syncer.syncInt(recipeMapWorkable.getParallelLimit());
                    if (pLimit > 0) {
                        IKey parallels = KeyUtil.number(TextFormatting.DARK_PURPLE, pLimit);

                        IKey bodyText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.parallel",
                                parallels);

                        IKey hoverText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.multi_furnace.parallel_hover");

                        richText.add(KeyUtil.setHover(bodyText, hoverText));
                    }
                })
                .addWorkingStatusLine();

        // Cross-recipe parallel display (synced via builder to prevent client/server buffer desync)
        builder.addCrossRecipeOrProgressDisplay(recipeMapWorkable);
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        ICasing matchedCoil = GTCasingGroups.heatingCoils().channel().getMatchedCasing(context);
        IHeatingCoilBlockStats coilType = matchedCoil != null ?
                matchedCoil.getPayloadAs(IHeatingCoilBlockStats.class) : null;
        if (coilType == null) {
            coilType = CoilType.CUPRONICKEL;
        }
        this.heatingCoilLevel = coilType.getLevel();
        this.heatingCoilDiscount = coilType.getEnergyDiscount();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.heatingCoilLevel = 0;
        this.heatingCoilDiscount = 0;
    }

    @NotNull
    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    public IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.HEAT_PROOF_CASING;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.MULTI_FURNACE_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    /**
     * @param heatingCoilLevel the level to get the parallel for
     * @return the max parallel for the heating coil level
     */
    public static int getMaxParallel(int heatingCoilLevel) {
        return 16 * heatingCoilLevel;
    }

    protected class MultiSmelterWorkable extends MultiblockRecipeLogic {

        public MultiSmelterWorkable(RecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        @Override
        protected void runOverclockingLogic(@NotNull OCParams ocParams, @NotNull OCResult ocResult,
                                            @NotNull RecipePropertyStorage propertyStorage, long maxVoltage) {
            standardOC(ocParams, ocResult, maxVoltage, getOverclockingDurationFactor(),
                    getOverclockingVoltageFactor());
        }

        @Override
        public int getParallelLimit() {
            return getMaxParallel(heatingCoilLevel);
        }
    }
}
