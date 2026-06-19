package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Typed reducer key for piece-local structure contributions.
 *
 * @param <E> emitted value type
 * @param <A> folded aggregate type
 */
public final class StructureContributionKey<E, A> {

    @FunctionalInterface
    public interface LegacyProjection<A> {

        void project(@NotNull PatternMatchContext context, @Nullable A value);
    }

    public static final class Validation {

        private static final Validation SUCCESS = new Validation(true, null);

        private final boolean success;
        @Nullable
        private final String errorMessage;

        private Validation(boolean success, @Nullable String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        @NotNull
        public static Validation success() {
            return SUCCESS;
        }

        @NotNull
        public static Validation failure(@NotNull String errorMessage) {
            return new Validation(false, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    static final class ReductionException extends RuntimeException {

        private ReductionException(@NotNull String message) {
            super(message);
        }
    }

    @NotNull
    private final String id;
    @NotNull
    private final Object schemaToken;
    @NotNull
    private final Supplier<A> identity;
    @NotNull
    private final BiFunction<A, E, A> reducer;
    @NotNull
    private final Function<A, Validation> validator;
    @Nullable
    private final LegacyProjection<A> legacyProjection;
    @NotNull
    private final UnaryOperator<E> emissionCopier;
    @NotNull
    private final UnaryOperator<A> aggregateCopier;

    @NotNull
    public static <E, A> StructureContributionKey<E, A> create(
            @NotNull String id,
            @NotNull Supplier<A> identity,
            @NotNull BiFunction<A, E, A> reducer) {
        return new StructureContributionKey<>(
                id, new Object(), identity, reducer, ignored -> Validation.success(), null,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public static <E, A> StructureContributionKey<E, A> create(
            @NotNull String id,
            @NotNull String schemaId,
            @NotNull Supplier<A> identity,
            @NotNull BiFunction<A, E, A> reducer,
            @NotNull Function<A, Validation> validator,
            @Nullable LegacyProjection<A> legacyProjection,
            @NotNull UnaryOperator<E> emissionCopier,
            @NotNull UnaryOperator<A> aggregateCopier) {
        return new StructureContributionKey<>(
                id, schemaId, identity, reducer, validator, legacyProjection,
                emissionCopier, aggregateCopier);
    }

    private StructureContributionKey(@NotNull String id,
                                     @NotNull Object schemaToken,
                                     @NotNull Supplier<A> identity,
                                     @NotNull BiFunction<A, E, A> reducer,
                                     @NotNull Function<A, Validation> validator,
                                     @Nullable LegacyProjection<A> legacyProjection,
                                     @NotNull UnaryOperator<E> emissionCopier,
                                     @NotNull UnaryOperator<A> aggregateCopier) {
        if (id.isEmpty() || id.indexOf(':') <= 0 || id.endsWith(":")) {
            throw new IllegalArgumentException("Contribution key id must be namespaced: " + id);
        }
        this.id = id;
        this.schemaToken = schemaToken;
        this.identity = identity;
        this.reducer = reducer;
        this.validator = validator;
        this.legacyProjection = legacyProjection;
        this.emissionCopier = emissionCopier;
        this.aggregateCopier = aggregateCopier;
    }

    @NotNull
    public static StructureContributionKey<Integer, Integer> sum(@NotNull String id) {
        return create(id, "sum", () -> 0, Integer::sum,
                ignored -> Validation.success(), null,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public static <T extends Comparable<T>> StructureContributionKey<T, T> min(@NotNull String id) {
        return create(id, "min", () -> null,
                (current, emitted) -> current == null || emitted.compareTo(current) < 0 ? emitted : current,
                ignored -> Validation.success(), null,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public static <T extends Comparable<T>> StructureContributionKey<T, T> max(@NotNull String id) {
        return create(id, "max", () -> null,
                (current, emitted) -> current == null || emitted.compareTo(current) > 0 ? emitted : current,
                ignored -> Validation.success(), null,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public static <T> StructureContributionKey<T, T> uniform(@NotNull String id) {
        return uniform(id, null);
    }

    @NotNull
    public static <T> StructureContributionKey<T, T> uniform(
            @NotNull String id,
            @Nullable LegacyProjection<T> legacyProjection) {
        return create(id, "uniform", () -> null, (current, emitted) -> {
            if (current == null || Objects.equals(current, emitted)) {
                return emitted;
            }
            throw new ReductionException(
                    "Contribution key '" + id + "' requires a uniform value");
        }, ignored -> Validation.success(), legacyProjection,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public static <T> StructureContributionKey<T, Set<T>> setUnion(@NotNull String id) {
        return create(id, "set-union", LinkedHashSet::new, (current, emitted) -> {
            Set<T> result = new LinkedHashSet<>(current);
            result.add(emitted);
            return result;
        }, ignored -> Validation.success(), null,
                UnaryOperator.identity(), value -> Collections.unmodifiableSet(new LinkedHashSet<>(value)));
    }

    @NotNull
    public static <T> StructureContributionKey<T, List<T>> orderedList(@NotNull String id) {
        return create(id, "ordered-list", ArrayList::new, (current, emitted) -> {
            List<T> result = new ArrayList<>(current);
            result.add(emitted);
            return result;
        }, ignored -> Validation.success(), null,
                UnaryOperator.identity(), value -> Collections.unmodifiableList(new ArrayList<>(value)));
    }

    @NotNull
    public static <T> StructureContributionKey<T, T> firstNonNull(@NotNull String id) {
        return create(id, "first-non-null", () -> null,
                (current, emitted) -> current == null ? emitted : current,
                ignored -> Validation.success(), null,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public static <T> StructureContributionKey<T, T> lastNonNull(@NotNull String id) {
        return create(id, "last-non-null", () -> null,
                (current, emitted) -> emitted == null ? current : emitted,
                ignored -> Validation.success(), null,
                UnaryOperator.identity(), UnaryOperator.identity());
    }

    @NotNull
    public String getId() {
        return id;
    }

    boolean isCompatibleWith(@NotNull StructureContributionKey<?, ?> other) {
        return this == other || id.equals(other.id) && schemaToken.equals(other.schemaToken);
    }

    @Nullable
    A identity() {
        A value = identity.get();
        return value == null ? null : aggregateCopier.apply(value);
    }

    @Nullable
    A reduce(@Nullable A aggregate, @Nullable E emission) {
        return reducer.apply(aggregate, copyEmission(emission));
    }

    @Nullable
    E copyEmission(@Nullable E emission) {
        return emission == null ? null : emissionCopier.apply(emission);
    }

    @NotNull
    Validation validate(@Nullable A aggregate) {
        return validator.apply(aggregate);
    }

    @Nullable
    A copyAggregate(@Nullable A aggregate) {
        return aggregate == null ? null : aggregateCopier.apply(aggregate);
    }

    void project(@NotNull PatternMatchContext context, @Nullable A aggregate) {
        if (legacyProjection != null) {
            legacyProjection.project(context, copyAggregate(aggregate));
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
