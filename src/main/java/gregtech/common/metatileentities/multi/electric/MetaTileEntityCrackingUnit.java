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
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;
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
import gregtech.common.blocks.BlockMetalCasing;
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

public class MetaTileEntityCrackingUnit extends RecipeMapMultiblockController {

    private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register("gregtech:cracker", () ->
            DeclarativePatternBuilder.start()
                    .aisle("HCHCH", "HCHCH", "HCHCH")
                    .aisle("HCHCH", "H###H", "HCHCH")
                    .aisle("HCHCH", "HCOCH", "HCHCH")
                    .where('O', selfPredicate(GTUtility.gregtechId("cracker")))
                    .where('#', air())
                    .casing('H', CasingDefinition.simple(
                            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STAINLESS_CLEAN),
                            "gregtech.machine.casing.stainless_clean"))
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

    public MetaTileEntityCrackingUnit(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.CRACKING_RECIPES);
        this.recipeMapWorkable = new CrackingUnitWorkableHandler(this);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCrackingUnit(metaTileEntityId);
    }

    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STAINLESS_CLEAN);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addCustom((textList, syncer) -> {
                    if (!isStructureFormed()) return;

                    // Coil energy discount line
                    IKey energyDiscount = KeyUtil.number(TextFormatting.AQUA,
                            syncer.syncLong(100 - 10L * getCoilTier()), "%");

                    IKey base = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.cracking_unit.energy",
                            energyDiscount);

                    IKey hover = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.cracking_unit.energy_hover");

                    textList.add(KeyUtil.setHover(base, hover));
                })
                .addParallelsLine(recipeMapWorkable.getParallelLimit())
                .addWorkingStatusLine();

        // Cross-recipe parallel display
        if (recipeMapWorkable.isCrossRecipeMode() && recipeMapWorkable.getCrossRecipeScheduler() != null) {
            addCrossRecipeDisplay(builder, recipeMapWorkable);
        } else {
            builder.addProgressLine(recipeMapWorkable.getProgress(), recipeMapWorkable.getMaxProgress())
                    .addRecipeOutputLine(recipeMapWorkable);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addCoilLogic().build(this, tooltip);
        tooltip.add(I18n.format("gregtech.machine.cracker.tooltip.1"));
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.CRACKING_UNIT_OVERLAY;
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

    @SuppressWarnings("InnerClassMayBeStatic")
    private class CrackingUnitWorkableHandler extends MultiblockRecipeLogic {

        public CrackingUnitWorkableHandler(RecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        @Override
        protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
            super.modifyOverclockPost(ocResult, storage);

            int coilTier = ((MetaTileEntityCrackingUnit) metaTileEntity).getCoilTier();
            if (coilTier <= 0)
                return;

            // each coil above cupronickel (coilTier = 0) uses 10% less energy
            ocResult.setEut(Math.max(1, (long) (ocResult.eut() * (1.0 - coilTier * 0.1))));
        }
    }
}
