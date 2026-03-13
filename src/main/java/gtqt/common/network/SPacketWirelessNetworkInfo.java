package gtqt.common.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import gtqt.api.util.wireless.ClientWirelessHUD;
import io.netty.buffer.ByteBuf;

import java.math.BigInteger;

public class SPacketWirelessNetworkInfo implements IMessage {
    private BigInteger stored;
    private BigInteger capacity;
    private BigInteger totalInput;
    private BigInteger totalOutput;

    public SPacketWirelessNetworkInfo() {}

    public SPacketWirelessNetworkInfo(BigInteger stored, BigInteger capacity, BigInteger totalInput,
                                      BigInteger totalOutput) {
        this.stored = stored;
        this.capacity = capacity;
        this.totalInput = totalInput;
        this.totalOutput = totalOutput;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int storedLen = buf.readInt();
        byte[] storedBytes = new byte[storedLen];
        buf.readBytes(storedBytes);
        stored = new BigInteger(storedBytes);

        int capLen = buf.readInt();
        byte[] capBytes = new byte[capLen];
        buf.readBytes(capBytes);
        capacity = new BigInteger(capBytes);

        int totalInputLen = buf.readInt();
        byte[] totalInputBytes = new byte[totalInputLen];
        buf.readBytes(totalInputBytes);
        totalInput = new BigInteger(totalInputBytes);

        int totalOutputLen = buf.readInt();
        byte[] totalOutputBytes = new byte[totalOutputLen];
        buf.readBytes(totalOutputBytes);
        totalOutput = new BigInteger(totalOutputBytes);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] storedBytes = stored.toByteArray();
        buf.writeInt(storedBytes.length);
        buf.writeBytes(storedBytes);

        byte[] capBytes = capacity.toByteArray();
        buf.writeInt(capBytes.length);
        buf.writeBytes(capBytes);

        byte[] totalInputBytes = totalInput.toByteArray();
        buf.writeInt(totalInputBytes.length);
        buf.writeBytes(totalInputBytes);

        byte[] totalOutputBytes = totalOutput.toByteArray();
        buf.writeInt(totalOutputBytes.length);
        buf.writeBytes(totalOutputBytes);
    }

    public static class Handler implements IMessageHandler<SPacketWirelessNetworkInfo, IMessage> {
        @Override
        public IMessage onMessage(SPacketWirelessNetworkInfo message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Minecraft.getMinecraft().addScheduledTask(() -> ClientWirelessHUD.updateInfo(message.stored, message.capacity, message.totalInput, message.totalOutput));
            }
            return null;
        }
    }
}
