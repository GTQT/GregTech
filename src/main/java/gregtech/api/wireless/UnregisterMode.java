package gregtech.api.wireless;

/**
 * Specifies the behavior when unregistering a storage node from the wireless network.
 */
public enum UnregisterMode {

    /**
     * Node is being gracefully removed (e.g. PSS structure invalidated or controller broken).
     * The node's stored energy remains in the PSS tile entity.
     * The node's capacity is subtracted from the network total.
     * If stored > remaining capacity after removal, excess enters overflow quarantine.
     */
    GRACEFUL,

    /**
     * Node is being forcefully removed (e.g. admin cleanup of stale nodes).
     * The node's stored energy that cannot be redistributed is lost.
     */
    FORCE
}
