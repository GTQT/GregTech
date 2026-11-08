package gregtech.common.network.multiblock;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntime;
import gregtech.common.items.MetaItems;
import gregtech.common.network.NetworkHandler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.TreeMap;

/**
 * Requests a survival/creative structure build for the controller at {@link #pos}.
 *
 * <p>The wizard client sends the channel values that were used to build its preview so the server
 * resolves the same layout. Result is reported through {@link SPacketStructureBuildResult}.
 */
public class CPacketExecuteStructureBuild implements IMessage {

    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;

    private BlockPos pos;
    private Map<String, Integer> channels = new TreeMap<>();

    public CPacketExecuteStructureBuild() {}

    public CPacketExecuteStructureBuild(BlockPos pos, Map<String, Integer> channels) {
        this.pos = pos;
        this.channels = channels == null ? new TreeMap<>() : new TreeMap<>(channels);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        pos = buffer.readBlockPos();
        int size = buffer.readVarInt();
        channels = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            channels.put(ByteBufUtils.readUTF8String(buffer), buffer.readVarInt());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(channels.size());
        for (Map.Entry<String, Integer> entry : channels.entrySet()) {
            ByteBufUtils.writeUTF8String(buffer, entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    public static class Handler implements IMessageHandler<CPacketExecuteStructureBuild, IMessage> {

        @Override
        public IMessage onMessage(CPacketExecuteStructureBuild message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(
                    () -> execute(player, message));
            return null;
        }

        private static void execute(EntityPlayerMP player, CPacketExecuteStructureBuild message) {
            if (message.pos == null || player.getDistanceSq(message.pos) > MAX_DISTANCE_SQR) {
                return;
            }
            net.minecraft.tileentity.TileEntity tile = player.world.getTileEntity(message.pos);
            if (!(tile instanceof gregtech.api.metatileentity.interfaces.IGregTechTileEntity)) {
                return;
            }
            gregtech.api.metatileentity.MetaTileEntity metaTileEntity =
                    ((gregtech.api.metatileentity.interfaces.IGregTechTileEntity) tile).getMetaTileEntity();
            if (!(metaTileEntity instanceof MultiblockControllerBase)) {
                return;
            }
            MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
            ItemStack held = heldBuilder(player);
            if (held.isEmpty()) {
                return;
            }
            StructureOperationRequest request = StructureOperationRequest.build(
                    player, controller, StructureOrientation.fromController(controller),
                    message.channels, held);
            StructureBuildResult result;
            boolean handledByController = controller.autoBuildStructure(request);
            if (handledByController) {
                result = StructureBuildResult.empty();
            } else {
                StructureRuntime runtime = controller.getOrCreateStructureRuntime();
                result = runtime.buildAllPieces(request);
            }
            NetworkHandler.INSTANCE.sendTo(new SPacketStructureBuildResult(result, handledByController), player);
            if (result.getPlacedCells() <= 0 && result.getMissingItems().isEmpty() && !handledByController) {
                player.sendStatusMessage(
                        new TextComponentTranslation("gregtech.multiblock.build_wizard.nothing_to_build"), true);
            }
        }

        private static ItemStack heldBuilder(EntityPlayerMP player) {
            ItemStack main = player.getHeldItemMainhand();
            if (MetaItems.MULTIBLOCK_BUILDER.isItemEqual(main)) {
                return main;
            }
            ItemStack off = player.getHeldItemOffhand();
            if (MetaItems.MULTIBLOCK_BUILDER.isItemEqual(off)) {
                return off;
            }
            return ItemStack.EMPTY;
        }
    }
}
