package gregtech.core.network.packets;

import gregtech.api.network.IClientExecutor;
import gregtech.api.network.IPacket;
import gregtech.api.network.IServerExecutor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import gtqt.api.util.wireless.ClientWirelessHUD;
import gtqt.api.util.wireless.NetworkManager;
import gtqt.api.util.wireless.NetworkNode;

import java.math.BigInteger;
import java.util.UUID;

public abstract class PacketWirelessNetwork implements IPacket {

    public PacketWirelessNetwork() {}

    public static class Server extends PacketWirelessNetwork implements IServerExecutor {

        public Server() {}

        @Override
        public void encode(PacketBuffer buf) {

        }

        @Override
        public void decode(PacketBuffer buf) {

        }

        @Override
        public void executeServer(NetHandlerPlayServer handler) {
            EntityPlayerMP player = handler.player;
            World world = player.getServerWorld();
            UUID playerId = player.getUniqueID();

            NetworkNode node = NetworkManager.INSTANCE.getNetworkForPlayer(world, playerId);
            if (node != null) {
                BigInteger stored = node.getTotalStored();
                BigInteger capacity = node.getTotalCapacity();
                Client response = new Client(stored, capacity);
                gregtech.api.GregTechAPI.networkHandler.sendTo(response, player);
            }
        }
    }

    public static class Client extends PacketWirelessNetwork implements IClientExecutor {

        private BigInteger stored;
        private BigInteger capacity;

        public Client() {}

        public Client(BigInteger stored, BigInteger capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        @Override
        public void encode(PacketBuffer buf) {
            byte[] storedBytes = stored.toByteArray();
            buf.writeInt(storedBytes.length);
            buf.writeBytes(storedBytes);

            byte[] capBytes = capacity.toByteArray();
            buf.writeInt(capBytes.length);
            buf.writeBytes(capBytes);
        }

        @Override
        public void decode(PacketBuffer buf) {
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
        @SideOnly(Side.CLIENT)
        public void executeClient(NetHandlerPlayClient handler) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientWirelessHUD.updateInfo(stored, capacity);
            });
        }
    }
}
