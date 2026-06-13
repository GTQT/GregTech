package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.PieceRuntime;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructureMatchSession;
import gregtech.api.pattern.StructureOperationState;
import gregtech.api.pattern.StructureActivationContext;
import gregtech.api.pattern.StructureFailureSelection;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

        private Result(boolean success, @Nullable FormedStructureMetadata metadata,
                       @Nullable PatternMatchContext context,
                       @Nullable StructureOperationState operationState,
                       @Nullable BlockPos errorPos, @Nullable String errorMessage,
                       @Nullable PatternError error,
                       @Nullable StructureFailureTrace failureTrace,
                       @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                       @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                       boolean flipped) {
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
        }

        /** Create a success result with formed metadata and aggregated context */
        @NotNull
        public static Result success(@NotNull FormedStructureMetadata metadata,
                                     @NotNull PatternMatchContext context,
                                     @NotNull StructureOperationState operationState,
                                     boolean flipped) {
            return new Result(
                    true, metadata, context, operationState,
                    null, null, null, null, Collections.emptyMap(), Collections.emptyMap(), flipped);
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
                    pos, msg, error, null, Collections.emptyMap(), Collections.emptyMap(), false);
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
                    null, msg, error, null, Collections.emptyMap(), Collections.emptyMap(), false);
        }

        @NotNull
        public static Result failure(@NotNull String msg,
                                     @Nullable StructureFailureTrace failureTrace,
                                     @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                     @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                     boolean flipped) {
            return new Result(
                    false, null, null, null,
                    failureTrace == null ? null : failureTrace.getErrorPos(), msg,
                    failureTrace == null ? null : failureTrace.getError(),
                    failureTrace, missingAbilities, abilityCounts, flipped);
        }

        @NotNull
        public static Result missingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                              @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                              @Nullable StructureFailureTrace failureTrace,
                                              boolean flipped) {
            return new Result(
                    false, null, null, null,
                    null, "Missing required multiblock abilities",
                    failureTrace == null ? null : failureTrace.getError(),
                    failureTrace, missingAbilities, abilityCounts, flipped);
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
        StructureMatchSession session = pattern.createMatchSession(context);
        session.setControllerContext(controller);

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
                    new StructureActivationContext<>(controller, world, controllerPos, prior, session);
            if (!piece.isActive(activation)) continue;
            BlockPos centerPos = piece.getCenterPos(controllerPos, orientation, prior);
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
                        world, controllerPos, orientation, prior, runtime, session);
                if (!ok) {
                    lastErrorPos = controllerPos;
                    lastErrorMessage = "Repeatable piece '" + piece.getName() + "' failed pattern check";
                    StructureFailureTrace failure = createFailureTrace(
                            controller, controllerPos, orientation, piece.getName(), null,
                            activePieceDepth(pattern, piece), lastErrorMessage,
                            runtime.getState().getError(), runtime.getState().getMissingAbilities(),
                            session.copyOperationState().getAbilityCounts(),
                            classifyError(runtime.getState().getError(),
                                    runtime.getState().getMissingAbilities()));
                    return Result.failure(lastErrorMessage, failure,
                            runtime.getState().getMissingAbilities(),
                            session.copyOperationState().getAbilityCounts(), orientation.isFlipped());
                }

                // Extract repeat counts from the runtime's cached reps
                int[] formedReps = runtime.getLastFormedReps();
                if (formedReps != null && formedReps.length > 0) {
                    pieceRepeats.put(piece.getName(), formedReps.clone());
                }

                extractChannelValues(session.getContext(), channelValues);
            } else {
                // Fixed piece: standard single-template check
                // Use the 4-arg getCenterPos so dynamic-anchor pieces can compute
                // their position from the prior pieces' repeat counts.
                boolean pieceMatched = session.tryFork(pieceSession ->
                        runtime.getState().checkPatternAtExact(
                                world, centerPos, orientation, 0, 0, 0, pieceSession) != null);
                if (!pieceMatched) {
                    lastErrorPos = centerPos;
                    lastErrorMessage = "Piece '" + piece.getName() + "' failed pattern check";
                    StructureFailureTrace failure = createFailureTrace(
                            controller, controllerPos, orientation, piece.getName(),
                            describeCell(runtime.getState().getError()),
                            activePieceDepth(pattern, piece), lastErrorMessage,
                            runtime.getState().getError(), runtime.getState().getMissingAbilities(),
                            session.copyOperationState().getAbilityCounts(),
                            classifyError(runtime.getState().getError(),
                                    runtime.getState().getMissingAbilities()));
                    return Result.failure(lastErrorMessage, failure,
                            runtime.getState().getMissingAbilities(),
                            session.copyOperationState().getAbilityCounts(), orientation.isFlipped());
                }

                // Extract repeat counts from the piece's MultiblockState
                int[] formedReps = runtime.getState().formedRepetitionCount;
                if (formedReps != null && formedReps.length > 0) {
                    pieceRepeats.put(piece.getName(), formedReps.clone());
                }

                // Extract channel values from the match context
                extractChannelValues(session.getContext(), channelValues);
            }
            pieceCenters.put(piece.getName(), centerPos);
            if (ConfigHolder.machines.debugStructureCheck) {
                int[] formedReps = pieceRepeats.get(piece.getName());
                GTLog.logger.debug("[StructureDefinition] matched piece={} center={} repetitions={}",
                        piece.getName(), centerPos,
                        formedReps == null ? "[]" : Arrays.toString(formedReps));
            }
        }

        StructureMatchSession.Validation validation = session.validate(true);
        if (!validation.success) {
            if (!validation.missingAbilities.isEmpty()) {
                StructureFailureTrace failure = createDeferredFailureTrace(
                        controller, controllerPos, orientation, "Missing required multiblock abilities",
                        StructureFailureTrace.Kind.MISSING_ABILITY,
                        pattern.getPieceList().size(), validation.missingAbilities, validation.abilityCounts,
                        lastActivePieceName, lastActivePieceCenter);
                return Result.missingAbilities(validation.missingAbilities, validation.abilityCounts,
                        failure, orientation.isFlipped());
            }
            String message = validation.errorMessage == null
                    ? "Structure-wide validation failed"
                    : validation.errorMessage;
            StructureFailureTrace failure = createDeferredFailureTrace(
                    controller, controllerPos, orientation, message,
                    StructureFailureTrace.Kind.COUNT_LIMIT,
                    pattern.getPieceList().size(), validation.missingAbilities, validation.abilityCounts,
                    lastActivePieceName, lastActivePieceCenter);
            return Result.failure(message, failure, validation.missingAbilities,
                    validation.abilityCounts, orientation.isFlipped());
        }

        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                pieceRepeats, channelValues, pieceCenters);
        return Result.success(
                metadata, session.getContext().copy(), session.copyOperationState(), orientation.isFlipped());
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
