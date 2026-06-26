package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.tooltips.InformationHandler;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockFusionCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

//这是与GCYM的转底炉 巨冰箱同一系列的设备
//此系列设备不给多线程
public class MetaTileEntityMegaChemicalReactor extends GCYMRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:mega_chemical_reactor", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("XXXXX", "XEEEX", "XEEEX", "XEEEX", "XXXXX")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("HPXPH", "#GGG#", "#GFG#", "#GGG#", "HPXPH")
                            .aisle("XXXXX", "XGGGX", "XGSGX", "XGGGX", "XXXXX")
                            .self('S', MetaTileEntityMegaChemicalReactor.class)
                            .where('E', states(getCasingState())
                                    .or(abilities(MultiblockAbility.INPUT_ENERGY)
                                            .setMaxGlobalLimited(8))
                                    .or(abilities(MultiblockAbility.INPUT_LASER)
                                            .setMaxGlobalLimited(1))
                            )
                            .where('P', states(getPipeCasingState()))
                            .where('#', air())
                            .where('G', states(getGlassState()))
                            .where('F', states(getCoilState()))
                            .casing('X', CasingDefinition.simple(getCasingState()))
                            .preset(HatchPresets.STANDARD_IO)
                            .preset(HatchPresets.MUFFLER_IO)
                            .tieredHatch()
                            .parallelHatch()
                            .where('H', states(getCasingState()))
                            .buildStructureDefinition()
    );

    public MetaTileEntityMegaChemicalReactor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] {
                RecipeMaps.LARGE_CHEMICAL_RECIPES,
                RecipeMaps.POLYMERIZATION_RECIPES,
                RecipeMaps.DESULFURIZATION_RECIPES
        });
        this.recipeMapWorkable = new GCYMMultiblockRecipeLogic(this, true);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING);
    }

    protected static IBlockState getPipeCasingState() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    protected static IBlockState getGlassState() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS);
    }

    protected static IBlockState getCoilState() {
        return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_COIL);
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

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        InformationHandler.topTooltips("最强反应釜", tooltip);
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addPerfectOC().addLaser().build(this, tooltip);
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityMegaChemicalReactor(this.metaTileEntityId);
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.MEGA_CHEMICAL_REACTOR;
    }
}
