package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.LazyTemplate;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMachineCasing.MachineCasingType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityPyrolyseOven extends RecipeMapMultiblockController {

    private static final LazyTemplate TEMPLATE = LazyTemplate.of(() ->
            DeclarativePatternBuilder.start()
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("CCC", "C#C", "CCC")
                    .aisle("CCC", "C#C", "CCC")
                    .aisle("XXX", "XSX", "XXX")
                    .where('S', selfPredicate(GTUtility.gregtechId("pyrolyse_oven")))
                    .where('#', air())
                    .casing('X', CasingDefinition.simple(
                            MetaBlocks.MACHINE_CASING.getState(MachineCasingType.ULV),
                            "gregtech.machine.casing.ulv"))
                        .withHatches(MultiblockAbility.INPUT_ENERGY, 1, 2)
                        .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
                        .withOptionalHatches(MultiblockAbility.MUFFLER_HATCH, 1)
                        .withOptionalHatches(MultiblockAbility.IMPORT_ITEMS, 4)
                        .withOptionalHatches(MultiblockAbility.EXPORT_ITEMS, 4)
                        .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 4)
                        .withOptionalHatches(MultiblockAbility.EXPORT_FLUIDS, 4)
                    .tieredCasing('C', GTCasingGroups.heatingCoils())
                        .withChannel(GTStructureChannels.HEATING_COIL)
                    .buildTemplate()
    );

    private int coilTier;

    public MetaTileEntityPyrolyseOven(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.PYROLYSE_RECIPES);
        this.recipeMapWorkable = new PyrolyseOvenWorkableHandler(this);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityPyrolyseOven(metaTileEntityId);
    }

    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.VOLTAGE_CASINGS[0];
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.MACHINE_CASING.getState(MachineCasingType.ULV);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.PYROLYSE_OVEN_OVERLAY;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        ICasing matchedCoil = GTStructureChannels.HEATING_COIL.getMatchedCasing(context);
        if (matchedCoil instanceof GTCasingGroups.HeatingCoilCasing) {
            this.coilTier = ((GTCasingGroups.HeatingCoilCasing) matchedCoil).getCoilStats().getTier();
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
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
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
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class PyrolyseOvenWorkableHandler extends MultiblockRecipeLogic {

        public PyrolyseOvenWorkableHandler(RecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        @Override
        protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
            super.modifyOverclockPost(ocResult, storage);

            int coilTier = ((MetaTileEntityPyrolyseOven) metaTileEntity).getCoilTier();
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
