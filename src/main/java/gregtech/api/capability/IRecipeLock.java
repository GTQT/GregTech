package gregtech.api.capability;

public interface IRecipeLock {

    boolean enableExtendControl();

    boolean isRecipeLocked();

    void setRecipeLocked(boolean enabled);

    boolean isEnergyLackWarningEnabled();

    void setEnergyLackWarningEnabled(boolean enabled);
}
