package gregtech.api.metatileentity.multiblock.nuclear.fission.components;

import gregtech.api.capability.ICoolantHandler;
import gregtech.api.metatileentity.multiblock.nuclear.fission.ICoolantStats;

import lombok.Getter;
import lombok.Setter;

public class CoolantChannel extends ReactorComponent {

    @Getter
    private final ICoolantStats coolant;
    @Setter
    @Getter
    private double weight;
    private int relatedFuelRodPairs;

    @Getter
    private final ICoolantHandler inputHandler;
    @Getter
    private final ICoolantHandler outputHandler;

    // Allows fission reactors to heat up less than a full liter of coolant.
    public double partialCoolant;

    public CoolantChannel(double maxTemperature, double thermalConductivity, ICoolantStats coolant, double mass,
                          ICoolantHandler inputHandler, ICoolantHandler outputHandler) {
        super(coolant.getModeratorFactor(), maxTemperature, thermalConductivity, mass,
                true);
        this.coolant = coolant;
        this.weight = 0;
        this.inputHandler = inputHandler;
        this.outputHandler = outputHandler;
    }

    public void addWeight(double weight) {
        this.weight += weight;
    }

    @Override
    public double getAbsorptionFactor(boolean controlsInserted, boolean isThermal) {
        return isThermal ? coolant.getSlowAbsorptionFactor() : coolant.getFastAbsorptionFactor();
    }
}
