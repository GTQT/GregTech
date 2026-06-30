package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Transactional world-match context for a {@link StructureRuntimeDetector}.
 */
public final class StructureRuntimeDetectionContext<T extends MultiblockControllerBase> {

    @NotNull
    private final World world;
    @NotNull
    private final BlockPos controllerPos;
    @NotNull
    private final StructureOrientation orientation;
    @NotNull
    private final T controller;
    @NotNull
    private final StructurePiece piece;
    @NotNull
    private final StructureMatchSession session;
    @NotNull
    private final BlockWorldState worldState = new BlockWorldState();
    @NotNull
    private final StructureEvaluationContext<Object> evaluationContext =
            new StructureEvaluationContext<>();
    @NotNull
    private final LongOpenHashSet formedPositions = new LongOpenHashSet();
    @NotNull
    private final LongOpenHashSet watchedPositions = new LongOpenHashSet();

    @Nullable
    private BlockPos failurePos;
    @Nullable
    private String expected;
    @Nullable
    private String actual;
    @Nullable
    private PatternError error;

    StructureRuntimeDetectionContext(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @NotNull T controller,
            @NotNull StructurePiece piece,
            @NotNull StructureMatchSession session) {
        this.world = world;
        this.controllerPos = controllerPos.toImmutable();
        this.orientation = orientation;
        this.controller = controller;
        this.piece = piece;
        this.session = session;
        this.session.setControllerContext(controller);
        this.session.beginPieceContribution(piece);
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    @NotNull
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @NotNull
    public StructureOrientation getOrientation() {
        return orientation;
    }

    @NotNull
    public T getController() {
        return controller;
    }

    @NotNull
    public BlockPos localPos(
            int x,
            int y,
            int z,
            @NotNull RelativeDirection charDir,
            @NotNull RelativeDirection stringDir,
            @NotNull RelativeDirection aisleDir) {
        BlockPos offset = RelativeDirection.setActualRelativeOffset(
                x, y, z,
                orientation.getStructureFront(), orientation.getUp(),
                orientation.isFlipped(),
                new RelativeDirection[] { charDir, stringDir, aisleDir });
        return controllerPos.add(offset);
    }

    /**
     * Match one runtime-discovered cell through the canonical typed element
     * contract.
     */
    @SuppressWarnings("unchecked")
    public boolean match(@NotNull BlockPos pos, @NotNull IStructureElement<?> element) {
        BlockPos immutablePos = pos.toImmutable();
        watchedPositions.add(immutablePos.toLong());
        IStructureElement<Object> typedElement =
                (IStructureElement<Object>) element.compile();
        worldState.update(world, immutablePos, session);
        evaluationContext.update(
                controller, session, worldState,
                StructureEvaluationContext.Operation.MATCH_WORLD);
        boolean matched = evaluationContext.transaction(context -> {
            boolean result = typedElement.match(context);
            if (!result) {
                error = worldState.getError();
            }
            return result;
        });
        if (matched) {
            formedPositions.add(immutablePos.toLong());
        } else {
            failurePos = immutablePos;
        }
        return matched;
    }

    /**
     * Publish a typed contribution from detector-level geometry or state.
     */
    public <E, A> void emit(@NotNull StructureContributionKey<E, A> key,
                            @Nullable E value) {
        session.getContributionBuilder().emit(key, value);
    }

    /**
     * Include a cell that was validated directly by the detector.
     */
    public void includePosition(@NotNull BlockPos pos) {
        long packed = pos.toLong();
        watchedPositions.add(packed);
        formedPositions.add(packed);
    }

    /**
     * Record a human-readable mismatch for failure diagnostics.
     */
    public boolean fail(@NotNull BlockPos pos,
                        @NotNull String expected,
                        @NotNull String actual) {
        this.failurePos = pos.toImmutable();
        this.expected = expected;
        this.actual = actual;
        return false;
    }

    @Nullable
    BlockPos getFailurePos() {
        return failurePos;
    }

    @Nullable
    String getExpected() {
        return expected;
    }

    @Nullable
    String getActual() {
        return actual;
    }

    @Nullable
    PatternError getError() {
        return error;
    }

    @NotNull
    LongSet copyFormedPositions() {
        return new LongOpenHashSet(formedPositions);
    }

    @NotNull
    LongSet copyWatchedPositions() {
        return new LongOpenHashSet(watchedPositions);
    }

    @NotNull
    StructureContribution finishContribution() {
        return session.finishPieceContribution(piece);
    }
}
