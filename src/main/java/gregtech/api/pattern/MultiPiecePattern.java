package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A composite multi-piece pattern for super-large multiblock structures.
 * Instead of a single monolithic pattern, the structure is divided into named pieces,
 * each with its own template and offset. The pattern itself is pure shared data;
 * per-controller state lives in {@link PieceRuntimes}.
 *
 * <p>When a block changes, the piece(s) containing that position are marked dirty.
 * The next event-driven check re-validates the complete active graph because shared
 * counts, context, activation, and dynamic offsets cross piece boundaries. The
 * dirty/validated flags and the formed-position set live on the per-controller {@link PieceRuntime}
 * (indexed by piece identity), so the compiled pattern itself is stateless and
 * safe to share across controllers of the same multiblock type.
 *
 * <p>Most check / auto-build / reset entry points take a {@link PieceRuntimes}
 * parameter to resolve the per-controller state for each piece. Callers that
 * don't have a runtime in hand (e.g. read-only queries) can pass a transient
 * "scratch" runtime built from a temporary piece list, but the normal
 * controller-owned runtime is the source of truth.
 *
 * <p>Usage example:
 * <pre>{@code
 * MultiPiecePattern pattern = MultiPiecePattern.builder()
 *     .piece("core", coreTemplate, Vec3i.ZERO)
 *     .piece("ring1", ring1Template, new Vec3i(0, 0, -59))
 *     .conditionalPiece("ring2", ring2Template, new Vec3i(0, 0, -67), () -> isUpgradeActive())
 *     .build();
 *
 * PieceRuntimes runtimes = new PieceRuntimes(pattern);
 * pattern.checkActiveGraph(world, controllerPos, orientation, runtimes);
 * }</pre>
 *
 * @see StructurePiece for individual piece definition
 * @see PieceRuntimes for the per-controller state holder
 */
public class MultiPiecePattern {

    private final Map<String, StructurePiece> pieces;
    private final List<StructurePiece> pieceList;
    private final List<StructurePiece> toolingPieceList;
    private final Map<String, Integer> toolingPieceIndices;
    private final Map<MultiblockAbility<?>, int[]> abilityLimits;
    private final List<AbilityGroupLimit> abilityGroupLimits;

    private MultiPiecePattern(Map<String, StructurePiece> pieces) {
        this.pieces = Collections.unmodifiableMap(pieces);
        this.pieceList = Collections.unmodifiableList(new ArrayList<>(pieces.values()));
        this.toolingPieceList = Collections.unmodifiableList(computeToolingPieceList(this.pieceList));
        this.toolingPieceIndices = Collections.unmodifiableMap(computeToolingPieceIndices(this.pieceList));
        this.abilityLimits = Collections.emptyMap();
        this.abilityGroupLimits = Collections.emptyList();
    }

    /**
     * Create a MultiPiecePattern from a list of pre-built pieces.
     * Used by StructureCompiler to assemble compiled pieces.
     *
     * @param pieceList the list of pieces (must not be empty, names must be unique)
     * @throws IllegalArgumentException if duplicate piece names are found
     */
    public MultiPiecePattern(@NotNull List<StructurePiece> pieceList) {
        this(pieceList, Collections.emptyMap());
    }

    public MultiPiecePattern(@NotNull List<StructurePiece> pieceList,
                             @NotNull Map<MultiblockAbility<?>, int[]> abilityLimits) {
        this(pieceList, abilityLimits, Collections.emptyList());
    }

