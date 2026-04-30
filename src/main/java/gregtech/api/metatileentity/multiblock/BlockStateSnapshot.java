package gregtech.api.metatileentity.multiblock;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A lightweight read-only snapshot of block states in a region of the world.
 * Created on the main thread so that async structure checking can safely read
 * block states without accessing the live World object.
 *
 * <p>Implements {@link IBlockAccess} so it can be used as a drop-in replacement
 * for World in pattern checking logic.
 *
 * @see AsyncStructureChecker
 */
public class BlockStateSnapshot implements IBlockAccess {

    private final Long2ObjectMap<IBlockState> blockStates = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<TileEntity> tileEntities = new Long2ObjectOpenHashMap<>();

    /**
     * Capture a snapshot of a cubic region around a center position.
     * Must be called from the main thread.
     *
     * @param world  the world to snapshot from
     * @param center the center position
     * @param radius the radius in each direction (total size = 2*radius+1 per axis)
     * @return the captured snapshot
     */
    public static BlockStateSnapshot capture(@NotNull World world, @NotNull BlockPos center, int radius) {
        BlockStateSnapshot snapshot = new BlockStateSnapshot();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mpos.setPos(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!world.isBlockLoaded(mpos)) continue;

                    long posLong = mpos.toLong();
                    IBlockState state = world.getBlockState(mpos);
                    snapshot.blockStates.put(posLong, state);

                    TileEntity te = world.getTileEntity(mpos);
                    if (te != null) {
                        snapshot.tileEntities.put(posLong, te);
                    }
                }
            }
        }
        return snapshot;
    }

    /**
     * Capture a snapshot covering only the positions specified in the given set.
     * More efficient than cubic capture for large sparse structures.
     *
     * @param world     the world to snapshot from
     * @param positions the specific positions to capture (as longs)
     * @return the captured snapshot
     */
    public static BlockStateSnapshot capturePositions(@NotNull World world,
                                                      @NotNull Iterable<Long> positions) {
        BlockStateSnapshot snapshot = new BlockStateSnapshot();
        for (long posLong : positions) {
            BlockPos pos = BlockPos.fromLong(posLong);
            if (!world.isBlockLoaded(pos)) continue;

            IBlockState state = world.getBlockState(pos);
            snapshot.blockStates.put(posLong, state);

            TileEntity te = world.getTileEntity(pos);
            if (te != null) {
                snapshot.tileEntities.put(posLong, te);
            }
        }
        return snapshot;
    }

    /**
     * Capture a snapshot of a rectangular region defined by min/max corners.
     *
     * @param world the world to snapshot from
     * @param min   the minimum corner (inclusive)
     * @param max   the maximum corner (inclusive)
     * @return the captured snapshot
     */
    public static BlockStateSnapshot captureRegion(@NotNull World world,
                                                    @NotNull BlockPos min, @NotNull BlockPos max) {
        BlockStateSnapshot snapshot = new BlockStateSnapshot();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;

                    long posLong = mpos.toLong();
                    IBlockState state = world.getBlockState(mpos);
                    snapshot.blockStates.put(posLong, state);

                    TileEntity te = world.getTileEntity(mpos);
                    if (te != null) {
                        snapshot.tileEntities.put(posLong, te);
                    }
                }
            }
        }
        return snapshot;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(@NotNull BlockPos pos) {
        return tileEntities.get(pos.toLong());
    }

    @Override
    @NotNull
    public IBlockState getBlockState(@NotNull BlockPos pos) {
        IBlockState state = blockStates.get(pos.toLong());
        if (state == null) {
            return net.minecraft.init.Blocks.AIR.getDefaultState();
        }
        return state;
    }

    @Override
    public int getCombinedLight(@NotNull BlockPos pos, int lightValue) {
        return 15 << 20 | lightValue << 4;
    }

    @Override
    public boolean isAirBlock(@NotNull BlockPos pos) {
        IBlockState state = getBlockState(pos);
        return state.getBlock().isAir(state, this, pos);
    }

    @Override
    public net.minecraft.world.biome.Biome getBiome(@NotNull BlockPos pos) {
        return net.minecraft.init.Biomes.PLAINS;
    }

    @Override
    public int getStrongPower(@NotNull BlockPos pos, @NotNull net.minecraft.util.EnumFacing direction) {
        return 0;
    }

    @Override
    public net.minecraft.world.WorldType getWorldType() {
        return net.minecraft.world.WorldType.DEFAULT;
    }

    @Override
    public boolean isSideSolid(@NotNull BlockPos pos, @NotNull net.minecraft.util.EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    /**
     * @return true if this snapshot contains data for the given position
     */
    public boolean hasPosition(BlockPos pos) {
        return blockStates.containsKey(pos.toLong());
    }

    /**
     * @return the number of block positions captured
     */
    public int size() {
        return blockStates.size();
    }
}
