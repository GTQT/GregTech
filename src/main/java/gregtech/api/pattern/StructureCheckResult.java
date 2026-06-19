package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCheckState;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable operation-level result for a synchronous structure check.
 *
 * <p>This normalizes typed V3 checks before controller assembly begins.
 * Mutable context/channel data is copied at the boundary so a later traversal
 * cannot change a result that is waiting to be committed.
 */
public final class StructureCheckResult {

    public enum Source {
        DEFINITION("definition"),
        /**
         * @deprecated Runtime checks now adapt legacy templates into typed
         *             compiled patterns. Retained for external compatibility
         *             with callers that still construct legacy results directly.
         */
        @Deprecated
        LEGACY_TEMPLATE("legacy-template");

        @NotNull
        private final String tracePath;

        Source(@NotNull String tracePath) {
            this.tracePath = tracePath;
        }

        @NotNull
        public String getTracePath() {
            return tracePath;
        }
    }

    @NotNull
    private final Source source;
    private final boolean matched;
    @Nullable
    private final PatternMatchContext context;
    @NotNull
    private final StructureOperationState operationState;
    @Nullable
    private final FormedStructureMetadata metadata;
    @Nullable
    private final PatternError error;
    @Nullable
    private final BlockPos errorPos;
    @Nullable
    private final String errorMessage;
    @Nullable
    private final StructureFailureTrace failureTrace;
    @Nullable
    private final String tracePathOverride;
    @Nullable
    private final String traceActualDetail;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> missingAbilities;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> abilityCounts;
    @NotNull
    private final StructureChannelValues channelValues;
    private final boolean flipped;
    @Nullable
    private final PieceRuntimes.Publication runtimePublication;
    @Nullable
    private final StructureResultTable resultTable;
    @Nullable
    private final StructureAggregateFolder.Result contributionAggregate;
    @Nullable
    private final StructureEligibilityPlan eligibilityPlan;
    @Nullable
    private final CommittedStructureGraph graphPublication;
    @Nullable
    private final StructureIncrementalCheckResult incrementalCheckResult;
    @Nullable
    private final String adapterTrace;

    private StructureCheckResult(@NotNull Source source,
                                 boolean matched,
                                 @Nullable PatternMatchContext context,
                                 @NotNull StructureOperationState operationState,
                                 @Nullable FormedStructureMetadata metadata,
                                  @Nullable PatternError error,
                                  @Nullable BlockPos errorPos,
                                  @Nullable String errorMessage,
                                  @Nullable StructureFailureTrace failureTrace,
                                  @Nullable String tracePathOverride,
                                  @Nullable String traceActualDetail,
                                  @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                  @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                  @NotNull StructureChannelValues channelValues,
                                  boolean flipped,
                                  @Nullable PieceRuntimes.Publication runtimePublication,
                                  @Nullable StructureResultTable resultTable,
                                  @Nullable StructureAggregateFolder.Result contributionAggregate,
                                  @Nullable StructureEligibilityPlan eligibilityPlan,
                                  @Nullable CommittedStructureGraph graphPublication,
                                  @Nullable StructureIncrementalCheckResult incrementalCheckResult,
                                  @Nullable String adapterTrace) {
        this.source = source;
        this.matched = matched;
        this.context = context == null ? null : context.copy();
        this.operationState = operationState.copy();
        this.metadata = metadata;
        this.error = error;
        this.errorPos = errorPos;
        this.errorMessage = errorMessage;
        this.failureTrace = failureTrace;
        this.tracePathOverride = tracePathOverride;
        this.traceActualDetail = traceActualDetail;
        this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
        this.abilityCounts = Collections.unmodifiableMap(new LinkedHashMap<>(abilityCounts));
        this.channelValues = channelValues.copy();
        this.flipped = flipped;
        this.runtimePublication = runtimePublication;
        this.resultTable = resultTable;
        this.contributionAggregate = contributionAggregate;
        this.eligibilityPlan = eligibilityPlan;
        this.graphPublication = graphPublication;
        this.incrementalCheckResult = incrementalCheckResult;
        this.adapterTrace = adapterTrace;
    }

