package gregtech.api.unification;


/**
 * This is some kind of Periodic Table, which can be used to determine Properties of the Materials.
 */
public class Element {

    public final String name;
    public final String symbol;
    public final long protons;
    public final long neutrons;

    public final boolean isIsotope;
    public final double halfLifeSeconds;
    public final String decayTo;

    /**
     * @param protons         Amount of Protons
     * @param neutrons        Amount of Neutrons (I could have made mistakes with the Neutron amount calculation, please
     *                        tell me if I did something wrong)
     * @param halfLifeSeconds Amount of Half Life this Material has in Seconds. -1 for stable Materials
     * @param decayTo         String representing the Elements it decays to. Separated by an '&' Character
     * @param name            Name of the Element
     * @param symbol          Symbol of the Element
     */
    protected Element(long protons, long neutrons, double halfLifeSeconds, String decayTo, String name, String symbol,
                      boolean isIsotope) {
        this.protons = protons;
        this.neutrons = neutrons;
        this.halfLifeSeconds = halfLifeSeconds;
        this.decayTo = decayTo;
        this.name = name;
        this.symbol = symbol;
        this.isIsotope = isIsotope;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getProtons() {
        return protons;
    }

    public long getNeutrons() {
        return neutrons;
    }

    public long getMass() {
        return protons + neutrons;
    }

    public double getHalfLiveSeconds() {
        return halfLifeSeconds;
    }

    @Override
    public String toString() {
        return name;
    }
}
