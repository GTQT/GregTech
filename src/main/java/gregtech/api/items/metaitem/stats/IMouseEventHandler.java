package gregtech.api.items.metaitem.stats;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.core.network.packets.PacketItemMouseEvent;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Implement on your {@link IItemComponent} to handle item mouse packets on the server.
 * Client-side mouse event handling must live in client-only classes.
 */
public interface IMouseEventHandler extends IItemComponent {

    default void sendToServer(@NotNull EnumHand hand, @NotNull Consumer<@NotNull PacketBuffer> bufferWriter) {
        PacketItemMouseEvent.toServer(bufferWriter, hand);
    }

    /**
     * Handle the received mouse event on the server side.
     *
     * @param buf          the packet containing the data from the client event
     * @param playerServer the server side counterpart of the client player
     * @param stack        the stack the player was holding upon receiving the packet
     */
    void handleMouseEventServer(@NotNull PacketBuffer buf, @NotNull EntityPlayerMP playerServer,
                                @NotNull EnumHand hand, @NotNull ItemStack stack);

    static @Nullable IMouseEventHandler getHandler(@NotNull ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof MetaItem<?>metaItem) {
            return metaItem.getMouseEventHandler(stack);
        } else if (item instanceof IMouseEventHandler itemHandler) {
            return itemHandler;
        }

        return null;
    }
}
