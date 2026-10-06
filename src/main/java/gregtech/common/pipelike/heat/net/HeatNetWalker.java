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
    /** 整条路径上最差的那根管子的传导率：串联管路的吞吐由瓶颈决定。 */
    private int minHeatTransfer = Integer.MAX_VALUE;

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
        walker.minHeatTransfer = minHeatTransfer;
        return walker;
    }

    @Override
    protected void checkPipe(TileEntityHeatConductor pipeTile, BlockPos pos) {
        conductors = ArrayUtils.add(conductors, pipeTile);
        totalLoss *= (1.0f - pipeTile.getNodeData().getHeatLossPerBlock());
        minHeatTransfer = Math.min(minHeatTransfer, pipeTile.getNodeData().getHeatTransfer());
    }

    @Override
    protected void checkNeighbour(TileEntityHeatConductor pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        if (pipeTile != conductors[conductors.length - 1]) {
            throw new IllegalStateException("The current pipe is not the last added pipe.");
        }
        // 管道之间的连通由 walker 自己递归处理，绝不能把另一根管道当成"端点"记进路由：
        // 路由里的端点会被 HeatNetHandler 直接调用 transferHeat，而管道会拿着自己那份新预算再扇出一次，
        // 结果就是热量在管道之间来回放大。只有真正的外部方块才算端点。
        if (neighbourTile instanceof TileEntityHeatConductor) {
            return;
        }
        if (neighbourTile != null) {
            IHeatable heatable = neighbourTile.getCapability(GregtechCapabilities.CAPABILITY_HEAT_CONTAINER,
                    faceToNeighbour.getOpposite());
            if (heatable != null) {
                routes.add(new HeatRoutePath(faceToNeighbour, conductors, getWalkedBlocks(), totalLoss,
                        minHeatTransfer));
            }
        }
    }

    @Override
    protected Class<TileEntityHeatConductor> getBasePipeClass() {
        return TileEntityHeatConductor.class;
    }
}
