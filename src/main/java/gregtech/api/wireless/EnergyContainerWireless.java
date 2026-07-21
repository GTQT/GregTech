package gregtech.api.wireless;

import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import java.util.UUID;
import java.util.function.IntSupplier;

public class EnergyContainerWireless extends EnergyContainerHandler {

    private static final int TRANSFER_INTERVAL = 20; // batch every second

    private final boolean isExport;
    private final IntSupplier channelIdSupplier;
    private int timer;

    public EnergyContainerWireless(MetaTileEntity tileEntity, boolean isExport, long voltage, long amperage,
                                   IntSupplier channelIdSupplier) {
        super(tileEntity, voltage * amperage * 320,
                isExport ? 0 : voltage, amperage,
                isExport ? voltage : 0, amperage);
        this.isExport = isExport;
        this.channelIdSupplier = channelIdSupplier;
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
        int channelId = getEffectiveChannelId(service, ownerId);
        service.updateEndpoint(ownerId, channelId, "hatch:" + this.metaTileEntity.getPos().toLong(),
                isExport ? "energy_hatch_output" : "energy_hatch_input",
                this.metaTileEntity.getWorld().provider.getDimension(), this.metaTileEntity.getPos().toLong(), true,
                false, this.metaTileEntity.getWorld().getTotalWorldTime());

        if (isExport) {
            if (this.energyStored > 0) {
                TransferResult result = service.insert(ownerId, channelId, this.energyStored, TransferContext.HATCH);
                if (result.isSuccess()) {
                    this.removeEnergy(result.getAmountLong());
                }
            }
        } else {
            long needEnergy = this.getEnergyCapacity() - this.getEnergyStored();
            if (needEnergy > 0) {
                TransferResult result = service.extractUpTo(ownerId, channelId, needEnergy, TransferContext.HATCH);
                if (result.isSuccess()) {
                    this.addEnergy(result.getAmountLong());
                }
            }
        }
    }

    @Override
    public long getEnergyCanBeInserted() {
        return isExport ? super.getEnergyCanBeInserted() : 0;
    }

    private int getEffectiveChannelId(WirelessEnergyService service, UUID ownerId) {
        int selected = Math.max(0, channelIdSupplier.getAsInt());
        return service.getView(ownerId, selected).isEmpty() ? 0 : selected;
    }
}
