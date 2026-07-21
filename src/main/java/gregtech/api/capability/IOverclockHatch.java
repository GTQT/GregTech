package gregtech.api.capability;

public interface IOverclockHatch {

    /**
     * @return the current duration divisor (2 ~ maxDivisor).
     *         Overclock duration factor = 1 / divisor.
     */
    int getCurrentDivisor();

    void setCurrentDivisor(int divisor);

    /**
     * @return the maximum divisor this hatch can provide.
     *         Higher divisor = smaller duration factor = faster overclocking.
     */
    int getMaxDivisor();
}
