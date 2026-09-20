package gregtech.api.capability;

import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberLight;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by anything that can receive light from a fiber optic line, such as an optical target
 * hatch.
 * <p>
 * A receiver declares what it is physically able to take ({@link #getAcceptedColor()} and
 * {@link #getIntensityBounds()}), and records what actually arrived through {@link #acceptLight}.
 * A source hatch asks the receiver for that contract before it sends anything, which is how a
 * mismatched colour or an oversized flux is rejected at the source.
 *
 * @see IFiberLightSource
 */
public interface IFiberLightReceiver {

    /**
     * Offers light to this receiver.
     *
     * @param light the arriving light
     * @return {@code true} when the light was taken, {@code false} when it was rejected
     */
    boolean acceptLight(@NotNull FiberLight light);

    /** The light currently held by this receiver, never {@code null}. */
    @NotNull
    FiberLight getAcceptedLight();

    /**
     * The only colour this receiver accepts, or {@code null} when it is colour-agnostic.
     */
    @Nullable
    FiberColor getAcceptedColor();

    /**
     * The flux range this receiver can take, or {@code null} when it takes any amount.
     * Hatch tiers are expressed as an upper bound, so a tiered hatch normally returns
     * {@code [1, 4^tier]}.
     */
    @Nullable
    IntensityBounds getIntensityBounds();

    /** Whether this receiver would accept {@code light} as it is. */
    default boolean accepts(@NotNull FiberLight light) {
        if (light.isEmpty()) return false;
        FiberColor color = getAcceptedColor();
        if (color != null && color != light.color()) return false;
        IntensityBounds bounds = getIntensityBounds();
        return bounds == null || bounds.contains(light.intensity());
    }

    /**
     * An inclusive flux range. Bounds are checked with a min and a max so a recipe or a hatch can
     * express "greater than", "less than" or a closed interval with the same object.
     *
     * @param min lowest accepted flux, inclusive
     * @param max highest accepted flux, inclusive
     */
    @Desugar
    record IntensityBounds(long min, long max) {

        /** Any positive amount. */
        public static final IntensityBounds UNBOUNDED = new IntensityBounds(1L, Long.MAX_VALUE);

        public IntensityBounds {
            if (min > max) throw new IllegalArgumentException("min " + min + " > max " + max);
        }

        /** Everything at or above {@code min}. */
        @NotNull
        public static IntensityBounds atLeast(long min) {
            return new IntensityBounds(min, Long.MAX_VALUE);
        }

        /** Everything at or below {@code max}. */
        @NotNull
        public static IntensityBounds atMost(long max) {
            return new IntensityBounds(1L, max);
        }

        /** A closed interval. */
        @NotNull
        public static IntensityBounds between(long min, long max) {
            return new IntensityBounds(min, max);
        }

        public boolean contains(long intensity) {
            return intensity >= min && intensity <= max;
        }
    }
}
