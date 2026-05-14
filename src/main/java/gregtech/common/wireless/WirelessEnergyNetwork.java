package gregtech.common.wireless;

import net.minecraft.nbt.NBTTagCompound;

import java.math.BigInteger;
import java.util.UUID;

/**
 * A single team/player wireless energy account.
 * <p>
 * Truly unbounded — no capacity limit, no per-node tracking.
 * The wireless pool is a pure "bank account": insert adds, extract removes,
 * and balance can never drop below zero.
 * <p>
 * Physical storage (PSS) periodically rebalances against this pool via
 * {@code WirelessController}, pushing excess in and pulling deficit out.
 */
public class WirelessEnergyNetwork {

    private static final int STATS_WINDOW_TICKS = 20;

    private final UUID networkId;
    private String networkName;

    private BigInteger stored = BigInteger.ZERO;

    // Rolling throughput statistics (per-second window)
    private BigInteger inputThisWindow = BigInteger.ZERO;
    private BigInteger outputThisWindow = BigInteger.ZERO;
    private BigInteger inputPerSecond = BigInteger.ZERO;
    private BigInteger outputPerSecond = BigInteger.ZERO;
    private int windowTickCounter = 0;

    private boolean dirty = false;

    public WirelessEnergyNetwork(UUID networkId, String networkName) {
        this.networkId = networkId;
        this.networkName = networkName;
    }

    // ==================== Energy Operations (long fast-path) ====================

    public long insert(long amount) {
        if (amount <= 0) return 0;
        stored = stored.add(BigInteger.valueOf(amount));
        recordInput(amount);
        markDirty();
        return amount;
    }

    public long extract(long amount) {
        if (amount <= 0) return 0;
        BigInteger biAmount = BigInteger.valueOf(amount);
        if (stored.compareTo(biAmount) < 0) return 0;
        stored = stored.subtract(biAmount);
        recordOutput(amount);
        markDirty();
        return amount;
    }

    public long extractUpTo(long amount) {
        if (amount <= 0) return 0;
        BigInteger biAmount = BigInteger.valueOf(amount);
        long available;
        if (stored.compareTo(biAmount) >= 0) {
            available = amount;
        } else {
            available = stored.longValue();
        }
        if (available <= 0) return 0;
        stored = stored.subtract(BigInteger.valueOf(available));
        recordOutput(available);
        markDirty();
        return available;
    }

    // ==================== Energy Operations (BigInteger path) ====================

    public BigInteger insert(BigInteger amount) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        stored = stored.add(amount);
        recordInput(amount);
        markDirty();
        return amount;
    }

    public BigInteger extract(BigInteger amount) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        if (stored.compareTo(amount) < 0) return BigInteger.ZERO;
        stored = stored.subtract(amount);
        recordOutput(amount);
        markDirty();
        return amount;
    }

    public BigInteger extractUpTo(BigInteger amount) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        BigInteger available = amount.min(stored);
        if (available.signum() <= 0) return BigInteger.ZERO;
        stored = stored.subtract(available);
        recordOutput(available);
        markDirty();
        return available;
    }

    // ==================== Statistics ====================

    public void tickStats() {
        windowTickCounter++;
        if (windowTickCounter >= STATS_WINDOW_TICKS) {
            inputPerSecond = inputThisWindow;
            outputPerSecond = outputThisWindow;
            inputThisWindow = BigInteger.ZERO;
            outputThisWindow = BigInteger.ZERO;
            windowTickCounter = 0;
        }
    }

    private void recordInput(long amount) {
        inputThisWindow = inputThisWindow.add(BigInteger.valueOf(amount));
    }

    private void recordInput(BigInteger amount) {
        inputThisWindow = inputThisWindow.add(amount);
    }

    private void recordOutput(long amount) {
        outputThisWindow = outputThisWindow.add(BigInteger.valueOf(amount));
    }

    private void recordOutput(BigInteger amount) {
        outputThisWindow = outputThisWindow.add(amount);
    }

    // ==================== Persistence ====================

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", networkId.toString());
        tag.setString("name", networkName);
        tag.setByteArray("stored", stored.toByteArray());
        tag.setByteArray("inputPerSec", inputPerSecond.toByteArray());
        tag.setByteArray("outputPerSec", outputPerSecond.toByteArray());
        return tag;
    }

    public static WirelessEnergyNetwork readFromNBT(NBTTagCompound tag) {
        UUID id = UUID.fromString(tag.getString("id"));
        String name = tag.getString("name");
        WirelessEnergyNetwork network = new WirelessEnergyNetwork(id, name);
        if (tag.hasKey("stored")) {
            network.stored = new BigInteger(tag.getByteArray("stored"));
        }
        if (tag.hasKey("inputPerSec")) {
            network.inputPerSecond = new BigInteger(tag.getByteArray("inputPerSec"));
        }
        if (tag.hasKey("outputPerSec")) {
            network.outputPerSecond = new BigInteger(tag.getByteArray("outputPerSec"));
        }
        return network;
    }

    // ==================== Dirty Management ====================

    private void markDirty() {
        dirty = true;
    }

    public boolean checkAndClearDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    // ==================== Getters/Setters ====================

    public UUID getNetworkId() {
        return networkId;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public BigInteger getStored() {
        return stored;
    }

    public BigInteger getInputPerSecond() {
        return inputPerSecond;
    }

    public BigInteger getOutputPerSecond() {
        return outputPerSecond;
    }

    /**
     * Sets stored energy directly. Used for migration and admin operations only.
     */
    public void setStored(BigInteger stored) {
        this.stored = stored;
        markDirty();
    }
}
