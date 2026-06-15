package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementCapability;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class StructureSnapshotOperationService {

    @NotNull
    private final StructureOperationContext context;

    StructureSnapshotOperationService(@NotNull StructureOperationContext context) {
        this.context = context;
    }

    @NotNull
    StructureSnapshotResult checkSnapshot(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SNAPSHOT_CHECK);
        StructureOperationRuntime runtime = context.runtime();
        StructureDefinition<?> definition = context.definition();
        if (definition != null
                && !definition.supportsElementCapability(StructureElementCapability.SNAPSHOT_MATCH)) {
            return StructureSnapshotResult.capabilityUnsupported()
                    .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
        }
        if (definition == null && !StructureOperationContext.supportsSnapshotMatch(runtime.pattern)) {
            return StructureSnapshotResult.capabilityUnsupported()
                    .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
        }
        return checkSnapshotTyped(
                request.requireSnapshot(), request.requireControllerPos(),
                request.requireOrientation(), request.getController())
                .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
    }

    @NotNull
    private StructureSnapshotResult checkSnapshotTyped(
            @NotNull IBlockAccess snapshot,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        StructureSnapshotResult result = checkSnapshotTypedOrientation(
                snapshot, controllerPos, orientation.withFlipped(false), controller);
        if (!result.isMatched() && orientation.allowsFlip()) {
            StructureSnapshotResult flipped = checkSnapshotTypedOrientation(
                    snapshot, controllerPos, orientation.withFlipped(true), controller);
            return StructureSnapshotResult.selectFailure(result, flipped);
        }
        return result;
    }

    @NotNull
    private StructureSnapshotResult checkSnapshotTypedOrientation(
            @NotNull IBlockAccess snapshot,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        StructureOperationRuntime runtimeView = context.runtime();
        MultiPiecePattern pattern = runtimeView.pattern;
        PieceRuntimes transientRuntimes = runtimeView.newCandidateRuntimes();
        StructureMatchSession session = pattern.createMatchSession();
        session.setControllerContext(controller);
        Map<String, int[]> pieceRepeats = new HashMap<>();
        Map<String, BlockPos> pieceCenters = new HashMap<>();
        int progressDepth = 0;

        for (StructurePiece piece : pattern.getPieceList()) {
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(pieceRepeats), Collections.emptyMap(),
                    new HashMap<>(pieceCenters));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(
                            controller, null, controllerPos, prior, session);
            if (!piece.isActive(activation)) {
                continue;
            }

            PieceRuntime pieceRuntime = transientRuntimes.get(piece);
            if (pieceRuntime == null) {
                return StructureSnapshotResult.mismatch(
                        orientation.isFlipped(), piece.getName(), progressDepth);
            }
            BlockPos pieceCenter = piece.getCenterPos(controllerPos, orientation, prior);
            BlockPos checkOrigin = piece instanceof RepeatGroupPiece
                    ? controllerPos
                    : pieceCenter;
            boolean matched = session.tryFork(pieceSession ->
                    piece.checkOnSnapshot(
                            snapshot, checkOrigin, orientation, prior, pieceRuntime, pieceSession));
            if (!matched) {
                return StructureSnapshotResult.mismatch(
                        orientation.isFlipped(), piece.getName(), progressDepth);
            }

            int[] repetitions = piece instanceof RepeatGroupPiece
                    ? pieceRuntime.getLastFormedReps()
                    : pieceRuntime.getState().formedRepetitionCount;
            if (repetitions != null && repetitions.length > 0) {
                pieceRepeats.put(piece.getName(), repetitions.clone());
            }
            pieceCenters.put(piece.getName(), pieceCenter);
            progressDepth++;
        }

        if (!session.validate(false).success) {
            return StructureSnapshotResult.mismatch(
                    orientation.isFlipped(), "requirements", progressDepth);
        }
        return StructureSnapshotResult.matched(orientation.isFlipped(), progressDepth);
    }
}
