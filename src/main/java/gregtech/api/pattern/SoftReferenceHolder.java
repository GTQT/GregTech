package gregtech.api.pattern;

import gregtech.api.pattern.internal.PooledReference;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Generic soft-reference holder with anti-thrash pinning.
 * This is the generic equivalent of {@link SoftTemplate}, usable for any type
 * (not just {@link BlockPatternTemplate}).
 *
 * <p>Uses {@link PooledReference} internally for the same GC-reclaimable caching
 * with anti-thrashing protection as {@link SoftTemplate}.
 *
 * <p>Usage:
 * <pre>{@code
 * private static final SoftReferenceHolder<MultiPiecePattern> COMPILED =
 *     SoftReferenceHolder.of(() -> StructureCompiler.compile(definition));
 * }</pre>
 *
 * @param <T> the type of value held by this reference
 * @see TemplatePool#registerGeneric(String, Supplier) for centralized pool management
 */
public final class SoftReferenceHolder<T> {

    private final PooledReference<T> ref;

    private SoftReferenceHolder(Supplier<T> factory) {
        this.ref = new PooledReference<>(factory);
    }

    /**
     * Create a soft-reference holder with the given factory.
     *
     * @param factory supplier that builds the value (called on first access and after GC reclaim)
     * @param <T>     the value type
     * @return a new SoftReferenceHolder instance
     */
    @NotNull
    public static <T> SoftReferenceHolder<T> of(@NotNull Supplier<T> factory) {
        return new SoftReferenceHolder<>(factory);
    }

    /**
     * Get the cached value. If reclaimed by GC, re-creates via factory.
     *
     * @return the cached value
     */
    @NotNull
    public T get() {
        return ref.get();
    }

    /**
     * Force eviction: clear both pin and soft reference.
     */
    public void invalidate() {
        ref.invalidate();
    }

    /**
     * @return true if the value is currently loaded in memory
     */
    public boolean isLoaded() {
        return ref.isLoaded();
    }

    /**
     * @return the number of times the value was recreated after GC reclaim
     */
    public int getRecreationCount() {
        return ref.getRecreationCount();
    }
}
