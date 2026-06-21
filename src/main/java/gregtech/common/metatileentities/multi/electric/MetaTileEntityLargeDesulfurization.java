package gregtech.common.metatileentities.multi.electric;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

//此系列设备不给多线程
public class MetaTileEntityLargeDesulfurization extends GCYMRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_desulfurizer", () ->
            DeclarativePatternBuilder.start()
                    .aisle("CCCCC", "CCCCC", "CCCCC", " CCC ", " CCC ")
                    .aisle("CCCCC", "UPFPU", "UUFUU", " UFU ", " CCC ")
                    .aisle("CCCCC", "CPFPC", "CPFPC", " CFC ", " CCC ")
                    .aisle("CCCCC", "UPFPU", "UUFUU", " UFU ", " CCC ")
                    .aisle("CCCCC", "CPFPC", "CPFPC", " CFC ", " CCC ")
                    .aisle("CCCCC", "UPFPU", "UUFUU", " UFU ", " CCC ")
                    .aisle("CCCCC", "CCSCC", "CCCCC", " CCC ", " CCC ")
                    .self('S', MetaTileEntityLargeDesulfurization.class)
                    .casing('C', CasingDefinition.simple(getCasingState()))
                    .energyInput(1, 2)
                    .tieredHatch()
                    .parallelHatch()
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('P', states(getCasingState2()))
                    .tieredCasing('U', GTCasingGroups.heatingCoils().group())
                    .withChannel(GTCasingGroups.heatingCoils().channel())
                    .where('F', states(getFrameState()))
                    .where(' ', any())
                    .buildStructureDefinition()
    );
    private int coilTier;

    public MetaTileEntityLargeDesulfurization(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.DESULFURIZATION_RECIPES);
        this.recipeMapWorkable = new DesulfurizationWorkableHandler(this);
    }

    private static IBlockState getFrameState() {
        return MetaBlocks.FRAMES.get(Materials.StainlessSteel).getBlock(Materials.StainlessSteel);
    }

    private static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.CORROSION_PROOF_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.STEEL_PIPE);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        ICasing matchedCoil = GTCasingGroups.heatingCoils().channel().getMatchedCasing(formed);
        IHeatingCoilBlockStats stats = matchedCoil != null ?
                matchedCoil.getPayloadAs(IHeatingCoilBlockStats.class) : null;
        if (stats != null) {
            this.coilTier = stats.getTier();
        } else {
            this.coilTier = 0;
        }
    }

    private TextFormatting getSpeedColor(int speed) {
        if (speed < 100) {
            return TextFormatting.RED;
        } else if (speed == 100) {
            return TextFormatting.GRAY;
        } else if (speed < 250) {
            return TextFormatting.GREEN;
        } else {
            return TextFormatting.LIGHT_PURPLE;
        }
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(this.getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addCustom((textList, syncer) -> {
                    if (!isStructureFormed()) return;
                    int tier = syncer.syncInt(coilTier);

                    int processingSpeed = tier == 0 ? 75 : 50 * (tier + 1);
                    IKey speed = KeyUtil.number(() -> getSpeedColor(processingSpeed), processingSpeed, "%");

                    IKey body = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.pyrolyse_oven.speed", speed);
                    IKey hover = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.pyrolyse_oven.speed_hover");
                    textList.add(KeyUtil.setHover(body, hover));
                })
                .addParallelsLine(recipeMapWorkable.getParallelLimit())
                .addWorkingStatusLine()
                .addProgressLine(recipeMapWorkable.getProgress(), recipeMapWorkable.getMaxProgress())
                .addRecipeOutputLine(recipeMapWorkable);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addCoilLogic().build(this, tooltip);
        tooltip.add(I18n.format("gregtech.machine.pyrolyse_oven.tooltip.1"));
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.coilTier = -1;
    }

    protected int getCoilTier() {
        return this.coilTier;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeDesulfurization(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.CORROSION_PROOF_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.MEGA_CHEMICAL_REACTOR;
    }

    private class DesulfurizationWorkableHandler extends GCYMMultiblockRecipeLogic {

        public DesulfurizationWorkableHandler(GCYMRecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        @Override
        protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
            super.modifyOverclockPost(ocResult, storage);

            int coilTier = ((MetaTileEntityLargeDesulfurization) metaTileEntity).getCoilTier();
            if (coilTier == -1)
                return;

            if (coilTier == 0) {
                // 75% speed with cupronickel (coilTier = 0)
                ocResult.setDuration(Math.max(1, (int) (ocResult.duration() * 4.0 / 3)));
            } else {
                // each coil above kanthal (coilTier = 1) is 50% faster
                ocResult.setDuration(Math.max(1, (int) (ocResult.duration() * 2.0 / (coilTier + 1))));
            }
        }
    }
}
