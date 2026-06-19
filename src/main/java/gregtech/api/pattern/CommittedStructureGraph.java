package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable committed graph state for an eligible controller generation.
 */
public final class CommittedStructureGraph {

    private static final AtomicLong GENERATIONS = new AtomicLong();

    private final long generation;
    @NotNull
    private final StructureResultTable resultTable;
    @NotNull
    private final StructureAggregateFolder.Result aggregate;
    @NotNull
    private final StructurePositionIndex positionIndex;
    @NotNull
    private final PieceRuntimes.Publication runtimePublication;
    @NotNull
    private final StructureOrientation orientation;
    @NotNull
    private final StructureExternalDependencySnapshot externalDependencySnapshot;
    private final long resultTableFingerprint;

    @NotNull
    public static CommittedStructureGraph create(
            @NotNull StructureResultTable resultTable,
            @NotNull StructureAggregateFolder.Result aggregate,
            @NotNull StructurePositionIndex positionIndex,
            @NotNull PieceRuntimes.Publication runtimePublication,
            @NotNull StructureOrientation orientation,
            @NotNull StructureExternalDependencySnapshot externalDependencySnapshot) {
        return new CommittedStructureGraph(
                GENERATIONS.incrementAndGet(), resultTable, aggregate, positionIndex,
                runtimePublication, orientation, externalDependencySnapshot);
    }

    private CommittedStructureGraph(
            long generation,
            @NotNull StructureResultTable resultTable,
            @NotNull StructureAggregateFolder.Result aggregate,
            @NotNull StructurePositionIndex positionIndex,
            @NotNull PieceRuntimes.Publication runtimePublication,
            @NotNull StructureOrientation orientation,
            @NotNull StructureExternalDependencySnapshot externalDependencySnapshot) {
        this.generation = generation;
        this.resultTable = resultTable;
        this.aggregate = aggregate;
        this.positionIndex = positionIndex;
        this.runtimePublication = runtimePublication;
        this.orientation = orientation;
        this.externalDependencySnapshot = externalDependencySnapshot;
        this.resultTableFingerprint = resultTable.getSemanticFingerprint();
    }

    public long getGeneration() {
        return generation;
    }

    @NotNull
    public StructureResultTable getResultTable() {
        return resultTable;
    }

    @NotNull
    public StructureAggregateFolder.Result getAggregate() {
        return aggregate;
    }

    @NotNull
    public StructurePositionIndex getPositionIndex() {
        return positionIndex;
    }

    @NotNull
    public PieceRuntimes.Publication getRuntimePublication() {
        return runtimePublication;
    }

    @NotNull
    public StructureOrientation getOrientation() {
        return orientation;
    }

    @NotNull
    public StructureExternalDependencySnapshot getExternalDependencySnapshot() {
        return externalDependencySnapshot;
    }

    public long getResultTableFingerprint() {
        return resultTableFingerprint;
    }
}
