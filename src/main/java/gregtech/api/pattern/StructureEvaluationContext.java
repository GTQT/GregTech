package gregtech.api.pattern;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Typed, per-cell execution context shared by matching, preview, hints and
 * building. Matching reuses one instance and updates it before each cell.
 *
 * @param <T> controller-specific context type
 */
public final class StructureEvaluationContext<T> {

    public enum Operation {
        MATCH_WORLD,
        MATCH_SNAPSHOT,
        PREVIEW,
        HINT,
        CREATIVE_BUILD,
        SURVIVAL_BUILD,
        ITERATE
    }

    @Nullable
    private T controller;
    @Nullable
    private StructureMatchSession session;
    @Nullable
    private BlockWorldState worldState;
    private Operation operation = Operation.MATCH_WORLD;

    @NotNull
    StructureEvaluationContext<T> update(@Nullable T controller,
                                         @Nullable StructureMatchSession session,
                                         @NotNull BlockWorldState worldState,
                                         @NotNull Operation operation) {
        this.controller = controller;
        this.session = session;
        this.worldState = worldState;
        this.operation = operation;
        return this;
    }

    @Nullable
    public T getController() {
        return controller;
    }

    @Nullable
    public StructureMatchSession getSession() {
        return session;
    }

    @NotNull
    public PatternMatchContext getLegacyContext() {
        return requireWorldState().getMatchContext();
    }

    @NotNull
    public StructureMatchCollector getCollector() {
        return session == null
                ? new StructureMatchCollector(getLegacyContext())
                : new StructureMatchCollector(session.getOperationState(), getLegacyContext());
    }

    @NotNull
    public Operation getOperation() {
        return operation;
    }

    public boolean isSnapshot() {
        return operation == Operation.MATCH_SNAPSHOT;
    }

    @Nullable
    public World getWorld() {
        return requireWorldState().getWorld();
    }

    @NotNull
    public IBlockAccess getBlockAccess() {
        return requireWorldState().getBlockAccess();
    }

    @NotNull
    public BlockPos getPos() {
        return requireWorldState().getPos();
    }

    @NotNull
    public IBlockState getBlockState() {
        return requireWorldState().getBlockState();
    }

    @Nullable
    public TileEntity getTileEntity() {
        return requireWorldState().getTileEntity();
    }

    public void setError(@Nullable PatternError error) {
        requireWorldState().setError(error);
    }

    @NotNull
    public Checkpoint checkpoint() {
        BlockWorldState state = requireWorldState();
        return session == null
                ? new Checkpoint(null, state.checkpoint())
                : new Checkpoint(session.checkpoint(), state.checkpoint());
    }

    public void restore(@NotNull Checkpoint checkpoint) {
        requireWorldState().restoreTo(checkpoint.worldStateCheckpoint);
        if (checkpoint.sessionCheckpoint != null) {
            if (session == null) {
                throw new IllegalStateException("Cannot restore a session checkpoint without an active session");
            }
            session.restoreTo(checkpoint.sessionCheckpoint);
        }
    }

    public boolean transaction(@NotNull Supplier<Boolean> action) {
        return transactionValue(ignored -> action.get(), Boolean.TRUE::equals);
    }

    public boolean transaction(@NotNull Function<StructureEvaluationContext<T>, Boolean> action) {
        return transactionValue(action, Boolean.TRUE::equals);
    }

    public void transactionAction(@NotNull Consumer<StructureEvaluationContext<T>> action) {
        transactionValue(context -> {
            action.accept(context);
            return Boolean.TRUE;
        }, Boolean.TRUE::equals);
    }

    public <R> R transactionValue(@NotNull Function<StructureEvaluationContext<T>, R> action,
                                  @NotNull Predicate<R> commitPredicate) {
        Checkpoint checkpoint = checkpoint();
        try {
            R result = action.apply(this);
            if (!commitPredicate.test(result)) {
                restore(checkpoint);
            }
            return result;
        } catch (RuntimeException | Error e) {
            restore(checkpoint);
            throw e;
        }
    }

    public boolean probe(@NotNull Supplier<Boolean> action) {
        return probeValue(ignored -> action.get());
    }

    public boolean probe(@NotNull Function<StructureEvaluationContext<T>, Boolean> action) {
        return probeValue(action);
    }

    public void probeAction(@NotNull Consumer<StructureEvaluationContext<T>> action) {
        probeValue(context -> {
            action.accept(context);
            return null;
        });
    }

    public <R> R probeValue(@NotNull Function<StructureEvaluationContext<T>, R> action) {
        Checkpoint checkpoint = checkpoint();
        try {
            return action.apply(this);
        } finally {
            restore(checkpoint);
        }
    }

    /**
     * Compatibility boundary for predicates compiled by the legacy builder.
     */
    public boolean test(@NotNull TraceabilityPredicate predicate) {
        return predicate.test(requireWorldState());
    }

    @NotNull
    private BlockWorldState requireWorldState() {
        if (worldState == null) {
            throw new IllegalStateException("Structure evaluation context has not been initialized");
        }
        return worldState;
    }

    public static final class Checkpoint {

        @Nullable
        private final StructureMatchSession.Checkpoint sessionCheckpoint;
        @NotNull
        private final BlockWorldState.Checkpoint worldStateCheckpoint;

        private Checkpoint(@Nullable StructureMatchSession.Checkpoint sessionCheckpoint,
                           @NotNull BlockWorldState.Checkpoint worldStateCheckpoint) {
            this.sessionCheckpoint = sessionCheckpoint;
            this.worldStateCheckpoint = worldStateCheckpoint;
        }
    }
}
