package gregtech.api.pattern;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured summary for a creative or survival structure build operation.
 *
 * <p>Placement budget counts cells that still needed a placement attempt in
 * this invocation. Already-valid cells are reported as existing cells and do
 * not consume budget. A partial result means this invocation placed at least
 * one budgeted cell and left at least one budgeted cell unresolved; calling the
 * same build request again resumes naturally because already-placed cells will
 * then be reported as existing cells.
 */
public final class StructureBuildResult {

    private static final StructureBuildResult EMPTY = new Builder().build();

    private final int attemptedTraversals;
    private final int inactivePieces;
    private final int invalidPieceRequests;
    private final int placementBudget;
    private final int visitedCells;
    private final int existingCells;
    private final int placedCells;
    private final int missingCandidateCells;
    private final int abilityLimitBlockedCells;
    private final int skippedHatchCells;
    private final int unavailableItemCells;
    private final int placementFailureCells;
    @NotNull
    private final List<ItemAmount> requiredItems;
    @NotNull
    private final List<ItemAmount> consumedItems;
    @NotNull
    private final List<ItemAmount> missingItems;
    @NotNull
    private final StructureOperationDiagnostics diagnostics;

    private StructureBuildResult(@NotNull Builder builder) {
        this.attemptedTraversals = builder.attemptedTraversals;
        this.inactivePieces = builder.inactivePieces;
        this.invalidPieceRequests = builder.invalidPieceRequests;
        this.placementBudget = builder.placementBudget;
        this.visitedCells = builder.visitedCells;
        this.existingCells = builder.existingCells;
        this.placedCells = builder.placedCells;
        this.missingCandidateCells = builder.missingCandidateCells;
        this.abilityLimitBlockedCells = builder.abilityLimitBlockedCells;
        this.skippedHatchCells = builder.skippedHatchCells;
        this.unavailableItemCells = builder.unavailableItemCells;
        this.placementFailureCells = builder.placementFailureCells;
        this.requiredItems = immutableCopy(builder.requiredItems);
        this.consumedItems = immutableCopy(builder.consumedItems);
        this.missingItems = immutableCopy(builder.missingItems);
        this.diagnostics = builder.diagnostics;
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

    /**
     * Cells that were not already valid and therefore needed a placement
     * decision during this invocation.
     */
    public int getPlacementBudget() {
        return placementBudget;
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

    public int getRemainingPlacementBudget() {
        return Math.max(0, placementBudget - placedCells);
    }

    @NotNull
    public List<ItemAmount> getRequiredItems() {
        return requiredItems;
    }

    @NotNull
    public List<ItemAmount> getConsumedItems() {
        return consumedItems;
    }

    @NotNull
    public List<ItemAmount> getMissingItems() {
        return missingItems;
    }

    public boolean isAttempted() {
        return attemptedTraversals > 0 || visitedCells > 0;
    }

    public boolean hasPlacements() {
        return placedCells > 0;
    }

    public boolean isComplete() {
        return isAttempted() && getRemainingPlacementBudget() == 0 && !hasBlockedCells();
    }

    public boolean hasPartialPlacement() {
        return placedCells > 0 && getRemainingPlacementBudget() > 0;
    }

    public boolean requiresResume() {
        return getRemainingPlacementBudget() > 0;
    }

    public boolean hasBlockedCells() {
        return missingCandidateCells > 0 ||
                abilityLimitBlockedCells > 0 ||
                skippedHatchCells > 0 ||
                unavailableItemCells > 0 ||
                placementFailureCells > 0 ||
                invalidPieceRequests > 0 ||
                getRemainingPlacementBudget() > 0;
    }

    @NotNull
    public StructureOperationDiagnostics getDiagnostics() {
        return diagnostics;
    }

    @NotNull
    public StructureBuildResult withDiagnostics(@NotNull StructureOperationDiagnostics diagnostics) {
        Builder builder = builder()
                .merge(this)
                .diagnostics(diagnostics);
        return builder.build();
    }

    @NotNull
    public String describeCounts() {
        return "attemptedTraversals=" + attemptedTraversals +
                ", inactivePieces=" + inactivePieces +
                ", invalidPieceRequests=" + invalidPieceRequests +
                ", placementBudget=" + placementBudget +
                ", remainingPlacementBudget=" + getRemainingPlacementBudget() +
                ", visitedCells=" + visitedCells +
                ", existingCells=" + existingCells +
                ", placedCells=" + placedCells +
                ", missingCandidateCells=" + missingCandidateCells +
                ", abilityLimitBlockedCells=" + abilityLimitBlockedCells +
                ", skippedHatchCells=" + skippedHatchCells +
                ", unavailableItemCells=" + unavailableItemCells +
                ", placementFailureCells=" + placementFailureCells +
                ", requiredItems=" + requiredItems +
                ", consumedItems=" + consumedItems +
                ", missingItems=" + missingItems;
    }

    @Override
    public String toString() {
        return "StructureBuildResult{" + describeCounts() + '}';
    }

    @NotNull
    private static List<ItemAmount> immutableCopy(@NotNull List<ItemAmount> source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemAmount> copy = new ArrayList<>(source.size());
        for (ItemAmount item : source) {
            copy.add(item.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class ItemAmount {

        @NotNull
        private final ItemStack stack;
        private final int count;

        private ItemAmount(@NotNull ItemStack stack, int count) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            this.stack = copy;
            this.count = count;
        }

        @NotNull
        public ItemStack getStack() {
            return stack.copy();
        }

        public int getCount() {
            return count;
        }

        @NotNull
        private ItemAmount copy() {
            return new ItemAmount(stack, count);
        }

        @Override
        public String toString() {
            return stack.getDisplayName() + " x" + count;
        }
    }

    public static final class Builder {

        private int attemptedTraversals;
        private int inactivePieces;
        private int invalidPieceRequests;
        private int placementBudget;
        private int visitedCells;
        private int existingCells;
        private int placedCells;
        private int missingCandidateCells;
        private int abilityLimitBlockedCells;
        private int skippedHatchCells;
        private int unavailableItemCells;
        private int placementFailureCells;
        @NotNull
        private final List<ItemAmount> requiredItems = new ArrayList<>();
        @NotNull
        private final List<ItemAmount> consumedItems = new ArrayList<>();
        @NotNull
        private final List<ItemAmount> missingItems = new ArrayList<>();
        @NotNull
        private StructureOperationDiagnostics diagnostics = StructureOperationDiagnostics.empty();

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
        public Builder recordPlacementBudget() {
            placementBudget++;
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
        public Builder recordRequiredItem(@NotNull ItemStack stack) {
            return recordRequiredItem(stack, 1);
        }

        @NotNull
        public Builder recordRequiredItem(@NotNull ItemStack stack, int count) {
            addItem(requiredItems, stack, count);
            return this;
        }

        @NotNull
        public Builder recordConsumedItem(@NotNull ItemStack stack) {
            return recordConsumedItem(stack, 1);
        }

        @NotNull
        public Builder recordConsumedItem(@NotNull ItemStack stack, int count) {
            addItem(consumedItems, stack, count);
            return this;
        }

        @NotNull
        public Builder recordMissingItem(@NotNull ItemStack stack) {
            return recordMissingItem(stack, 1);
        }

        @NotNull
        public Builder recordMissingItem(@NotNull ItemStack stack, int count) {
            addItem(missingItems, stack, count);
            return this;
        }

        @NotNull
        public Builder merge(@NotNull StructureBuildResult result) {
            attemptedTraversals += result.attemptedTraversals;
            inactivePieces += result.inactivePieces;
            invalidPieceRequests += result.invalidPieceRequests;
            placementBudget += result.placementBudget;
            visitedCells += result.visitedCells;
            existingCells += result.existingCells;
            placedCells += result.placedCells;
            missingCandidateCells += result.missingCandidateCells;
            abilityLimitBlockedCells += result.abilityLimitBlockedCells;
            skippedHatchCells += result.skippedHatchCells;
            unavailableItemCells += result.unavailableItemCells;
            placementFailureCells += result.placementFailureCells;
            mergeItems(requiredItems, result.requiredItems);
            mergeItems(consumedItems, result.consumedItems);
            mergeItems(missingItems, result.missingItems);
            if (diagnostics == StructureOperationDiagnostics.empty()
                    && result.diagnostics != StructureOperationDiagnostics.empty()) {
                diagnostics = result.diagnostics;
            }
            return this;
        }

        @NotNull
        public Builder diagnostics(@NotNull StructureOperationDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
            return this;
        }

        @NotNull
        public StructureBuildResult build() {
            return new StructureBuildResult(this);
        }

        private static void mergeItems(@NotNull List<ItemAmount> target,
                                       @NotNull List<ItemAmount> source) {
            for (ItemAmount item : source) {
                addItem(target, item.stack, item.count);
            }
        }

        private static void addItem(@NotNull List<ItemAmount> target,
                                    @NotNull ItemStack stack,
                                    int count) {
            if (stack.isEmpty() || count <= 0) {
                return;
            }
            for (int i = 0; i < target.size(); i++) {
                ItemAmount current = target.get(i);
                if (ItemStack.areItemsEqual(current.stack, stack) &&
                        ItemStack.areItemStackTagsEqual(current.stack, stack)) {
                    target.set(i, new ItemAmount(current.stack, current.count + count));
                    return;
                }
            }
            target.add(new ItemAmount(stack, count));
        }
    }
}
