package gregtech.api.utils;

import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class FluidStackHelper {

    public static FluidStack readFromBuf(ByteBuf dataIn) {
        PacketBuffer buf = new PacketBuffer(dataIn);
        try {
            NBTTagCompound nbt = buf.readCompoundTag();
            FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(nbt);
            if (fluidStack != null) {
                fluidStack.amount = buf.readInt();
            }
            return fluidStack;
        } catch (IOException e) {
            GTLog.logger.error("Cannot read fluid stack info from buffer!");
            return null;
        }
    }

    public static void writeToBuf(ByteBuf dataOut, FluidStack fluidStack) {
        PacketBuffer buf = new PacketBuffer(dataOut);
        NBTTagCompound nbt = new NBTTagCompound();
        fluidStack.writeToNBT(nbt);
        try {
            buf.writeCompoundTag(nbt);
            buf.writeInt(fluidStack.amount);
        } catch (Exception e) {
            GTLog.logger.error("Cannot write fluid stack info to buffer!");
        }
    }
}
