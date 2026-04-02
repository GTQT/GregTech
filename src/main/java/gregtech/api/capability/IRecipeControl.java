package gregtech.api.capability;

public interface IRecipeControl {

    boolean enableExtendControl();

    boolean isRecipeLocked();

    void setRecipeLocked(boolean enabled);

    boolean isEnergyLackWarningEnabled();

    void setEnergyLackWarningEnabled(boolean enabled);
}
