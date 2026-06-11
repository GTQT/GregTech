package gregtech.api.pattern;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        SURVIVAL_BUILD
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
        return new StructureMatchCollector(getLegacyContext());
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
}
