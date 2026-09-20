package gregtech.common.pipelike.fiber.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.pipenet.PipeNetWalker;
import gregtech.api.util.GTUtility;
import gregtech.common.pipelike.fiber.tile.TileEntityFiberPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Walks a fiber optic line from one end to the machine on the other end.
 * <p>
 * Fiber never branches and never attenuates, so the walk simply follows the single chain until it
 * finds a machine carrying a fiber light capability, and stops there.
 */
public class FiberNetWalker extends PipeNetWalker<TileEntityFiberPipe> {

    public static final FiberRoutePath FAILED_MARKER = new FiberRoutePath(null, null, 0,
            FiberRoutePath.Kind.SOURCE);

    /**
     * @param wanted which capability the caller is looking for; when a machine carries both, that
     *               one wins
     */
    @Nullable
    public static FiberRoutePath createNetData(World world, BlockPos sourcePipe, EnumFacing faceToSourceHandler,
                                               FiberRoutePath.Kind wanted) {
        FiberNetWalker walker = new FiberNetWalker(world, sourcePipe, 1);
        walker.sourcePipe = sourcePipe;
        walker.facingToHandler = faceToSourceHandler;
        walker.wanted = wanted;
        walker.traversePipeNet();
        return walker.isFailed() ? FAILED_MARKER : walker.routePath;
    }

    private FiberRoutePath routePath;
    private BlockPos sourcePipe;
    private EnumFacing facingToHandler;
    private FiberRoutePath.Kind wanted;

    protected FiberNetWalker(World world, BlockPos sourcePipe, int distance) {
        super(world, sourcePipe, distance);
    }

    @Override
    protected PipeNetWalker<TileEntityFiberPipe> createSubWalker(World world, EnumFacing facingToNextPos,
                                                                 BlockPos nextPos, int walkedBlocks) {
        FiberNetWalker walker = new FiberNetWalker(world, nextPos, walkedBlocks);
        walker.facingToHandler = facingToHandler;
        walker.sourcePipe = sourcePipe;
        walker.wanted = wanted;
        return walker;
    }

    @Override
    protected void checkPipe(TileEntityFiberPipe pipeTile, BlockPos pos) {}

    @Override
    protected void checkNeighbour(TileEntityFiberPipe pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        if (neighbourTile == null ||
                (GTUtility.arePosEqual(pipePos, sourcePipe) && faceToNeighbour == facingToHandler)) {
            return;
        }

        FiberNetWalker root = (FiberNetWalker) this.root;
        if (root.routePath != null) return;

        EnumFacing capSide = faceToNeighbour.getOpposite();
        boolean isSource = neighbourTile.hasCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE,
                capSide);
        boolean isReceiver = neighbourTile.hasCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER,
                capSide);
        if (!isSource && !isReceiver) return;

        FiberRoutePath.Kind found = (wanted == FiberRoutePath.Kind.SOURCE ? isSource : isReceiver) ?
                wanted : (isSource ? FiberRoutePath.Kind.SOURCE : FiberRoutePath.Kind.RECEIVER);
        root.routePath = new FiberRoutePath(pipeTile, faceToNeighbour, getWalkedBlocks(), found);
        stop();
    }

    @Override
    protected Class<TileEntityFiberPipe> getBasePipeClass() {
        return TileEntityFiberPipe.class;
    }
}
