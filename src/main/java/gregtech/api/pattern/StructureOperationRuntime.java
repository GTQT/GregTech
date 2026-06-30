package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

final class StructureOperationRuntime {

    @NotNull
    final MultiPiecePattern pattern;
    @NotNull
    final PieceRuntimes runtimes;
    private final boolean transientRuntimes;
    private final boolean syntheticSinglePiece;
    @NotNull
    final String checkTracePath;
    @NotNull
    private final String detail;

    StructureOperationRuntime(@NotNull MultiPiecePattern pattern,
                              @NotNull PieceRuntimes runtimes,
                              boolean transientRuntimes,
                              boolean syntheticSinglePiece,
                              @NotNull String checkTracePath,
                              @NotNull String detail) {
        this.pattern = pattern;
        this.runtimes = runtimes;
        this.transientRuntimes = transientRuntimes;
        this.syntheticSinglePiece = syntheticSinglePiece;
        this.checkTracePath = checkTracePath;
        this.detail = detail;
    }

    @NotNull
    PieceRuntimes newCandidateRuntimes() {
        if (!transientRuntimes) {
            return new PieceRuntimes(pattern);
        }
        return runtimes;
    }

    @NotNull
    String describe() {
        return detail;
    }

    @NotNull
    StructureOperationDiagnostics diagnostics(
            @NotNull StructureEvaluationContext.Operation operation) {
        return StructureOperationDiagnostics.of(
                checkTracePath, operation.name(), detail,
                pattern.getPieceCount(), syntheticSinglePiece);
    }
}
