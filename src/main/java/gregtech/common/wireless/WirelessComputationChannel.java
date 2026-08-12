package gregtech.common.wireless;

import gregtech.api.GTValues;
import gregtech.api.capability.IOpticalComputationProvider;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named computation channel inside a team's wireless computation network.
 * Holds the runtime pool of registered uplink nodes and rolling allocation
 * statistics. Capacity is simply the sum of the registered nodes' CWT[tier].
 */
public final class WirelessComputationChannel {

    private static final int STATS_WINDOW_TICKS = 20;

    private final int channelId;
    private String name;
    private final Map<String, ComputationNode> nodes = new LinkedHashMap<>();
    private int capacity;
    private int allocatedThisTick;
    private int allocatedThisWindow;
    private int allocatedPerSecond;
    private int windowTickCounter;
    private boolean dirty;

    public WirelessComputationChannel(int channelId, String name) {
        this.channelId = channelId;
        this.name = name;
    }

    // ==================== Node Registration ====================

    public boolean registerNode(String key, int tier, int dimension, long position,
                                IOpticalComputationProvider provider, long gameTime) {
        ComputationNode previous = nodes.get(key);
        if (previous == null) {
            capacity += capacityOfTier(tier);
        }
        nodes.put(key, new ComputationNode(key, tier, dimension, position, provider, gameTime));
        dirty = true;
        return previous == null;
    }

    public boolean unregisterNode(String key) {
        ComputationNode removed = nodes.remove(key);
        if (removed != null) {
            capacity = Math.max(0, capacity - capacityOfTier(removed.getTier()));
            dirty = true;
        }
        return removed != null;
    }

    public boolean touchNode(String key, long gameTime) {
        ComputationNode node = nodes.get(key);
        if (node == null) return false;
        node.touch(gameTime);
        dirty = true;
        return true;
    }

    /** Removes nodes whose last heartbeat is older than the given game time (stale cleanup). */
    public int removeStaleNodes(long staleBefore) {
        int removed = 0;
        var iterator = nodes.values().iterator();
        while (iterator.hasNext()) {
            ComputationNode node = iterator.next();
            if (node.getLastSeen() < staleBefore) {
                iterator.remove();
                capacity = Math.max(0, capacity - capacityOfTier(node.getTier()));
                removed++;
            }
        }
        if (removed > 0) dirty = true;
        return removed;
    }

    private static int capacityOfTier(int tier) {
        if (tier < 0 || tier >= GTValues.CWT.length) return 0;
        return GTValues.CWT[tier];
    }

    // ==================== Allocation ====================

    /**
     * Aggregates a CWU/t request across the registered nodes in registration
     * order, passing the same {@code seen} collection through each node so the
     * underlying provider chain stays cycle-free and its per-tick accounting
     * prevents overselling (same guarantee as the optical network).
     */
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        if (cwut <= 0 || nodes.isEmpty()) return 0;
        int remaining = cwut;
        for (ComputationNode node : nodes.values()) {
            if (remaining <= 0) break;
            int supplied = node.getProvider().requestCWUt(remaining, simulate, seen);
            if (supplied > 0) remaining -= supplied;
        }
        int allocated = cwut - remaining;
        if (allocated > 0 && !simulate) {
            allocatedThisTick += allocated;
            allocatedThisWindow += allocated;
        }
        return allocated;
    }

    /** Total capacity offered by this channel: sum of registered CWT[tier]. */
    public int getMaxCWUt() {
        return capacity;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public void tickStats() {
        if (++windowTickCounter < STATS_WINDOW_TICKS) return;
        allocatedPerSecond = allocatedThisWindow;
        allocatedThisWindow = 0;
        windowTickCounter = 0;
    }

    public void resetTickAccounting() {
        allocatedThisTick = 0;
    }

    // ==================== Persistence ====================

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", channelId);
        tag.setString("name", name);
        return tag;
    }

    public static WirelessComputationChannel readFromNBT(NBTTagCompound tag) {
        return new WirelessComputationChannel(tag.getInteger("id"), tag.getString("name"));
    }

    // ==================== Accessors ====================

    public int getChannelId() { return channelId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; dirty = true; }
    public int getAllocatedThisTick() { return allocatedThisTick; }
    public int getAllocatedPerSecond() { return allocatedPerSecond; }
    public Collection<ComputationNode> getNodes() { return nodes.values(); }
    public boolean checkAndClearDirty() { boolean wasDirty = dirty; dirty = false; return wasDirty; }
}
