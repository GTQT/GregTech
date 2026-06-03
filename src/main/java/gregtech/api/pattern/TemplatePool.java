package gregtech.api.pattern;

import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Global pool of multiblock pattern templates with soft-reference caching.
 * Provides centralized lifecycle management, statistics, and bulk eviction.
 *
 * <p>Supports two types of pooled references:
 * <ul>
 *   <li>{@link SoftTemplate} — for {@link BlockPatternTemplate} instances (legacy)</li>
 *   <li>{@link SoftReferenceHolder} — for any generic type (e.g. {@link StructureDefinition},
 *       compiled {@link MultiPiecePattern})</li>
 * </ul>
 *
 * <p>Each registered entry is identified by a unique string key. When JVM memory pressure
 * is high, unused entries are eligible for garbage collection and transparently re-created
 * on next demand.
 *
 * @see SoftTemplate for BlockPatternTemplate-specific soft-reference holder
 * @see SoftReferenceHolder for generic soft-reference holder
 */
public final class TemplatePool {

    private static final TemplatePool INSTANCE = new TemplatePool();

    /** All registered soft templates (BlockPatternTemplate), keyed by unique identifier */
    private final ConcurrentHashMap<String, SoftTemplate> pool = new ConcurrentHashMap<>();

    /** All registered generic soft-reference holders, keyed by unique identifier */
    private final ConcurrentHashMap<String, SoftReferenceHolder<?>> genericPool = new ConcurrentHashMap<>();

    // --- Global statistics ---

    private final AtomicInteger totalRegistrations = new AtomicInteger();
    private final AtomicInteger totalRecreations = new AtomicInteger();

    private TemplatePool() {}

    /**
     * @return the singleton pool instance
     */
    @NotNull
    public static TemplatePool getInstance() {
        return INSTANCE;
    }

