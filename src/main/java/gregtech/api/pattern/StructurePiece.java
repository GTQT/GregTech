package gregtech.api.pattern;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Represents a single piece (segment) of a multi-piece multiblock structure.
 * Each piece has its own pattern template, offset from the controller, and independent
 * dirty/validated state for efficient partial re-checking.
 *
 * <p>For standard multiblocks (< 100 blocks), a single piece is sufficient.
 * Multi-piece patterns are designed for super-large structures (e.g. 570K blocks)
 * where checking the entire structure on every block change is too expensive.
 *
 * @see MultiPiecePattern for the composite pattern that holds multiple pieces
 */
public class StructurePiece {

    private final String name;
    private final BlockPatternTemplate template;
    private final Vec3i offset;
    private final OffsetMode offsetMode;
    @Nullable
    private final BooleanSupplier condition;

    // --- Per-instance mutable state ---

    private final MultiblockState state;
    /**
     * Set of block positions (as longs) belonging to this piece when formed.
     * Uses volatile reference + swap strategy for thread safety:
     * - Event thread reads positions via contains() (safe on immutable snapshot)
     * - Main thread replaces reference atomically via swapPositions()
     */
    private volatile LongSet positions = new LongOpenHashSet();
    private volatile boolean validated = false;
    private volatile boolean dirty = true;

    /**
     * @param name       unique name for this piece (e.g. "core", "ring1")
     * @param template   the immutable pattern template for this piece
     * @param offset     offset from the controller position (Vec3i.ZERO for the core piece)
     * @param offsetMode how the offset is interpreted relative to controller facing
     * @param condition  optional condition; if non-null, this piece is only checked when condition returns true
     */
    public StructurePiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable BooleanSupplier condition) {
        this.name = name;
        this.template = template;
        this.offset = offset;
        this.offsetMode = offsetMode;
        this.condition = condition;
        this.state = template.createState();
    }

    /**
     * @param name      unique name for this piece (e.g. "core", "ring1")
     * @param template  the immutable pattern template for this piece
     * @param offset    offset from the controller position (Vec3i.ZERO for the core piece)
     * @param condition optional condition; if non-null, this piece is only checked when condition returns true
     */
    public StructurePiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                          @NotNull Vec3i offset, @Nullable BooleanSupplier condition) {
        this(name, template, offset, OffsetMode.RELATIVE, condition);
    }

    /**
     * Create an unconditional piece with RELATIVE offset mode.
     */
    public StructurePiece(@NotNull String name, @NotNull BlockPatternTemplate template, @NotNull Vec3i offset) {
        this(name, template, offset, OffsetMode.RELATIVE, null);
    }

    /**
     * Create an unconditional piece with explicit offset mode.
     */
    public StructurePiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode) {
        this(name, template, offset, offsetMode, null);
    }

    /**
     * @return the unique name of this piece
     */
    public String getName() {
        return name;
    }

    /**
     * @return the immutable pattern template for this piece
     */
    public BlockPatternTemplate getTemplate() {
        return template;
    }

    /**
     * @return the offset from the controller position
     */
    public Vec3i getOffset() {
        return offset;
    }

    /**
     * @return the offset mode that determines how the offset is applied
     */
    @NotNull
    public OffsetMode getOffsetMode() {
        return offsetMode;
    }

    /**
     * @return the per-instance mutable state for pattern checking
     */
    public MultiblockState getState() {
        return state;
    }

    /**
     * @return set of block positions (as longs) belonging to this piece when formed.
     *         The returned reference is a snapshot — safe to read from any thread.
     */
    public LongSet getPositions() {
        return positions;
    }

    /**
     * Atomically replace the positions set with a new one.
     * This is the thread-safe way to update positions after re-validation.
     * The old set becomes unreachable and can be GC'd.
     *
     * @param newPositions the new set of positions
     */
    public void swapPositions(@NotNull LongSet newPositions) {
        this.positions = newPositions;
    }

    /**
     * @return true if this piece has been validated (pattern check passed)
     */
    public boolean isValidated() {
        return validated;
    }

    /**
     * @param validated new validation status
     */
    public void setValidated(boolean validated) {
        this.validated = validated;
    }

    /**
     * @return true if this piece needs re-checking (a block changed within it)
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Mark this piece as needing re-check.
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Clear the dirty flag after re-checking.
     */
    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * @return true if this piece is conditional (has a condition supplier)
     */
    public boolean isConditional() {
        return condition != null;
    }

    /**
     * @return true if this piece should be active (condition is null or returns true)
     */
    public boolean isActive() {
        return condition == null || condition.getAsBoolean();
    }

    /**
     * Compute the actual center position for this piece given the controller position.
     * Uses the legacy absolute offset (no rotation). Prefer the overload with facings.
     *
     * @param controllerPos the controller's block position
     * @return the center position for this piece's pattern check
     */
    public BlockPos getCenterPos(BlockPos controllerPos) {
        return controllerPos.add(offset);
    }

    /**
     * Compute the actual center position for this piece given the controller position and facings.
     * Applies the piece's {@link OffsetMode} to correctly rotate the offset.
     *
     * @param controllerPos the controller's block position
     * @param frontFacing   the controller's front facing
     * @param upFacing      the controller's upward facing
     * @return the center position for this piece's pattern check
     */
    @NotNull
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull EnumFacing frontFacing,
                                 @NotNull EnumFacing upFacing) {
        int[] off = { offset.getX(), offset.getY(), offset.getZ() };
        return offsetMode.apply(controllerPos, off, frontFacing, upFacing);
    }

    /**
     * Reset this piece's state (on invalidation).
     */
    public void reset() {
        this.validated = false;
        this.dirty = true;
        this.positions = new LongOpenHashSet();
        this.state.clearCache();
    }
}
