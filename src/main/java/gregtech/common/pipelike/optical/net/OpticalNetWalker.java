package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.pipenet.PipeNetWalker;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.api.util.GTUtility;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Walks one optical cable segment from its source, looking for the first machine that carries an
 * optical capability.
 * <p>
 * A cable material only preserves a signal for {@link OpticalCableProperties#getDecayDistance()}
 * blocks. The walker enforces that budget: the signal is spent once the endpoint would sit farther
 * than the material allows, so the segment simply reads as disconnected until an optical relay
 * regenerates it.
 */
public class OpticalNetWalker extends PipeNetWalker<TileEntityOpticalPipe> {

    public static final OpticalRoutePath FAILED_MARKER = new OpticalRoutePath(null, null, 0,
            OpticalRoutePath.Kind.DATA);

    @Nullable
    public static OpticalRoutePath createNetData(World world, BlockPos sourcePipe, EnumFacing faceToSourceHandler,
                                                 OpticalRoutePath.Kind kind) {
        TileEntity tile = world.getTileEntity(sourcePipe);
        if (!(tile instanceof TileEntityOpticalPipe opticalPipe)) return FAILED_MARKER;
        int maxDistance = opticalPipe.getOpticalProperties().getDecayDistance();
        OpticalNetWalker walker = new OpticalNetWalker(world, sourcePipe, 0, maxDistance, kind);
        walker.sourcePipe = sourcePipe;
        walker.facingToHandler = faceToSourceHandler;
        walker.traversePipeNet();
        return walker.isFailed() ? FAILED_MARKER : walker.routePath;
    }

    private OpticalRoutePath routePath;
    private BlockPos sourcePipe;
    private EnumFacing facingToHandler;
    private final int maxDistance;
    private final OpticalRoutePath.Kind kind;

    protected OpticalNetWalker(World world, BlockPos sourcePipe, int distance, int maxDistance,
                               OpticalRoutePath.Kind kind) {
        super(world, sourcePipe, distance);
        this.maxDistance = maxDistance;
        this.kind = kind;
    }

    @Override
    protected PipeNetWalker<TileEntityOpticalPipe> createSubWalker(World world, EnumFacing facingToNextPos,
                                                                   BlockPos nextPos, int walkedBlocks) {
        OpticalNetWalker walker = new OpticalNetWalker(world, nextPos, walkedBlocks, maxDistance, kind);
        walker.facingToHandler = facingToHandler;
        walker.sourcePipe = sourcePipe;
        return walker;
    }

    @Override
    protected void checkPipe(TileEntityOpticalPipe pipeTile, BlockPos pos) {}

    @Override
    protected void checkNeighbour(TileEntityOpticalPipe pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        if (neighbourTile == null ||
                (GTUtility.arePosEqual(pipePos, sourcePipe) && faceToNeighbour == facingToHandler)) {
            return;
        }

        OpticalNetWalker root = (OpticalNetWalker) this.root;
        if (root.routePath != null) return;

        // The endpoint sits one block past this pipe, and getWalkedBlocks() is this pipe's
        // distance from its source, so the signal must still be alive at that distance.
        if (!isSignalAliveAt(getWalkedBlocks())) return;

        // Prefer the capability that was asked for, but accept whichever one this segment carries.
        boolean dataFirst = kind != OpticalRoutePath.Kind.COMPUTATION;
        boolean foundData = neighbourTile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS,
                faceToNeighbour.getOpposite());
        boolean foundComputation = neighbourTile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER,
                faceToNeighbour.getOpposite());
        if (dataFirst ? foundData : foundComputation) {
            root.routePath = new OpticalRoutePath(pipeTile, faceToNeighbour, getWalkedBlocks(),
                    foundData ? OpticalRoutePath.Kind.DATA : OpticalRoutePath.Kind.COMPUTATION);
            stop();
        } else if (dataFirst ? foundComputation : foundData) {
            root.routePath = new OpticalRoutePath(pipeTile, faceToNeighbour, getWalkedBlocks(),
                    foundComputation ? OpticalRoutePath.Kind.COMPUTATION : OpticalRoutePath.Kind.DATA);
            stop();
        }
    }

    /** Whether a signal that has travelled {@code distance} blocks from its source is still usable. */
    private boolean isSignalAliveAt(int distance) {
        return distance <= maxDistance;
    }

    @Override
    protected Class<TileEntityOpticalPipe> getBasePipeClass() {
        return TileEntityOpticalPipe.class;
    }
}
