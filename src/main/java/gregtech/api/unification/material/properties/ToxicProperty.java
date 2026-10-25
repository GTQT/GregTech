package gregtech.api.unification.material.properties;

/**
 * Makes every shape of a material toxic: dust, ingot, fluid-containing item and everything
 * else. Armor chemical resistance scales the damage down.
 */
public class ToxicProperty extends HazardProperty {

    public ToxicProperty(float toxicity) {
        super(toxicity);
    }

    /** @deprecated use {@link #getHazardDamage()} */
    @Deprecated
    public float getToxicity() {
        return getHazardDamage();
    }
}
