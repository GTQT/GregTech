package gregtech.common.wireless;

import gregtech.api.wireless.WirelessNodeId;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.api.wireless.WirelessStorageNodeSnapshot;
import gregtech.api.wireless.WirelessStorageNodeSnapshot.NodeStatus;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single wireless energy network belonging to a team or player.
 * Maintains authoritative stored/capacity values and registered storage nodes.
 * <p>
 * The service layer is the single source of truth for stored/capacity.
 * PSS tiles are online adapters that sync with the service, not the other way around.
 */
public class WirelessEnergyNetwork {

    private static final int STATS_WINDOW_TICKS = 20;

    private final UUID networkId;
    private String networkName;

    // Authoritative energy state
    private BigInteger stored;
    private BigInteger capacity;

    // Registered storage nodes
    private final Map<WirelessNodeId, WirelessStorageNodeSnapshot> nodes;

    // Cached sorted node list (rebuilt on registration changes or priority changes)
    private List<WirelessStorageNodeSnapshot> sortedNodes;
    private boolean sortedNodesDirty = true;

    // Rolling throughput statistics (per-second window)
    private BigInteger inputThisWindow = BigInteger.ZERO;
    private BigInteger outputThisWindow = BigInteger.ZERO;
    private BigInteger inputPerSecond = BigInteger.ZERO;
    private BigInteger outputPerSecond = BigInteger.ZERO;
    private int windowTickCounter = 0;

    // Dirty flag for batched markDirty
    private boolean dirty = false;

    public WirelessEnergyNetwork(UUID networkId, String networkName) {
        this.networkId = networkId;
        this.networkName = networkName;
        this.stored = BigInteger.ZERO;
        this.capacity = BigInteger.ZERO;
        this.nodes = new HashMap<>();
    }

    // ==================== Energy Operations ====================

    /**
     * Inserts energy into the network (long fast-path).
     *
     * @param amount amount to insert (must be > 0)
     * @param allowOverflow if true, accepts all energy regardless of capacity
     * @return actual amount inserted
     */
    public long insert(long amount, boolean allowOverflow) {
        if (amount <= 0) return 0;

        if (allowOverflow) {
            stored = stored.add(BigInteger.valueOf(amount));
            recordInput(amount);
            markDirtyBatched();
            return amount;
        }

        BigInteger available = capacity.subtract(stored);
        if (available.signum() <= 0) return 0;

        long accepted;
        if (available.compareTo(BigInteger.valueOf(amount)) >= 0) {
            accepted = amount;
        } else {
            accepted = available.longValueExact();
        }

        if (accepted > 0) {
            stored = stored.add(BigInteger.valueOf(accepted));
            recordInput(accepted);
            markDirtyBatched();
        }
        return accepted;
    }

    /**
     * Inserts energy into the network (BigInteger path).
     *
     * @param amount amount to insert (must be positive)
     * @param allowOverflow if true, accepts all energy regardless of capacity
     * @return actual amount inserted
     */
    public BigInteger insert(BigInteger amount, boolean allowOverflow) {
        if (amount.signum() <= 0) return BigInteger.ZERO;

        if (allowOverflow) {
            stored = stored.add(amount);
            recordInput(amount);
            markDirtyBatched();
            return amount;
        }

        BigInteger available = capacity.subtract(stored);
        if (available.signum() <= 0) return BigInteger.ZERO;

        BigInteger accepted = amount.min(available);
        if (accepted.signum() > 0) {
            stored = stored.add(accepted);
            recordInput(accepted);
            markDirtyBatched();
        }
        return accepted;
    }

    /**
     * Extracts energy from the network (long fast-path).
     * Atomic: returns 0 if insufficient balance.
     *
     * @param amount amount to extract (must be > 0)
     * @return actual amount extracted (either full amount or 0)
     */
    public long extract(long amount) {
        if (amount <= 0) return 0;

        if (stored.compareTo(BigInteger.valueOf(amount)) < 0) {
            return 0;
        }

        stored = stored.subtract(BigInteger.valueOf(amount));
        recordOutput(amount);
        markDirtyBatched();
        return amount;
    }

