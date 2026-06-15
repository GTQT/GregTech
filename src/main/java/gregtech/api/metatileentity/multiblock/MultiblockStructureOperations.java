package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureCheckResult;
import gregtech.api.pattern.StructureDirtyPrecheck;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureHintResult;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;
import gregtech.common.ConfigHolder;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Internal facade for structure operations owned by {@link MultiblockControllerBase}.
 *
 * <p>The controller keeps its addon-facing methods, while this helper owns the
 * request/runtime dispatch and the fixed order of check -> commit -> registration
 * side effects.
 */
final class MultiblockStructureOperations {

    private MultiblockStructureOperations() {}

    static void checkStructurePattern(@NotNull MultiblockControllerBase controller) {
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
        StructureCommitToken token = StructureCommitToken.captureForCheck(controller);
        StructureTrace.debug(controller, "check-start", runtime.describeShape());
        StructureCheckResult result = runtime.check(
                StructureOperationRequest.check(
                        controller.getWorld(),
                        controller.getPos(),
                        StructureOrientation.fromController(controller),
                        controller.isDelayCheck() && ConfigHolder.machines.enableStructureCheckSample,
                        null,
                        controller));
        MultiblockStructureCommitter.applyCheckResult(controller, result, token);
    }

    static void checkActiveGraph(@NotNull MultiblockControllerBase controller) {
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
        StructureCommitToken token = StructureCommitToken.captureForCheck(controller);
        StructureTrace.debug(controller, "active-graph-check-start", runtime.describeShape());
        StructureCheckResult result = runtime.checkActiveGraph(
                StructureOperationRequest.check(
                        controller.getWorld(),
                        controller.getPos(),
                        StructureOrientation.fromController(controller),
                        false,
                        null,
                        controller));
        MultiblockStructureCommitter.applyCheckResult(controller, result, token);
        MultiblockStructureRegistration.refreshMultiPieceRegistrationFromRuntime(
                controller, controller.multiPiecePattern, controller.pieceRuntimes);
    }

    static void checkIncrementalGraph(@NotNull MultiblockControllerBase controller) {
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
        StructureCommitToken token = StructureCommitToken.captureForCheck(controller);
        checkIncrementalGraph(controller, runtime, token, null);
    }

    static void checkIncrementalGraph(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureCommitToken token,
            @NotNull StructureDirtyPrecheck.Result detachedPrecheck) {
        String staleReason = token.staleReason();
        if (staleReason != null) {
            token.traceStale("async-dirty-live-confirm", staleReason);
            return;
        }
        checkIncrementalGraph(
                controller, controller.getOrCreateStructureRuntime(), token, detachedPrecheck);
    }

    private static void checkIncrementalGraph(
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureRuntime runtime,
            @NotNull StructureCommitToken token,
            @Nullable StructureDirtyPrecheck.Result detachedPrecheck) {
        StructureTrace.debug(controller, "incremental-check-start", runtime.describeShape());
        StructureCheckResult result = runtime.checkIncremental(
                StructureOperationRequest.check(
                        controller.getWorld(),
                        controller.getPos(),
                        StructureOrientation.fromController(controller),
                        false,
                        null, controller),
                detachedPrecheck);
        MultiblockStructureCommitter.applyCheckResult(controller, result, token);
    }

    @NotNull
    static List<StructureChannel> getSupportedChannels(@NotNull MultiblockControllerBase controller) {
        if (controller.patternTemplate == null) {
            controller.reinitializeStructurePattern();
            if (controller.patternTemplate == null) {
                return MultiblockStructureChannels.collectChannelsFromMultiPiece(controller.multiPiecePattern);
            }
        }
        return MultiblockStructureChannels.collectChannelsFromTemplate(controller.patternTemplate);
    }

