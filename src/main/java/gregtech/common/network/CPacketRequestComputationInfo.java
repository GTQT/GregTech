package gregtech.common.network;

import gregtech.api.wireless.IWirelessComputationService;
import gregtech.api.wireless.WirelessComputationView;
import gregtech.common.wireless.WirelessComputationServiceImpl;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class CPacketRequestComputationInfo implements IMessage {

    public CPacketRequestComputationInfo() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<CPacketRequestComputationInfo, IMessage> {
        @Override
        public IMessage onMessage(CPacketRequestComputationInfo message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                UUID playerId = player.getUniqueID();
                IWirelessComputationService service = WirelessComputationServiceImpl.getService();
                if (service == null) return;

                WirelessComputationView view = service.getView(playerId, 0);
                if (!view.isEmpty()) {
                    NetworkHandler.INSTANCE.sendTo(new SPacketWirelessComputationInfo(
                            view.getMaxCWUt(), view.getAllocatedCWUt(),
                            view.getAllocatedPerSecond(), view.getNodeCount()), player);
                }
            });
            return null;
        }
    }
}
