package gregtech.common.metatileentities.multi.steam;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ParallelLogicType;
import gregtech.api.metatileentity.multiblock.RecipeMapSteamMultiblockController;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static gregtech.client.renderer.texture.Textures.BRONZE_PLATED_BRICKS;

public class MetaTileEntitySteamGrinder extends RecipeMapSteamMultiblockController {

    private static final int PARALLEL_LIMIT = 8;

    public MetaTileEntitySteamGrinder(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.MACERATOR_RECIPES, CONVERSION_RATE, ParallelLogicType.CROSS_RECIPE);
        this.recipeMapWorkable.setParallelLimit(PARALLEL_LIMIT);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntitySteamGrinder(metaTileEntityId);
    }

    private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:steam_grinder", () -> DeclarativePatternBuilder.start()
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("XXX", "X#X", "XXX")
                    .aisle("XXX", "XSX", "XXX")
                    .where('S', selfPredicate(MetaTileEntitySteamGrinder.class))
                    .where('#', air())
                    .casing('X', CasingDefinition.simple(
                            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.BRONZE_BRICKS)))
                        .hatch(MultiblockAbility.STEAM, 1, 2)
                        .optionalHatch(MultiblockAbility.STEAM_IMPORT_ITEMS, 2)
                        .optionalHatch(MultiblockAbility.STEAM_EXPORT_ITEMS, 2)
                    .buildStructureDefinition());

    @Override
    protected StructureDefinition createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    public IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.BRONZE_BRICKS);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return BRONZE_PLATED_BRICKS;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.ROCK_BREAKER_OVERLAY;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public int getItemOutputLimit() {
        return 1;
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        TooltipBuilder.create().addSteamMachine(PARALLEL_LIMIT).build(this, tooltip);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (isActive()) {
            VanillaParticleEffects.defaultFrontEffect(this, 0.4F, EnumParticleTypes.SMOKE_NORMAL);
        }
    }
}
