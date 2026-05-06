package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Thread-safe lazy holder for {@link BlockPatternTemplate} instances.
 * Provides zero-lock-contention caching suitable for static fields in multiblock controllers.
 *
 * <p>Usage:
 * <pre>{@code
 * private static final LazyTemplate BEAM_SHAFT = LazyTemplate.of(() ->
 *     FactoryBlockPattern.start(RIGHT, UP, FRONT)
 *         .aisle(...)
 *         .where(...)
 *         .buildTemplate()
 * );
 *
 * // With explicit centerOffset (for sub-pieces without selfPredicate):
 * private static final LazyTemplate FIRST_RING = LazyTemplate.of(() ->
 *     FactoryBlockPattern.start(RIGHT, UP, FRONT)
 *         .aisle(...)
 *         .where(...)
 *         .buildTemplate(new int[]{63, 14, 0, 0, 0})
 * );
 *
 * // Access:
 * BlockPatternTemplate template = BEAM_SHAFT.get();
 * }</pre>
 *
 * <p>Thread safety is guaranteed by the double-checked locking pattern with volatile.
 * After first initialization, subsequent calls to {@link #get()} are a single volatile read
 * with no synchronization overhead.
 */
public final class LazyTemplate {

    private final Supplier<BlockPatternTemplate> factory;
    private volatile BlockPatternTemplate instance;

    private LazyTemplate(@NotNull Supplier<BlockPatternTemplate> factory) {
        this.factory = factory;
    }

    /**
     * Create a lazy template holder with the given factory.
     * The factory is called at most once, on first access.
     *
     * @param factory supplier that builds the template (called lazily, at most once)
     * @return a new LazyTemplate instance
     */
    @NotNull
    public static LazyTemplate of(@NotNull Supplier<BlockPatternTemplate> factory) {
        return new LazyTemplate(factory);
    }

    /**
     * Get the cached template, initializing it on first access.
     * Thread-safe via double-checked locking.
     *
     * @return the shared immutable template
     */
    @NotNull
    public BlockPatternTemplate get() {
        BlockPatternTemplate result = instance;
        if (result == null) {
            synchronized (this) {
                result = instance;
                if (result == null) {
                    result = factory.get();
                    instance = result;
                }
            }
        }
        return result;
    }

    /**
     * Invalidate the cached template, forcing re-creation on next access.
     * Useful for hot-reload scenarios during development.
     */
    public void invalidate() {
        instance = null;
    }

    /**
     * @return true if the template has been initialized
     */
    public boolean isInitialized() {
        return instance != null;
    }
}
