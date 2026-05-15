package gtqt.api.util.wireless;

import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import java.util.UUID;

public class EnergyContainerWireless extends EnergyContainerHandler {

    private static final int TRANSFER_INTERVAL = 20; // batch every second

    private final boolean isExport;
    private int timer;

    public EnergyContainerWireless(MetaTileEntity tileEntity, boolean isExport, long voltage, long amperage) {
        super(tileEntity, voltage * amperage * 320,
                isExport ? 0 : voltage, amperage,
                isExport ? voltage : 0, amperage);
        this.isExport = isExport;
        this.timer = tileEntity.getWorld() != null
                ? (int) (tileEntity.getWorld().getTotalWorldTime() % TRANSFER_INTERVAL)
                : 0;
    }

    @Override
    public void update() {
        super.update();
        if (this.metaTileEntity.getWorld().isRemote) return;
        if (++timer % TRANSFER_INTERVAL != 0) return;

        UUID ownerId = this.metaTileEntity.getOwnerGT();
        if (ownerId == null) return;

        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return;

        if (isExport) {
            if (this.energyStored > 0) {
                TransferResult result = service.insert(ownerId, this.energyStored, TransferContext.HATCH);
                if (result.isSuccess()) {
                    this.removeEnergy(result.getAmountLong());
                }
            }
        } else {
            long needEnergy = this.getEnergyCapacity() - this.getEnergyStored();
            if (needEnergy > 0) {
                TransferResult result = service.extractUpTo(ownerId, needEnergy, TransferContext.HATCH);
                if (result.isSuccess()) {
                    this.addEnergy(result.getAmountLong());
                }
            }
        }
    }

    @Override
    public long getEnergyCanBeInserted() {
        return 0;
    }
}
