package gregtech.common.pipelike.optical.tile;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.pipenet.block.material.TileEntityMaterialPipeBase;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.recipes.Recipe;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.api.util.TaskScheduler;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.OpticalNetHandler;
import gregtech.common.pipelike.optical.net.OpticalPipeNet;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.EnumMap;

public class TileEntityOpticalPipe extends TileEntityMaterialPipeBase<OpticalPipeType, OpticalCableProperties> {

    private final EnumMap<EnumFacing, OpticalNetHandler> handlers = new EnumMap<>(EnumFacing.class);
    // the OpticalNetHandler can only be created on the server, so we have an empty placeholder for the client
    private final IDataAccessHatch clientDataHandler = new DefaultDataHandler();
    private final IOpticalComputationProvider clientComputationHandler = new DefaultComputationHandler();
    private WeakReference<OpticalPipeNet> currentPipeNet = new WeakReference<>(null);
    private OpticalNetHandler defaultHandler;

    private int ticksActive = 0;
    private boolean isActive;

    @Override
    public Class<OpticalPipeType> getPipeTypeClass() {
        return OpticalPipeType.class;
    }

    @Override
    public boolean supportsTicking() {
        return false;
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    /** The properties of this cable, as decided by its material. */
    public OpticalCableProperties getOpticalProperties() {
        return getNodeData();
    }

    /**
     * How much of the optical signal is left at this exact cable, as a fraction in {@code (0, 1]},
     * decaying linearly from the end the signal is injected at.
     *
     * @return {@code 0} when nothing is attached or the whole span is longer than the material's
     *         decay distance, i.e. no signal reaches this cable at all
     */
    public double getSignalStrength() {
        if (world == null || world.isRemote) return 0.0d;

        int decayDistance = getOpticalProperties().getDecayDistance();
        int toA = walkToEndpoint(EnumFacing.DOWN, decayDistance);
        int toB = walkToEndpoint(EnumFacing.UP, decayDistance);
        if (toA < 0 || toB < 0) return 0.0d;

        // the whole span has to fit inside the material's reach
        if (toA > decayDistance || toB > decayDistance) return 0.0d;

        // distance from whichever end the signal entered at; both ends count block for block
        int distanceFromSource = Math.max(1, Math.min(toA, toB));
        return getOpticalProperties().getSignalStrength(distanceFromSource);
    }

    /**
     * Walks the cable away from this pipe, starting towards {@code startFacing}, until it reaches a
     * machine carrying an optical capability.
     *
     * @return the distance to that machine in blocks, or {@code -1} when the line ends without one
     *         or runs past {@code limit}
     */
    private int walkToEndpoint(EnumFacing startFacing, int limit) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(getPos());
        for (int distance = 1; distance <= limit + 1; distance++) {
            cursor.move(startFacing);
            TileEntity tile = world.getTileEntity(cursor);
            if (tile instanceof TileEntityOpticalPipe) {
                EnumFacing next = nextCableFacing((TileEntityOpticalPipe) tile, startFacing.getOpposite());
                if (next == null) return -1; // dead end
                startFacing = next;
                continue;
            }
            if (tile == null) return -1;
            EnumFacing capSide = startFacing.getOpposite();
            if (tile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, capSide) ||
                    tile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, capSide)) {
                return distance;
            }
            return -1;
        }
        return -1;
    }

    /**
     * @return the facing that continues the cable out of {@code pipe}, skipping the side we came
     *         from, or {@code null} when this pipe is the end of the line.
     */
    @Nullable
    private static EnumFacing nextCableFacing(@NotNull TileEntityOpticalPipe pipe, @NotNull EnumFacing cameFrom) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == cameFrom || !pipe.isConnected(facing)) continue;
            if (pipe.getNeighbor(facing) instanceof TileEntityOpticalPipe) return facing;
        }
        return null;
    }

    private void initHandlers() {
        OpticalPipeNet net = getOpticalPipeNet();
        if (net == null) return;
        for (EnumFacing facing : EnumFacing.VALUES) {
            handlers.put(facing, new OpticalNetHandler(net, this, facing));
        }
        defaultHandler = new OpticalNetHandler(net, this, null);
    }

    @Nullable
    @Override
    public <T> T getCapabilityInternal(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechTileCapabilities.CAPABILITY_DATA_ACCESS) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CAPABILITY_DATA_ACCESS.cast(clientDataHandler);
            }

            if (handlers.isEmpty()) initHandlers();

            checkNetwork();
            return GregtechTileCapabilities.CAPABILITY_DATA_ACCESS.cast(handlers.getOrDefault(facing, defaultHandler));
        }

        if (capability == GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER) {
            if (world.isRemote) {
                return GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER.cast(clientComputationHandler);
            }

            if (handlers.isEmpty()) initHandlers();

            checkNetwork();
            return GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER
                    .cast(handlers.getOrDefault(facing, defaultHandler));
        }
        return super.getCapabilityInternal(capability, facing);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            OpticalPipeNet current = getOpticalPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (OpticalNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    public OpticalPipeNet getOpticalPipeNet() {
        if (world == null || world.isRemote)
            return null;
        OpticalPipeNet currentPipeNet = this.currentPipeNet.get();
        if (currentPipeNet != null && currentPipeNet.isValid() && currentPipeNet.containsNode(getPipePos()))
            return currentPipeNet; // if current net is valid and does contain position, return it
        WorldOpticalPipeNet worldNet = (WorldOpticalPipeNet) getPipeBlock().getWorldPipeNet(getPipeWorld());
        currentPipeNet = worldNet.getNetFromPos(getPipePos());
        if (currentPipeNet != null) {
            this.currentPipeNet = new WeakReference<>(currentPipeNet);
        }
        return currentPipeNet;
    }

    @Override
    public void transferDataFrom(IPipeTile<OpticalPipeType, OpticalCableProperties> tileEntity) {
        super.transferDataFrom(tileEntity);
        if (getOpticalPipeNet() == null)
            return;
        TileEntityOpticalPipe pipe = (TileEntityOpticalPipe) tileEntity;
        if (!pipe.handlers.isEmpty() && pipe.defaultHandler != null) {
            // take handlers from old pipe
            handlers.clear();
            handlers.putAll(pipe.handlers);
            defaultHandler = pipe.defaultHandler;
            checkNetwork();
        } else {
            // create new handlers
            initHandlers();
        }
    }

    @Override
    public void setConnection(EnumFacing side, boolean connected, boolean fromNeighbor) {
        if (!getWorld().isRemote && connected && !fromNeighbor) {
            // never allow more than two connections total
            if (getNumConnections() >= 2) return;

            // also check the other pipe
            var tile = getWorld().getTileEntity(getPos().offset(side));
            if (tile instanceof IPipeTile<?, ?> pipeTile &&
                    pipeTile.getPipeType().getClass() == this.getPipeType().getClass()) {
                if (pipeTile.getNumConnections() >= 2) return;
            }
        }
        super.setConnection(side, connected, fromNeighbor);
    }

    public boolean isActive() {
        return this.isActive;
    }

    /**
     * @param active   if the pipe should become active
     * @param duration how long the pipe should be active for
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
            writeCustomData(GregtechDataCodes.PIPE_OPTICAL_ACTIVE, buf -> {
                buf.writeBoolean(this.isActive);
            });
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
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        this.handlers.clear();
    }

    private static class DefaultDataHandler implements IDataAccessHatch {

        @Override
        public boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private static class DefaultComputationHandler implements IOpticalComputationProvider {

        @Override
        public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
            return 0;
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            return 0;
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            // nothing found, so don't report a problem, just pass quietly
            return true;
        }
    }
}
