package gregtech.api.unification.material.properties;

import com.google.common.base.Preconditions;

/**
 * A substance entry for {@link gregtech.api.util.Hazard}: a material that hurts in every one
 * of its shapes.
 * <p>
 * A material property is the only place a substance enters the hazard system. A radioactive
 * material irradiates as a dust, an ingot or a fuel rod; the shape contributes nothing extra
 * unless the {@link gregtech.api.unification.ore.OrePrefix} registers its own reading for the
 * same hazard. The two readings add.
 * <p>
 * The value is unshielded damage per second, before armor. All hazard properties share this
 * class so that a hazard never has to know which concrete property it is reading.
 */
public abstract class HazardProperty implements IMaterialProperty {

    private final float damagePerSecond;

    protected HazardProperty(float damagePerSecond) {
        Preconditions.checkArgument(damagePerSecond >= 0f,
                "Hazard damage must be non-negative, got %s", damagePerSecond);
        this.damagePerSecond = damagePerSecond;
    }

    /** The unshielded damage per second this substance adds to every prefix. */
    public final float getHazardDamage() {
        return damagePerSecond;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        // A hazard property must not force a base type such as DUST onto the material,
        // otherwise gas-only materials (e.g. radon) would generate solid dust items.
    }
}
