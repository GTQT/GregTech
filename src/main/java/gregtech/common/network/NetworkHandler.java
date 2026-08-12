package gregtech.common.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("gtqt_wireless");
    private static int id = 0;

    public static void registerMessages() {
        INSTANCE.registerMessage(CPacketRequestNetworkInfo.Handler.class, CPacketRequestNetworkInfo.class, id++, Side.SERVER);
        INSTANCE.registerMessage(SPacketWirelessNetworkInfo.Handler.class, SPacketWirelessNetworkInfo.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(CPacketRequestComputationInfo.Handler.class, CPacketRequestComputationInfo.class, id++, Side.SERVER);
        INSTANCE.registerMessage(SPacketWirelessComputationInfo.Handler.class, SPacketWirelessComputationInfo.class, id++, Side.CLIENT);
    }
}
