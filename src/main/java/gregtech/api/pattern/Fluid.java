package gregtech.api.pattern;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class Fluid {
    public static final String FLUID_BLOCKS_KEY = "FluidBlocks";

    public static TraceabilityPredicate fluid(FluidStack fluidStack) {
        return fluid(fluidStack.getFluid());
    }

    public static TraceabilityPredicate fluid(net.minecraftforge.fluids.Fluid fluid) {
        Block fluidBlock = fluid.getBlock();
        String fluidName = fluid.getName();
        if (fluidBlock == null) {
            throw new IllegalArgumentException("Fluid \"" + fluidName + "\" has no associated block!");
        }
        IBlockState stillState = fluidBlock.getDefaultState();

        return new TraceabilityPredicate(
                bws -> {
                    IBlockState blockState = bws.getBlockState();
                    if (blockState == stillState) return true;
                    if (bws.getWorld().isAirBlock(bws.getPos()) || blockState.getBlock() == fluidBlock) {
                        bws.getMatchContext()
                                /// This can be a [Map] for multiple types of fluids,
                                /// but this should be enough for now.
                                /// Using an [ArrayList] here since we need to sort this later.
                                /// [LinkedList] would be horrible for that
                                .getOrPut(FLUID_BLOCKS_KEY, new ArrayList<>())
                                .add(bws.getPos());
                        return true;
                    }
                    return false;
                },
                () -> new BlockInfo[]{
                        ConfigHolder.misc.showFluidsForAutoFillingMultiblocks ?
                                new BlockInfo(stillState) : new BlockInfo(Blocks.AIR)
                });
    }

    public static void fillFluid(MultiblockControllerBase multi, List<BlockPos> toFill, FluidStack fluidStack) {
        fillFluid(multi, toFill, fluidStack.getFluid());
    }

    public static void fillFluid(MultiblockControllerBase multi, List<BlockPos> toFill, net.minecraftforge.fluids.Fluid fluid) {
        if (toFill.isEmpty()) return;

        // TODO: is it necessary for a multi to have a recipe logic for this?
        AbstractRecipeLogic recipeLogic = multi.getRecipeLogic();
        if (recipeLogic == null) return;

        IMultipleTankHandler fluidInputs = recipeLogic.getInputTank();
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
