package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.util.BlockInfo;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Per-controller structure runtime.
 *
 * <p>The runtime owns the typed operation evaluator and the mutable lifecycle
 * state published by successful structure checks. Single-template and
 * multi-piece structures differ only in the compiled pattern/runtimes supplied
 * here.
 */
public final class StructureRuntime {

    private static final long FAILURE_TRACE_LOG_INTERVAL_MS = 5000L;

    @Nullable
    private final StructureDefinition<?> definition;
    @Nullable
    private BlockPatternTemplate template;
    @Nullable
    private final PieceRuntimeState state;
    @Nullable
    private final MultiPiecePattern multiPiecePattern;
    @Nullable
    private final PieceRuntimes pieceRuntimes;
    @NotNull
    private final StructureOperationEvaluator evaluator;

    @NotNull
    private StructureChannelValues channelValues = new StructureChannelValues();
    @Nullable
    private FormedStructureMetadata formedMetadata;
    @Nullable
    private StructureFailureTrace lastFailure;
    private long lastFailureLogTime = -1;
    @Nullable
    private String lastFailureLogSummary;
    @NotNull
    private Map<String, Integer> missingAbilities = Collections.emptyMap();
    @NotNull
    private final StructureDirtyState dirtyState = new StructureDirtyState();
    @NotNull
    private volatile StructureLifecycleState lifecycleState = StructureLifecycleState.empty();
    private volatile long lifecycleGeneration;
    @Nullable
    private volatile CommittedStructureGraph committedGraph;
    @Nullable
    private String adapterTrace;

    public StructureRuntime(@Nullable StructureDefinition<?> definition,
                            @Nullable BlockPatternTemplate template,
                            @Nullable PieceRuntimeState state,
                            @Nullable MultiPiecePattern multiPiecePattern,
                            @Nullable PieceRuntimes pieceRuntimes) {
        this.definition = definition;
        this.template = template;
        this.state = state;
        this.multiPiecePattern = multiPiecePattern;
        this.pieceRuntimes = pieceRuntimes;
        this.evaluator = new StructureOperationEvaluator(
                definition, state, multiPiecePattern, pieceRuntimes);
    }

    @NotNull
    public static StructureRuntime fromDefinition(@NotNull StructureDefinition<?> definition) {
        MultiPiecePattern multiPiecePattern = definition.getCompiledPattern();
        StructurePiece primaryPiece = definition.supportsSingleTemplatePath()
                ? multiPiecePattern.getPrimaryPiece()
                : null;
        PieceRuntimeState state = primaryPiece == null ? null : new PieceRuntimeState(primaryPiece.getPieceTemplate());
        return new StructureRuntime(definition, null, state, multiPiecePattern,
                state == null
                        ? new PieceRuntimes(multiPiecePattern)
                        : PieceRuntimes.singleWithState(multiPiecePattern, state));
    }

    @Nullable
    public StructureDefinition<?> getDefinition() {
        return definition;
    }

    @Nullable
    public BlockPatternTemplate getTemplate() {
        if (template == null && state != null) {
            template = state.getTemplate();
        }
        return template;
    }

    @Nullable
    public PieceRuntimeState getRuntimeState() {
        return state;
    }

    /**
     * @deprecated Compatibility accessor for legacy code that received a
     *             {@link MultiblockState}. Runtime internals should use
     *             {@link #getRuntimeState()}.
     */
    @Deprecated
    @Nullable
    public MultiblockState getState() {
        return state == null ? null : state.createCompatibilityProjection();
    }

    @Nullable
    public MultiPiecePattern getMultiPiecePattern() {
        return multiPiecePattern;
    }

    @Nullable
    public PieceRuntimes getPieceRuntimes() {
        return pieceRuntimes;
    }

    /**
     * @deprecated Runtime-owned operations should call the request methods on
     *             this runtime. This accessor remains for legacy adapters while
     *             the evaluator is still a separate delegating implementation.
     */
    @Deprecated
    @NotNull
    public StructureOperationEvaluator getEvaluator() {
        return evaluator;
    }

    @NotNull
    public StructureCheckResult check(@NotNull StructureOperationRequest request) {
        return evaluator.check(request);
    }

