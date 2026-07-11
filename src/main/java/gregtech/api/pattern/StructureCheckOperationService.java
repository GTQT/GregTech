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
 * Check and incremental-check service for typed structure operations.
 */
final class StructureCheckOperationService {

    @Nullable
    private final StructureDefinition<?> definition;
    @NotNull
    private final StructureOperationContext operationContext;
    @NotNull
    private final StructureSnapshotOperationService snapshotOperations;

    StructureCheckOperationService(@NotNull StructureOperationContext operationContext) {
        this.definition = operationContext.definition();
        this.operationContext = operationContext;
        this.snapshotOperations = new StructureSnapshotOperationService(operationContext);
    }

    @NotNull
    public StructureCheckResult check(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            boolean doRandomCheck,
            @Nullable MultiblockControllerBase controller) {
        return check(StructureOperationRequest.check(
                world, controllerPos, orientation, doRandomCheck, controller));
    }

    @NotNull
    public StructureCheckResult check(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        StructureOperationRuntime runtime = operationRuntime();
        if (definition != null) {
            StructureEligibilityPlan plan = definition.getEligibilityPlan();
            if (definition.hasRuntimeDetector()) {
                StructureCheckResult result = StructureRuntimeDetectionEvaluator.check(
                        definition, request)
                        .withEligibilityPlan(plan);
                return attachGraphPublication(result, request, plan);
            }
            if (!plan.isEligible()) {
                return checkActiveGraph(request)
                        .withEligibilityPlan(plan)
                        .withTraceContext("active-graph-fallback", plan.describeFallback());
            }
            StructureCheckResult result = StructureCheckResult.fromDefinition(checkDefinition(
                    request.requireWorld(), request.requireControllerPos(), request.requireOrientation(),
                    request.getController()))
                    .withEligibilityPlan(plan);
            return attachGraphPublication(result, request, plan);
        }
        return checkActiveGraph(request);
    }

    @NotNull
    public StructureSnapshotResult checkSnapshot(@NotNull StructureOperationRequest request) {
        return snapshotOperations.checkSnapshot(request);
    }

    @NotNull
    public StructureCheckResult checkActiveGraph(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        if (definition != null && definition.hasRuntimeDetector()) {
            StructureEligibilityPlan plan = definition.getEligibilityPlan();
            StructureCheckResult result = StructureRuntimeDetectionEvaluator.check(
                    definition, request)
                    .withEligibilityPlan(plan);
            return attachGraphPublication(result, request, plan);
        }
        StructureOperationRuntime runtime = operationRuntime();
        MultiPiecePattern pattern = runtime.pattern;
        PieceRuntimes candidates = runtime.newCandidateRuntimes();
        MultiPiecePattern.ActiveGraphCheckResult result = pattern.checkActiveGraphWithResult(
                request.requireWorld(), request.requireControllerPos(), request.requireOrientation(),
                candidates, request.getController());
        return StructureCheckResult.fromActiveGraphDefinition(
                result.isMatched(), result.copyOperationState(), result.getMetadata(),
                result.getFailureTrace(), result.getMissingAbilities(), result.getAbilityCounts(),
                result.isFlipped(), result.isMatched() ? candidates.capturePublication() : null,
                result.getResultTable(), result.getContributionAggregate())
                .withTraceContext(runtime.checkTracePath, runtime.describe());
    }

    @NotNull
    public StructureCheckResult checkIncremental(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull Set<String> dirtyRoots,
            @NotNull StructureEligibilityPlan plan) {
        return checkIncremental(request, baseline, dirtyRoots, plan, null);
    }

