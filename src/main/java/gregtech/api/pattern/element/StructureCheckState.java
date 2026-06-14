package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.PieceRuntime;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.PieceEvaluationResult;
import gregtech.api.pattern.StructureAggregateFolder;
import gregtech.api.pattern.StructureMatchSession;
import gregtech.api.pattern.StructureOperationState;
import gregtech.api.pattern.StructureResultTable;
import gregtech.api.pattern.StructureActivationContext;
import gregtech.api.pattern.StructureFailureSelection;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureContribution;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.StructureSnapshotResult;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-check mutable state for structure checking.
 * Created via {@link StructureDefinition#createState()}.
 * Each check operation should use its own state instance.
 *
 * <p>The {@link #check} method returns a {@link Result} containing:
 * <ul>
 *   <li>Success/failure status</li>
 *   <li>{@link FormedStructureMetadata} on success (actual repeat counts + channel values)</li>
 *   <li>Typed {@link StructureOperationState} plus a legacy context view on success</li>
 *   <li>Error position and message on failure</li>
 * </ul>
 */
public final class StructureCheckState {

    private final StructureDefinition<?> definition;

    @Nullable
    private BlockPos lastErrorPos;

    @Nullable
    private String lastErrorMessage;

    StructureCheckState(@NotNull StructureDefinition<?> definition) {
        this.definition = definition;
    }

    /**
     * Check result containing success/failure status and metadata.
     */
    public static final class Result {

        public final boolean success;

        @Nullable
        public final FormedStructureMetadata metadata;

        /** Legacy context data from all pieces. Collector-owned data is stored separately. */
        @Nullable
        public final PatternMatchContext context;

        @Nullable
        public final StructureOperationState operationState;

        @Nullable
        public final BlockPos errorPos;

        @Nullable
        public final String errorMessage;

        @Nullable
        public final PatternError error;

        @Nullable
        public final StructureFailureTrace failureTrace;

        @NotNull
        public final Map<MultiblockAbility<?>, Integer> missingAbilities;

        @NotNull
        public final Map<MultiblockAbility<?>, Integer> abilityCounts;

        public final boolean flipped;

        @Nullable
        public final PieceRuntimes.Publication runtimePublication;

        @Nullable
        public final StructureResultTable resultTable;

        @Nullable
        public final StructureAggregateFolder.Result contributionAggregate;

        private Result(boolean success, @Nullable FormedStructureMetadata metadata,
                       @Nullable PatternMatchContext context,
                       @Nullable StructureOperationState operationState,
                       @Nullable BlockPos errorPos, @Nullable String errorMessage,
                       @Nullable PatternError error,
                       @Nullable StructureFailureTrace failureTrace,
                       @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                       @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                       boolean flipped,
                       @Nullable PieceRuntimes.Publication runtimePublication,
                       @Nullable StructureResultTable resultTable,
                       @Nullable StructureAggregateFolder.Result contributionAggregate) {
            this.success = success;
            this.metadata = metadata;
            this.context = context;
            this.operationState = operationState;
            this.errorPos = errorPos;
            this.errorMessage = errorMessage;
            this.error = error;
            this.failureTrace = failureTrace;
            this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
            this.abilityCounts = Collections.unmodifiableMap(new LinkedHashMap<>(abilityCounts));
            this.flipped = flipped;
            this.runtimePublication = runtimePublication;
            this.resultTable = resultTable;
            this.contributionAggregate = contributionAggregate;
        }

        /** Create a success result with formed metadata and aggregated context */
        @NotNull
        public static Result success(@NotNull FormedStructureMetadata metadata,
                                     @NotNull PatternMatchContext context,
                                     @NotNull StructureOperationState operationState,
                                     boolean flipped,
                                     @NotNull PieceRuntimes.Publication runtimePublication,
                                     @NotNull StructureResultTable resultTable,
                                     @NotNull StructureAggregateFolder.Result contributionAggregate) {
            return new Result(
                    true, metadata, context, operationState,
                    null, null, null, null, Collections.emptyMap(), Collections.emptyMap(),
                    flipped, runtimePublication, resultTable, contributionAggregate);
        }

        /** Create a failure result with error info */
        @NotNull
        public static Result failure(@NotNull BlockPos pos, @NotNull String msg) {
            return failure(pos, msg, null);
        }

        /** Create a failure result with error info */
        @NotNull
        public static Result failure(@NotNull BlockPos pos, @NotNull String msg, @Nullable PatternError error) {
            return new Result(
                    false, null, null, null,
                    pos, msg, error, null, Collections.emptyMap(), Collections.emptyMap(),
                    false, null, null, null);
        }

        /** Create a failure result without specific position */
        @NotNull
        public static Result failure(@NotNull String msg) {
            return failure(msg, null);
        }

        /** Create a failure result without specific position */
        @NotNull
        public static Result failure(@NotNull String msg, @Nullable PatternError error) {
            return new Result(
                    false, null, null, null,
                    null, msg, error, null, Collections.emptyMap(), Collections.emptyMap(),
                    false, null, null, null);
        }

        @NotNull
        public static Result failure(@NotNull String msg,
                                     @Nullable StructureFailureTrace failureTrace,
                                     @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                     @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                     boolean flipped) {
            return failure(
                    msg, failureTrace, missingAbilities, abilityCounts, flipped, null, null);
        }

        @NotNull
        public static Result failure(@NotNull String msg,
                                     @Nullable StructureFailureTrace failureTrace,
                                     @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                     @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                     boolean flipped,
                                     @Nullable StructureResultTable resultTable,
                                     @Nullable StructureAggregateFolder.Result contributionAggregate) {
            return new Result(
                    false, null, null, null,
                    failureTrace == null ? null : failureTrace.getErrorPos(), msg,
                    failureTrace == null ? null : failureTrace.getError(),
                    failureTrace, missingAbilities, abilityCounts, flipped, null,
                    resultTable, contributionAggregate);
        }

        @NotNull
        public static Result missingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                              @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                              @Nullable StructureFailureTrace failureTrace,
                                              boolean flipped) {
            return missingAbilities(
                    missingAbilities, abilityCounts, failureTrace, flipped, null, null);
        }

        @NotNull
        public static Result missingAbilities(
                @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                @Nullable StructureFailureTrace failureTrace,
                boolean flipped,
                @Nullable StructureResultTable resultTable,
                @Nullable StructureAggregateFolder.Result contributionAggregate) {
            return new Result(
                    false, null, null, null,
                    null, "Missing required multiblock abilities",
                    failureTrace == null ? null : failureTrace.getError(),
                    failureTrace, missingAbilities, abilityCounts, flipped, null,
                    resultTable, contributionAggregate);
        }
    }

    /**
     * Perform a synchronous structure check.
     *
     * @param world         the world
     * @param controllerPos the controller position
     * @param front         the front facing (into-structure direction)
     * @param up            the upward facing
     * @param flipped       whether the structure is flipped
     * @param context       optional pattern match context (null = create new)
     * @return the check result
     */
    @NotNull
    public Result check(@NotNull World world, @NotNull BlockPos controllerPos,
                        @NotNull StructureOrientation orientation,
                        @Nullable PatternMatchContext context) {
        return check(world, controllerPos, orientation, context, null);
    }

    @NotNull
    public Result check(@NotNull World world, @NotNull BlockPos controllerPos,
                        @NotNull StructureOrientation orientation,
                        @Nullable PatternMatchContext context,
                        @Nullable MultiblockControllerBase controller) {
        Result result = checkOrientation(world, controllerPos, orientation.withFlipped(false), context, controller);
        if (!result.success && orientation.allowsFlip()) {
            Result flippedResult =
                    checkOrientation(world, controllerPos, orientation.withFlipped(true), context, controller);
            if (!flippedResult.success) {
                StructureFailureTrace selected = StructureFailureSelection.select(
                        result.failureTrace, flippedResult.failureTrace);
                return selected == result.failureTrace ? result : flippedResult;
            }
            return flippedResult;
        }
        return result;
    }

    @NotNull
    private Result checkOrientation(@NotNull World world, @NotNull BlockPos controllerPos,
                                    @NotNull StructureOrientation orientation,
                                    @Nullable PatternMatchContext context,
                                    @Nullable MultiblockControllerBase controller) {
        MultiPiecePattern pattern = definition.getCompiledPattern();

        // Per-check transient runtimes: StructureCheckState.check is called from the
        // main thread (e.g. JEI preview render, structure shape computation) and
        // must not mutate the controller's owned PieceRuntimes, which may be
        // concurrently read by the async structure checker.
        PieceRuntimes transientRuntimes = new PieceRuntimes(pattern);
        StructureResultTable.Builder resultTable = StructureResultTable.builder(pattern);

        // Collect piece repeat counts and channel values
        Map<String, int[]> pieceRepeats = new HashMap<>();
        Map<String, Integer> channelValues = new HashMap<>();
        Map<String, BlockPos> pieceCenters = new HashMap<>();
        String lastActivePieceName = null;
        BlockPos lastActivePieceCenter = null;

        for (StructurePiece piece : pattern.getPieceList()) {
            // Per-piece runtime for this check. Always non-null since we just built
            // the runtimes from the same pattern above.
            PieceRuntime runtime = transientRuntimes.get(piece);

            // Build a FormedStructureMetadata snapshot of all previously-checked
            // pieces, so a DynamicOffsetPiece can read the repeat count of its
            // anchor at the time this piece's center position is computed. This
            // is rebuilt every iteration because FormedStructureMetadata is
            // immutable.
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(pieceRepeats), new HashMap<>(channelValues),
                    new HashMap<>(pieceCenters));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(controller, world, controllerPos, prior, null);
            if (!piece.isActive(activation)) {
                resultTable.add(PieceEvaluationResult.inactive(piece));
                continue;
            }
            BlockPos centerPos = piece.getCenterPos(controllerPos, orientation, prior);
            StructureMatchSession pieceSession = pattern.createMatchSession(context);
            pieceSession.setControllerContext(controller);
            pieceSession.beginPieceContribution(piece);
            lastActivePieceName = piece.getName();
            lastActivePieceCenter = centerPos;
            if (ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug(
                        "[StructureDefinition] checking piece={} center={} front={} up={} flipped={}",
                        piece.getName(), centerPos, orientation.getStructureFront(),
                        orientation.getUp(), orientation.isFlipped());
            }

            if (piece instanceof RepeatGroupPiece repeatPiece) {
                // Repeatable piece: use synchronous World-based check
                // (uses checkPatternFastAt for cache-accelerated checks)
                boolean ok = repeatPiece.checkSync(
                        world, controllerPos, orientation, prior, runtime, pieceSession);
                if (!ok) {
                    pieceSession.discardPieceContribution(piece);
                    lastErrorPos = controllerPos;
                    lastErrorMessage = "Repeatable piece '" + piece.getName() + "' failed pattern check";
                    StructureFailureTrace failure = createFailureTrace(
                            controller, controllerPos, orientation, piece.getName(), null,
                            activePieceDepth(pattern, piece), lastErrorMessage,
                            runtime.getState().getError(), runtime.getState().getMissingAbilities(),
                            pieceSession.copyOperationState().getAbilityCounts(),
                            classifyError(runtime.getState().getError(),
                                    runtime.getState().getMissingAbilities()));
                    return Result.failure(lastErrorMessage, failure,
                            runtime.getState().getMissingAbilities(),
                            pieceSession.copyOperationState().getAbilityCounts(), orientation.isFlipped());
                }
                runtime.setValidated(true);
                runtime.clearDirty();

                // Extract repeat counts from the runtime's cached reps
                int[] formedReps = runtime.getLastFormedReps();
                if (formedReps != null && formedReps.length > 0) {
                    pieceRepeats.put(piece.getName(), formedReps.clone());
                }

                runtime.setLastAggregatedContext(null);
            } else {
                // Fixed piece: standard single-template check
                // Use the 4-arg getCenterPos so dynamic-anchor pieces can compute
                // their position from the prior pieces' repeat counts.
                boolean pieceMatched = pieceSession.tryFork(candidate ->
                        runtime.getState().checkPatternAtExact(
                                world, centerPos, orientation, 0, 0, 0, candidate) != null);
                if (!pieceMatched) {
                    pieceSession.discardPieceContribution(piece);
                    lastErrorPos = centerPos;
                    lastErrorMessage = "Piece '" + piece.getName() + "' failed pattern check";
                    StructureFailureTrace failure = createFailureTrace(
                            controller, controllerPos, orientation, piece.getName(),
                            describeCell(runtime.getState().getError()),
                            activePieceDepth(pattern, piece), lastErrorMessage,
                            runtime.getState().getError(), runtime.getState().getMissingAbilities(),
                            pieceSession.copyOperationState().getAbilityCounts(),
                            classifyError(runtime.getState().getError(),
                                    runtime.getState().getMissingAbilities()));
                    return Result.failure(lastErrorMessage, failure,
                            runtime.getState().getMissingAbilities(),
                            pieceSession.copyOperationState().getAbilityCounts(), orientation.isFlipped());
                }
                runtime.setValidated(true);
                runtime.clearDirty();
                runtime.swapPositions(new LongOpenHashSet(runtime.getState().cache.keySet()));

                // Extract repeat counts from the piece's MultiblockState
                int[] formedReps = runtime.getState().formedRepetitionCount;
                if (formedReps != null && formedReps.length > 0) {
                    pieceRepeats.put(piece.getName(), formedReps.clone());
                    runtime.cacheFormedReps(formedReps);
                }

            }
            pieceCenters.put(piece.getName(), centerPos);
            int[] resultRepetitions = piece instanceof RepeatGroupPiece
                    ? runtime.getLastFormedReps()
                    : runtime.getState().formedRepetitionCount;
            StructureContribution contribution = pieceSession.finishPieceContribution(piece);
            PatternMatchContext compatibilityContext =
                    contribution.projectCompatibilityContext(pieceSession.getContext());
            extractChannelValues(compatibilityContext, channelValues);
            resultTable.add(PieceEvaluationResult.activeMatchedWithRuntime(
                    piece, centerPos, resultRepetitions,
                    runtime.getPositions(), runtime.getPositions(),
                    runtime, contribution, compatibilityContext));
            if (ConfigHolder.machines.debugStructureCheck) {
                int[] formedReps = pieceRepeats.get(piece.getName());
                GTLog.logger.debug("[StructureDefinition] matched piece={} center={} repetitions={}",
                        piece.getName(), centerPos,
                        formedReps == null ? "[]" : Arrays.toString(formedReps));
            }
        }

        StructureResultTable completedTable = resultTable.build();
        StructureAggregateFolder.Result contributionAggregate =
                StructureAggregateFolder.fold(pattern, completedTable, context);
        if (!contributionAggregate.isMatched()) {
            if (!contributionAggregate.getMissingAbilities().isEmpty()) {
                StructureFailureTrace failure = createDeferredFailureTrace(
                        controller, controllerPos, orientation, "Missing required multiblock abilities",
                        StructureFailureTrace.Kind.MISSING_ABILITY,
                        pattern.getPieceList().size(), contributionAggregate.getMissingAbilities(),
                        contributionAggregate.getAbilityCounts(),
                        lastActivePieceName, lastActivePieceCenter);
                return Result.missingAbilities(
                        contributionAggregate.getMissingAbilities(), contributionAggregate.getAbilityCounts(),
                        failure, orientation.isFlipped(), completedTable, contributionAggregate);
            }
            String message = contributionAggregate.getErrorMessage() == null
                    ? "Structure-wide validation failed"
                    : contributionAggregate.getErrorMessage();
            StructureFailureTrace failure = createDeferredFailureTrace(
                    controller, controllerPos, orientation, message,
                    StructureFailureTrace.Kind.COUNT_LIMIT,
                    pattern.getPieceList().size(), contributionAggregate.getMissingAbilities(),
                    contributionAggregate.getAbilityCounts(),
                    lastActivePieceName, lastActivePieceCenter);
            return Result.failure(message, failure, contributionAggregate.getMissingAbilities(),
                    contributionAggregate.getAbilityCounts(), orientation.isFlipped(),
                    completedTable, contributionAggregate);
        }

        return Result.success(
                contributionAggregate.getMetadata(), contributionAggregate.copyCompatibilityContext(),
                contributionAggregate.copyOperationState(),
                orientation.isFlipped(), transientRuntimes.capturePublication(),
                completedTable, contributionAggregate);
    }

    @NotNull
    public StructureSnapshotResult checkSnapshot(
            @NotNull IBlockAccess snapshot,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        StructureSnapshotResult result = checkSnapshotOrientation(
                snapshot, controllerPos, orientation.withFlipped(false), controller);
        if (!result.isMatched() && orientation.allowsFlip()) {
            StructureSnapshotResult flipped = checkSnapshotOrientation(
                    snapshot, controllerPos, orientation.withFlipped(true), controller);
            return StructureSnapshotResult.selectFailure(result, flipped);
        }
        return result;
    }

    @NotNull
    private StructureSnapshotResult checkSnapshotOrientation(
            @NotNull IBlockAccess snapshot,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        MultiPiecePattern pattern = definition.getCompiledPattern();
        PieceRuntimes transientRuntimes = new PieceRuntimes(pattern);
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

            PieceRuntime runtime = transientRuntimes.get(piece);
            BlockPos pieceCenter = piece.getCenterPos(controllerPos, orientation, prior);
            BlockPos checkOrigin = piece instanceof RepeatGroupPiece
                    ? controllerPos
                    : pieceCenter;
            boolean matched = session.tryFork(pieceSession ->
                    piece.checkOnSnapshot(
                            snapshot, checkOrigin, orientation, prior, runtime, pieceSession));
            if (!matched) {
                return StructureSnapshotResult.mismatch(
                        orientation.isFlipped(), piece.getName(), progressDepth);
            }

            int[] repetitions = piece instanceof RepeatGroupPiece
                    ? runtime.getLastFormedReps()
                    : runtime.getState().formedRepetitionCount;
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

    /**
     * Extract channel values from a pattern match context.
     * Channel values are stored as Integer entries in the context by the predicate system.
     */
    private void extractChannelValues(@NotNull PatternMatchContext context,
                                      @NotNull Map<String, Integer> channelValues) {
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                channelValues.putIfAbsent(entry.getKey(), (Integer) entry.getValue());
            }
        }
    }

    @NotNull
    private static StructureFailureTrace createDeferredFailureTrace(
            @Nullable MultiblockControllerBase controller,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull String message,
            @NotNull StructureFailureTrace.Kind kind,
            int progressDepth,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            @Nullable String pieceName,
            @Nullable BlockPos errorPos) {
        return traceBuilder(controller, controllerPos, orientation)
                .path("definition")
                .operation("CHECK")
                .result(kind.getTraceName())
                .kind(kind)
                .piece(pieceName == null ? "deferred" : pieceName)
                .cell("requirements")
                .errorPosition(errorPos)
                .progressDepth(progressDepth)
                .expected(kind == StructureFailureTrace.Kind.MISSING_ABILITY
                        ? "required abilities present"
                        : "requirements within declared limits")
                .actual(message)
                .missingAbilities(missingAbilities)
                .abilityCounts(abilityCounts)
                .build();
    }

    @NotNull
    private static StructureFailureTrace createFailureTrace(
            @Nullable MultiblockControllerBase controller,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull String pieceName,
            @Nullable String cell,
            int progressDepth,
            @NotNull String message,
            @Nullable PatternError error,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
            @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
            @NotNull StructureFailureTrace.Kind kind) {
        StructureFailureTrace.Builder builder = traceBuilder(controller, controllerPos, orientation)
                .path("definition")
                .operation("CHECK")
                .result(kind.getTraceName())
                .kind(kind)
                .piece(pieceName)
                .cell(cell)
                .progressDepth(progressDepth)
                .missingAbilities(missingAbilities)
                .abilityCounts(abilityCounts);
        if (error != null) {
            builder.error(error);
        } else {
            builder.errorPosition(controllerPos)
                    .expected("piece pattern matched")
                    .actual(message);
        }
        return builder.build();
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
    public BlockPos getLastErrorPos() {
        return lastErrorPos;
    }

    @Nullable
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
