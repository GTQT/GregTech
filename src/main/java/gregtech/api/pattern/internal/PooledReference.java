package gregtech.api.pattern.internal;

import gregtech.api.pattern.TemplatePool;

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Generic soft-reference holder with anti-thrash pinning.
 * Internal implementation shared by {@link gregtech.api.pattern.SoftTemplate}
 * and {@link gregtech.api.pattern.SoftReferenceHolder}.
 *
 * <p>Uses double-checked locking with volatile fields for thread safety.
 * After (re-)creation, the value is pinned via a strong reference for
 * {@link #MIN_PIN_DURATION_NS} to prevent rapid GC→recreate→GC cycles.
 *
 * <p>Note: this class is declared public solely so that the public
 * {@link gregtech.api.pattern.SoftTemplate} and
 * {@link gregtech.api.pattern.SoftReferenceHolder} types (which live in
 * {@code gregtech.api.pattern}) can hold a reference to it. The {@code internal}
 * sub-package is the project's signal that this is an implementation detail
 * that external code should not depend on directly — prefer the higher-level
 * {@link gregtech.api.pattern.SoftTemplate} / {@link gregtech.api.pattern.SoftReferenceHolder}
 * facade classes.
 *
 * @param <T> the type of value held by this reference
 */
public final class PooledReference<T> {

    /** Minimum pin duration: 30 seconds in nanoseconds */
    private static final long MIN_PIN_DURATION_NS = 30_000_000_000L;

    private final Supplier<T> factory;

    // --- Soft-reference caching with anti-thrash pin ---

    private volatile SoftReference<T> softRef;

    /**
     * Strong reference pin that keeps the value alive for at least
     * {@link #MIN_PIN_DURATION_NS} after creation.
     */
    private volatile T pin;

    /** System.nanoTime() at which the pin was set */
    private volatile long pinTimestampNanos;

    // --- Statistics ---

    private final AtomicInteger recreationCount = new AtomicInteger();

    /**
     * Create a new pooled reference backed by the given factory.
     * The factory is called once on first {@link #get()} access, and again after
     * a GC reclaim. Public so {@link gregtech.api.pattern.SoftTemplate} and
     * {@link gregtech.api.pattern.SoftReferenceHolder} can construct instances.
     */
    public PooledReference(Supplier<T> factory) {
        this.factory = factory;
    }

    /**
     * Get the cached value. If reclaimed by GC, re-creates via factory.
     * Thread-safe via double-checked locking.
     *
     * @return the cached value (never null if factory produces non-null)
     */
    public T get() {
        // Fast path: check pin first (strong reference, cheapest check)
        T pinned = pin;
        if (pinned != null) {
            if (System.nanoTime() - pinTimestampNanos >= MIN_PIN_DURATION_NS) {
                pin = null;
            }
            return pinned;
        }

        // Check soft reference
        SoftReference<T> currentRef = softRef;
        T result = (currentRef != null) ? currentRef.get() : null;
        if (result != null) {
            return result;
        }

        // Slow path: need to create or re-create
        synchronized (this) {
            pinned = pin;
            if (pinned != null) {
                return pinned;
            }
            currentRef = softRef;
            result = (currentRef != null) ? currentRef.get() : null;
            if (result != null) {
                return result;
            }

            // (Re-)create the value
            boolean isRecreation = recreationCount.get() > 0;
            result = factory.get();
            softRef = new SoftReference<>(result);

            // Pin with strong reference to prevent immediate GC thrashing
            pin = result;
            pinTimestampNanos = System.nanoTime();

            recreationCount.incrementAndGet();
            if (isRecreation) {
                TemplatePool.onHolderRecreated();
            }

            return result;
        }
    }

    /**
     * Force eviction: clear both pin and soft reference.
     */
    public void invalidate() {
        pin = null;
        softRef = null;
    }

    /**
     * @return true if the value is currently loaded in memory
     */
    public boolean isLoaded() {
        if (pin != null) return true;
        SoftReference<T> r = softRef;
        return r != null && r.get() != null;
    }

    /**
     * @return the number of times the value was recreated after GC reclaim
     */
    public int getRecreationCount() {
        int count = recreationCount.get();
        return count > 0 ? count - 1 : 0;
    }
}
