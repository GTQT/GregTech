package gregtech.api.unification.material.properties;

import gregtech.api.metatileentity.multiblock.nuclear.fission.IModeratorStats;

import lombok.Builder;
import lombok.Getter;

@Builder
public class ModeratorProperty implements IMaterialProperty, IModeratorStats {
    @Getter
    private int maxTemperature;
    @Getter
    private double moderationFactor;
    @Getter
    private double absorptionFactor;

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }
}
