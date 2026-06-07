package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Represents a single piece (segment) of a multi-piece multiblock structure.
 * Each piece is a pure template reference: it carries an immutable
 * {@link BlockPatternTemplate}, an offset from the controller, an offset mode,
 * and an optional activation condition. It carries no per-instance state.
 *
 * <p>Per-instance mutable state (the {@link MultiblockState} / dirty flag /
 * validated flag / formed positions / repeat-search cache) lives on
 * {@link PieceRuntime}, which is created and owned by the
 * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase} (one
 * per controller, aggregated in a {@link PieceRuntimes}). This split keeps
 * the compiled {@link MultiPiecePattern} stateless and safe to cache and share
 * across controllers of the same multiblock type.
 *
 * <h2>Why state moved out</h2>
 * Previously this class held a {@code final MultiblockState} plus
 * {@code volatile positions / validated / dirty} fields, initialized in the
 * constructor. Because {@link MultiPiecePattern} instances are cached in
 * the structure-definition pool, two independent controllers of the same
 * multiblock type ended up sharing the same {@link MultiblockState} and
 * positions set — a silent cross-controller state leak. Moving the state
 * to {@link PieceRuntime} (owned by the controller) makes the bug
 * structurally impossible.
 *
 * @see MultiPiecePattern for the composite pattern that holds multiple pieces
 * @see PieceRuntime for the per-controller state holder
 */
public class StructurePiece {

    /**
     * Functional interface for snapshot-based structure checking.
     * Bound at compile time by {@code StructureCompiler}.
     *
     * <p>The {@code runtime} parameter carries the per-controller
     * {@link PieceRuntime}; it holds the {@link MultiblockState} and
     * dirty/validated flags that this checker needs. Passing the runtime
     * explicitly keeps the piece stateless and safe to share across
     * controllers that share the same compiled {@link MultiPiecePattern}.
     */
    @FunctionalInterface
    public interface SnapshotChecker {
        boolean check(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                      @NotNull EnumFacing front, @NotNull EnumFacing up, boolean flipped,
                      @Nullable FormedStructureMetadata prior,
                      @NotNull PieceRuntime runtime);
    }

    private final String name;
    /**
     * The canonical piece IR. The new compile path
     * ({@link gregtech.api.pattern.element.StructureCompiler}) constructs
     * this directly; the legacy compile path
     * ({@link MultiPiecePattern.Builder#piece(String, BlockPatternTemplate, Vec3i)})
     * supplies a {@link BlockPatternTemplate} facade whose
     * {@link BlockPatternTemplate#getDelegate()} is stored here.
     */
    private final PieceTemplate pieceTemplate;
    /**
     * Backward-compatibility view of {@link #pieceTemplate} as a
     * {@link BlockPatternTemplate} facade. Lazily constructed on first
     * {@link #getTemplate()} call. May be null if no legacy accessor is ever
     * invoked, saving memory for the new path.
     */
    @Nullable
    private BlockPatternTemplate templateView;
    private final Vec3i offset;
    private final OffsetMode offsetMode;
    @Nullable
    private final BooleanSupplier condition;

    /**
     * Snapshot checker bound at construction time. Receives a per-call
     * {@link PieceRuntime} (and thus the per-controller state) via its
     * {@link SnapshotChecker#check} method, so this field stays stateless
     * and is safe to share across controllers that share the same compiled
     * {@link MultiPiecePattern}.
     *
     * <p>{@code final} with a no-op default: subclasses (or the compiler)
     * can pass a real implementation via the constructor, or callers can
     * fall back to the no-op for pieces that never participate in async
     * snapshot checks.
     */
    @NotNull
    private final SnapshotChecker snapshotChecker;

