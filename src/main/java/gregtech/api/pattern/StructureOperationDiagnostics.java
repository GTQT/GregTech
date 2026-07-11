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
            new StructureOperationDiagnostics("unknown", "UNKNOWN", null, 0);

    @NotNull
    private final String path;
    @NotNull
    private final String operation;
    @Nullable
    private final String detail;
    private final int pieceCount;

    private StructureOperationDiagnostics(@NotNull String path,
                                          @NotNull String operation,
                                          @Nullable String detail,
                                          int pieceCount) {
        this.path = path;
        this.operation = operation;
        this.detail = detail;
        this.pieceCount = Math.max(0, pieceCount);
    }

    @NotNull
    public static StructureOperationDiagnostics empty() {
        return EMPTY;
    }

    @NotNull
    public static StructureOperationDiagnostics of(@NotNull String path,
                                                   @NotNull String operation,
                                                   @Nullable String detail,
                                                   int pieceCount) {
        return new StructureOperationDiagnostics(path, operation, detail, pieceCount);
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

    @NotNull
    public String describe() {
        return "path=" + path +
                ", operation=" + operation +
                ", pieceCount=" + pieceCount +
                (detail == null || detail.isEmpty() ? "" : ", detail=" + detail);
    }
}