    @NotNull
    static int[] getChannelRange(@NotNull MultiblockControllerBase controller,
                                 @NotNull StructureChannel channel) {
        if (controller.patternTemplate == null) {
            controller.reinitializeStructurePattern();
            if (controller.patternTemplate == null) {
                return MultiblockStructureChannels.getChannelRangeFromMultiPiece(
                        controller.multiPiecePattern, channel);
            }
        }
        return MultiblockStructureChannels.getTemplateChannelRange(
                controller.patternTemplate, channel.getName());
    }

    @NotNull
    static List<MultiblockShapeInfo> getMatchingShapes(@NotNull MultiblockControllerBase controller,
                                                       @Nullable Map<String, Integer> channelValues) {
        if (channelValues == null || channelValues.isEmpty()) {
            return getMatchingShapes(controller);
        }
        if (controller.patternTemplate == null) {
            controller.reinitializeStructurePattern();
            if (controller.patternTemplate == null) {
                return buildMultiPieceShapes(controller, channelValues);
            }
        }
        return MultiblockStructurePreviews.getMatchingShapes(
                controller, controller.patternTemplate, controller.multiblockState,
                controller.getStructureRuntime(), channelValues);
    }

    @NotNull
    static List<MultiblockShapeInfo> getMatchingShapes(@NotNull MultiblockControllerBase controller) {
        if (controller.patternTemplate == null) {
            controller.reinitializeStructurePattern();
            if (controller.patternTemplate == null) {
                return buildMultiPieceShapes(controller, null);
            }
        }
        return MultiblockStructurePreviews.getMatchingShapes(
                controller, controller.patternTemplate, controller.multiblockState,
                controller.getStructureRuntime(), null);
    }

