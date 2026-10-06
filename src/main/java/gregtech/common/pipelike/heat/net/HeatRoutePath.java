package gregtech.common.pipelike.heat.net;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IHeatable;
import gregtech.api.pipenet.IRoutePath;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

public class HeatRoutePath implements IRoutePath<TileEntityHeatConductor> {

    private final TileEntityHeatConductor targetPipe;
    private final EnumFacing destFacing;
    private final int distance;
    private final TileEntityHeatConductor[] path;
    private final float efficiency; // 传输效率（沿途按段连乘的热损失）
    private final int minHeatTransfer; // 路径上最差的管子传导率，串联管路的瓶颈

    public HeatRoutePath(EnumFacing destFacing, TileEntityHeatConductor[] path, int distance, float efficiency,
                         int minHeatTransfer) {
        this.targetPipe = path[path.length - 1];
        this.destFacing = destFacing;
        this.path = path;
        this.distance = distance;
        this.efficiency = efficiency;
        this.minHeatTransfer = minHeatTransfer;
    }

    @Override
    public @NotNull TileEntityHeatConductor getTargetPipe() {
        return targetPipe;
    }

    @Override
    public @NotNull EnumFacing getTargetFacing() {
        return destFacing;
    }

    @Override
    public int getDistance() {
        return distance;
    }

    public float getEfficiency() {
        return efficiency;
    }

    /** 路径上最差的那根管子的传导率（HU/tick）。串联时吞吐由瓶颈决定。 */
    public int getMinHeatTransfer() {
        return minHeatTransfer;
    }

    public TileEntityHeatConductor[] getPath() {
        return path;
    }

    public IHeatable getHandler() {
        return getTargetCapability(GregtechCapabilities.CAPABILITY_HEAT_CONTAINER);
    }
}
