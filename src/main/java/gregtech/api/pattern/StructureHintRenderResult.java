package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-cell outcome for hint rendering.
 *
 * <p>This separates "which hint entry point was called" from whether that
 * entry point actually produced a renderable hint.
 */
public final class StructureHintRenderResult {

    public enum Outcome {
        RENDERED,
        SKIPPED,
        FAILED
    }

    public enum Source {
        TRIGGER,
        CONTEXT
    }

    @NotNull
    private final Outcome outcome;
    @NotNull
    private final Source source;
    @Nullable
    private final String message;

    private StructureHintRenderResult(@NotNull Outcome outcome,
                                      @NotNull Source source,
                                      @Nullable String message) {
        this.outcome = outcome;
        this.source = source;
        this.message = message;
    }

    @NotNull
    public static StructureHintRenderResult rendered(@NotNull Source source) {
        return new StructureHintRenderResult(Outcome.RENDERED, source, null);
    }

    @NotNull
    public static StructureHintRenderResult skipped(@NotNull Source source) {
        return new StructureHintRenderResult(Outcome.SKIPPED, source, null);
    }

    @NotNull
    public static StructureHintRenderResult failed(@NotNull Source source,
                                                   @Nullable String message) {
        return new StructureHintRenderResult(Outcome.FAILED, source, message);
    }

    @NotNull
    public Outcome getOutcome() {
        return outcome;
    }

    @NotNull
    public Source getSource() {
        return source;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public boolean rendered() {
        return outcome == Outcome.RENDERED;
    }

    public boolean skipped() {
        return outcome == Outcome.SKIPPED;
    }

    public boolean failed() {
        return outcome == Outcome.FAILED;
    }
}
