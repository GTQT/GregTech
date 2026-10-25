package gregtech.api.unification.material.properties;

/**
 * Makes every shape of a material cryogenic. Armor heat resistance scales the cold down,
 * the same stat that protects against a hot shape.
 */
public class ColdProperty extends HazardProperty {

    public ColdProperty(float coldDamage) {
        super(coldDamage);
    }

    /** @deprecated use {@link #getHazardDamage()} */
    @Deprecated
    public float getColdDamage() {
        return getHazardDamage();
    }
}
