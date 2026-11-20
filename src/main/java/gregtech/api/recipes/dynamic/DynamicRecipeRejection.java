package gregtech.api.recipes.dynamic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Signals that a candidate matched the physical inputs but cannot be used in the current
 * context, for example because a machine tier is too low or a decoded culture state is invalid.
 *
 * <p>This is control flow, not a crash: {@link DynamicRecipeMap} catches it, keeps the reason
 * for diagnostics and tries the next candidate. Stack traces are therefore suppressed.
 */
public class DynamicRecipeRejection extends RuntimeException {

    private final String reason;

    public DynamicRecipeRejection(@NotNull String reason) {
        super(reason);
        this.reason = reason;
    }

    public DynamicRecipeRejection(@NotNull String reason, @Nullable Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    @NotNull
    public String reason() {
        return reason;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