    @NotNull
    public StructureCheckResult checkIncremental(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull Set<String> dirtyRoots,
            @NotNull StructureEligibilityPlan plan,
            @Nullable StructureDirtyPrecheck.Result detachedPrecheck) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        if (definition == null) {
            return checkActiveGraph(request)
                    .withTraceContext("active-graph-fallback", "fallback=typed-pattern-runtime");
        }
        if (!plan.isEligible()) {
            return checkActiveGraph(request)
                    .withEligibilityPlan(plan)
                    .withTraceContext("active-graph-fallback", plan.describeFallback());
        }
        StructureWorldReadTracker.Scope readScope =
                ConfigHolder.machines.debugStructureCheck
                        ? StructureWorldReadTracker.begin()
                        : null;
        StructureCheckResult result;
        StructureWorldReadTracker.Metrics readMetrics;
        try {
            result = checkIncrementalEligible(
                    request, baseline, dirtyRoots, plan, detachedPrecheck);
        } finally {
            readMetrics = readScope == null
                    ? StructureWorldReadTracker.emptyMetrics()
                    : readScope.finish();
        }
        StructureShadowValidator.maybeValidateIncremental(
                this, request, result, readMetrics);
        return result;
    }

    @NotNull
    private StructureCheckResult checkIncrementalEligible(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull Set<String> dirtyRoots,
            @NotNull StructureEligibilityPlan plan,
            @Nullable StructureDirtyPrecheck.Result detachedPrecheck) {
        boolean snapshotPrecheckAttempted = false;
        boolean snapshotPrecheckFailed = false;
        boolean asynchronousSnapshotPrecheck = false;
        int snapshotPrecheckPositions = 0;
        if (detachedPrecheck != null
                && detachedPrecheck.getGraphGeneration() == baseline.getGeneration()) {
            snapshotPrecheckAttempted = true;
            snapshotPrecheckFailed = !detachedPrecheck.matchedBaseline();
            asynchronousSnapshotPrecheck = true;
            snapshotPrecheckPositions = detachedPrecheck.getComparedPositions();
        } else if (definition.supportsElementCapability(StructureElementCapability.SNAPSHOT_MATCH)) {
            snapshotPrecheckAttempted = true;
            SnapshotPrecheckDiagnostic precheck = precheckDirtyPiecesOnSnapshot(
                    request.requireWorld(), baseline, baseline.getOrientation(), dirtyRoots);
            snapshotPrecheckFailed = !precheck.matched;
            snapshotPrecheckPositions = precheck.comparedPositions;
        }
        return checkIncrementalOrientation(
                request, baseline, plan, baseline.getOrientation(), dirtyRoots,
                snapshotPrecheckAttempted, snapshotPrecheckFailed,
                asynchronousSnapshotPrecheck, snapshotPrecheckPositions)
                .withEligibilityPlan(plan);
    }

    @NotNull
    public StructureCheckState.Result checkDefinition(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        if (definition == null) {
            throw new IllegalStateException("Definition check requested without a structure definition");
        }
        return definition.createState().check(
                world, controllerPos, orientation, controller);
    }

    @NotNull
    private StructureCheckResult checkIncrementalOrientation(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull StructureEligibilityPlan plan,
            @NotNull StructureOrientation orientation,
            @NotNull Set<String> dirtyRoots,
            boolean snapshotPrecheckAttempted,
            boolean snapshotPrecheckFailed,
            boolean asynchronousSnapshotPrecheck,
            int snapshotPrecheckPositions) {
        MultiPiecePattern pattern = operationRuntime().pattern;
        Set<String> staticClosure = plan.getGraph().dependentClosure(dirtyRoots);
        Set<String> externalDependencyRoots = externalDependencyRoots(
                plan, baseline, request.getController());
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
        int cacheProbeAttempts = 0;
        int cacheProbeHits = 0;
        int cacheProbeMisses = 0;

        for (StructurePiece piece : pattern.getPieceList()) {
            PieceEvaluationResult baselineResult = baseline.getResultTable().get(piece);
            if (baselineResult == null) {
                StructureIncrementalCheckResult diagnostic = incrementalDiagnostic(
                        dirtyRoots, staticClosure, prunedPieces, recheckPieces,
                        pattern, cacheProbeAttempts, cacheProbeHits, cacheProbeMisses,
                        snapshotPrecheckAttempted, snapshotPrecheckFailed,
                        asynchronousSnapshotPrecheck, snapshotPrecheckPositions);
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
                        pattern, cacheProbeAttempts, cacheProbeHits, cacheProbeMisses,
                        snapshotPrecheckAttempted, snapshotPrecheckFailed,
                        asynchronousSnapshotPrecheck, snapshotPrecheckPositions);
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
            CacheProbeReuse cacheProbeReuse = probeBaselineCacheReuse(
                    request, baselineResult, piece, runtime, centerPos,
                    dirtyRoots, externalDependencyRoots, plan.getGraph());
            if (cacheProbeReuse != CacheProbeReuse.NOT_ATTEMPTED) {
                cacheProbeAttempts++;
                if (cacheProbeReuse == CacheProbeReuse.HIT) {
                    cacheProbeHits++;
                } else {
                    cacheProbeMisses++;
                }
            }
            if (cacheProbeReuse == CacheProbeReuse.HIT) {
                resultTable.add(baselineResult);
                accumulatePriorFromResult(baselineResult, pieceRepeats, channelValues, pieceCenters);
                lastActivePieceName = piece.getName();
                lastActivePieceCenter = centerPos;
                propagateChangedAspects(plan.getGraph(), baselineResult, baselineResult,
                        recheckPieces, prunedPieces);
                if (ConfigHolder.machines.debugStructureCheck) {
                    GTLog.logger.debug(
                            "[StructureIncremental] cache-probe reused piece={} center={} front={} up={} flipped={}",
                            piece.getName(), centerPos, orientation.getStructureFront(),
                            orientation.getUp(), orientation.isFlipped());
                }
                continue;
            }

            StructureMatchSession pieceSession = pattern.createMatchSession();
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
                }
            } else {
                matched = pieceSession.tryFork(candidate ->
                        runtime.getState().checkPatternAtExact(
                                request.requireWorld(), centerPos, orientation, 0, 0, 0, candidate));
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
                        pattern, cacheProbeAttempts, cacheProbeHits, cacheProbeMisses,
                        snapshotPrecheckAttempted, snapshotPrecheckFailed,
                        asynchronousSnapshotPrecheck, snapshotPrecheckPositions);
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
            contribution.collectChannelValues(channelValues);
            PieceEvaluationResult newResult = PieceEvaluationResult.activeMatchedWithRuntime(
                    piece, centerPos, resultRepetitions,
                    runtime.getPositions(), runtime.getPositions(), runtime,
                    contribution);
            resultTable.add(newResult);
            propagateChangedAspects(plan.getGraph(), baselineResult, newResult,
                    recheckPieces, prunedPieces);
        }

        StructureIncrementalCheckResult diagnostic = incrementalDiagnostic(
                dirtyRoots, staticClosure, prunedPieces, recheckPieces,
                pattern, cacheProbeAttempts, cacheProbeHits, cacheProbeMisses,
                snapshotPrecheckAttempted, snapshotPrecheckFailed,
                asynchronousSnapshotPrecheck, snapshotPrecheckPositions);

        StructureResultTable completedTable = resultTable.build();
        StructureAggregateFolder.Result aggregate =
                StructureAggregateFolder.fold(pattern, completedTable, baseline);
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
                true, aggregate.copyOperationState(),
                aggregate.getMetadata(), null, null, Collections.emptyMap(),
                aggregate.getAbilityCounts(), orientation.isFlipped(), publication,
                completedTable, aggregate)
                .withEligibilityPlan(plan)
                .withIncrementalCheckResult(diagnostic);
        result = attachGraphPublication(result, request, plan, orientation);
        return result;
    }

    @NotNull
    private static StructureIncrementalCheckResult incrementalDiagnostic(
            @NotNull Set<String> dirtyRoots,
            @NotNull Set<String> staticClosure,
            @NotNull Set<String> prunedPieces,
            @NotNull Set<String> recheckPieces,
            @NotNull MultiPiecePattern pattern,
            int cacheProbeAttempts,
            int cacheProbeHits,
            int cacheProbeMisses,
            boolean snapshotPrecheckAttempted,
            boolean snapshotPrecheckFailed,
            boolean asynchronousSnapshotPrecheck,
            int snapshotPrecheckPositions) {
        return new StructureIncrementalCheckResult(
                dirtyRoots, staticClosure, prunedPieces,
                recheckPieces.size(), pattern.getPieceCount() - recheckPieces.size(),
                cacheProbeAttempts, cacheProbeHits, cacheProbeMisses,
                snapshotPrecheckAttempted, snapshotPrecheckFailed,
                asynchronousSnapshotPrecheck, snapshotPrecheckPositions);
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

    @NotNull
    private static CacheProbeReuse probeBaselineCacheReuse(
            @NotNull StructureOperationRequest request,
            @NotNull PieceEvaluationResult baselineResult,
            @NotNull StructurePiece piece,
            @NotNull PieceRuntime runtime,
            @NotNull BlockPos currentCenter,
            @NotNull Set<String> originalDirtyRoots,
            @NotNull Set<String> externalDependencyRoots,
            @NotNull PieceDependencyGraph graph) {
        if (piece instanceof RepeatGroupPiece) {
            return CacheProbeReuse.NOT_ATTEMPTED;
        }
        if (!originalDirtyRoots.contains(piece.getName())) {
            return CacheProbeReuse.NOT_ATTEMPTED;
        }
        if (externalDependencyRoots.contains(piece.getName())) {
            return CacheProbeReuse.NOT_ATTEMPTED;
        }
        if (!graph.getIncomingEdges(piece.getName()).isEmpty()) {
            return CacheProbeReuse.NOT_ATTEMPTED;
        }
        if (!baselineResult.isActive()) {
            return CacheProbeReuse.NOT_ATTEMPTED;
        }
        BlockPos baselineCenter = baselineResult.getResolvedCenter();
        if (!currentCenter.equals(baselineCenter)) {
            return CacheProbeReuse.NOT_ATTEMPTED;
        }
        return runtime.probeCachedBlocks(request.requireWorld(), request.doRandomCheck())
                ? CacheProbeReuse.HIT
                : CacheProbeReuse.MISS;
    }

    private enum CacheProbeReuse {
        NOT_ATTEMPTED,
        HIT,
        MISS
    }

    @NotNull
    private static Set<String> externalDependencyRoots(
            @NotNull StructureEligibilityPlan plan,
            @NotNull CommittedStructureGraph baseline,
            @Nullable MultiblockControllerBase controller) {
        StructureExternalDependencySnapshot current =
                plan.snapshotExternalDependencies(controller);
        StructureExternalDependencySnapshot.ChangeSet changes =
                current.changesFrom(baseline.getExternalDependencySnapshot());
        if (changes.hasFailures()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(plan.getGraph().getPieceNames()));
        }
        return plan.rootsForExternalDependencyChanges(changes.getChangedKeys());
    }

    @NotNull
    private static SnapshotPrecheckDiagnostic precheckDirtyPiecesOnSnapshot(
            @NotNull IBlockAccess snapshot,
            @NotNull CommittedStructureGraph baseline,
            @NotNull StructureOrientation orientation,
            @NotNull Set<String> dirtyRoots) {
        int comparedPositions = 0;
        for (String root : dirtyRoots) {
            PieceEvaluationResult result = baseline.getResultTable().get(root);
            if (result == null || !result.isActive()) {
                continue;
            }
            SnapshotPrecheckDiagnostic pieceDiagnostic = precheckPiecePositions(
                    snapshot, result, baseline.getRuntimePublication(), orientation);
            comparedPositions += pieceDiagnostic.comparedPositions;
            if (!pieceDiagnostic.matched) {
                return new SnapshotPrecheckDiagnostic(false, comparedPositions);
            }
        }
        return new SnapshotPrecheckDiagnostic(true, comparedPositions);
    }

    @NotNull
    private static SnapshotPrecheckDiagnostic precheckPiecePositions(
            @NotNull IBlockAccess snapshot,
            @NotNull PieceEvaluationResult result,
            @NotNull PieceRuntimes.Publication runtimePublication,
            @NotNull StructureOrientation orientation) {
        PieceRuntime.Publication piecePublication = runtimePublication.get(result.getPiece());
        if (piecePublication == null) {
            return new SnapshotPrecheckDiagnostic(false, 0);
        }
        int comparedPositions = 0;
        Map<Long, BlockInfo> cachedBlocks = piecePublication.copyCachedBlocks();
        for (long posLong : result.getWatchedPositions()) {
            BlockInfo expected = cachedBlocks.get(posLong);
            if (expected == null || expected.getBlockState() == null) {
                continue;
            }
            comparedPositions++;
            StructureWorldReadTracker.recordBlockStateRead();
            if (!expected.getBlockState().equals(snapshot.getBlockState(BlockPos.fromLong(posLong)))) {
                return new SnapshotPrecheckDiagnostic(false, comparedPositions);
            }
        }
        return new SnapshotPrecheckDiagnostic(true, comparedPositions);
    }

    private static final class SnapshotPrecheckDiagnostic {

        private final boolean matched;
        private final int comparedPositions;

        private SnapshotPrecheckDiagnostic(boolean matched, int comparedPositions) {
            this.matched = matched;
            this.comparedPositions = comparedPositions;
        }
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
                false, null, null, failure, message,
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
        // V3 §7/§8: every matched check must publish a CommittedStructureGraph
        // through the committer. The single-template fast-path lives inside
        // CommittedStructureGraph.create / MultiPiecePattern, not here — bypassing
        // graph publication breaks the canonical formed snapshot, the read API
        // (FormedStructureView.getAggregate/...) and the incremental baseline.
        MultiPiecePattern pattern = operationRuntime().pattern;
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
        result.getContribution().collectChannelValues(channelValues);
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
        if (error instanceof CountLimitError) {
            return StructureFailureTrace.Kind.COUNT_LIMIT;
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

    public void clearSingleCache() {
        PieceRuntime runtime = operationRuntime().runtimes.getPrimary();
        if (runtime == null) {
            throw new IllegalStateException("Single-piece cache clear requested without a primary runtime");
        }
        runtime.getState().clearCache();
    }

    @NotNull
    private StructureOperationRuntime operationRuntime() {
        return operationContext.runtime();
    }

}
