package gregtech.api.metatileentity.interfaces;

public interface IPollution {

    double getPollutionAmount();
    int getPollutionTicks();
    void pollution(double amount,int ticks);
}
