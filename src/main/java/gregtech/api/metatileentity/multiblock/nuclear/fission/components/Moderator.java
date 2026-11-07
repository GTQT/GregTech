package gregtech.api.metatileentity.multiblock.nuclear.fission.components;

import gregtech.api.metatileentity.multiblock.nuclear.fission.IModeratorStats;

import lombok.Getter;

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
