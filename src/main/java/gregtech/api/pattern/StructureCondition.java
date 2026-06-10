package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

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

    @Override
    default boolean getAsBoolean() {
        return test(StructureActivationContext.empty());
    }
}
