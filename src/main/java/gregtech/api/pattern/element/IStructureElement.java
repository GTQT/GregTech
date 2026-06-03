package gregtech.api.pattern.element;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Single-position matching rule for structure elements.
 * Each element defines how a single position in a structure piece is matched,
 * previewed, and auto-built.
 */
public interface IStructureElement {

    /**
     * Check if the block at the given position matches this element.
     *
     * @param world   the world
     * @param pos     the block position
     * @param context the pattern match context for storing match results
     * @return true if the block matches
     */
    boolean check(World world, BlockPos pos, PatternMatchContext context);

    /**
     * Get the candidate blocks for preview and auto-build.
     *
     * @return array of candidate BlockInfo, or empty array if not applicable
     */
    BlockInfo[] getCandidates();

    /**
     * Place a block at the given position for auto-build.
     *
     * @param world       the world
     * @param pos         the block position
     * @param context     the pattern match context
     * @param player      the player performing the build
     * @param skipHatches if true, skip hatch placement
     * @return true if a block was placed
     */
    boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                       EntityPlayer player, boolean skipHatches);

    /**
     * Spawn a structure hint at the given position.
     *
     * @param world the world
     * @param pos   the block position
     */
    void spawnHint(World world, BlockPos pos);

    /** Minimum global count for this element (0 = no minimum). */
    default int getMinGlobalCount() {
        return 0;
    }

    /** Maximum global count for this element (-1 = no maximum). */
    default int getMaxGlobalCount() {
        return -1;
    }

    /** Minimum per-layer count for this element (0 = no minimum). */
    default int getMinLayerCount() {
        return 0;
    }

    /** Maximum per-layer count for this element (-1 = no maximum). */
    default int getMaxLayerCount() {
        return -1;
    }

    /** Whether this element marks the controller center position. */
    default boolean isCenter() {
        return false;
    }

    /** Add tooltip lines for this element. */
    default void addTooltip(List<String> tooltip) {}

    /**
     * Convert this element to a TraceabilityPredicate for compatibility
     * with the existing pattern checking system.
     */
    TraceabilityPredicate toPredicate();
}
