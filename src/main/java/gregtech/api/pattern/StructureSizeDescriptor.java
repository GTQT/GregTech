package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, view-friendly description of a {@link StructureDefinition}'s pattern-local
 * footprint. Used by tooltips and JEI to render a compact "width x height x finger" label
 * without requiring callers to special-case single-piece vs. multi-piece patterns.
 *
 * <p>For a single-piece definition, the per-piece list contains exactly one entry and
 * {@link #getFingerMin()} equals {@link #getFingerMax()}. For a multi-piece definition,
 * {@link #getFingerMin()} and {@link #getFingerMax()} may differ when at least one piece
 * repeats along the finger axis; {@link #getPalmMin()}/{@link #getPalmMax()} and
 * {@link #getThumbMin()}/{@link #getThumbMax()} may likewise differ for repeat groups
 * that expand along the palm or thumb axis.
 *
 * <p>Use {@link #formatTooltip()} to obtain a human-readable string already collapsing
 * ranges (e.g. "5 x 3 x 9" for a fixed single piece, "5 x 3 x 3..9" for a single
 * repeating piece, "5..7 x 3..4 x 3..9" for a multi-piece structure with varying extents).
 */
public final class StructureSizeDescriptor {

    private final List<PieceSize> pieceSizes;
    private final int palmMin;
    private final int palmMax;
    private final int thumbMin;
    private final int thumbMax;
    private final int fingerMin;
    private final int fingerMax;

    StructureSizeDescriptor(@NotNull List<PieceSize> pieceSizes) {
        this.pieceSizes = Collections.unmodifiableList(Objects.requireNonNull(pieceSizes, "pieceSizes"));
        if (pieceSizes.isEmpty()) {
            throw new IllegalArgumentException("pieceSizes must not be empty");
        }
        int pMin = 0, pMax = 0;
        int tMin = 0, tMax = 0;
        int fMin = 0, fMax = 0;
        for (PieceSize ps : pieceSizes) {
            pMin = Math.max(pMin, ps.palmMin);
            pMax = Math.max(pMax, ps.palmMax);
            tMin = Math.max(tMin, ps.thumbMin);
            tMax = Math.max(tMax, ps.thumbMax);
            fMin += ps.fingerMin;
            fMax += ps.fingerMax;
        }
        this.palmMin = pMin;
        this.palmMax = pMax;
        this.thumbMin = tMin;
        this.thumbMax = tMax;
        this.fingerMin = fMin;
        this.fingerMax = fMax;
    }

    /**
     * Build a descriptor from a list of per-piece sizes. Visible across packages because
     * the canonical factory lives in {@link gregtech.api.pattern.element.StructureDefinition}
     * (a different package).
     */
    @NotNull
    public static StructureSizeDescriptor of(@NotNull List<PieceSize> pieceSizes) {
        return new StructureSizeDescriptor(pieceSizes);
    }

    /** @return immutable per-piece breakdown (one entry per piece, in declaration order) */
    @NotNull
    public List<PieceSize> getPieceSizes() {
        return pieceSizes;
    }

    public int getPalmMin() {
        return palmMin;
    }

    public int getPalmMax() {
        return palmMax;
    }

    public int getThumbMin() {
        return thumbMin;
    }

    public int getThumbMax() {
        return thumbMax;
    }

    public int getFingerMin() {
        return fingerMin;
    }

    public int getFingerMax() {
        return fingerMax;
    }

    /**
     * @return the palm axis (width) as a tooltip-friendly range string.
     *         Collapses to a single number when {@link #getPalmMin()} == {@link #getPalmMax()}.
     */
    @NotNull
    public String getFormattedPalm() {
        return formatRange(palmMin, palmMax);
    }

    /**
     * @return the thumb axis (height) as a tooltip-friendly range string.
     *         Collapses to a single number when {@link #getThumbMin()} == {@link #getThumbMax()}.
     */
    @NotNull
    public String getFormattedThumb() {
        return formatRange(thumbMin, thumbMax);
    }

    /**
     * @return the finger axis (length) as a tooltip-friendly range string.
     *         Collapses to a single number when {@link #getFingerMin()} == {@link #getFingerMax()}.
     */
    @NotNull
    public String getFormattedFinger() {
        return formatRange(fingerMin, fingerMax);
    }

    /**
     * @return true if this is a single-piece descriptor with no expandable dimension
     */
    public boolean isFixed() {
        return pieceSizes.size() == 1
                && palmMin == palmMax
                && thumbMin == thumbMax
                && fingerMin == fingerMax;
    }

    /**
     * Format as a tooltip-friendly string. Single values are emitted as-is;
     * ranges use the "min..max" form.
     *
     * <p>Examples:
     * <ul>
     *   <li>Fixed single piece: "5 x 3 x 9"</li>
     *   <li>Single repeating piece: "5 x 3 x 3..9"</li>
     *   <li>Multi-piece with varying extents: "5..7 x 3..4 x 3..9"</li>
     * </ul>
     */
    @NotNull
    public String formatTooltip() {
        return formatRange(palmMin, palmMax) + " x "
                + formatRange(thumbMin, thumbMax) + " x "
                + formatRange(fingerMin, fingerMax);
    }

    private static String formatRange(int min, int max) {
        return min == max ? Integer.toString(min) : (min + ".." + max);
    }

    /**
     * Per-piece pattern-local size ranges (palm x thumb x finger).
     */
    public static final class PieceSize {

        private final String pieceName;
        private final int palmMin;
        private final int palmMax;
        private final int thumbMin;
        private final int thumbMax;
        private final int fingerMin;
        private final int fingerMax;

        public PieceSize(@NotNull String pieceName, int palm, int thumb, int fingerMin, int fingerMax) {
            this(pieceName, palm, palm, thumb, thumb, fingerMin, fingerMax);
        }

        public PieceSize(@NotNull String pieceName,
                         int palmMin, int palmMax,
                         int thumbMin, int thumbMax,
                         int fingerMin, int fingerMax) {
            this.pieceName = Objects.requireNonNull(pieceName, "pieceName");
            this.palmMin = palmMin;
            this.palmMax = palmMax;
            this.thumbMin = thumbMin;
            this.thumbMax = thumbMax;
            this.fingerMin = fingerMin;
            this.fingerMax = fingerMax;
        }

        @NotNull
        public String getPieceName() {
            return pieceName;
        }

        public int getPalm() {
            return palmMax;
        }

        public int getPalmMin() {
            return palmMin;
        }

        public int getPalmMax() {
            return palmMax;
        }

        public int getThumb() {
            return thumbMax;
        }

        public int getThumbMin() {
            return thumbMin;
        }

        public int getThumbMax() {
            return thumbMax;
        }

        public int getFingerMin() {
            return fingerMin;
        }

        public int getFingerMax() {
            return fingerMax;
        }
    }
}
