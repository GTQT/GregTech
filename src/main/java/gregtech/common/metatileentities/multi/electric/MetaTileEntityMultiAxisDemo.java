package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static gregtech.api.pattern.element.Elements.*;

/**
 * Demo multiblock using irregular repeatable sub-regions with multi-axis repetition.
 *
 * <p>Structure layout (L-shaped cross-section, repeatable along X and Y axes):
 * <pre>
 *   Controller base (fixed, 1 aisle):
 *     WSW      S = self (controller)
 *     WWW      W = casing wall
 *
 *   L-shaped wall (repeatable along X and Y, 1 aisle):
 *     WCW      C = corner block (different from W, makes this non-tensor-product)
 *     W W      (space) = air inside
 *              -> triggers NESTED_BACKTRACKING strategy
 * </pre>
 *
 * <p>The L-shaped wall piece repeats along X (width: 2~5) and Y (height: 2~7),
 * creating a variable-size L-shaped structure. The irregular pattern (different
 * characters W and C in the same slice) ensures this uses the NESTED_BACKTRACKING
 * search strategy rather than INDEPENDENT_1D.
 */
public class MetaTileEntityMultiAxisDemo extends MultiblockWithDisplayBase {

    // Structure definition using DeclarativePatternBuilder
    private static final StructureDefinition DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:multi_axis_demo", () ->
            DeclarativePatternBuilder.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                    // Fixed base piece: controller sits on the bottom
                    .piece("base")
                            .aisle("WSW", "WWW")
                            .centerOffset(1, 0, 0)
                    // Repeatable L-shaped wall piece: irregular (W + C + space)
                    // X axis: 2~5, Y axis: 2~7
                    .repeatablePiece("wall",
                            new String[][]{{"WCW", "W W"}},
                            new Vec3i(0, 1, 0))
                            .repeatAxes(0, 1)     // X and Y axes
                            .repeatRange(2, 5, 2, 7) // X: 2~5, Y: 2~7
                            .stepSizes(1, 1)       // step 1 for both axes
                            .channelNames(
                                    GTStructureChannels.STRUCTURE_WIDTH.getName(),
                                    GTStructureChannels.STRUCTURE_HEIGHT.getName())
                            .centerOffset(1, 0, 0)
                    .where('S', self(MetaTileEntityMultiAxisDemo.class))
                    .where('W', block(getCasingState()))
                    .where('C', block(getCornerState()))
                    .where(' ', air())
                    .buildStructureDefinition()
    );

    private EnergyContainerList energyContainer;

    public MetaTileEntityMultiAxisDemo(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMultiAxisDemo(metaTileEntityId);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        this.energyContainer = new EnergyContainerList(getAbilities(MultiblockAbility.INPUT_ENERGY));
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.energyContainer = new EnergyContainerList(Collections.emptyList());
    }

    @Override
    protected void updateFormedValid() {
    }

    @Nullable
    @Override
    protected StructureDefinition createStructureDefinition() {
        return DEFINITION;
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    public boolean allowsFlip() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.VOLTAGE_CASINGS[GTValues.LV];
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.MULTIBLOCK_WORKABLE_OVERLAY;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        tooltip.add("Multi-Axis Demo: L-shaped irregular repeatable sub-region");
        tooltip.add("X: 2~5, Y: 2~7 (NESTED_BACKTRACKING strategy)");
    }

    /** Get the casing block state for walls */
    @NotNull
    protected static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
    }

    /** Get the corner block state (different from walls to make pattern irregular) */
    @NotNull
    protected static IBlockState getCornerState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.INVAR_HEATPROOF);
    }
}
