package gtqt.api.util.wireless;


import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;

import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessEnergyHatch;

import java.math.BigInteger;
import java.util.UUID;

public class EnergyContainerWireless extends EnergyContainerHandler {

    public EnergyContainerWireless(MetaTileEntity tileEntity, boolean isExport, long voltage, long amperage){
        this(tileEntity,voltage*amperage*320,isExport?0:voltage,amperage,isExport?voltage:0,amperage);
    }

    public EnergyContainerWireless(MetaTileEntity tileEntity, long maxCapacity, long maxInputVoltage, long maxInputAmperage, long maxOutputVoltage, long maxOutputAmperage) {
        super(tileEntity, maxCapacity, maxInputVoltage, maxInputAmperage, maxOutputVoltage, maxOutputAmperage);
    }

    @Override
    public void update() {
        super.update();
        if(!this.metaTileEntity.getWorld().isRemote){
            var world = metaTileEntity.getWorld();
            if(this.metaTileEntity.getOwnerGT()!=null){
                int id = ((MetaTileEntityWirelessEnergyHatch)metaTileEntity).WirelessId;
                if(id==-1 || id==0)
                    return;
                // 安全获取网络示例
                NetworkDatabase db = NetworkDatabase.get(world);
                NetworkNode node = db.getNetwork(((MetaTileEntityWirelessEnergyHatch)metaTileEntity).WirelessId);

                if (node == null) {
                    NetworkManager.INSTANCE.createNetwork(world,metaTileEntity.getOwnerGT(),"无线网络");
                    db = NetworkDatabase.get(world);
                    node = db.getNetwork(((MetaTileEntityWirelessEnergyHatch)metaTileEntity).WirelessId);
                }
                {
                    var machine = new WorldBlockPos(metaTileEntity.getWorld().provider.getDimension(),metaTileEntity.getPos());
                    if(!node.machines.contains(machine))
                    {
                        node.machines.add(machine);
                        NetworkDatabase finalDb = db;
                        NetworkNode finalNode = node;
                        db.getNetworks().keySet().forEach(x->{
                            var del = finalDb.getNetwork(x);
                            if(del.getNetworkID()!= finalNode.getNetworkID())
                            {
                                del.machines.remove(machine);
                            }
                        });
                    }
                }
                //是动力舱 给网络增加能量
                if(this.getInputVoltage()==0)
                {
                    if(this.energyStored>0)
                    {
                        var b1 =BigInteger.valueOf(this.energyStored);
                        if(node!=null)
                        {
                            long added = NetworkManager.INSTANCE.transferEnergy(world,node.getNetworkID(),b1);
                            this.removeEnergy(added);
                        }

                    }
                }//是能源仓 抽取能量
                else
                {
                    long needEnergy = this.getEnergyCapacity()-this.getEnergyStored();
                    if(node!=null)
                    {
                        long added = NetworkManager.INSTANCE.transferEnergy(world,node.getNetworkID(),BigInteger.valueOf(-needEnergy));
                        this.addEnergy(Math.abs(added));
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
