package gregtech.api.wireless;

import gregtech.api.capability.IOpticalComputationProvider;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Unified service interface for the wireless computation network.
 * <p>
 * Uplink cloud computation hatches register their multiblock controller
 * (an {@link IOpticalComputationProvider}, e.g. an HPCA) into a team channel.
 * Downlink hatches request CWU/t per tick, which is aggregated across the
 * registered providers of that channel — no physical optical pipes involved.
 * <p>
 * Providers are runtime registrations only: after a world reload, uplink
 * hatches re-register themselves during their first update ticks.
 *
 * @see WirelessComputationView
 */
public interface IWirelessComputationService {

    /**
     * Gets a read-only snapshot of the given channel for the actor's network.
     *
     * @param actor     the player UUID (resolved to team network)
     * @param channelId the channel id
     * @return channel view, or {@link WirelessComputationView#EMPTY} if no network/channel exists
     */
    WirelessComputationView getView(UUID actor, int channelId);

    /**
     * Registers (or renews) an uplink node into the channel. Duplicate keys
     * overwrite the previous registration, refreshing its last-seen timestamp —
     * uplink hatches call this periodically as a heartbeat. Stale nodes are
     * cleaned up by the service when no heartbeat arrives in time.
     *
     * @param actor     the player UUID (resolved to team network)
     * @param channelId the channel id
     * @param key       unique node key (e.g. "uplink:" + block pos)
     * @param tier      uplink hatch tier, used to derive channel capacity (CWT[tier])
     * @param dimension dimension of the uplink hatch
     * @param position  block position of the uplink hatch
     * @param provider  the computation provider (the uplink hatch itself)
     */
    void registerProvider(UUID actor, int channelId, String key, int tier, int dimension, long position,
                          IOpticalComputationProvider provider);

    /**
     * Removes a previously registered uplink node (hatch dismantled or structure invalidated).
     */
    void unregisterProvider(UUID actor, int channelId, String key);

    // ==================== Channel Management ====================

    /** Gets all channels of the actor's network (in stable channel-id order). */
    List<WirelessComputationView> getChannels(UUID actor);

    /** Creates a named channel. Returns its id, or -1 on failure. */
    int createChannel(UUID actor, String name);

    boolean renameChannel(UUID actor, int channelId, String name);

    /** Deletes a channel (the final channel cannot be deleted). */
    boolean deleteChannel(UUID actor, int channelId);

    /**
     * Requests CWU/t from the actor's wireless computation channel.
     * Aggregates across all registered uplink nodes, capped by the channel
     * capacity (sum of registered {@code CWT[tier]}).
     *
     * @param actor     the player UUID (resolved to team network)
     * @param channelId the channel id
     * @param cwut      maximum amount of CWU/t requested
     * @param simulate  whether this is a trial request
     * @param seen      providers already checked (cycle prevention)
     * @return the amount of CWU/t that could be supplied
     */
    int requestCWUt(UUID actor, int channelId, int cwut, boolean simulate,
                    Collection<IOpticalComputationProvider> seen);
}
