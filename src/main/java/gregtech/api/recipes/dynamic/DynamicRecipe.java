package gregtech.api.recipes.dynamic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One candidate inside a {@link DynamicRecipeMap}.
 *
 * <p>A dynamic recipe is split into a cheap {@link #matches(Object) condition} and a
 * {@link #resolve(Object) resolver}. Several candidates may be registered under the same key;
 * the map uses the condition to decide which of them apply to the caller's context and only
 * then invokes the resolver. This is what allows two recipes with identical item/fluid inputs
 * to coexist: the key describes the physical inputs, the condition describes the state that
 * tells them apart.
 *
 * <p>When a candidate matches but cannot be used, the resolver should throw a
 * {@link DynamicRecipeRejection}. The map records the reason and continues with the remaining
 * candidates instead of failing the whole lookup. Any other runtime exception is treated as a
 * programming error and propagates.
 *
 * <p>Implementations must be side effect free: lookups may run every tick and may invoke
 * {@link #resolve(Object)} more than once for the same candidate.
 *
 * @param <C> caller supplied context (world, machine, decoded NBT, ...)
 * @param <R> resolved result (recipe, operation, product, ...)
 */
public interface DynamicRecipe<C, R> {

    /**
     * Resolves this candidate for the given context.
     *
     * @return the result, or {@code null} when the candidate is not applicable. A {@code null}
     *         result is skipped silently; use {@link DynamicRecipeRejection} when the reason is
     *         worth reporting to the player.
     * @throws DynamicRecipeRejection when the candidate matches but is unusable in this context
     */
    @Nullable
    R resolve(@NotNull C context);

    /**
     * Cheap applicability test. It must not mutate the context and should avoid doing the work
     * that {@link #resolve(Object)} already does.
     */
    default boolean matches(@NotNull C context) {
        return true;
    }

    /** Higher priority candidates are considered first. Ties keep registration order. */
    default int priority() {
        return 0;
    }

    /** Relative weight for {@link DynamicRecipeMap.Selection#WEIGHTED}. Values below one are skipped. */
    default int weight() {
        return 1;
    }

    /** Stable identifier used in diagnostics. */
    @NotNull
    default String id() {
        return getClass().getName();
    }

    /** Creates a recipe with a condition and a resolver. */
    @NotNull
    static <C, R> DynamicRecipe<C, R> of(@NotNull Predicate<C> matches, @NotNull Function<C, R> resolver) {
        return of("anonymous", matches, resolver);
    }

    /** Creates a named recipe with a condition and a resolver. */
    @NotNull
    static <C, R> DynamicRecipe<C, R> of(@NotNull String id, @NotNull Predicate<C> matches,
                                         @NotNull Function<C, R> resolver) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(matches, "matches");
        Objects.requireNonNull(resolver, "resolver");
        return new DynamicRecipe<C, R>() {

            @Override
            public boolean matches(@NotNull C context) {
                return matches.test(context);
            }

            @Override
            @Nullable
            public R resolve(@NotNull C context) {
                return resolver.apply(context);
            }

            @Override
            @NotNull
            public String id() {
                return id;
            }
        };
    }
}
