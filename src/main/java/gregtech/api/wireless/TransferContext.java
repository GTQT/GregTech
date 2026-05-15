package gregtech.api.wireless;

/**
 * Context information for a wireless energy transfer operation.
 * Provides metadata about the transfer source and behavior modifiers.
 */
public final class TransferContext {

    /** Standard context for wireless energy hatch operations (per-tick transfers). */
    public static final TransferContext HATCH = new TransferContext(Source.HATCH);

    /** Context for ported GT5 machine operations calling the unified service directly. */
    public static final TransferContext MACHINE = new TransferContext(Source.MACHINE);

    /** Context for PSS rebalance operations — large batch transfers between PSS and wireless pool. */
    public static final TransferContext PSS_REBALANCE = new TransferContext(Source.PSS_REBALANCE);

    /** Context for legacy bridge operations (e.g. old API compatibility layer). */
    public static final TransferContext LEGACY_BRIDGE = new TransferContext(Source.LEGACY_BRIDGE);

    /** Context for admin/command operations. */
    public static final TransferContext ADMIN = new TransferContext(Source.ADMIN);

    private final Source source;

    private TransferContext(Source source) {
        this.source = source;
    }

    public Source getSource() {
        return source;
    }

    public enum Source {
        HATCH,
        MACHINE,
        PSS_REBALANCE,
        LEGACY_BRIDGE,
        ADMIN
    }
}
