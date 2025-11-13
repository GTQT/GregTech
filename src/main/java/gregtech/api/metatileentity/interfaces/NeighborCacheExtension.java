package gregtech.api.metatileentity.interfaces;

import gregtech.api.metatileentity.NeighborCacheTileEntityBase;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
/*
 * INeighborCache implementation that uses weak references to cache tile entities.
 * from https://github.com/SymmetricDevs/Supercritical/commit/8615461f693d4cea09ba0d052c15fb6b2ca8deff
 */
public interface NeighborCacheExtension {

    WeakReference<TileEntity> NULL_REFERENCE = new WeakReference<>(null);
    WeakReference<TileEntity> INVALID_REFERENCE = new WeakReference<>(null);

    static NeighborCacheExtension cast(NeighborCacheTileEntityBase neighborCache) {
        return neighborCache;
    }

    /**
     * Checks if the neighbor reference in the given direction is invalid
     */
    boolean isNeighborRefInvalid(EnumFacing facing);

    /**
     * Computes and caches the neighbor in the given direction
     */
    @NotNull
    WeakReference<TileEntity> computeNeighbor(EnumFacing facing);

    /**
     * Gets the cached neighbor reference in the given direction
     */
    @NotNull
    WeakReference<TileEntity> getNeighborRef(EnumFacing facing);

    /**
     * Checks if the adjacent chunk in the given direction is unloaded
     * Helper method from brachy
     */
    static boolean isAdjacentChunkUnloaded(World world, BlockPos pos, EnumFacing facing) {
        int x = pos.getX(), z = pos.getZ();
        int chunkX = x >> 4, chunkZ = z >> 4;
        int nearbyChunkX = (x + facing.getXOffset()) >> 4;
        int nearbyChunkZ = (z + facing.getZOffset()) >> 4;

        // Within the same chunk, no need to check
        if (chunkX == nearbyChunkX && chunkZ == nearbyChunkZ) {
            return false;
        }

        IChunkProvider chunkProvider = world.getChunkProvider();
        return chunkProvider.getLoadedChunk(nearbyChunkX, nearbyChunkZ) == null;
    }
}
