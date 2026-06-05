package gregtech.api.pattern;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Per-controller mutable state for a single {@link StructurePiece}.
 *
 * <p>Previously this state lived directly on {@link StructurePiece} as the
 * {@code state} / {@code positions} / {@code validated} / {@code dirty} fields
 * (and on {@link RepeatGroupPiece} as {@code lastFormedReps} /
 * {@code lastAggregatedContext} / {@code lastSuccessReps} / {@code lastSuccessPositions}).
 * That created an ownership bug: {@link MultiPiecePattern} instances are shared
 * across controllers via {@link gregtech.api.pattern.element.StructureDefinition}'s
 * compiled-pattern cache, so per-instance state was silently shared between
 * independent controllers of the same multiblock type.
 *
 * <p>This class breaks that sharing: each controller constructs its own
 * {@link PieceRuntimes} in {@code reinitializeStructurePattern()}, which maps
 * each piece (by identity) to its own {@code PieceRuntime}. Two controllers of
 * the same multiblock type now have independent state.
 *
 * <p>For {@link RepeatGroupPiece} the runtime additionally carries
 * repeat-search cache fields so the per-piece backtracking state lives next to
 * the per-piece {@link MultiblockState} it operates on.
 *
 * <h2>Thread safety</h2>
 * {@code positions} is updated via reference swap (volatile) so the event thread
 * can read a stable snapshot while the main thread builds a successor set. The
 * repeat-cache fields ({@code lastSuccessReps} / {@code lastSuccessPositions})
 * are only touched on the main thread.
 */
public final class PieceRuntime {

    /** Per-piece {@link MultiblockState}, bound at construction. */
    private final MultiblockState state;

    /** The piece this runtime belongs to (back-reference for the snapshot-checker dispatch). */
    @NotNull
    private final StructurePiece piece;

    /**
     * Block positions (as longs) belonging to this piece when formed.
     * Replaced atomically via {@link #swapPositions(LongSet)}.
     */
    private volatile LongSet positions = new LongOpenHashSet();
    private volatile boolean validated = false;
    private volatile boolean dirty = true;

    // --- Repeat group search cache (only meaningful for RepeatGroupPiece) ---

    /** Last successful repeat counts (for prior acceleration on subsequent checks) */
    @Nullable
    private int[] lastFormedReps;

    /** Aggregated PatternMatchContext from the last successful check (for parts aggregation) */
    @Nullable
    private PatternMatchContext lastAggregatedContext;

    /**
     * Cached position set from the last successful check, keyed by the
     * reps that produced it. Enables O(1) position reuse in the steady state
     * (multiblock already formed, same repeat counts across ticks).
     */
    @Nullable
    private int[] lastSuccessReps;

    @Nullable
    private LongSet lastSuccessPositions;

    public PieceRuntime(@NotNull StructurePiece piece) {
        this.piece = piece;
        this.state = piece.getTemplate().createState();
    }

    // --- MultiblockState access ---

    /** @return the per-piece mutable {@link MultiblockState} */
    @NotNull
    public MultiblockState getState() {
        return state;
    }

    /** @return the {@link StructurePiece} this runtime belongs to */
    @NotNull
    public StructurePiece getPiece() {
        return piece;
    }

    // --- Dirty / validated / positions (all pieces) ---

    @NotNull
    public LongSet getPositions() {
        return positions;
    }

    public void swapPositions(@NotNull LongSet newPositions) {
        this.positions = newPositions;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setValidated(boolean validated) {
        this.validated = validated;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // --- Repeat group search cache (RepeatGroupPiece only) ---

    /** Cache the formed repeat counts for this piece. */
    public void cacheFormedReps(@NotNull int[] reps) {
        this.lastFormedReps = reps.clone();
    }

    @Nullable
    public int[] getLastFormedReps() {
        return lastFormedReps;
    }

    /** @return the aggregated PatternMatchContext from the last successful check */
    @Nullable
    public PatternMatchContext getLastAggregatedContext() {
        return lastAggregatedContext;
    }

    public void setLastAggregatedContext(@Nullable PatternMatchContext ctx) {
        this.lastAggregatedContext = ctx;
    }

    /**
     * Try to reuse the cached position set from the last successful check.
     * Returns the cached set if {@code reps} exactly matches the reps that
     * produced it; otherwise returns null and the caller must build a new set
     * via {@link #publishPositionSet}.
     */
    @Nullable
    public LongSet tryReusePositionSet(@NotNull int[] reps) {
        if (lastSuccessReps != null
                && lastSuccessReps.length == reps.length
                && Arrays.equals(lastSuccessReps, reps)) {
            return lastSuccessPositions;
        }
        return null;
    }

    /**
     * Publish a freshly built position set as the new last-success snapshot.
     * Caches the set keyed by {@code reps} for reuse on subsequent checks
     * with the same rep configuration, then atomically swaps it into the
     * volatile {@code positions} field.
     */
    public void publishPositionSet(@NotNull LongSet set, @NotNull int[] reps) {
        this.lastSuccessPositions = set;
        this.lastSuccessReps = reps.clone();
        this.positions = set;
    }

    // --- Reset ---

    /** Reset this runtime to its initial (unformed) state. */
    public void reset() {
        this.validated = false;
        this.dirty = true;
        this.positions = new LongOpenHashSet();
        this.state.clearCache();
        this.lastAggregatedContext = null;
        this.lastSuccessReps = null;
        this.lastSuccessPositions = null;
        this.lastFormedReps = null;
    }
}
