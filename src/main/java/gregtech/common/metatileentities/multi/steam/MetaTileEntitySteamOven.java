package gregtech.common.metatileentities.multi.steam;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.SteamMultiWorkable;
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
import gregtech.common.blocks.BlockFireboxCasing;
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

public class MetaTileEntitySteamOven extends RecipeMapSteamMultiblockController {

    private static final int PARALLEL_LIMIT = 8;

    public MetaTileEntitySteamOven(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.FURNACE_RECIPES, CONVERSION_RATE, ParallelLogicType.CROSS_RECIPE);
        this.recipeMapWorkable = new SteamMultiWorkable(this, CONVERSION_RATE, ParallelLogicType.CROSS_RECIPE);
        this.recipeMapWorkable.setParallelLimit(PARALLEL_LIMIT);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntitySteamOven(metaTileEntityId);
    }

    @NotNull
    private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:steam_oven", () -> DeclarativePatternBuilder.start()
                    .aisle("XXX", "CCC", "#C#")
                    .aisle("XXX", "C#C", "#C#")
                    .aisle("XXX", "CSC", "#C#")
                    .where('S', selfPredicate(MetaTileEntitySteamOven.class))
                    .where('#', any())
                    .casing('X', CasingDefinition.simple(
                            MetaBlocks.BOILER_FIREBOX_CASING.getState(BlockFireboxCasing.FireboxCasingType.BRONZE_FIREBOX)))
                        .hatch(MultiblockAbility.STEAM, 1, 3)
                    .casing('C', CasingDefinition.simple(
                            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.BRONZE_BRICKS)))
                        .optionalHatch(MultiblockAbility.STEAM_IMPORT_ITEMS, 4)
                        .optionalHatch(MultiblockAbility.STEAM_EXPORT_ITEMS, 4)
                    .buildStructureDefinition());

    @Override
    protected StructureDefinition createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    public IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.BRONZE_BRICKS);
    }

    public IBlockState getFireboxState() {
        return MetaBlocks.BOILER_FIREBOX_CASING.getState(BlockFireboxCasing.FireboxCasingType.BRONZE_FIREBOX);
    }

    private boolean isFireboxPart(IMultiblockPart sourcePart) {
        return isStructureFormed() && (((MetaTileEntity) sourcePart).getPos().getY() < getPos().getY());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        if (sourcePart != null && isFireboxPart(sourcePart)) {
            return lastActive ? Textures.BRONZE_FIREBOX_ACTIVE : Textures.BRONZE_FIREBOX;
        }
        return Textures.BRONZE_PLATED_BRICKS;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.ELECTRIC_FURNACE_OVERLAY;
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
            VanillaParticleEffects.defaultFrontEffect(this, EnumParticleTypes.SMOKE_LARGE, EnumParticleTypes.FLAME);
            if (GTValues.RNG.nextBoolean()) {
                VanillaParticleEffects.defaultFrontEffect(this, 0.5F, EnumParticleTypes.SMOKE_NORMAL);
            }
        }
    }
}
