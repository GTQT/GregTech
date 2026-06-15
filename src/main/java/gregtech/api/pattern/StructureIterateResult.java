package gregtech.api.pattern;

import gregtech.api.util.BlockInfo;

import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed outcome for read-only structure iteration.
 */
public final class StructureIterateResult {

    public enum Outcome {
        VISITED,
        EMPTY,
        UNSUPPORTED
    }

    public enum Source {
        SINGLE_PIECE,
        MULTI_PIECE
    }

    @NotNull
    private final Outcome outcome;
    @NotNull
    private final Source source;
    @NotNull
    private final Map<BlockPos, BlockInfo> blocks;
    @NotNull
    private final LongSet positions;
    private final int activePieces;
    private final int inactivePieces;
    @NotNull
    private final StructureOperationDiagnostics diagnostics;

    private StructureIterateResult(@NotNull Outcome outcome,
                                   @NotNull Source source,
                                   @NotNull Map<BlockPos, BlockInfo> blocks,
                                   @NotNull LongSet positions,
                                   int activePieces,
                                   int inactivePieces,
                                   @NotNull StructureOperationDiagnostics diagnostics) {
        this.outcome = outcome;
        this.source = source;
        this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks));
        this.positions = new LongOpenHashSet(positions);
        this.activePieces = activePieces;
        this.inactivePieces = inactivePieces;
        this.diagnostics = diagnostics;
    }

    @NotNull
    public static StructureIterateResult single(@NotNull Map<BlockPos, BlockInfo> blocks) {
        LongSet positions = new LongOpenHashSet();
        for (BlockPos pos : blocks.keySet()) {
            positions.add(pos.toLong());
        }
        return new StructureIterateResult(
                blocks.isEmpty() ? Outcome.EMPTY : Outcome.VISITED,
                Source.SINGLE_PIECE, blocks, positions, blocks.isEmpty() ? 0 : 1, 0,
                StructureOperationDiagnostics.empty());
    }

    @NotNull
    public static StructureIterateResult multi(@NotNull LongSet positions,
                                               int activePieces,
                                               int inactivePieces) {
        return new StructureIterateResult(
                positions.isEmpty() ? Outcome.EMPTY : Outcome.VISITED,
                Source.MULTI_PIECE, Collections.emptyMap(), positions,
                activePieces, inactivePieces, StructureOperationDiagnostics.empty());
    }

    @NotNull
    public static StructureIterateResult unsupported(@NotNull Source source) {
        return new StructureIterateResult(
                Outcome.UNSUPPORTED, source, Collections.emptyMap(),
                new LongOpenHashSet(), 0, 0, StructureOperationDiagnostics.empty());
    }

    @NotNull
    public Outcome getOutcome() {
        return outcome;
    }

    @NotNull
    public Source getSource() {
        return source;
    }

    public boolean visited() {
        return outcome == Outcome.VISITED;
    }

    @NotNull
    public Map<BlockPos, BlockInfo> getBlocks() {
        return blocks;
    }

    @NotNull
    public LongSet getPositions() {
        return new LongOpenHashSet(positions);
    }

    public int getVisitedPositions() {
        return positions.size();
    }

    public int getActivePieces() {
        return activePieces;
    }

    public int getInactivePieces() {
        return inactivePieces;
    }

    @NotNull
    public StructureOperationDiagnostics getDiagnostics() {
        return diagnostics;
    }

    @NotNull
    public StructureIterateResult withDiagnostics(@NotNull StructureOperationDiagnostics diagnostics) {
        return new StructureIterateResult(
                outcome, source, blocks, positions, activePieces, inactivePieces, diagnostics);
    }
}
