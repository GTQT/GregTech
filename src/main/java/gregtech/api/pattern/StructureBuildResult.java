package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

/**
 * Structured summary for a creative or survival structure build operation.
 *
 * <p>This is intentionally a lightweight accounting shell. It reports what the
 * current legacy placement path visited, placed, or skipped without changing
 * item extraction, block placement, or rollback semantics.
 */
public final class StructureBuildResult {

    private static final StructureBuildResult EMPTY = new Builder().build();

    private final int attemptedTraversals;
    private final int inactivePieces;
    private final int invalidPieceRequests;
    private final int visitedCells;
    private final int existingCells;
    private final int placedCells;
    private final int missingCandidateCells;
    private final int abilityLimitBlockedCells;
    private final int skippedHatchCells;
    private final int unavailableItemCells;
    private final int placementFailureCells;

    private StructureBuildResult(@NotNull Builder builder) {
        this.attemptedTraversals = builder.attemptedTraversals;
        this.inactivePieces = builder.inactivePieces;
        this.invalidPieceRequests = builder.invalidPieceRequests;
        this.visitedCells = builder.visitedCells;
        this.existingCells = builder.existingCells;
        this.placedCells = builder.placedCells;
        this.missingCandidateCells = builder.missingCandidateCells;
        this.abilityLimitBlockedCells = builder.abilityLimitBlockedCells;
        this.skippedHatchCells = builder.skippedHatchCells;
        this.unavailableItemCells = builder.unavailableItemCells;
        this.placementFailureCells = builder.placementFailureCells;
    }

    @NotNull
    public static StructureBuildResult empty() {
        return EMPTY;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public int getAttemptedTraversals() {
        return attemptedTraversals;
    }

    public int getInactivePieces() {
        return inactivePieces;
    }

    public int getInvalidPieceRequests() {
        return invalidPieceRequests;
    }

    public int getVisitedCells() {
        return visitedCells;
    }

    public int getExistingCells() {
        return existingCells;
    }

    public int getPlacedCells() {
        return placedCells;
    }

    public int getMissingCandidateCells() {
        return missingCandidateCells;
    }

    public int getAbilityLimitBlockedCells() {
        return abilityLimitBlockedCells;
    }

    public int getSkippedHatchCells() {
        return skippedHatchCells;
    }

    public int getUnavailableItemCells() {
        return unavailableItemCells;
    }

    public int getPlacementFailureCells() {
        return placementFailureCells;
    }

    public boolean isAttempted() {
        return attemptedTraversals > 0 || visitedCells > 0;
    }

    public boolean hasPlacements() {
        return placedCells > 0;
    }

    public boolean hasBlockedCells() {
        return missingCandidateCells > 0 ||
                abilityLimitBlockedCells > 0 ||
                skippedHatchCells > 0 ||
                unavailableItemCells > 0 ||
                placementFailureCells > 0 ||
                invalidPieceRequests > 0;
    }

    @NotNull
    public String describeCounts() {
        return "attemptedTraversals=" + attemptedTraversals +
                ", inactivePieces=" + inactivePieces +
                ", invalidPieceRequests=" + invalidPieceRequests +
                ", visitedCells=" + visitedCells +
                ", existingCells=" + existingCells +
                ", placedCells=" + placedCells +
                ", missingCandidateCells=" + missingCandidateCells +
                ", abilityLimitBlockedCells=" + abilityLimitBlockedCells +
                ", skippedHatchCells=" + skippedHatchCells +
                ", unavailableItemCells=" + unavailableItemCells +
                ", placementFailureCells=" + placementFailureCells;
    }

    @Override
    public String toString() {
        return "StructureBuildResult{" + describeCounts() + '}';
    }

    public static final class Builder {

        private int attemptedTraversals;
        private int inactivePieces;
        private int invalidPieceRequests;
        private int visitedCells;
        private int existingCells;
        private int placedCells;
        private int missingCandidateCells;
        private int abilityLimitBlockedCells;
        private int skippedHatchCells;
        private int unavailableItemCells;
        private int placementFailureCells;

        private Builder() {}

        @NotNull
        public Builder recordAttemptedTraversal() {
            attemptedTraversals++;
            return this;
        }

        @NotNull
        public Builder recordInactivePiece() {
            inactivePieces++;
            return this;
        }

        @NotNull
        public Builder recordInvalidPieceRequest() {
            invalidPieceRequests++;
            return this;
        }

        @NotNull
        public Builder recordVisitedCell() {
            visitedCells++;
            return this;
        }

        @NotNull
        public Builder recordExistingCell() {
            existingCells++;
            return this;
        }

        @NotNull
        public Builder recordPlacedCell() {
            placedCells++;
            return this;
        }

        @NotNull
        public Builder recordMissingCandidateCell() {
            missingCandidateCells++;
            return this;
        }

        @NotNull
        public Builder recordAbilityLimitBlockedCell() {
            abilityLimitBlockedCells++;
            return this;
        }

        @NotNull
        public Builder recordSkippedHatchCell() {
            skippedHatchCells++;
            return this;
        }

        @NotNull
        public Builder recordUnavailableItemCell() {
            unavailableItemCells++;
            return this;
        }

        @NotNull
        public Builder recordPlacementFailureCell() {
            placementFailureCells++;
            return this;
        }

        @NotNull
        public Builder merge(@NotNull StructureBuildResult result) {
            attemptedTraversals += result.attemptedTraversals;
            inactivePieces += result.inactivePieces;
            invalidPieceRequests += result.invalidPieceRequests;
            visitedCells += result.visitedCells;
            existingCells += result.existingCells;
            placedCells += result.placedCells;
            missingCandidateCells += result.missingCandidateCells;
            abilityLimitBlockedCells += result.abilityLimitBlockedCells;
            skippedHatchCells += result.skippedHatchCells;
            unavailableItemCells += result.unavailableItemCells;
            placementFailureCells += result.placementFailureCells;
            return this;
        }

        @NotNull
        public StructureBuildResult build() {
            return new StructureBuildResult(this);
        }
    }
}
