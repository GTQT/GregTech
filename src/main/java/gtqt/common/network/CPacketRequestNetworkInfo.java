package gtqt.common.network;

import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class CPacketRequestNetworkInfo implements IMessage {

    public CPacketRequestNetworkInfo() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<CPacketRequestNetworkInfo, IMessage> {
        @Override
        public IMessage onMessage(CPacketRequestNetworkInfo message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                UUID playerId = player.getUniqueID();
                WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
                if (service == null) return;

                WirelessNetworkView view = service.getView(playerId);
                if (!view.isEmpty()) {
                    NetworkHandler.INSTANCE.sendTo(new SPacketWirelessNetworkInfo(
                            view.getStored(),
                            view.getInputPerSecond(), view.getOutputPerSecond()), player);
                }
            });
            return null;
        }
    }
}
