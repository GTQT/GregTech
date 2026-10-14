package gregtech.api.unification.material.properties;

public class ToxicProperty implements IMaterialProperty {

    /**
     * Base toxicity damage per second when held in inventory.
     * The actual damage is multiplied by the armor's poison resistance.
     */
    private final float toxicity;

    public ToxicProperty(float toxicity) {
        this.toxicity = toxicity;
    }

    public float getToxicity() {
        return toxicity;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        // Hazard properties must not force a DUST property onto the material,
        // otherwise gas-only materials (e.g. Chlorine) would generate solid dust items.
    }
}
