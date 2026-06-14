package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable declaration-ordered table of successful piece results.
 */
public final class StructureResultTable {

    @NotNull
    private final List<PieceEvaluationResult> results;
    @NotNull
    private final IdentityHashMap<StructurePiece, PieceEvaluationResult> byPiece;
    @NotNull
    private final Map<String, PieceEvaluationResult> byName;

    private StructureResultTable(@NotNull Builder builder) {
        this.results = Collections.unmodifiableList(new ArrayList<>(builder.results));
        this.byPiece = new IdentityHashMap<>(builder.byPiece);
        this.byName = Collections.unmodifiableMap(new LinkedHashMap<>(builder.byName));
    }

    @NotNull
    public static Builder builder(@NotNull MultiPiecePattern pattern) {
        return new Builder(pattern);
    }

    public int size() {
        return results.size();
    }

    @NotNull
    public List<PieceEvaluationResult> getResults() {
        return results;
    }

    @Nullable
    public PieceEvaluationResult get(@NotNull StructurePiece piece) {
        return byPiece.get(piece);
    }

    @Nullable
    public PieceEvaluationResult get(@NotNull String pieceName) {
        return byName.get(pieceName);
    }

    public static final class Builder {

        @NotNull
        private final MultiPiecePattern pattern;
        @NotNull
        private final List<PieceEvaluationResult> results = new ArrayList<>();
        @NotNull
        private final IdentityHashMap<StructurePiece, PieceEvaluationResult> byPiece =
                new IdentityHashMap<>();
        @NotNull
        private final Map<String, PieceEvaluationResult> byName = new LinkedHashMap<>();

        private Builder(@NotNull MultiPiecePattern pattern) {
            this.pattern = pattern;
        }

        @NotNull
        public Builder add(@NotNull PieceEvaluationResult result) {
            int ordinal = results.size();
            if (ordinal >= pattern.getPieceList().size()
                    || pattern.getPieceList().get(ordinal) != result.getPiece()) {
                throw new IllegalArgumentException(
                        "Piece results must be added in compiled declaration order");
            }
            if (byPiece.put(result.getPiece(), result) != null
                    || byName.put(result.getPiece().getName(), result) != null) {
                throw new IllegalArgumentException(
                        "Duplicate piece result: " + result.getPiece().getName());
            }
            results.add(result);
            return this;
        }

        @NotNull
        public StructureResultTable build() {
            if (results.size() != pattern.getPieceList().size()) {
                throw new IllegalStateException(
                        "Result table is incomplete: expected " + pattern.getPieceList().size()
                                + " pieces, got " + results.size());
            }
            return new StructureResultTable(this);
        }
    }
}
