package gregtech.api.pattern.element;

import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Single-position matching rule for structure elements.
 * Each element defines how a single position in a structure piece is matched,
 * previewed, and auto-built.
 *
 * <p>This interface is the single canonical concept for cell-level matching
 * in the new (StructureDefinition) path. The legacy
 * {@link gregtech.api.pattern.TraceabilityPredicate} remains a public API
 * for the old (FactoryBlockPattern) path; the
 * {@link #applyTo(String, PieceTemplateCompiler)} bridge method encapsulates
 * the only point of contact between the two systems, so the rest of the
 * element API does not need to mention TraceabilityPredicate.
 */
public interface IStructureElement<T> {

    /**
     * Canonical runtime match entry. Compiled templates call this method for
     * both live-world and snapshot checks.
     */
    default boolean check(@NotNull StructureEvaluationContext<T> context) {
        return context.testElementPredicate();
    }

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
     * Canonical candidate entry for preview and both build modes.
     */
    default BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
        return getCandidates();
    }

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
     * Canonical placement entry. The operation in the evaluation context
     * distinguishes creative and survival construction.
     */
    default boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                               @NotNull EntityPlayer player, boolean skipHatches) {
        World world = context.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot place a structure element against a snapshot");
        }
        return placeBlock(world, context.getPos(), context.getLegacyContext(), player, skipHatches);
    }

    /**
     * Spawn a structure hint at the given position.
     *
     * @param world the world
     * @param pos   the block position
     */
    void spawnHint(World world, BlockPos pos);

    /**
     * Canonical hint entry.
     */
    default void spawnHint(@NotNull StructureEvaluationContext<T> context) {
        World world = context.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot spawn a structure hint against a snapshot");
        }
        spawnHint(world, context.getPos());
    }

    /**
     * Compile this declaration to the immutable element executed by templates.
     */
    @NotNull
    default CompiledStructureElement<T> compile() {
        return CompiledStructureElement.compile(this);
    }

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
     * Register this element into a {@link PieceTemplateCompiler} under the
     * given symbol. This is the single, interface-level bridge to the
     * legacy {@link gregtech.api.pattern.TraceabilityPredicate}-based compile
     * path: the new (StructureDefinition) path goes through this method
     * and never mentions TraceabilityPredicate directly.
     *
     * <p>Default implementation converts via {@code toPredicate()}; concrete
     * implementations may override to provide a more direct build path
     * (e.g. populating the template's predicate 3D array without going
     * through the legacy predicate system).
     *
     * @param symbol    the single-character symbol this element was bound to
     * @param compiler  the target template compiler (in build state)
     */
    default void applyTo(@NotNull String symbol, @NotNull PieceTemplateCompiler compiler) {
        compiler.whereElement(symbol, this);
    }

    /**
     * Build the legacy {@link gregtech.api.pattern.TraceabilityPredicate}
     * for this element. Implementations retain this as an internal helper
     * used by the default {@link #applyTo} bridge; the new path does not
     * call it directly.
     */
    @NotNull
    gregtech.api.pattern.TraceabilityPredicate toPredicate();
}
