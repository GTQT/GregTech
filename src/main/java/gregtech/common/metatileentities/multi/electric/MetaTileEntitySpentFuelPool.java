package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.TextComponentUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockNuclearCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static gregtech.api.pattern.FluidStructureElements.*;
import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntitySpentFuelPool extends RecipeMapMultiblockController {

    public static final int PARALLEL_PER_LENGTH = 32;
    @NotNull
    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:spent_fuel_pool", () -> DeclarativePatternBuilder.start(FRONT, UP, RIGHT)
                    // spotless:off
                    .aisle("CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "TTTTTTTTTT")
                    .aisle("CCCCCCCCCC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "S........T")
                    .repeatablePiece("body", 1, 10)
                    .aisle("CCCCCCCCCC", "CWRRRRRRWC", "CWRRRRRRWC", "CWRRRRRRWC", "CWRRRRRRWC", "CWRRRRRRWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "T........T")
                    .withAisleChannel(GTStructureChannels.STRUCTURE_LENGTH.getName())
                    .end()
                    .piece("rear")
                    .aisle("CCCCCCCCCC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "CWWWWWWWWC", "T........T")
                    .end()
                    .piece("back")
                    .aisle("CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "CCCCCCCCCC", "TTTTTTTTTT")
                    //spotless:on
                    .end()
                    .self('S', MetaTileEntitySpentFuelPool.class)
                    .any('.')
                    .blocks('C', MetaBlocks.PANELLING)
                    .where('W', fluidElement(FluidRegistry.WATER))
                    .block('R', getRodState())
                    .casing('T', getCasingState())
                    .optionalItemInput(4)
                    .optionalItemOutput(4)
                    .optionalFluidInput(4)
                    .optionalFluidOutput(4)
                    .done()
                    .buildStructureDefinition()
    );
    private boolean waterFilled;
    private List<BlockPos> waterPositions;

    public MetaTileEntitySpentFuelPool(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.SPENT_FUEL_POOL_RECIPES);
    }

    /** Preserve the legacy into-structure orientation of this template. */
    @Override
    public EnumFacing getFrontFacingForStructure() {
        return getFrontFacing().getOpposite();
    }

    private static IBlockState getRodState() {
        return MetaBlocks.NUCLEAR_CASING.getState(BlockNuclearCasing.NuclearCasingType.SPENT_FUEL_CASING);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STAINLESS_CLEAN);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntitySpentFuelPool(metaTileEntityId);
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    protected void formStructure(FormedStructureView formed) {
        super.formStructure(formed);
        this.recipeMapWorkable.setParallelLimit(
                formed.getChannelValue(GTStructureChannels.STRUCTURE_LENGTH) * PARALLEL_PER_LENGTH);

        List<BlockPos> positions = formed.getAggregate(FLUID_BLOCK_POSITIONS);
        this.waterPositions = positions == null ? new ArrayList<>() : new ArrayList<>(positions);
        this.waterPositions.sort(Comparator.comparingInt(BlockPos::getY));
        this.waterFilled = waterPositions.isEmpty();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.waterPositions = null; // Clear water fill data when the structure is invalidated
        this.waterFilled = false;
    }

    @Override
    protected void updateFormedValid() {
        super.updateFormedValid();
        if (!waterFilled && getOffsetTimer() % 5 == 0) {
            fillFluid(this, this.waterPositions, FluidRegistry.WATER);
            if (this.waterPositions.isEmpty()) {
                this.waterFilled = true;
            }
        }
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || !waterFilled;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.SPENT_FUEL_POOL_OVERLAY;
    }

    @Override
    protected void addErrorText(List<ITextComponent> textList) {
        super.addErrorText(textList);
        if (isStructureFormed() && !waterFilled) {
            textList.add(TextComponentUtil.translationWithColor(TextFormatting.RED,
                    "gregtech.multiblock.spent_fuel_pool.obstructed"));
            textList.add(TextComponentUtil.translationWithColor(TextFormatting.GRAY,
                    "gregtech.multiblock.spent_fuel_pool.obstructed.desc"));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.spent_fuel_pool.tooltip.parallel", PARALLEL_PER_LENGTH));
        tooltip.add(I18n.format("gregtech.machine.fluid_auto_fill.tooltip"));
    }

    @Override
    public boolean isMultiblockPartWeatherResistant(@NotNull IMultiblockPart part) {
        return true;
    }

    @Override
    public boolean getIsWeatherOrTerrainResistant() {
        return true;
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }
}
