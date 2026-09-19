package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalDataAccessHatch;
import gregtech.api.recipes.Recipe;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class OpticalNetHandler implements IDataAccessHatch, IOpticalComputationProvider {

    private final TileEntityOpticalPipe pipe;
    private final World world;
    private final EnumFacing facing;

    private OpticalPipeNet net;

    public OpticalNetHandler(OpticalPipeNet net, @NotNull TileEntityOpticalPipe pipe, @Nullable EnumFacing facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
        this.world = pipe.getWorld();
    }

    public void updateNetwork(OpticalPipeNet net) {
        this.net = net;
    }

    public OpticalPipeNet getNet() {
        return net;
    }

    /**
     * How much CWU/t a single cable of this pipe's material can physically forward.
     */
    public int getMaxCWUt() {
        return pipe.getOpticalProperties().getMaxCWUt();
    }

    @Override
    public boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
        boolean isAvailable = traverseRecipeAvailable(recipe, seen);
        if (isAvailable) setPipesActive();
        return isAvailable;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        // the cable material caps how much computation a single line can carry
        int cableLimit = getMaxCWUt();
        int requested = Math.min(cwut, cableLimit);
        int provided = traverseRequestCWUt(requested, simulate, seen);
        if (provided > 0) setPipesActive();
        return Math.min(provided, cableLimit);
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        return traverseMaxCWUt(seen);
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        return traverseCanBridge(seen);
    }

    private void setPipesActive() {
        for (BlockPos pos : net.getAllNodes().keySet()) {
            if (world.getTileEntity(pos) instanceof TileEntityOpticalPipe opticalPipe) {
                opticalPipe.setActive(true, 100);
            }
        }
    }

    private boolean isNetInvalidForTraversal() {
        return net == null || pipe == null || pipe.isInvalid();
    }

    /** The route this handler resolves to, or {@code null} if there is nothing on the other end. */
    @Nullable
    private OpticalRoutePath getRoute(OpticalRoutePath.Kind kind) {
        if (isNetInvalidForTraversal()) return null;
        // decay is already enforced when the route is recorded: the walker refuses to reach past
        // the material's decay distance, so a cached route is always within reach
        return net.getNetData(pipe.getPipePos(), facing, kind);
    }

    private boolean traverseRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
        OpticalRoutePath route = getRoute(OpticalRoutePath.Kind.DATA);
        if (route == null) return false;

        IOpticalDataAccessHatch hatch = route.getDataHatch();
        if (hatch == null) return false;

        // a receiver hatch asks the attached transmitter hatch whether it can supply the recipe
        if (hatch.isTransmitter()) {
            return hatch.isRecipeAvailable(recipe, seen);
        }
        return false;
    }

    private int traverseRequestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return 0;
        return provider.requestCWUt(cwut, simulate, seen);
    }

    private int traverseMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return 0;
        return provider.getMaxCWUt(seen);
    }

    private boolean traverseCanBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return true; // nothing found, so don't report a problem, just pass quietly
        return provider.canBridge(seen);
    }

    @Nullable
    private IOpticalComputationProvider getComputationProvider(@NotNull Collection<IOpticalComputationProvider> seen) {
        OpticalRoutePath route = getRoute(OpticalRoutePath.Kind.COMPUTATION);
        if (route == null) return null;

        IOpticalComputationProvider hatch = route.getComputationHatch();
        if (hatch == null || seen.contains(hatch)) return null;
        return hatch;
    }
}
