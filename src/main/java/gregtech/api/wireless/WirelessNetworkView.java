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
public final class WirelessNetworkView {

    public static final WirelessNetworkView EMPTY = new WirelessNetworkView(
            null, "No Network", BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ZERO);

    private final UUID networkId;
    private final String networkName;
    private final BigInteger stored;
    private final BigInteger inputPerSecond;
    private final BigInteger outputPerSecond;

    public WirelessNetworkView(UUID networkId, String networkName,
                               BigInteger stored,
                               BigInteger inputPerSecond, BigInteger outputPerSecond) {
        this.networkId = networkId;
        this.networkName = networkName;
        this.stored = stored;
        this.inputPerSecond = inputPerSecond;
        this.outputPerSecond = outputPerSecond;
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

    /** Average input EU per second over the rolling statistics window. */
    public BigInteger getInputPerSecond() {
        return inputPerSecond;
    }

    /** Average output EU per second over the rolling statistics window. */
    public BigInteger getOutputPerSecond() {
        return outputPerSecond;
    }

    public boolean isEmpty() {
        return networkId == null;
    }
}
