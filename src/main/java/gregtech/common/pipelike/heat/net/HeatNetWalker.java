package gregtech.common.pipelike.heat.net;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IHeatable;
import gregtech.api.pipenet.PipeNetWalker;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HeatNetWalker extends PipeNetWalker<TileEntityHeatConductor> {

    public static List<HeatRoutePath> createNetData(World world, BlockPos sourcePipe) {
        if (!(world.getTileEntity(sourcePipe) instanceof TileEntityHeatConductor)) {
            return null;
        }
        HeatNetWalker walker = new HeatNetWalker(world, sourcePipe, 1, new ArrayList<>());
        walker.traversePipeNet();
        return walker.isFailed() ? null : walker.routes;
    }

    private final List<HeatRoutePath> routes;
    private TileEntityHeatConductor[] conductors = {};
    private float totalLoss = 1.0f;

    protected HeatNetWalker(World world, BlockPos sourcePipe, int walkedBlocks, List<HeatRoutePath> routes) {
        super(world, sourcePipe, walkedBlocks);
        this.routes = routes;
    }

    @Override
    protected PipeNetWalker<TileEntityHeatConductor> createSubWalker(World world, EnumFacing facingToNextPos, BlockPos nextPos,
                                                                     int walkedBlocks) {
        HeatNetWalker walker = new HeatNetWalker(world, nextPos, walkedBlocks, routes);
        walker.totalLoss = totalLoss;
        walker.conductors = conductors;
        return walker;
    }

    @Override
    protected void checkPipe(TileEntityHeatConductor pipeTile, BlockPos pos) {
        conductors = ArrayUtils.add(conductors, pipeTile);
        totalLoss *= (1.0f - pipeTile.getNodeData().getHeatLossPerBlock());
    }

    @Override
    protected void checkNeighbour(TileEntityHeatConductor pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        if (pipeTile != conductors[conductors.length - 1]) {
            throw new IllegalStateException("The current pipe is not the last added pipe.");
        }
        if (neighbourTile != null) {
            IHeatable heatable = neighbourTile.getCapability(GregtechCapabilities.CAPABILITY_HEAT_CONTAINER,
                    faceToNeighbour.getOpposite());
            if (heatable != null) {
                routes.add(new HeatRoutePath(faceToNeighbour, conductors, getWalkedBlocks(), totalLoss));
            }
        }
    }

    @Override
    protected Class<TileEntityHeatConductor> getBasePipeClass() {
        return TileEntityHeatConductor.class;
    }
}
