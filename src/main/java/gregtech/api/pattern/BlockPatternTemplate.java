package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Backward-compatibility facade over {@link PieceTemplate}.
 *
 * <p>The new structure compile path
 * {@link gregtech.api.pattern.element.IStructurePiece} → {@link StructurePiece}
 * holds a {@link PieceTemplate} directly. This class is retained so that the
 * public APIs ({@link FactoryBlockPattern#buildTemplate()},
 * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase#createStructureTemplate()},
 * and the {@link #getTemplate()} accessor) continue to work for the
 * ~100 legacy multiblocks and any external addons.
 *
 * <p>Conceptually, this class is no longer the canonical IR — it is a thin
 * view. New code should use {@link PieceTemplate} (or the {@link StructurePiece}
 * methods) directly.
 *
 * @see PieceTemplate for the canonical IR
 * @see PieceRuntimeState for internal per-instance mutable state
 * @see FactoryBlockPattern for the legacy builder
 */
public class BlockPatternTemplate {

    /**
     * Aisle definition record. Lives here for backward compatibility with
     * external callers that import {@code BlockPatternTemplate.AisleDef}.
     * The canonical record is the same one used by {@link PieceTemplate}.
     *
     * @param minRepeat    minimum number of repetitions for this aisle
     * @param maxRepeat    maximum number of repetitions for this aisle
     * @param channelName  optional channel name; {@code null} means the aisle is not channel-controlled
     */
    @Desugar
    public record AisleDef(int minRepeat, int maxRepeat, @Nullable String channelName) {

        /**
         * @return a copy of the legacy {@code [minRepeat, maxRepeat]} pair for callers
         *         that still need the int[] shape (e.g. RepetitionDFS / preview builders).
         */
        public int[] toRangeArray() {
            return new int[] { minRepeat, maxRepeat };
        }
    }

    /**
     * Center offset for a template. Lives here for backward compatibility with
     * external callers that import {@code BlockPatternTemplate.CenterOffset}.
     * The canonical record is the same one used by {@link PieceTemplate}.
     *
     * @param x    controller x offset within the pattern
     * @param y    controller y offset within the pattern
     * @param z    controller z offset within the pattern
     * @param minZ cumulative min aisle count before the center aisle
     * @param maxZ cumulative max aisle count before the center aisle
     */
    @Desugar
    public record CenterOffset(int x, int y, int z, int minZ, int maxZ) {

        // Empty records occasionally confuse older javac + Jabel combinations
        // that walk the body looking for the @Desugar anchor. The no-op getter
        // below makes the body non-empty and avoids the "Must be annotated with
        // @Desugar" error reported against this record when it sits below
        // another @Desugar-annotated record in the same file.
        @SuppressWarnings("unused")
        public boolean isSynthetic() {
            return true;
        }
    }

    private final PieceTemplate delegate;

    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions) {
        this(new PieceTemplate(predicatesIn, structureDir, aisleRepetitions));
    }

    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions,
                                @Nullable String[] aisleChannelNames) {
        this(new PieceTemplate(predicatesIn, structureDir, aisleRepetitions, aisleChannelNames));
    }

    /**
     * Full constructor with optional external center offset and structure description.
     *
     * @param predicatesIn          the 3D predicate array [z][y][x]
     * @param structureDir          the 3 relative directions
     * @param aisleRepetitions      the repetition ranges per aisle
     * @param aisleChannelNames     channel names per aisle (nullable entries)
     * @param externalCenterOffset  optional externally-specified center offset;
     *                              if {@code null}, auto-discovers from the {@code isCenter} predicate
     * @param structureDescription  optional auto-generated description lines for tooltip display;
     *                              if {@code null}, defaults to an empty list
     */
    public BlockPatternTemplate(@NotNull TraceabilityPredicate[][][] predicatesIn,
                                @NotNull RelativeDirection[] structureDir,
                                @NotNull int[][] aisleRepetitions,
                                @Nullable String[] aisleChannelNames,
                                @Nullable int[] externalCenterOffset,
                                @Nullable List<String> structureDescription) {
        this(new PieceTemplate(predicatesIn, structureDir, aisleRepetitions,
                aisleChannelNames, externalCenterOffset, structureDescription));
    }

    /**
     * Wrap an existing {@link PieceTemplate} as a {@code BlockPatternTemplate}.
     * Used by the new compile path to hand a legacy facade to consumers that
     * have not yet migrated to {@link PieceTemplate}.
     */
    public BlockPatternTemplate(@NotNull PieceTemplate delegate) {
        this.delegate = delegate;
    }

    /**
     * @return the underlying {@link PieceTemplate} that this facade delegates to.
     *         New code should use this directly; the facade methods are kept
     *         only for backward compatibility.
     */
    @NotNull
    public PieceTemplate getDelegate() {
        return delegate;
    }

    // --- Accessors — all delegate to the underlying PieceTemplate ---

    public TraceabilityPredicate[][][] getBlockMatches() {
        return delegate.getBlockMatches();
    }

    @NotNull
    public PieceTemplateLegacyView getLegacyView() {
        return delegate.getLegacyView();
    }

    @NotNull
    public AisleDef[] getAisles() {
        return delegate.getAisles();
    }

    @NotNull
    public int[][] getAisleRepetitions() {
        return delegate.getAisleRepetitions();
    }

    @NotNull
    public String[] getAisleChannelNames() {
        return delegate.getAisleChannelNames();
    }

    public RelativeDirection[] getStructureDir() {
        return delegate.getStructureDir();
    }

    public int getXLength() {
        return delegate.getXLength();
    }

    public int getYLength() {
        return delegate.getYLength();
    }

    public int getZLength() {
        return delegate.getZLength();
    }

    public CenterOffset getCenterOffset() {
        return delegate.getCenterOffset();
    }

    /**
     * Compute the maximum expanded finger length, accounting for repeatable aisles.
     * This is the sum of all max repetition counts across all aisles,
     * representing the worst-case structure length along the finger axis.
     */
    public int getMaxExpandedFingerLength() {
        return delegate.getMaxExpandedFingerLength();
    }

    /**
     * Walk every non-null, non-{@link TraceabilityPredicate#ANY} cell of this template and
     * invoke {@code consumer} with the pattern-local world position (as if the controller
     * were at the origin) and the predicate occupying that cell.
     */
    /**
     * @deprecated Legacy orientation facade. New code should pass
     *             {@link StructureOrientation}.
     */
    @Deprecated
    public void forEachPredicate(@NotNull EnumFacing front, @NotNull EnumFacing up, boolean flipped,
                                 @NotNull BiConsumer<BlockPos, TraceabilityPredicate> consumer) {
        delegate.forEachPredicate(StructureOrientation.legacy(front, up, flipped, false), consumer);
    }

    public void forEachPredicate(@NotNull StructureOrientation orientation,
                                 @NotNull BiConsumer<BlockPos, TraceabilityPredicate> consumer) {
        delegate.forEachPredicate(orientation, consumer);
    }

    /**
     * Compute the precise world-space AABB for this structure template given the controller state.
     */
    @NotNull
    /**
     * @deprecated Legacy orientation facade. New code should pass
     *             {@link StructureOrientation}.
     */
    @Deprecated
    public BlockPos[] computeWorldAABB(@NotNull BlockPos centerPos, @NotNull EnumFacing frontFacing,
                                       @NotNull EnumFacing upwardsFacing, boolean isFlipped, int margin) {
        return delegate.computeWorldAABB(centerPos,
                StructureOrientation.legacy(frontFacing, upwardsFacing, isFlipped, false), margin);
    }

    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos centerPos,
                                       @NotNull StructureOrientation orientation,
                                       int margin) {
        return delegate.computeWorldAABB(centerPos, orientation, margin);
    }

    @NotNull
    public List<String> getStructureDescription() {
        return delegate.getStructureDescription();
    }
}
