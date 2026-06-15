package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Diagnostics for one incremental evaluator attempt.
 */
public final class StructureIncrementalCheckResult {

    @NotNull
    private final Set<String> dirtyRoots;
    @NotNull
    private final Set<String> dependencyClosure;
    @NotNull
    private final Set<String> prunedPieces;
    private final int recheckedPieces;
    private final int reusedPieces;
    private final int cacheProbeAttempts;
    private final int cacheProbeHits;
    private final int cacheProbeMisses;
    private final boolean snapshotPrecheckAttempted;
    private final boolean snapshotPrecheckFailed;
    private final boolean asynchronousSnapshotPrecheck;
    private final int snapshotPrecheckPositions;

    public StructureIncrementalCheckResult(
            @NotNull Set<String> dirtyRoots,
            @NotNull Set<String> dependencyClosure,
            int recheckedPieces,
            int reusedPieces) {
        this(dirtyRoots, dependencyClosure, Collections.emptySet(),
                recheckedPieces, reusedPieces, 0, 0, 0, false, false, false, 0);
    }

    public StructureIncrementalCheckResult(
            @NotNull Set<String> dirtyRoots,
            @NotNull Set<String> dependencyClosure,
            @NotNull Set<String> prunedPieces,
            int recheckedPieces,
            int reusedPieces,
            boolean snapshotPrecheckAttempted,
            boolean snapshotPrecheckFailed) {
        this(dirtyRoots, dependencyClosure, prunedPieces, recheckedPieces, reusedPieces,
                0, 0, 0, snapshotPrecheckAttempted, snapshotPrecheckFailed, false, 0);
    }

    public StructureIncrementalCheckResult(
            @NotNull Set<String> dirtyRoots,
            @NotNull Set<String> dependencyClosure,
            @NotNull Set<String> prunedPieces,
            int recheckedPieces,
            int reusedPieces,
            int cacheProbeAttempts,
            int cacheProbeHits,
            int cacheProbeMisses,
            boolean snapshotPrecheckAttempted,
            boolean snapshotPrecheckFailed,
            boolean asynchronousSnapshotPrecheck,
            int snapshotPrecheckPositions) {
        this.dirtyRoots = Collections.unmodifiableSet(new LinkedHashSet<>(dirtyRoots));
        this.dependencyClosure = Collections.unmodifiableSet(new LinkedHashSet<>(dependencyClosure));
        this.prunedPieces = Collections.unmodifiableSet(new LinkedHashSet<>(prunedPieces));
        this.recheckedPieces = recheckedPieces;
        this.reusedPieces = reusedPieces;
        this.cacheProbeAttempts = Math.max(0, cacheProbeAttempts);
        this.cacheProbeHits = Math.max(0, cacheProbeHits);
        this.cacheProbeMisses = Math.max(0, cacheProbeMisses);
        this.snapshotPrecheckAttempted = snapshotPrecheckAttempted;
        this.snapshotPrecheckFailed = snapshotPrecheckFailed;
        this.asynchronousSnapshotPrecheck = asynchronousSnapshotPrecheck;
        this.snapshotPrecheckPositions = Math.max(0, snapshotPrecheckPositions);
    }

    @NotNull
    public Set<String> getDirtyRoots() {
        return dirtyRoots;
    }

    @NotNull
    public Set<String> getDependencyClosure() {
        return dependencyClosure;
    }

    @NotNull
    public Set<String> getPrunedPieces() {
        return prunedPieces;
    }

    public int getRecheckedPieces() {
        return recheckedPieces;
    }

    public int getReusedPieces() {
        return reusedPieces;
    }

    public int getCacheProbeAttempts() {
        return cacheProbeAttempts;
    }

    public int getCacheProbeHits() {
        return cacheProbeHits;
    }

    public int getCacheProbeMisses() {
        return cacheProbeMisses;
    }

    public boolean wasSnapshotPrecheckAttempted() {
        return snapshotPrecheckAttempted;
    }

    public boolean didSnapshotPrecheckFail() {
        return snapshotPrecheckFailed;
    }

    public boolean wasSnapshotPrecheckAsynchronous() {
        return asynchronousSnapshotPrecheck;
    }

    public int getSnapshotPrecheckPositions() {
        return snapshotPrecheckPositions;
    }

    @NotNull
    public String describe() {
        return "roots=" + dirtyRoots.size()
                + ", closure=" + dependencyClosure.size()
                + ", pruned=" + prunedPieces.size()
                + ", rechecked=" + recheckedPieces
                + ", reused=" + reusedPieces
                + ", cacheProbeAttempts=" + cacheProbeAttempts
                + ", cacheProbeHits=" + cacheProbeHits
                + ", cacheProbeMisses=" + cacheProbeMisses
                + ", snapshotPrecheckAttempted=" + snapshotPrecheckAttempted
                + ", snapshotPrecheckFailed=" + snapshotPrecheckFailed
                + ", asynchronousSnapshotPrecheck=" + asynchronousSnapshotPrecheck
                + ", snapshotPrecheckPositions=" + snapshotPrecheckPositions;
    }
}
