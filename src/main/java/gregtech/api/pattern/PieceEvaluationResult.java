package gregtech.api.pattern;

import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable successful result for one compiled structure piece.
 */
public final class PieceEvaluationResult {

    public enum Status {
        ACTIVE_MATCHED,
        OPTIONAL_UNMATCHED,
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
    @NotNull
    private final Map<PieceDependencyAspect, Long> aspectFingerprints;

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
        this.formedPositions = immutableLongSet(formedPositions);
        this.watchedPositions = immutableLongSet(watchedPositions);
        this.matcherPublication = matcherPublication;
        this.contribution = contribution;
        this.aspectFingerprints = computeAspectFingerprints();
        this.semanticFingerprint = aspectFingerprints.get(PieceDependencyAspect.ANY_RESULT);
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
                piece, resolvedCenter, repetitions, formedPositions, watchedPositions,
                (PieceRuntime.Publication) null, contribution);
    }

    @NotNull
    public static PieceEvaluationResult activeMatchedWithRuntime(
            @NotNull StructurePiece piece,
            @NotNull BlockPos resolvedCenter,
            @Nullable int[] repetitions,
            @NotNull LongSet formedPositions,
            @NotNull LongSet watchedPositions,
            @NotNull PieceRuntime runtime,
            @NotNull StructureContribution contribution) {
        return activeMatched(
                piece, resolvedCenter, repetitions, formedPositions, watchedPositions,
                runtime.capturePublication(), contribution);
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

    /**
     * Result for an optional fixed piece whose pattern is currently absent.
     * It contributes no formed blocks or abilities, but retains its complete
     * candidate volume as watched positions for event-driven rechecks.
     */
    @NotNull
    public static PieceEvaluationResult optionalUnmatched(
            @NotNull StructurePiece piece,
            @NotNull LongSet watchedPositions) {
        if (!piece.isOptional()) {
            throw new IllegalArgumentException(
                    "Only optional pieces may produce an optional-unmatched result: " + piece.getName());
        }
        return new PieceEvaluationResult(
                piece, Status.OPTIONAL_UNMATCHED, null, new int[0],
                LongSets.EMPTY_SET, watchedPositions, null, StructureContribution.empty());
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

    public boolean isOptionalUnmatched() {
        return status == Status.OPTIONAL_UNMATCHED;
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

    public long getAspectFingerprint(@NotNull PieceDependencyAspect aspect) {
        Long value = aspectFingerprints.get(aspect);
        return value == null ? semanticFingerprint : value;
    }

    private Map<PieceDependencyAspect, Long> computeAspectFingerprints() {
        EnumMap<PieceDependencyAspect, Long> result = new EnumMap<>(PieceDependencyAspect.class);
        long activation = fingerprint(status);
        long center = fingerprint(status, resolvedCenter);
        long repetitionsFingerprint = fingerprint(status, Arrays.hashCode(repetitions));
        long contributionFingerprint = fingerprint(
                status,
                contribution.getRequirements().hashCode(),
                contribution.getCounts().hashCode(),
                contribution.getParts().hashCode(),
                contribution.getAbilityCounts().hashCode(),
                contribution.getAbilityParts().hashCode(),
                contribution.getCountedAbilityParts().hashCode(),
                contribution.getVariantActiveBlocks().hashCode(),
                contribution.getTypedEmissions().hashCode());
        long any = fingerprint(
                activation, center, repetitionsFingerprint,
                formedPositions.hashCode(), watchedPositions.hashCode(), contributionFingerprint);
        result.put(PieceDependencyAspect.ACTIVATION, activation);
        result.put(PieceDependencyAspect.CENTER, center);
        result.put(PieceDependencyAspect.REPETITIONS, repetitionsFingerprint);
        result.put(PieceDependencyAspect.CONTRIBUTION_VALUE, contributionFingerprint);
        result.put(PieceDependencyAspect.CONTROLLER_STATE, contributionFingerprint);
        result.put(PieceDependencyAspect.ANY_RESULT, any);
        return result;
    }

    private static long fingerprint(@Nullable Object... values) {
        long result = 1125899906842597L;
        for (Object value : values) {
            result = 31L * result + (value == null ? 0 : value.hashCode());
        }
        return result;
    }

    @NotNull
    private static LongSet immutableLongSet(@NotNull LongSet source) {
        if (source.isEmpty()) {
            return LongSets.EMPTY_SET;
        }
        return LongSets.unmodifiable(new LongOpenHashSet(source));
    }
}
