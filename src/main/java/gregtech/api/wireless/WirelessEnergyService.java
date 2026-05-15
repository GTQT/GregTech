package gregtech.api.wireless;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Unified service interface for all wireless energy network operations.
 * <p>
 * The wireless pool is a truly unbounded "bank account" per team.
 * Physical PSS units periodically rebalance against the pool — pushing
 * excess energy in and pulling deficit out — but the pool itself has
 * no capacity limit.
 * <p>
 * All transfer amounts use {@code long} for the per-operation fast path.
 * {@code BigInteger} variants are available for large batch operations
 * (admin commands, migration, PSS rebalance of very large buffers).
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
     *
     * @param actor the player UUID (resolved to team network)
     * @return network view, or {@link WirelessNetworkView#EMPTY} if no network exists
     */
    WirelessNetworkView getView(UUID actor);

    // ==================== Transfer (long fast-path) ====================

    /**
     * Inserts energy into the actor's wireless network. Truly unbounded — always succeeds.
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  amount of EU to insert (must be > 0)
     * @param context transfer context specifying source
     * @return result indicating success and actual amount transferred
     */
    TransferResult insert(UUID actor, long amount, TransferContext context);

    /**
     * Extracts energy from the actor's wireless network.
     * Atomic: if the network has less than the requested amount, NO energy is extracted.
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  amount of EU to extract (must be > 0)
     * @param context transfer context specifying source
     * @return result indicating success/failure and actual amount transferred
     */
    TransferResult extract(UUID actor, long amount, TransferContext context);

    /**
     * Extracts up to the requested amount from the actor's wireless network.
     * Non-atomic: extracts whatever is available (min of amount and stored).
     *
     * @param actor   the player UUID (resolved to team network)
     * @param amount  maximum amount of EU to extract (must be > 0)
     * @param context transfer context specifying source
     * @return result indicating success/partial/failure and actual amount transferred
     */
    TransferResult extractUpTo(UUID actor, long amount, TransferContext context);

    // ==================== Transfer (BigInteger path) ====================

    TransferResult insert(UUID actor, BigInteger amount, TransferContext context);

    TransferResult extract(UUID actor, BigInteger amount, TransferContext context);

    TransferResult extractUpTo(UUID actor, BigInteger amount, TransferContext context);
}
