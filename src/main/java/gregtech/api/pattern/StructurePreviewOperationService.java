package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class StructurePreviewOperationService {

    @NotNull
    private final StructureOperationContext context;

    StructurePreviewOperationService(@NotNull StructureOperationContext context) {
        this.context = context;
    }

    @NotNull
    BlockInfo[][][] previewSingle(@NotNull StructureOperationRequest request) {
        return previewSingleResult(request).toBlockArray();
    }

    @NotNull
    StructurePreviewResult previewSingleResult(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        StructureOperationRuntime runtimeView = context.runtime();
        PieceRuntime runtime = runtimeView.runtimes.getPrimary();
        if (runtime == null) {
            return StructurePreviewResult.unsupported(StructurePreviewResult.Source.SINGLE_PIECE)
                    .withDiagnostics(runtimeView.diagnostics(request.getEvaluationOperation()));
        }
        AbilityPlacementTracker abilityTracker = request.getAbilityTracker();
        if (abilityTracker == null) {
            abilityTracker = runtimeView.pattern.createAbilityPlacementTracker();
        }
        return StructurePreviewResult.single(runtime.getState().createPreviewCells(
                request.requireRepetitions(), request.getChannelValues(), abilityTracker))
                .withDiagnostics(runtimeView.diagnostics(request.getEvaluationOperation()));
    }

    @NotNull
    MultiPiecePreviewAssembler.Result previewMultiPiece(
            @Nullable Map<String, Integer> channelValues,
            @Nullable MultiblockControllerBase controller) {
        return previewMultiPiece(StructureOperationRequest.previewMultiPiece(channelValues, controller));
    }

    @NotNull
    MultiPiecePreviewAssembler.Result previewMultiPiece(@NotNull StructureOperationRequest request) {
        StructurePreviewResult result = previewMultiPieceResult(request);
        MultiPiecePreviewAssembler.Result multiPieceResult = result.getMultiPieceResult();
        if (multiPieceResult == null) {
            throw new IllegalStateException("Multi-piece preview did not produce a multi-piece result");
        }
        return multiPieceResult;
    }

    @NotNull
    StructurePreviewResult previewMultiPieceResult(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        StructureOperationRuntime runtime = context.runtime();
        return StructurePreviewResult.multi(MultiPiecePreviewAssembler.assemble(
                runtime.pattern, runtime.runtimes,
                request.getChannelValues(), request.getController(),
                request.getPieceIndex()))
                .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
    }
}
