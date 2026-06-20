package gregtech.common.metatileentities.multiblock.standard;

import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
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

//此系列设备不给多线程
public class MetaTileEntityLargeChemicalComplex extends GCYMRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_chemical_complex", () ->
            DeclarativePatternBuilder.start()
                    .aisle("X###X", "XXXXX", "X###X", "XXXXX", "X###X")
                    .aisle("XXXXX", "XPPPX", "XCCCX", "XPPPX", "XXXXX")
                    .aisle("XXXXX", "XPPPX", "XCPCX", "XPPPX", "XXXXX")
                    .aisle("XXXXX", "XPPPX", "XCCCX", "XPPPX", "XXXXX")
                    .aisle("X###X", "SXXXX", "X###X", "XXXXX", "X###X")
                    .where('S', selfPredicate(MetaTileEntityLargeChemicalComplex.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1, 2)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('P', states(getCasingState2()))
                    .tieredCasing('C', GTCasingGroups.heatingCoils().group())
                    .withChannel(GTCasingGroups.heatingCoils().channel())
                    .where('#', any())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeChemicalComplex(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[]{
                RecipeMaps.CHEMICAL_RECIPES,
                RecipeMaps.POLYMERIZATION_RECIPES,
                RecipeMaps.DESULFURIZATION_RECIPES
        });
        this.recipeMapWorkable = new GCYMMultiblockRecipeLogic(this, true);
    }

    private static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeChemicalComplex(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.INERT_PTFE_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.LARGE_CHEMICAL_REACTOR_OVERLAY;
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
}
