package gtqt.api.util.wireless;

import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.world.World;

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
            World world = metaTileEntity.getWorld();
            UUID ownerId = this.metaTileEntity.getOwnerGT();
            if (ownerId == null) return; // 无所有者，无法操作网络

            NetworkNode node = NetworkManager.INSTANCE.getNetworkForPlayer(world, ownerId);
            if (node == null) return;

            if (isExport) { // 动力舱（输出能量到网络）
                if (this.energyStored > 0) {
                    long toTransfer = this.energyStored;
                    long transferred = node.fill(toTransfer);
                    if (transferred > 0) {
                        this.removeEnergy(transferred);
                    }
                }
            } else { // 能源仓：从网络抽取能量
                long needEnergy = this.getEnergyCapacity() - this.getEnergyStored();
                if (needEnergy > 0) {
                    long transferred = node.drain(needEnergy);
                    if (transferred > 0) {
                        this.addEnergy(transferred);
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
