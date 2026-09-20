package gregtech.common.pipelike.fiber.net;

import gregtech.api.capability.IFiberLightReceiver;
import gregtech.api.capability.IFiberLightSource;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberLight;
import gregtech.common.pipelike.fiber.FiberLightCommand;
import gregtech.common.pipelike.fiber.tile.TileEntityFiberPipe;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The capability handed out by a fiber link.
 * <p>
 * Asking a link for the source capability returns the emitter on the far end of the line; asking
 * it for the receiver capability returns the machine on the far end. Because fiber does not branch
 * and does not attenuate, both ends see exactly the same light.
 */
public class FiberNetHandler implements IFiberLightSource, IFiberLightReceiver {

    private FiberPipeNet net;
    private final TileEntityFiberPipe pipe;
    private final EnumFacing facing;

    public FiberNetHandler(FiberPipeNet net, @NotNull TileEntityFiberPipe pipe, @Nullable EnumFacing facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
    }

    public void updateNetwork(FiberPipeNet net) {
        this.net = net;
    }

    public FiberPipeNet getNet() {
        return net;
    }

    /** Follows the line to the machine at the far end, in the direction asked for. */
    @Nullable
    private FiberRoutePath getRemoteEnd(FiberRoutePath.Kind wanted) {
        if (net == null || pipe == null || pipe.isInvalid() || facing == null) return null;
        return net.getNetData(pipe.getPipePos(), facing, wanted);
    }

    private void setPipesActive() {
        for (BlockPos pos : net.getAllNodes().keySet()) {
            if (pipe.getWorld().getTileEntity(pos) instanceof TileEntityFiberPipe fiber) {
                fiber.setActive(true, 100);
            }
        }
    }

    /** Lights the whole line up in the colour currently being carried. */
    private void setPipesColor(@NotNull FiberColor color) {
        for (BlockPos pos : net.getAllNodes().keySet()) {
            if (pipe.getWorld().getTileEntity(pos) instanceof TileEntityFiberPipe fiber) {
                fiber.setTransmittedColor(color);
            }
        }
    }

    // region source side

    @Override
    public boolean receiveLightCommand(@NotNull FiberLightCommand command) {
        FiberRoutePath route = getRemoteEnd(FiberRoutePath.Kind.SOURCE);
        if (route == null) return false;
        IFiberLightSource source = route.getLightSource();
        if (source == null) return false;
        boolean accepted = source.receiveLightCommand(command);
        if (accepted && !command.isEmpty()) setPipesActive();
        return accepted;
    }

    @NotNull
    @Override
    public FiberLight getEmittedLight() {
        FiberRoutePath route = getRemoteEnd(FiberRoutePath.Kind.SOURCE);
        if (route == null) return FiberLight.NONE;
        IFiberLightSource source = route.getLightSource();
        if (source == null) return FiberLight.NONE;

        FiberLight light = source.getEmittedLight();
        if (!light.isEmpty()) {
            setPipesActive();
            setPipesColor(light.color());
        }
        return light;
    }

    // endregion

    // region receiver side

    @Override
    public boolean acceptLight(@NotNull FiberLight light) {
        IFiberLightReceiver receiver = getFarReceiver();
        if (receiver == null) return false;
        boolean accepted = receiver.acceptLight(light);
        if (accepted) {
            setPipesActive();
            setPipesColor(light.color());
        }
        return accepted;
    }

    @NotNull
    @Override
    public FiberLight getAcceptedLight() {
        IFiberLightReceiver receiver = getFarReceiver();
        return receiver == null ? FiberLight.NONE : receiver.getAcceptedLight();
    }

    @Nullable
    @Override
    public FiberColor getAcceptedColor() {
        IFiberLightReceiver receiver = getFarReceiver();
        return receiver == null ? null : receiver.getAcceptedColor();
    }

    @Nullable
    @Override
    public IntensityBounds getIntensityBounds() {
        IFiberLightReceiver receiver = getFarReceiver();
        return receiver == null ? null : receiver.getIntensityBounds();
    }

    @Nullable
    private IFiberLightReceiver getFarReceiver() {
        FiberRoutePath route = getRemoteEnd(FiberRoutePath.Kind.RECEIVER);
        return route == null ? null : route.getLightReceiver();
    }

    // endregion
}
