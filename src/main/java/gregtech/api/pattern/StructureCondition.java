package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Typed activation rule for a conditional structure piece.
 *
 * <p>It extends {@link BooleanSupplier} only as a compatibility bridge for the
 * existing piece constructors. New runtime paths call {@link #test} with an
 * explicit context.
 */
@FunctionalInterface
public interface StructureCondition<T> extends BooleanSupplier {

    boolean test(@NotNull StructureActivationContext<T> context);

    /**
     * Typed dependencies consumed by this activation rule.
     *
     * <p>The default is intentionally empty for source compatibility with
     * existing lambdas. A conditional piece with an empty dependency set is
     * treated as opaque by the dependency compiler and falls back to the
     * active-graph evaluator.
     */
    @NotNull
    default Set<StructureDependency> dependencies() {
        return Collections.emptySet();
    }

    @Override
    default boolean getAsBoolean() {
        return test(StructureActivationContext.empty());
    }

    @SafeVarargs
    @NotNull
    static <T> StructureCondition<T> withDependencies(
            @NotNull StructureCondition<T> condition,
            @NotNull StructureDependency... dependencies) {
        return withDependencies(condition, new LinkedHashSet<>(Arrays.asList(dependencies)));
    }

    @NotNull
    static <T> StructureCondition<T> withDependencies(
            @NotNull StructureCondition<T> condition,
            @NotNull Set<StructureDependency> dependencies) {
        Set<StructureDependency> copied =
                Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
        return new StructureCondition<T>() {
            @Override
            public boolean test(@NotNull StructureActivationContext<T> context) {
                return condition.test(context);
            }

            @NotNull
            @Override
            public Set<StructureDependency> dependencies() {
                return copied;
            }
        };
    }
}
