package gregtech.api.unification.material.properties;

public class MixProperty  implements IMaterialProperty {
    private int durationOverride = -1;
    private int eutOverride = -1;
    private int circuit = -1;

    public MixProperty() {

    }

    public MixProperty(int circuit) {
        this.circuit = circuit;
    }
    public MixProperty(int eutOverride, int durationOverride,int circuit) {
        this.durationOverride = durationOverride;
        this.eutOverride = eutOverride;
        this.circuit = circuit;
    }
    public MixProperty(int eutOverride, int durationOverride) {
        this.durationOverride = durationOverride;
        this.eutOverride = eutOverride;
    }

    public int getDurationOverride() {
        return durationOverride;
    }

    public MixProperty setDurationOverride(int duration) {
        this.durationOverride = duration;
        return this;
    }

    public int getEUtOverride() {
        return eutOverride;
    }

    public MixProperty setEutOverride(int eut) {
        this.eutOverride = eut;
        return this;
    }

    public int getCircuit() {
        return circuit;
    }

    public MixProperty setCircuit(int circuit) {
        this.circuit = circuit;
        return this;
    }
    @Override
    public void verifyProperty(MaterialProperties properties) {
        properties.ensureSet(PropertyKey.DUST, true);
    }


    public static class Builder {

        private int eutOverride = -1;
        private int durationOverride = -1;
        private int circuit = -1;

        public Builder() {}

        public Builder mixStats(int eutOverride, int durationOverride) {
            this.eutOverride = eutOverride;
            this.durationOverride = durationOverride;
            return this;
        }

        public Builder mixStats(int circuit) {
            this.circuit = circuit;
            return this;
        }

        public Builder mixStats(int eutOverride, int durationOverride,int circuit) {
            this.eutOverride = eutOverride;
            this.durationOverride = durationOverride;
            this.circuit = circuit;
            return this;
        }

        public MixProperty build() {
            return new MixProperty(eutOverride, durationOverride,circuit);
        }
    }
}
