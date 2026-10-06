package gregtech.common.pipelike.heat.net;

import gregtech.api.capability.IHeatable;
import gregtech.api.util.GTLog;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

/**
 * 管道某一面暴露出去的 {@link IHeatable}。
 * <p>
 * 管道只搬运热量、不存储热量，所以这里的"容量"语义是 <b>0</b>：吞吐上限由管道材料的
 * {@link gregtech.api.unification.material.properties.HeatConductorProperties#getHeatTransfer()} 决定，
 * 通过 {@link #transferHeat} 内部限流体现，而不是通过 {@code getHeatCapacity()}。
 */
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

    public HeatNet getNet() {
        return net;
    }

    public void updateNetwork(HeatNet net) {
        this.net = net;
    }

    /**
     * 接收上游热量并把它转交给网络里可达的端点。
     * <p>
     * 关键点：{@code heatToTransfer} 是<b>整次调用的总预算</b>，不是每个端点各拿一份。
     * 早期版本对每条路由都用同一个 {@code heatToTransfer} 去试，结果一台只有 100 HU 的机器
     * 能同时喂饱 50 个端点、凭空生成 4900 HU。现在每交付一份就从预算里扣掉源头实际消耗的部分。
     *
     * @param heatToTransfer   上游愿意给出的热量（源头实际要扣掉的量）
     * @param sourceTemperature 上游温度（K）
     * @return 管道从上游<b>实际取走</b>的热量（含沿途损耗）。
     *         这里必须返回"取走量"而不是"送达量"，否则损耗就没人买单了：
     *         调用方按返回值扣自己的库存，返回送达量等于把损耗算成凭空消失的免费热量。
     */
    @Override
    public long transferHeat(long heatToTransfer, int sourceTemperature) {
        if (transfer || facing == null || heatToTransfer <= 0) return 0;

        World world = conductor.getWorld();
        if (world == null || world.isRemote) return 0;

        // 有热量真的流进来，才说明这个方向接着一个热源
        net.reportHeatSource(conductor.getPos(), sourceTemperature);

        List<HeatRoutePath> routes = net.getNetData(conductor.getPos());
        if (routes.isEmpty()) return 0;

        // 预算 = min(上游给的热量, 这根管子本 tick 剩余额度)
        // 额度记在 TileEntity 上、同一个 tick 内所有面共用，所以接 6 个面也不会变成 6 倍吞吐。
        long budget = Math.min(heatToTransfer, conductor.getRemainingTransferBudget());
        if (budget <= 0) return 0;

        long delivered = 0L;
        long consumed = 0L;
        transfer = true;
        try {
            for (HeatRoutePath path : routes) {
                if (budget <= 0) break;

                IHeatable target = path.getHandler();
                if (target == null || !target.canAcceptHeat()) continue;

                // 沿途按段连乘得到的效率（HeatNetWalker 里算好的）
                float efficiency = path.getEfficiency();
                if (efficiency <= 0.0f) continue;

                // 串联管路的吞吐由路径上最差的那根管子决定
                long routeBudget = Math.min(budget, path.getMinHeatTransfer());
                if (routeBudget <= 0) continue;

                long offered = (long) (routeBudget * efficiency);
                if (offered <= 0) continue;

                long accepted = target.transferHeat(offered, sourceTemperature);
                if (accepted <= 0) continue;

                // 目标收到 accepted，按效率反推源头实际消耗了多少预算
                long spent = efficiency >= 1.0f ? accepted : (long) Math.ceil(accepted / efficiency);
                if (spent > budget) spent = budget;

                budget -= spent;
                consumed += spent;
                delivered += accepted;
            }
        } finally {
            transfer = false;
        }

        conductor.consumeTransferBudget(consumed);
        net.addHeatInput(consumed);
        net.addHeatOutput(delivered);

        return consumed;
    }

    /** 管道不储能，所以恒为 0。 */
    @Override
    public long getHeatStored() {
        return 0;
    }

    /** 管道不储能，所以恒为 0（吞吐看材料属性，见 {@link #transferHeat}）。 */
    @Override
    public long getHeatCapacity() {
        return 0;
    }

    @Override
    public int getTemperature() {
        return net != null ? net.getNetworkTemperature() : conductor.getTemperature();
    }

    @Override
    public void setTemperature(int temperature) {
        // 管道温度由网络广播决定，这里只是让外部能够沿路径写入
        conductor.setTemperature(temperature);
    }

    @Override
    public int getMaxTemperature() {
        return conductor.getNodeData().getMaxTemperature();
    }

    @Override
    public void setMaxTemperature(int maxTemperature) {
        // 管道最大温度由材料决定，不能被外部设置
        GTLog.logger.warn(
                "Do not use setMaxTemperature() on HeatNetHandler! Pipe max temperature is determined by its material.");
    }

    @Override
    public boolean canAcceptHeat() {
        // 管道是传输介质：上限由材料温度决定（超了会炸管），但"能不能接热"永远为真。
        // 这里不能返回 temperature < maxTemperature —— 管道温度等于网络温度，
        // 一旦网络温度被热源顶到材料上限，管道就会开始拒收，热源 20 tick 后过期、
        // 网络降温、又能收热，形成周期性的"断续供热"。
        return true;
    }

    @Override
    public boolean canOutputHeat() {
        return true;
    }

    @Override
    public long changeHeat(long heatToAdd) {
        // 往管道里"存"热量没有意义，热量必须直接交给端点
        GTLog.logger.warn(
                "Do not use changeHeat() for heat conductors directly! Use transferHeat() for heat transfer between blocks.");
        return 0;
    }

    @Override
    public long getInputPerSec() {
        return net != null ? net.getLastHeatInputPerSec() : 0L;
    }

    @Override
    public long getOutputPerSec() {
        return net != null ? net.getLastHeatOutputPerSec() : 0L;
    }
}
