package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.PieceTemplate;
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
import gregtech.api.pattern.StructurePreviewResult;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Internal facade for structure operations owned by {@link MultiblockControllerBase}.
 *
 * <p>The controller keeps its addon-facing methods, while this helper owns the
 * request/runtime dispatch and the fixed order of check -> commit -> registration
 * side effects.
 */
final class MultiblockStructureOperations {

    private static final Set<String> PREVIEW_PATH_DIAGNOSTICS =
            Collections.synchronizedSet(new HashSet<>());

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
                        controller),
                detachedPrecheck);
        MultiblockStructureCommitter.applyCheckResult(controller, result, token);
    }

    @NotNull
    static List<StructureChannel> getSupportedChannels(@NotNull MultiblockControllerBase controller) {
        ensureMultiPiecePatternInitialized(controller);
        // V3 §13: tooling consumes the compiled MultiPiecePattern, not a
        // bypassing single-template accessor. Single-template patterns compile
        // to a MultiPiecePattern with one piece, so this path covers both.
        return MultiblockStructureChannels.collectChannelsFromMultiPiece(controller.multiPiecePattern);
    }

    @NotNull
    static int[] getChannelRange(@NotNull MultiblockControllerBase controller,
                                 @NotNull StructureChannel channel) {
        ensureMultiPiecePatternInitialized(controller);
        // V3 §13: channel range is read from the compiled MultiPiecePattern so
        // single-piece and multi-piece share one tooling path.
        return MultiblockStructureChannels.getChannelRangeFromMultiPiece(
                controller.multiPiecePattern, channel);
    }

    @NotNull
    static List<MultiblockShapeInfo> getMatchingShapes(@NotNull MultiblockControllerBase controller,
                                                       @Nullable Map<String, Integer> channelValues) {
        Map<String, Integer> effectiveChannels = emptyToNull(channelValues);
        ensureMultiPiecePatternInitialized(controller);
        // V3 §13: JEI / projector / build-all consume the compiled MultiPiecePattern
        // and PieceRuntimes. The single-template fast-path is internal to the
        // multi-piece preview assembler, not a separate branch here.
        List<MultiblockShapeInfo> shapes = MultiblockStructurePreviews.buildMultiPieceShapes(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), effectiveChannels);
        logMatchingShapes(controller, false, effectiveChannels, shapes.size());
        return shapes;
    }

    private static void ensureMultiPiecePatternInitialized(@NotNull MultiblockControllerBase controller) {
        if (controller.multiPiecePattern == null) {
            controller.reinitializeStructurePattern();
        }
    }

    @NotNull
    static List<MultiblockShapeInfo> getMatchingShapes(@NotNull MultiblockControllerBase controller) {
        return getMatchingShapes(controller, null);
    }

    @NotNull
    static MultiPiecePreviewAssembler.IncrementalPreview beginIncrementalMultiPiecePreview(
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues) {
        Map<String, Integer> effectiveChannels = emptyToNull(channelValues);
        ensureMultiPiecePatternInitialized(controller);
        return MultiblockStructurePreviews.beginIncrementalMultiPiecePreview(
                controller, controller.multiPiecePattern, controller.pieceRuntimes, effectiveChannels);
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
    static Map<BlockPos, StructureElementPreviewEntry> buildMultiPiecePreviewEntries(
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.buildMultiPiecePreviewEntries(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), channelValues);
    }

    @NotNull
    static Map<BlockPos, StructureElementPreviewEntry> buildStructurePreviewEntries(
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues) {
        Map<String, Integer> effectiveChannels = emptyToNull(channelValues);
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
        // V3 §13: typed preview entries come from the compiled MultiPiecePattern
        // via MultiPiecePreviewAssembler. Single-piece patterns compile to a
        // one-piece MultiPiecePattern, so this path serves both cases and the
        // fast-path lives inside the assembler, not as a branch here.
        StructurePreviewResult result = runtime.previewMultiPieceResult(
                StructureOperationRequest.previewMultiPieceCumulative(effectiveChannels, controller));
        MultiPiecePreviewAssembler.Result preview = result.getMultiPieceResult();
        Map<BlockPos, StructureElementPreviewEntry> entries =
                preview == null ? Collections.emptyMap() : preview.getPreviewEntries();
        logPreviewEntries(controller, false, effectiveChannels, result.getOutcome().name(),
                null, entries.size());
        return entries;
    }

    @Nullable
    private static Map<String, Integer> emptyToNull(@Nullable Map<String, Integer> channelValues) {
        return channelValues == null || channelValues.isEmpty() ? null : channelValues;
    }

    private static void logMatchingShapes(@NotNull MultiblockControllerBase controller,
                                          boolean singleTemplate,
                                          @Nullable Map<String, Integer> channelValues,
                                          int shapeCount) {
        String key = "shapes|" + controller.metaTileEntityId + "|" + singleTemplate + "|"
                + StructureOperationRequest.isNoHatch(channelValues) + "|" + channelKey(channelValues);
        if (PREVIEW_PATH_DIAGNOSTICS.add(key)) {
            GTLog.logger.debug("[MultiblockPreview] getMatchingShapes controller={} singleTemplate={} " +
                            "noHatch={} channels={} shapes={}",
                    controller.metaTileEntityId, singleTemplate,
                    StructureOperationRequest.isNoHatch(channelValues),
                    channelLogValue(channelValues), shapeCount);
        }
    }

    private static void logPreviewEntries(@NotNull MultiblockControllerBase controller,
                                          boolean singleTemplate,
                                          @Nullable Map<String, Integer> channelValues,
                                          @NotNull String outcome,
                                          @Nullable int[] repetitions,
                                          int entryCount) {
        String key = "entries|" + controller.metaTileEntityId + "|" + singleTemplate + "|"
                + channelKey(channelValues);
        if (PREVIEW_PATH_DIAGNOSTICS.add(key)) {
            GTLog.logger.debug("[MultiblockPreview] typed preview entries controller={} singleTemplate={} " +
                            "channels={} outcome={} repetitions={} entries={}",
                    controller.metaTileEntityId, singleTemplate, channelLogValue(channelValues),
                    outcome, repetitions == null ? "n/a" : Arrays.toString(repetitions), entryCount);
        }
    }

    @NotNull
    private static String channelKey(@Nullable Map<String, Integer> channelValues) {
        return channelValues == null ? "{}" : new TreeMap<>(channelValues).toString();
    }

    @NotNull
    private static Map<String, Integer> channelLogValue(@Nullable Map<String, Integer> channelValues) {
        return channelValues == null ? Collections.emptyMap() : new TreeMap<>(channelValues);
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

    @Nullable
    static MultiPiecePreviewAssembler.Result getMatchingMultiPiecePreview(
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.getMatchingMultiPiecePreview(
                controller, controller.multiPiecePattern, controller.pieceRuntimes,
                controller.getStructureRuntime(), channelValues);
    }

    @NotNull
    static StructureHintResult hintStructure(@NotNull MultiblockControllerBase controller,
                                             @NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
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
                                                         @NotNull PieceTemplate template) {
        return createDynamicStructureRuntime(StructureDefinition.fromTemplate(pieceName, template));
    }

    @NotNull
    static StructureCheckResult checkDynamicStructure(@NotNull MultiblockControllerBase controller,
                                                     @NotNull StructureOperationRequest request,
                                                     @NotNull String pieceName,
                                                     @NotNull PieceTemplate template) {
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
                                                   @NotNull PieceTemplate template) {
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
                                             @NotNull PieceTemplate template) {
        request.requireBuildKind();
        StructureRuntime dynamicRuntime = controller.createDynamicStructureRuntime(pieceName, template);
        StructureTrace.debug(controller, "dynamic-build",
                "path=dynamic-runtime, operation=" + request.getEvaluationOperation()
                        + ", piece=" + pieceName + ", channels=" + request.getChannelValues()
                        + ", " + dynamicRuntime.describeShape());
        StructureBuildResult result = dynamicRuntime.buildAllPieces(request);
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
                                           @NotNull PieceTemplate template) {
        hintDynamicStructure(controller, request, pieceName, template);
    }

    @NotNull
    static StructureHintResult hintDynamicStructure(@NotNull MultiblockControllerBase controller,
                                                   @NotNull StructureOperationRequest request,
                                                   @NotNull String pieceName,
                                                   @NotNull PieceTemplate template) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureRuntime dynamicRuntime = controller.createDynamicStructureRuntime(pieceName, template);
        StructureTrace.debug(controller, "dynamic-hint",
                "path=dynamic-runtime, operation=" + request.getEvaluationOperation()
                        + ", piece=" + pieceName + ", channels=" + request.getChannelValues()
                        + ", " + dynamicRuntime.describeShape());
        return dynamicRuntime.hintAllPieces(request);
    }

    @NotNull
    static List<MultiblockShapeInfo> emptyShapes() {
        return Collections.emptyList();
    }
}
