package gtqt.api.util.wireless;

import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import java.util.UUID;

public class EnergyContainerWireless extends EnergyContainerHandler {

    boolean isExport;

    public EnergyContainerWireless(MetaTileEntity tileEntity, boolean isExport, long voltage, long amperage) {
        this(tileEntity, voltage * amperage * 320, isExport ? 0 : voltage, amperage, isExport ? voltage : 0, amperage);
        this.isExport = isExport;
    }

    public EnergyContainerWireless(MetaTileEntity tileEntity, long maxCapacity, long maxInputVoltage,
                                   long maxInputAmperage, long maxOutputVoltage, long maxOutputAmperage) {
        super(tileEntity, maxCapacity, maxInputVoltage, maxInputAmperage, maxOutputVoltage, maxOutputAmperage);
    }

    @Override
    public void update() {
        super.update();
        if (!this.metaTileEntity.getWorld().isRemote) {
            UUID ownerId = this.metaTileEntity.getOwnerGT();
            if (ownerId == null) return;

            WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
            if (service == null) return;

            if (isExport) {
                // Dynamo hatch: push local buffer into wireless network
                if (this.energyStored > 0) {
                    TransferResult result = service.insert(ownerId, this.energyStored, TransferContext.HATCH);
                    if (result.isSuccess()) {
                        this.removeEnergy(result.getAmountLong());
                    }
                }
            } else {
                // Energy hatch: pull from wireless network into local buffer
                long needEnergy = this.getEnergyCapacity() - this.getEnergyStored();
                if (needEnergy > 0) {
                    TransferResult result = service.extractUpTo(ownerId, needEnergy, TransferContext.HATCH);
                    if (result.isSuccess()) {
                        this.addEnergy(result.getAmountLong());
                    }
                }
            }
        }
    }

    @Override
    public long getEnergyCanBeInserted() {
        return 0;
    }
}
