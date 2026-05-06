package gregtech.api.wireless;

import net.minecraft.util.math.BlockPos;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Immutable snapshot of a wireless storage node's state.
 * Represents a PSS (or other storage source) registered in the wireless network.
 * The service layer uses these snapshots as the authoritative record of node capacity/stored,
 * eliminating the need to query world tiles on every transfer.
 */
public final class WirelessStorageNodeSnapshot {

    private final WirelessNodeId nodeId;
    private final UUID ownerNetworkId;
    private final int priority;
    private final int tier;
    private final BigInteger capacity;
    private final BigInteger stored;
    private final long maxInputPerTick;
    private final long maxOutputPerTick;
    private final boolean allowExternalAccess;
    private final long lastSeenTick;
    private final NodeStatus status;

    private WirelessStorageNodeSnapshot(Builder builder) {
        this.nodeId = builder.nodeId;
        this.ownerNetworkId = builder.ownerNetworkId;
        this.priority = builder.priority;
        this.tier = builder.tier;
        this.capacity = builder.capacity;
        this.stored = builder.stored;
        this.maxInputPerTick = builder.maxInputPerTick;
        this.maxOutputPerTick = builder.maxOutputPerTick;
        this.allowExternalAccess = builder.allowExternalAccess;
        this.lastSeenTick = builder.lastSeenTick;
        this.status = builder.status;
    }

    // ==================== Accessors ====================

    public WirelessNodeId getNodeId() {
        return nodeId;
    }

    public UUID getOwnerNetworkId() {
        return ownerNetworkId;
    }

    public int getDimension() {
        return nodeId.getDimension();
    }

    public BlockPos getPos() {
        return nodeId.getPos();
    }

    public int getPriority() {
        return priority;
    }

    public int getTier() {
        return tier;
    }

    public BigInteger getCapacity() {
        return capacity;
    }

    public BigInteger getStored() {
        return stored;
    }

    public long getMaxInputPerTick() {
        return maxInputPerTick;
    }

    public long getMaxOutputPerTick() {
        return maxOutputPerTick;
    }

    public boolean isAllowExternalAccess() {
        return allowExternalAccess;
    }

    public long getLastSeenTick() {
        return lastSeenTick;
    }

    public NodeStatus getStatus() {
        return status;
    }

    // ==================== Builder ====================

    public static Builder builder(WirelessNodeId nodeId, UUID ownerNetworkId) {
        return new Builder(nodeId, ownerNetworkId);
    }

    public static final class Builder {

        private final WirelessNodeId nodeId;
        private final UUID ownerNetworkId;
        private int priority;
        private int tier;
        private BigInteger capacity = BigInteger.ZERO;
        private BigInteger stored = BigInteger.ZERO;
        private long maxInputPerTick;
        private long maxOutputPerTick;
        private boolean allowExternalAccess = true;
        private long lastSeenTick;
        private NodeStatus status = NodeStatus.ONLINE;

        private Builder(WirelessNodeId nodeId, UUID ownerNetworkId) {
            this.nodeId = nodeId;
            this.ownerNetworkId = ownerNetworkId;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        public Builder capacity(BigInteger capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder stored(BigInteger stored) {
            this.stored = stored;
            return this;
        }

        public Builder maxInputPerTick(long maxInputPerTick) {
            this.maxInputPerTick = maxInputPerTick;
            return this;
        }

        public Builder maxOutputPerTick(long maxOutputPerTick) {
            this.maxOutputPerTick = maxOutputPerTick;
            return this;
        }

        public Builder allowExternalAccess(boolean allowExternalAccess) {
            this.allowExternalAccess = allowExternalAccess;
            return this;
        }

        public Builder lastSeenTick(long lastSeenTick) {
            this.lastSeenTick = lastSeenTick;
            return this;
        }

        public Builder status(NodeStatus status) {
            this.status = status;
            return this;
        }

        public WirelessStorageNodeSnapshot build() {
            return new WirelessStorageNodeSnapshot(this);
        }
    }

    public enum NodeStatus {
        /** Node is currently loaded and actively participating in the network. */
        ONLINE,
        /** Node is unloaded but its capacity/stored are still accounted for. */
        OFFLINE,
        /** Node location is invalid or could not be verified; capacity removed from network. */
        STALE
    }
}
