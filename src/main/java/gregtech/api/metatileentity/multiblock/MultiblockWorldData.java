package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.MultiPiecePattern;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-world facade for structure dirty indexing.
 *
 * <p>World callbacks record dirty controllers/pieces/chunks through this class.
 * The scheduler consumes the dirty state later; block-change callbacks must not
 * run structure checks directly.
 */
public class MultiblockWorldData {

    // Thread-safe: Forge events may fire from non-main threads (e.g., async chunk loading)
    private static final Map<World, MultiblockWorldData> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final StructureWorldIndex structureIndex = new StructureWorldIndex();

    public static MultiblockWorldData get(World world) {
        return INSTANCES.computeIfAbsent(world, w -> new MultiblockWorldData());
    }

    public static void remove(World world) {
        INSTANCES.remove(world);
    }

    /**
     * Register a formed multiblock's block positions for event-driven checking.
     * Called when a multiblock successfully forms.
     *
     * @param controller the multiblock controller
     * @param positions  the set of block positions (as longs) in the structure
     */
    public void registerMultiblock(MultiblockControllerBase controller, LongSet positions) {
        structureIndex.registerMultiblock(controller, positions);
    }

    /**
     * Register a formed multiblock with multi-piece pattern for piece-level dirty tracking.
     * The combined positions from all active pieces are used for the chunk index.
     *
     * @param controller   the multiblock controller
     * @param positions    the combined set of block positions (as longs) across all pieces
     * @param piecePattern the multi-piece pattern for piece-level dirty marking
     */
    public void registerMultiblock(MultiblockControllerBase controller, LongSet positions,
                                   MultiPiecePattern piecePattern) {
        structureIndex.registerMultiblock(controller, positions, piecePattern);
    }

    /**
     * Unregister a multiblock when it invalidates.
     * Called when a multiblock structure breaks.
     *
     * @param controller the multiblock controller
     */
    public void unregisterMultiblock(MultiblockControllerBase controller) {
        structureIndex.unregisterMultiblock(controller);
    }

    /**
     * Called when a block changes in the world.
     * Checks if any formed multiblock has this position registered
     * and marks it for re-validation.
     *
     * For multi-piece controllers, only the affected piece(s) are marked dirty
     * instead of the entire controller (P3 piece-level dirty tracking).
     *
     * @param pos      the position where a block changed
     * @param gameTick the current server tick (from {@code world.getTotalWorldTime()})
     * @return true if at least one registered multiblock was affected by this position
     */
    public boolean onBlockChanged(BlockPos pos, long gameTick) {
        return structureIndex.markBlockChanged(pos, gameTick);
    }

    /**
     * Capture the change revisions for every chunk touched by a snapshot AABB.
     */
    ChangeSnapshot captureChangeSnapshot(BlockPos minCorner, BlockPos maxCorner) {
        return new ChangeSnapshot(structureIndex.captureChangeSnapshot(minCorner, maxCorner));
    }

    boolean isChangeSnapshotCurrent(ChangeSnapshot snapshot) {
        return structureIndex.isChangeSnapshotCurrent(snapshot.delegate);
    }

    /**
     * Consume pending dirty state for scheduler selection.
     */
    @NotNull
    DirtyCheckDecision consumeDirtyCheck(MultiblockControllerBase controller, long currentTick) {
        return new DirtyCheckDecision(structureIndex.consumeDirtyCheck(controller, currentTick));
    }

    /**
     * @deprecated Use {@link #consumeDirtyCheck(MultiblockControllerBase, long)}
     *             so the scheduler can choose between active-graph and full checks.
     */
    @Deprecated
    public boolean hasPendingRecheck(MultiblockControllerBase controller, long currentTick) {
        return consumeDirtyCheck(controller, currentTick).shouldCheck();
    }

    /**
     * Check if a controller is registered for event-driven checking.
     *
     * @param controller the controller to check
     * @return true if the controller is registered
     */
    public boolean isRegistered(MultiblockControllerBase controller) {
        return structureIndex.isRegistered(controller);
    }

    /**
     * Get all registered positions for a controller.
     *
     * @param controller the controller
     * @return the set of positions, or empty set if not registered
     */
    public LongSet getPositions(MultiblockControllerBase controller) {
        return structureIndex.getPositions(controller);
    }

    /**
     * Clear all data. Called when a world is unloaded.
     */
    public void clear() {
        structureIndex.clear();
    }

    static final class ChangeSnapshot {

        private final StructureWorldIndex.ChangeSnapshot delegate;

        private ChangeSnapshot(@NotNull StructureWorldIndex.ChangeSnapshot delegate) {
            this.delegate = delegate;
        }
    }

    static final class DirtyCheckDecision {

        private final StructureWorldIndex.DirtyCheckDecision delegate;

        private DirtyCheckDecision(@NotNull StructureWorldIndex.DirtyCheckDecision delegate) {
            this.delegate = delegate;
        }

        boolean isRegistered() {
            return delegate.isRegistered();
        }

        boolean shouldCheck() {
            return delegate.shouldCheck();
        }

        boolean shouldCheckActiveGraph() {
            return delegate.shouldCheckActiveGraph();
        }

        @NotNull
        String describeAction() {
            return delegate.getAction().name();
        }

        long getLastChangedTick() {
            return delegate.getLastChangedTick();
        }
    }

    /**
     * Suppress event-driven recheck notifications for the given controller.
     * While suppressed, block changes within the controller's registered positions
     * will be silently ignored. Call {@link #unsuppressRecheck} after the modification
     * is complete.
     *
     * @param controller the controller to suppress
     */
    public void suppressRecheck(MultiblockControllerBase controller) {
        structureIndex.suppressRecheck(controller);
    }

    /**
     * Re-enable event-driven recheck notifications for the given controller.
     * Also clears any pending recheck that may have been queued before suppression.
     *
     * @param controller the controller to unsuppress
     */
    public void unsuppressRecheck(MultiblockControllerBase controller) {
        structureIndex.unsuppressRecheck(controller);
    }
}
