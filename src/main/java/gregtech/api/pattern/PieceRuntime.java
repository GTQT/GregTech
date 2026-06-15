package gregtech.api.pattern;

import gregtech.api.util.BlockInfo;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-controller mutable state for a single {@link StructurePiece}.
 *
 * <p>Previously this state lived directly on {@link StructurePiece} as the
 * {@code state} / {@code positions} / {@code validated} / {@code dirty} fields
 * (and on {@link RepeatGroupPiece} as {@code lastFormedReps} /
 * {@code lastAggregatedContext}).
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
 * <p>For {@link RepeatGroupPiece} the runtime additionally carries repeat
 * metadata so the per-piece backtracking state lives next to the per-piece
 * {@link PieceRuntimeState} it operates on.
 *
 * <h2>Thread safety</h2>
 * {@code positions} is updated via reference swap (volatile) so the event thread
 * can read a stable snapshot while the main thread builds a successor set. The
 * repeat metadata is only touched on the main thread.
 */
public final class PieceRuntime {

    /** Per-piece {@link PieceRuntimeState}, bound at construction. */
    private final PieceRuntimeState state;

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

    public PieceRuntime(@NotNull StructurePiece piece) {
        this(piece, null);
    }

    PieceRuntime(@NotNull StructurePiece piece,
                 @Nullable PieceRuntimeState stateOverride) {
        this.piece = piece;
        this.state = stateOverride == null ? new PieceRuntimeState(piece.getPieceTemplate()) : stateOverride;
    }

    // --- PieceRuntimeState access ---

    /** @return the per-piece mutable {@link PieceRuntimeState} */
    @NotNull
    public PieceRuntimeState getState() {
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

    public boolean probeCachedBlocks(@NotNull World world,
                                     boolean doRandomCheck) {
        return probeCachedBlocks(world, doRandomCheck, 0, 0, 0);
    }

    public boolean probeCachedBlocks(@NotNull World world,
                                     boolean doRandomCheck,
                                     int xOffset, int yOffset, int zOffset) {
        return state.probeCacheAt(world, doRandomCheck, xOffset, yOffset, zOffset);
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

    @NotNull
    Checkpoint checkpoint() {
        return new Checkpoint(this);
    }

    void restoreTo(@NotNull Checkpoint checkpoint) {
        state.restoreTo(checkpoint.stateCheckpoint);
        positions = new LongOpenHashSet(checkpoint.positions);
        validated = checkpoint.validated;
        dirty = checkpoint.dirty;
        lastFormedReps = checkpoint.lastFormedReps == null ? null : checkpoint.lastFormedReps.clone();
        lastAggregatedContext = checkpoint.lastAggregatedContext == null
                ? null
                : checkpoint.lastAggregatedContext.copy();
    }

    @NotNull
    Publication capturePublication() {
        return new Publication(piece, new Checkpoint(this));
    }

    void publish(@NotNull Publication publication) {
        if (publication.piece != piece) {
            throw new IllegalArgumentException("Piece runtime publication belongs to a different piece");
        }
        restoreTo(publication.checkpoint);
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

    public void publishPositionSet(@NotNull LongSet set) {
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
        this.lastFormedReps = null;
    }

    /**
     * Publish a clean inactive piece state into an operation-local candidate.
     */
    void publishInactive() {
        this.validated = true;
        this.dirty = false;
        this.positions = new LongOpenHashSet();
        this.state.clearCache();
        this.state.clearFormedRepetitionCount();
        this.lastAggregatedContext = null;
        this.lastFormedReps = null;
    }

    static final class Checkpoint {

        @NotNull
        private final PieceRuntimeState.Checkpoint stateCheckpoint;
        @NotNull
        private final LongSet positions;
        private final boolean validated;
        private final boolean dirty;
        @Nullable
        private final int[] lastFormedReps;
        @Nullable
        private final PatternMatchContext lastAggregatedContext;

        private Checkpoint(@NotNull PieceRuntime runtime) {
            this.stateCheckpoint = runtime.state.checkpoint();
            this.positions = new LongOpenHashSet(runtime.positions);
            this.validated = runtime.validated;
            this.dirty = runtime.dirty;
            this.lastFormedReps = runtime.lastFormedReps == null ? null : runtime.lastFormedReps.clone();
            this.lastAggregatedContext = runtime.lastAggregatedContext == null
                    ? null
                    : runtime.lastAggregatedContext.copy();
        }
    }

    static final class Publication {

        @NotNull
        private final StructurePiece piece;
        @NotNull
        private final Checkpoint checkpoint;

        private Publication(@NotNull StructurePiece piece,
                            @NotNull Checkpoint checkpoint) {
            this.piece = piece;
            this.checkpoint = checkpoint;
        }

        @NotNull
        Map<Long, BlockInfo> copyCachedBlocks() {
            Map<Long, BlockInfo> result = new LinkedHashMap<>();
            for (Long2ObjectMap.Entry<BlockInfo> entry :
                    checkpoint.stateCheckpoint.copyCache().long2ObjectEntrySet()) {
                result.put(entry.getLongKey(), entry.getValue());
            }
            return result;
        }
    }
}
