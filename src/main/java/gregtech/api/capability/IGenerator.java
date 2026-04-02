package gregtech.api.capability;

public interface IGenerator {

    boolean isDynamoFull();

    boolean isEnergyOverFlow();

    void setEnergyOverFlowMode(boolean enable);
}
