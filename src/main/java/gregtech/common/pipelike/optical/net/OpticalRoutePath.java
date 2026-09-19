package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalDataAccessHatch;
import gregtech.api.pipenet.IRoutePath;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One end of an optical cable segment: the pipe that terminates the segment and the face it
 * looks through to reach the machine holding the capability.
 * <p>
 * The distance is measured from the segment's source (the machine that injects the signal),
 * not from the pipe the query came from. Once the distance exceeds the cable material's
 * decay distance the signal is spent and this path no longer carries anything.
 */
public class OpticalRoutePath implements IRoutePath<TileEntityOpticalPipe> {

    public enum Kind {
        /** Reaches an {@link IOpticalComputationProvider}. */
        COMPUTATION,
        /** Reaches an {@link IOpticalDataAccessHatch}. */
        DATA
    }

    private final TileEntityOpticalPipe targetPipe;
    private final EnumFacing faceToHandler;
    private final int distance;
    private final Kind kind;

    public OpticalRoutePath(TileEntityOpticalPipe targetPipe, EnumFacing faceToHandler, int distance, Kind kind) {
        this.targetPipe = targetPipe;
        this.faceToHandler = faceToHandler;
        this.distance = distance;
        this.kind = kind;
    }

    @NotNull
    @Override
    public TileEntityOpticalPipe getTargetPipe() {
        return targetPipe;
    }

    @NotNull
    @Override
    public EnumFacing getTargetFacing() {
        return faceToHandler;
    }

    /**
     * Distance of the terminating pipe from the segment's source, as counted by the walker
     * (the source pipe itself is {@code 0}).
     */
    public int getDistance() {
        return distance;
    }

    public Kind getKind() {
        return kind;
    }

    @Nullable
    public IOpticalDataAccessHatch getDataHatch() {
        if (kind != Kind.DATA) return null;
        IDataAccessHatch dataAccessHatch = getTargetCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS);
        return dataAccessHatch instanceof IOpticalDataAccessHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public IOpticalComputationProvider getComputationHatch() {
        if (kind != Kind.COMPUTATION) return null;
        return getTargetCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER);
    }
}
