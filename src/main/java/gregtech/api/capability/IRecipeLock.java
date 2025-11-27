package gregtech.api.capability;

public interface IRecipeLock {

    boolean isRecipeLockAllowed();

    boolean isRecipeLocked();

    void setRecipeLocked(boolean enabled);
}
