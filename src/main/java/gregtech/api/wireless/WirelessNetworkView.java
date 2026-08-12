package gregtech.api.wireless;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Read-only snapshot of a wireless energy network's state.
 * Returned by {@link WirelessEnergyService#getView(UUID)} for UI display and HUD rendering.
 * <p>
 * The wireless pool is truly unbounded — there is no capacity limit.
 * Physical storage capacity is managed by individual PSS units.
 */
public final class WirelessNetworkView implements ChannelInfo {

    public static final WirelessNetworkView EMPTY = new WirelessNetworkView(
            null, -1, "No Network", BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ZERO, false, 0);

    private final UUID networkId;
    private final int channelId;
    private final String networkName;
    private final BigInteger stored;
    private final BigInteger inputPerSecond;
    private final BigInteger outputPerSecond;
    private final boolean wirelessChargingEnabled;
    private final int wirelessChargingSlots;

    public WirelessNetworkView(UUID networkId, int channelId, String networkName,
                               BigInteger stored,
                               BigInteger inputPerSecond, BigInteger outputPerSecond,
                               boolean wirelessChargingEnabled, int wirelessChargingSlots) {
        this.networkId = networkId;
        this.channelId = channelId;
        this.networkName = networkName;
        this.stored = stored;
        this.inputPerSecond = inputPerSecond;
        this.outputPerSecond = outputPerSecond;
        this.wirelessChargingEnabled = wirelessChargingEnabled;
        this.wirelessChargingSlots = wirelessChargingSlots;
    }

    public UUID getNetworkId() {
        return networkId;
    }

    public int getChannelId() {
        return channelId;
    }

    public String getNetworkName() {
        return networkName;
    }

    public BigInteger getStored() {
        return stored;
    }

    /** Average input EU per second over the rolling statistics window. */
    public BigInteger getInputPerSecond() {
        return inputPerSecond;
    }

    /** Average output EU per second over the rolling statistics window. */
    public BigInteger getOutputPerSecond() {
        return outputPerSecond;
    }

    public boolean isWirelessChargingEnabled() {
        return wirelessChargingEnabled;
    }

    public int getWirelessChargingSlots() {
        return wirelessChargingSlots;
    }

    public boolean isEmpty() {
        return networkId == null;
    }
}
