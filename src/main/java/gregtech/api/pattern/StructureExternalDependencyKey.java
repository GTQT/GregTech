package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Stable key for controller or external state used by activation conditions.
 *
 * @param <T> snapshot value type
 */
public final class StructureExternalDependencyKey<T> {

    @NotNull
    private final String id;
    @NotNull
    private final Function<MultiblockControllerBase, T> snapshot;
    @NotNull
    private final BiPredicate<T, T> equivalent;

    @NotNull
    public static <T> StructureExternalDependencyKey<T> create(
            @NotNull String id,
            @NotNull Function<MultiblockControllerBase, T> snapshot,
            @NotNull BiPredicate<T, T> equivalent) {
        return new StructureExternalDependencyKey<>(id, snapshot, equivalent);
    }

    private StructureExternalDependencyKey(
            @NotNull String id,
            @NotNull Function<MultiblockControllerBase, T> snapshot,
            @NotNull BiPredicate<T, T> equivalent) {
        if (id.isEmpty() || id.indexOf(':') <= 0 || id.endsWith(":")) {
            throw new IllegalArgumentException("External dependency key id must be namespaced: " + id);
        }
        this.id = id;
        this.snapshot = snapshot;
        this.equivalent = equivalent;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @Nullable
    public T snapshot(@Nullable MultiblockControllerBase controller) {
        return snapshot.apply(controller);
    }

    @SuppressWarnings("unchecked")
    public boolean equivalentObjects(@Nullable Object left, @Nullable Object right) {
        return equivalent.test((T) left, (T) right);
    }

    public boolean equivalent(@Nullable T left, @Nullable T right) {
        return equivalent.test(left, right);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || obj instanceof StructureExternalDependencyKey
                && id.equals(((StructureExternalDependencyKey<?>) obj).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