    @NotNull
    public static StructureCheckResult fromDefinition(@NotNull StructureCheckState.Result result) {
        PatternMatchContext context = result.context;
        return new StructureCheckResult(
                Source.DEFINITION,
                result.success,
                context,
                result.operationState == null ? new StructureOperationState() : result.operationState,
                result.metadata,
                result.error,
                result.errorPos,
                result.errorMessage,
                result.failureTrace,
                null,
                null,
                result.missingAbilities,
                result.abilityCounts,
                context == null ? new StructureChannelValues() : StructureChannelValues.fromContext(context),
                result.flipped,
                result.runtimePublication,
                result.resultTable,
                result.contributionAggregate,
                null,
                null,
                null,
                null);
    }

    @NotNull
    public static StructureCheckResult fromActiveGraphDefinition(
            boolean matched,
            @Nullable PatternMatchContext context,
            @Nullable StructureOperationState operationState,
            @Nullable FormedStructureMetadata metadata,
            @Nullable StructureFailureTrace failureTrace,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            boolean flipped,
            @Nullable PieceRuntimes.Publication runtimePublication,
            @Nullable StructureResultTable resultTable,
            @Nullable StructureAggregateFolder.Result contributionAggregate) {
        return new StructureCheckResult(
                Source.DEFINITION,
                matched,
                context,
                operationState == null ? new StructureOperationState() : operationState,
                metadata,
                failureTrace == null ? null : failureTrace.getError(),
                failureTrace == null ? null : failureTrace.getErrorPos(),
                matched ? null : "Active-graph structure check failed",
                failureTrace,
                "active-graph",
                null,
                missingAbilities,
                abilityCounts,
                context == null ? new StructureChannelValues() : StructureChannelValues.fromContext(context),
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                null,
                null,
                null,
                null);
    }

    @NotNull
    static StructureCheckResult fromIncrementalDefinition(
            boolean matched,
            @Nullable PatternMatchContext context,
            @Nullable StructureOperationState operationState,
            @Nullable FormedStructureMetadata metadata,
            @Nullable StructureFailureTrace failureTrace,
            @Nullable String errorMessage,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            boolean flipped,
            @Nullable PieceRuntimes.Publication runtimePublication,
            @Nullable StructureResultTable resultTable,
            @Nullable StructureAggregateFolder.Result contributionAggregate) {
        return new StructureCheckResult(
                Source.DEFINITION,
                matched,
                context,
                operationState == null ? new StructureOperationState() : operationState,
                metadata,
                failureTrace == null ? null : failureTrace.getError(),
                failureTrace == null ? null : failureTrace.getErrorPos(),
                errorMessage,
                failureTrace,
                "incremental",
                null,
                missingAbilities,
                abilityCounts,
                context == null ? new StructureChannelValues() : StructureChannelValues.fromContext(context),
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                null,
                null,
                null,
                null);
    }

