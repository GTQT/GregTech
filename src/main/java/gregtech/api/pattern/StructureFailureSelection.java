package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chooses the most useful failure when both orientations or multiple lifecycle
 * phases fail.
 */
public final class StructureFailureSelection {

    private StructureFailureSelection() {}

    @Nullable
    public static StructureFailureTrace select(@Nullable StructureFailureTrace first,
                                               @Nullable StructureFailureTrace second) {
        if (first == null) return second;
        if (second == null) return first;
        return compare(first, second) >= 0 ? first : second;
    }

    public static int compare(@NotNull StructureFailureTrace first,
                              @NotNull StructureFailureTrace second) {
        int reason = Integer.compare(first.getReasonPriority(), second.getReasonPriority());
        if (reason != 0) return reason;

        int progress = Integer.compare(first.getProgressDepth(), second.getProgressDepth());
        if (progress != 0) return progress;

        int piece = compareNullable(first.getPiece(), second.getPiece());
        if (piece != 0) return -piece;

        int cell = compareNullable(first.getCell(), second.getCell());
        if (cell != 0) return -cell;

        int flipped = Boolean.compare(!first.isFlipped(), !second.isFlipped());
        if (flipped != 0) return flipped;

        return Long.compare(first.getSequence(), second.getSequence());
    }

    private static int compareNullable(@Nullable String first, @Nullable String second) {
        if (first == null && second == null) return 0;
        if (first == null) return 1;
        if (second == null) return -1;
        return first.compareTo(second);
    }
}
