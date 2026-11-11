package gregtech.common.network.multiblock;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureBuildPlanner;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureCellOverrides;
import gregtech.api.pattern.StructureEncodeHandlers;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.casing.GTStructureChannels;

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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Asks the server to write the current configuration's material list into a mod-specific pattern item. The server
 * re-evaluates the structure first, so the pattern always matches what a real build would consume.
 */
public class CPacketEncodeStructure implements IMessage {

    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;
    private static final int MAX_CHANNEL_ENTRIES = 256;
    private static final int MAX_CHANNEL_KEY_LENGTH = 128;

    private BlockPos pos;
    private Map<String, Integer> channels = new TreeMap<>();
    private Map<BlockPos, ItemStack> cellOverrides = new HashMap<>();
    private boolean useNetworkStock = true;
    private boolean malformed;

    public CPacketEncodeStructure() {}

    public CPacketEncodeStructure(BlockPos pos, Map<String, Integer> channels) {
        this(pos, channels, null);
    }

    public CPacketEncodeStructure(BlockPos pos, Map<String, Integer> channels,
                                  Map<BlockPos, ItemStack> cellOverrides) {
        this(pos, channels, cellOverrides, true);
    }

    public CPacketEncodeStructure(BlockPos pos, Map<String, Integer> channels,
                                  Map<BlockPos, ItemStack> cellOverrides, boolean useNetworkStock) {
        this.pos = pos;
        this.channels = channels == null ? new TreeMap<>() : new TreeMap<>(channels);
        this.cellOverrides = new HashMap<>();
        if (cellOverrides != null) {
            for (Map.Entry<BlockPos, ItemStack> entry : cellOverrides.entrySet()) {
                if (entry.getKey() != null && StructureCellOverrides.isUsable(entry.getValue())) {
                    this.cellOverrides.put(entry.getKey(), StructureCellOverrides.normalize(entry.getValue()));
                }
            }
        }
        this.useNetworkStock = useNetworkStock;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        pos = buffer.readBlockPos();
        int channelCount = buffer.readVarInt();
        if (channelCount < 0 || channelCount > MAX_CHANNEL_ENTRIES) {
            malformed = true;
            return;
        }
        Map<String, Integer> readChannels = new TreeMap<>();
        for (int i = 0; i < channelCount; i++) {
            if (!buffer.isReadable()) {
                malformed = true;
                return;
            }
            String key = ByteBufUtils.readUTF8String(buffer);
            int value = buffer.readVarInt();
            if (key.isEmpty() || key.length() > MAX_CHANNEL_KEY_LENGTH) {
                continue;
            }
            readChannels.put(key, value);
        }
        channels = readChannels;
        int overrideCount = buffer.readVarInt();
        if (overrideCount < 0 || overrideCount > StructureCellOverrides.MAX_OVERRIDES) {
            malformed = true;
            return;
        }
        Map<BlockPos, ItemStack> readOverrides = new HashMap<>();
        for (int i = 0; i < overrideCount && buffer.isReadable(); i++) {
            BlockPos cellPos = buffer.readBlockPos();
            ItemStack stack = ByteBufUtils.readItemStack(buffer);
            if (cellPos != null && StructureCellOverrides.isUsable(stack)) {
                readOverrides.put(cellPos, StructureCellOverrides.normalize(stack));
            }
        }
        cellOverrides = readOverrides;
        useNetworkStock = buffer.readBoolean();
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
        buffer.writeVarInt(cellOverrides.size());
        for (Map.Entry<BlockPos, ItemStack> entry : cellOverrides.entrySet()) {
            buffer.writeBlockPos(entry.getKey());
            ByteBufUtils.writeItemStack(buffer, entry.getValue());
        }
        buffer.writeBoolean(useNetworkStock);
    }

    public static class Handler implements IMessageHandler<CPacketEncodeStructure, IMessage> {

        @Override
        public IMessage onMessage(CPacketEncodeStructure message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(
                    () -> encode(player, message));
            return null;
        }

        private static void encode(EntityPlayerMP player, CPacketEncodeStructure message) {
            if (message.malformed || message.pos == null ||
                    player.getDistanceSq(message.pos) > MAX_DISTANCE_SQR) {
                return;
            }
            if (!player.world.isBlockLoaded(message.pos, false)) {
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
            Map<BlockPos, ItemStack> overrides = new HashMap<>();
            for (Map.Entry<BlockPos, ItemStack> entry : message.cellOverrides.entrySet()) {
                BlockPos cellPos = entry.getKey();
                if (cellPos == null || !StructureCellOverrides.isUsable(entry.getValue())) {
                    continue;
                }
                if (Math.abs(cellPos.getX()) > StructureCellOverrides.MAX_OFFSET ||
                        Math.abs(cellPos.getY()) > StructureCellOverrides.MAX_OFFSET ||
                        Math.abs(cellPos.getZ()) > StructureCellOverrides.MAX_OFFSET) {
                    continue;
                }
                overrides.put(cellPos, StructureCellOverrides.normalize(entry.getValue()));
                if (overrides.size() >= StructureCellOverrides.MAX_OVERRIDES) {
                    break;
                }
            }
            StructureBuildResult result = StructureBuildPlanner.evaluate(
                    player, controller, StructureOrientation.fromController(controller),
                    normalizeChannels(message.channels), overrides, message.useNetworkStock);
            List<ItemStack> required = new ArrayList<>();
            for (StructureBuildResult.ItemAmount amount : result.getRequiredItems()) {
                ItemStack stack = amount.getStack().copy();
                stack.setCount(amount.getCount());
                required.add(stack);
            }
            if (!StructureEncodeHandlers.hasHandler()) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "gregtech.multiblock.build_wizard.encode_unavailable"), true);
                return;
            }
            boolean encoded = StructureEncodeHandlers.encode(player, controller, required);
            player.sendStatusMessage(new TextComponentTranslation(encoded
                    ? "gregtech.multiblock.build_wizard.encode_done"
                    : "gregtech.multiblock.build_wizard.encode_unavailable"), true);
        }

        @NotNull
        private static Map<String, Integer> normalizeChannels(@NotNull Map<String, Integer> channels) {
            if (channels.isEmpty()) {
                return channels;
            }
            Map<String, Integer> normalized = new HashMap<>(channels);
            Integer noHatch = normalized.get(GTStructureChannels.NO_HATCH.getName());
            if (noHatch != null) {
                normalized.put(GTStructureChannels.NO_HATCH.getName(), noHatch == 0 ? 0 : 1);
            }
            return normalized;
        }
    }
}
