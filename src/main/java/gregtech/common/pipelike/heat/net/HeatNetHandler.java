package gregtech.common.pipelike.heat.net;

import gregtech.api.capability.IHeatable;
import gregtech.api.util.GTLog;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import lombok.Getter;

import java.util.Objects;

public class HeatNetHandler implements IHeatable {

    @Getter
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

    @Override
    public long transferHeat(long heatToTransfer, int sourceTemperature) {
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

            // 传输热量和温度
            transfer = true;
            long acceptedHeat = target.transferHeat(heatAfterLoss, sourceTemperature);
            transfer = false;

            if (acceptedHeat > 0) {
                transferredHeat += acceptedHeat;
                // 更新管道温度（设置网络温度）
                updateConductorTemperature(path, sourceTemperature);

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

    private void updateConductorTemperature(HeatRoutePath path, int temperature) {
        for (TileEntityHeatConductor conductor : path.getPath()) {
            if (!conductor.isInvalid()) {
                conductor.setTemperature(temperature);
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
        // 返回网络温度
        return net != null ? net.getNetworkTemperature() : conductor.getTemperature();
    }

    @Override
    public void setTemperature(int temperature) {
        // 管道温度应该由网络管理
        if (net != null) {
            // 如果是热源连接，需要更新网络温度
            // 这里简化处理：直接设置管道温度
            conductor.setTemperature(temperature);
        }
    }

    @Override
    public int getMaxTemperature() {
        return conductor.getNodeData().getMaxTemperature();
    }

    @Override
    public void setMaxTemperature(int maxTemperature) {
        // 管道最大温度由材料决定，不能被外部设置
        GTLog.logger.warn("Do not use setMaxTemperature() on HeatNetHandler! Pipe max temperature is determined by its material.");
    }

    @Override
    public boolean canAcceptHeat() {
        return true;
    }

    @Override
    public boolean canOutputHeat() {
        return true;
    }

    @Override
    public long changeHeat(long heatToAdd) {
        GTLog.logger.warn("Do not use changeHeat() for heat conductors directly! Use transferHeat() for heat transfer between blocks.");
        return 0;
    }

    @Override
    public long getInputPerSec() {
        return net != null ? net.getHeatFluxPerSec() : 0;
    }

    @Override
    public long getOutputPerSec() {
        return net != null ? net.getHeatFluxPerSec() : 0;
    }
}
