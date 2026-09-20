package gregtech.common.pipelike.fiber.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IFiberLightReceiver;
import gregtech.api.capability.IFiberLightSource;
import gregtech.api.pipenet.IRoutePath;
import gregtech.common.pipelike.fiber.tile.TileEntityFiberPipe;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One end of a fiber optic line: the last link and the face that looks at the machine attached
 * there. Fiber does not attenuate, so the distance is informational only.
 */
public class FiberRoutePath implements IRoutePath<TileEntityFiberPipe> {

    /** Which capability the machine at this end carries. */
    public enum Kind {
        /** An {@link IFiberLightSource}: light enters the line here. */
        SOURCE,
        /** An {@link IFiberLightReceiver}: light leaves the line here. */
        RECEIVER
    }

    private final TileEntityFiberPipe targetPipe;
    private final EnumFacing faceToHandler;
    private final int distance;
    private final Kind kind;

    public FiberRoutePath(TileEntityFiberPipe targetPipe, EnumFacing faceToHandler, int distance, Kind kind) {
        this.targetPipe = targetPipe;
        this.faceToHandler = faceToHandler;
        this.distance = distance;
        this.kind = kind;
    }

    @NotNull
    @Override
    public TileEntityFiberPipe getTargetPipe() {
        return targetPipe;
    }

    @NotNull
    @Override
    public EnumFacing getTargetFacing() {
        return faceToHandler;
    }

    /** How many links the light travelled to reach this end. Informational: there is no decay. */
    public int getDistance() {
        return distance;
    }

    public Kind getKind() {
        return kind;
    }

    @Nullable
    public IFiberLightSource getLightSource() {
        if (kind != Kind.SOURCE) return null;
        return getTargetCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE);
    }

    @Nullable
    public IFiberLightReceiver getLightReceiver() {
        if (kind != Kind.RECEIVER) return null;
        return getTargetCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER);
    }
}
