package gregtech.common.wireless;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named, unbounded energy account inside a team's wireless network.
 */
public final class WirelessEnergyChannel {

    private static final int STATS_WINDOW_TICKS = 20;

    private final int channelId;
    private String name;
    private BigInteger stored = BigInteger.ZERO;
    private BigInteger inputThisWindow = BigInteger.ZERO;
    private BigInteger outputThisWindow = BigInteger.ZERO;
    private BigInteger inputPerSecond = BigInteger.ZERO;
    private BigInteger outputPerSecond = BigInteger.ZERO;
    private int windowTickCounter;
    private boolean wirelessChargingEnabled;
    private int wirelessChargingSlots;
    private final Map<String, WirelessEndpointRecord> endpoints = new LinkedHashMap<>();
    private boolean dirty;

    public WirelessEnergyChannel(int channelId, String name) {
        this.channelId = channelId;
        this.name = name;
    }

    public long insert(long amount) {
        if (amount <= 0) return 0;
        insert(BigInteger.valueOf(amount));
        return amount;
    }

    public BigInteger insert(BigInteger amount) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        stored = stored.add(amount);
        inputThisWindow = inputThisWindow.add(amount);
        dirty = true;
        return amount;
    }

    public long extract(long amount) {
        if (amount <= 0 || stored.compareTo(BigInteger.valueOf(amount)) < 0) return 0;
        extract(BigInteger.valueOf(amount));
        return amount;
    }

    public BigInteger extract(BigInteger amount) {
        if (amount.signum() <= 0 || stored.compareTo(amount) < 0) return BigInteger.ZERO;
        stored = stored.subtract(amount);
        outputThisWindow = outputThisWindow.add(amount);
        dirty = true;
        return amount;
    }

    public long extractUpTo(long amount) {
        return extractUpTo(BigInteger.valueOf(Math.max(amount, 0))).longValue();
    }

    public BigInteger extractUpTo(BigInteger amount) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        BigInteger extracted = stored.min(amount);
        if (extracted.signum() <= 0) return BigInteger.ZERO;
        stored = stored.subtract(extracted);
        outputThisWindow = outputThisWindow.add(extracted);
        dirty = true;
        return extracted;
    }

    public void tickStats() {
        if (++windowTickCounter < STATS_WINDOW_TICKS) return;
        inputPerSecond = inputThisWindow;
        outputPerSecond = outputThisWindow;
        inputThisWindow = BigInteger.ZERO;
        outputThisWindow = BigInteger.ZERO;
        windowTickCounter = 0;
    }

    public void touchEndpoint(String key, String type, int dimension, long position, boolean chunkLoaded,
                              boolean forceLoaded, long gameTime) {
        endpoints.computeIfAbsent(key, ignored -> new WirelessEndpointRecord(key, type, dimension, position))
                .touch(chunkLoaded, forceLoaded, gameTime);
        dirty = true;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", channelId);
        tag.setString("name", name);
        tag.setByteArray("stored", stored.toByteArray());
        tag.setByteArray("inputPerSec", inputPerSecond.toByteArray());
        tag.setByteArray("outputPerSec", outputPerSecond.toByteArray());
        tag.setBoolean("wirelessCharging", wirelessChargingEnabled);
        tag.setInteger("wirelessSlots", wirelessChargingSlots);
        NBTTagList endpointList = new NBTTagList();
        endpoints.values().forEach(endpoint -> endpointList.appendTag(endpoint.writeToNBT()));
        tag.setTag("endpoints", endpointList);
        return tag;
    }

    public static WirelessEnergyChannel readFromNBT(NBTTagCompound tag) {
        WirelessEnergyChannel channel = new WirelessEnergyChannel(tag.getInteger("id"), tag.getString("name"));
        if (tag.hasKey("stored")) channel.stored = new BigInteger(tag.getByteArray("stored"));
        if (tag.hasKey("inputPerSec")) channel.inputPerSecond = new BigInteger(tag.getByteArray("inputPerSec"));
        if (tag.hasKey("outputPerSec")) channel.outputPerSecond = new BigInteger(tag.getByteArray("outputPerSec"));
        channel.wirelessChargingEnabled = tag.getBoolean("wirelessCharging");
        channel.wirelessChargingSlots = tag.getInteger("wirelessSlots");
        NBTTagList endpointList = tag.getTagList("endpoints", 10);
        for (int i = 0; i < endpointList.tagCount(); i++) {
            WirelessEndpointRecord endpoint = WirelessEndpointRecord.readFromNBT(endpointList.getCompoundTagAt(i));
            channel.endpoints.put(endpoint.writeToNBT().getString("key"), endpoint);
        }
        return channel;
    }

    public int getChannelId() { return channelId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; dirty = true; }
    public BigInteger getStored() { return stored; }
    public BigInteger getInputPerSecond() { return inputPerSecond; }
    public BigInteger getOutputPerSecond() { return outputPerSecond; }
    public boolean isWirelessChargingEnabled() { return wirelessChargingEnabled; }
    public void setWirelessChargingEnabled(boolean enabled) { wirelessChargingEnabled = enabled; dirty = true; }
    public int getWirelessChargingSlots() { return wirelessChargingSlots; }
    public void setWirelessChargingSlots(int slots) { wirelessChargingSlots = slots; dirty = true; }
    public boolean checkAndClearDirty() { boolean wasDirty = dirty; dirty = false; return wasDirty; }
}
