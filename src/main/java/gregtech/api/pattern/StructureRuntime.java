package gregtech.api.pattern;

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
import java.util.Map;
import java.util.TreeMap;

/**
 * Thin per-controller structure runtime.
 *
 * <p>The runtime owns a thin {@link StructureOperationEvaluator} that routes
 * check, build, preview, and iteration operations to the existing
 * implementations. The evaluator is deliberately delegating for now so the
 * migration does not change structure behavior.
 */
public final class StructureRuntime {

    private static final long FAILURE_TRACE_LOG_INTERVAL_MS = 5000L;

    @Nullable
    private final StructureDefinition<?> definition;
    @Nullable
    private final BlockPatternTemplate template;
    @Nullable
    private final MultiblockState state;
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

    public StructureRuntime(@Nullable StructureDefinition<?> definition,
                            @Nullable BlockPatternTemplate template,
                            @Nullable MultiblockState state,
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
        BlockPatternTemplate template = definition.supportsSingleTemplatePath()
                ? multiPiecePattern.getPrimaryPiece().getTemplate()
                : null;
        MultiblockState state = template == null ? null : template.createState();
        return new StructureRuntime(definition, template, state, multiPiecePattern,
                new PieceRuntimes(multiPiecePattern));
    }

    @Nullable
    public StructureDefinition<?> getDefinition() {
        return definition;
    }

    @Nullable
    public BlockPatternTemplate getTemplate() {
        return template;
    }

    @Nullable
    public MultiblockState getState() {
        return state;
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
    public MultiPiecePreviewAssembler.Result previewMultiPiece(@NotNull StructureOperationRequest request) {
        return evaluator.previewMultiPiece(request);
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(@NotNull StructureOperationRequest request) {
        return evaluator.iterateSingle(request);
    }

    @NotNull
    public StructureChannelValues getChannelValues() {
        return channelValues;
    }

    public void setChannelValues(@NotNull StructureChannelValues channelValues) {
        this.channelValues = copyChannelValues(channelValues);
    }

    @Nullable
    public FormedStructureMetadata getFormedMetadata() {
        return formedMetadata;
    }

    public void setFormedMetadata(@Nullable FormedStructureMetadata formedMetadata) {
        this.formedMetadata = formedMetadata;
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

    @NotNull
    public Map<String, Integer> getMissingAbilities() {
        return missingAbilities;
    }

    public void setMissingAbilities(@NotNull Map<String, Integer> missingAbilities) {
        this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
    }

    /**
     * Publish state from a successful check after controller-side assembly
     * validation has completed.
     */
    public void commitSuccessfulCheck(@Nullable FormedStructureMetadata formedMetadata,
                                      @NotNull StructureChannelValues channelValues) {
        this.formedMetadata = formedMetadata;
        this.channelValues = copyChannelValues(channelValues);
        this.missingAbilities = Collections.emptyMap();
        this.lastFailure = null;
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
        this.formedMetadata = previous.formedMetadata;
        this.channelValues = copyChannelValues(previous.channelValues);
    }

    public void clearFormedState() {
        this.channelValues = new StructureChannelValues();
        this.formedMetadata = null;
    }

    public String describeShape() {
        String path = definition != null ? "definition" : "legacy-template";
        int pieces = multiPiecePattern == null ? 0 : multiPiecePattern.getPieceList().size();
        boolean singleTemplate = template != null;
        return "path=" + path + ", singleTemplate=" + singleTemplate + ", pieces=" + pieces;
    }

    @NotNull
    private static StructureChannelValues copyChannelValues(@NotNull StructureChannelValues source) {
        return source.copy();
    }
}
