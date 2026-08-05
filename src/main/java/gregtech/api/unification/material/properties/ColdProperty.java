package gregtech.api.unification.material.properties;

public class ColdProperty implements IMaterialProperty {

    /**
     * Base frost damage per second when held in inventory.
     * The actual damage is multiplied by the armor's heat resistance.
     */
    private final float coldDamage;

    public ColdProperty(float coldDamage) {
        this.coldDamage = coldDamage;
    }

    public float getColdDamage() {
        return coldDamage;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }
}
