package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCheckState;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.BlockInfo;
import gregtech.common.ConfigHolder;
import gregtech.api.util.GTLog;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Thin operation boundary over the current structure implementations.
 *
 * <p>This class intentionally delegates to {@link MultiblockState},
 * {@link StructureCheckState}, {@link MultiPiecePattern}, and
 * {@link MultiPiecePreviewAssembler}. It provides one place for public
 * check/build/preview/iteration entry points before those implementations
 * are converged onto one traversal engine.
 */
public final class StructureOperationEvaluator {

    @Nullable
    private final StructureDefinition<?> definition;
    @Nullable
    private final MultiblockState state;
    @Nullable
    private final MultiPiecePattern multiPiecePattern;
    @Nullable
    private final PieceRuntimes pieceRuntimes;

    public StructureOperationEvaluator(@Nullable StructureDefinition<?> definition,
                                       @Nullable MultiblockState state,
                                       @Nullable MultiPiecePattern multiPiecePattern,
                                       @Nullable PieceRuntimes pieceRuntimes) {
        this.definition = definition;
        this.state = state;
        this.multiPiecePattern = multiPiecePattern;
        this.pieceRuntimes = pieceRuntimes;
    }

    @NotNull
    public StructureCheckResult check(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            boolean doRandomCheck,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        return check(StructureOperationRequest.check(
                world, controllerPos, orientation, doRandomCheck, context, controller));
    }

    @NotNull
    public StructureCheckResult check(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        if (definition != null) {
            StructureEligibilityPlan plan = definition.getEligibilityPlan();
            if (!plan.isEligible()) {
                return checkActiveGraph(request)
                        .withEligibilityPlan(plan)
                        .withTraceContext("active-graph-fallback", plan.describeFallback());
            }
            StructureCheckResult result = StructureCheckResult.fromDefinition(checkDefinition(
                    request.requireWorld(), request.requireControllerPos(), request.requireOrientation(),
                    request.getMatchContext(), request.getController()))
                    .withEligibilityPlan(plan);
            return attachGraphPublication(result, request, plan);
        }
        PatternMatchContext legacyContext = checkSingle(
                request.requireWorld(),
                request.requireControllerPos(),
                request.requireOrientation(),
                request.doRandomCheck());
        return StructureCheckResult.fromLegacy(legacyContext, requireState());
    }

