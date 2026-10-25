package gregtech.api.unification.material.properties;

/**
 * Makes every shape of a material radioactive: dust, ingot, fuel rod and everything else.
 * Armor radiation resistance scales the damage down.
 */
public class RadioactiveProperty extends HazardProperty {

    public RadioactiveProperty(float radioactivity) {
        super(radioactivity);
    }

    /** @deprecated use {@link #getHazardDamage()} */
    @Deprecated
    public float getRadioactivity() {
        return getHazardDamage();
    }
}
