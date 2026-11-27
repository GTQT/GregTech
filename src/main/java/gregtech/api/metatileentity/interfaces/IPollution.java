package gregtech.api.metatileentity.interfaces;

public interface IPollution {

    /**
     * @return the emission rate in pollution points per second. This is the
     *         single canonical unit used by the pollution service, mufflers and
     *         tooltips; machines override only this method.
     */
    default double getPollutionPerSecond() {
        return 0;
    }

    /**
     * Emits one second worth of pollution at the given rate.
     *
     * @param pollutionPerSecond pollution points emitted per second
     */
    default void pollution(double pollutionPerSecond) {
        pollution(pollutionPerSecond, 20);
    }

    /**
     * Low-level emission seam: emits {@code amount} pollution points over
     * {@code ticks} ticks. Machines normally call {@link #pollution(double)};
     * this overload exists so an addon can intercept the actual write (GTQT's
     * Pollution addon overwrites it to queue the emission instead of writing
     * the world aura immediately).
     *
     * @param amount pollution points emitted during {@code ticks}
     * @param ticks  period length in ticks, must be {@code > 0}
     */
    void pollution(double amount, int ticks);
}
