package gtqt.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import gtqt.api.util.wireless.NetworkManager;
import gtqt.api.util.wireless.NetworkNode;
import io.netty.buffer.ByteBuf;

import java.math.BigInteger;
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
                World world = player.getServerWorld();
                UUID playerId = player.getUniqueID();
                NetworkNode node = NetworkManager.INSTANCE.getNetworkForPlayer(world, playerId);
                if (node != null) {
                    BigInteger stored = node.getTotalStored();
                    BigInteger capacity = node.getTotalCapacity();
                    BigInteger energyIn = node.getTotalInput();
                    BigInteger energyOut = node.getTotalOutput();
                    node.resetStats();
                    NetworkHandler.INSTANCE.sendTo(new SPacketWirelessNetworkInfo(stored, capacity,energyIn,energyOut), player);
                }
            });
            return null;
        }
    }
}
