package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StructureOperationContext {

    @Nullable
    private final StructureDefinition<?> definition;
    @NotNull
    private final MultiPiecePattern multiPiecePattern;
    @NotNull
    private final PieceRuntimes pieceRuntimes;

    StructureOperationContext(@Nullable StructureDefinition<?> definition,
                              @NotNull MultiPiecePattern multiPiecePattern,
                              @NotNull PieceRuntimes pieceRuntimes) {
        this.definition = definition;
        this.multiPiecePattern = multiPiecePattern;
        this.pieceRuntimes = pieceRuntimes;
    }

    @Nullable
    StructureDefinition<?> definition() {
        return definition;
    }

    @NotNull
    StructureOperationRuntime runtime() {
        return new StructureOperationRuntime(
                multiPiecePattern, pieceRuntimes,
                definition == null ? "v3-typed-pattern" : "definition",
                "pieces=" + multiPiecePattern.getPieceCount());
    }

    static boolean supportsSnapshotMatch(@NotNull MultiPiecePattern pattern) {
        for (StructurePiece piece : pattern.getPieceList()) {
            if (piece.isConditional()) {
                return false;
            }
            for (IStructureElement<?>[][] layer : piece.getTemplate().getElements()) {
                for (IStructureElement<?>[] row : layer) {
                    for (IStructureElement<?> element : row) {
                        if (element == null
                                || !element.supports(gregtech.api.pattern.element.StructureElementCapability.SNAPSHOT_MATCH)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
