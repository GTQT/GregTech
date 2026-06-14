package gregtech.api.pattern;

import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Immutable successful result for one compiled structure piece.
 */
public final class PieceEvaluationResult {

    public enum Status {
        ACTIVE_MATCHED,
        INACTIVE
    }

    @NotNull
    private final StructurePiece piece;
    @NotNull
    private final Status status;
    @Nullable
    private final BlockPos resolvedCenter;
    @NotNull
    private final int[] repetitions;
    @NotNull
    private final LongSet formedPositions;
    @NotNull
    private final LongSet watchedPositions;
    @Nullable
    private final PieceRuntime.Publication matcherPublication;
    @NotNull
    private final StructureContribution contribution;
    private final long semanticFingerprint;

    private PieceEvaluationResult(@NotNull StructurePiece piece,
                                  @NotNull Status status,
                                  @Nullable BlockPos resolvedCenter,
                                  @NotNull int[] repetitions,
                                  @NotNull LongSet formedPositions,
                                  @NotNull LongSet watchedPositions,
                                  @Nullable PieceRuntime.Publication matcherPublication,
                                  @NotNull StructureContribution contribution) {
        this.piece = piece;
        this.status = status;
        this.resolvedCenter = resolvedCenter == null ? null : resolvedCenter.toImmutable();
        this.repetitions = repetitions.clone();
        this.formedPositions = LongSets.unmodifiable(new LongOpenHashSet(formedPositions));
        this.watchedPositions = LongSets.unmodifiable(new LongOpenHashSet(watchedPositions));
        this.matcherPublication = matcherPublication;
        this.contribution = contribution;
        this.semanticFingerprint = computeFingerprint();
    }

    @NotNull
    public static PieceEvaluationResult activeMatched(
            @NotNull StructurePiece piece,
            @NotNull BlockPos resolvedCenter,
            @Nullable int[] repetitions,
            @NotNull LongSet formedPositions,
            @NotNull LongSet watchedPositions,
            @NotNull StructureContribution contribution) {
        return activeMatched(
                piece, resolvedCenter, repetitions, formedPositions, watchedPositions, null, contribution);
    }

    @NotNull
    static PieceEvaluationResult activeMatched(
            @NotNull StructurePiece piece,
            @NotNull BlockPos resolvedCenter,
            @Nullable int[] repetitions,
            @NotNull LongSet formedPositions,
            @NotNull LongSet watchedPositions,
            @Nullable PieceRuntime.Publication matcherPublication,
            @NotNull StructureContribution contribution) {
        return new PieceEvaluationResult(
                piece, Status.ACTIVE_MATCHED, resolvedCenter,
                repetitions == null ? new int[0] : repetitions,
                formedPositions, watchedPositions, matcherPublication, contribution);
    }

    @NotNull
    public static PieceEvaluationResult inactive(@NotNull StructurePiece piece) {
        return new PieceEvaluationResult(
                piece, Status.INACTIVE, null, new int[0],
                LongSets.EMPTY_SET, LongSets.EMPTY_SET, null, StructureContribution.empty());
    }

    @NotNull
    public StructurePiece getPiece() {
        return piece;
    }

    @NotNull
    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE_MATCHED;
    }

    @Nullable
    public BlockPos getResolvedCenter() {
        return resolvedCenter;
    }

    @NotNull
    public int[] getRepetitions() {
        return repetitions.clone();
    }

    @NotNull
    public LongSet getFormedPositions() {
        return formedPositions;
    }

    @NotNull
    public LongSet getWatchedPositions() {
        return watchedPositions;
    }

    @NotNull
    public StructureContribution getContribution() {
        return contribution;
    }

    public long getSemanticFingerprint() {
        return semanticFingerprint;
    }

    private long computeFingerprint() {
        long result = status.hashCode();
        result = 31L * result + (resolvedCenter == null ? 0 : resolvedCenter.hashCode());
        result = 31L * result + Arrays.hashCode(repetitions);
        result = 31L * result + formedPositions.hashCode();
        result = 31L * result + contribution.getCounts().hashCode();
        result = 31L * result + contribution.getTypedEmissions().hashCode();
        return result;
    }
}
