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

import static gregtech.api.pattern.element.Elements.block;
import static gregtech.api.pattern.element.Elements.self;

/**
 * Demo multiblock using irregular repeatable sub-regions with multi-axis repetition.
 *
 * <p>Structure layout (irregular two-aisle sub-region, repeatable along X, Y and Z axes):
 * <pre>
 *   Controller base (fixed, 1 aisle):
 *     WSW      S = self (controller)
 *     WWW      W = casing wall
 *
 *   Wall voxel (repeatable along X, Y and Z, 2 aisles):
 *     WCW      C = corner block (different from W, makes this non-tensor-product)
 *     W W      (space) = air inside
 *
 *     WWW
 *     C W
 *              -> triggers NESTED_BACKTRACKING strategy
 * </pre>
 *
 * <p>The wall piece itself is larger than one block on every axis (3 x 2 x 2),
 * and then repeats along X (width: 1~5), Y (height: 1~7) and Z (depth: 1~4).
 * The irregular pattern (different characters W and C across slices) ensures
 * this uses the NESTED_BACKTRACKING search strategy rather than INDEPENDENT_1D.
 */
public class MetaTileEntityMultiAxisDemo extends MultiblockWithDisplayBase {

    // Structure definition using DeclarativePatternBuilder
    private static final StructureDefinition<?> DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:multi_axis_demo", () ->
            DeclarativePatternBuilder.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                    // Fixed base piece: controller sits on the bottom
                    .piece("base")
                            .aisle("WSW", "WWW")
                            .centerOffset(1, 0, 0)
                    // Repeatable wall piece: irregular (W + C + space), size 3 x 2 x 2.
                    // X axis: 1~5, Y axis: 1~7, Z axis: 1~4.
                    .repeatablePiece("wall",
                            new String[][]{
                                    {"WCW", "W W"},
                                    {"WWW", "C W"}
                            },
                            // The base occupies local Y=0..1, so the wall must start at Y=2.
                            new Vec3i(0, 2, 0))
                            .repeatAxes(0, 1, 2)       // X, Y and Z axes
                            .repeatRange(1, 5, 1, 7, 1, 4) // X: 1~5, Y: 1~7, Z: 1~4
                            // Runtime local +Z points toward the controller front. Anchor the
                            // near aisle at Z=0 and repeat toward the back with a negative step.
                            .stepSizes(3, 2, -2)       // tile the 3 x 2 x 2 sub-region
                            .channelNames(
                                    GTStructureChannels.STRUCTURE_WIDTH.getName(),
                                    GTStructureChannels.STRUCTURE_HEIGHT.getName(),
                                    GTStructureChannels.STRUCTURE_LENGTH.getName())
                            .centerOffset(1, 0, 1)
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
    protected StructureDefinition<?> createStructureDefinition() {
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
        tooltip.add("Multi-Axis Demo: irregular 3 x 2 x 2 repeatable sub-region");
        tooltip.add("X: 1~5, Y: 1~7, Z: 1~4 (NESTED_BACKTRACKING strategy)");
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
