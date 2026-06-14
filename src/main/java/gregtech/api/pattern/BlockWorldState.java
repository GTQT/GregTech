package gregtech.api.pattern;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockWorldState {

    protected World world;
    protected IBlockAccess blockAccess;
    protected BlockPos pos;
    protected IBlockState state;
    protected TileEntity tileEntity;
    protected boolean tileEntityInitialized;
    protected PatternMatchContext matchContext;
    protected Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
    protected Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount;
    @Nullable
    protected TraceabilityPredicate predicate;
    protected StructureElementPreviewEntry previewEntry;
    protected PatternError error;

    public void update(World worldIn, BlockPos posIn, PatternMatchContext matchContext,
                       Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                       Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount,
                       @Nullable TraceabilityPredicate predicate) {
        this.world = worldIn;
        this.blockAccess = worldIn;
        this.pos = posIn;
        this.state = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
        this.matchContext = matchContext;
        this.globalCount = globalCount;
        this.layerCount = layerCount;
        this.predicate = predicate;
        this.previewEntry = null;
        this.error = null;
    }

    public void update(World worldIn, BlockPos posIn, PatternMatchContext matchContext,
                       Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                       Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount,
                       @Nullable TraceabilityPredicate predicate,
                       @NotNull StructureElementPreviewEntry previewEntry) {
        update(worldIn, posIn, matchContext, globalCount, layerCount, predicate);
        this.previewEntry = previewEntry;
    }

    /**
     * Update using an IBlockAccess (snapshot) instead of a live World.
     * Used for async pattern checking where World access is not thread-safe.
     */
    public void updateFromBlockAccess(IBlockAccess blockAccessIn, BlockPos posIn, PatternMatchContext matchContext,
                                      Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                                      Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount,
                                      @Nullable TraceabilityPredicate predicate) {
        this.world = null;
        this.blockAccess = blockAccessIn;
        this.pos = posIn;
        this.state = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
        this.matchContext = matchContext;
        this.globalCount = globalCount;
        this.layerCount = layerCount;
        this.predicate = predicate;
        this.previewEntry = null;
        this.error = null;
    }

    public void updateFromBlockAccess(IBlockAccess blockAccessIn, BlockPos posIn, PatternMatchContext matchContext,
                                      Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                                      Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount,
                                      @Nullable TraceabilityPredicate predicate,
                                      @NotNull StructureElementPreviewEntry previewEntry) {
        updateFromBlockAccess(blockAccessIn, posIn, matchContext, globalCount, layerCount, predicate);
        this.previewEntry = previewEntry;
    }

    public boolean hasError() {
        return error != null;
    }

    public void setError(PatternError error) {
        this.error = error;
        if (error != null) {
            error.setWorldState(this);
        }
    }

    public PatternMatchContext getMatchContext() {
        return matchContext;
    }

    @Nullable
    public StructureElementPreviewEntry getPreviewEntry() {
        return previewEntry;
    }

    public boolean transaction(Predicate<BlockWorldState> action) {
        return transactionValue(action::test, Boolean.TRUE::equals);
    }

    public boolean transaction(Supplier<Boolean> action) {
        return transactionValue(ignored -> action.get(), Boolean.TRUE::equals);
    }

    public void transactionAction(Consumer<BlockWorldState> action) {
        transactionValue(state -> {
            action.accept(state);
            return Boolean.TRUE;
        }, Boolean.TRUE::equals);
    }

    public <T> T transactionValue(Function<BlockWorldState, T> action, Predicate<T> commitPredicate) {
        Checkpoint checkpoint = checkpoint();
        try {
            T result = action.apply(this);
            if (!commitPredicate.test(result)) {
                restoreTo(checkpoint);
            }
            return result;
        } catch (RuntimeException | Error e) {
            restoreTo(checkpoint);
            throw e;
        }
    }

    public boolean probe(Predicate<BlockWorldState> action) {
        return probeValue(action::test);
    }

    public boolean probe(Supplier<Boolean> action) {
        return probeValue(ignored -> action.get());
    }

    public void probeAction(Consumer<BlockWorldState> action) {
        probeValue(state -> {
            action.accept(state);
            return null;
        });
    }

    public <T> T probeValue(Function<BlockWorldState, T> action) {
        Checkpoint checkpoint = checkpoint();
        try {
            return action.apply(this);
        } finally {
            restoreTo(checkpoint);
        }
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(this);
    }

    public void restoreTo(Checkpoint checkpoint) {
        matchContext.restore(checkpoint.context);
        globalCount.clear();
        globalCount.putAll(checkpoint.globalCount);
        layerCount.clear();
        layerCount.putAll(checkpoint.layerCount);
    }

    public IBlockState getBlockState() {
        if (this.state == null) {
            this.state = this.blockAccess.getBlockState(this.pos);
        }

        return this.state;
    }

    @Nullable
    public IBlockState getCachedBlockState() {
        return state;
    }

    @Nullable
    public TileEntity getTileEntity() {
        if (this.tileEntity == null && !this.tileEntityInitialized) {
            this.tileEntity = this.blockAccess.getTileEntity(this.pos);
            this.tileEntityInitialized = true;
        }

        return this.tileEntity;
    }

    public BlockPos getPos() {
        return this.pos.toImmutable();
    }

    public IBlockState getOffsetState(EnumFacing face) {
        if (pos instanceof MutableBlockPos) {
            ((MutableBlockPos) pos).move(face);
            IBlockState blockState = blockAccess.getBlockState(pos);
            ((MutableBlockPos) pos).move(face.getOpposite());
            return blockState;
        }
        return blockAccess.getBlockState(this.pos.offset(face));
    }

    /**
     * @return the world instance, or null if using a snapshot (async mode)
     */
    @Nullable
    public World getWorld() {
        return world;
    }

    /**
     * @return the block access (either World or snapshot)
     */
    public IBlockAccess getBlockAccess() {
        return blockAccess;
    }

    public static final class Checkpoint {

        private final PatternMatchContext.Checkpoint context;
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount;

        private Checkpoint(BlockWorldState blockWorldState) {
            this.context = blockWorldState.getMatchContext().checkpoint();
            this.globalCount = new HashMap<>(blockWorldState.globalCount);
            this.layerCount = new HashMap<>(blockWorldState.layerCount);
        }
    }
}
