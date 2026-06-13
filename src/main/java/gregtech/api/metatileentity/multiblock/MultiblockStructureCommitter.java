package gregtech.api.metatileentity.multiblock;

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
        StructureRuntime runtime = requireRuntime(controller);
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

        PatternMatchContext context = result.copyContext();
        if (context == null) {
            recordRejection(controller, result.getTracePath(),
                    "Successful structure check returned no match context");
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

        commit(controller, runtime, context, prepared, result.getMetadata(),
                result.copyChannelValues(), result.isFlipped(), result.getTracePath());
        if (prepared.initial) {
            registerInitialCommit(controller, result.getSource());
            return;
        }

        StructureTrace.debug(controller, "still-valid", "path=" + result.getTracePath()
                + ", metadata=" + controller.getFormedMetadata());
        if (result.getSource() == StructureCheckResult.Source.LEGACY_TEMPLATE) {
            MultiblockStructureRegistration.reregisterLegacyCache(
                    controller, controller.multiblockState);
        }
    }

    static boolean reassemble(@NotNull MultiblockControllerBase controller,
                              @NotNull PatternMatchContext context) {
        StructureOperationState operationState =
                StructureOperationState.fromLegacyContext(context);
        MultiblockStructureAssembler.PreparedCommit prepared =
                MultiblockStructureAssembler.prepare(
                        controller, operationState, controller.getMultiblockParts(), true);
        if (!prepared.successful) {
            recordRejection(controller, "runtime", prepared.failureMessage);
            return false;
        }

        return commit(controller, requireRuntime(controller), context, prepared,
                controller.getFormedMetadata(), StructureChannelValues.fromContext(context),
                context.neededFlip(), "runtime");
    }

    private static boolean commit(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureRuntime runtime,
            @NotNull PatternMatchContext context,
            @NotNull MultiblockStructureAssembler.PreparedCommit prepared,
            @Nullable FormedStructureMetadata metadata,
            @NotNull StructureChannelValues channelValues,
            boolean flipped,
            @NotNull String path) {
        controller.setFlipped(flipped);

        if (prepared.changed) {
            prepared.removedParts.forEach(part -> part.removeFromMultiBlock(controller));
            controller.mutableMultiblockParts().clear();
            controller.mutableMultiblockParts().addAll(prepared.parts);
            controller.mutableMultiblockAbilities().clear();
            controller.mutableMultiblockAbilities().putAll(prepared.abilities);
            if (prepared.initial) {
                prepared.parts.forEach(part -> part.addToMultiBlock(controller));
            } else {
                prepared.addedParts.forEach(part -> part.addToMultiBlock(controller));
            }
        }

        runtime.commitSuccessfulCheck(metadata, channelValues);
        if (prepared.initial) {
            controller.publishStructureFormed();
        }
        if (prepared.changed) {
            controller.formStructure(context);
            StructureTrace.debug(controller, prepared.initial ? "formed" : "reassembled",
                    "path=" + path + ", metadata=" + metadata + ", channels=" + channelValues);
        }
        return prepared.changed;
    }

    private static void registerInitialCommit(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureCheckResult.Source source) {
        if (source == StructureCheckResult.Source.LEGACY_TEMPLATE) {
            AsyncStructureChecker.getInstance().unregister(controller);
            MultiblockStructureRegistration.registerFormedLegacy(
                    controller, controller.multiPiecePattern, controller.pieceRuntimes,
                    controller.multiblockState);
            return;
        }
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
