package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single piece (segment) of a multi-piece multiblock structure.
 * Each piece is a pure template reference: it carries an immutable
 * {@link PieceTemplate}, an offset from the controller, an offset mode,
 * and an optional activation condition. It carries no per-instance state.
 *
 * <p>Per-instance mutable state (the {@link PieceRuntimeState} / dirty flag /
 * validated flag / formed positions / repeat-search cache) lives on
 * {@link PieceRuntime}, which is created and owned by the
 * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase} (one
 * per controller, aggregated in a {@link PieceRuntimes}). This split keeps
 * the compiled {@link MultiPiecePattern} stateless and safe to cache and share
 * across controllers of the same multiblock type.
 *
 * <h2>Why state moved out</h2>
 * Previously this class held a final mutable state plus
 * {@code volatile positions / validated / dirty} fields, initialized in the
 * constructor. Because {@link MultiPiecePattern} instances are cached in
 * the structure-definition pool, two independent controllers of the same
 * multiblock type ended up sharing the same mutable state and
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
 * {@link PieceRuntime}; it holds the {@link PieceRuntimeState} and
     * dirty/validated flags that this checker needs. Passing the runtime
     * explicitly keeps the piece stateless and safe to share across
     * controllers that share the same compiled {@link MultiPiecePattern}.
     */
    @FunctionalInterface
    public interface SnapshotChecker {
        boolean check(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                      @NotNull StructureOrientation orientation,
                      @Nullable FormedStructureMetadata prior,
                      @NotNull PieceRuntime runtime,
                      @NotNull StructureMatchSession session);
    }

    private final String name;
    /**
     * The canonical piece IR.
     */
    private final PieceTemplate pieceTemplate;
    private final Vec3i offset;
    private final OffsetMode offsetMode;
    @Nullable
    private final StructureCondition<?> condition;
    private final boolean toolingVisible;
    private final boolean optional;

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
     * @param name       unique name for this piece (e.g. "core", "ring1")
     * @param template   the canonical piece IR
     * @param offset     offset from the controller position (Vec3i.ZERO for the core piece)
     * @param offsetMode how the offset is interpreted relative to controller facing
     * @param condition  optional condition; if non-null, this piece is only checked when condition returns true
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable StructureCondition<?> condition) {
        this(name, template, offset, offsetMode, condition, noopSnapshotChecker());
    }

    /**
     * Full constructor taking a canonical {@link PieceTemplate} directly
     * with an explicit {@link SnapshotChecker}.
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable StructureCondition<?> condition,
                          @NotNull SnapshotChecker snapshotChecker) {
        this(name, template, offset, offsetMode, condition, snapshotChecker, true);
    }

    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable StructureCondition<?> condition,
                          @NotNull SnapshotChecker snapshotChecker,
                          boolean toolingVisible) {
        this(name, template, offset, offsetMode, condition, snapshotChecker, toolingVisible, false);
    }

    /**
     * Full constructor with tooling visibility and fixed-piece optionality.
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                          @Nullable StructureCondition<?> condition,
                          @NotNull SnapshotChecker snapshotChecker,
                          boolean toolingVisible,
                          boolean optional) {
        this.name = name;
        this.pieceTemplate = template;
        this.offset = offset;
        this.offsetMode = offsetMode;
        this.condition = condition;
        this.snapshotChecker = snapshotChecker;
        this.toolingVisible = toolingVisible;
        this.optional = optional;
    }

    /**
     * @param name      unique name for this piece (e.g. "core", "ring1")
     * @param template  the immutable pattern template for this piece
     * @param offset    offset from the controller position (Vec3i.ZERO for the core piece)
     * @param condition optional condition; if non-null, this piece is only checked when condition returns true
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @Nullable StructureCondition<?> condition) {
        this(name, template, offset, OffsetMode.RELATIVE, condition);
    }

    /**
     * Create an unconditional piece with RELATIVE offset mode.
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template, @NotNull Vec3i offset) {
        this(name, template, offset, OffsetMode.RELATIVE, null);
    }

    /**
     * Create an unconditional piece with explicit offset mode.
     */
    public StructurePiece(@NotNull String name, @NotNull PieceTemplate template,
                          @NotNull Vec3i offset, @NotNull OffsetMode offsetMode) {
        this(name, template, offset, offsetMode, null);
    }

    private static SnapshotChecker noopSnapshotChecker() {
        return (s, o, orientation, p, r, session) -> false;
    }

    /**
     * @return the unique name of this piece
     */
    public String getName() {
        return name;
    }

    /**
     * @return the canonical piece IR
     */
    @NotNull
    public PieceTemplate getTemplate() {
        return pieceTemplate;
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

    public boolean isToolingVisible() {
        return toolingVisible;
    }

    /**
     * @return true when this fixed piece may remain unmatched while the parent
     * multiblock remains formed.
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * @return the optional typed activation condition.
     */
    @Nullable
    public StructureCondition<?> getCondition() {
        return condition;
    }

    /**
     * @return true if this piece should be active (condition is null or returns true)
     */
    public boolean isActive() {
        return condition == null || condition.test(StructureActivationContext.empty());
    }

    /**
     * Evaluate this piece with an explicit controller/session context.
     */
    @SuppressWarnings("unchecked")
    public boolean isActive(@NotNull StructureActivationContext<?> context) {
        if (condition == null) return true;
        return ((StructureCondition<Object>) condition)
                .test((StructureActivationContext<Object>) context);
    }

    /**
     * Compute the piece center with the same complete orientation used for
     * transforming pattern cells.
     */
    @NotNull
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull StructureOrientation orientation) {
        int[] off = { offset.getX(), offset.getY(), offset.getZ() };
        return offsetMode.apply(controllerPos, off, orientation);
    }

    /**
     * Compute the actual center position, with access to the prior pieces' runtime
     * metadata. This overload supports pieces whose offset depends on the runtime
     * repeat count of an earlier piece (e.g. the "top" piece that follows a
     * repeatable "body" piece in the middle of a structure).
     *
     * <p>Default implementation falls back to the static center calculation. Subclasses such as
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
                                 @NotNull StructureOrientation orientation,
                                 @Nullable FormedStructureMetadata prior) {
        return getCenterPos(controllerPos, orientation);
    }

    /**
     * Async structure check entry point.
     * Delegates to the snapshot checker bound at construction time, passing in
     * the per-controller {@link PieceRuntime}.
     *
     * @param runtime the per-controller state holder for this piece
     */
    public boolean checkOnSnapshot(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                   @NotNull StructureOrientation orientation,
                                   @Nullable FormedStructureMetadata prior,
                                   @NotNull PieceRuntime runtime) {
        StructureMatchSession session = new StructureMatchSession();
        return checkOnSnapshot(snap, origin, orientation, prior, runtime, session)
                && session.validate(false).success;
    }

    public boolean checkOnSnapshot(@NotNull IBlockAccess snap, @NotNull BlockPos origin,
                                   @NotNull StructureOrientation orientation,
                                   @Nullable FormedStructureMetadata prior,
                                   @NotNull PieceRuntime runtime,
                                   @NotNull StructureMatchSession session) {
        return snapshotChecker.check(snap, origin, orientation, prior, runtime, session);
    }

}
