package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Lightweight summary for a structure hint operation.
 *
 * <p>Hint implementations still render their effects directly, but each cell
 * now reports whether the selected hint path actually rendered, skipped, or
 * failed.
 */
public final class StructureHintResult {

    private static final StructureHintResult EMPTY = new Builder().build();

    private final int attemptedTraversals;
    private final int activePieces;
    private final int inactivePieces;
    private final int visitedCells;
    private final int triggerHandledCells;
    private final int contextFallbackCells;
    private final int renderedCells;
    private final int skippedRenderCells;
    private final int failedRenderCells;

    private StructureHintResult(@NotNull Builder builder) {
        this.attemptedTraversals = builder.attemptedTraversals;
        this.activePieces = builder.activePieces;
        this.inactivePieces = builder.inactivePieces;
        this.visitedCells = builder.visitedCells;
        this.triggerHandledCells = builder.triggerHandledCells;
        this.contextFallbackCells = builder.contextFallbackCells;
        this.renderedCells = builder.renderedCells;
        this.skippedRenderCells = builder.skippedRenderCells;
        this.failedRenderCells = builder.failedRenderCells;
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

    public int getRenderedCells() {
        return renderedCells;
    }

    public int getSkippedRenderCells() {
        return skippedRenderCells;
    }

    public int getFailedRenderCells() {
        return failedRenderCells;
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
                ", contextFallbackCells=" + contextFallbackCells +
                ", renderedCells=" + renderedCells +
                ", skippedRenderCells=" + skippedRenderCells +
                ", failedRenderCells=" + failedRenderCells;
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
        private int renderedCells;
        private int skippedRenderCells;
        private int failedRenderCells;

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
        public Builder recordRenderOutcome(@NotNull StructureHintRenderResult outcome) {
            switch (outcome.getOutcome()) {
                case RENDERED:
                    renderedCells++;
                    break;
                case SKIPPED:
                    skippedRenderCells++;
                    break;
                case FAILED:
                    failedRenderCells++;
                    break;
                default:
                    break;
            }
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
            renderedCells += result.renderedCells;
            skippedRenderCells += result.skippedRenderCells;
            failedRenderCells += result.failedRenderCells;
            return this;
        }

        @NotNull
        public StructureHintResult build() {
            return new StructureHintResult(this);
        }
    }
}
