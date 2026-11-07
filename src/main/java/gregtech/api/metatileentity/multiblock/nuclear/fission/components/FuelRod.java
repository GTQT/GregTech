package gregtech.api.metatileentity.multiblock.nuclear.fission.components;

import gregtech.api.unification.material.properties.IFissionFuelStats;

import net.minecraft.item.ItemStack;

import lombok.Getter;
import lombok.Setter;

public class FuelRod extends ReactorComponent {

    @Getter
    private IFissionFuelStats fuel;
    @Getter
    @Setter
    private double weight = 1;
    @Getter
    @Setter
    private double thermalProportion = 0;

    public FuelRod(double maxTemperature, double thermalConductivity, IFissionFuelStats fuel, double mass) {
        super(0, maxTemperature, thermalConductivity, mass, true);
        this.fuel = fuel;
    }

    public double getDuration() {
        return fuel.getDuration();
    }

    public double getNeutronGenerationTime() {
        return fuel.getNeutronGenerationTime();
    }

    public void setFuel(IFissionFuelStats property) {
        this.fuel = property;
        this.maxTemperature = property.getMaxTemperature();
    }

    public ItemStack getDepletedFuel() {
        return fuel.getDepletedFuel(thermalProportion);
    }
}
