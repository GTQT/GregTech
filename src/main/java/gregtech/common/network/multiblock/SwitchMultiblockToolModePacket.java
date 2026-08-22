package gregtech.common.network.multiblock;

import gregtech.common.items.behaviors.MultiblockToolBehavior;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

public final class SwitchMultiblockToolModePacket implements IMessage {
    private boolean offHand;

    public SwitchMultiblockToolModePacket() {}

    public SwitchMultiblockToolModePacket(EnumHand hand) {
        this.offHand = hand == EnumHand.OFF_HAND;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        offHand = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(offHand);
    }

    public static final class Handler implements IMessageHandler<SwitchMultiblockToolModePacket, IMessage> {
        @Override
        public IMessage onMessage(SwitchMultiblockToolModePacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> MultiblockToolBehavior.cycleMode(
                    player, message.offHand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND));
            return null;
        }
    }
}
