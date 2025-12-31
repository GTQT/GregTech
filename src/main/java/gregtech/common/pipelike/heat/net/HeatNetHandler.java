package gregtech.common.pipelike.heat.net;

import gregtech.api.capability.IHeatable;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.Objects;

public class HeatNetHandler implements IHeatable {

    private HeatNet net;
    private boolean transfer;
    private final TileEntityHeatConductor conductor;
    private final EnumFacing facing;

    public HeatNetHandler(HeatNet net, TileEntityHeatConductor conductor, EnumFacing facing) {
        this.net = Objects.requireNonNull(net);
        this.conductor = Objects.requireNonNull(conductor);
        this.facing = facing;
    }

    public void updateNetwork(HeatNet net) {
        this.net = net;
    }

    public HeatNet getNet() {
        return net;
    }

    @Override
    public long transferHeat(long heatToTransfer) {
        if (transfer) return 0;
        if (facing == null) return 0;

        World world = conductor.getWorld();
        if (world == null || world.isRemote) return 0;

        long transferredHeat = 0L;

        // 获取所有可能的传输路径
        for (HeatRoutePath path : net.getNetData(conductor.getPos())) {
            IHeatable target = path.getHandler();
            if (target == null) continue;

            // 检查目标是否可以接受热量
            if (!target.canAcceptHeat()) continue;

            // 计算路径上的热损失
            long heatLoss = calculateHeatLoss(path, heatToTransfer);
            long heatAfterLoss = heatToTransfer - heatLoss;
            if (heatAfterLoss <= 0) continue;

            // 传输热量
            transfer = true;
            long acceptedHeat = target.transferHeat(heatAfterLoss);
            transfer = false;

            if (acceptedHeat > 0) {
                transferredHeat += acceptedHeat;
                // 更新管道温度
                updateConductorTemperature(path, acceptedHeat + heatLoss);

                net.addHeatFluxPerSec(acceptedHeat);
            }
        }

        return transferredHeat;
    }

    private long calculateHeatLoss(HeatRoutePath path, long heat) {
        long totalLoss = 0;
        for (TileEntityHeatConductor conductor : path.getPath()) {
            float lossFactor = conductor.getNodeData().getHeatLossPerBlock();
            totalLoss += (long) (heat * lossFactor);
        }
        return totalLoss;
    }

    private void updateConductorTemperature(HeatRoutePath path, long heat) {
        for (TileEntityHeatConductor conductor : path.getPath()) {
            if (!conductor.isInvalid()) {
                conductor.applyHeat((int) (heat / 1000)); // 简化的温度计算
            }
        }
    }

    @Override
    public long getHeatStored() {
        return 0; // 管道本身不存储热量，只传输
    }

    @Override
    public long getHeatCapacity() {
        return conductor.getNodeData().getHeatTransfer();
    }

    @Override
    public int getTemperature() {
        return conductor.getTemperature();
    }

    @Override
    public int getMaxTemperature() {
        return conductor.getNodeData().getMaxTemperature();
    }

    @Override
    public boolean canAcceptHeat() {
        return true;
    }

    @Override
    public boolean canOutputHeat() {
        return true;
    }
}
