package gregtech.common.wireless;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.util.GTLog;
import gregtech.api.wireless.IWirelessComputationService;
import gregtech.api.wireless.WirelessComputationView;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server lifecycle owner and API implementation for the wireless computation
 * network. Uplink cloud computation hatches register into team channels;
 * downlink hatches request CWU/t which is aggregated across the channel's
 * registered nodes (passing the same {@code seen} collection through each
 * node, so provider-level per-tick accounting prevents overselling exactly
 * as in the optical network).
 */
public class WirelessComputationServiceImpl implements IWirelessComputationService {

    /** Heartbeat cadence of uplink hatches is 20 ticks; 200 ticks without a beat means the node is gone. */
    private static final long STALE_THRESHOLD_TICKS = 200;
    /** How often stale-node cleanup runs. */
    private static final long CLEANUP_INTERVAL_TICKS = 100;

    private static WirelessComputationServiceImpl INSTANCE;
    private WirelessComputationSavedData savedData;
    private long cleanupTimer;

    public static WirelessComputationServiceImpl getInstance() { return INSTANCE; }
    public static IWirelessComputationService getService() { return INSTANCE; }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        INSTANCE = this;
        savedData = WirelessComputationSavedData.loadOrCreate(world);
        GTLog.logger.info("WirelessComputationService: Initialized team computation channel service.");
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        if (savedData != null) savedData.flushDirtyNetworks();
        savedData = null;
        WirelessComputationSavedData.clearInstance();
        INSTANCE = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || savedData == null) return;
        boolean anyDirty = false;
        for (Map.Entry<UUID, WirelessComputationNetwork> entry : savedData.getAllNetworks().entrySet()) {
            WirelessComputationNetwork network = entry.getValue();
            network.tickStats();
            network.resetTickAccounting();
            anyDirty |= network.checkAndClearDirty();
        }
        if (anyDirty) savedData.markDirty();

        // Periodic stale-node cleanup
        if (++cleanupTimer >= CLEANUP_INTERVAL_TICKS) {
            cleanupTimer = 0;
            long staleBefore = getGameTime() - STALE_THRESHOLD_TICKS;
            for (WirelessComputationNetwork network : savedData.getAllNetworks().values()) {
                boolean removedAny = false;
                for (WirelessComputationChannel channel : network.getChannels()) {
                    removedAny |= channel.removeStaleNodes(staleBefore) > 0;
                }
                if (removedAny) savedData.markDirty();
            }
        }
    }

    private static long getGameTime() {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        var world = server == null ? null : server.getWorld(0);
        return world == null ? 0L : world.getTotalWorldTime();
    }

    @Override
    public WirelessComputationView getView(UUID actor, int channelId) {
        WirelessComputationNetwork network = getNetwork(actor, false);
        if (network == null) return WirelessComputationView.EMPTY;
        WirelessComputationChannel channel = network.getChannel(channelId);
        return channel == null ? WirelessComputationView.EMPTY : toView(network, channel);
    }

    @Override
    public List<WirelessComputationView> getChannels(UUID actor) {
        WirelessComputationNetwork network = getNetwork(actor, false);
        if (network == null) return new ArrayList<>();
        List<WirelessComputationView> views = new ArrayList<>();
        for (WirelessComputationChannel channel : network.getChannels()) {
            views.add(toView(network, channel));
        }
        return views;
    }

    @Override
    public int createChannel(UUID actor, String name) {
        WirelessComputationNetwork network = getNetwork(actor, true);
        if (network == null) return -1;
        WirelessComputationChannel channel = network.createChannel(name);
        savedData.markDirty();
        return channel.getChannelId();
    }

    @Override
    public boolean renameChannel(UUID actor, int channelId, String name) {
        WirelessComputationNetwork network = getNetwork(actor, false);
        return network != null && network.renameChannel(channelId, name);
    }

    @Override
    public boolean deleteChannel(UUID actor, int channelId) {
        WirelessComputationNetwork network = getNetwork(actor, false);
        if (network == null || !network.deleteChannel(channelId)) return false;
        savedData.markDirty();
        return true;
    }

    @Override
    public void registerProvider(UUID actor, int channelId, String key, int tier, int dimension, long position,
                                 IOpticalComputationProvider provider) {
        WirelessComputationNetwork network = getNetwork(actor, true);
        if (network == null) return;
        WirelessComputationChannel channel = network.getChannel(channelId);
        if (channel == null) return;
        channel.registerNode(key, tier, dimension, position, provider, getGameTime());
        savedData.markDirty();
    }

    @Override
    public void unregisterProvider(UUID actor, int channelId, String key) {
        WirelessComputationNetwork network = getNetwork(actor, false);
        if (network == null) return;
        WirelessComputationChannel channel = network.getChannel(channelId);
        if (channel != null && channel.unregisterNode(key)) {
            savedData.markDirty();
        }
    }

    @Override
    public int requestCWUt(UUID actor, int channelId, int cwut, boolean simulate,
                           Collection<IOpticalComputationProvider> seen) {
        if (cwut <= 0) return 0;
        WirelessComputationNetwork network = getNetwork(actor, false);
        if (network == null) return 0;
        WirelessComputationChannel channel = network.getChannel(channelId);
        if (channel == null) return 0;
        return channel.requestCWUt(cwut, simulate, seen);
    }

    private WirelessComputationNetwork getNetwork(UUID actor, boolean create) {
        if (savedData == null) return null;
        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return null;
        return create ? savedData.getOrCreateNetwork(networkId, "Wireless Network") : savedData.getNetwork(networkId);
    }

    private static WirelessComputationView toView(WirelessComputationNetwork network,
                                                  WirelessComputationChannel channel) {
        return new WirelessComputationView(network.getNetworkId(), channel.getChannelId(), channel.getName(),
                channel.getMaxCWUt(), channel.getAllocatedThisTick(), channel.getAllocatedPerSecond(),
                channel.getNodeCount());
    }
}
