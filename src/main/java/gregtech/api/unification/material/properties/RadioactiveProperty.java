package gregtech.api.unification.material.properties;

public class RadioactiveProperty implements IMaterialProperty {

    /**
     * Base radiation damage per second when held in inventory.
     * The actual damage is multiplied by the armor's radiation resistance.
     */
    private final float radioactivity;

    public RadioactiveProperty(float radioactivity) {
        this.radioactivity = radioactivity;
    }

    public float getRadioactivity() {
        return radioactivity;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }
}
