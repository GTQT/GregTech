package gregtech.common.network.multiblock;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.AbilityPlacementTracker;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureBuildTools;
import gregtech.api.pattern.StructureCellOverrides;
import gregtech.api.pattern.StructureExclusions;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Requests a survival/creative structure build for the controller at {@link #pos}.
 *
 * <p>The wizard client sends the channel values that were used to build its preview so the server
 * resolves the same layout. Result is reported through {@link SPacketStructureBuildResult}.
 */
public class CPacketExecuteStructureBuild implements IMessage {

    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;
    /** Ticks a player has to wait between two accepted build requests. */
    private static final int BUILD_COOLDOWN_TICKS = 40;
    /** Upper bound for a single channel map; real structures need a handful of entries. */
    private static final int MAX_CHANNEL_ENTRIES = 256;
    /** Maximum accepted ability count; placements beyond this are a client bug or a forged packet. */
    private static final int MAX_ABILITY_COUNT = 1024;
    private static final int MAX_CHANNEL_KEY_LENGTH = 128;

    private static final Map<UUID, long[]> LAST_BUILD = new ConcurrentHashMap<>();

    private BlockPos pos;
    private Map<String, Integer> channels = new TreeMap<>();
    private Map<BlockPos, ItemStack> cellOverrides = new HashMap<>();
    private boolean useNetworkStock = true;
    private boolean malformed;

    public CPacketExecuteStructureBuild() {}

    public CPacketExecuteStructureBuild(BlockPos pos, Map<String, Integer> channels) {
        this(pos, channels, null);
    }

    public CPacketExecuteStructureBuild(BlockPos pos, Map<String, Integer> channels,
                                        Map<BlockPos, ItemStack> cellOverrides) {
        this(pos, channels, cellOverrides, true);
    }

    public CPacketExecuteStructureBuild(BlockPos pos, Map<String, Integer> channels,
                                        Map<BlockPos, ItemStack> cellOverrides, boolean useNetworkStock) {
        this.useNetworkStock = useNetworkStock;
        this.pos = pos;
        this.channels = channels == null ? new TreeMap<>() : new TreeMap<>(channels);
        this.cellOverrides = new HashMap<>();
        if (cellOverrides != null) {
            for (Map.Entry<BlockPos, ItemStack> entry : cellOverrides.entrySet()) {
                if (entry.getKey() != null && StructureCellOverrides.isUsable(entry.getValue())) {
                    this.cellOverrides.put(entry.getKey(),
                            StructureCellOverrides.normalize(entry.getValue()));
                }
            }
        }
    }

    public boolean isNetworkStock() {
        return useNetworkStock;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer buffer = new PacketBuffer(buf);
        pos = buffer.readBlockPos();
        channels = new TreeMap<>();
        int size = buffer.readVarInt();
        // A forged length must not allocate or spin; reject the whole map instead of throwing on the netty thread.
        if (size < 0 || size > MAX_CHANNEL_ENTRIES) {
            malformed = true;
            return;
        }
        for (int i = 0; i < size; i++) {
            if (!buffer.isReadable()) {
                channels.clear();
                malformed = true;
                return;
            }
            channels.put(ByteBufUtils.readUTF8String(buffer), buffer.readVarInt());
        }
        cellOverrides = new HashMap<>();
        int overrideCount = buffer.readVarInt();
        if (overrideCount < 0 || overrideCount > StructureCellOverrides.MAX_OVERRIDES) {
            malformed = true;
            return;
        }
        for (int i = 0; i < overrideCount; i++) {
            if (!buffer.isReadable()) {
                malformed = true;
                return;
            }
            BlockPos cellPos = buffer.readBlockPos();
            ItemStack stack = ByteBufUtils.readItemStack(buffer);
            if (cellPos != null && StructureCellOverrides.isUsable(stack)) {
                cellOverrides.put(cellPos, StructureCellOverrides.normalize(stack));
            }
        }
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

    public static class Handler implements IMessageHandler<CPacketExecuteStructureBuild, IMessage> {

        @Override
        public IMessage onMessage(CPacketExecuteStructureBuild message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(
                    () -> execute(player, message));
            return null;
        }

        private static void execute(EntityPlayerMP player, CPacketExecuteStructureBuild message) {
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
            ItemStack held = heldBuilder(player);
            if (held.isEmpty()) {
                return;
            }
            if (!readyForNextBuild(player)) {
                player.sendStatusMessage(
                        new TextComponentTranslation("gregtech.multiblock.build_wizard.cooldown"), true);
                return;
            }
            Map<String, Integer> channels = sanitizeChannels(controller, message.channels);
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
            StructureOperationRequest request = StructureOperationRequest.build(
                    player, controller, StructureOrientation.fromController(controller),
                    channels, held).withCellOverrides(overrides).withNetworkStock(message.useNetworkStock);
            LAST_BUILD.put(player.getUniqueID(),
                    new long[] { player.world.provider.getDimension(), player.world.getTotalWorldTime() });
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

        /**
         * Rejects rapid repeats of the build action. The window is tracked per player and per dimension so a
         * dimension change neither blocks the first build nor inherits a stale timer.
         */
        private static boolean readyForNextBuild(EntityPlayerMP player) {
            long[] last = LAST_BUILD.get(player.getUniqueID());
            if (last == null || last[0] != player.world.provider.getDimension()) {
                return true;
            }
            long elapsed = player.world.getTotalWorldTime() - last[1];
            return elapsed < 0 || elapsed >= BUILD_COOLDOWN_TICKS;
        }

        /**
         * Filters the client channel map down to what the controller can actually consume: unknown keys are
         * dropped, tiered casing values are clamped to the pattern range and ability caps are bounded.
         *
         * <p>Only keys advertised by {@link MultiblockControllerBase#getSupportedChannels()} survive, so a client
         * cannot smuggle arbitrary entries into the build resolver. The {@code structure_piece} selection is
         * advertised for multi-piece patterns and therefore keeps working.
         */
        private static Map<String, Integer> sanitizeChannels(MultiblockControllerBase controller,
                                                             Map<String, Integer> requested) {
            Map<String, Integer> sanitized = new TreeMap<>();
            if (requested == null || requested.isEmpty()) {
                return sanitized;
            }
            Map<String, int[]> ranges = new HashMap<>();
            List<StructureChannel> supported = controller.getSupportedChannels();
            for (StructureChannel channel : supported) {
                if (channel == null || channel.getName() == null) {
                    continue;
                }
                int[] range = controller.getChannelRange(channel);
                ranges.put(channel.getName(),
                        range != null && range.length >= 2 ? range : new int[] { 0, 0 });
            }
            for (Map.Entry<String, Integer> entry : requested.entrySet()) {
                if (sanitized.size() >= MAX_CHANNEL_ENTRIES) {
                    break;
                }
                String key = entry.getKey();
                if (key == null || key.isEmpty() || key.length() > MAX_CHANNEL_KEY_LENGTH) {
                    continue;
                }
                Integer raw = entry.getValue();
                int value = raw == null ? 0 : raw;
                if (key.startsWith(AbilityPlacementTracker.ABILITY_COUNT_PREFIX)) {
                    sanitized.put(key, Math.max(0, Math.min(MAX_ABILITY_COUNT, value)));
                    continue;
                }
                if (StructureExclusions.isExclusionKey(key)) {
                    int pieceIndex = StructureExclusions.indexOf(key);
                    if (pieceIndex > 0) {
                        sanitized.put(StructureExclusions.key(pieceIndex), value == 0 ? 0 : 1);
                    }
                    continue;
                }
                // Global build options are not template elements, so collectChannelsFromMultiPiece never lists
                // them. Dropping them here would make the wire build disagree with the preview (which does see
                // them), e.g. the "no hatches" switch would silently place hatches again.
                if (GTStructureChannels.NO_HATCH.getName().equals(key)) {
                    sanitized.put(key, value == 0 ? 0 : 1);
                    continue;
                }
                int[] range = ranges.get(key);
                if (range == null) {
                    continue;
                }
                int min = Math.min(range[0], range[1]);
                int max = Math.max(range[0], range[1]);
                sanitized.put(key, Math.max(min, Math.min(max, value)));
            }
            return sanitized;
        }

        /**
         * Returns the builder item the player is holding, or {@link ItemStack#EMPTY} when the main and off hand
         * hold neither the built-in builder nor a tool registered through
         * {@link gregtech.api.pattern.StructureBuildTools}.
         */
        private static ItemStack heldBuilder(EntityPlayerMP player) {
            ItemStack main = player.getHeldItemMainhand();
            if (isBuildTool(main)) {
                return main;
            }
            ItemStack off = player.getHeldItemOffhand();
            if (isBuildTool(off)) {
                return off;
            }
            return ItemStack.EMPTY;
        }

        private static boolean isBuildTool(ItemStack stack) {
            return MetaItems.MULTIBLOCK_BUILDER.isItemEqual(stack) ||
                    StructureBuildTools.isRegistered(stack);
        }
    }
}
