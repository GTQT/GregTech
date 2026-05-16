package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;
import org.jetbrains.annotations.NotNull;

public class DrumInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":drum_info_provider";
    }

    @Override
    public void addProbeInfo(@NotNull ProbeMode mode, @NotNull IProbeInfo probeInfo, @NotNull EntityPlayer player,
                             @NotNull World world, @NotNull IBlockState blockState, @NotNull IProbeHitData data) {
        if (!blockState.getBlock().hasTileEntity(blockState)) {
            return;
        }

        TileEntity tileEntity = world.getTileEntity(data.getPos());
        if (!(tileEntity instanceof IGregTechTileEntity gregTechTile) ||
                !(gregTechTile.getMetaTileEntity() instanceof MetaTileEntityDrum drum)) {
            return;
        }

        IFluidTankProperties[] tankProperties = drum.getFluidInventory().getTankProperties();
        if (tankProperties.length == 0) {
            return;
        }

        IFluidTankProperties tank = tankProperties[0];
        int capacity = tank.getCapacity();
        if (capacity <= 0) {
            return;
        }

        FluidStack fluidStack = tank.getContents();
        int amount = fluidStack == null ? 0 : fluidStack.amount;
        boolean exact = player.isSneaking();

        probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.fluid_capacity*} " + TextFormatting.AQUA +
                TextFormattingUtil.formatNumbers(capacity) + TextStyleClass.INFO + " L");
        if (fluidStack != null) {
            probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.fluid_stored*} " + TextFormatting.AQUA +
                    formatFluidAmount(amount, exact) + TextStyleClass.INFO + " " +
                    TextFormatting.WHITE + fluidStack.getLocalizedName());
        }
        probeInfo.progress(amount, capacity, probeInfo.defaultProgressStyle()
                .numberFormat(exact || amount < 10000 ? NumberFormat.COMMAS : NumberFormat.COMPACT)
                .suffix(" / " + formatFluidAmount(capacity, exact))
                .filledColor(0xFF2F9BFF)
                .alternateFilledColor(0xFF62C5FF)
                .borderColor(0xFF555555));
    }

    private static String formatFluidAmount(int amount, boolean exact) {
        return exact || amount < 10000 ?
                TextFormattingUtil.formatNumbers(amount) + " L" :
                ElementProgress.format(amount, NumberFormat.COMPACT, "L");
    }
}
