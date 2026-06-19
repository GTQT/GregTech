package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

final class StructureHintOperationService {

    @NotNull
    private final StructureOperationContext context;

    StructureHintOperationService(@NotNull StructureOperationContext context) {
        this.context = context;
    }

    void spawnHintsSingle(@NotNull StructureOperationRequest request) {
        hintSingle(request);
    }

    @NotNull
    StructureHintResult hintSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureTrace.debug(request.requireController(), "hint-single",
                "path=v3-typed-single, operation=" + request.getEvaluationOperation());
        StructureHintResult result = hintAllPieces(request);
        StructureTrace.debug(request.requireController(), "hint-single-result",
                result.describeCounts());
        return result;
    }

    boolean spawnHintsAllPieces(@NotNull StructureOperationRequest request) {
        return hintAllPieces(request).isAttempted();
    }

    @NotNull
    StructureHintResult hintAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureTrace.debug(request.requireController(), "hint-all-pieces",
                "path=v3-typed-pattern, operation=" + request.getEvaluationOperation());
        StructureOperationRuntime runtime = context.runtime();
        StructureHintResult result = runtime.pattern.spawnHintsAllPiecesWithResult(
                request.requireWorld(), request.requireController(), request.requireOrientation(),
                request.getChannelValues(), runtime.runtimes, request.requireTriggerStack())
                .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
        StructureTrace.debug(request.requireController(), "hint-all-pieces-result",
                result.describeCounts());
        return result;
    }
}
