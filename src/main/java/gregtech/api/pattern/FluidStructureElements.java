package gregtech.api.pattern;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.BlockInfo;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Set;

public final class FluidStructureElements {

    public static final StructureContributionKey<BlockPos, List<BlockPos>> FLUID_BLOCK_POSITIONS =
            StructureContributionKey.orderedList("gregtech:fluid_block_positions");

    private FluidStructureElements() {}

    public static IStructureElement<Object> fluidElement(net.minecraftforge.fluids.Fluid fluid) {
        Block fluidBlock = fluid.getBlock();
        String fluidName = fluid.getName();
        if (fluidBlock == null) {
            throw new IllegalArgumentException("Fluid \"" + fluidName + "\" has no associated block!");
        }
        IBlockState stillState = fluidBlock.getDefaultState();

        return new ITypedStructureElement<Object>() {

            @Override
            public boolean check(StructureEvaluationContext<Object> context) {
                IBlockState blockState = context.getBlockState();
                if (blockState == stillState) return true;
                if (blockState.getBlock().isAir(blockState, context.getBlockAccess(), context.getPos()) ||
                        blockState.getBlock() == fluidBlock) {
                    context.getCollector().emit(FLUID_BLOCK_POSITIONS, context.getPos());
                    return true;
                }
                return false;
            }

            @Override
            public Set<StructureElementCapability> getCapabilities() {
                return StructureElementCapability.snapshotSafe();
            }

            @Override
            public BlockInfo[] getCandidates() {
                IBlockState state = ConfigHolder.misc.showFluidsForAutoFillingMultiblocks ?
                        stillState : Blocks.AIR.getDefaultState();
                return new BlockInfo[]{new BlockInfo(state)};
            }

            @Override
            public boolean placeBlock(StructureEvaluationContext<Object> context,
                                      EntityPlayer player, boolean skipHatches) {
                World world = context.getWorld();
                if (world == null) return false;
                IBlockState state = ConfigHolder.misc.showFluidsForAutoFillingMultiblocks ?
                        stillState : Blocks.AIR.getDefaultState();
                return world.setBlockState(context.getPos(), state);
            }
        };
    }

    public static void fillFluid(MultiblockControllerBase multi, List<BlockPos> toFill, FluidStack fluidStack) {
        fillFluid(multi, toFill, fluidStack.getFluid());
    }

    public static void fillFluid(MultiblockControllerBase multi, List<BlockPos> toFill,
                                 net.minecraftforge.fluids.Fluid fluid) {
        if (toFill.isEmpty()) return;

        AbstractRecipeLogic recipeLogic = multi.getRecipeLogic();
        if (recipeLogic == null) return;

        IMultipleTankHandler fluidInputs = recipeLogic.inputTank();
        if (fluidInputs == null) return;

        FluidStack toDrain = new FluidStack(fluid, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME);
        FluidStack drained = fluidInputs.drain(toDrain, false);
        if (drained == null || drained.amount == 0) return;

        if (drained.amount == net.minecraftforge.fluids.Fluid.BUCKET_VOLUME) {
            World world = multi.getWorld();
            BlockPos pos = toFill.get(0);

            if (world.isBlockLoaded(pos) &&
                    (world.isAirBlock(pos) || world.getBlockState(pos).getBlock() == fluid.getBlock())) {
                world.setBlockState(pos, fluid.getBlock().getDefaultState(), Constants.BlockFlags.SEND_TO_CLIENTS);
                fluidInputs.drain(drained, true);
                toFill.remove(0);
            }
        }
    }
}
