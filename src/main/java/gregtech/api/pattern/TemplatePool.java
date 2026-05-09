package gregtech.api.pattern;

import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Global pool of multiblock pattern templates with soft-reference caching.
 * Provides centralized lifecycle management, statistics, and bulk eviction.
 *
 * <p>Each registered template is identified by a unique string key (typically the machine's
 * ResourceLocation string or a composite key for multi-variant machines).
 * When JVM memory pressure is high, unused templates (those not held by any active controller)
 * are eligible for garbage collection. They are transparently re-created on next demand.
 *
 * <p>Usage for single-ID machines:
 * <pre>{@code
 * private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
 *     .register("gregtech:electric_blast_furnace", () ->
 *         DeclarativePatternBuilder.start()
 *             .where('S', selfPredicate(new ResourceLocation("gregtech", "electric_blast_furnace")))
 *             .aisle(...)
 *             .buildTemplate()
 *     );
 *
 * @Override
 * protected BlockPatternTemplate createStructureTemplate() {
 *     return TEMPLATE.get();
 * }
 * }</pre>
 *
 * <p>Usage for multi-variant machines:
 * <pre>{@code
 * private static SoftTemplate getTemplate(int tier) {
 *     return TemplatePool.getInstance().register(
 *         "gregtech:large_miner/" + tier,
 *         () -> buildTemplate(tier)
 *     );
 * }
 * }</pre>
 *
 * <p>For core high-frequency machines that should never be evicted, continue using
 * {@link LazyTemplate} directly.
 *
 * @see SoftTemplate for the individual soft-reference holder
 * @see LazyTemplate for permanent (never-evicted) caching
 */
public final class TemplatePool {

    private static final TemplatePool INSTANCE = new TemplatePool();

    /** All registered soft templates, keyed by unique identifier */
    private final ConcurrentHashMap<String, SoftTemplate> pool = new ConcurrentHashMap<>();

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
     * Called by {@link SoftTemplate} when a template is recreated after GC reclaim.
     * Tracks global recreation count for monitoring.
     */
    static void onTemplateRecreated() {
        INSTANCE.totalRecreations.incrementAndGet();
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.debug("[TemplatePool] A template was recreated after GC reclaim. " +
                    "Total recreations: {}", INSTANCE.totalRecreations.get());
        }
    }

    /**
     * Register a template factory under the given key. Idempotent: calling with the same key
     * multiple times returns the existing {@link SoftTemplate} without replacing it.
     *
     * <p>This is the primary entry point for machine classes to declare their template.
     *
     * @param key     unique identifier (e.g. "gregtech:electric_blast_furnace" or
     *                "gregtech:large_turbine/steam")
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
     * Get a previously registered template by key and resolve it.
     * Returns null if the key was never registered.
     *
     * <p>Prefer using the {@link SoftTemplate} reference directly (returned by {@link #register})
     * instead of repeated key lookups.
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
     * Check if a key has been registered.
     *
     * @param key the key to check
     * @return true if registered
     */
    public boolean isRegistered(@NotNull String key) {
        return pool.containsKey(key);
    }

    /**
     * Force eviction of all templates in the pool.
     * Active controllers still holding strong references will keep their templates alive,
     * but the pool's soft references will be cleared.
     *
     * <p>Useful for dimension unload, world change, or explicit memory cleanup commands.
     */
    public void evictAll() {
        pool.values().forEach(SoftTemplate::invalidate);
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.info("[TemplatePool] evictAll() called. {} templates invalidated.", pool.size());
        }
    }

    /**
     * Force eviction of a specific template by key.
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
        return false;
    }

    /**
     * Get a snapshot of pool statistics for debugging and profiling.
     *
     * @return current pool stats
     */
    @NotNull
    public PoolStats getStats() {
        int loaded = 0;
        int total = pool.size();
        long totalRecreationsPerTemplate = 0;
        for (SoftTemplate st : pool.values()) {
            if (st.isLoaded()) {
                loaded++;
            }
            totalRecreationsPerTemplate += st.getRecreationCount();
        }
        return new PoolStats(total, loaded, totalRegistrations.get(),
                totalRecreations.get(), totalRecreationsPerTemplate);
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

        /** Number of templates currently loaded in memory (not reclaimed) */
        public int getLoaded() {
            return loaded;
        }

        /** Total number of register() calls (including idempotent duplicates) */
        public int getTotalRegistrations() {
            return totalRegistrations;
        }

        /** Total number of recreations across all templates (global counter) */
        public int getTotalRecreations() {
            return totalRecreations;
        }

        /** Sum of per-template recreation counts */
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
