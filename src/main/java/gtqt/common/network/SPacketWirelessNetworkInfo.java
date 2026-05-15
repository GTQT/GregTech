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
    private BigInteger totalInput;
    private BigInteger totalOutput;

    public SPacketWirelessNetworkInfo() {}

    public SPacketWirelessNetworkInfo(BigInteger stored, BigInteger totalInput, BigInteger totalOutput) {
        this.stored = stored;
        this.totalInput = totalInput;
        this.totalOutput = totalOutput;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int storedLen = buf.readInt();
        byte[] storedBytes = new byte[storedLen];
        buf.readBytes(storedBytes);
        stored = new BigInteger(storedBytes);

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
                Minecraft.getMinecraft().addScheduledTask(() ->
                        ClientWirelessHUD.updateInfo(message.stored, message.totalInput, message.totalOutput));
            }
            return null;
        }
    }
}
