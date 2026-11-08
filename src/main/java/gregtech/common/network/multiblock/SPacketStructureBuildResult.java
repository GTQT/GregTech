package gregtech.common.network.multiblock;

import gregtech.api.pattern.StructureBuildResult;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a wizard build request. The wizard screen reads the static snapshot; the packet deliberately
 * keeps no client-only classes so the common network handler is safe on a dedicated server.
 */
public class SPacketStructureBuildResult implements IMessage {

    @SideOnly(Side.CLIENT)
    private static volatile Snapshot lastSnapshot;

    private int placedCells;
    private boolean handledByController;
    private List<ItemStack> missingItems = Collections.emptyList();

    public SPacketStructureBuildResult() {}

    public SPacketStructureBuildResult(StructureBuildResult result, boolean handledByController) {
        this.placedCells = result.getPlacedCells();
        this.handledByController = handledByController;
        List<ItemStack> missing = new ArrayList<>();
        for (StructureBuildResult.ItemAmount amount : result.getMissingItems()) {
            ItemStack stack = amount.getStack();
            stack.setCount(amount.getCount());
            missing.add(stack);
        }
        this.missingItems = missing;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        placedCells = buffer.readVarInt();
        handledByController = buffer.readBoolean();
        int size = buffer.readVarInt();
        List<ItemStack> missing = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buffer);
            stack.setCount(buffer.readVarInt());
            missing.add(stack);
        }
        missingItems = missing;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        buffer.writeVarInt(placedCells);
        buffer.writeBoolean(handledByController);
        buffer.writeVarInt(missingItems.size());
        for (ItemStack stack : missingItems) {
            ByteBufUtils.writeItemStack(buffer, stack);
            buffer.writeVarInt(stack.getCount());
        }
    }

    public boolean isHandledByController() {
        return handledByController;
    }

    public int getPlacedCells() {
        return placedCells;
    }

    public List<ItemStack> getMissingItems() {
        return missingItems;
    }

    @SideOnly(Side.CLIENT)
    public static Snapshot takeSnapshot() {
        Snapshot snapshot = lastSnapshot;
        lastSnapshot = null;
        return snapshot;
    }

    public static class Handler implements IMessageHandler<SPacketStructureBuildResult, IMessage> {

        @Override
        public IMessage onMessage(SPacketStructureBuildResult message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                apply(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(SPacketStructureBuildResult message) {
            lastSnapshot = new Snapshot(message.placedCells, message.handledByController, message.missingItems);
        }
    }

    /** Immutable client-side view consumed by the wizard screen. */
    public static final class Snapshot {

        private final int placedCells;
        private final boolean handledByController;
        private final List<ItemStack> missingItems;

        Snapshot(int placedCells, boolean handledByController, List<ItemStack> missingItems) {
            this.placedCells = placedCells;
            this.handledByController = handledByController;
            this.missingItems = Collections.unmodifiableList(new ArrayList<>(missingItems));
        }

        public int getPlacedCells() {
            return placedCells;
        }

        public boolean isHandledByController() {
            return handledByController;
        }

        public List<ItemStack> getMissingItems() {
            return missingItems;
        }
    }
}
