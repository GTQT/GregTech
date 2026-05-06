package gregtech.api.wireless;

/**
 * Context information for a wireless energy transfer operation.
 * Provides metadata about the transfer source and behavior modifiers.
 */
public final class TransferContext {

    /** Standard context for wireless energy hatch operations (per-tick transfers). */
    public static final TransferContext HATCH = new TransferContext(Source.HATCH, false);

    /** Context for ported GT5 machine operations calling the unified service directly. */
    public static final TransferContext MACHINE = new TransferContext(Source.MACHINE, false);

    /** Context for the deprecated WirelessNetworkManager bridge layer. */
    public static final TransferContext LEGACY_BRIDGE = new TransferContext(Source.LEGACY_BRIDGE, true);

    /** Context for admin/command operations that may bypass capacity limits. */
    public static final TransferContext ADMIN = new TransferContext(Source.ADMIN, true);

    private final Source source;
    private final boolean allowOverflow;

    private TransferContext(Source source, boolean allowOverflow) {
        this.source = source;
        this.allowOverflow = allowOverflow;
    }

    /**
     * Creates a custom transfer context.
     *
     * @param source the transfer source category
     * @param allowOverflow whether this transfer can exceed network capacity on insert
     * @return new TransferContext instance
     */
    public static TransferContext of(Source source, boolean allowOverflow) {
        return new TransferContext(source, allowOverflow);
    }

    public Source getSource() {
        return source;
    }

    /**
     * Whether this transfer is allowed to exceed network capacity on insert.
     * If true, the insert operation will accept all energy even if it exceeds capacity.
     * Used for admin operations and legacy bridge to avoid data loss during migration.
     */
    public boolean isAllowOverflow() {
        return allowOverflow;
    }

    public enum Source {
        HATCH,
        MACHINE,
        LEGACY_BRIDGE,
        ADMIN
    }
}
