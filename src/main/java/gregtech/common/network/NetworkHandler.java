package gregtech.common.network;

import gregtech.common.network.multiblock.CPacketDismantleStructure;
import gregtech.common.network.multiblock.CPacketEncodeStructure;
import gregtech.common.network.multiblock.CPacketEvaluateStructureBuild;
import gregtech.common.network.multiblock.CPacketExecuteStructureBuild;
import gregtech.common.network.multiblock.ClearMoverPreviewPacket;
import gregtech.common.network.multiblock.RotateMoverPreviewPacket;
import gregtech.common.network.multiblock.SPacketStructureBuildBill;
import gregtech.common.network.multiblock.SPacketStructureBuildResult;
import gregtech.common.network.multiblock.StartMoverPreviewPacket;
import gregtech.common.network.multiblock.SwitchMultiblockToolModePacket;

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
        INSTANCE.registerMessage(StartMoverPreviewPacket.Handler.class, StartMoverPreviewPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(ClearMoverPreviewPacket.Handler.class, ClearMoverPreviewPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(RotateMoverPreviewPacket.Handler.class, RotateMoverPreviewPacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(SwitchMultiblockToolModePacket.Handler.class,
                SwitchMultiblockToolModePacket.class, id++, Side.SERVER);
        INSTANCE.registerMessage(CPacketExecuteStructureBuild.Handler.class,
                CPacketExecuteStructureBuild.class, id++, Side.SERVER);
        INSTANCE.registerMessage(SPacketStructureBuildResult.Handler.class,
                SPacketStructureBuildResult.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(CPacketEvaluateStructureBuild.Handler.class,
                CPacketEvaluateStructureBuild.class, id++, Side.SERVER);
        INSTANCE.registerMessage(SPacketStructureBuildBill.Handler.class,
                SPacketStructureBuildBill.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(CPacketDismantleStructure.Handler.class,
                CPacketDismantleStructure.class, id++, Side.SERVER);
        INSTANCE.registerMessage(CPacketEncodeStructure.Handler.class,
                CPacketEncodeStructure.class, id++, Side.SERVER);
    }
}
