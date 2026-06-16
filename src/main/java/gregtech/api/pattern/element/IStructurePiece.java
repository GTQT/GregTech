package gregtech.api.pattern.element;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Named structure piece with optional multi-axis repeat capability.
 * Each piece defines a 2D pattern (array of strings), a symbol-to-element mapping,
 * and optional repeat ranges along one or more axes.
 */
public interface IStructurePiece {

    /** Unique name for this piece within its StructureDefinition. */
    String getName();

    /**
     * The 2D pattern as an array of string arrays.
     * Outer array = aisles (z-axis), inner arrays = rows (y-axis),
     * each string = columns (x-axis).
     */
    String[][] getPattern();

    /** Mapping from pattern character to IStructureElement. */
    Map<Character, IStructureElement> getSymbolMap();

    /**
     * Axes along which this piece can repeat.
     * Empty array = fixed (non-repeatable) piece.
     * 0 = X, 1 = Y, 2 = Z
     */
    int[] getRepeatAxes();

    /**
     * Repeat ranges for each axis in getRepeatAxes().
     * Each int[] is {min, max}. Parallel to getRepeatAxes().
     */
    int[][] getRepeatRanges();

    /**
     * Step sizes for each axis in getRepeatAxes().
     * Parallel to getRepeatAxes().
     */
    int[] getStepSizes();

    /**
     * Channel names for each repeat axis (nullable entries).
     * Parallel to getRepeatAxes().
     */
    @Nullable
    String[] getRepeatChannelNames();

    /**
     * Center offset within the base piece pattern {x, y, z}.
     * Used to position the piece relative to the controller.
     */
    int[] getCenterOffset();

    /** Whether this piece is repeatable (has at least one repeat axis). */
    default boolean isRepeatable() {
        return getRepeatAxes().length > 0;
    }

    /**
     * Whether user-facing tooling should expose this piece for preview, hint,
     * and construction operations.
     *
     * <p>Runtime-only pieces are still part of canonical matching and dirty
     * validation, but are hidden from build/projector flows. Typical use cases
     * are rendered-air validation variants that should never be constructed by
     * players or JEI/projector tooling.
     */
    default boolean isToolingVisible() {
        return true;
    }
}