    public MultiPiecePattern(@NotNull List<StructurePiece> pieceList,
                             @NotNull Map<MultiblockAbility<?>, int[]> abilityLimits,
                             @NotNull List<AbilityGroupLimit> abilityGroupLimits) {
        Map<String, StructurePiece> map = new LinkedHashMap<>();
        for (StructurePiece piece : pieceList) {
            if (map.containsKey(piece.getName())) {
                throw new IllegalArgumentException("Duplicate piece name: " + piece.getName());
            }
            map.put(piece.getName(), piece);
        }
        this.pieces = Collections.unmodifiableMap(map);
        this.pieceList = Collections.unmodifiableList(new ArrayList<>(pieceList));
        this.toolingPieceList = Collections.unmodifiableList(computeToolingPieceList(this.pieceList));
        this.toolingPieceIndices = Collections.unmodifiableMap(computeToolingPieceIndices(this.pieceList));
        Map<MultiblockAbility<?>, int[]> copiedLimits = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : abilityLimits.entrySet()) {
            copiedLimits.put(entry.getKey(), entry.getValue().clone());
        }
        this.abilityLimits = Collections.unmodifiableMap(copiedLimits);
        this.abilityGroupLimits = Collections.unmodifiableList(new ArrayList<>(abilityGroupLimits));
    }

    @NotNull
    public AbilityPlacementTracker createAbilityPlacementTracker() {
        return new AbilityPlacementTracker(abilityLimits, abilityGroupLimits);
    }

    @NotNull
    public StructureMatchSession createMatchSession() {
        return new StructureMatchSession(abilityLimits, abilityGroupLimits, null);
    }

    @NotNull
    public StructureMatchSession createMatchSession(@Nullable PatternMatchContext initialContext) {
        return new StructureMatchSession(abilityLimits, abilityGroupLimits, initialContext);
    }

    /**
     * @return a defensive copy of the global ability limits for legacy adapters.
     */
    @NotNull
    public Map<MultiblockAbility<?>, int[]> getAbilityLimits() {
        Map<MultiblockAbility<?>, int[]> copy = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : abilityLimits.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    @NotNull
    public List<AbilityGroupLimit> getAbilityGroupLimits() {
        return abilityGroupLimits;
    }

    /**
     * @return an unmodifiable map of piece name -> piece
     */
    public Map<String, StructurePiece> getPieces() {
        return pieces;
    }

    /**
     * @return an unmodifiable list of all pieces in insertion order
     */
    public List<StructurePiece> getPieceList() {
        return pieceList;
    }

    /**
     * @return declaration-ordered pieces visible to user-facing tooling.
     */
    @NotNull
    public List<StructurePiece> getToolingPieceList() {
        return toolingPieceList;
    }

    public int getToolingPieceCount() {
        return toolingPieceList.size();
    }

    /**
     * Convert a 1-based user-facing tooling piece index to the compiled
     * 1-based piece index. Returns {@code -1} when the user index is invalid
     * or maps to no visible piece.
     */
    public int resolveToolingPieceIndex(int toolingPieceIndex) {
        if (toolingPieceIndex < 1 || toolingPieceIndex > toolingPieceList.size()) {
            return -1;
        }
        StructurePiece piece = toolingPieceList.get(toolingPieceIndex - 1);
        Integer compiledIndex = toolingPieceIndices.get(piece.getName());
        return compiledIndex == null ? -1 : compiledIndex;
    }

    /**
     * Resolve a 1-based user-facing tooling piece index to the compiled piece.
     *
     * @return the visible piece, or null when the tooling index is invalid.
     */
    @Nullable
    public StructurePiece getToolingPiece(int toolingPieceIndex) {
        if (toolingPieceIndex < 1 || toolingPieceIndex > toolingPieceList.size()) {
            return null;
        }
        return toolingPieceList.get(toolingPieceIndex - 1);
    }

    /**
     * @param name the piece name
     * @return the piece, or null if not found
     */
    @Nullable
    public StructurePiece getPiece(String name) {
        return pieces.get(name);
    }

    /**
     * Get the combined set of all block positions across all active, validated pieces.
     *
     * @param runtimes per-controller state for each piece; positions are read from
     *                 the per-piece {@link PieceRuntime#getPositions()}
     * @return a new LongSet containing all positions
     */
    public LongSet getAllPositions(@NotNull PieceRuntimes runtimes) {
        return getAllPositions(runtimes, null);
    }

    public LongSet getAllPositions(@NotNull PieceRuntimes runtimes,
                                   @Nullable MultiblockControllerBase controller) {
        return iteratePositions(runtimes, controller).getPositions();
    }

    @NotNull
    public StructureIterateResult iteratePositions(@NotNull PieceRuntimes runtimes,
                                                   @Nullable MultiblockControllerBase controller) {
        LongSet all = new LongOpenHashSet();
        int activePieces = 0;
        int inactivePieces = 0;
        StructureActivationContext<MultiblockControllerBase> activation = activationContext(
                controller, null, null);
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive(activation)) {
                inactivePieces++;
                continue;
            }
            activePieces++;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) continue;
            if (runtime.isValidated()) {
                all.addAll(runtime.getPositions());
            }
        }
        return StructureIterateResult.multi(all, activePieces, inactivePieces);
    }

    /**
     * Check if any piece is dirty and needs re-validation.
     *
     * @param runtimes per-controller state for each piece
     * @return true if at least one active piece is dirty
     */
    public boolean hasDirtyPieces(@NotNull PieceRuntimes runtimes) {
        return hasDirtyPieces(runtimes, null);
    }

    public boolean hasDirtyPieces(@NotNull PieceRuntimes runtimes,
                                  @Nullable MultiblockControllerBase controller) {
        StructureActivationContext<MultiblockControllerBase> activation = activationContext(
                controller, null, null);
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive(activation)) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null && runtime.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list of dirty pieces that need re-checking.
     *
     * @param runtimes per-controller state for each piece
     * @return list of dirty, active pieces
     */
    @NotNull
    public List<StructurePiece> getDirtyPieces(@NotNull PieceRuntimes runtimes) {
        return getDirtyPieces(runtimes, null);
    }

    @NotNull
    public List<StructurePiece> getDirtyPieces(@NotNull PieceRuntimes runtimes,
                                               @Nullable MultiblockControllerBase controller) {
        List<StructurePiece> dirty = new ArrayList<>();
        StructureActivationContext<MultiblockControllerBase> activation = activationContext(
                controller, null, null);
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive(activation)) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null && runtime.isDirty()) {
                dirty.add(piece);
            }
        }
        return dirty;
    }

    /**
     * Re-check the active piece graph after a dirty event.
     *
     * <p>A complete sweep is required because structure-wide predicate counts,
     * shared context values, and dynamic offsets cannot be reconstructed safely
     * from only the dirty piece's previous result.
     *
     * @param world          the world to check against
     * @param controllerPos  the controller's position
     * @param runtimes       per-controller state for each piece
     * @return true if all active pieces are valid
     */
    public boolean checkActiveGraph(World world, BlockPos controllerPos,
                                    @NotNull StructureOrientation orientation,
                                    @NotNull PieceRuntimes runtimes) {
        return checkActiveGraph(world, controllerPos, orientation, runtimes, null);
    }

    public boolean checkActiveGraph(World world, BlockPos controllerPos,
                                    @NotNull StructureOrientation orientation,
                                    @NotNull PieceRuntimes runtimes,
                                    @Nullable MultiblockControllerBase controller) {
        return checkActiveGraphWithResult(
                world, controllerPos, orientation, runtimes, controller).isMatched();
    }

    @NotNull
    public ActiveGraphCheckResult checkActiveGraphWithResult(
            World world, BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull PieceRuntimes runtimes,
            @Nullable MultiblockControllerBase controller) {
        StructureMatchSession session = createMatchSession();
        session.setControllerContext(controller);
        PieceRuntimes.Checkpoint runtimeCheckpoint = runtimes.checkpoint();
        ActiveGraphCheckResult result = session.transactionValue(candidate ->
                checkActiveGraphInSession(
                        world, controllerPos, orientation, runtimes, controller, candidate),
                ActiveGraphCheckResult::isMatched);
        if (!result.isMatched()) {
            runtimes.restoreTo(runtimeCheckpoint);
        }
        return result;
    }

    @NotNull
    private ActiveGraphCheckResult checkActiveGraphInSession(
            World world, BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull PieceRuntimes runtimes,
            @Nullable MultiblockControllerBase controller,
            @NotNull StructureMatchSession session) {
        Map<String, int[]> priorRepeats = new HashMap<>();
        Map<String, BlockPos> priorCenters = new HashMap<>();
        Map<String, Integer> channelValues = new HashMap<>();
        StructureResultTable.Builder resultTable = StructureResultTable.builder(this);
        String lastActivePieceName = null;
        BlockPos lastActivePieceCenter = null;

        for (StructurePiece piece : pieceList) {
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) continue;

            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(priorRepeats), Collections.emptyMap(), new HashMap<>(priorCenters));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(controller, world, controllerPos, prior, session);
            if (!piece.isActive(activation)) {
                resultTable.add(PieceEvaluationResult.inactive(piece));
                continue;
            }
            BlockPos pieceCenter = piece.getCenterPos(controllerPos, orientation, prior);
            session.beginPieceContribution(piece);
            lastActivePieceName = piece.getName();
            lastActivePieceCenter = pieceCenter;

            if (piece instanceof RepeatGroupPiece repeatPiece) {
                boolean ok = repeatPiece.checkSync(world, controllerPos, orientation, prior, runtime, session);
                runtime.setValidated(ok);
            } else {
                StructureCellTraversal traversal = StructureCellTraversal.at(pieceCenter, orientation);
                boolean matched = session.tryFork(pieceSession ->
                        runtime.getState().checkPatternAtExact(
                                world, traversal, pieceSession) != null);
                if (matched) {
                    runtime.setValidated(true);
                    LongSet newPositions = new LongOpenHashSet(runtime.getState().cache.keySet());
                    runtime.swapPositions(newPositions);
                    runtime.setLastAggregatedContext(session.getContext().copy());
                } else {
                    runtime.setValidated(false);
                }
            }
            runtime.clearDirty();

            if (!runtime.isValidated()) {
                session.discardPieceContribution(piece);
                StructureFailureTrace failure = createActiveGraphFailureTrace(
                        controller, controllerPos, orientation, piece.getName(),
                        describeCell(runtime.getState().getError()), activePieceDepth(piece),
                        "Active piece '" + piece.getName() + "' failed pattern check",
                        runtime.getState().getError(), runtime.getState().getMissingAbilities(),
                        session.copyOperationState().getAbilityCounts(),
                        classifyError(runtime.getState().getError(), runtime.getState().getMissingAbilities()));
                return ActiveGraphCheckResult.failure(failure,
                        runtime.getState().getMissingAbilities(),
                        session.copyOperationState().getAbilityCounts(),
                        orientation.isFlipped());
            }

            // Add this piece's repeat counts to the prior metadata so subsequent
            // pieces (which may be DynamicOffsetPieces) can read them.
            if (piece instanceof RepeatGroupPiece) {
                int[] reps = runtime.getLastFormedReps();
                if (reps != null && reps.length > 0) {
                    priorRepeats.put(piece.getName(), reps.clone());
                }
            } else {
                int[] reps = runtime.getState().formedRepetitionCount;
                if (reps != null && reps.length > 0) {
                    priorRepeats.put(piece.getName(), reps.clone());
                    runtime.cacheFormedReps(reps);
                }
            }
            priorCenters.put(piece.getName(), pieceCenter);
            int[] resultRepetitions = piece instanceof RepeatGroupPiece
                    ? runtime.getLastFormedReps()
                    : runtime.getState().formedRepetitionCount;
            StructureContribution contribution = session.finishPieceContribution(piece);
            PatternMatchContext compatibilityContext =
                    contribution.projectCompatibilityContext(session.getContext());
            extractChannelValues(compatibilityContext, channelValues);
            resultTable.add(PieceEvaluationResult.activeMatched(
                    piece, pieceCenter, resultRepetitions,
                    runtime.getPositions(), runtime.getPositions(),
                    runtime.capturePublication(), contribution,
                    compatibilityContext));
        }

        StructureResultTable completedTable = resultTable.build();
        StructureAggregateFolder.Result contributionAggregate =
                StructureAggregateFolder.fold(this, completedTable);
        StructureMatchSession.Validation validation = session.validate(true);
        if (!validation.success) {
            StructureFailureTrace.Kind kind = validation.missingAbilities.isEmpty()
                    ? StructureFailureTrace.Kind.COUNT_LIMIT
                    : StructureFailureTrace.Kind.MISSING_ABILITY;
            StructureFailureTrace failure = createActiveGraphValidationFailureTrace(
                    controller, controllerPos, orientation,
                    validation.errorMessage == null
                            ? "Active-graph validation failed"
                            : validation.errorMessage,
                    kind, validation.missingAbilities, validation.abilityCounts,
                    lastActivePieceName, lastActivePieceCenter);
            return ActiveGraphCheckResult.failure(
                    failure, validation.missingAbilities, validation.abilityCounts,
                    orientation.isFlipped(), completedTable, contributionAggregate);
        }

        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                priorRepeats, channelValues, priorCenters);
        return ActiveGraphCheckResult.success(
                metadata, contributionAggregate.copyCompatibilityContext(),
                contributionAggregate.copyOperationState(), orientation.isFlipped(),
                completedTable, contributionAggregate);
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
    private StructureFailureTrace createActiveGraphValidationFailureTrace(
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
                .path("active-graph")
                .operation("CHECK")
                .result(kind.getTraceName())
                .kind(kind)
                .piece(pieceName == null ? "deferred" : pieceName)
                .cell("requirements")
                .errorPosition(errorPos)
                .progressDepth(pieceList.size())
                .expected(kind == StructureFailureTrace.Kind.MISSING_ABILITY
                        ? "required abilities present"
                        : "requirements within declared limits")
                .actual(message)
                .missingAbilities(missingAbilities)
                .abilityCounts(abilityCounts)
                .build();
    }

    @NotNull
    private StructureFailureTrace createActiveGraphFailureTrace(
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
                .path("active-graph")
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
                    .expected("active piece matched")
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

    private int activePieceDepth(@NotNull StructurePiece piece) {
        int index = pieceList.indexOf(piece);
        return index < 0 ? 0 : index + 1;
    }

    public static final class ActiveGraphCheckResult {

        private final boolean matched;
        @Nullable
        private final FormedStructureMetadata metadata;
        @Nullable
        private final PatternMatchContext context;
        @Nullable
        private final StructureOperationState operationState;
        @Nullable
        private final StructureFailureTrace failureTrace;
        @NotNull
        private final Map<MultiblockAbility<?>, Integer> missingAbilities;
        @NotNull
        private final Map<MultiblockAbility<?>, Integer> abilityCounts;
        private final boolean flipped;
        @Nullable
        private final StructureResultTable resultTable;
        @Nullable
        private final StructureAggregateFolder.Result contributionAggregate;

        private ActiveGraphCheckResult(boolean matched,
                                       @Nullable FormedStructureMetadata metadata,
                                       @Nullable PatternMatchContext context,
                                       @Nullable StructureOperationState operationState,
                                       @Nullable StructureFailureTrace failureTrace,
                                       @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                       @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                       boolean flipped,
                                       @Nullable StructureResultTable resultTable,
                                       @Nullable StructureAggregateFolder.Result contributionAggregate) {
            this.matched = matched;
            this.metadata = metadata;
            this.context = context == null ? null : context.copy();
            this.operationState = operationState == null ? null : operationState.copy();
            this.failureTrace = failureTrace;
            this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
            this.abilityCounts = Collections.unmodifiableMap(new LinkedHashMap<>(abilityCounts));
            this.flipped = flipped;
            this.resultTable = resultTable;
            this.contributionAggregate = contributionAggregate;
        }

        @NotNull
        static ActiveGraphCheckResult success(@NotNull FormedStructureMetadata metadata,
                                              @NotNull PatternMatchContext context,
                                              @NotNull StructureOperationState operationState,
                                              boolean flipped,
                                              @NotNull StructureResultTable resultTable,
                                              @NotNull StructureAggregateFolder.Result contributionAggregate) {
            return new ActiveGraphCheckResult(true, metadata, context, operationState, null,
                    Collections.emptyMap(), Collections.emptyMap(), flipped,
                    resultTable, contributionAggregate);
        }

        @NotNull
        static ActiveGraphCheckResult failure(@NotNull StructureFailureTrace failureTrace,
                                              @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                              @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                              boolean flipped) {
            return failure(
                    failureTrace, missingAbilities, abilityCounts, flipped, null, null);
        }

        @NotNull
        static ActiveGraphCheckResult failure(
                @NotNull StructureFailureTrace failureTrace,
                @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                boolean flipped,
                @Nullable StructureResultTable resultTable,
                @Nullable StructureAggregateFolder.Result contributionAggregate) {
            return new ActiveGraphCheckResult(false, null, null, null, failureTrace,
                    missingAbilities, abilityCounts, flipped, resultTable, contributionAggregate);
        }

        public boolean isMatched() {
            return matched;
        }

        @Nullable
        public FormedStructureMetadata getMetadata() {
            return metadata;
        }

        @Nullable
        public PatternMatchContext copyContext() {
            return context == null ? null : context.copy();
        }

        @Nullable
        public StructureOperationState copyOperationState() {
            return operationState == null ? null : operationState.copy();
        }

        @Nullable
        public StructureFailureTrace getFailureTrace() {
            return failureTrace;
        }

        @NotNull
        public Map<MultiblockAbility<?>, Integer> getMissingAbilities() {
            return missingAbilities;
        }

        @NotNull
        public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
            return abilityCounts;
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
    }

    /**
     * @deprecated The operation checks the complete active graph, not only dirty pieces.
     */
    @Deprecated
    public boolean checkDirtyPieces(World world, BlockPos controllerPos,
                                    @NotNull StructureOrientation orientation,
                                    @NotNull PieceRuntimes runtimes) {
        return checkActiveGraph(world, controllerPos, orientation, runtimes);
    }

    /**
     * @deprecated The operation checks the complete active graph, not only dirty pieces.
     */
    @Deprecated
    public boolean checkDirtyPieces(World world, BlockPos controllerPos,
                                    @NotNull StructureOrientation orientation,
                                    @NotNull PieceRuntimes runtimes,
                                    @Nullable MultiblockControllerBase controller) {
        return checkActiveGraph(world, controllerPos, orientation, runtimes, controller);
    }

    /**
     * @deprecated The operation checks the complete active graph, not only dirty pieces.
     */
    @Deprecated
    @NotNull
    public ActiveGraphCheckResult checkDirtyPiecesWithResult(
            World world, BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull PieceRuntimes runtimes,
            @Nullable MultiblockControllerBase controller) {
        return checkActiveGraphWithResult(
                world, controllerPos, orientation, runtimes, controller);
    }

    /**
     * Perform a full check of all active pieces (ignoring dirty flags).
     *
     * @param world          the world to check against
     * @param controllerPos  the controller's position
     * @param runtimes       per-controller state for each piece
     * @return true if all active pieces are valid
     */
    public boolean checkAllPieces(World world, BlockPos controllerPos,
                                  @NotNull StructureOrientation orientation,
                                  @NotNull PieceRuntimes runtimes) {
        return checkAllPieces(world, controllerPos, orientation, runtimes, null);
    }

    public boolean checkAllPieces(World world, BlockPos controllerPos,
                                  @NotNull StructureOrientation orientation,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable MultiblockControllerBase controller) {
        for (StructurePiece piece : pieceList) {
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null) {
                runtime.markDirty();
            }
        }
        return checkActiveGraph(world, controllerPos, orientation, runtimes, controller);
    }

    /**
     * Mark a specific piece as dirty by position.
     * Called when a block change is detected within the piece's cached positions.
     *
     * @param posLong  the changed block position as a long
     * @param runtimes per-controller state for each piece
     * @return true if a piece was found and marked dirty
     */
    public boolean markDirtyByPosition(long posLong, @NotNull PieceRuntimes runtimes) {
        return markDirtyByPosition(posLong, runtimes, null);
    }

    public boolean markDirtyByPosition(long posLong, @NotNull PieceRuntimes runtimes,
                                       @Nullable MultiblockControllerBase controller) {
        boolean found = false;
        StructureActivationContext<MultiblockControllerBase> activation = activationContext(
                controller, null, null);
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive(activation)) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null && runtime.isValidated() && runtime.getPositions().contains(posLong)) {
                runtime.markDirty();
                found = true;
            }
        }
        return found;
    }

    @NotNull
    private static StructureActivationContext<MultiblockControllerBase> activationContext(
            @Nullable MultiblockControllerBase controller,
            @Nullable FormedStructureMetadata prior,
            @Nullable StructureMatchSession session) {
        return new StructureActivationContext<>(
                controller,
                controller == null ? null : controller.getWorld(),
                controller == null ? null : controller.getPos(),
                prior,
                session);
    }

    @FunctionalInterface
    private interface RepeatPieceOperation<R> {

        @NotNull
        R apply(@NotNull RepeatGroupPiece piece,
                @NotNull PieceRuntime runtime,
                @NotNull FormedStructureMetadata prior);
    }

    @FunctionalInterface
    private interface FixedPieceOperation<R> {

        @NotNull
        R apply(@NotNull StructurePiece piece,
                @NotNull PieceRuntime runtime,
                @NotNull FormedStructureMetadata prior,
                @NotNull StructureCellTraversal traversal);
    }

    @NotNull
    private <R> R dispatchPieceTraversal(@NotNull StructurePiece piece,
                                         @NotNull PieceRuntime runtime,
                                         @NotNull MultiblockControllerBase controller,
                                         @NotNull StructureOrientation orientation,
                                         @NotNull FormedStructureMetadata prior,
                                         @NotNull RepeatPieceOperation<R> repeatOperation,
                                         @NotNull FixedPieceOperation<R> fixedOperation) {
        if (piece instanceof RepeatGroupPiece repeatPiece) {
            return repeatOperation.apply(repeatPiece, runtime, prior);
        }
        BlockPos pieceCenter = piece.getCenterPos(controller.getPos(), orientation, prior);
        return fixedOperation.apply(
                piece, runtime, prior, StructureCellTraversal.at(pieceCenter, orientation));
    }

    /**
     * Auto-build a specific piece by its 1-based index.
     * Computes the piece's center position from the controller and delegates to the
     * piece's runtime state. For {@link RepeatGroupPiece}, the per-piece runtime
     * is forwarded to its multi-axis auto-build path.
     *
     * <p>For pieces anchored to a repeatable body (i.e. {@link DynamicOffsetPiece}),
     * the anchor's repeat count is read from the per-piece runtime's
     * {@link PieceRuntime#getLastFormedReps()} so the dynamic offset can resolve
     * to the correct world position. The body must be built (i.e. its runtime
     * cached the repeat counts) before the anchored piece's auto-build is
     * invoked, otherwise the piece will fall back to its static baseOffset.
     *
     * @param pieceIndex     1-based index into the piece list
     * @param player         the player performing the build
     * @param controller     the multiblock controller
     * @param channelValues  channel values for tier selection
     * @param skipHatches    if true, skip hatch placement
     * @param runtimes       per-controller state for each piece
     * @return true if the piece was successfully built (index valid and piece exists)
     */
    public boolean autoBuildPiece(int pieceIndex, EntityPlayer player, MultiblockControllerBase controller,
                                   @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                   @NotNull PieceRuntimes runtimes) {
        return autoBuildPiece(pieceIndex, player, controller, channelValues, skipHatches,
                runtimes, createAbilityPlacementTracker());
    }

    public boolean autoBuildPiece(int pieceIndex, EntityPlayer player, MultiblockControllerBase controller,
                                  @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                  @NotNull PieceRuntimes runtimes,
                                  @NotNull AbilityPlacementTracker abilityTracker) {
        return autoBuildPiece(pieceIndex, player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches, runtimes, abilityTracker);
    }

    public boolean autoBuildPiece(int pieceIndex, EntityPlayer player, MultiblockControllerBase controller,
                                  @NotNull StructureOrientation orientation,
                                  @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                  @NotNull PieceRuntimes runtimes,
                                  @NotNull AbilityPlacementTracker abilityTracker) {
        return autoBuildPiece(pieceIndex, player, controller, orientation, channelValues, skipHatches,
                runtimes, abilityTracker, StructureEvaluationContext.Operation.CREATIVE_BUILD);
    }

    public boolean autoBuildPiece(int pieceIndex, EntityPlayer player, MultiblockControllerBase controller,
                                  @NotNull StructureOrientation orientation,
                                  @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                  @NotNull PieceRuntimes runtimes,
                                  @NotNull AbilityPlacementTracker abilityTracker,
                                  @NotNull StructureEvaluationContext.Operation operation) {
        return autoBuildPieceWithResult(pieceIndex, player, controller, orientation, channelValues,
                skipHatches, runtimes, abilityTracker, operation, ItemStack.EMPTY).isAttempted();
    }

    @NotNull
    public StructureBuildResult autoBuildPieceWithResult(int pieceIndex,
                                                         EntityPlayer player,
                                                         MultiblockControllerBase controller,
                                                         @NotNull StructureOrientation orientation,
                                                         @Nullable Map<String, Integer> channelValues,
                                                          boolean skipHatches,
                                                          @NotNull PieceRuntimes runtimes,
                                                          @NotNull AbilityPlacementTracker abilityTracker,
                                                          @NotNull StructureEvaluationContext.Operation operation) {
        return autoBuildPieceWithResult(pieceIndex, player, controller, orientation, channelValues,
                skipHatches, runtimes, abilityTracker, operation, ItemStack.EMPTY);
    }

    @NotNull
    public StructureBuildResult autoBuildPieceWithResult(int pieceIndex,
                                                         EntityPlayer player,
                                                         MultiblockControllerBase controller,
                                                         @NotNull StructureOrientation orientation,
                                                         @Nullable Map<String, Integer> channelValues,
                                                         boolean skipHatches,
                                                         @NotNull PieceRuntimes runtimes,
                                                         @NotNull AbilityPlacementTracker abilityTracker,
                                                         @NotNull StructureEvaluationContext.Operation operation,
                                                         @NotNull ItemStack triggerStack) {
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        if (pieceIndex < 1 || pieceIndex > pieceList.size()) {
            return result.recordInvalidPieceRequest().build();
        }

        StructurePiece piece = pieceList.get(pieceIndex - 1);
        if (!piece.isToolingVisible()) {
            return result.recordInactivePiece().build();
        }
        PieceRuntime runtime = runtimes.get(piece);
        if (runtime == null) {
            return result.recordInvalidPieceRequest().build();
        }

        // Build prior metadata from preceding pieces' runtime state. The
        // check path accumulates this incrementally as each piece is checked;
        // the auto-build path is per-piece, so we rebuild it on demand from
        // whatever the previous pieces' runtimes have cached via
        // PieceRuntime.getLastFormedReps().
        FormedStructureMetadata prior = buildPriorMetadata(pieceIndex, runtimes, controller, orientation);
        if (!piece.isActive(activationContext(controller, prior, null))) {
            return result.recordInactivePiece().build();
        }
        result.merge(dispatchPieceTraversal(
                piece, runtime, controller, orientation, prior,
                (repeatPiece, pieceRuntime, piecePrior) ->
                        repeatPiece.autoBuildAtRepeatedWithResult(
                                player, controller, controller.getPos(), orientation, piecePrior,
                                channelValues, skipHatches, pieceRuntime, abilityTracker,
                                operation, triggerStack),
                (fixedPiece, pieceRuntime, piecePrior, traversal) ->
                        pieceRuntime.getState().autoBuildAtWithResult(
                                player, controller, traversal, channelValues, skipHatches,
                                abilityTracker, operation, triggerStack)));
        return result.build();
    }

    public boolean spawnHintsAllPieces(@NotNull World world,
                                       @NotNull MultiblockControllerBase controller,
                                       @NotNull StructureOrientation orientation,
                                       @Nullable Map<String, Integer> channelValues,
                                       @NotNull PieceRuntimes runtimes,
                                       @NotNull ItemStack triggerStack) {
        return spawnHintsAllPiecesWithResult(
                world, controller, orientation, channelValues, runtimes, triggerStack).isAttempted();
    }

    @NotNull
    public StructureHintResult spawnHintsAllPiecesWithResult(
            @NotNull World world,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            @NotNull PieceRuntimes runtimes,
            @NotNull ItemStack triggerStack) {
        StructureHintResult.Builder result = StructureHintResult.builder();

        for (int pieceIndex = 1; pieceIndex <= pieceList.size(); pieceIndex++) {
            StructurePiece piece = pieceList.get(pieceIndex - 1);
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) continue;
            if (!piece.isToolingVisible()) continue;

            FormedStructureMetadata prior = buildPriorMetadata(pieceIndex, runtimes, controller, orientation);
            if (!piece.isActive(activationContext(controller, prior, null))) {
                result.recordInactivePiece();
                continue;
            }

            result.recordActivePiece();
            BlockPos pieceCenter = piece.getCenterPos(controller.getPos(), orientation, prior);
            result.merge(dispatchPieceTraversal(
                    piece, runtime, controller, orientation, prior,
                    (repeatPiece, pieceRuntime, piecePrior) ->
                            repeatPiece.spawnHintsAtRepeatedWithResult(
                                    world, controller, controller.getPos(),
                                    orientation, piecePrior, channelValues, pieceRuntime, triggerStack),
                    (fixedPiece, pieceRuntime, piecePrior, traversal) ->
                            pieceRuntime.getState().spawnHintsAtWithResult(
                                    world, controller, traversal, channelValues, triggerStack)));
        }
        return result.build();
    }

    /**
     * Build a {@link FormedStructureMetadata} snapshot from the per-piece runtimes
     * of all pieces preceding {@code upToIndex} (1-based, exclusive). Repeat
     * counts are read from each runtime's {@code lastFormedReps} field, which is
     * populated by the check path ({@code checkActiveGraph}) and by
     * {@code RepeatGroupPiece.autoBuildAtRepeated}.
     *
     * <p>This is the auto-build equivalent of the incremental
     * {@code priorRepeats} accumulation done in
     * {@link #checkActiveGraph(World, BlockPos, StructureOrientation, PieceRuntimes)}.
     */
    @NotNull
    private FormedStructureMetadata buildPriorMetadata(int upToIndex, @NotNull PieceRuntimes runtimes,
                                                       @NotNull MultiblockControllerBase controller) {
        return buildPriorMetadata(upToIndex, runtimes, controller, StructureOrientation.fromController(controller));
    }

    @NotNull
    private FormedStructureMetadata buildPriorMetadata(int upToIndex, @NotNull PieceRuntimes runtimes,
                                                       @NotNull MultiblockControllerBase controller,
                                                       @NotNull StructureOrientation orientation) {
        Map<String, int[]> priorRepeats = new HashMap<>();
        Map<String, BlockPos> priorCenters = new HashMap<>();
        for (int i = 0; i < upToIndex - 1; i++) {
            StructurePiece p = pieceList.get(i);
            PieceRuntime r = runtimes.get(p);
            if (r == null) continue;
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(priorRepeats), Collections.emptyMap(), new HashMap<>(priorCenters));
            BlockPos center = p.getCenterPos(controller.getPos(), orientation, prior);
            priorCenters.put(p.getName(), center);
            int[] reps = r.getLastFormedReps();
            if (reps != null && reps.length > 0) {
                priorRepeats.put(p.getName(), reps.clone());
            }
        }
        return FormedStructureMetadata.fromCheckResult(
                priorRepeats, Collections.emptyMap(), priorCenters);
    }

    /**
     * @return the number of pieces in this pattern
     */
    public int getPieceCount() {
        return pieceList.size();
    }

    public int getVisiblePieceCount() {
        return getToolingPieceCount();
    }

    /**
     * Get the primary (first) piece of this pattern.
     * Useful for single-piece patterns where the first piece is the main structure.
     *
     * @return the first piece in the piece list
     * @throws IllegalStateException if the pattern has no pieces
     */
    @NotNull
    public StructurePiece getPrimaryPiece() {
        if (pieceList.isEmpty()) {
            throw new IllegalStateException("MultiPiecePattern has no pieces");
        }
        return pieceList.get(0);
    }

    /**
     * Reset every piece's runtime state via the per-controller {@link PieceRuntimes}.
     * The pattern itself is unaffected — it carries no per-instance state to reset.
     */
    public void resetAll(@NotNull PieceRuntimes runtimes) {
        runtimes.reset();
    }

    /**
     * @return a new builder for constructing a MultiPiecePattern
     */
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    private static List<StructurePiece> computeToolingPieceList(@NotNull List<StructurePiece> pieces) {
        List<StructurePiece> visible = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (piece.isToolingVisible()) {
                visible.add(piece);
            }
        }
        return visible;
    }

    @NotNull
    private static Map<String, Integer> computeToolingPieceIndices(@NotNull List<StructurePiece> pieces) {
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            if (piece.isToolingVisible()) {
                indices.put(piece.getName(), i + 1);
            }
        }
        return indices;
    }

    // --- Builder ---

    public static class Builder {

        private final Map<String, StructurePiece> pieces = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Add an unconditional piece with default RELATIVE offset mode.
         *
         * @param name     unique name for this piece
         * @param template the pattern template
         * @param offset   offset from the controller position
         * @return this builder
         */
        public Builder piece(@NotNull String name, @NotNull BlockPatternTemplate template, @NotNull Vec3i offset) {
            return piece(name, template, offset, OffsetMode.RELATIVE);
        }

        /**
         * Add an unconditional piece with explicit offset mode.
         *
         * @param name       unique name for this piece
         * @param template   the pattern template
         * @param offset     offset from the controller position
         * @param offsetMode how the offset is interpreted relative to controller facing
         * @return this builder
         */
        public Builder piece(@NotNull String name, @NotNull BlockPatternTemplate template,
                             @NotNull Vec3i offset, @NotNull OffsetMode offsetMode) {
            if (pieces.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate piece name: " + name);
            }
            pieces.put(name, new StructurePiece(name, template, offset, offsetMode));
            return this;
        }

        /**
         * Add a conditional piece with default RELATIVE offset mode.
         *
         * @param name      unique name for this piece
         * @param template  the pattern template
         * @param offset    offset from the controller position
         * @param condition condition supplier; piece is only active when this returns true
         * @return this builder
         */
        public Builder conditionalPiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                                        @NotNull Vec3i offset, @NotNull BooleanSupplier condition) {
            return conditionalPiece(name, template, offset, OffsetMode.RELATIVE, condition);
        }

        /**
         * Add a conditional piece with explicit offset mode.
         *
         * @param name       unique name for this piece
         * @param template   the pattern template
         * @param offset     offset from the controller position
         * @param offsetMode how the offset is interpreted relative to controller facing
         * @param condition  condition supplier; piece is only active when this returns true
         * @return this builder
         */
        public Builder conditionalPiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                                        @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                                        @NotNull BooleanSupplier condition) {
            if (pieces.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate piece name: " + name);
            }
            pieces.put(name, new StructurePiece(name, template, offset, offsetMode, condition));
            return this;
        }

        @NotNull
        public <T extends MultiblockControllerBase> Builder conditionalPieceContextual(
                @NotNull String name, @NotNull BlockPatternTemplate template,
                @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                @NotNull StructureCondition<T> condition) {
            if (pieces.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate piece name: " + name);
            }
            pieces.put(name, new StructurePiece(name, template, offset, offsetMode, condition));
            return this;
        }

        /**
         * Build the multi-piece pattern.
         *
         * @return the constructed MultiPiecePattern
         * @throws IllegalStateException if no pieces were added
         */
        public MultiPiecePattern build() {
            if (pieces.isEmpty()) {
                throw new IllegalStateException("MultiPiecePattern must have at least one piece");
            }
            return new MultiPiecePattern(pieces);
        }
    }
}
