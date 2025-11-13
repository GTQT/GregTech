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
 * This file contains code modified from the SussyPatches project,
 * which is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0).
 *
 * Original source: https://github.com/MCTian-mi/SussyPatches
 * Original file: src/main/java/dev/tianmi/sussypatches/api/core/mixin/extension/NeighborCacheExtension.java
 * Commit: b0602c9ade30e89a253a85ac3de8817f7a00fa3f
 *
 * Modifications made: [描述您所做的任何修改，如果没有修改则写"None"]
 *
 * This file is also licensed under LGPL-3.0 to comply with the original license terms.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
