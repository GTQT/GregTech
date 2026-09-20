package gregtech.common.pipelike.fiber;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;

/**
 * One quantum of light on a fiber optic line: a color plus an intensity (flux).
 * <p>
 * Intensity is a {@code long} because the tier scale grows by a factor of four per tier and runs
 * past {@code Integer.MAX_VALUE} at the top end.
 *
 * @param color     the carried color
 * @param intensity flux, in the same arbitrary unit the hatch tiers are expressed in
 */
@Desugar
public record FiberLight(@NotNull FiberColor color, long intensity) {

    /** No light at all. */
    public static final FiberLight NONE = new FiberLight(FiberColor.WHITE, 0L);

    public boolean isEmpty() {
        return intensity <= 0L;
    }

    /** Light of the same color, but limited to at most {@code limit} flux. */
    @NotNull
    public FiberLight clampedTo(long limit) {
        return intensity <= limit ? this : new FiberLight(color, Math.max(0L, limit));
    }

    @NotNull
    @Override
    public String toString() {
        return color.name() + "@" + intensity;
    }
}
