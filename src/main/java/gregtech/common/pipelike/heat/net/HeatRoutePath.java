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
    private final float efficiency; // 传输效率（考虑热损失）

    public HeatRoutePath(EnumFacing destFacing, TileEntityHeatConductor[] path, int distance, float efficiency) {
        this.targetPipe = path[path.length - 1];
        this.destFacing = destFacing;
        this.path = path;
        this.distance = distance;
        this.efficiency = efficiency;
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

    public TileEntityHeatConductor[] getPath() {
        return path;
    }

    public IHeatable getHandler() {
        return getTargetCapability(GregtechCapabilities.CAPABILITY_HEAT_CONTAINER);
    }
}
