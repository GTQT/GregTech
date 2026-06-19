package gregtech.api.pattern;

import gregtech.api.util.BlockInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed outcome for structure preview assembly.
 */
public final class StructurePreviewResult {

    public enum Outcome {
        GENERATED,
        EMPTY,
        UNSUPPORTED
    }

    public enum Source {
        SINGLE_PIECE,
        MULTI_PIECE
    }

    @NotNull
    private final Outcome outcome;
    @NotNull
    private final Source source;
    @Nullable
    private final PieceRuntimeState.PreviewCells singlePieceCells;
    @Nullable
    private final MultiPiecePreviewAssembler.Result multiPieceResult;
    @NotNull
    private final StructureOperationDiagnostics diagnostics;

    private StructurePreviewResult(@NotNull Outcome outcome,
                                   @NotNull Source source,
                                   @Nullable PieceRuntimeState.PreviewCells singlePieceCells,
                                   @Nullable MultiPiecePreviewAssembler.Result multiPieceResult,
                                   @NotNull StructureOperationDiagnostics diagnostics) {
        this.outcome = outcome;
        this.source = source;
        this.singlePieceCells = singlePieceCells;
        this.multiPieceResult = multiPieceResult;
        this.diagnostics = diagnostics;
    }

    @NotNull
    public static StructurePreviewResult single(@NotNull PieceRuntimeState.PreviewCells cells) {
        return new StructurePreviewResult(cells.isEmpty() ? Outcome.EMPTY : Outcome.GENERATED,
                Source.SINGLE_PIECE, cells, null, StructureOperationDiagnostics.empty());
    }

    @NotNull
    public static StructurePreviewResult multi(@NotNull MultiPiecePreviewAssembler.Result result) {
        return new StructurePreviewResult(result.isEmpty() ? Outcome.EMPTY : Outcome.GENERATED,
                Source.MULTI_PIECE, null, result, StructureOperationDiagnostics.empty());
    }

    @NotNull
    public static StructurePreviewResult unsupported(@NotNull Source source) {
        return new StructurePreviewResult(
                Outcome.UNSUPPORTED, source, null, null, StructureOperationDiagnostics.empty());
    }

    @NotNull
    public Outcome getOutcome() {
        return outcome;
    }

    @NotNull
    public Source getSource() {
        return source;
    }

    public boolean isGenerated() {
        return outcome == Outcome.GENERATED;
    }

    @NotNull
    public StructureOperationDiagnostics getDiagnostics() {
        return diagnostics;
    }

    @NotNull
    public StructurePreviewResult withDiagnostics(@NotNull StructureOperationDiagnostics diagnostics) {
        return new StructurePreviewResult(outcome, source, singlePieceCells, multiPieceResult, diagnostics);
    }

    @Nullable
    public PieceRuntimeState.PreviewCells getSinglePieceCells() {
        return singlePieceCells;
    }

    @Nullable
    public MultiPiecePreviewAssembler.Result getMultiPieceResult() {
        return multiPieceResult;
    }

    @NotNull
    public BlockInfo[][][] toBlockArray() {
        if (singlePieceCells != null) {
            return singlePieceCells.toBlockArray();
        }
        if (multiPieceResult != null) {
            return multiPieceResult.getShape().getBlocks();
        }
        return new BlockInfo[][][]{{{BlockInfo.EMPTY}}};
    }
}
