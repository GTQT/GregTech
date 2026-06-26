package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.CommittedStructureGraph;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureCheckResult;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureOperationState;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-thread boundary for publishing structure check results.
 *
 * <p>Matching and assembly preparation complete before this class mutates
 * controller or runtime state. Future async results can add generation checks
 * before entering this boundary without duplicating commit side effects.
 */
final class MultiblockStructureCommitter {

    private MultiblockStructureCommitter() {}

    static void applyCheckResult(@NotNull MultiblockControllerBase controller,
                                 @NotNull StructureCheckResult result) {
        applyCheckResult(controller, result, StructureCommitToken.captureForCheck(controller));
    }

    static void applyCheckResult(@NotNull MultiblockControllerBase controller,
                                 @NotNull StructureCheckResult result,
                                 @NotNull StructureCommitToken token) {
        StructureRuntime runtime = requireRuntime(controller);
        String staleReason = token.staleReason();
        if (staleReason != null) {
            token.traceStale("commit", staleReason);
            requeueRejectedIncremental(controller, result);
            return;
        }
        if (!result.isMatched()) {
            StructureFailureTrace failure = result.createFailureTrace(controller);
            runtime.recordCheckFailure(failure, result.getMissingAbilities());
            StructureTrace.debug(controller, "check-failed", "path=" + result.getTracePath()
                    + ", missingAbilities=" + StructureTrace.describeMissingAbilities(result.getMissingAbilities()));
            if (controller.isStructureFormed()) {
                controller.invalidateStructure();
            }
            return;
        }

        MultiblockStructureAssembler.PreparedCommit prepared =
                MultiblockStructureAssembler.prepare(
                        controller, result.copyOperationState(), controller.getMultiblockParts(),
                        controller.isStructureFormed());
        if (!prepared.successful) {
            recordRejection(controller, result.getTracePath(), prepared.failureMessage);
            return;
        }

        FormedStructureView formed = FormedStructureView.fromCheckResult(result);
        commit(controller, runtime, formed, prepared, result.getMetadata(),
                result.copyChannelValues(), result.isFlipped(), result.getTracePath(), result);
        if (prepared.initial) {
            registerInitialCommit(controller, result.getSource());
            return;
        }

        StructureTrace.debug(controller, "still-valid", "path=" + result.getTracePath()
                + ", metadata=" + controller.getFormedMetadata());
        if (result.getGraphPublication() != null) {
            MultiblockStructureRegistration.refreshMultiPieceRegistrationFromRuntime(
                    controller, controller.multiPiecePattern, controller.pieceRuntimes);
        }
    }

    private static boolean commit(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureRuntime runtime,
            @NotNull FormedStructureView formed,
            @NotNull MultiblockStructureAssembler.PreparedCommit prepared,
            @Nullable FormedStructureMetadata metadata,
            @NotNull StructureChannelValues channelValues,
            boolean flipped,
            @NotNull String path,
            @Nullable StructureCheckResult result) {
        if (result != null && controller.pieceRuntimes != null) {
            result.validatePieceRuntimePublication(controller.pieceRuntimes);
        }
        CommittedStructureGraph graphPublication = result == null ? null : result.getGraphPublication();
        boolean formationPayloadChanged = prepared.changed
                || hasFormationPayloadChanged(runtime.getCommittedGraph(), graphPublication);
        controller.setFlipped(flipped);

        if (prepared.changed) {
            prepared.removedParts.forEach(part -> part.removeFromMultiBlock(controller));
            if (prepared.initial) {
                prepared.parts.forEach(part -> part.addToMultiBlock(controller));
            } else {
                prepared.addedParts.forEach(part -> part.addToMultiBlock(controller));
            }
        }

        if (result != null && controller.pieceRuntimes != null) {
            result.publishPieceRuntimes(controller.pieceRuntimes);
        }
        runtime.publishLifecycleState(
                prepared.changed ? prepared.parts : controller.getMultiblockParts(),
                prepared.changed ? prepared.abilities : controller.mutableMultiblockAbilities(),
                metadata,
                channelValues,
                graphPublication);
        controller.projectStructureLifecycle(runtime.getLifecycleState());
        if (formationPayloadChanged) {
            runFormStructure(controller, formed);
            StructureTrace.debug(controller, prepared.initial ? "formed" :
                            prepared.changed ? "reassembled" : "formation-payload-refreshed",
                    "path=" + path + ", metadata=" + metadata + ", channels=" + channelValues);
        }
        return formationPayloadChanged;
    }

    private static void runFormStructure(@NotNull MultiblockControllerBase controller,
                                         @NotNull FormedStructureView formed) {
        controller.formStructure(formed);
    }

    private static boolean hasFormationPayloadChanged(
            @Nullable CommittedStructureGraph previous,
            @Nullable CommittedStructureGraph next) {
        if (previous == next) {
            return false;
        }
        if (previous == null || next == null) {
            return previous != next;
        }
        return previous.getResultTableFingerprint() != next.getResultTableFingerprint();
    }

    private static void registerInitialCommit(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureCheckResult.Source source) {
        MultiblockStructureRegistration.registerFormedDefinition(
                controller, controller.multiPiecePattern, controller.pieceRuntimes);
    }

    private static void recordRejection(@NotNull MultiblockControllerBase controller,
                                        @NotNull String path,
                                        @Nullable String detail) {
        String message = detail == null ? "Structure assembly was rejected without a reason" : detail;
        StructureTrace.debug(controller, "commit-rejected", "path=" + path + ", reason=" + message);
        StructureFailureTrace failure = StructureTrace.assemblyFailure(controller, path, message);
        requireRuntime(controller).recordLifecycleFailure(failure);
    }

    private static void requeueRejectedIncremental(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureCheckResult result) {
        if (result.getIncrementalCheckResult() == null
                || result.getIncrementalCheckResult().getDirtyRoots().isEmpty()
                || controller.getWorld() == null
                || controller.getWorld().isRemote) {
            return;
        }
        MultiblockWorldData.get(controller.getWorld()).enqueueDirtyRoots(
                controller,
                result.getIncrementalCheckResult().getDirtyRoots(),
                controller.getWorld().getTotalWorldTime());
    }

    @NotNull
    private static StructureRuntime requireRuntime(@NotNull MultiblockControllerBase controller) {
        StructureRuntime runtime = controller.getStructureRuntime();
        if (runtime == null) {
            throw new IllegalStateException("Structure runtime is not initialized for "
                    + controller.getMetaName());
        }
        return runtime;
    }
}
