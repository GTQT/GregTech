package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Thread-safe lazy holder for {@link BlockPatternTemplate} instances.
 * Provides zero-lock-contention caching suitable for static fields in multiblock controllers.
 *
 * @deprecated This class permanently retains templates in memory and cannot release them under
 * memory pressure. Use {@link SoftTemplate} (optionally via {@link TemplatePool}) instead, which
 * provides GC-reclaimable caching with anti-thrashing protection.
 */
@Deprecated
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
     * @deprecated Use {@link SoftTemplate#of(Supplier)} or {@link TemplatePool#register} instead.
     */
    @Deprecated
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
