package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import gregtech.api.recipes.SCRecipeMaps;
import gregtech.client.renderer.textures.SCTextures;
import gregtech.common.blocks.BlockGasCentrifugeCasing;
import gregtech.common.blocks.BlockNuclearCasing;
import gregtech.common.blocks.MetaBlocks;

import java.util.List;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityGasCentrifuge extends RecipeMapMultiblockController {

    @NotNull
    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:gas_centrifuge", () -> DeclarativePatternBuilder.start(FRONT, UP, RIGHT)
                    .aisle("SI", "HH", "CC", "CC", "CC", "CC", "CC")
                    .repeatablePiece("body", 1, 14)
                    .aisle("EE", "HH", "CC", "CC", "CC", "CC", "CC")
                    .withAisleChannel(GTStructureChannels.STRUCTURE_LENGTH.getName())
                    .end()
                    .piece("end")
                    .aisle("OO", "HH", "CC", "CC", "CC", "CC", "CC")
                    .end()
                    .self('S', MetaTileEntityGasCentrifuge.class)
                    .block('P', getPipeState())
                    .block('H', getHeaterState())
                    .block('C', getCentrifugeState())
                    .where('I', Elements.chain(
                            Elements.block(getPipeState()),
                            Elements.hatch(MultiblockAbility.IMPORT_FLUIDS)))
                    .where('E', Elements.chain(
                            Elements.block(getPipeState()),
                            Elements.hatch(MultiblockAbility.MAINTENANCE_HATCH),
                            Elements.hatch(MultiblockAbility.INPUT_ENERGY)))
                    .where('O', Elements.chain(
                            Elements.block(getPipeState()),
                            Elements.hatch(MultiblockAbility.EXPORT_FLUIDS)))
                    .buildStructureDefinition()
    );

    public MetaTileEntityGasCentrifuge(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, SCRecipeMaps.GAS_CENTRIFUGE_RECIPES);
        this.recipeMapWorkable = new MultiblockRecipeLogic(this);
    }

    /** Preserve the legacy into-structure orientation of this template. */
    @Override
    public EnumFacing getFrontFacingForStructure() {
        return getFrontFacing().getOpposite();
    }

    private static IBlockState getPipeState() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    private static IBlockState getHeaterState() {
        return MetaBlocks.NUCLEAR_CASING.getState(
                BlockNuclearCasing.NuclearCasingType.GAS_CENTRIFUGE_HEATER);
    }

    private static IBlockState getCentrifugeState() {
        return MetaBlocks.GAS_CENTRIFUGE_CASING
                .getState(BlockGasCentrifugeCasing.GasCentrifugeCasingType.GAS_CENTRIFUGE_COLUMN);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.INERT_PTFE_CASING;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityGasCentrifuge(metaTileEntityId);
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    protected void formStructure(FormedStructureView formed) {
        super.formStructure(formed);
        this.recipeMapWorkable.setParallelLimit(formed.getChannelValue(GTStructureChannels.STRUCTURE_LENGTH));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.gas_centrifuge.tooltip.parallel"));
    }

    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return SCTextures.GAS_CENTRIFUGE_OVERLAY;
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }
}
