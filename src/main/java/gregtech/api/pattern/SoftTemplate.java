package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Memory-pressure-sensitive lazy holder for {@link BlockPatternTemplate}.
 * Uses {@link SoftReference} so templates can be GC'd when no controller instance holds a strong
 * reference. Re-creates via factory on next access if reclaimed.
 *
 * <p>Includes an anti-thrashing mechanism: after a template is (re-)created, it is pinned via a
 * strong reference for a configurable minimum lifetime ({@link #MIN_PIN_DURATION_MS}).
 * This prevents rapid GC→recreate→GC cycles under memory pressure.
 *
 * <p>Best for environments with hundreds of multiblock types where most are rarely used.
 * For core high-frequency machines, prefer {@link LazyTemplate} which never releases.
 *
 * <p>Usage:
 * <pre>{@code
 * private static final SoftTemplate TEMPLATE = SoftTemplate.of(() ->
 *     DeclarativePatternBuilder.start()
 *         .where('S', selfPredicate(...))
 *         .aisle(...)
 *         .buildTemplate()
 * );
 *
 * @Override
 * protected BlockPatternTemplate createStructureTemplate() {
 *     return TEMPLATE.get();
 * }
 * }</pre>
 *
 * <p>Thread safety is guaranteed via double-checked locking with volatile fields.
 *
 * @see LazyTemplate for permanent (never-evicted) caching
 * @see TemplatePool for centralized pool management and statistics
 */
public final class SoftTemplate {

    /**
     * Minimum duration (in milliseconds) to pin a newly created template via strong reference.
     * Prevents rapid GC thrashing when memory pressure is high but the template is still in use.
     * Default: 30 seconds.
     */
    private static final long MIN_PIN_DURATION_MS = 30_000L;

    private final Supplier<BlockPatternTemplate> factory;

    // --- Soft-reference caching with anti-thrash pin ---

    private volatile SoftReference<BlockPatternTemplate> softRef;

    /**
     * Strong reference pin that keeps the template alive for at least {@link #MIN_PIN_DURATION_MS}
     * after creation. Cleared by {@link #get()} once the pin duration expires.
     */
    private volatile BlockPatternTemplate pin;

    /** System.nanoTime() at which the pin was set */
    private volatile long pinTimestampNanos;

    // --- Statistics ---

    private final AtomicInteger recreationCount = new AtomicInteger();

    private SoftTemplate(@NotNull Supplier<BlockPatternTemplate> factory) {
        this.factory = factory;
    }

    /**
     * Create a soft-reference template holder with the given factory.
     * The factory may be called multiple times if the template is reclaimed and re-requested.
     *
     * @param factory supplier that builds the template (called on first access and after GC reclaim)
     * @return a new SoftTemplate instance
     */
    @NotNull
    public static SoftTemplate of(@NotNull Supplier<BlockPatternTemplate> factory) {
        return new SoftTemplate(factory);
    }

    /**
     * Get the cached template. If reclaimed by GC, re-creates via factory.
     * Thread-safe via double-checked locking on the soft reference.
     *
     * <p>After creation, the template is pinned with a strong reference for
     * {@link #MIN_PIN_DURATION_MS} to prevent thrashing.
     *
     * @return the shared immutable template (never null)
     */
    @NotNull
    public BlockPatternTemplate get() {
        // Fast path: check pin first (strong reference, cheapest check)
        BlockPatternTemplate pinned = pin;
        if (pinned != null) {
            // Check if pin has expired
            if (System.nanoTime() - pinTimestampNanos >= MIN_PIN_DURATION_MS * 1_000_000L) {
                pin = null; // Release pin, let SoftReference manage lifetime
            }
            return pinned;
        }

        // Check soft reference
        SoftReference<BlockPatternTemplate> currentRef = softRef;
        BlockPatternTemplate result = (currentRef != null) ? currentRef.get() : null;
        if (result != null) {
            return result;
        }

        // Slow path: need to create or re-create
        synchronized (this) {
            // Double-check inside lock
            pinned = pin;
            if (pinned != null) {
                return pinned;
            }
            currentRef = softRef;
            result = (currentRef != null) ? currentRef.get() : null;
            if (result != null) {
                return result;
            }

            // (Re-)create the template
            boolean isRecreation = recreationCount.get() > 0;
            result = factory.get();
            softRef = new SoftReference<>(result);

            // Pin with strong reference to prevent immediate GC thrashing
            pin = result;
            pinTimestampNanos = System.nanoTime();

            recreationCount.incrementAndGet();
            if (isRecreation) {
                TemplatePool.onTemplateRecreated();
            }

            return result;
        }
    }

    /**
     * Force eviction: clear both pin and soft reference.
     * The template will be recreated on next {@link #get()} call.
     */
    public void invalidate() {
        pin = null;
        softRef = null;
    }

    /**
     * @return true if the template is currently loaded in memory (either pinned or soft-reachable)
     */
    public boolean isLoaded() {
        if (pin != null) return true;
        SoftReference<BlockPatternTemplate> r = softRef;
        return r != null && r.get() != null;
    }

    /**
     * @return the number of times the template has been created (1 = initial, 2+ = recreations)
     */
    public int getCreationCount() {
        return recreationCount.get();
    }

    /**
     * @return the number of times the template was recreated after GC reclaim (creationCount - 1)
     */
    public int getRecreationCount() {
        int count = recreationCount.get();
        return count > 0 ? count - 1 : 0;
    }
}
