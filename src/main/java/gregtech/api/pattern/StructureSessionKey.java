package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Strongly typed key for data owned by a {@link StructureMatchSession}.
 *
 * <p>The copy function defines the transaction boundary for the value. Immutable
 * values can use {@link #immutable(String)}; mutable values must provide an
 * explicit copier so forks and checkpoints cannot leak mutations.
 */
public final class StructureSessionKey<T> {

    private final String name;
    private final UnaryOperator<T> copier;

    private StructureSessionKey(@NotNull String name, @NotNull UnaryOperator<T> copier) {
        this.name = Objects.requireNonNull(name, "name");
        this.copier = Objects.requireNonNull(copier, "copier");
    }

    @NotNull
    public static <T> StructureSessionKey<T> immutable(@NotNull String name) {
        return new StructureSessionKey<>(name, value -> value);
    }

    @NotNull
    public static <T> StructureSessionKey<T> copying(@NotNull String name,
                                                     @NotNull UnaryOperator<T> copier) {
        return new StructureSessionKey<>(name, copier);
    }

    @NotNull
    T copy(@NotNull T value) {
        return Objects.requireNonNull(copier.apply(value),
                "Session key copier returned null for " + name);
    }

    @Override
    public String toString() {
        return "StructureSessionKey[" + name + "]";
    }
}
