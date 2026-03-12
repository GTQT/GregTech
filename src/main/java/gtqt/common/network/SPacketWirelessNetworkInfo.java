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

    public SPacketWirelessNetworkInfo() {}

    public SPacketWirelessNetworkInfo(BigInteger stored, BigInteger capacity) {
        this.stored = stored;
        this.capacity = capacity;
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
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] storedBytes = stored.toByteArray();
        buf.writeInt(storedBytes.length);
        buf.writeBytes(storedBytes);

        byte[] capBytes = capacity.toByteArray();
        buf.writeInt(capBytes.length);
        buf.writeBytes(capBytes);
    }

    public static class Handler implements IMessageHandler<SPacketWirelessNetworkInfo, IMessage> {
        @Override
        public IMessage onMessage(SPacketWirelessNetworkInfo message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Minecraft.getMinecraft().addScheduledTask(() -> ClientWirelessHUD.updateInfo(message.stored, message.capacity));
            }
            return null;
        }
    }
}
