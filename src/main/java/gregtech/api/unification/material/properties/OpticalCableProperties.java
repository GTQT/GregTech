package gregtech.api.unification.material.properties;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Properties of a material when it is drawn into an optical cable.
 * <p>
 * Optical cables do not transport energy or matter: they forward the
 * {@link gregtech.api.capability.IOpticalComputationProvider} (CWU/t) and
 * {@link gregtech.api.capability.IOpticalDataAccessHatch} capabilities between machines.
 * The material decides how much computation a single cable can carry and how far a
 * signal survives before it decays into nothing.
 */
public class OpticalCableProperties implements IMaterialProperty {

    /** Fallback used when a material has no explicit optical properties. */
    public static final OpticalCableProperties DEFAULT = new OpticalCableProperties(128, 8);

    /** How much CWU/t this cable material can forward. */
    private final int maxCWUt;
    /** How many blocks a signal travels before it loses all carried information. */
    private final int decayDistance;

    public OpticalCableProperties(int maxCWUt, int decayDistance) {
        this.maxCWUt = Math.max(0, maxCWUt);
        this.decayDistance = Math.max(1, decayDistance);
    }

    public OpticalCableProperties() {
        this(128, 8);
    }

    /** Maximum CWU/t a single optical cable of this material can forward. */
    public int getMaxCWUt() {
        return maxCWUt;
    }

    /**
     * Maximum number of blocks a signal may travel from its source before it is spent.
     * A value of {@code n} means the signal dies at {@code n + 1} blocks from the source.
     */
    public int getDecayDistance() {
        return decayDistance;
    }

    /**
     * Signal strength at a given distance from the (nearest) source, in {@code (0, 1]}.
     * Returns {@code 0} once the signal has fully decayed.
     */
    public double getSignalStrength(int distance) {
        if (distance > decayDistance) return 0.0d;
        return 1.0d - (double) distance / (double) decayDistance;
    }

    /** Whether a signal can still reach {@code distance} blocks away from its source. */
    public boolean isAliveAt(int distance) {
        return distance <= decayDistance;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpticalCableProperties that)) return false;
        return maxCWUt == that.maxCWUt && decayDistance == that.decayDistance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxCWUt, decayDistance);
    }

    @NotNull
    @Override
    public String toString() {
        return "OpticalCableProperties{maxCWUt=" + maxCWUt + ", decayDistance=" + decayDistance + '}';
    }
}
