package gregtech.common.wireless;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A team's wireless computation account: a set of named computation channels,
 * each an independent pool of registered uplink nodes.
 */
public class WirelessComputationNetwork {

    public static final int DEFAULT_CHANNEL_ID = 0;
    private static final String NBT_CHANNELS = "channels";

    private final UUID networkId;
    private String networkName;
    private final Map<Integer, WirelessComputationChannel> channels = new LinkedHashMap<>();
    private int nextChannelId = 1;

    public WirelessComputationNetwork(UUID networkId, String networkName) {
        this.networkId = networkId;
        this.networkName = networkName;
        channels.put(DEFAULT_CHANNEL_ID, new WirelessComputationChannel(DEFAULT_CHANNEL_ID, "Main"));
    }

    public WirelessComputationChannel getChannel(int channelId) {
        return channels.get(channelId);
    }

    public Collection<WirelessComputationChannel> getChannels() {
        return channels.values();
    }

    public WirelessComputationChannel createChannel(String name) {
        int id = nextChannelId++;
        WirelessComputationChannel channel = new WirelessComputationChannel(id, normalizeChannelName(name, id));
        channels.put(id, channel);
        return channel;
    }

    public boolean renameChannel(int channelId, String name) {
        WirelessComputationChannel channel = channels.get(channelId);
        if (channel == null) return false;
        channel.setName(normalizeChannelName(name, channelId));
        return true;
    }

    /** Deletes a channel only when another channel remains. Its nodes are dropped (no balance to redistribute). */
    public boolean deleteChannel(int channelId) {
        if (channels.size() <= 1 || !channels.containsKey(channelId)) return false;
        channels.remove(channelId);
        return true;
    }

    public void tickStats() {
        channels.values().forEach(WirelessComputationChannel::tickStats);
    }

    public void resetTickAccounting() {
        channels.values().forEach(WirelessComputationChannel::resetTickAccounting);
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

    public static WirelessComputationNetwork readFromNBT(NBTTagCompound tag) {
        UUID id = UUID.fromString(tag.getString("id"));
        WirelessComputationNetwork network = new WirelessComputationNetwork(id, tag.getString("name"));
        network.channels.clear();

        NBTTagList channelList = tag.getTagList(NBT_CHANNELS, 10);
        for (NBTBase entry : channelList) {
            WirelessComputationChannel channel = WirelessComputationChannel.readFromNBT((NBTTagCompound) entry);
            network.channels.put(channel.getChannelId(), channel);
            network.nextChannelId = Math.max(network.nextChannelId, channel.getChannelId() + 1);
        }
        network.nextChannelId = Math.max(network.nextChannelId, tag.getInteger("nextChannelId"));

        if (network.channels.isEmpty()) {
            network.channels.put(DEFAULT_CHANNEL_ID, new WirelessComputationChannel(DEFAULT_CHANNEL_ID, "Main"));
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
        for (WirelessComputationChannel channel : channels.values()) {
            dirty |= channel.checkAndClearDirty();
        }
        return dirty;
    }

    public UUID getNetworkId() { return networkId; }
    public String getNetworkName() { return networkName; }
    public void setNetworkName(String networkName) { this.networkName = networkName; }
}
