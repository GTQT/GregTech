package gregtech.api.pattern;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Immutable owner index for event-driven dirty roots.
 */
public final class StructurePositionIndex {

    @NotNull
    private final Long2ObjectMap<BitSet> ownersByWatchedPosition;
    @NotNull
    private final LongSet allWatchedPositions;
    @NotNull
    private final LongSet allFormedPositions;
    @NotNull
    private final List<String> pieceNames;

    private StructurePositionIndex(@NotNull Long2ObjectMap<BitSet> ownersByWatchedPosition,
                                   @NotNull LongSet allWatchedPositions,
                                   @NotNull LongSet allFormedPositions,
                                   @NotNull List<String> pieceNames) {
        Long2ObjectOpenHashMap<BitSet> copiedOwners = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<BitSet> entry : ownersByWatchedPosition.long2ObjectEntrySet()) {
            copiedOwners.put(entry.getLongKey(), (BitSet) entry.getValue().clone());
        }
        this.ownersByWatchedPosition = copiedOwners;
        this.allWatchedPositions = LongSets.unmodifiable(new LongOpenHashSet(allWatchedPositions));
        this.allFormedPositions = LongSets.unmodifiable(new LongOpenHashSet(allFormedPositions));
        this.pieceNames = Collections.unmodifiableList(new ArrayList<>(pieceNames));
    }

    @NotNull
    public static StructurePositionIndex fromResultTable(@NotNull MultiPiecePattern pattern,
                                                         @NotNull StructureResultTable table) {
        Long2ObjectOpenHashMap<BitSet> owners = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet watched = new LongOpenHashSet();
        LongOpenHashSet formed = new LongOpenHashSet();
        List<String> pieceNames = new ArrayList<>(pattern.getPieceList().size());

        for (int ordinal = 0; ordinal < pattern.getPieceList().size(); ordinal++) {
            StructurePiece piece = pattern.getPieceList().get(ordinal);
            pieceNames.add(piece.getName());
            PieceEvaluationResult result = table.getResults().get(ordinal);
            if (result.getPiece() != piece) {
                throw new IllegalArgumentException("Result table piece order does not match the compiled pattern");
            }
            formed.addAll(result.getFormedPositions());
            watched.addAll(result.getWatchedPositions());
            for (long pos : result.getWatchedPositions()) {
                BitSet bits = owners.get(pos);
                if (bits == null) {
                    bits = new BitSet(pattern.getPieceCount());
                    owners.put(pos, bits);
                }
                bits.set(ordinal);
            }
        }

        return new StructurePositionIndex(owners, watched, formed, pieceNames);
    }

    @NotNull
    public BitSet getOwnerBits(long position) {
        BitSet bits = ownersByWatchedPosition.get(position);
        return bits == null ? new BitSet(pieceNames.size()) : (BitSet) bits.clone();
    }

    @NotNull
    public List<String> getOwners(long position) {
        BitSet bits = ownersByWatchedPosition.get(position);
        if (bits == null || bits.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> owners = new ArrayList<>();
        for (int ordinal = bits.nextSetBit(0); ordinal >= 0; ordinal = bits.nextSetBit(ordinal + 1)) {
            if (ordinal < pieceNames.size()) {
                owners.add(pieceNames.get(ordinal));
            }
        }
        return Collections.unmodifiableList(owners);
    }

    @NotNull
    public LongSet getAllWatchedPositions() {
        return allWatchedPositions;
    }

    @NotNull
    public LongSet getAllFormedPositions() {
        return allFormedPositions;
    }

    public int watchedPositionCount() {
        return allWatchedPositions.size();
    }

    public int formedPositionCount() {
        return allFormedPositions.size();
    }
}