    /**
     * New-path constructor taking a canonical {@link PieceTemplate} directly.
     * The new compile path
     * ({@link gregtech.api.pattern.element.StructureCompiler}) uses this to
     * skip the {@link BlockPatternTemplate} facade entirely.
     *
     * @param name       unique name for this piece (e.g. "core", "ring1")
     * @param template   the canonical piece IR
     * @param offset     offset from the controller position (Vec3i.ZERO for the core piece)
     * @param offsetMode how the offset is interpreted relative to controller facing
     * @param condition  optional condition; if non-null, this piece is only checked when condition returns true
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable BooleanSupplier condition) {
        this(name, template, offset, offsetMode, condition, noopSnapshotChecker());
    }

    /**
     * New-path full constructor taking a canonical {@link PieceTemplate} directly
     * with an explicit {@link SnapshotChecker}.
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable BooleanSupplier condition,
                          @NotNull SnapshotChecker snapshotChecker) {
        this.name = name;
        this.pieceTemplate = template;
        this.offset = offset;
        this.offsetMode = offsetMode;
        this.condition = condition;
        this.snapshotChecker = snapshotChecker;
    }

    /**
     * Legacy-path constructor accepting a {@link BlockPatternTemplate} facade.
     * The facade's {@link BlockPatternTemplate#getDelegate() delegate}
     * (the canonical {@link PieceTemplate}) is stored; the facade itself is
     * retained for the {@link #getTemplate()} accessor.
     */
    public StructurePiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable BooleanSupplier condition) {
        this(name, template, offset, offsetMode, condition, noopSnapshotChecker());
    }

    /**
     * Legacy-path full constructor accepting a {@link BlockPatternTemplate} facade
     * with an explicit {@link SnapshotChecker}.
     */
    public StructurePiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable BooleanSupplier condition,
                          @NotNull SnapshotChecker snapshotChecker) {
        this(name, template.getDelegate(), offset, offsetMode, condition, snapshotChecker);
        this.templateView = template;
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

    private static SnapshotChecker noopSnapshotChecker() {
        return (s, o, f, u, fl, p, r) -> false;
    }

    /**
     * @return the unique name of this piece
     */
    public String getName() {
        return name;
    }

    /**
     * @return the canonical piece IR (the new canonical data class)
     */
    @NotNull
    public PieceTemplate getPieceTemplate() {
        return pieceTemplate;
    }

    /**
     * @return the legacy facade view of the piece IR. Lazily constructed on
     *         first call; for new code, prefer {@link #getPieceTemplate()}.
     */
    public BlockPatternTemplate getTemplate() {
        if (templateView == null) {
            templateView = new BlockPatternTemplate(pieceTemplate);
        }
        return templateView;
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
     * Compute the actual center position, with access to the prior pieces' runtime
     * metadata. This overload supports pieces whose offset depends on the runtime
     * repeat count of an earlier piece (e.g. the "top" piece that follows a
     * repeatable "body" piece in the middle of a structure).
     *
     * <p>Default implementation falls back to the static
     * {@link #getCenterPos(BlockPos, EnumFacing, EnumFacing)}. Subclasses such as
     * {@link DynamicOffsetPiece} override this to compute a dynamic position
     * based on the prior metadata.
     *
     * @param controllerPos the controller's block position
     * @param frontFacing   the controller's front facing
     * @param upFacing      the controller's upward facing
     * @param prior         the formed-structure metadata accumulated from previously
     *                      checked pieces; may be {@code null} when no pieces have
     *                      been checked yet (e.g. the first piece, or a single-piece
     *                      structure)
     * @return the center position for this piece's pattern check
     */
    @NotNull
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull EnumFacing frontFacing,
                                 @NotNull EnumFacing upFacing,
                                 @Nullable FormedStructureMetadata prior) {
        return getCenterPos(controllerPos, frontFacing, upFacing);
    }

    /**
     * Async structure check entry point.
     * Delegates to the snapshot checker bound at construction time, passing in
     * the per-controller {@link PieceRuntime}.
     *
     * @param runtime the per-controller state holder for this piece
     */
    public boolean checkOnSnapshot(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                   @NotNull EnumFacing front, @NotNull EnumFacing up, boolean flipped,
                                   @Nullable FormedStructureMetadata prior,
                                   @NotNull PieceRuntime runtime) {
        return snapshotChecker.check(snap, origin, front, up, flipped, prior, runtime);
    }
}
