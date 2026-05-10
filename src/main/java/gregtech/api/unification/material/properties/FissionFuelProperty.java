package gregtech.api.unification.material.properties;

import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class FissionFuelProperty implements IMaterialProperty, IFissionFuelStats {

    // The max temperature the fuel can handle before it liquefies.
    private int maxTemperature;
    // Scales how long the fuel rod lasts in the reactor.
    private int duration;
    // How likely it is to absorb a neutron that had touched a moderator.
    private double slowNeutronCaptureCrossSection;
    // How likely it is to absorb a neutron that has not yet touched a moderator.
    private double fastNeutronCaptureCrossSection;
    // How likely it is for a moderated neutron to cause fission in this fuel.
    private double slowNeutronFissionCrossSection;
    // How likely it is for a not-yet-moderated neutron to cause fission in this fuel.
    private double fastNeutronFissionCrossSection;
    // The average time for a neutron to be emitted during a fission event. Do not make this accurate.
    private double neutronGenerationTime;
    private double releasedNeutrons;
    private double requiredNeutrons = 1;
    private double releasedHeatEnergy;
    private double decayRate;
    private String id;

    private Function<Double, ItemStack> depletedFuelSupplier;
    private Supplier<List<ItemStack>> allDepletedFuels;

    private FissionFuelProperty(FissionFuelPropertyBuilder builder) {
        this.maxTemperature = builder.maxTemperature;
        this.duration = builder.duration;
        this.slowNeutronCaptureCrossSection = builder.slowNeutronCaptureCrossSection;
        this.fastNeutronCaptureCrossSection = builder.fastNeutronCaptureCrossSection;
        this.slowNeutronFissionCrossSection = builder.slowNeutronFissionCrossSection;
        this.fastNeutronFissionCrossSection = builder.fastNeutronFissionCrossSection;
        this.neutronGenerationTime = builder.neutronGenerationTime;
        this.releasedNeutrons = builder.releasedNeutrons;
        this.requiredNeutrons = builder.requiredNeutrons;
        this.releasedHeatEnergy = builder.releasedHeatEnergy;
        this.decayRate = builder.decayRate;
        this.id = builder.id;
        this.depletedFuelSupplier = builder.depletedFuelSupplier;
        this.allDepletedFuels = builder.allDepletedFuels;
    }

    public static FissionFuelPropertyBuilder builder() {
        return new FissionFuelPropertyBuilder();
    }

    public static FissionFuelPropertyBuilder builder(String id, int maxTemperature, int duration,
                                                     double neutronGenerationTime) {
        return builder()
                .id(id)
                .maxTemperature(maxTemperature)
                .duration(duration)
                .neutronGenerationTime(neutronGenerationTime);
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }

    @Override
    public List<ItemStack> getDepletedFuels() {
        return allDepletedFuels.get();
    }

    @Override
    public ItemStack getDepletedFuel(double thermalRatio) {
        return depletedFuelSupplier.apply(thermalRatio);
    }

    @Override
    public int getMaxTemperature() {
        return maxTemperature;
    }

    @Override
    public int getDuration() {
        return duration;
    }

    @Override
    public double getSlowNeutronCaptureCrossSection() {
        return slowNeutronCaptureCrossSection;
    }

    @Override
    public double getFastNeutronCaptureCrossSection() {
        return fastNeutronCaptureCrossSection;
    }

    @Override
    public double getSlowNeutronFissionCrossSection() {
        return slowNeutronFissionCrossSection;
    }

    @Override
    public double getFastNeutronFissionCrossSection() {
        return fastNeutronFissionCrossSection;
    }

    @Override
    public double getNeutronGenerationTime() {
        return neutronGenerationTime;
    }

    @Override
    public double getReleasedNeutrons() {
        return releasedNeutrons;
    }

    @Override
    public double getRequiredNeutrons() {
        return requiredNeutrons;
    }

    @Override
    public double getReleasedHeatEnergy() {
        return releasedHeatEnergy;
    }

    @Override
    public double getDecayRate() {
        return decayRate;
    }

    @Override
    public String getId() {
        return id;
    }

    public Function<Double, ItemStack> getDepletedFuelSupplier() {
        return depletedFuelSupplier;
    }

    public void setDepletedFuelSupplier(Function<Double, ItemStack> depletedFuelSupplier) {
        this.depletedFuelSupplier = depletedFuelSupplier;
    }

    public void setAllDepletedFuels(Supplier<List<ItemStack>> allDepletedFuels) {
        this.allDepletedFuels = allDepletedFuels;
    }

    public static class FissionFuelPropertyBuilder {

        private int maxTemperature;
        private int duration;
        private double slowNeutronCaptureCrossSection;
        private double fastNeutronCaptureCrossSection;
        private double slowNeutronFissionCrossSection;
        private double fastNeutronFissionCrossSection;
        private double neutronGenerationTime;
        private double releasedNeutrons;
        private double requiredNeutrons = 1;
        private double releasedHeatEnergy;
        private double decayRate;
        private String id;
        private Function<Double, ItemStack> depletedFuelSupplier;
        private Supplier<List<ItemStack>> allDepletedFuels;

        public FissionFuelPropertyBuilder maxTemperature(int maxTemperature) {
            this.maxTemperature = maxTemperature;
            return this;
        }

        public FissionFuelPropertyBuilder duration(int duration) {
            this.duration = duration;
            return this;
        }

        public FissionFuelPropertyBuilder slowNeutronCaptureCrossSection(double slowNeutronCaptureCrossSection) {
            this.slowNeutronCaptureCrossSection = slowNeutronCaptureCrossSection;
            return this;
        }

        public FissionFuelPropertyBuilder fastNeutronCaptureCrossSection(double fastNeutronCaptureCrossSection) {
            this.fastNeutronCaptureCrossSection = fastNeutronCaptureCrossSection;
            return this;
        }

        public FissionFuelPropertyBuilder slowNeutronFissionCrossSection(double slowNeutronFissionCrossSection) {
            this.slowNeutronFissionCrossSection = slowNeutronFissionCrossSection;
            return this;
        }

        public FissionFuelPropertyBuilder fastNeutronFissionCrossSection(double fastNeutronFissionCrossSection) {
            this.fastNeutronFissionCrossSection = fastNeutronFissionCrossSection;
            return this;
        }

        public FissionFuelPropertyBuilder neutronGenerationTime(double neutronGenerationTime) {
            this.neutronGenerationTime = neutronGenerationTime;
            return this;
        }

        public FissionFuelPropertyBuilder releasedNeutrons(double releasedNeutrons) {
            this.releasedNeutrons = releasedNeutrons;
            return this;
        }

        public FissionFuelPropertyBuilder requiredNeutrons(double requiredNeutrons) {
            this.requiredNeutrons = requiredNeutrons;
            return this;
        }

        public FissionFuelPropertyBuilder releasedHeatEnergy(double releasedHeatEnergy) {
            this.releasedHeatEnergy = releasedHeatEnergy;
            return this;
        }

        public FissionFuelPropertyBuilder decayRate(double decayRate) {
            this.decayRate = decayRate;
            return this;
        }

        public FissionFuelPropertyBuilder id(String id) {
            this.id = id;
            return this;
        }

        public FissionFuelPropertyBuilder depletedFuelSupplier(Function<Double, ItemStack> depletedFuelSupplier) {
            this.depletedFuelSupplier = depletedFuelSupplier;
            return this;
        }

        public FissionFuelPropertyBuilder allDepletedFuels(Supplier<List<ItemStack>> allDepletedFuels) {
            this.allDepletedFuels = allDepletedFuels;
            return this;
        }

        public FissionFuelProperty build() {
            return new FissionFuelProperty(this);
        }
    }
}
