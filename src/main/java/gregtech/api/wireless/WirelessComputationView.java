package gregtech.api.wireless;

import java.util.UUID;

/**
 * Read-only snapshot of a wireless computation channel.
 * Returned by {@link IWirelessComputationService#getView(UUID, int)} for UI display.
 */
public final class WirelessComputationView implements ChannelInfo {

    public static final WirelessComputationView EMPTY = new WirelessComputationView(
            null, -1, "No Network", 0, 0, 0, 0);

    private final UUID networkId;
    private final int channelId;
    private final String networkName;
    /** Sum of registered uplink hatches' CWT[tier] — the channel's hard capacity. */
    private final int maxCWUt;
    /** CWU/t allocated to downlink requests during the current tick. */
    private final int allocatedCWUt;
    /** Average allocated CWU/s over the rolling statistics window. */
    private final int allocatedPerSecond;
    /** Number of registered uplink nodes. */
    private final int nodeCount;

    public WirelessComputationView(UUID networkId, int channelId, String networkName,
                                   int maxCWUt, int allocatedCWUt, int allocatedPerSecond,
                                   int nodeCount) {
        this.networkId = networkId;
        this.channelId = channelId;
        this.networkName = networkName;
        this.maxCWUt = maxCWUt;
        this.allocatedCWUt = allocatedCWUt;
        this.allocatedPerSecond = allocatedPerSecond;
        this.nodeCount = nodeCount;
    }

    public UUID getNetworkId() { return networkId; }
    public int getChannelId() { return channelId; }
    public String getNetworkName() { return networkName; }
    public int getMaxCWUt() { return maxCWUt; }
    public int getAllocatedCWUt() { return allocatedCWUt; }
    public int getAllocatedPerSecond() { return allocatedPerSecond; }
    public int getNodeCount() { return nodeCount; }
    public boolean isEmpty() { return networkId == null; }
}