    @NotNull
    public StructureSnapshotResult checkSnapshot(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SNAPSHOT_CHECK);
        if (definition == null
                || !definition.supportsElementCapability(StructureElementCapability.SNAPSHOT_MATCH)) {
            return StructureSnapshotResult.capabilityUnsupported();
        }
        return definition.createState().checkSnapshot(
                request.requireSnapshot(), request.requireControllerPos(),
                request.requireOrientation(), request.getController());
    }

    @NotNull
    public StructureCheckResult checkActiveGraph(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        MultiPiecePattern pattern = requireMultiPiecePattern();
        PieceRuntimes candidates = new PieceRuntimes(pattern);
        MultiPiecePattern.ActiveGraphCheckResult result = pattern.checkActiveGraphWithResult(
                request.requireWorld(), request.requireControllerPos(), request.requireOrientation(),
                candidates, request.getController());
        return StructureCheckResult.fromActiveGraphDefinition(
                result.isMatched(), result.copyContext(), result.copyOperationState(), result.getMetadata(),
                result.getFailureTrace(), result.getMissingAbilities(), result.getAbilityCounts(),
                result.isFlipped(), result.isMatched() ? candidates.capturePublication() : null,
                result.getResultTable(), result.getContributionAggregate());
    }

    @NotNull
    public StructureCheckResult checkIncremental(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull Set<String> dirtyRoots,
            @NotNull StructureEligibilityPlan plan) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        if (definition == null) {
            return checkActiveGraph(request)
                    .withTraceContext("active-graph-fallback", "fallback=legacy-definition");
        }
        if (!plan.isEligible()) {
            return checkActiveGraph(request)
                    .withEligibilityPlan(plan)
                    .withTraceContext("active-graph-fallback", plan.describeFallback());
        }
        boolean snapshotPrecheckAttempted = false;
        boolean snapshotPrecheckFailed = false;
        if (definition.supportsElementCapability(StructureElementCapability.SNAPSHOT_MATCH)) {
            snapshotPrecheckAttempted = true;
            snapshotPrecheckFailed = !precheckDirtyPiecesOnSnapshot(
                    request.requireWorld(), baseline, baseline.getOrientation(), dirtyRoots);
        }
        return checkIncrementalOrientation(
                request, baseline, plan, baseline.getOrientation(), dirtyRoots,
                snapshotPrecheckAttempted, snapshotPrecheckFailed)
                .withEligibilityPlan(plan);
    }

    /**
     * @deprecated The operation checks the complete active graph, not only dirty pieces.
     */
    @Deprecated
    @NotNull
    public StructureCheckResult checkDirtyPieces(@NotNull StructureOperationRequest request) {
        return checkActiveGraph(request);
    }

    @NotNull
    public StructureCheckState.Result checkDefinition(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        if (definition == null) {
            throw new IllegalStateException("Definition check requested without a structure definition");
        }
        return definition.createState().check(
                world, controllerPos, orientation, context, controller);
    }

    @NotNull
    private StructureCheckResult checkIncrementalOrientation(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull StructureEligibilityPlan plan,
            @NotNull StructureOrientation orientation,
            @NotNull Set<String> dirtyRoots,
            boolean snapshotPrecheckAttempted,
            boolean snapshotPrecheckFailed) {
        MultiPiecePattern pattern = requireMultiPiecePattern();
        Set<String> staticClosure = plan.getGraph().dependentClosure(dirtyRoots);
        LinkedHashSet<String> recheckPieces = new LinkedHashSet<>(dirtyRoots);
        LinkedHashSet<String> prunedPieces = new LinkedHashSet<>();

        PieceRuntimes candidateRuntimes = new PieceRuntimes(pattern);
        candidateRuntimes.publish(baseline.getRuntimePublication());
        StructureResultTable.Builder resultTable = StructureResultTable.builder(pattern);
        Map<String, int[]> pieceRepeats = new HashMap<>();
        Map<String, Integer> channelValues = new HashMap<>();
        Map<String, BlockPos> pieceCenters = new HashMap<>();
        String lastActivePieceName = null;
        BlockPos lastActivePieceCenter = null;

        for (StructurePiece piece : pattern.getPieceList()) {
            PieceEvaluationResult baselineResult = baseline.getResultTable().get(piece);
            if (baselineResult == null) {
                StructureIncrementalCheckResult diagnostic = incrementalDiagnostic(
                        dirtyRoots, staticClosure, prunedPieces, recheckPieces,
                        pattern, snapshotPrecheckAttempted, snapshotPrecheckFailed);
                return incrementalFailure(
                        request, orientation, null,
                        "Committed result table is missing piece '" + piece.getName() + "'",
                        Collections.emptyMap(), Collections.emptyMap(), null, null, diagnostic);
            }

            if (!recheckPieces.contains(piece.getName())) {
                resultTable.add(baselineResult);
                accumulatePriorFromResult(baselineResult, pieceRepeats, channelValues, pieceCenters);
                if (baselineResult.isActive()) {
                    lastActivePieceName = piece.getName();
                    lastActivePieceCenter = baselineResult.getResolvedCenter();
                }
                continue;
            }

            PieceRuntime runtime = candidateRuntimes.get(piece);
            if (runtime == null) {
                StructureIncrementalCheckResult diagnostic = incrementalDiagnostic(
                        dirtyRoots, staticClosure, prunedPieces, recheckPieces,
                        pattern, snapshotPrecheckAttempted, snapshotPrecheckFailed);
                return incrementalFailure(
                        request, orientation, null,
                        "Candidate runtimes are missing piece '" + piece.getName() + "'",
                        Collections.emptyMap(), Collections.emptyMap(), null, null, diagnostic);
            }

            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(pieceRepeats), new HashMap<>(channelValues),
                    new HashMap<>(pieceCenters));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(
                            request.getController(), request.requireWorld(),
                            request.requireControllerPos(), prior, null);
            if (!piece.isActive(activation)) {
                runtime.publishInactive();
                PieceEvaluationResult inactive = PieceEvaluationResult.inactive(piece);
                resultTable.add(inactive);
                propagateChangedAspects(plan.getGraph(), baselineResult, inactive,
                        recheckPieces, prunedPieces);
                continue;
            }

            BlockPos centerPos = piece.getCenterPos(
                    request.requireControllerPos(), orientation, prior);
            StructureMatchSession pieceSession = pattern.createMatchSession(request.getMatchContext());
            pieceSession.setControllerContext(request.getController());
            pieceSession.beginPieceContribution(piece);
            lastActivePieceName = piece.getName();
            lastActivePieceCenter = centerPos;
            if (ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug(
                        "[StructureIncremental] checking piece={} center={} front={} up={} flipped={}",
                        piece.getName(), centerPos, orientation.getStructureFront(),
                        orientation.getUp(), orientation.isFlipped());
            }

            boolean matched;
            if (piece instanceof RepeatGroupPiece repeatPiece) {
                matched = repeatPiece.checkSync(
                        request.requireWorld(), request.requireControllerPos(),
                        orientation, prior, runtime, pieceSession);
                if (matched) {
                    runtime.setValidated(true);
                    runtime.clearDirty();
                    int[] formedReps = runtime.getLastFormedReps();
                    if (formedReps != null && formedReps.length > 0) {
                        pieceRepeats.put(piece.getName(), formedReps.clone());
                    }
                    runtime.setLastAggregatedContext(null);
                }
            } else {
                matched = pieceSession.tryFork(candidate ->
                        runtime.getState().checkPatternAtExact(
                                request.requireWorld(), centerPos, orientation, 0, 0, 0, candidate) != null);
                if (matched) {
                    runtime.setValidated(true);
                    runtime.clearDirty();
                    runtime.swapPositions(new LongOpenHashSet(runtime.getState().cache.keySet()));
                    int[] formedReps = runtime.getState().formedRepetitionCount;
                    if (formedReps != null && formedReps.length > 0) {
                        pieceRepeats.put(piece.getName(), formedReps.clone());
                        runtime.cacheFormedReps(formedReps);
                    }
                }
            }

            if (!matched) {
                pieceSession.discardPieceContribution(piece);
                StructureFailureTrace failure = incrementalPieceFailureTrace(
                        request.getController(), request.requireControllerPos(), orientation,
                        piece.getName(), runtime.getState().getError(),
                        runtime.getState().getMissingAbilities(),
                        pieceSession.copyOperationState().getAbilityCounts(),
                        activePieceDepth(pattern, piece));
                StructureIncrementalCheckResult diagnostic = incrementalDiagnostic(
                        dirtyRoots, staticClosure, prunedPieces, recheckPieces,
                        pattern, snapshotPrecheckAttempted, snapshotPrecheckFailed);
                return incrementalFailure(
                        request, orientation, failure,
                        "Incremental piece '" + piece.getName() + "' failed pattern check",
                        runtime.getState().getMissingAbilities(),
                        pieceSession.copyOperationState().getAbilityCounts(),
                        null, null, diagnostic);
            }

            pieceCenters.put(piece.getName(), centerPos);
            int[] resultRepetitions = piece instanceof RepeatGroupPiece
                    ? runtime.getLastFormedReps()
                    : runtime.getState().formedRepetitionCount;
            StructureContribution contribution = pieceSession.finishPieceContribution(piece);
            PatternMatchContext compatibilityContext =
                    contribution.projectCompatibilityContext(pieceSession.getContext());
            extractChannelValues(compatibilityContext, channelValues);
            PieceEvaluationResult newResult = PieceEvaluationResult.activeMatchedWithRuntime(
                    piece, centerPos, resultRepetitions,
                    runtime.getPositions(), runtime.getPositions(), runtime,
                    contribution, compatibilityContext);
            resultTable.add(newResult);
            propagateChangedAspects(plan.getGraph(), baselineResult, newResult,
                    recheckPieces, prunedPieces);
        }

        StructureIncrementalCheckResult diagnostic = incrementalDiagnostic(
                dirtyRoots, staticClosure, prunedPieces, recheckPieces,
                pattern, snapshotPrecheckAttempted, snapshotPrecheckFailed);

        StructureResultTable completedTable = resultTable.build();
        StructureAggregateFolder.Result aggregate =
                StructureAggregateFolder.fold(pattern, completedTable, request.getMatchContext(),
                        request.getMatchContext() == null ? baseline : null);
        if (!aggregate.isMatched()) {
            StructureFailureTrace.Kind kind = aggregate.getMissingAbilities().isEmpty()
                    ? StructureFailureTrace.Kind.COUNT_LIMIT
                    : StructureFailureTrace.Kind.MISSING_ABILITY;
            String message = aggregate.getErrorMessage() == null
                    ? "Incremental structure-wide validation failed"
                    : aggregate.getErrorMessage();
            StructureFailureTrace failure = incrementalAggregateFailureTrace(
                    request.getController(), request.requireControllerPos(), orientation,
                    message, kind, aggregate.getMissingAbilities(),
                    aggregate.getAbilityCounts(), lastActivePieceName, lastActivePieceCenter);
            return incrementalFailure(
                    request, orientation, failure, message,
                    aggregate.getMissingAbilities(), aggregate.getAbilityCounts(),
                    completedTable, aggregate, diagnostic);
        }

        PieceRuntimes.Publication publication = candidateRuntimes.capturePublication();
        StructureCheckResult result = StructureCheckResult.fromIncrementalDefinition(
                true, aggregate.copyCompatibilityContext(), aggregate.copyOperationState(),
                aggregate.getMetadata(), null, null, Collections.emptyMap(),
                aggregate.getAbilityCounts(), orientation.isFlipped(), publication,
                completedTable, aggregate)
                .withEligibilityPlan(plan)
                .withIncrementalCheckResult(diagnostic);
        return attachGraphPublication(result, request, plan, orientation);
    }

    @NotNull
    private static StructureIncrementalCheckResult incrementalDiagnostic(
            @NotNull Set<String> dirtyRoots,
            @NotNull Set<String> staticClosure,
            @NotNull Set<String> prunedPieces,
            @NotNull Set<String> recheckPieces,
            @NotNull MultiPiecePattern pattern,
            boolean snapshotPrecheckAttempted,
            boolean snapshotPrecheckFailed) {
        return new StructureIncrementalCheckResult(
                dirtyRoots, staticClosure, prunedPieces,
                recheckPieces.size(), pattern.getPieceCount() - recheckPieces.size(),
                snapshotPrecheckAttempted, snapshotPrecheckFailed);
    }

    private static void propagateChangedAspects(
            @NotNull PieceDependencyGraph graph,
            @NotNull PieceEvaluationResult oldSource,
            @NotNull PieceEvaluationResult newSource,
            @NotNull Set<String> recheckPieces,
            @NotNull Set<String> prunedPieces) {
        for (PieceDependencyGraph.Edge edge : graph.getOutgoingEdges(newSource.getPiece().getName())) {
            if (recheckPieces.contains(edge.getTargetPiece())) {
                continue;
            }
            if (edgeChanged(oldSource, newSource, edge)) {
                recheckPieces.add(edge.getTargetPiece());
                prunedPieces.remove(edge.getTargetPiece());
            } else {
                prunedPieces.add(edge.getTargetPiece());
            }
        }
    }

    private static boolean edgeChanged(@NotNull PieceEvaluationResult oldSource,
                                       @NotNull PieceEvaluationResult newSource,
                                       @NotNull PieceDependencyGraph.Edge edge) {
        for (PieceDependencyAspect aspect : edge.getAspects()) {
            if (oldSource.getAspectFingerprint(aspect) != newSource.getAspectFingerprint(aspect)) {
                return true;
            }
        }
        return false;
    }

    private static boolean precheckDirtyPiecesOnSnapshot(
            @NotNull IBlockAccess snapshot,
            @NotNull CommittedStructureGraph baseline,
            @NotNull StructureOrientation orientation,
            @NotNull Set<String> dirtyRoots) {
        for (String root : dirtyRoots) {
            PieceEvaluationResult result = baseline.getResultTable().get(root);
            if (result == null || !result.isActive()) {
                continue;
            }
            if (!precheckPiecePositions(snapshot, result, baseline.getRuntimePublication(), orientation)) {
                return false;
            }
        }
        return true;
    }

    private static boolean precheckPiecePositions(
            @NotNull IBlockAccess snapshot,
            @NotNull PieceEvaluationResult result,
            @NotNull PieceRuntimes.Publication runtimePublication,
            @NotNull StructureOrientation orientation) {
        PieceRuntime.Publication piecePublication = runtimePublication.get(result.getPiece());
        if (piecePublication == null) {
            return false;
        }
        Map<Long, BlockInfo> cachedBlocks = piecePublication.copyCachedBlocks();
        for (long posLong : result.getWatchedPositions()) {
            BlockInfo expected = cachedBlocks.get(posLong);
            if (expected == null || expected.getBlockState() == null) {
                continue;
            }
            if (!expected.getBlockState().equals(snapshot.getBlockState(BlockPos.fromLong(posLong)))) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    private StructureCheckResult incrementalFailure(
            @NotNull StructureOperationRequest request,
            @NotNull StructureOrientation orientation,
            @Nullable StructureFailureTrace failure,
            @NotNull String message,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            @Nullable StructureResultTable resultTable,
            @Nullable StructureAggregateFolder.Result aggregate,
            @NotNull StructureIncrementalCheckResult diagnostic) {
        return StructureCheckResult.fromIncrementalDefinition(
                false, null, null, null, failure, message,
                missingAbilities, abilityCounts, orientation.isFlipped(),
                null, resultTable, aggregate)
                .withIncrementalCheckResult(diagnostic);
    }

    @NotNull
    private StructureCheckResult attachGraphPublication(
            @NotNull StructureCheckResult result,
            @NotNull StructureOperationRequest request,
            @NotNull StructureEligibilityPlan plan) {
        return attachGraphPublication(
                result, request, plan,
                request.requireOrientation().withFlipped(result.isFlipped()));
    }

    @NotNull
    private StructureCheckResult attachGraphPublication(
            @NotNull StructureCheckResult result,
            @NotNull StructureOperationRequest request,
            @NotNull StructureEligibilityPlan plan,
            @NotNull StructureOrientation orientation) {
        if (!result.isMatched()
                || result.getResultTable() == null
                || result.getContributionAggregate() == null
                || result.getRuntimePublication() == null) {
            return result;
        }
        MultiPiecePattern pattern = requireMultiPiecePattern();
        CommittedStructureGraph graph = CommittedStructureGraph.create(
                result.getResultTable(),
                result.getContributionAggregate(),
                StructurePositionIndex.fromResultTable(pattern, result.getResultTable()),
                result.getRuntimePublication(),
                orientation,
                plan.snapshotExternalDependencies(request.getController()));
        return result.withGraphPublication(graph);
    }

    private static void accumulatePriorFromResult(
            @NotNull PieceEvaluationResult result,
            @NotNull Map<String, int[]> pieceRepeats,
            @NotNull Map<String, Integer> channelValues,
            @NotNull Map<String, BlockPos> pieceCenters) {
        if (!result.isActive()) {
            return;
        }
        int[] repetitions = result.getRepetitions();
        if (repetitions.length > 0) {
            pieceRepeats.put(result.getPiece().getName(), repetitions);
        }
        BlockPos center = result.getResolvedCenter();
        if (center != null) {
            pieceCenters.put(result.getPiece().getName(), center);
        }
        extractChannelValues(result.copyCompatibilityContext(), channelValues);
    }

    private static void extractChannelValues(@NotNull PatternMatchContext context,
                                             @NotNull Map<String, Integer> channelValues) {
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                channelValues.putIfAbsent(entry.getKey(), (Integer) entry.getValue());
            }
        }
    }

    @NotNull
    private static StructureFailureTrace incrementalPieceFailureTrace(
            @Nullable MultiblockControllerBase controller,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull String pieceName,
            @Nullable PatternError error,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            int progressDepth) {
        StructureFailureTrace.Builder builder = traceBuilder(controller, controllerPos, orientation)
                .path("incremental")
                .operation("CHECK")
                .result(classifyError(error, missingAbilities).getTraceName())
                .kind(classifyError(error, missingAbilities))
                .piece(pieceName)
                .cell(describeCell(error))
                .progressDepth(progressDepth)
                .missingAbilities(missingAbilities)
                .abilityCounts(abilityCounts);
        if (error != null) {
            builder.error(error);
        } else {
            builder.errorPosition(controllerPos)
                    .expected("piece pattern matched")
                    .actual("Incremental piece '" + pieceName + "' failed pattern check");
        }
        return builder.build();
    }

    @NotNull
    private static StructureFailureTrace incrementalAggregateFailureTrace(
            @Nullable MultiblockControllerBase controller,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull String message,
            @NotNull StructureFailureTrace.Kind kind,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            @Nullable String pieceName,
            @Nullable BlockPos errorPos) {
        return traceBuilder(controller, controllerPos, orientation)
                .path("incremental")
                .operation("CHECK")
                .result(kind.getTraceName())
                .kind(kind)
                .piece(pieceName == null ? "deferred" : pieceName)
                .cell("requirements")
                .errorPosition(errorPos)
                .progressDepth(0)
                .expected(kind == StructureFailureTrace.Kind.MISSING_ABILITY
                        ? "required abilities present"
                        : "requirements within declared limits")
                .actual(message)
                .missingAbilities(missingAbilities)
                .abilityCounts(abilityCounts)
                .build();
    }

    @NotNull
    private static StructureFailureTrace.Builder traceBuilder(
            @Nullable MultiblockControllerBase controller,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation) {
        StructureFailureTrace.Builder builder = new StructureFailureTrace.Builder(
                controller == null ? "unknown" : controller.getMetaName(), controllerPos)
                .orientation(orientation);
        if (controller != null) {
            builder.formed(controller.isStructureFormed());
        }
        return builder;
    }

    @NotNull
    private static StructureFailureTrace.Kind classifyError(
            @Nullable PatternError error,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (!missingAbilities.isEmpty()) {
            return StructureFailureTrace.Kind.MISSING_ABILITY;
        }
        if (error instanceof TraceabilityPredicate.SinglePredicateError) {
            TraceabilityPredicate.SinglePredicateError single =
                    (TraceabilityPredicate.SinglePredicateError) error;
            if (single.type == 0 || single.type == 2) {
                return StructureFailureTrace.Kind.COUNT_LIMIT;
            }
        }
        return StructureFailureTrace.Kind.BLOCK_MISMATCH;
    }

    @Nullable
    private static String describeCell(@Nullable PatternError error) {
        if (error == null) {
            return null;
        }
        try {
            BlockPos pos = error.getPos();
            return pos == null ? null : pos.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int activePieceDepth(@NotNull MultiPiecePattern pattern,
                                        @NotNull StructurePiece piece) {
        int index = pattern.getPieceList().indexOf(piece);
        return index < 0 ? 0 : index + 1;
    }

    @Nullable
    public PatternMatchContext checkSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            boolean doRandomCheck) {
        return requireState().checkPatternFastAt(
                world, centerPos, orientation, doRandomCheck);
    }

    public void clearSingleCache() {
        requireState().clearCache();
    }

    @NotNull
    public StructureBuildResult buildSingle(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        if (request.getEvaluationOperation().isCreativeBuild()) {
            return creativeBuildSingle(request);
        }
        return survivalBuildSingle(request);
    }

    @NotNull
    public StructureBuildResult buildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        return request.getEvaluationOperation().isCreativeBuild()
                ? creativeBuildAllPieces(request)
                : survivalBuildAllPieces(request);
    }

    @NotNull
    public StructureBuildResult buildPiece(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        return request.getEvaluationOperation().isCreativeBuild()
                ? creativeBuildPiece(request)
                : survivalBuildPiece(request);
    }

    public void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        creativeBuildSingle(player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches);
    }

    public void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        creativeBuildSingle(StructureOperationRequest.creativeBuild(
                player, controller, orientation, channelValues, skipHatches));
    }

    @NotNull
    public StructureBuildResult creativeBuildSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        StructureBuildResult result = requireState().autoBuildAtWithResult(
                request.requirePlayer(), request.requireController(),
                request.requireControllerPos(), request.requireOrientation(),
                0, 0, 0, request.getChannelValues(), request.skipHatches(), null,
                request.getEvaluationOperation(), ItemStack.EMPTY);
        StructureTrace.debug(request.requireController(), "creative-build-single-result",
                result.describeCounts());
        return result;
    }

    @Deprecated
    public void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            int tier) {
        requireState().autoBuild(player, controller, tier);
    }

    public boolean creativeBuildPiece(
            int pieceIndex,
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull AbilityPlacementTracker abilityTracker) {
        return creativeBuildPiece(
                pieceIndex, player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches, abilityTracker);
    }

    public boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        return creativeBuildAllPieces(player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches);
    }

    public boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        return creativeBuildAllPieces(StructureOperationRequest.creativeBuild(
                player, controller, orientation, channelValues, skipHatches)).isAttempted();
    }

    @NotNull
    public StructureBuildResult creativeBuildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getPieceCount();
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            result.merge(creativeBuildPiece(StructureOperationRequest.creativeBuildPiece(
                    pieceIndex, request.requirePlayer(), request.requireController(),
                    request.requireOrientation(), request.getChannelValues(),
                    request.skipHatches(), abilityTracker)));
        }
        StructureBuildResult buildResult = result.build();
        StructureTrace.debug(request.requireController(), "creative-build-all-pieces-result",
                buildResult.describeCounts());
        return buildResult;
    }

    public boolean creativeBuildPiece(
            int pieceIndex,
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull AbilityPlacementTracker abilityTracker) {
        return creativeBuildPiece(StructureOperationRequest.creativeBuildPiece(
                pieceIndex, player, controller, orientation, channelValues,
                skipHatches, abilityTracker)).isAttempted();
    }

    @NotNull
    public StructureBuildResult creativeBuildPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = request.getAbilityTracker();
        if (abilityTracker == null) {
            abilityTracker = pattern.createAbilityPlacementTracker();
        }
        StructureBuildResult result = pattern.autoBuildPieceWithResult(
                request.getPieceIndex(), request.requirePlayer(), request.requireController(),
                request.requireOrientation(), request.getChannelValues(), request.skipHatches(),
                requirePieceRuntimes(), abilityTracker,
                request.getEvaluationOperation(), ItemStack.EMPTY);
        StructureTrace.debug(request.requireController(), "creative-build-piece-result",
                "pieceIndex=" + request.getPieceIndex() + ", " + result.describeCounts());
        return result;
    }

    public void survivalBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull ItemStack triggerStack) {
        survivalBuildSingle(StructureOperationRequest.survivalBuild(
                player, controller, orientation, channelValues, skipHatches, triggerStack));
    }

    @NotNull
    public StructureBuildResult survivalBuildSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-single",
                "path=single-piece-legacy-autobuild, operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        StructureBuildResult result = requireState().autoBuildAtWithResult(
                request.requirePlayer(), request.requireController(),
                request.requireControllerPos(), request.requireOrientation(),
                0, 0, 0, request.getChannelValues(), request.skipHatches(), null,
                request.getEvaluationOperation(), request.requireTriggerStack());
        StructureTrace.debug(request.requireController(), "survival-build-single-result",
                result.describeCounts());
        return result;
    }

    public boolean survivalBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull ItemStack triggerStack) {
        return survivalBuildAllPieces(StructureOperationRequest.survivalBuild(
                player, controller, orientation, channelValues, skipHatches, triggerStack)).isAttempted();
    }

    @NotNull
    public StructureBuildResult survivalBuildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-all-pieces",
                "path=multi-piece-legacy-autobuild, operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getPieceCount();
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            result.merge(survivalBuildPiece(StructureOperationRequest.survivalBuildPiece(
                    pieceIndex, request.requirePlayer(), request.requireController(),
                    request.requireOrientation(), request.getChannelValues(),
                    request.skipHatches(), abilityTracker, request.requireTriggerStack())));
        }
        StructureBuildResult buildResult = result.build();
        StructureTrace.debug(request.requireController(), "survival-build-all-pieces-result",
                buildResult.describeCounts());
        return buildResult;
    }

    @NotNull
    public StructureBuildResult survivalBuildPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = request.getAbilityTracker();
        if (abilityTracker == null) {
            abilityTracker = pattern.createAbilityPlacementTracker();
        }
        StructureTrace.debug(request.requireController(), "survival-build-piece",
                "path=multi-piece-legacy-autobuild, pieceIndex=" + request.getPieceIndex()
                        + ", operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        StructureBuildResult result = pattern.autoBuildPieceWithResult(
                request.getPieceIndex(), request.requirePlayer(), request.requireController(),
                request.requireOrientation(), request.getChannelValues(), request.skipHatches(),
                requirePieceRuntimes(), abilityTracker,
                request.getEvaluationOperation(), request.requireTriggerStack());
        StructureTrace.debug(request.requireController(), "survival-build-piece-result",
                "pieceIndex=" + request.getPieceIndex() + ", " + result.describeCounts());
        return result;
    }

    public void spawnHintsSingle(@NotNull StructureOperationRequest request) {
        hintSingle(request);
    }

    @NotNull
    public StructureHintResult hintSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureTrace.debug(request.requireController(), "hint-single",
                "path=single-piece-fixed-walker, operation=" + request.getEvaluationOperation());
        StructureHintResult result = StructureHintResult.builder()
                .recordActivePiece()
                .merge(requireState().spawnHintsAtWithResult(
                        request.requireWorld(), request.requireController(),
                        request.requireControllerPos(), request.requireOrientation(),
                        request.getChannelValues(), request.requireTriggerStack()))
                .build();
        StructureTrace.debug(request.requireController(), "hint-single-result",
                result.describeCounts());
        return result;
    }

    public boolean spawnHintsAllPieces(@NotNull StructureOperationRequest request) {
        return hintAllPieces(request).isAttempted();
    }

    @NotNull
    public StructureHintResult hintAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureTrace.debug(request.requireController(), "hint-all-pieces",
                "path=multi-piece-fixed-walker, operation=" + request.getEvaluationOperation());
        StructureHintResult result = requireMultiPiecePattern().spawnHintsAllPiecesWithResult(
                request.requireWorld(), request.requireController(), request.requireOrientation(),
                request.getChannelValues(), requirePieceRuntimes(), request.requireTriggerStack());
        StructureTrace.debug(request.requireController(), "hint-all-pieces-result",
                result.describeCounts());
        return result;
    }

    @NotNull
    public BlockInfo[][][] previewSingle(
            @NotNull int[] repetitions,
            @Nullable Map<String, Integer> channelValues) {
        return previewSingle(StructureOperationRequest.preview(repetitions, channelValues));
    }

    @NotNull
    public BlockInfo[][][] previewSingle(@NotNull StructureOperationRequest request) {
        return previewSingleResult(request).toBlockArray();
    }

    @NotNull
    public StructurePreviewResult previewSingleResult(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        return StructurePreviewResult.single(requireState().createPreviewCells(
                request.requireRepetitions(), request.getChannelValues()));
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(
            @Nullable Map<String, Integer> channelValues,
            @Nullable MultiblockControllerBase controller) {
        return previewMultiPiece(StructureOperationRequest.previewMultiPiece(channelValues, controller));
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(@NotNull StructureOperationRequest request) {
        StructurePreviewResult result = previewMultiPieceResult(request);
        MultiPiecePreviewAssembler.Result multiPieceResult = result.getMultiPieceResult();
        if (multiPieceResult == null) {
            throw new IllegalStateException("Multi-piece preview did not produce a multi-piece result");
        }
        return multiPieceResult;
    }

    @NotNull
    public StructurePreviewResult previewMultiPieceResult(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        return StructurePreviewResult.multi(MultiPiecePreviewAssembler.assemble(
                requireMultiPiecePattern(), requirePieceRuntimes(),
                request.getChannelValues(), request.getController()));
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation) {
        return iterateSingle(StructureOperationRequest.iterate(world, centerPos, orientation));
    }

    @NotNull
    public StructureIterateResult iterateMultiPiece(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        return iterateMultiPiece(StructureOperationRequest.iterate(
                world, controllerPos, orientation, controller));
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(@NotNull StructureOperationRequest request) {
        return iterateSingleResult(request).getBlocks();
    }

    @NotNull
    public StructureIterateResult iterateSingleResult(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.ITERATE);
        return StructureIterateResult.single(requireState().getAllStructureBlocks(
                request.requireWorld(), request.requireControllerPos(), request.requireOrientation()));
    }

    @NotNull
    public StructureIterateResult iterateMultiPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.ITERATE);
        return requireMultiPiecePattern().iteratePositions(requirePieceRuntimes(), request.getController());
    }

    @NotNull
    private MultiblockState requireState() {
        if (state == null) {
            throw new IllegalStateException("Single-piece operation requested without a multiblock state");
        }
        return state;
    }

    @NotNull
    private MultiPiecePattern requireMultiPiecePattern() {
        if (multiPiecePattern == null) {
            throw new IllegalStateException("Multi-piece operation requested without a compiled pattern");
        }
        return multiPiecePattern;
    }

    @NotNull
    private PieceRuntimes requirePieceRuntimes() {
        if (pieceRuntimes == null) {
            throw new IllegalStateException("Multi-piece operation requested without piece runtimes");
        }
        return pieceRuntimes;
    }
}
