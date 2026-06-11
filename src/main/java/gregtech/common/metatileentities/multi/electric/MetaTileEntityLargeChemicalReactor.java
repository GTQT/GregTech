package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityLargeChemicalReactor extends RecipeMapMultiblockController {

    private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:large_chemical_reactor", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXX", "XCX", "XXX")
                    .aisle("XCX", "CPC", "XCX")
                    .aisle("XXX", "XSX", "XXX")
                    .where('S', selfPredicate(MetaTileEntityLargeChemicalReactor.class))
                    .where('P', Elements.block(getPipeCasingState()))
                    .where('C', Elements.chain(
                            Elements.tieredCasing(
                                    GTCasingGroups.heatingCoils().group(),
                                    GTCasingGroups.heatingCoils().channel().getName(),
                                    1, 1),
                            Elements.hatch(MultiblockAbility.IMPORT_ITEMS, 0, -1, 0),
                            Elements.hatch(MultiblockAbility.EXPORT_ITEMS, 0, -1, 0),
                            Elements.hatch(MultiblockAbility.IMPORT_FLUIDS, 0, -1, 0),
                            Elements.hatch(MultiblockAbility.EXPORT_FLUIDS, 0, -1, 0),
                            Elements.block(getCasingState())))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                        .maintenance().preset(HatchPresets.STANDARD_IO).optionalEnergyInput(2)
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeChemicalReactor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.LARGE_CHEMICAL_RECIPES);
        this.recipeMapWorkable = new MultiblockRecipeLogic(this, true);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeChemicalReactor(metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.INERT_PTFE_CASING;
    }

    protected static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING);
    }

    protected static IBlockState getPipeCasingState() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        TooltipBuilder.create().addPerfectOC().build(this, tooltip);
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.LARGE_CHEMICAL_REACTOR_OVERLAY;
    }
}
