package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

final class StructureOperationRuntime {

    @NotNull
    final MultiPiecePattern pattern;
    @NotNull
    final PieceRuntimes runtimes;
    @NotNull
    final String checkTracePath;
    @NotNull
    private final String detail;

    StructureOperationRuntime(@NotNull MultiPiecePattern pattern,
                              @NotNull PieceRuntimes runtimes,
                              @NotNull String checkTracePath,
                              @NotNull String detail) {
        this.pattern = pattern;
        this.runtimes = runtimes;
        this.checkTracePath = checkTracePath;
        this.detail = detail;
    }

    @NotNull
    PieceRuntimes newCandidateRuntimes() {
        return new PieceRuntimes(pattern);
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
                pattern.getPieceCount());
    }
}
