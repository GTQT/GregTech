package gregtech.common.pipelike.fiber.tile;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IFiberLightReceiver;
import gregtech.api.capability.IFiberLightSource;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.api.util.TaskScheduler;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberLight;
import gregtech.common.pipelike.fiber.FiberLightCommand;
import gregtech.common.pipelike.fiber.FiberPipeProperties;
import gregtech.common.pipelike.fiber.FiberPipeType;
import gregtech.common.pipelike.fiber.net.FiberNetHandler;
import gregtech.common.pipelike.fiber.net.FiberPipeNet;
import gregtech.common.pipelike.fiber.net.WorldFiberPipeNet;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public class TileEntityFiberPipe extends TileEntityPipeBase<FiberPipeType, FiberPipeProperties> {

    private final EnumMap<EnumFacing, FiberNetHandler> handlers = new EnumMap<>(EnumFacing.class);
    // the FiberNetHandler can only be created on the server, so we have an empty placeholder for the client
    private final IFiberLightSource clientLightSource = new DefaultLightSource();
    private final IFiberLightReceiver clientLightReceiver = new DefaultLightReceiver();
    private WeakReference<FiberPipeNet> currentPipeNet = new WeakReference<>(null);
    private FiberNetHandler defaultHandler;

    private int ticksActive = 0;
    private boolean isActive;

    /** How long a transmitted color stays lit after the light stops, in ticks. */
    public static final int COLOR_HOLD_TICKS = 20;

    /** Color currently travelling down the line, or {@code null} when the line is dark. */
    private FiberColor transmittedColor;
    /** Ticks left before {@link #transmittedColor} is dropped. */
    private int colorHold;

    @Override
    public Class<FiberPipeType> getPipeTypeClass() {
        return FiberPipeType.class;
    }

    @Override
    public boolean supportsTicking() {
        return false;
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    private void initHandlers() {
        FiberPipeNet net = getFiberPipeNet();
        if (net == null) return;
        for (EnumFacing facing : EnumFacing.VALUES) {
            handlers.put(facing, new FiberNetHandler(net, this, facing));
        }
        defaultHandler = new FiberNetHandler(net, this, null);
    }

    @Nullable
    @Override
    public <T> T getCapabilityInternal(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE.cast(clientLightSource);
            }
            if (handlers.isEmpty()) initHandlers();
            checkNetwork();
            return GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE
                    .cast(handlers.getOrDefault(facing, defaultHandler));
        }

        if (capability == GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER.cast(clientLightReceiver);
            }
            if (handlers.isEmpty()) initHandlers();
            checkNetwork();
            return GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER
                    .cast(handlers.getOrDefault(facing, defaultHandler));
        }
        return super.getCapabilityInternal(capability, facing);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            FiberPipeNet current = getFiberPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (FiberNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    public FiberPipeNet getFiberPipeNet() {
        if (world == null || world.isRemote) return null;
        FiberPipeNet currentPipeNet = this.currentPipeNet.get();
        if (currentPipeNet != null && currentPipeNet.isValid() && currentPipeNet.containsNode(getPipePos())) {
            return currentPipeNet;
        }
        WorldFiberPipeNet worldNet = (WorldFiberPipeNet) getPipeBlock().getWorldPipeNet(getPipeWorld());
        currentPipeNet = worldNet.getNetFromPos(getPipePos());
        if (currentPipeNet != null) {
            this.currentPipeNet = new WeakReference<>(currentPipeNet);
        }
        return currentPipeNet;
    }

    @Override
    public void transferDataFrom(IPipeTile<FiberPipeType, FiberPipeProperties> tileEntity) {
        super.transferDataFrom(tileEntity);
        if (getFiberPipeNet() == null) return;

        TileEntityFiberPipe pipe = (TileEntityFiberPipe) tileEntity;
        if (!pipe.handlers.isEmpty() && pipe.defaultHandler != null) {
            handlers.clear();
            handlers.putAll(pipe.handlers);
            defaultHandler = pipe.defaultHandler;
            checkNetwork();
        } else {
            initHandlers();
        }
    }

    @Override
    public void setConnection(EnumFacing side, boolean connected, boolean fromNeighbor) {
        if (!getWorld().isRemote && connected && !fromNeighbor) {
            // never allow more than two connections total: fiber does not branch
            if (getNumConnections() >= 2) return;

            var tile = getWorld().getTileEntity(getPos().offset(side));
            if (tile instanceof IPipeTile<?, ?> pipeTile &&
                    pipeTile.getPipeType().getClass() == this.getPipeType().getClass()) {
                if (pipeTile.getNumConnections() >= 2) return;
            }
        }
        super.setConnection(side, connected, fromNeighbor);
    }

    /**
     * Records the colour currently being carried, so the pipe can be rendered in it.
     * <p>
     * The colour is held for {@link #COLOR_HOLD_TICKS} ticks after the last call, which keeps the
     * line lit for a moment instead of flickering when the light is sent in bursts.
     */
    public void setTransmittedColor(@NotNull FiberColor color) {
        if (this.transmittedColor == color && this.colorHold > 0) {
            this.colorHold = COLOR_HOLD_TICKS;
            return;
        }
        boolean wasDark = this.transmittedColor == null;
        this.transmittedColor = color;
        this.colorHold = COLOR_HOLD_TICKS;
        writeCustomData(GregtechDataCodes.PIPE_FIBER_COLOR, buf -> buf.writeVarInt(color.getDyeMeta()));
        scheduleChunkForRenderUpdate();

        if (wasDark) {
            // one task per lit period, not per light packet
            TaskScheduler.scheduleTask(getWorld(), () -> {
                if (--this.colorHold > 0) return true;
                this.colorHold = 0;
                this.transmittedColor = null;
                writeCustomData(GregtechDataCodes.PIPE_FIBER_COLOR, buf -> buf.writeVarInt(-1));
                scheduleChunkForRenderUpdate();
                return false;
            });
        }
    }

    /** Color travelling down this link, or {@code null} when it is dark. */
    @Nullable
    public FiberColor getTransmittedColor() {
        return transmittedColor;
    }

    public boolean isActive() {
        return this.isActive;
    }

    /**
     * @param active   if the line should light up
     * @param duration how long it should stay lit
     */
    public void setActive(boolean active, int duration) {
        boolean stateChanged = false;
        if (this.isActive && !active) {
            this.isActive = false;
            stateChanged = true;
        } else if (!this.isActive && active) {
            this.isActive = true;
            stateChanged = true;
            TaskScheduler.scheduleTask(getWorld(), () -> {
                if (++this.ticksActive % duration == 0) {
                    this.ticksActive = 0;
                    setActive(false, -1);
                    return false;
                }
                return true;
            });
        }

        if (stateChanged) {
            writeCustomData(GregtechDataCodes.PIPE_OPTICAL_ACTIVE, buf -> buf.writeBoolean(this.isActive));
            notifyBlockUpdate();
            markDirty();
        }
    }

    @Override
    public void receiveCustomData(int discriminator, PacketBuffer buf) {
        super.receiveCustomData(discriminator, buf);
        if (discriminator == GregtechDataCodes.PIPE_OPTICAL_ACTIVE) {
            this.isActive = buf.readBoolean();
            scheduleChunkForRenderUpdate();
        } else if (discriminator == GregtechDataCodes.PIPE_FIBER_COLOR) {
            int meta = buf.readVarInt();
            this.transmittedColor = meta < 0 ? null : FiberColor.byDyeMeta(meta);
            scheduleChunkForRenderUpdate();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        this.handlers.clear();
    }

    /** Client-side placeholder: fiber light is only resolved on the server. */
    private static class DefaultLightSource implements IFiberLightSource {

        @Override
        public boolean receiveLightCommand(@NotNull FiberLightCommand command) {
            return false;
        }

        @NotNull
        @Override
        public FiberLight getEmittedLight() {
            return FiberLight.NONE;
        }
    }

    /** Client-side placeholder: fiber light is only resolved on the server. */
    private static class DefaultLightReceiver implements IFiberLightReceiver {

        @Override
        public boolean acceptLight(@NotNull FiberLight light) {
            return false;
        }

        @NotNull
        @Override
        public FiberLight getAcceptedLight() {
            return FiberLight.NONE;
        }

        @Nullable
        @Override
        public FiberColor getAcceptedColor() {
            return null;
        }

        @Nullable
        @Override
        public IntensityBounds getIntensityBounds() {
            return null;
        }
    }
}
