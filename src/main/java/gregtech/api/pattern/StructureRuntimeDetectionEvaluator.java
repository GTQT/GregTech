package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.StructureCheckState;
import gregtech.api.pattern.element.StructureDefinition;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Adapts runtime detector output into the normal typed result/aggregate graph.
 */
final class StructureRuntimeDetectionEvaluator {

    private StructureRuntimeDetectionEvaluator() {}

    @NotNull
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static StructureCheckResult check(
            @NotNull StructureDefinition<?> definition,
            @NotNull StructureOperationRequest request) {
        MultiblockControllerBase rawController = request.getController();
        if (rawController == null) {
            return detectorFailure(
                    request, null, request.requireControllerPos(),
                    "runtime detector controller", "no controller was supplied");
        }

        MultiPiecePattern pattern = definition.getCompiledPattern();
        StructurePiece piece = pattern.getPrimaryPiece();
        PieceRuntimes transientRuntimes = new PieceRuntimes(pattern);
        PieceRuntime pieceRuntime = transientRuntimes.getPrimary();
        StructureMatchSession session = pattern.createMatchSession();
        StructureRuntimeDetectionContext context = new StructureRuntimeDetectionContext(
                request.requireWorld(), request.requireControllerPos(),
                request.requireOrientation(), rawController, piece, session);
        StructureRuntimeDetector detector = definition.getRuntimeDetector();

        if (!detector.detect(context)) {
            return detectorFailure(
                    request, context.getError(), context.getFailurePos(),
                    context.getExpected() == null ? "runtime detector matched" : context.getExpected(),
                    context.getActual() == null ? "runtime detector rejected the structure" : context.getActual());
        }

        StructureContribution contribution = context.finishContribution();
        pieceRuntime.swapPositions(context.copyFormedPositions());
        pieceRuntime.setValidated(true);
        pieceRuntime.clearDirty();

        StructureResultTable table = StructureResultTable.builder(pattern)
                .add(PieceEvaluationResult.activeMatchedWithRuntime(
                        piece, request.requireControllerPos(), null,
                        context.copyFormedPositions(), context.copyWatchedPositions(),
                        pieceRuntime, contribution))
                .build();
        StructureAggregateFolder.Result aggregate =
                StructureAggregateFolder.fold(pattern, table);
        if (!aggregate.isMatched()) {
            return aggregateFailure(request, table, aggregate);
        }

        StructureCheckState.Result stateResult = StructureCheckState.Result.success(
                aggregate.getMetadata(),
                aggregate.copyOperationState(), request.requireOrientation().isFlipped(),
                transientRuntimes.capturePublication(), table, aggregate);
        return StructureCheckResult.fromDefinition(stateResult)
                .withTraceContext("runtime-detector", detector.getClass().getName());
    }

    @NotNull
    private static StructureCheckResult aggregateFailure(
            @NotNull StructureOperationRequest request,
            @NotNull StructureResultTable table,
            @NotNull StructureAggregateFolder.Result aggregate) {
        StructureFailureTrace.Kind kind = aggregate.getMissingAbilities().isEmpty()
                ? StructureFailureTrace.Kind.COUNT_LIMIT
                : StructureFailureTrace.Kind.MISSING_ABILITY;
        String message = aggregate.getErrorMessage() == null
                ? "Runtime detector contribution validation failed"
                : aggregate.getErrorMessage();
        StructureFailureTrace failure = failureTrace(
                request, kind, request.requireControllerPos(),
                kind == StructureFailureTrace.Kind.MISSING_ABILITY
                        ? "required abilities present"
                        : "typed contributions satisfied",
                message, aggregate.getMissingAbilities(), aggregate.getAbilityCounts());
        StructureCheckState.Result stateResult = aggregate.getMissingAbilities().isEmpty()
                ? StructureCheckState.Result.failure(
                        message, failure, aggregate.getMissingAbilities(),
                        aggregate.getAbilityCounts(), request.requireOrientation().isFlipped(),
                        table, aggregate)
                : StructureCheckState.Result.missingAbilities(
                        aggregate.getMissingAbilities(), aggregate.getAbilityCounts(),
                        failure, request.requireOrientation().isFlipped(), table, aggregate);
        return StructureCheckResult.fromDefinition(stateResult)
                .withTraceContext("runtime-detector", message);
    }

    @NotNull
    private static StructureCheckResult detectorFailure(
            @NotNull StructureOperationRequest request,
            @Nullable PatternError error,
            @Nullable BlockPos failurePos,
            @NotNull String expected,
            @NotNull String actual) {
        BlockPos pos = failurePos == null ? request.requireControllerPos() : failurePos;
        StructureFailureTrace failure = failureTrace(
                request, StructureFailureTrace.Kind.BLOCK_MISMATCH,
                pos, expected, actual, Collections.emptyMap(), Collections.emptyMap());
        if (error != null) {
            failure = new StructureFailureTrace.Builder(
                    request.getController() == null
                            ? "unknown"
                            : request.getController().getMetaName(),
                    request.requireControllerPos())
                    .formed(request.getController() != null
                            && request.getController().isStructureFormed())
                    .orientation(request.requireOrientation())
                    .path("runtime-detector")
                    .operation("CHECK")
                    .result(StructureFailureTrace.Kind.BLOCK_MISMATCH.getTraceName())
                    .kind(StructureFailureTrace.Kind.BLOCK_MISMATCH)
                    .piece("runtime")
                    .cell(pos.toString())
                    .error(error)
                    .progressDepth(0)
                    .build();
        }
        return StructureCheckResult.fromDefinition(StructureCheckState.Result.failure(
                actual, failure, Collections.emptyMap(), Collections.emptyMap(),
                request.requireOrientation().isFlipped()))
                .withTraceContext("runtime-detector", actual);
    }

    @NotNull
    private static StructureFailureTrace failureTrace(
            @NotNull StructureOperationRequest request,
            @NotNull StructureFailureTrace.Kind kind,
            @NotNull BlockPos pos,
            @NotNull String expected,
            @NotNull String actual,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts) {
        MultiblockControllerBase controller = request.getController();
        return new StructureFailureTrace.Builder(
                controller == null ? "unknown" : controller.getMetaName(),
                request.requireControllerPos())
                .formed(controller != null && controller.isStructureFormed())
                .orientation(request.requireOrientation())
                .path("runtime-detector")
                .operation("CHECK")
                .result(kind.getTraceName())
                .kind(kind)
                .piece("runtime")
                .cell(pos.toString())
                .errorPosition(pos)
                .expected(expected)
                .actual(actual)
                .missingAbilities(missingAbilities)
                .abilityCounts(abilityCounts)
                .progressDepth(0)
                .build();
    }
}
