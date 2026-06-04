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
 * repeat-cache fields ({@code lastSuccessRepsA} / {@code lastSuccessPositionsA} /
 * {@code lastSuccessRepsB} / {@code lastSuccessPositionsB}) are only touched on
 * the main thread.
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
     * Double-buffered cache of the two most recent successful (reps, position set)
     * pairs. The {@code active} slot is the one whose set is currently published
     * into the volatile {@link #positions} field; the {@code inactive} slot keeps
     * the previous pair alive so an A->B->A flip-flop can hit the cache on the
     * second A without rebuilding the cartesian-product set.
     *
     * <p>Readers check both slots; the writer always targets the inactive slot
     * and then flips the active flag, so the active slot's previous contents are
     * preserved as the new inactive slot.
     */
    @Nullable
    private int[] lastSuccessRepsA;

    @Nullable
    private LongSet lastSuccessPositionsA;

    @Nullable
    private int[] lastSuccessRepsB;

    @Nullable
    private LongSet lastSuccessPositionsB;

    /**
     * Which slot is currently the writer's target. The writer always publishes
     * to the slot that is NOT active, then flips this flag. {@code true} => A
     * is active (next publish goes to B); {@code false} => B is active.
     * Readers ignore this flag and check both slots.
     */
    private boolean activeIsA = true;

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
     * Try to reuse a cached position set from the last two successful checks.
     * Returns the cached set if {@code reps} exactly matches either slot's
     * reps key; otherwise returns null and the caller must build a new set
     * via {@link #publishPositionSet}.
     *
     * <p>The double-buffered slots ensure an A->B->A flip-flop hits the cache
     * on the second A: the first A writes to slot A, the B writes to slot B
     * (preserving slot A), and the second A reads back from slot A.
     */
    @Nullable
    public LongSet tryReusePositionSet(@NotNull int[] reps) {
        int[] a = lastSuccessRepsA;
        int[] b = lastSuccessRepsB;
        if (a != null && a.length == reps.length && Arrays.equals(a, reps)) {
            return lastSuccessPositionsA;
        }
        if (b != null && b.length == reps.length && Arrays.equals(b, reps)) {
            return lastSuccessPositionsB;
        }
        return null;
    }

    /**
     * Publish a freshly built position set as the new last-success snapshot.
     * Writes to the inactive slot (so the previously-active slot's pair is
     * preserved as the new inactive entry), then flips the active flag and
     * atomically publishes the set into the volatile {@link #positions} field
     * for readers.
     */
    public void publishPositionSet(@NotNull LongSet set, @NotNull int[] reps) {
        int[] cloned = reps.clone();
        if (activeIsA) {
            // A is currently active; write to B and promote B to active.
            this.lastSuccessRepsB = cloned;
            this.lastSuccessPositionsB = set;
            this.activeIsA = false;
        } else {
            // B is currently active; write to A and promote A to active.
            this.lastSuccessRepsA = cloned;
            this.lastSuccessPositionsA = set;
            this.activeIsA = true;
        }
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
        this.lastSuccessRepsA = null;
        this.lastSuccessPositionsA = null;
        this.lastSuccessRepsB = null;
        this.lastSuccessPositionsB = null;
        this.activeIsA = true;
        this.lastFormedReps = null;
    }
}
