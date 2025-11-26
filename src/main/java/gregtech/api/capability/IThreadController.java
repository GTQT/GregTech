package gregtech.api.capability;

public interface IThreadController {

    void refreshThread(int ThreadAmount);

    int getThread();

    void setThread(int ThreadAmount);

    int getMaxThread();
}
