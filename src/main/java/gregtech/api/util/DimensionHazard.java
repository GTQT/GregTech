package gregtech.api.util;

import org.jetbrains.annotations.NotNull;

/**
 * A hazard a dimension inflicts on the players inside it.
 */
public final class DimensionHazard {

    @NotNull
    private final Hazard hazard;

    private final float baseDamage;

    /**
     * @param hazard     which hazard — also supplies its damage source and armor resistance
     * @param baseDamage damage per second
     */
    public DimensionHazard(@NotNull Hazard hazard, float baseDamage) {
        this.hazard = hazard;
        this.baseDamage = baseDamage;
    }

    @NotNull
    public Hazard hazard() {
        return hazard;
    }

    public float baseDamage() {
        return baseDamage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DimensionHazard other)) return false;
        return Float.compare(other.baseDamage, baseDamage) == 0
                && hazard.equals(other.hazard);
    }

    @Override
    public int hashCode() {
        return 31 * hazard.hashCode() + Float.hashCode(baseDamage);
    }

    @Override
    public String toString() {
        return "DimensionHazard[hazard=" + hazard + ", baseDamage=" + baseDamage + "]";
    }
}