    /**
     * @deprecated Runtime operation paths adapt legacy templates into typed
     *             compiled patterns and no longer create legacy check results.
     *             Retained for external compatibility only.
     */
    @Deprecated
    @NotNull
    public static StructureCheckResult fromLegacy(@Nullable PatternMatchContext context,
                                                  @NotNull MultiblockState state) {
        boolean matched = context != null;
        StructureOperationState operationState = matched
                ? StructureOperationState.fromLegacyContext(context)
                : new StructureOperationState();
        return new StructureCheckResult(
                Source.LEGACY_TEMPLATE,
                matched,
                context,
                operationState,
                null,
                matched ? null : state.getError(),
                null,
                matched ? null : "Legacy structure template did not match",
                null,
                null,
                null,
                matched ? Collections.emptyMap() : state.getMissingAbilities(),
                Collections.emptyMap(),
                matched ? StructureChannelValues.fromContext(context) : new StructureChannelValues(),
                matched && context.neededFlip(),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @NotNull
    public Source getSource() {
        return source;
    }

    @NotNull
    public String getTracePath() {
        return tracePathOverride == null ? source.getTracePath() : tracePathOverride;
    }

    @NotNull
    public StructureOperationDiagnostics getDiagnostics() {
        int pieceCount = resultTable == null ? 0 : resultTable.size();
        boolean syntheticSinglePiece = "v3-typed-single".equals(getTracePath());
        return StructureOperationDiagnostics.of(
                getTracePath(),
                StructureEvaluationContext.Operation.MATCH_WORLD.name(),
                traceActualDetail,
                pieceCount,
                syntheticSinglePiece)
                .withAdapterTrace(adapterTrace);
    }

    @NotNull
    public StructureCheckResult withTraceContext(@NotNull String tracePath,
                                                 @Nullable String actualDetail) {
        return new StructureCheckResult(
                source,
                matched,
                context,
                operationState,
                metadata,
                error,
                errorPos,
                errorMessage,
                failureTrace,
                tracePath,
                actualDetail,
                missingAbilities,
                abilityCounts,
                channelValues,
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                eligibilityPlan,
                graphPublication,
                incrementalCheckResult,
                adapterTrace);
    }

    @NotNull
    public StructureCheckResult withGraphPublication(
            @Nullable CommittedStructureGraph graphPublication) {
        return new StructureCheckResult(
                source,
                matched,
                context,
                operationState,
                metadata,
                error,
                errorPos,
                errorMessage,
                failureTrace,
                tracePathOverride,
                traceActualDetail,
                missingAbilities,
                abilityCounts,
                channelValues,
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                eligibilityPlan,
                graphPublication,
                incrementalCheckResult,
                adapterTrace);
    }

    @NotNull
    public StructureCheckResult withIncrementalCheckResult(
            @Nullable StructureIncrementalCheckResult incrementalCheckResult) {
        return new StructureCheckResult(
                source,
                matched,
                context,
                operationState,
                metadata,
                error,
                errorPos,
                errorMessage,
                failureTrace,
                tracePathOverride,
                traceActualDetail,
                missingAbilities,
                abilityCounts,
                channelValues,
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                eligibilityPlan,
                graphPublication,
                incrementalCheckResult,
                adapterTrace);
    }

    @NotNull
    public StructureCheckResult withEligibilityPlan(@Nullable StructureEligibilityPlan eligibilityPlan) {
        return new StructureCheckResult(
                source,
                matched,
                context,
                operationState,
                metadata,
                error,
                errorPos,
                errorMessage,
                failureTrace,
                tracePathOverride,
                traceActualDetail,
                missingAbilities,
                abilityCounts,
                channelValues,
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                eligibilityPlan,
                graphPublication,
                incrementalCheckResult,
                adapterTrace);
    }

    @NotNull
    public StructureCheckResult withAdapterTrace(@Nullable String adapterTrace) {
        if (adapterTrace == null || adapterTrace.isEmpty()) {
            return this;
        }
        return new StructureCheckResult(
                source,
                matched,
                context,
                operationState,
                metadata,
                error,
                errorPos,
                errorMessage,
                failureTrace,
                tracePathOverride,
                traceActualDetail,
                missingAbilities,
                abilityCounts,
                channelValues,
                flipped,
                runtimePublication,
                resultTable,
                contributionAggregate,
                eligibilityPlan,
                graphPublication,
                incrementalCheckResult,
                adapterTrace);
    }

    public boolean isMatched() {
        return matched;
    }

    @ApiStatus.Internal
    public boolean hasLegacyContext() {
        return context != null;
    }

    @Nullable
    public PatternMatchContext copyContext() {
        if (context == null) {
            return null;
        }
        PatternMatchContext copy = context.copy();
        operationState.applyCompatibilityView(copy);
        return copy;
    }

    @NotNull
    public StructureOperationState copyOperationState() {
        return operationState.copy();
    }

    @Nullable
    public FormedStructureMetadata getMetadata() {
        return metadata;
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getMissingAbilities() {
        return missingAbilities;
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
        return abilityCounts;
    }

    @NotNull
    public StructureChannelValues copyChannelValues() {
        return channelValues.copy();
    }

    public boolean isFlipped() {
        return flipped;
    }

    @Nullable
    public StructureResultTable getResultTable() {
        return resultTable;
    }

    @Nullable
    public StructureAggregateFolder.Result getContributionAggregate() {
        return contributionAggregate;
    }

    @Nullable
    public StructureEligibilityPlan getEligibilityPlan() {
        return eligibilityPlan;
    }

    @Nullable
    public PieceRuntimes.Publication getRuntimePublication() {
        return effectiveRuntimePublication();
    }

    @Nullable
    public CommittedStructureGraph getGraphPublication() {
        return graphPublication;
    }

    @Nullable
    public StructureIncrementalCheckResult getIncrementalCheckResult() {
        return incrementalCheckResult;
    }

    public boolean usedActiveGraphFallback() {
        return eligibilityPlan != null
                && !eligibilityPlan.isEligible()
                && "active-graph-fallback".equals(getTracePath());
    }

    public boolean usedIncrementalEvaluator() {
        return incrementalCheckResult != null && "incremental".equals(getTracePath());
    }

    /**
     * Publish operation-local piece runtimes after controller-side assembly
     * validation succeeds.
     *
     * @return true when this result carried a publication payload
     */
    public boolean publishPieceRuntimes(@NotNull PieceRuntimes target) {
        PieceRuntimes.Publication publication = effectiveRuntimePublication();
        if (publication == null) {
            return false;
        }
        target.publish(publication);
        return true;
    }

    /**
     * Validate the runtime publication before controller commit side effects.
     */
    public void validatePieceRuntimePublication(@NotNull PieceRuntimes target) {
        PieceRuntimes.Publication publication = effectiveRuntimePublication();
        if (publication != null) {
            target.validatePublication(publication);
        }
    }

    @Nullable
    private PieceRuntimes.Publication effectiveRuntimePublication() {
        if (runtimePublication != null) {
            return runtimePublication;
        }
        return graphPublication == null ? null : graphPublication.getRuntimePublication();
    }

    @NotNull
    public StructureFailureTrace createFailureTrace(@NotNull MultiblockControllerBase controller) {
        if (failureTrace != null) {
            if (tracePathOverride != null || traceActualDetail != null) {
                return copyFailureTrace(controller, failureTrace);
            }
            return failureTrace;
        }
        StructureFailureTrace.Builder builder =
                new StructureFailureTrace.Builder(controller.getMetaName(), controller.getPos())
                        .formed(controller.isStructureFormed())
                        .orientation(StructureOrientation.fromController(controller))
                        .path(getTracePath())
                        .operation("CHECK")
                        .result(missingAbilities.isEmpty()
                                ? classifyError(error).getTraceName()
                                : StructureFailureTrace.Kind.MISSING_ABILITY.getTraceName())
                        .kind(missingAbilities.isEmpty()
                                ? classifyError(error)
                                : StructureFailureTrace.Kind.MISSING_ABILITY)
                        .missingAbilities(missingAbilities)
                        .abilityCounts(abilityCounts);
        if (error != null) {
            builder.error(error);
        } else {
            builder.errorPosition(errorPos);
        }
        if (traceActualDetail != null) {
            builder.actual(errorMessage == null ? traceActualDetail : errorMessage + "; " + traceActualDetail);
        } else if (error == null && errorMessage != null) {
            builder.actual(errorMessage);
        }
        return builder.build();
    }

    @NotNull
    private StructureFailureTrace copyFailureTrace(@NotNull MultiblockControllerBase controller,
                                                  @NotNull StructureFailureTrace failure) {
        StructureFailureTrace.Builder builder =
                new StructureFailureTrace.Builder(controller.getMetaName(), controller.getPos())
                        .formed(controller.isStructureFormed())
                        .orientation(StructureOrientation.fromController(controller))
                        .path(getTracePath())
                        .operation(failure.getOperation())
                        .result(failure.getResult())
                        .kind(failure.getKind())
                        .piece(failure.getPiece())
                        .cell(failure.getCell())
                        .progressDepth(failure.getProgressDepth())
                        .missingAbilities(missingAbilities)
                        .abilityCounts(abilityCounts);
        if (failure.getError() != null) {
            builder.error(failure.getError());
        } else {
            builder.errorPosition(failure.getErrorPos());
        }
        builder.expected(failure.getExpected())
                .actual(appendDetail(failure.getActual(), traceActualDetail));
        return builder.build();
    }

    @Nullable
    private static String appendDetail(@Nullable String actual,
                                       @Nullable String detail) {
        if (detail == null || detail.isEmpty()) {
            return actual;
        }
        if (actual == null || actual.isEmpty()) {
            return detail;
        }
        return actual + "; " + detail;
    }

    @NotNull
    private static StructureFailureTrace.Kind classifyError(@Nullable PatternError error) {
        if (error instanceof TraceabilityPredicate.SinglePredicateError) {
            TraceabilityPredicate.SinglePredicateError single =
                    (TraceabilityPredicate.SinglePredicateError) error;
            if (single.type == 0 || single.type == 2) {
                return StructureFailureTrace.Kind.COUNT_LIMIT;
            }
        }
        if (error == null) {
            return StructureFailureTrace.Kind.LEGACY_PATTERN;
        }
        return StructureFailureTrace.Kind.BLOCK_MISMATCH;
    }
}
