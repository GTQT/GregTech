package gregtech.api.nuclear.fission.components;

import lombok.Getter;
import gregtech.api.nuclear.fission.IModeratorStats;

public class Moderator extends ReactorComponent {
    @Getter
    private final IModeratorStats moderatorStats;

    public Moderator(IModeratorStats moderatorStats, double thermalConductivity, double mass) {
        super(moderatorStats.getModerationFactor(), moderatorStats.getMaxTemperature(), thermalConductivity, mass, true);
        this.moderatorStats = moderatorStats;
    }

    public double getAbsorptionFactor(boolean controlsInserted, boolean isThermal) {
        return moderatorStats.getAbsorptionFactor();
    }
}