    @NotNull
    static List<MultiblockShapeInfo> buildMultiPieceShapes(
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.buildMultiPieceShapes(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), channelValues);
    }

    @NotNull
    static Map<BlockPos, TraceabilityPredicate> buildMultiPiecePredicateMap(
            @NotNull MultiblockControllerBase controller) {
        return MultiblockStructurePreviews.buildMultiPiecePredicateMap(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime());
    }

    @NotNull
    static Map<BlockPos, StructureElementPreviewEntry> buildMultiPiecePreviewEntries(
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.buildMultiPiecePreviewEntries(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), channelValues);
    }

    @Nullable
    static MultiblockShapeInfo getMatchingShapeForPiece(
            @NotNull MultiblockControllerBase controller,
            int pieceIndex,
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.getMatchingShapeForPiece(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), pieceIndex, channelValues);
    }

    @Nullable
    static MultiPiecePreviewAssembler.PieceResult getMatchingPreviewPiece(
            @NotNull MultiblockControllerBase controller,
            int pieceIndex,
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.getMatchingPreviewPiece(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), pieceIndex, channelValues);
    }

    @NotNull
    static StructureHintResult hintStructure(@NotNull MultiblockControllerBase controller,
                                             @NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
        if (runtime.getState() != null) {
            return runtime.hintSingle(request);
        }
        return runtime.hintAllPieces(request);
    }

    static void spawnStructureHints(@NotNull MultiblockControllerBase controller,
                                    @NotNull StructureOperationRequest request) {
        hintStructure(controller, request);
    }

    @NotNull
    static StructureRuntime createDynamicStructureRuntime(@NotNull StructureDefinition<?> definition) {
        return StructureRuntime.fromDefinition(definition);
    }

    @NotNull
    static StructureRuntime createDynamicStructureRuntime(@NotNull String pieceName,
                                                         @NotNull BlockPatternTemplate template) {
        return createDynamicStructureRuntime(StructureDefinition.fromTemplate(pieceName, template));
    }

    @NotNull
    static StructureCheckResult checkDynamicStructure(@NotNull MultiblockControllerBase controller,
                                                     @NotNull StructureOperationRequest request,
                                                     @NotNull String pieceName,
                                                     @NotNull BlockPatternTemplate template) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        StructureRuntime dynamicRuntime = controller.createDynamicStructureRuntime(pieceName, template);
        StructureTrace.debug(controller, "dynamic-check",
                "path=dynamic-runtime, operation=" + request.getEvaluationOperation()
                        + ", piece=" + pieceName + ", channels=" + request.getChannelValues()
                        + ", " + dynamicRuntime.describeShape());
        StructureCheckResult result = dynamicRuntime.check(request).withTraceContext(
                "dynamic-runtime",
                "piece=" + pieceName + ", channels=" + request.getChannelValues());
        if (!result.isMatched()) {
            StructureRuntime canonicalRuntime = controller.getOrCreateStructureRuntime();
            canonicalRuntime.recordCheckFailure(result.createFailureTrace(controller), result.getMissingAbilities());
        }
        return result;
    }

    @NotNull
    static BlockInfo[][][] previewDynamicStructure(@NotNull MultiblockControllerBase controller,
                                                   @NotNull StructureOperationRequest request,
                                                   @NotNull String pieceName,
                                                   @NotNull BlockPatternTemplate template) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        StructureRuntime dynamicRuntime = controller.createDynamicStructureRuntime(pieceName, template);
        StructureTrace.debug(controller, "dynamic-preview",
                "path=dynamic-runtime, operation=" + request.getEvaluationOperation()
                        + ", piece=" + pieceName + ", channels=" + request.getChannelValues()
                        + ", " + dynamicRuntime.describeShape());
        return dynamicRuntime.previewSingle(request);
    }

    static boolean autoBuildDynamicStructure(@NotNull MultiblockControllerBase controller,
                                             @NotNull StructureOperationRequest request,
                                             @NotNull String pieceName,
                                             @NotNull BlockPatternTemplate template) {
        request.requireBuildKind();
        StructureRuntime dynamicRuntime = controller.createDynamicStructureRuntime(pieceName, template);
        StructureTrace.debug(controller, "dynamic-build",
                "path=dynamic-runtime, operation=" + request.getEvaluationOperation()
                        + ", piece=" + pieceName + ", channels=" + request.getChannelValues()
                        + ", " + dynamicRuntime.describeShape());
        StructureBuildResult result = dynamicRuntime.buildSingle(request);
        if (result.hasBlockedCells()) {
            controller.getOrCreateStructureRuntime().recordLifecycleFailure(new StructureFailureTrace.Builder(
                    controller.getMetaName(), controller.getPos())
                    .formed(controller.isStructureFormed())
                    .orientation(request.requireOrientation())
                    .path("dynamic-runtime")
                    .operation(request.getEvaluationOperation().name())
                    .result(StructureFailureTrace.Kind.UNKNOWN.getTraceName())
                    .kind(StructureFailureTrace.Kind.UNKNOWN)
                    .piece(pieceName)
                    .cell("build")
                    .expected("dynamic build completed")
                    .actual(result.describeCounts())
                    .build());
        }
        return true;
    }

    static void spawnDynamicStructureHints(@NotNull MultiblockControllerBase controller,
                                           @NotNull StructureOperationRequest request,
                                           @NotNull String pieceName,
                                           @NotNull BlockPatternTemplate template) {
        hintDynamicStructure(controller, request, pieceName, template);
    }

    @NotNull
    static StructureHintResult hintDynamicStructure(@NotNull MultiblockControllerBase controller,
                                                   @NotNull StructureOperationRequest request,
                                                   @NotNull String pieceName,
                                                   @NotNull BlockPatternTemplate template) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureRuntime dynamicRuntime = controller.createDynamicStructureRuntime(pieceName, template);
        StructureTrace.debug(controller, "dynamic-hint",
                "path=dynamic-runtime, operation=" + request.getEvaluationOperation()
                        + ", piece=" + pieceName + ", channels=" + request.getChannelValues()
                        + ", " + dynamicRuntime.describeShape());
        return dynamicRuntime.hintSingle(request);
    }

    @NotNull
    static List<MultiblockShapeInfo> emptyShapes() {
        return Collections.emptyList();
    }
}
