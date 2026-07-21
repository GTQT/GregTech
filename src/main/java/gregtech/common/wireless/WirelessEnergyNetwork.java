package gregtech.common.wireless;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A team's wireless account. Energy is partitioned into named channels, each
 * with an independent unbounded {@link BigInteger} balance and statistics.
 */
public class WirelessEnergyNetwork {

    public static final int DEFAULT_CHANNEL_ID = 0;
    private static final String NBT_CHANNELS = "channels";

    private final UUID networkId;
    private String networkName;
    private final Map<Integer, WirelessEnergyChannel> channels = new LinkedHashMap<>();
    private int nextChannelId = 1;

    public WirelessEnergyNetwork(UUID networkId, String networkName) {
        this.networkId = networkId;
        this.networkName = networkName;
        channels.put(DEFAULT_CHANNEL_ID, new WirelessEnergyChannel(DEFAULT_CHANNEL_ID, "Main"));
    }

    public WirelessEnergyChannel getDefaultChannel() {
        return channels.get(DEFAULT_CHANNEL_ID);
    }

    public WirelessEnergyChannel getChannel(int channelId) {
        return channels.get(channelId);
    }

    public Collection<WirelessEnergyChannel> getChannels() {
        return channels.values();
    }

    public WirelessEnergyChannel createChannel(String name) {
        int id = nextChannelId++;
        WirelessEnergyChannel channel = new WirelessEnergyChannel(id, normalizeChannelName(name, id));
        channels.put(id, channel);
        return channel;
    }

    public boolean renameChannel(int channelId, String name) {
        WirelessEnergyChannel channel = channels.get(channelId);
        if (channel == null) return false;
        channel.setName(normalizeChannelName(name, channelId));
        return true;
    }

    /**
     * Deletes a channel only when another channel remains. Its balance is
     * divided evenly across the remaining channels; any remainder is assigned
     * in stable channel-id order.
     */
    public boolean deleteChannel(int channelId) {
        if (channels.size() <= 1 || !channels.containsKey(channelId)) return false;
        WirelessEnergyChannel deleted = channels.remove(channelId);
        BigInteger[] division = deleted.getStored().divideAndRemainder(BigInteger.valueOf(channels.size()));
        int remainder = division[1].intValue();
        for (WirelessEnergyChannel channel : channels.values()) {
            BigInteger allocation = division[0];
            if (remainder-- > 0) allocation = allocation.add(BigInteger.ONE);
            channel.insert(allocation);
        }
        return true;
    }

    public void tickStats() {
        channels.values().forEach(WirelessEnergyChannel::tickStats);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", networkId.toString());
        tag.setString("name", networkName);
        tag.setInteger("nextChannelId", nextChannelId);

        NBTTagList channelList = new NBTTagList();
        channels.values().forEach(channel -> channelList.appendTag(channel.writeToNBT()));
        tag.setTag(NBT_CHANNELS, channelList);
        return tag;
    }

    public static WirelessEnergyNetwork readFromNBT(NBTTagCompound tag) {
        UUID id = UUID.fromString(tag.getString("id"));
        WirelessEnergyNetwork network = new WirelessEnergyNetwork(id, tag.getString("name"));
        network.channels.clear();

        if (tag.hasKey(NBT_CHANNELS)) {
            NBTTagList channelList = tag.getTagList(NBT_CHANNELS, 10);
            for (NBTBase entry : channelList) {
                WirelessEnergyChannel channel = WirelessEnergyChannel.readFromNBT((NBTTagCompound) entry);
                network.channels.put(channel.getChannelId(), channel);
                network.nextChannelId = Math.max(network.nextChannelId, channel.getChannelId() + 1);
            }
            network.nextChannelId = Math.max(network.nextChannelId, tag.getInteger("nextChannelId"));
        } else {
            // Version 1 stored its account directly on the team network.
            WirelessEnergyChannel legacy = new WirelessEnergyChannel(DEFAULT_CHANNEL_ID, "Main");
            if (tag.hasKey("stored")) legacy.insert(new BigInteger(tag.getByteArray("stored")));
            network.channels.put(DEFAULT_CHANNEL_ID, legacy);
        }

        if (network.channels.isEmpty()) {
            network.channels.put(DEFAULT_CHANNEL_ID, new WirelessEnergyChannel(DEFAULT_CHANNEL_ID, "Main"));
        }
        return network;
    }

    private static String normalizeChannelName(String name, int id) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return "Channel " + id;
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    public boolean checkAndClearDirty() {
        boolean dirty = false;
        for (WirelessEnergyChannel channel : channels.values()) {
            dirty |= channel.checkAndClearDirty();
        }
        return dirty;
    }

    public UUID getNetworkId() { return networkId; }
    public String getNetworkName() { return networkName; }
    public void setNetworkName(String networkName) { this.networkName = networkName; }
    public BigInteger getStored() { return getDefaultChannel().getStored(); }
    public BigInteger getInputPerSecond() { return getDefaultChannel().getInputPerSecond(); }
    public BigInteger getOutputPerSecond() { return getDefaultChannel().getOutputPerSecond(); }
    public void setStored(BigInteger stored) {
        BigInteger current = getDefaultChannel().getStored();
        if (stored.compareTo(current) >= 0) getDefaultChannel().insert(stored.subtract(current));
        else getDefaultChannel().extractUpTo(current.subtract(stored));
    }
}
