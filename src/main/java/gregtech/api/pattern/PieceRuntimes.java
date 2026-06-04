package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Per-controller aggregate of {@link PieceRuntime} instances, one per piece of a
 * {@link MultiPiecePattern}.
 *
 * <p>This is the new home of multiblock runtime state. Controllers create a
 * {@code PieceRuntimes} in {@code reinitializeStructurePattern()} (or once per
 * structure re-formation), giving them their own independent state for each
 * piece in a multi-piece pattern. The pattern itself is a true shared
 * template; the runtime state belongs to the controller, not to the piece.
 *
 * <p>Pieces are keyed by identity ({@link IdentityHashMap}) so two
 * controllers that share the same compiled {@link MultiPiecePattern} still
 * resolve to independent {@link PieceRuntime} instances.
 *
 * <h2>Lookup convenience</h2>
 * Most call sites only have a single piece in hand. Use {@link #get(StructurePiece)}
 * for that, or {@link #getPrimary()} for the common case of "first piece in
 * the pattern" (e.g. single-piece SD-compiled patterns where the primary
 * piece is the only piece).
 */
public final class PieceRuntimes {

    private final IdentityHashMap<StructurePiece, PieceRuntime> runtimeMap;
    private final List<PieceRuntime> runtimeList;
    @Nullable
    private final PieceRuntime primaryRuntime;

    public PieceRuntimes(@NotNull MultiPiecePattern pattern) {
        this(pattern, pattern.getPieceList());
    }

    /**
     * Construct a {@code PieceRuntimes} over an explicit (possibly filtered) list
     * of pieces. The list order is the order passed in, not the pattern's
     * internal order.
     */
    public PieceRuntimes(@NotNull MultiPiecePattern pattern, @NotNull List<StructurePiece> pieces) {
        this.runtimeMap = new IdentityHashMap<>(pieces.size());
        this.runtimeList = new ArrayList<>(pieces.size());
        PieceRuntime primary = null;
        for (StructurePiece piece : pieces) {
            PieceRuntime runtime = new PieceRuntime(piece);
            runtimeMap.put(piece, runtime);
            runtimeList.add(runtime);
            if (primary == null && pattern.getPrimaryPiece() == piece) {
                primary = runtime;
            }
        }
        this.primaryRuntime = primary;
    }

    /** @return the runtime for the given piece, or null if the piece isn't in this aggregate */
    @Nullable
    public PieceRuntime get(@NotNull StructurePiece piece) {
        return runtimeMap.get(piece);
    }

    /** @return the runtime for the pattern's primary piece, or null if there is no primary */
    @Nullable
    public PieceRuntime getPrimary() {
        return primaryRuntime;
    }

    /** @return an unmodifiable view of all runtimes in declaration order */
    @NotNull
    public List<PieceRuntime> getAll() {
        return Collections.unmodifiableList(runtimeList);
    }

    /** @return the number of pieces tracked by this aggregate */
    public int size() {
        return runtimeList.size();
    }

    /** Reset every piece's runtime to its initial (unformed) state. */
    public void reset() {
        for (PieceRuntime runtime : runtimeList) {
            runtime.reset();
        }
    }
}
