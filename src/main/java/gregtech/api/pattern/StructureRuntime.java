package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.casing.StructureChannelValues;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Thin per-controller structure runtime.
 *
 * <p>The runtime owns a thin {@link StructureOperationEvaluator} that routes
 * check, build, preview, and iteration operations to the existing
 * implementations. The evaluator is deliberately delegating for now so the
 * migration does not change structure behavior.
 */
public final class StructureRuntime {

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

    @NotNull
    public StructureOperationEvaluator getEvaluator() {
        return evaluator;
    }

    @NotNull
    public StructureChannelValues getChannelValues() {
        return channelValues;
    }

    public void setChannelValues(@NotNull StructureChannelValues channelValues) {
        this.channelValues = channelValues;
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

    @NotNull
    public Map<String, Integer> getMissingAbilities() {
        return missingAbilities;
    }

    public void setMissingAbilities(@NotNull Map<String, Integer> missingAbilities) {
        this.missingAbilities = missingAbilities;
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
}
