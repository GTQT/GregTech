package gregtech.api.metatileentity;

import gregtech.api.metatileentity.interfaces.INeighborCache;
import gregtech.api.metatileentity.interfaces.NeighborCacheExtension;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
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
public abstract class NeighborCacheTileEntityBase extends SyncedTileEntityBase
        implements INeighborCache, NeighborCacheExtension {

    private final List<WeakReference<TileEntity>> neighbors = Arrays.asList(
            INVALID_REFERENCE, INVALID_REFERENCE, INVALID_REFERENCE,
            INVALID_REFERENCE, INVALID_REFERENCE, INVALID_REFERENCE);
    private boolean neighborsInvalidated = false;

    public NeighborCacheTileEntityBase() {
        invalidateNeighbors();
    }

    protected void invalidateNeighbors() {
        if (!this.neighborsInvalidated) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                this.neighbors.set(facing.getIndex(), INVALID_REFERENCE);
            }
            this.neighborsInvalidated = true;
        }
    }

    @MustBeInvokedByOverriders
    @Override
    public void setWorld(@NotNull World worldIn) {
        super.setWorld(worldIn);
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void setPos(@NotNull BlockPos posIn) {
        super.setPos(posIn);
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void invalidate() {
        super.invalidate();
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        invalidateNeighbors();
    }

    @Override
    @Nullable
    public TileEntity getNeighbor(@NotNull EnumFacing facing) {
        if (world == null || pos == null) {
            return null;
        }

        // If the reference is invalid, compute neighbor, otherwise return the cached TileEntity or null
        WeakReference<TileEntity> neighborRef = isNeighborRefInvalid(facing) ?
                computeNeighbor(facing) : getNeighborRef(facing);
        return neighborRef.get();
    }

    @Override
    public boolean isNeighborRefInvalid(EnumFacing facing) {
        WeakReference<TileEntity> neighborRef = getNeighborRef(facing);

        // Reference is explicitly marked as invalid
        if (neighborRef == INVALID_REFERENCE) {
            return true;
        }

        TileEntity tileEntity = neighborRef.get();

        // Check if adjacent chunk is unloaded for null tile entities
        if (tileEntity == null && NeighborCacheExtension.isAdjacentChunkUnloaded(world, pos, facing)) {
            return true;
        }

        // Check if tile entity is invalid
        return tileEntity != null && tileEntity.isInvalid();
    }

    @NotNull
    @Override
    public WeakReference<TileEntity> computeNeighbor(EnumFacing facing) {
        // Get the actual neighbor tile entity from the world
        TileEntity tileEntity = super.getNeighbor(facing);

        // Avoid creating new references to null tile entities - use the shared NULL_REFERENCE
        WeakReference<TileEntity> neighborRef = (tileEntity == null) ?
                NULL_REFERENCE : new WeakReference<>(tileEntity);

        // Cache the reference
        this.neighbors.set(facing.getIndex(), neighborRef);
        this.neighborsInvalidated = false;

        return neighborRef;
    }

    @NotNull
    @Override
    public WeakReference<TileEntity> getNeighborRef(EnumFacing facing) {
        return this.neighbors.get(facing.getIndex());
    }

    @Override
    public void onNeighborChanged(@NotNull EnumFacing facing) {
        // Mark the neighbor in this direction as invalid, so it will be recomputed next time
        this.neighbors.set(facing.getIndex(), INVALID_REFERENCE);
    }
}
