package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Operation-level diagnostics attached to typed structure results.
 *
 * <p>This is intentionally compact: detailed block mismatch diagnostics live on
 * {@link StructureFailureTrace}, while build/hint/preview/iterate results need
 * a stable way to report which runtime path produced their accounting without
 * asking callers to inspect mutable runtime error state directly.
 */
public final class StructureOperationDiagnostics {

    private static final StructureOperationDiagnostics EMPTY =
            new StructureOperationDiagnostics("unknown", "UNKNOWN", null, 0, false);

    @NotNull
    private final String path;
    @NotNull
    private final String operation;
    @Nullable
    private final String detail;
    private final int pieceCount;
    private final boolean syntheticSinglePiece;
    @Nullable
    private final String adapterTrace;

    private StructureOperationDiagnostics(@NotNull String path,
                                          @NotNull String operation,
                                          @Nullable String detail,
                                          int pieceCount,
                                          boolean syntheticSinglePiece) {
        this(path, operation, detail, pieceCount, syntheticSinglePiece, null);
    }

    private StructureOperationDiagnostics(@NotNull String path,
                                          @NotNull String operation,
                                          @Nullable String detail,
                                          int pieceCount,
                                          boolean syntheticSinglePiece,
                                          @Nullable String adapterTrace) {
        this.path = path;
        this.operation = operation;
        this.detail = detail;
        this.pieceCount = Math.max(0, pieceCount);
        this.syntheticSinglePiece = syntheticSinglePiece;
        this.adapterTrace = adapterTrace;
    }

    @NotNull
    public static StructureOperationDiagnostics empty() {
        return EMPTY;
    }

    @NotNull
    public static StructureOperationDiagnostics of(@NotNull String path,
                                                   @NotNull String operation,
                                                   @Nullable String detail,
                                                   int pieceCount,
                                                   boolean syntheticSinglePiece) {
        return new StructureOperationDiagnostics(
                path, operation, detail, pieceCount, syntheticSinglePiece);
    }

    @NotNull
    public StructureOperationDiagnostics withAdapterTrace(@Nullable String adapterTrace) {
        if (adapterTrace == null || adapterTrace.isEmpty()) {
            return this;
        }
        return new StructureOperationDiagnostics(
                path, operation, detail, pieceCount, syntheticSinglePiece, adapterTrace);
    }

    @NotNull
    public String getPath() {
        return path;
    }

    @NotNull
    public String getOperation() {
        return operation;
    }

    @Nullable
    public String getDetail() {
        return detail;
    }

    public int getPieceCount() {
        return pieceCount;
    }

    public boolean isSyntheticSinglePiece() {
        return syntheticSinglePiece;
    }

    @Nullable
    public String getAdapterTrace() {
        return adapterTrace;
    }

    @NotNull
    public String describe() {
        return "path=" + path +
                ", operation=" + operation +
                ", pieceCount=" + pieceCount +
                ", syntheticSinglePiece=" + syntheticSinglePiece +
                (adapterTrace == null || adapterTrace.isEmpty() ? "" : ", adapterTrace=" + adapterTrace) +
                (detail == null || detail.isEmpty() ? "" : ", detail=" + detail);
    }
}
