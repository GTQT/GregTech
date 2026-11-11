package gregtech.common.network.multiblock;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.common.items.MetaItems;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * Dismantles a formed multiblock from the builder screen. The actual recovery logic lives in
 * {@link MultiblockControllerBase#dismantleStructure(net.minecraft.entity.player.EntityPlayer)}; this packet only
 * validates the target and forwards the request.
 */
public class CPacketDismantleStructure implements IMessage {

    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;

    private BlockPos pos;

    public CPacketDismantleStructure() {}

    public CPacketDismantleStructure(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = new PacketBuffer(buf).readBlockPos();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        new PacketBuffer(buf).writeBlockPos(pos);
    }

    public static class Handler implements IMessageHandler<CPacketDismantleStructure, IMessage> {

        @Override
        public IMessage onMessage(CPacketDismantleStructure message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(
                    () -> dismantle(player, message));
            return null;
        }

        private static void dismantle(EntityPlayerMP player, CPacketDismantleStructure message) {
            if (message.pos == null || player.getDistanceSq(message.pos) > MAX_DISTANCE_SQR) {
                return;
            }
            if (!player.world.isBlockLoaded(message.pos, false)) {
                return;
            }
            if (!heldBuilder(player)) {
                return;
            }
            MultiblockControllerBase controller = controllerAt(player, message.pos);
            if (controller == null) {
                return;
            }
            boolean removed = controller.dismantleStructure(player);
            player.sendStatusMessage(new TextComponentTranslation(removed
                    ? "gregtech.multiblock.build_wizard.demolish_done"
                    : "gregtech.multiblock.build_wizard.demolish_failed"), true);
        }

        private static boolean heldBuilder(EntityPlayerMP player) {
            return isBuilder(player.getHeldItemMainhand()) || isBuilder(player.getHeldItemOffhand());
        }

        private static boolean isBuilder(ItemStack stack) {
            return !stack.isEmpty() && MetaItems.MULTIBLOCK_BUILDER.isItemEqual(stack);
        }

        private static MultiblockControllerBase controllerAt(EntityPlayerMP player, BlockPos pos) {
            net.minecraft.tileentity.TileEntity tile = player.world.getTileEntity(pos);
            if (!(tile instanceof gregtech.api.metatileentity.interfaces.IGregTechTileEntity)) {
                return null;
            }
            gregtech.api.metatileentity.MetaTileEntity mte =
                    ((gregtech.api.metatileentity.interfaces.IGregTechTileEntity) tile).getMetaTileEntity();
            return mte instanceof MultiblockControllerBase ? (MultiblockControllerBase) mte : null;
        }
    }
}