    /**
     * Extracts energy from the network (BigInteger path).
     * Atomic: returns ZERO if insufficient balance.
     *
     * @param amount amount to extract (must be positive)
     * @return actual amount extracted (either full amount or ZERO)
     */
    public BigInteger extract(BigInteger amount) {
        if (amount.signum() <= 0) return BigInteger.ZERO;

        if (stored.compareTo(amount) < 0) {
            return BigInteger.ZERO;
        }

        stored = stored.subtract(amount);
        recordOutput(amount);
        markDirtyBatched();
        return amount;
    }

    // ==================== Node Management ====================

    public void registerNode(WirelessStorageNodeSnapshot node) {
        WirelessStorageNodeSnapshot old = nodes.put(node.getNodeId(), node);
        if (old != null) {
            // Update: adjust capacity/stored deltas
            capacity = capacity.subtract(old.getCapacity()).add(node.getCapacity());
            stored = stored.subtract(old.getStored()).add(node.getStored());
        } else {
            // New registration
            capacity = capacity.add(node.getCapacity());
            stored = stored.add(node.getStored());
        }
        sortedNodesDirty = true;
        markDirtyBatched();
    }

    public void updateNode(WirelessStorageNodeSnapshot node) {
        WirelessStorageNodeSnapshot old = nodes.get(node.getNodeId());
        if (old == null) {
            registerNode(node);
            return;
        }
        // Adjust capacity delta
        BigInteger capacityDelta = node.getCapacity().subtract(old.getCapacity());
        BigInteger storedDelta = node.getStored().subtract(old.getStored());

        capacity = capacity.add(capacityDelta);
        stored = stored.add(storedDelta);

        // Ensure stored doesn't exceed capacity
        if (stored.compareTo(capacity) > 0) {
            stored = capacity;
        }
        // Ensure stored doesn't go negative
        if (stored.signum() < 0) {
            stored = BigInteger.ZERO;
        }

        nodes.put(node.getNodeId(), node);
        if (old.getPriority() != node.getPriority()) {
            sortedNodesDirty = true;
        }
        markDirtyBatched();
    }

    public void unregisterNode(WirelessNodeId nodeId) {
        WirelessStorageNodeSnapshot removed = nodes.remove(nodeId);
        if (removed != null) {
            capacity = capacity.subtract(removed.getCapacity());
            // Stored energy remains in PSS tile; clamp network stored to new capacity
            if (stored.compareTo(capacity) > 0) {
                stored = capacity;
            }
            if (capacity.signum() < 0) {
                capacity = BigInteger.ZERO;
            }
            if (stored.signum() < 0) {
                stored = BigInteger.ZERO;
            }
            sortedNodesDirty = true;
            markDirtyBatched();
        }
    }

    // ==================== Statistics ====================

    /**
     * Called once per tick by the service to advance the statistics window.
     */
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

    // ==================== View ====================

    public WirelessNetworkView createView() {
        int total = nodes.size();
        int online = 0;
        for (WirelessStorageNodeSnapshot node : nodes.values()) {
            if (node.getStatus() == NodeStatus.ONLINE) {
                online++;
            }
        }
        return new WirelessNetworkView(
                networkId, networkName, stored, capacity,
                inputPerSecond, outputPerSecond, total, online);
    }

    // ==================== Sorted Nodes ====================

    /**
     * Returns nodes sorted by priority descending (higher priority first).
     */
    public List<WirelessStorageNodeSnapshot> getSortedNodes() {
        if (sortedNodesDirty) {
            sortedNodes = new ArrayList<>(nodes.values());
            sortedNodes.sort(Comparator.comparingInt(WirelessStorageNodeSnapshot::getPriority).reversed());
            sortedNodesDirty = false;
        }
        return Collections.unmodifiableList(sortedNodes);
    }

    // ==================== Persistence ====================

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", networkId.toString());
        tag.setString("name", networkName);
        tag.setByteArray("stored", stored.toByteArray());
        tag.setByteArray("capacity", capacity.toByteArray());
        tag.setByteArray("inputPerSec", inputPerSecond.toByteArray());
        tag.setByteArray("outputPerSec", outputPerSecond.toByteArray());

