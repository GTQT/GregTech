package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Lightweight summary for a structure hint operation.
 *
 * <p>The current hint implementations render their effects directly. This
 * result therefore reports traversal and dispatch decisions without claiming
 * that a client-visible particle was produced.
 */
public final class StructureHintResult {

    private static final StructureHintResult EMPTY = new Builder().build();

    private final int attemptedTraversals;
    private final int activePieces;
    private final int inactivePieces;
    private final int visitedCells;
    private final int triggerHandledCells;
    private final int contextFallbackCells;

    private StructureHintResult(@NotNull Builder builder) {
        this.attemptedTraversals = builder.attemptedTraversals;
        this.activePieces = builder.activePieces;
        this.inactivePieces = builder.inactivePieces;
        this.visitedCells = builder.visitedCells;
        this.triggerHandledCells = builder.triggerHandledCells;
        this.contextFallbackCells = builder.contextFallbackCells;
    }

    @NotNull
    public static StructureHintResult empty() {
        return EMPTY;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public int getAttemptedTraversals() {
        return attemptedTraversals;
    }

    public int getActivePieces() {
        return activePieces;
    }

    public int getInactivePieces() {
        return inactivePieces;
    }

    public int getVisitedCells() {
        return visitedCells;
    }

    public int getTriggerHandledCells() {
        return triggerHandledCells;
    }

    public int getContextFallbackCells() {
        return contextFallbackCells;
    }

    public boolean isAttempted() {
        return attemptedTraversals > 0 || visitedCells > 0;
    }

    @NotNull
    public String describeCounts() {
        return "attemptedTraversals=" + attemptedTraversals +
                ", activePieces=" + activePieces +
                ", inactivePieces=" + inactivePieces +
                ", visitedCells=" + visitedCells +
                ", triggerHandledCells=" + triggerHandledCells +
                ", contextFallbackCells=" + contextFallbackCells;
    }

    @Override
    public String toString() {
        return "StructureHintResult{" + describeCounts() + '}';
    }

    public static final class Builder {

        private int attemptedTraversals;
        private int activePieces;
        private int inactivePieces;
        private int visitedCells;
        private int triggerHandledCells;
        private int contextFallbackCells;

        private Builder() {}

        @NotNull
        public Builder recordAttemptedTraversal() {
            attemptedTraversals++;
            return this;
        }

        @NotNull
        public Builder recordActivePiece() {
            activePieces++;
            return this;
        }

        @NotNull
        public Builder recordInactivePiece() {
            inactivePieces++;
            return this;
        }

        @NotNull
        public Builder recordVisitedCell() {
            visitedCells++;
            return this;
        }

        @NotNull
        public Builder recordTriggerHandledCell() {
            triggerHandledCells++;
            return this;
        }

        @NotNull
        public Builder recordContextFallbackCell() {
            contextFallbackCells++;
            return this;
        }

        @NotNull
        public Builder merge(@NotNull StructureHintResult result) {
            attemptedTraversals += result.attemptedTraversals;
            activePieces += result.activePieces;
            inactivePieces += result.inactivePieces;
            visitedCells += result.visitedCells;
            triggerHandledCells += result.triggerHandledCells;
            contextFallbackCells += result.contextFallbackCells;
            return this;
        }

        @NotNull
        public StructureHintResult build() {
            return new StructureHintResult(this);
        }
    }
}
