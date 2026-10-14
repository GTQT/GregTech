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
        // Hazard properties must not force a DUST property onto the material,
        // otherwise gas-only materials (e.g. Chlorine) would generate solid dust items.
    }
}
