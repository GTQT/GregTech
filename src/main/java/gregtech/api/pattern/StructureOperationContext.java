package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;

import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

final class StructureOperationContext {

    private static final String SYNTHETIC_SINGLE_PIECE_NAME = "main";

    @Nullable
    private final StructureDefinition<?> definition;
    @Nullable
    private final PieceRuntimeState state;
    @Nullable
    private final MultiPiecePattern multiPiecePattern;
    @Nullable
    private final PieceRuntimes pieceRuntimes;
    @Nullable
    private String adapterTrace;
    @Nullable
    private StructureOperationRuntime syntheticSingleRuntime;

    StructureOperationContext(@Nullable StructureDefinition<?> definition,
                              @Nullable PieceRuntimeState state,
                              @Nullable MultiPiecePattern multiPiecePattern,
                              @Nullable PieceRuntimes pieceRuntimes) {
        this.definition = definition;
        this.state = state;
        this.multiPiecePattern = multiPiecePattern;
        this.pieceRuntimes = pieceRuntimes;
    }

    @Nullable
    StructureDefinition<?> definition() {
        return definition;
    }

    void setAdapterTrace(@Nullable String adapterTrace) {
        this.adapterTrace = adapterTrace;
        this.syntheticSingleRuntime = null;
    }

    @NotNull
    StructureOperationRuntime runtime() {
        if (multiPiecePattern != null && pieceRuntimes != null) {
            return new StructureOperationRuntime(
                    multiPiecePattern, pieceRuntimes, false, false,
                    definition == null ? "v3-typed-pattern" : "definition",
                    "pieces=" + multiPiecePattern.getPieceCount(),
                    adapterTrace);
        }
        if (state != null) {
            StructureOperationRuntime runtime = syntheticSingleRuntime;
            if (runtime == null) {
                MultiPiecePattern syntheticPattern = new MultiPiecePattern(Collections.singletonList(
                        new StructurePiece(
                                SYNTHETIC_SINGLE_PIECE_NAME,
                                state.getPieceTemplate(),
                                Vec3i.NULL_VECTOR,
                                OffsetMode.RELATIVE,
                                null,
                                (snap, origin, orientation, prior, pieceRuntime, session) ->
                                        pieceRuntime.getState().checkPatternAtSnapshotExact(
                                                snap, origin, orientation, 0, 0, 0, session) != null)));
                runtime = new StructureOperationRuntime(
                        syntheticPattern,
                        PieceRuntimes.singleWithState(syntheticPattern, state),
                        true, true,
                        "v3-typed-single",
                        "pieces=1, source=single-template-state",
                        adapterTrace);
                syntheticSingleRuntime = runtime;
            }
            return runtime;
        }
        if (multiPiecePattern != null) {
            return new StructureOperationRuntime(
                    multiPiecePattern, new PieceRuntimes(multiPiecePattern), true, false,
                    definition == null ? "v3-typed-pattern" : "definition",
                    "pieces=" + multiPiecePattern.getPieceCount() + ", source=transient",
                    adapterTrace);
        }
        throw new IllegalStateException("Structure operation requested without a compiled pattern");
    }

    static boolean supportsSnapshotMatch(@NotNull MultiPiecePattern pattern) {
        for (StructurePiece piece : pattern.getPieceList()) {
            if (piece.isConditional()) {
                return false;
            }
            for (IStructureElement<?>[][] layer : piece.getPieceTemplate().getElements()) {
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
