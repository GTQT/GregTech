package gregtech.api.wireless;

import java.math.BigInteger;

/**
 * Result of a wireless energy transfer operation (insert or extract).
 * Immutable value object reporting the outcome.
 */
public final class TransferResult {

    private static final TransferResult ZERO_SUCCESS = new TransferResult(0L, Status.SUCCESS);
    private static final TransferResult FAILED_INSUFFICIENT = new TransferResult(0L, Status.INSUFFICIENT_ENERGY);
    private static final TransferResult FAILED_NO_NETWORK = new TransferResult(0L, Status.NO_NETWORK);
    private static final TransferResult FAILED_FULL = new TransferResult(0L, Status.NETWORK_FULL);

    private final long amountLong;
    private final BigInteger amountBig;
    private final Status status;

    private TransferResult(long amount, Status status) {
        this.amountLong = amount;
        this.amountBig = null;
        this.status = status;
    }

    private TransferResult(BigInteger amount, Status status) {
        this.amountLong = 0;
        this.amountBig = amount;
        this.status = status;
    }

    // ==================== Factory methods ====================

    public static TransferResult success(long amount) {
        if (amount == 0) return ZERO_SUCCESS;
        return new TransferResult(amount, Status.SUCCESS);
    }

    public static TransferResult success(BigInteger amount) {
        if (amount.signum() == 0) return ZERO_SUCCESS;
        return new TransferResult(amount, Status.SUCCESS);
    }

    public static TransferResult partial(long amount) {
        return new TransferResult(amount, Status.PARTIAL);
    }

    public static TransferResult partial(BigInteger amount) {
        return new TransferResult(amount, Status.PARTIAL);
    }

    public static TransferResult insufficientEnergy() {
        return FAILED_INSUFFICIENT;
    }

    public static TransferResult networkFull() {
        return FAILED_FULL;
    }

    public static TransferResult noNetwork() {
        return FAILED_NO_NETWORK;
    }

    // ==================== Accessors ====================

    /**
     * Returns the amount actually transferred.
     * Use this for long-path operations.
     */
    public long getAmountLong() {
        if (amountBig != null) {
            return amountBig.longValueExact();
        }
        return amountLong;
    }

    /**
     * Returns the amount actually transferred as BigInteger.
     * Use this for BigInteger-path operations.
     */
    public BigInteger getAmount() {
        if (amountBig != null) {
            return amountBig;
        }
        return BigInteger.valueOf(amountLong);
    }

    public Status getStatus() {
        return status;
    }

    /**
     * Whether any energy was successfully transferred (amount > 0).
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PARTIAL;
    }

    public enum Status {
        /** Full amount transferred successfully. */
        SUCCESS,
        /** Partial amount transferred (insert hit capacity limit). */
        PARTIAL,
        /** Extract failed: not enough energy in network. */
        INSUFFICIENT_ENERGY,
        /** Insert failed: network is at full capacity (only when overflow not allowed). */
        NETWORK_FULL,
        /** Operation failed: no network found for the given actor. */
        NO_NETWORK
    }
}
