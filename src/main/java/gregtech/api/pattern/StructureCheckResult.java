package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCheckState;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable operation-level result for a synchronous structure check.
 *
 * <p>This normalizes definition and legacy-template checks before controller
 * assembly begins. Mutable context/channel data is copied at the boundary so a
 * later traversal cannot change a result that is waiting to be committed.
 */
public final class StructureCheckResult {

    public enum Source {
        DEFINITION("definition"),
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
                                 boolean flipped) {
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
                result.flipped);
    }

    @NotNull
    public static StructureCheckResult fromDirtyPieceDefinition(
            boolean matched,
            @Nullable PatternMatchContext context,
            @Nullable StructureOperationState operationState,
            @Nullable FormedStructureMetadata metadata,
            @Nullable StructureFailureTrace failureTrace,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            boolean flipped) {
        return new StructureCheckResult(
                Source.DEFINITION,
                matched,
                context,
                operationState == null ? new StructureOperationState() : operationState,
                metadata,
                failureTrace == null ? null : failureTrace.getError(),
                failureTrace == null ? null : failureTrace.getErrorPos(),
                matched ? null : "Dirty-piece structure check failed",
                failureTrace,
                "dirty-piece",
                null,
                missingAbilities,
                abilityCounts,
                context == null ? new StructureChannelValues() : StructureChannelValues.fromContext(context),
                flipped);
    }

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
                matched && context.neededFlip());
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
                flipped);
    }

    public boolean isMatched() {
        return matched;
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
