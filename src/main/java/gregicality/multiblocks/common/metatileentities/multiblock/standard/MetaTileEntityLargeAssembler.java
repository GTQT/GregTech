package gregicality.multiblocks.common.metatileentities.multiblock.standard;

import gregicality.multiblocks.api.GCYMValues;
import gregicality.multiblocks.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregicality.multiblocks.api.render.GCYMTextures;
import gregicality.multiblocks.common.block.GCYMMetaBlocks;
import gregicality.multiblocks.common.block.blocks.BlockLargeMultiblockCasing;
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
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityLargeAssembler extends GCYMAdvanceRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild("gcym:large_assembler", () ->
            DeclarativePatternBuilder.start(FRONT, UP, RIGHT)
                    .aisle("XXX", "XXX", "XXX")
                    .aisleRepeatable(3, 3, "XXX", "CAX", "CCX")
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("XXX", "XAX", "#XX")
                    .aisle("XXX", "SXX", "#XX")
                    .aisle("XXX", "XAX", "#XX")
                    .aisle("XXX", "XXX", "XXX")
                    .where('S', selfPredicate(MetaTileEntityLargeAssembler.class))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                    .energyInput(1)
                    .custom(tieredCasing(), 1)
                    .custom(parallelCasing(), 1)
                    .custom(threadCasing(), 1)
                    .preset(HatchPresets.STANDARD_IO)
                    .preset(HatchPresets.MUFFLER_IO)
                    .where('C', states(getCasingState2()))
                    .where('A', air())
                    .where('#', any())
                    .buildStructureDefinition()
    );

    public MetaTileEntityLargeAssembler(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, determineRecipeMaps());
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.ASSEMBLING_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.TEMPERED_GLASS);
    }

    private static @NotNull RecipeMap<?> @NotNull [] determineRecipeMaps() {
        RecipeMap<?> cuisineAssemblerMap = RecipeMap.getByName("cuisine_assembler");
        if (Loader.isModLoaded(GCYMValues.GTFO_MODID) && cuisineAssemblerMap != null) {
            return new RecipeMap<?>[]{RecipeMaps.ASSEMBLER_RECIPES, cuisineAssemblerMap};
        }
        return new RecipeMap<?>[]{RecipeMaps.ASSEMBLER_RECIPES};
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityLargeAssembler(this.metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.ASSEMBLING_CASING;
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return GCYMTextures.LARGE_ASSEMBLER_OVERLAY;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addSpecialLogic().build(this, tooltip);
        tooltip.add(I18n.format("gcym.tooltip.max_energy_hatches", 1));
    }
}