    @NotNull
    public StructureSnapshotResult checkSnapshot(@NotNull StructureOperationRequest request) {
        return evaluator.checkSnapshot(request);
    }

    @NotNull
    public StructureCheckResult checkActiveGraph(@NotNull StructureOperationRequest request) {
        return evaluator.checkActiveGraph(request);
    }

    @NotNull
    public StructureCheckResult checkIncremental(@NotNull StructureOperationRequest request) {
        return checkIncremental(request, null);
    }

    @NotNull
    public StructureCheckResult checkIncremental(
            @NotNull StructureOperationRequest request,
            @Nullable StructureDirtyPrecheck.Result detachedPrecheck) {
        StructureOrientation orientation = request.requireOrientation();
        IncrementalFallback fallback = getIncrementalFallback(orientation, request.getController());
        if (fallback.getReason() != null) {
            return fallbackFromIncremental(request, fallback.getReason(), fallback.getDetail());
        }
        Set<String> dirtyRoots = consumeDirtyRoots(request.getController());
        if (dirtyRoots.isEmpty()) {
            return fallbackFromIncremental(request, StructureIncrementalFallbackReason.NO_BASELINE);
        }
        return evaluator.checkIncremental(
                request, getCommittedGraph(), dirtyRoots, definition.getEligibilityPlan(),
                detachedPrecheck);
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
    public StructureBuildResult buildSingle(@NotNull StructureOperationRequest request) {
        return evaluator.buildSingle(request);
    }

    @NotNull
    public StructureBuildResult buildPiece(@NotNull StructureOperationRequest request) {
        return evaluator.buildPiece(request);
    }

    @NotNull
    public StructureBuildResult buildAllPieces(@NotNull StructureOperationRequest request) {
        return evaluator.buildAllPieces(request);
    }

    @NotNull
    public StructureBuildResult creativeBuildSingle(@NotNull StructureOperationRequest request) {
        return evaluator.creativeBuildSingle(request);
    }

    @NotNull
    public StructureBuildResult creativeBuildPiece(@NotNull StructureOperationRequest request) {
        return evaluator.creativeBuildPiece(request);
    }

    @NotNull
    public StructureBuildResult creativeBuildAllPieces(@NotNull StructureOperationRequest request) {
        return evaluator.creativeBuildAllPieces(request);
    }

    @NotNull
    public StructureBuildResult survivalBuildSingle(@NotNull StructureOperationRequest request) {
        return evaluator.survivalBuildSingle(request);
    }

    @NotNull
    public StructureBuildResult survivalBuildPiece(@NotNull StructureOperationRequest request) {
        return evaluator.survivalBuildPiece(request);
    }

    @NotNull
    public StructureBuildResult survivalBuildAllPieces(@NotNull StructureOperationRequest request) {
        return evaluator.survivalBuildAllPieces(request);
    }

    public void spawnHintsSingle(@NotNull StructureOperationRequest request) {
        evaluator.spawnHintsSingle(request);
    }

    @NotNull
    public StructureHintResult hintSingle(@NotNull StructureOperationRequest request) {
        return evaluator.hintSingle(request);
    }

    public boolean spawnHintsAllPieces(@NotNull StructureOperationRequest request) {
        return evaluator.spawnHintsAllPieces(request);
    }

    @NotNull
    public StructureHintResult hintAllPieces(@NotNull StructureOperationRequest request) {
        return evaluator.hintAllPieces(request);
    }

    @NotNull
    public BlockInfo[][][] previewSingle(@NotNull StructureOperationRequest request) {
        return evaluator.previewSingle(request);
    }

    @NotNull
    public StructurePreviewResult previewSingleResult(@NotNull StructureOperationRequest request) {
        return evaluator.previewSingleResult(request);
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(@NotNull StructureOperationRequest request) {
        return evaluator.previewMultiPiece(request);
    }

    @NotNull
    public StructurePreviewResult previewMultiPieceResult(@NotNull StructureOperationRequest request) {
        return evaluator.previewMultiPieceResult(request);
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(@NotNull StructureOperationRequest request) {
        return evaluator.iterateSingle(request);
    }

    @NotNull
    public StructureIterateResult iterateSingleResult(@NotNull StructureOperationRequest request) {
        return evaluator.iterateSingleResult(request);
    }

    @NotNull
    public StructureIterateResult iterateMultiPiece(@NotNull StructureOperationRequest request) {
        return evaluator.iterateMultiPiece(request);
    }

    @NotNull
    public StructureChannelValues getChannelValues() {
        return lifecycleState.getChannelValues();
    }

    public void setChannelValues(@NotNull StructureChannelValues channelValues) {
        this.channelValues = copyChannelValues(channelValues);
        this.lifecycleState = this.lifecycleState.withChannelValues(channelValues);
        this.lifecycleGeneration++;
    }

    @Nullable
    public FormedStructureMetadata getFormedMetadata() {
        return lifecycleState.getFormedMetadata();
    }

    public void setFormedMetadata(@Nullable FormedStructureMetadata formedMetadata) {
        this.formedMetadata = formedMetadata;
        this.lifecycleState = this.lifecycleState.withFormedMetadata(formedMetadata);
        this.lifecycleGeneration++;
    }

    @Nullable
    public StructureFailureTrace getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(@Nullable StructureFailureTrace lastFailure) {
        this.lastFailure = lastFailure;
    }

    public void recordLifecycleFailure(@NotNull StructureFailureTrace failure) {
        recordSelectedFailure(failure);
    }

    public void recordAdapterTrace(@NotNull String source, int pieces) {
        this.adapterTrace = "source=" + source + ", pieces=" + Math.max(0, pieces);
        this.evaluator.setAdapterTrace(this.adapterTrace);
    }

    @Nullable
    public String getAdapterTrace() {
        return adapterTrace;
    }

    @NotNull
    public Map<String, Integer> getMissingAbilities() {
        return missingAbilities;
    }

    public void setMissingAbilities(@NotNull Map<String, Integer> missingAbilities) {
        this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
    }

    @Nullable
    public CommittedStructureGraph getCommittedGraph() {
        return lifecycleState.getCommittedGraph();
    }

    public void publishCommittedGraph(@Nullable CommittedStructureGraph graph) {
        this.committedGraph = graph;
        this.lifecycleState = this.lifecycleState.withCommittedGraph(graph);
        this.lifecycleGeneration++;
        dirtyState.clear();
    }

    public void clearCommittedGraph() {
        this.committedGraph = null;
        this.lifecycleState = this.lifecycleState.withCommittedGraph(null);
        this.lifecycleGeneration++;
        dirtyState.clear();
    }

    public boolean addDirtyRoots(@NotNull Iterable<String> pieceNames) {
        return dirtyState.addRoots(pieceNames);
    }

    public boolean addDirtyRoot(@NotNull String pieceName) {
        return dirtyState.addRoot(pieceName);
    }

    public boolean markDirtyByPosition(long position) {
        CommittedStructureGraph graph = getCommittedGraph();
        if (graph == null) {
            return false;
        }
        return dirtyState.addRoots(graph.getPositionIndex().getOwners(position));
    }

    public boolean hasPendingDirtyRoots(@Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        return !snapshotDirtyRoots(controller).isEmpty();
    }

    /**
     * Build a detached async precheck plan without consuming pending roots.
     *
     * <p>The plan is only a scheduling hint. The server-thread live confirm
     * consumes the current root set again before publishing any state.
     */
    @Nullable
    public StructureDirtyPrecheck createDirtyPrecheck(
            @Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        CommittedStructureGraph graph = getCommittedGraph();
        if (definition == null || definition.hasRuntimeDetector()
                || graph == null || !definition.getEligibilityPlan().isEligible()) {
            return null;
        }
        Set<String> roots = snapshotDirtyRoots(controller);
        if (roots.isEmpty()) {
            return null;
        }
        return StructureDirtyPrecheck.create(graph, roots);
    }

    @NotNull
    public Set<String> rootsForChangedExternalDependencies(
            @Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        CommittedStructureGraph graph = getCommittedGraph();
        if (definition == null || graph == null) {
            return Collections.emptySet();
        }
        StructureEligibilityPlan plan = definition.getEligibilityPlan();
        if (!plan.isEligible()) {
            return Collections.emptySet();
        }
        StructureExternalDependencySnapshot current = plan.snapshotExternalDependencies(controller);
        StructureExternalDependencySnapshot.ChangeSet changes =
                current.changesFrom(graph.getExternalDependencySnapshot());
        if (hasExternalDependencyFailures(graph.getExternalDependencySnapshot(), current, changes)) {
            return allPieceRoots(plan);
        }
        return plan.rootsForExternalDependencyChanges(changes.getChangedKeys());
    }

    @Nullable
    public StructureIncrementalFallbackReason getIncrementalFallbackReason(
            @NotNull StructureOrientation orientation) {
        return getIncrementalFallbackWithoutExternalState(orientation).getReason();
    }

    @Nullable
    public StructureIncrementalFallbackReason getIncrementalFallbackReason(
            @NotNull StructureOrientation orientation,
            @Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        return getIncrementalFallback(orientation, controller).getReason();
    }

    @NotNull
    private IncrementalFallback getIncrementalFallback(
            @NotNull StructureOrientation orientation,
            @Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        IncrementalFallback structuralFallback =
                getIncrementalFallbackWithoutExternalState(orientation);
        if (structuralFallback.getReason() != null) {
            return structuralFallback;
        }
        StructureEligibilityPlan plan = definition.getEligibilityPlan();
        CommittedStructureGraph graph = getCommittedGraph();
        StructureExternalDependencySnapshot current =
                plan.snapshotExternalDependencies(controller);
        StructureExternalDependencySnapshot baseline =
                graph.getExternalDependencySnapshot();
        StructureExternalDependencySnapshot.ChangeSet changes =
                current.changesFrom(baseline);
        if (hasExternalDependencyFailures(baseline, current, changes)) {
            return IncrementalFallback.of(
                    StructureIncrementalFallbackReason.UNKNOWN_EXTERNAL_DEPENDENCY,
                    describeExternalDependencyFailures(baseline, current, changes));
        }
        return IncrementalFallback.none();
    }

    @NotNull
    private IncrementalFallback getIncrementalFallbackWithoutExternalState(
            @NotNull StructureOrientation orientation) {
        if (definition == null) {
            return IncrementalFallback.of(
                    StructureIncrementalFallbackReason.DEFINITION_NOT_ELIGIBLE, null);
        }
        if (definition.hasRuntimeDetector()) {
            return IncrementalFallback.of(
                    StructureIncrementalFallbackReason.DEFINITION_NOT_ELIGIBLE,
                    "runtime detector requires a live full-box validation");
        }
        StructureEligibilityPlan plan = definition.getEligibilityPlan();
        if (!plan.isEligible()) {
            StructureIncrementalFallbackReason reason = plan.getFallbackReason() == null
                    ? StructureIncrementalFallbackReason.DEFINITION_NOT_ELIGIBLE
                    : plan.getFallbackReason();
            return IncrementalFallback.of(reason, null);
        }
        CommittedStructureGraph graph = getCommittedGraph();
        if (graph == null) {
            return IncrementalFallback.of(StructureIncrementalFallbackReason.NO_BASELINE, null);
        }
        if (!graph.getOrientation().equals(orientation)) {
            return IncrementalFallback.of(
                    StructureIncrementalFallbackReason.ORIENTATION_CHANGED, null);
        }
        return IncrementalFallback.none();
    }

    /**
     * Publish state from a successful check after controller-side assembly
     * validation has completed.
     */
    public void commitSuccessfulCheck(@Nullable FormedStructureMetadata formedMetadata,
                                      @NotNull StructureChannelValues channelValues) {
        this.formedMetadata = formedMetadata;
        this.channelValues = copyChannelValues(channelValues);
        this.lifecycleState = this.lifecycleState
                .withFormedMetadata(formedMetadata)
                .withChannelValues(channelValues);
        this.lifecycleGeneration++;
        this.missingAbilities = Collections.emptyMap();
        this.lastFailure = null;
    }

    /**
     * Publish the canonical formed lifecycle snapshot. Controller fields are a
     * legacy projection of this state and must be updated by the server-thread
     * committer in the same commit section.
     */
    public void publishLifecycleState(
            @NotNull List<IMultiblockPart> parts,
            @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
            @Nullable FormedStructureMetadata formedMetadata,
            @NotNull StructureChannelValues channelValues,
            @Nullable CommittedStructureGraph graph) {
        this.formedMetadata = formedMetadata;
        this.channelValues = copyChannelValues(channelValues);
        this.committedGraph = graph;
        this.lifecycleState = StructureLifecycleState.formed(
                parts, abilities, formedMetadata, channelValues, graph);
        this.lifecycleGeneration++;
        this.missingAbilities = Collections.emptyMap();
        this.lastFailure = null;
        dirtyState.clear();
    }

    @NotNull
    public StructureLifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public long getLifecycleGeneration() {
        return lifecycleGeneration;
    }

    /**
     * Store a failed check and its missing ability summary without changing the
     * last successfully committed formed state.
     */
    public void recordCheckFailure(@NotNull StructureFailureTrace failure,
                                   @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        recordFailure(failure);
        if (missingAbilities.isEmpty()) {
            this.missingAbilities = Collections.emptyMap();
            return;
        }

        Map<String, Integer> sorted = new TreeMap<>();
        for (Map.Entry<MultiblockAbility<?>, Integer> entry : missingAbilities.entrySet()) {
            if (entry.getValue() > 0) {
                sorted.put(entry.getKey().toString(), entry.getValue());
            }
        }
        this.missingAbilities = Collections.unmodifiableMap(sorted);
    }

    private void recordFailure(@NotNull StructureFailureTrace failure) {
        this.lastFailure = failure;
        logFailure(failure);
    }

    private void recordSelectedFailure(@NotNull StructureFailureTrace failure) {
        StructureFailureTrace selected = StructureFailureSelection.select(this.lastFailure, failure);
        this.lastFailure = selected;
        if (selected == failure) {
            logFailure(failure);
        }
    }

    private void logFailure(@NotNull StructureFailureTrace failure) {
        String summary = failure.describeForCommand();
        long now = System.currentTimeMillis();
        if (!summary.equals(lastFailureLogSummary)
                || lastFailureLogTime < 0
                || now - lastFailureLogTime >= FAILURE_TRACE_LOG_INTERVAL_MS) {
            StructureTrace.debugLifecycle(failure);
            lastFailureLogSummary = summary;
            lastFailureLogTime = now;
        }
    }

    /**
     * Preserve committed formed state when immutable definitions and
     * per-controller checker objects are rebuilt.
     */
    public void copyFormedStateFrom(@Nullable StructureRuntime previous) {
        if (previous == null) {
            return;
        }
        this.lifecycleState = previous.getLifecycleState().withCommittedGraph(null);
        this.formedMetadata = lifecycleState.getFormedMetadata();
        this.channelValues = lifecycleState.getChannelValues();
        this.committedGraph = null;
        this.lifecycleGeneration++;
    }

    public void clearFormedState() {
        this.channelValues = new StructureChannelValues();
        this.formedMetadata = null;
        this.committedGraph = null;
        this.lifecycleState = StructureLifecycleState.empty();
        this.lifecycleGeneration++;
        dirtyState.clear();
    }

    public String describeShape() {
        String path = definition != null
                ? definition.hasRuntimeDetector() ? "runtime-detector" : "definition"
                : template != null ? "v3-typed-single" : "v3-typed-pattern";
        int pieces = multiPiecePattern == null ? 0 : multiPiecePattern.getPieceList().size();
        boolean singleTemplate = template != null;
        return "path=" + path + ", singleTemplate=" + singleTemplate + ", pieces=" + pieces
                + (adapterTrace == null ? "" : ", adapterTrace={" + adapterTrace + "}");
    }

    @NotNull
    private static StructureChannelValues copyChannelValues(@NotNull StructureChannelValues source) {
        return source.copy();
    }

    @NotNull
    private Set<String> consumeDirtyRoots(
            @Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        LinkedHashSet<String> roots = new LinkedHashSet<>(dirtyState.consume());
        CommittedStructureGraph graph = getCommittedGraph();
        if (definition != null && graph != null) {
            StructureEligibilityPlan plan = definition.getEligibilityPlan();
            if (plan.isEligible()) {
                StructureExternalDependencySnapshot current =
                        plan.snapshotExternalDependencies(controller);
                StructureExternalDependencySnapshot.ChangeSet changes =
                        current.changesFrom(graph.getExternalDependencySnapshot());
                if (hasExternalDependencyFailures(
                        graph.getExternalDependencySnapshot(), current, changes)) {
                    roots.addAll(allPieceRoots(plan));
                } else {
                    roots.addAll(plan.rootsForExternalDependencyChanges(
                            changes.getChangedKeys()));
                }
            }
        }
        return Collections.unmodifiableSet(roots);
    }

    @NotNull
    private Set<String> snapshotDirtyRoots(
            @Nullable gregtech.api.metatileentity.multiblock.MultiblockControllerBase controller) {
        LinkedHashSet<String> roots = new LinkedHashSet<>(dirtyState.snapshot());
        roots.addAll(rootsForChangedExternalDependencies(controller));
        return Collections.unmodifiableSet(roots);
    }

    @NotNull
    private StructureCheckResult fallbackFromIncremental(
            @NotNull StructureOperationRequest request,
            @NotNull StructureIncrementalFallbackReason reason) {
        return fallbackFromIncremental(request, reason, null);
    }

    @NotNull
    private StructureCheckResult fallbackFromIncremental(
            @NotNull StructureOperationRequest request,
            @NotNull StructureIncrementalFallbackReason reason,
            @Nullable String detail) {
        String fallbackDetail = "fallback=" + reason
                + (detail == null || detail.isEmpty() ? "" : ", detail=" + detail);
        if (definition != null) {
            StructureEligibilityPlan plan = definition.getEligibilityPlan();
            if (plan.isEligible()) {
                return check(request)
                        .withEligibilityPlan(plan)
                        .withTraceContext("definition-fallback", fallbackDetail);
            }
            return checkActiveGraph(request)
                    .withEligibilityPlan(plan)
                    .withTraceContext("active-graph-fallback",
                            fallbackDetail + ", " + plan.describeFallback());
        }
        return checkActiveGraph(request)
                .withTraceContext("active-graph-fallback", fallbackDetail);
    }

    @NotNull
    private static Set<String> allPieceRoots(@NotNull StructureEligibilityPlan plan) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(plan.getGraph().getPieceNames()));
    }

    private static boolean hasExternalDependencyFailures(
            @NotNull StructureExternalDependencySnapshot baseline,
            @NotNull StructureExternalDependencySnapshot current,
            @NotNull StructureExternalDependencySnapshot.ChangeSet changes) {
        return baseline.hasFailures() || current.hasFailures() || changes.hasFailures();
    }

    @NotNull
    private static String describeExternalDependencyFailures(
            @NotNull StructureExternalDependencySnapshot baseline,
            @NotNull StructureExternalDependencySnapshot current,
            @NotNull StructureExternalDependencySnapshot.ChangeSet changes) {
        StringBuilder builder = new StringBuilder("external dependency diagnostics");
        if (baseline.hasFailures()) {
            builder.append("; committed=").append(baseline.describeFailures());
        }
        if (current.hasFailures()) {
            builder.append("; current=").append(current.describeFailures());
        }
        if (changes.hasFailures()) {
            builder.append("; comparison=").append(changes.describeFailures());
        }
        return builder.toString();
    }

    private static final class IncrementalFallback {

        @Nullable
        private final StructureIncrementalFallbackReason reason;
        @Nullable
        private final String detail;

        @NotNull
        private static IncrementalFallback none() {
            return new IncrementalFallback(null, null);
        }

        @NotNull
        private static IncrementalFallback of(
                @NotNull StructureIncrementalFallbackReason reason,
                @Nullable String detail) {
            return new IncrementalFallback(reason, detail);
        }

        private IncrementalFallback(
                @Nullable StructureIncrementalFallbackReason reason,
                @Nullable String detail) {
            this.reason = reason;
            this.detail = detail;
        }

        @Nullable
        private StructureIncrementalFallbackReason getReason() {
            return reason;
        }

        @Nullable
        private String getDetail() {
            return detail;
        }
    }
}
