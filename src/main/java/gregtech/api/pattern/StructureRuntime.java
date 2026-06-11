package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.casing.StructureChannelValues;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thin per-controller structure runtime.
 *
 * <p>V3 will move check/build/preview orchestration into this class. For now it
 * is deliberately a small state holder over the existing template, state, and
 * multi-piece runtime fields so the migration can start without changing
 * structure behavior.
 */
public final class StructureRuntime {

    @Nullable
    private final StructureDefinition definition;
    @Nullable
    private final BlockPatternTemplate template;
    @Nullable
    private final MultiblockState state;
    @Nullable
    private final MultiPiecePattern multiPiecePattern;
    @Nullable
    private final PieceRuntimes pieceRuntimes;

    @NotNull
    private StructureChannelValues channelValues = new StructureChannelValues();
    @Nullable
    private FormedStructureMetadata formedMetadata;
    @Nullable
    private StructureTrace.Failure lastFailure;

    public StructureRuntime(@Nullable StructureDefinition definition,
                            @Nullable BlockPatternTemplate template,
                            @Nullable MultiblockState state,
                            @Nullable MultiPiecePattern multiPiecePattern,
                            @Nullable PieceRuntimes pieceRuntimes) {
        this.definition = definition;
        this.template = template;
        this.state = state;
        this.multiPiecePattern = multiPiecePattern;
        this.pieceRuntimes = pieceRuntimes;
    }

    @Nullable
    public StructureDefinition getDefinition() {
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
    public StructureTrace.Failure getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(@Nullable StructureTrace.Failure lastFailure) {
        this.lastFailure = lastFailure;
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
