package gregtech.common.network;

import gregtech.api.wireless.ClientWirelessComputationHUD;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

public class SPacketWirelessComputationInfo implements IMessage {
    private int maxCWUt;
    private int allocatedCWUt;
    private int allocatedPerSecond;
    private int nodeCount;

    public SPacketWirelessComputationInfo() {}

    public SPacketWirelessComputationInfo(int maxCWUt, int allocatedCWUt, int allocatedPerSecond, int nodeCount) {
        this.maxCWUt = maxCWUt;
        this.allocatedCWUt = allocatedCWUt;
        this.allocatedPerSecond = allocatedPerSecond;
        this.nodeCount = nodeCount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        maxCWUt = buf.readInt();
        allocatedCWUt = buf.readInt();
        allocatedPerSecond = buf.readInt();
        nodeCount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(maxCWUt);
        buf.writeInt(allocatedCWUt);
        buf.writeInt(allocatedPerSecond);
        buf.writeInt(nodeCount);
    }

    public static class Handler implements IMessageHandler<SPacketWirelessComputationInfo, IMessage> {
        @Override
        public IMessage onMessage(SPacketWirelessComputationInfo message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Minecraft.getMinecraft().addScheduledTask(() ->
                        ClientWirelessComputationHUD.updateInfo(message.maxCWUt, message.allocatedCWUt,
                                message.allocatedPerSecond, message.nodeCount));
            }
            return null;
        }
    }
}
