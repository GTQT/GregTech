package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Global pool of multiblock pattern templates with soft-reference caching.
 * Provides centralized lifecycle management, statistics, and bulk eviction.
 *
 * <p>Each registered entry is identified by a unique string key. When JVM memory pressure
 * is high, unused entries are eligible for garbage collection and transparently re-created
 * on next demand.
 *
 * @see SoftReferenceHolder for generic soft-reference holder
 */
public final class TemplatePool {

    private static final TemplatePool INSTANCE = new TemplatePool();

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
     * Tracks global recreation count for monitoring. Public because {@link PooledReference}
     * (declared public so {@code SoftReferenceHolder} can hold a reference to it) lives in
     * the {@code internal} sub-package and needs to invoke this hook.
     */
    public static void onHolderRecreated() {
        INSTANCE.totalRecreations.incrementAndGet();
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.debug("[TemplatePool] A pooled reference was recreated after GC reclaim. " +
                    "Total recreations: {}", INSTANCE.totalRecreations.get());
        }
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
    public <T extends MultiblockControllerBase> SoftReferenceHolder<StructureDefinition<T>> registerStructure(
            @NotNull String key, @NotNull Supplier<StructureDefinition<T>> factory) {
        return registerGeneric(key, factory);
    }

    /**
     * Check if a key has been registered.
     *
     * @param key the key to check
     * @return true if registered
     */
    public boolean isRegistered(@NotNull String key) {
        return genericPool.containsKey(key);
    }

    /**
     * Force eviction of all entries.
     */
    public void evictAll() {
        genericPool.values().forEach(SoftReferenceHolder::invalidate);
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.info("[TemplatePool] evictAll() called. {} generic entries invalidated.",
                    genericPool.size());
        }
    }

    /**
     * Force eviction of a specific entry by key.
     *
     * @param key the key to evict
     * @return true if the key existed and was evicted
     */
    public boolean evict(@NotNull String key) {
        SoftReferenceHolder<?> holder = genericPool.get(key);
        if (holder != null) {
            holder.invalidate();
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
        int total = genericPool.size();
        long perTemplateRecreations = 0;
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
