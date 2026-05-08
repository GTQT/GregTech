package gregtech.api.wireless;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Unified service interface for all wireless energy network operations.
 * All wireless energy transfers (hatches, GT5-ported machines, commands, HUD) MUST go through this service.
 * <p>
 * Design principles:
 * <ul>
 *   <li>All extract operations are atomic: balance cannot go negative.</li>
 *   <li>All insert operations check capacity unless context allows overflow.</li>
 *   <li>long fast-path for per-tick hatch operations; BigInteger path for large machines and commands.</li>
 *   <li>Service only modifies data on the server main thread.</li>
 * </ul>
 *
 * @see TransferResult
 * @see TransferContext
 * @see WirelessNetworkView
 */
public interface WirelessEnergyService {

    // ==================== Query ====================

    /**
     * Gets a read-only view of the wireless network for the given actor.
     * The actor UUID is resolved to a canonical network UUID via team resolution.
     * Reading a view does NOT modify any service-side statistics.
     *
     * @param actor the player UUID (will be resolved to team network)
     * @return network view, or {@link WirelessNetworkView#EMPTY} if no network exists
     */
    WirelessNetworkView getView(UUID actor);

    // ==================== Transfer (long fast-path) ====================

    /**
     * Inserts energy into the actor's wireless network.
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  amount of EU to insert (must be > 0)
     * @param context transfer context specifying source and behavior
     * @return result indicating success/partial/failure and actual amount transferred
     */
    TransferResult insert(UUID actor, long amount, TransferContext context);

    /**
     * Extracts energy from the actor's wireless network.
     * Atomic: if the network has less than the requested amount, NO energy is extracted.
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  amount of EU to extract (must be > 0)
     * @param context transfer context specifying source and behavior
     * @return result indicating success/failure and actual amount transferred
     */
    TransferResult extract(UUID actor, long amount, TransferContext context);

    /**
     * Extracts up to the requested amount from the actor's wireless network.
     * Non-atomic: extracts whatever is available (min of amount and stored).
     * Use this for hatch refilling where partial fulfillment is acceptable.
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  maximum amount of EU to extract (must be > 0)
     * @param context transfer context specifying source and behavior
     * @return result indicating success/partial/failure and actual amount transferred
     */
    TransferResult extractUpTo(UUID actor, long amount, TransferContext context);

    // ==================== Transfer (BigInteger path) ====================

    /**
     * Inserts energy into the actor's wireless network (BigInteger variant).
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  amount of EU to insert (must be positive)
     * @param context transfer context specifying source and behavior
     * @return result indicating success/partial/failure and actual amount transferred
     */
    TransferResult insert(UUID actor, BigInteger amount, TransferContext context);

    /**
     * Extracts energy from the actor's wireless network (BigInteger variant).
     * Atomic: if the network has less than the requested amount, NO energy is extracted.
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  amount of EU to extract (must be positive)
     * @param context transfer context specifying source and behavior
     * @return result indicating success/failure and actual amount transferred
     */
    TransferResult extract(UUID actor, BigInteger amount, TransferContext context);

    /**
     * Extracts up to the requested amount from the actor's wireless network (BigInteger variant).
     * Non-atomic: extracts whatever is available (min of amount and stored).
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  maximum amount of EU to extract (must be positive)
     * @param context transfer context specifying source and behavior
     * @return result indicating success/partial/failure and actual amount transferred
     */
    TransferResult extractUpTo(UUID actor, BigInteger amount, TransferContext context);

    // ==================== Node Management ====================

    /**
     * Registers a new storage node (e.g. PSS with wireless controller) in the network.
     * The node's capacity and stored energy are added to the network totals.
     *
     * @param node snapshot of the storage node to register
     */
    void registerStorageNode(WirelessStorageNodeSnapshot node);

    /**
     * Updates an existing storage node's snapshot (e.g. PSS capacity changed, stored synced).
     * The network totals are adjusted according to the delta between old and new snapshot.
     *
     * @param node updated snapshot of the storage node
     */
    void updateStorageNode(WirelessStorageNodeSnapshot node);

    /**
     * Unregisters a storage node from the network.
     *
     * @param nodeId the unique identifier of the node to remove
     * @param mode   the unregister behavior (graceful vs force)
     */
    void unregisterStorageNode(WirelessNodeId nodeId, UnregisterMode mode);
}
