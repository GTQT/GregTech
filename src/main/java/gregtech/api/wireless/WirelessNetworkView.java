package gregtech.api.wireless;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Read-only snapshot of a wireless energy network's state.
 * Returned by {@link WirelessEnergyService#getView(UUID)} for UI display and HUD rendering.
 * Reading a view does NOT reset or modify any service-side statistics.
 */
public final class WirelessNetworkView {

    public static final WirelessNetworkView EMPTY = new WirelessNetworkView(
            null, "No Network", BigInteger.ZERO, BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ZERO, 0, 0);

    private final UUID networkId;
    private final String networkName;
    private final BigInteger stored;
    private final BigInteger capacity;
    private final BigInteger inputPerSecond;
    private final BigInteger outputPerSecond;
    private final int nodeCount;
    private final int onlineNodeCount;

    public WirelessNetworkView(UUID networkId, String networkName,
                               BigInteger stored, BigInteger capacity,
                               BigInteger inputPerSecond, BigInteger outputPerSecond,
                               int nodeCount, int onlineNodeCount) {
        this.networkId = networkId;
        this.networkName = networkName;
        this.stored = stored;
        this.capacity = capacity;
        this.inputPerSecond = inputPerSecond;
        this.outputPerSecond = outputPerSecond;
        this.nodeCount = nodeCount;
        this.onlineNodeCount = onlineNodeCount;
    }

    public UUID getNetworkId() {
        return networkId;
    }

    public String getNetworkName() {
        return networkName;
    }

    public BigInteger getStored() {
        return stored;
    }

    public BigInteger getCapacity() {
        return capacity;
    }

    /** Average input EU per second over the rolling statistics window. */
    public BigInteger getInputPerSecond() {
        return inputPerSecond;
    }

    /** Average output EU per second over the rolling statistics window. */
    public BigInteger getOutputPerSecond() {
        return outputPerSecond;
    }

    /** Total number of registered storage nodes (including offline/stale). */
    public int getNodeCount() {
        return nodeCount;
    }

    /** Number of currently online storage nodes. */
    public int getOnlineNodeCount() {
        return onlineNodeCount;
    }

    public boolean isEmpty() {
        return networkId == null;
    }
}