    /**
     * Called by {@link PooledReference} when a pooled value is recreated after GC reclaim.
     * Tracks global recreation count for monitoring.
     */
    public static void onHolderRecreated() {
        INSTANCE.totalRecreations.incrementAndGet();
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.debug("[TemplatePool] A pooled reference was recreated after GC reclaim. " +
                    "Total recreations: {}", INSTANCE.totalRecreations.get());
        }
    }

    /**
     * Backward-compatible alias for {@link #onHolderRecreated()}.
     * Called by the old SoftTemplate recreation path.
     */
    static void onTemplateRecreated() {
        onHolderRecreated();
    }

    /**
     * Register a template factory under the given key. Idempotent: calling with the same key
     * multiple times returns the existing {@link SoftTemplate} without replacing it.
     *
     * @param key     unique identifier
     * @param factory supplier that builds the {@link BlockPatternTemplate}
     * @return the registered (or existing) SoftTemplate for the given key
     */
    @NotNull
    public SoftTemplate register(@NotNull String key, @NotNull Supplier<BlockPatternTemplate> factory) {
        return pool.computeIfAbsent(key, k -> {
            totalRegistrations.incrementAndGet();
            return SoftTemplate.of(factory);
        });
    }

    /**
     * Register a generic value factory under the given key. Idempotent.
     *
     * @param key     unique identifier
     * @param factory supplier that builds the value
     * @param <T>     the value type
     * @return the registered (or existing) SoftReferenceHolder for the given key
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> SoftReferenceHolder<T> registerGeneric(@NotNull String key, @NotNull Supplier<T> factory) {
        SoftReferenceHolder<T> holder = (SoftReferenceHolder<T>) genericPool.computeIfAbsent(key, k -> {
            totalRegistrations.incrementAndGet();
            return SoftReferenceHolder.of(factory);
        });
        return holder;
    }

    /**
     * Convenience method for registering {@link StructureDefinition} instances.
     *
     * @param key     unique identifier (e.g. "gregtech:distillation_tower")
     * @param factory supplier that builds the StructureDefinition
     * @return the registered (or existing) SoftReferenceHolder for the given key
     */
    @NotNull
    public SoftReferenceHolder<StructureDefinition> registerStructure(
            @NotNull String key, @NotNull Supplier<StructureDefinition> factory) {
        return registerGeneric(key, factory);
    }

    /**
     * Get a previously registered template by key and resolve it.
     *
     * @param key the registration key
     * @return the resolved template, or null if key not found
     */
    @Nullable
    public BlockPatternTemplate get(@NotNull String key) {
        SoftTemplate st = pool.get(key);
        return st != null ? st.get() : null;
    }

    /**
     * Get the {@link SoftTemplate} holder by key, without resolving (loading) the template.
     *
     * @param key the registration key
     * @return the SoftTemplate, or null if never registered
     */
    @Nullable
    public SoftTemplate getHolder(@NotNull String key) {
        return pool.get(key);
    }

    /**
     * Check if a key has been registered (in either pool).
     *
     * @param key the key to check
     * @return true if registered
     */
    public boolean isRegistered(@NotNull String key) {
        return pool.containsKey(key) || genericPool.containsKey(key);
    }

    /**
     * Build an EnumMap-based template cache for enum-keyed multi-variant machines.
     *
     * @param poolKeyPrefix the pool key prefix
     * @param enumClass     the variant enum class
     * @param factory       function that creates a template Supplier for each enum constant
     * @param <V>           the enum type
     * @return unmodifiable EnumMap of variant → SoftTemplate
     */
    @NotNull
    public static <V extends Enum<V>> Map<V, SoftTemplate> buildEnumCache(
            @NotNull String poolKeyPrefix,
            @NotNull Class<V> enumClass,
            @NotNull Function<V, Supplier<BlockPatternTemplate>> factory) {
        Map<V, SoftTemplate> cache = new EnumMap<>(enumClass);
        TemplatePool pool = getInstance();
        for (V value : enumClass.getEnumConstants()) {
            String key = poolKeyPrefix + "/" + value.name().toLowerCase();
            cache.put(value, pool.register(key, factory.apply(value)));
        }
        return cache;
    }

    /**
     * Force eviction of all entries in both pools.
     */
    public void evictAll() {
        pool.values().forEach(SoftTemplate::invalidate);
        genericPool.values().forEach(SoftReferenceHolder::invalidate);
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.info("[TemplatePool] evictAll() called. {} templates + {} generic entries invalidated.",
                    pool.size(), genericPool.size());
        }
    }

    /**
     * Force eviction of a specific entry by key (from either pool).
     *
     * @param key the key to evict
     * @return true if the key existed and was evicted
     */
    public boolean evict(@NotNull String key) {
        SoftTemplate st = pool.get(key);
        if (st != null) {
            st.invalidate();
            return true;
        }
        SoftReferenceHolder<?> holder = genericPool.get(key);
        if (holder != null) {
            holder.invalidate();
            return true;
        }
        return false;
    }

    /**
     * Get a snapshot of pool statistics for debugging and profiling.
     * Covers both the BlockPatternTemplate pool and the generic pool.
     *
     * @return current pool stats
     */
    @NotNull
    public PoolStats getStats() {
        int loaded = 0;
        int total = pool.size() + genericPool.size();
        long perTemplateRecreations = 0;
        for (SoftTemplate st : pool.values()) {
            if (st.isLoaded()) {
                loaded++;
            }
            perTemplateRecreations += st.getRecreationCount();
        }
        for (SoftReferenceHolder<?> h : genericPool.values()) {
            if (h.isLoaded()) {
                loaded++;
            }
            perTemplateRecreations += h.getRecreationCount();
        }
        return new PoolStats(total, loaded, totalRegistrations.get(),
                totalRecreations.get(), perTemplateRecreations);
    }

    /**
     * Immutable snapshot of pool statistics.
     */
    public static final class PoolStats {

        private final int registered;
        private final int loaded;
        private final int totalRegistrations;
        private final int totalRecreations;
        private final long perTemplateTotalRecreations;

        PoolStats(int registered, int loaded, int totalRegistrations,
                  int totalRecreations, long perTemplateTotalRecreations) {
            this.registered = registered;
            this.loaded = loaded;
            this.totalRegistrations = totalRegistrations;
            this.totalRecreations = totalRecreations;
            this.perTemplateTotalRecreations = perTemplateTotalRecreations;
        }

        /** Number of unique keys registered in the pool */
        public int getRegistered() {
            return registered;
        }

        /** Number of entries currently loaded in memory (not reclaimed) */
        public int getLoaded() {
            return loaded;
        }

        /** Total number of register() calls (including idempotent duplicates) */
        public int getTotalRegistrations() {
            return totalRegistrations;
        }

        /** Total number of recreations across all entries (global counter) */
        public int getTotalRecreations() {
            return totalRecreations;
        }

        /** Sum of per-entry recreation counts */
        public long getPerTemplateTotalRecreations() {
            return perTemplateTotalRecreations;
        }

        @Override
        public String toString() {
            return String.format("TemplatePool[registered=%d, loaded=%d/%d, recreations=%d]",
                    registered, loaded, registered, totalRecreations);
        }
    }
}
