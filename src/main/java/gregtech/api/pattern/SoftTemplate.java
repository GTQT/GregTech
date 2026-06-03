package gregtech.api.pattern;

import gregtech.api.pattern.internal.PooledReference;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Memory-pressure-sensitive lazy holder for {@link BlockPatternTemplate}.
 * Uses {@link PooledReference} internally for GC-reclaimable caching with anti-thrashing
 * protection. The public API is unchanged from the pre-refactor version.
 *
 * <p>Includes an anti-thrashing mechanism: after a template is (re-)created, it is pinned via a
 * strong reference for a minimum lifetime of 30 seconds.
 * This prevents rapid GC→recreate→GC cycles under memory pressure.
 *
 * <p>Best for environments with hundreds of multiblock types where most are rarely used.
 * For core high-frequency machines, prefer holding a strong static reference to the
 * {@link SoftTemplate} instance — it will not be reclaimed while the static field is alive.
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
 * <p>Thread safety is guaranteed via double-checked locking with volatile fields
 * (delegated to {@link PooledReference}).
 *
 * @see TemplatePool for centralized pool management and statistics
 * @see SoftReferenceHolder for the generic equivalent usable for any type
 */
public final class SoftTemplate {

    private final PooledReference<BlockPatternTemplate> ref;

    private SoftTemplate(@NotNull Supplier<BlockPatternTemplate> factory) {
        this.ref = new PooledReference<>(factory);
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
     * 30 seconds to prevent thrashing.
     *
     * @return the shared immutable template (never null)
     */
    @NotNull
    public BlockPatternTemplate get() {
        return ref.get();
    }

    /**
     * Force eviction: clear both pin and soft reference.
     * The template will be recreated on next {@link #get()} call.
     */
    public void invalidate() {
        ref.invalidate();
    }

    /**
     * @return true if the template is currently loaded in memory (either pinned or soft-reachable)
     */
    public boolean isLoaded() {
        return ref.isLoaded();
    }

    /**
     * @return the number of times the template has been created (1 = initial, 2+ = recreations)
     */
    public int getCreationCount() {
        return ref.getRecreationCount() + 1;
    }

    /**
     * @return the number of times the template was recreated after GC reclaim (creationCount - 1)
     */
    public int getRecreationCount() {
        return ref.getRecreationCount();
    }
}
