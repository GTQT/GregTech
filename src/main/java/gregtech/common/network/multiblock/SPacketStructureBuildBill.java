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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative material bill for a build dry run. The wizard screen compares this against the player's
 * inventory instead of trusting its own preview palette.
 */
public class SPacketStructureBuildBill implements IMessage {

    @SideOnly(Side.CLIENT)
    private static volatile Snapshot lastBill;

    private int requestId;
    private int placedCells;
    private List<ItemStack> requiredItems = Collections.emptyList();
    private List<ItemStack> missingItems = Collections.emptyList();
    private Map<String, List<ItemStack>> roleItems = Collections.emptyMap();
    private boolean networkLinked;

    public SPacketStructureBuildBill() {}

    public SPacketStructureBuildBill(int requestId, StructureBuildResult result) {
        this(requestId, result, false);
    }

    public SPacketStructureBuildBill(int requestId, StructureBuildResult result, boolean networkLinked) {
        this.requestId = requestId;
        this.placedCells = result.getPlacedCells();
        this.requiredItems = toStacks(result.getRequiredItems());
        this.missingItems = toStacks(result.getMissingItems());
        this.networkLinked = networkLinked;
        Map<String, List<ItemStack>> roles = new HashMap<>();
        for (Map.Entry<String, List<StructureBuildResult.ItemAmount>> entry : result.getRoleItems().entrySet()) {
            roles.put(entry.getKey(), toStacks(entry.getValue()));
        }
        this.roleItems = roles;
    }

    private static List<ItemStack> toStacks(List<StructureBuildResult.ItemAmount> amounts) {
        List<ItemStack> stacks = new ArrayList<>(amounts.size());
        for (StructureBuildResult.ItemAmount amount : amounts) {
            ItemStack stack = amount.getStack().copy();
            stack.setCount(amount.getCount());
            stacks.add(stack);
        }
        return stacks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        requestId = buffer.readVarInt();
        placedCells = buffer.readVarInt();
        requiredItems = readStacks(buffer);
        missingItems = readStacks(buffer);
        int roleCount = buffer.readVarInt();
        if (roleCount < 0 || roleCount > 128) {
            roleItems = Collections.emptyMap();
            return;
        }
        Map<String, List<ItemStack>> roles = new HashMap<>();
        for (int i = 0; i < roleCount && buffer.isReadable(); i++) {
            String role = ByteBufUtils.readUTF8String(buffer);
            roles.put(role, readStacks(buffer));
        }
        roleItems = roles;
        networkLinked = buffer.readBoolean();
    }

    private static List<ItemStack> readStacks(PacketBuffer buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 4096) {
            return Collections.emptyList();
        }
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size && buffer.isReadable(); i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buffer);
            stack.setCount(buffer.readVarInt());
            stacks.add(stack);
        }
        return stacks;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(placedCells);
        writeStacks(buffer, requiredItems);
        writeStacks(buffer, missingItems);
        buffer.writeVarInt(roleItems.size());
        for (Map.Entry<String, List<ItemStack>> entry : roleItems.entrySet()) {
            ByteBufUtils.writeUTF8String(buffer, entry.getKey());
            writeStacks(buffer, entry.getValue());
        }
        buffer.writeBoolean(networkLinked);
    }

    private static void writeStacks(PacketBuffer buffer, List<ItemStack> stacks) {
        buffer.writeVarInt(stacks.size());
        for (ItemStack stack : stacks) {
            ByteBufUtils.writeItemStack(buffer, stack);
            buffer.writeVarInt(stack.getCount());
        }
    }

    public int getRequestId() {
        return requestId;
    }

    public int getPlacedCells() {
        return placedCells;
    }

    public List<ItemStack> getRequiredItems() {
        return requiredItems;
    }

    public List<ItemStack> getMissingItems() {
        return missingItems;
    }

    @SideOnly(Side.CLIENT)
    public static Snapshot takeBill() {
        Snapshot bill = lastBill;
        lastBill = null;
        return bill;
    }

    public static class Handler implements IMessageHandler<SPacketStructureBuildBill, IMessage> {

        @Override
        public IMessage onMessage(SPacketStructureBuildBill message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                apply(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(SPacketStructureBuildBill message) {
            lastBill = new Snapshot(message.requestId, message.placedCells,
                    message.requiredItems, message.missingItems, message.roleItems, message.networkLinked);
        }
    }

    /** Immutable client-side view consumed by the wizard screen. */
    public static final class Snapshot {

        private final int requestId;
        private final int placedCells;
        private final List<ItemStack> requiredItems;
        private final List<ItemStack> missingItems;
        private final Map<String, List<ItemStack>> roleItems;
        private final boolean networkLinked;

        Snapshot(int requestId, int placedCells, List<ItemStack> requiredItems, List<ItemStack> missingItems,
                 Map<String, List<ItemStack>> roleItems, boolean networkLinked) {
            this.requestId = requestId;
            this.placedCells = placedCells;
            this.requiredItems = Collections.unmodifiableList(new ArrayList<>(requiredItems));
            this.missingItems = Collections.unmodifiableList(new ArrayList<>(missingItems));
            this.networkLinked = networkLinked;
            Map<String, List<ItemStack>> roles = new HashMap<>();
            for (Map.Entry<String, List<ItemStack>> entry : roleItems.entrySet()) {
                roles.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            this.roleItems = Collections.unmodifiableMap(roles);
        }

        public int getRequestId() {
            return requestId;
        }

        public int getPlacedCells() {
            return placedCells;
        }

        public List<ItemStack> getRequiredItems() {
            return requiredItems;
        }

        public List<ItemStack> getMissingItems() {
            return missingItems;
        }

        public boolean isNetworkLinked() {
            return networkLinked;
        }

        public Map<String, List<ItemStack>> getRoleItems() {
            return roleItems;
        }
    }
}