        NBTTagList nodeList = new NBTTagList();
        for (WirelessStorageNodeSnapshot node : nodes.values()) {
            nodeList.appendTag(writeNodeToNBT(node));
        }
        tag.setTag("nodes", nodeList);
        return tag;
    }

    public static WirelessEnergyNetwork readFromNBT(NBTTagCompound tag) {
        UUID id = UUID.fromString(tag.getString("id"));
        String name = tag.getString("name");
        WirelessEnergyNetwork network = new WirelessEnergyNetwork(id, name);

        if (tag.hasKey("stored")) {
            network.stored = new BigInteger(tag.getByteArray("stored"));
        }
        if (tag.hasKey("capacity")) {
            network.capacity = new BigInteger(tag.getByteArray("capacity"));
        }
        if (tag.hasKey("inputPerSec")) {
            network.inputPerSecond = new BigInteger(tag.getByteArray("inputPerSec"));
        }
        if (tag.hasKey("outputPerSec")) {
            network.outputPerSecond = new BigInteger(tag.getByteArray("outputPerSec"));
        }

        NBTTagList nodeList = tag.getTagList("nodes", 10);
        for (int i = 0; i < nodeList.tagCount(); i++) {
            WirelessStorageNodeSnapshot node = readNodeFromNBT(nodeList.getCompoundTagAt(i), id);
            if (node != null) {
                network.nodes.put(node.getNodeId(), node);
            }
        }
        network.sortedNodesDirty = true;
        return network;
    }

    private static NBTTagCompound writeNodeToNBT(WirelessStorageNodeSnapshot node) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", node.getDimension());
        tag.setInteger("x", node.getPos().getX());
        tag.setInteger("y", node.getPos().getY());
        tag.setInteger("z", node.getPos().getZ());
        tag.setInteger("priority", node.getPriority());
        tag.setInteger("tier", node.getTier());
        tag.setByteArray("capacity", node.getCapacity().toByteArray());
        tag.setByteArray("stored", node.getStored().toByteArray());
        tag.setLong("maxInput", node.getMaxInputPerTick());
        tag.setLong("maxOutput", node.getMaxOutputPerTick());
        tag.setBoolean("extAccess", node.isAllowExternalAccess());
        tag.setLong("lastSeen", node.getLastSeenTick());
        tag.setString("status", node.getStatus().name());
        return tag;
    }

    private static WirelessStorageNodeSnapshot readNodeFromNBT(NBTTagCompound tag, UUID ownerNetworkId) {
        try {
            WirelessNodeId nodeId = new WirelessNodeId(
                    tag.getInteger("dim"),
                    new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));

            NodeStatus status;
            try {
                status = NodeStatus.valueOf(tag.getString("status"));
            } catch (IllegalArgumentException e) {
                status = NodeStatus.OFFLINE;
            }

            return WirelessStorageNodeSnapshot.builder(nodeId, ownerNetworkId)
                    .priority(tag.getInteger("priority"))
                    .tier(tag.getInteger("tier"))
                    .capacity(new BigInteger(tag.getByteArray("capacity")))
                    .stored(new BigInteger(tag.getByteArray("stored")))
                    .maxInputPerTick(tag.getLong("maxInput"))
                    .maxOutputPerTick(tag.getLong("maxOutput"))
                    .allowExternalAccess(tag.getBoolean("extAccess"))
                    .lastSeenTick(tag.getLong("lastSeen"))
                    .status(status)
                    .build();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================== Dirty Management ====================

    private void markDirtyBatched() {
        dirty = true;
    }

    /**
     * Checks and clears the dirty flag. Called by the service layer to batch persistence.
     *
     * @return true if data was modified since last check
     */
    public boolean checkAndClearDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    // ==================== Getters ====================

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

    public BigInteger getCapacity() {
        return capacity;
    }

    public Map<WirelessNodeId, WirelessStorageNodeSnapshot> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * Sets stored energy directly. Used for migration and admin operations only.
     */
    public void setStored(BigInteger stored) {
        this.stored = stored;
        markDirtyBatched();
    }

    /**
     * Sets capacity directly. Used for migration and admin operations only.
     */
    public void setCapacity(BigInteger capacity) {
        this.capacity = capacity;
        markDirtyBatched();
    }
}
