package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;
import org.jetbrains.annotations.NotNull;

public class FluidPipeInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":fluid_pipe_info_provider";
    }

    @Override
    public void addProbeInfo(@NotNull ProbeMode mode, @NotNull IProbeInfo probeInfo, @NotNull EntityPlayer player,
                             @NotNull World world, @NotNull IBlockState blockState, @NotNull IProbeHitData data) {
        if (!blockState.getBlock().hasTileEntity(blockState)) {
            return;
        }

        TileEntity tileEntity = world.getTileEntity(data.getPos());
        if (!(tileEntity instanceof TileEntityFluidPipeTickable fluidPipe)) {
            return;
        }

        FluidTank[] tanks = fluidPipe.getFluidTanks();
        int tankCount = tanks.length;
        int capacityPerTank = fluidPipe.getCapacityPerTank();
        int totalCapacity = capacityPerTank * tankCount;
        if (totalCapacity <= 0) {
            return;
        }

        int totalStored = 0;
        for (FluidTank tank : tanks) {
            totalStored += tank.getFluidAmount();
        }

        boolean exact = player.isSneaking();
        probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.fluid_capacity*} " + TextFormatting.AQUA +
                formatFluidAmount(totalCapacity, exact));
        if (tankCount > 1) {
            probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.fluid_pipe.channels*} " + TextFormatting.AQUA +
                    tankCount + TextStyleClass.INFO + " x " + TextFormatting.AQUA +
                    formatFluidAmount(capacityPerTank, exact));
        }

        addStoredFluidInfo(probeInfo, tanks, exact);
        probeInfo.progress(totalStored, totalCapacity, probeInfo.defaultProgressStyle()
                .numberFormat(exact || totalStored < 10000 ? NumberFormat.COMMAS : NumberFormat.COMPACT)
                .suffix(" / " + formatFluidAmount(totalCapacity, exact))
                .filledColor(0xFF2F9BFF)
                .alternateFilledColor(0xFF62C5FF)
                .borderColor(0xFF555555));
    }

    private static void addStoredFluidInfo(@NotNull IProbeInfo probeInfo, @NotNull FluidTank[] tanks, boolean exact) {
        boolean multiTank = tanks.length > 1;
        for (int i = 0; i < tanks.length; i++) {
            FluidStack fluidStack = tanks[i].getFluid();
            if (fluidStack == null || fluidStack.amount <= 0) {
                continue;
            }

            String label = multiTank ?
                    TextStyleClass.INFO + "{*gregtech.top.fluid_pipe.tank*} " + TextFormatting.AQUA + (i + 1) +
                            TextStyleClass.INFO + ": " :
                    TextStyleClass.INFO + "{*gregtech.top.fluid_stored*} ";
            probeInfo.text(label + TextFormatting.AQUA + formatFluidAmount(fluidStack.amount, exact) +
                    TextStyleClass.INFO + " " + TextFormatting.WHITE + fluidStack.getLocalizedName());
        }
    }

    private static String formatFluidAmount(int amount, boolean exact) {
        return exact || amount < 10000 ?
                TextFormattingUtil.formatNumbers(amount) + " L" :
                ElementProgress.format(amount, NumberFormat.COMPACT, "L");
    }
}
