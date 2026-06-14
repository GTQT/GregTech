package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of a read-only snapshot structure check.
 *
 * <p>Snapshot results are precheck signals only. They never contain a
 * controller runtime publication and must not be submitted to the normal
 * structure committer.
 */
public final class StructureSnapshotResult {

    public enum Outcome {
        MATCHED,
        MISMATCH,
        CAPABILITY_UNSUPPORTED
    }

    @NotNull
    private final Outcome outcome;
    private final boolean flipped;
    @Nullable
    private final String failurePiece;
    private final int progressDepth;

    private StructureSnapshotResult(@NotNull Outcome outcome,
                                    boolean flipped,
                                    @Nullable String failurePiece,
                                    int progressDepth) {
        this.outcome = outcome;
        this.flipped = flipped;
        this.failurePiece = failurePiece;
        this.progressDepth = Math.max(0, progressDepth);
    }

    @NotNull
    public static StructureSnapshotResult matched(boolean flipped, int progressDepth) {
        return new StructureSnapshotResult(Outcome.MATCHED, flipped, null, progressDepth);
    }

    @NotNull
    public static StructureSnapshotResult mismatch(boolean flipped,
                                                   @Nullable String failurePiece,
                                                   int progressDepth) {
        return new StructureSnapshotResult(
                Outcome.MISMATCH, flipped, failurePiece, progressDepth);
    }

    @NotNull
    public static StructureSnapshotResult capabilityUnsupported() {
        return new StructureSnapshotResult(
                Outcome.CAPABILITY_UNSUPPORTED, false, null, 0);
    }

    @NotNull
    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isMatched() {
        return outcome == Outcome.MATCHED;
    }

    public boolean isSupported() {
        return outcome != Outcome.CAPABILITY_UNSUPPORTED;
    }

    public boolean isFlipped() {
        return flipped;
    }

    @Nullable
    public String getFailurePiece() {
        return failurePiece;
    }

    public int getProgressDepth() {
        return progressDepth;
    }

    @NotNull
    public static StructureSnapshotResult selectFailure(
            @NotNull StructureSnapshotResult first,
            @NotNull StructureSnapshotResult second) {
        if (first.isMatched()) return first;
        if (second.isMatched()) return second;
        if (!first.isSupported()) return second;
        if (!second.isSupported()) return first;
        if (second.progressDepth > first.progressDepth) return second;
        return first;
    }
}
