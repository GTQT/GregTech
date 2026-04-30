package gregtech.api.pattern;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
    protected TraceabilityPredicate predicate;
    protected PatternError error;

    public void update(World worldIn, BlockPos posIn, PatternMatchContext matchContext,
                       Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                       Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount,
                       TraceabilityPredicate predicate) {
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
        this.error = null;
    }

    /**
     * Update using an IBlockAccess (snapshot) instead of a live World.
     * Used for async pattern checking where World access is not thread-safe.
     */
    public void updateFromBlockAccess(IBlockAccess blockAccessIn, BlockPos posIn, PatternMatchContext matchContext,
                                      Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                                      Map<TraceabilityPredicate.SimplePredicate, Integer> layerCount,
                                      TraceabilityPredicate predicate) {
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
        this.error = null;
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

    public IBlockState getBlockState() {
        if (this.state == null) {
            this.state = this.blockAccess.getBlockState(this.pos);
        }

        return this.state;
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
}
