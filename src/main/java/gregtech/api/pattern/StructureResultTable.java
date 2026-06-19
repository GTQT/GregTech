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
    private final long semanticFingerprint;

    private StructureResultTable(@NotNull Builder builder) {
        this.results = immutableList(builder.results);
        this.byPiece = new IdentityHashMap<>(builder.byPiece);
        this.byName = immutableMap(builder.byName);
        this.semanticFingerprint = computeFingerprint(this.results);
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

    public long getSemanticFingerprint() {
        return semanticFingerprint;
    }

    private static long computeFingerprint(@NotNull List<PieceEvaluationResult> results) {
        long result = 1125899906842597L;
        for (PieceEvaluationResult pieceResult : results) {
            result = 31L * result + pieceResult.getSemanticFingerprint();
        }
        return result;
    }

    @NotNull
    private static List<PieceEvaluationResult> immutableList(
            @NotNull List<PieceEvaluationResult> source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        if (source.size() == 1) {
            return Collections.singletonList(source.get(0));
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @NotNull
    private static Map<String, PieceEvaluationResult> immutableMap(
            @NotNull Map<String, PieceEvaluationResult> source) {
        if (source.isEmpty()) {
            return Collections.emptyMap();
        }
        if (source.size() == 1) {
            Map.Entry<String, PieceEvaluationResult> entry = source.entrySet().iterator().next();
            return Collections.singletonMap(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
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
