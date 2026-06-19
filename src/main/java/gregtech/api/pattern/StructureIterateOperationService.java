package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class StructureIterateOperationService {

    @NotNull
    private final StructureOperationContext context;

    StructureIterateOperationService(@NotNull StructureOperationContext context) {
        this.context = context;
    }

    @NotNull
    Map<BlockPos, BlockInfo> iterateSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation) {
        return iterateSingle(StructureOperationRequest.iterate(world, centerPos, orientation));
    }

    @NotNull
    StructureIterateResult iterateMultiPiece(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        return iterateMultiPiece(StructureOperationRequest.iterate(
                world, controllerPos, orientation, controller));
    }

    @NotNull
    Map<BlockPos, BlockInfo> iterateSingle(@NotNull StructureOperationRequest request) {
        return iterateSingleResult(request).getBlocks();
    }

    @NotNull
    StructureIterateResult iterateSingleResult(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.ITERATE);
        StructureOperationRuntime runtimeView = context.runtime();
        PieceRuntime runtime = runtimeView.runtimes.getPrimary();
        if (runtime == null) {
            return StructureIterateResult.unsupported(StructureIterateResult.Source.SINGLE_PIECE)
                    .withDiagnostics(runtimeView.diagnostics(request.getEvaluationOperation()));
        }
        return StructureIterateResult.single(runtime.getState().getAllStructureBlocks(
                request.requireWorld(), request.requireControllerPos(), request.requireOrientation()))
                .withDiagnostics(runtimeView.diagnostics(request.getEvaluationOperation()));
    }

    @NotNull
    StructureIterateResult iterateMultiPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.ITERATE);
        StructureOperationRuntime runtime = context.runtime();
        return runtime.pattern.iteratePositions(runtime.runtimes, request.getController())
                .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
    }
}
