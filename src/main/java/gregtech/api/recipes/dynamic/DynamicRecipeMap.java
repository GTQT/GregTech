package gregtech.api.recipes.dynamic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A context aware alternative to the GT ingredient tree for recipes that share identical inputs.
 *
 * <p>GT's {@code RecipeMap} stores exactly one recipe per ingredient path: the second registration
 * with the same inputs is dropped as a duplicate. That is correct for ordinary processing recipes,
 * but systems such as GTQTCore's biology operations and DrTech's crop breeding produce different
 * NBT results from the same item inputs depending on state that is not part of the ingredient list
 * (culture genomes, crop parents, ...). This map gives those systems a proper home:
 *
 * <ol>
 *     <li>Several {@link DynamicRecipe candidates} may live under the same key.</li>
 *     <li>Each candidate decides with {@link DynamicRecipe#matches(Object)} whether it applies to the
 *         caller supplied context.</li>
 *     <li>{@link #lookup(Object, Object, Selection, Random)} either returns the first usable candidate,
 *         a weighted random candidate, or every usable candidate.</li>
 *     <li>Candidates that match but reject themselves with {@link DynamicRecipeRejection} are skipped
 *         and their reasons are collected for the machine GUI / logs.</li>
 * </ol>
 *
 * <p>The map is a registry, not a scheduler: it never mutates inventories or consumes power. It only
 * answers "which recipe applies". Callers remain responsible for validating resources and executing.
 *
 * <p>Overclocking, parallel processing and GT's multithreaded / cross-recipe schedulers are
 * execution concerns and are deliberately not implemented here. A consumer that wants those
 * effects applies its own processing profile to the resolved candidate (GTQTCore biology already
 * runs GT's {@code OverclockingLogic} in {@code BiologyProcessingProfile}; DrTech applies its
 * breeding-machine profile in {@code CropMachineRecipeLogic}). The map itself never multiplies
 * outputs, power or duration.
 *
 * <p>Registration is expected during mod initialization; lookups are lock free and may happen from
 * the server thread every tick. The implementation keeps an immutable snapshot per key, so a
 * concurrent registration cannot corrupt an in-flight lookup.
 *
 * @param <K> lookup key (an input signature, a device id, a channel, ...)
 * @param <C> caller supplied context
 * @param <R> resolved result
 */
public final class DynamicRecipeMap<K, C, R> {

    /** How {@link #lookup(Object, Object, Selection, Random)} picks among matching candidates. */
    public enum Selection {
        /** Highest priority matching candidate that resolves successfully. */
        FIRST,
        /** Weighted random candidate, retried when a candidate rejects itself. */
        WEIGHTED,
        /** Every matching candidate that resolves successfully. */
        ALL
    }

    private final ConcurrentMap<K, Bucket<C, R>> buckets = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile Selection selection;

    public DynamicRecipeMap() {
        this(Selection.FIRST);
    }

    public DynamicRecipeMap(@NotNull Selection selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    @NotNull
    public Selection getSelection() {
        return selection;
    }

    public void setSelection(@NotNull Selection selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    // ==================== registration ====================

    /**
     * Registers a candidate. Duplicate registrations of the exact same instance are ignored.
     */
    public void add(@NotNull K key, @NotNull DynamicRecipe<C, R> recipe) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(recipe, "recipe");
        Bucket<C, R> bucket = buckets.computeIfAbsent(key, ignored -> new Bucket<>());
        synchronized (bucket) {
            for (Entry<C, R> entry : bucket.entries) {
                if (entry.recipe == recipe) {
                    return;
                }
            }
            List<Entry<C, R>> next = new ArrayList<>(bucket.entries);
            next.add(new Entry<>(recipe, sequence.getAndIncrement()));
            Collections.sort(next);
            bucket.entries = Collections.unmodifiableList(next);
        }
    }

    /** Convenience overload for condition + resolver pairs. */
    public void add(@NotNull K key, @NotNull Predicate<C> matches, @NotNull Function<C, R> resolver) {
        add(key, DynamicRecipe.of(matches, resolver));
    }

    /** Removes a candidate, returning whether it was present under the key. */
    public boolean remove(@NotNull K key, @NotNull DynamicRecipe<C, R> recipe) {
        Bucket<C, R> bucket = buckets.get(key);
        if (bucket == null) {
            return false;
        }
        synchronized (bucket) {
            List<Entry<C, R>> next = new ArrayList<>(bucket.entries);
            for (int index = 0; index < next.size(); index++) {
                if (next.get(index).recipe.equals(recipe)) {
                    next.remove(index);
                    bucket.entries = Collections.unmodifiableList(next);
                    if (next.isEmpty()) {
                        buckets.remove(key, bucket);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public void clear(@NotNull K key) {
        buckets.remove(key);
    }

    public void clear() {
        buckets.clear();
    }

    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    public int size() {
        int size = 0;
        for (Bucket<C, R> bucket : buckets.values()) {
            size += bucket.entries.size();
        }
        return size;
    }

    @NotNull
    public Set<K> keys() {
        return Collections.unmodifiableSet(buckets.keySet());
    }

    // ==================== lookup ====================

    /**
     * Looks up a result using the map's configured selection and a thread local random source.
     */
    @Nullable
    public R find(@NotNull K key, @NotNull C context) {
        return find(key, context, selection, null);
    }

    /**
     * Looks up a result.
     *
     * @param random random source for {@link Selection#WEIGHTED}; may be {@code null}
     */
    @Nullable
    public R find(@NotNull K key, @NotNull C context, @NotNull Selection selection, @Nullable Random random) {
        return lookup(key, context, selection, random).result();
    }

    /** Looks up every usable result regardless of the configured selection. */
    @NotNull
    public List<R> findAll(@NotNull K key, @NotNull C context) {
        return lookup(key, context, Selection.ALL, null).results();
    }

    /**
     * Full lookup result, including rejection diagnostics.
     */
    @NotNull
    public Lookup<R> lookup(@NotNull K key, @NotNull C context) {
        return lookup(key, context, selection, null);
    }

    /**
     * Full lookup result.
     *
     * @param random random source for {@link Selection#WEIGHTED}; falls back to
     *               {@link ThreadLocalRandom} when {@code null}
     */
    @NotNull
    public Lookup<R> lookup(@NotNull K key, @NotNull C context, @NotNull Selection selection,
                            @Nullable Random random) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(selection, "selection");
        Bucket<C, R> bucket = buckets.get(key);
        if (bucket == null || bucket.entries.isEmpty()) {
            return Lookup.empty();
        }
        List<Entry<C, R>> entries = bucket.entries;
        if (selection == Selection.ALL) {
            return collect(entries, context);
        }
        if (selection == Selection.WEIGHTED) {
            return weighted(entries, context,
                    random != null ? random : ThreadLocalRandom.current());
        }
        return first(entries, context);
    }

    /**
     * Returns the ordered candidates whose condition currently passes, without resolving them.
     * This is useful when the caller has to run its own pipeline (resource checks, batch
     * planning) over every candidate instead of asking for one result.
     */
    @NotNull
    public List<DynamicRecipe<C, R>> candidates(@NotNull K key, @NotNull C context) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(context, "context");
        Bucket<C, R> bucket = buckets.get(key);
        if (bucket == null) {
            return Collections.emptyList();
        }
        List<DynamicRecipe<C, R>> result = new ArrayList<>();
        for (Entry<C, R> entry : bucket.entries) {
            if (entry.recipe.matches(context)) {
                result.add(entry.recipe);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ==================== selection algorithms ====================

    @NotNull
    private Lookup<R> first(List<Entry<C, R>> entries, C context) {
        int matched = 0;
        List<String> rejections = new ArrayList<>();
        for (Entry<C, R> entry : entries) {
            if (!entry.recipe.matches(context)) {
                continue;
            }
            matched++;
            try {
                R result = entry.recipe.resolve(context);
                if (result != null) {
                    return Lookup.single(result, matched, rejections);
                }
            } catch (DynamicRecipeRejection rejection) {
                rejections.add(label(entry, rejection));
            }
        }
        return new Lookup<>(Collections.emptyList(), matched, rejections);
    }

    @NotNull
    private Lookup<R> weighted(List<Entry<C, R>> entries, C context, Random random) {
        List<Entry<C, R>> eligible = new ArrayList<>();
        for (Entry<C, R> entry : entries) {
            if (entry.recipe.weight() > 0 && entry.recipe.matches(context)) {
                eligible.add(entry);
            }
        }
        int matched = eligible.size();
        List<String> rejections = new ArrayList<>();
        while (!eligible.isEmpty()) {
            long total = 0;
            for (Entry<C, R> entry : eligible) {
                total += entry.recipe.weight();
            }
            long roll = total <= Integer.MAX_VALUE
                    ? random.nextInt((int) total)
                    : (long) (random.nextDouble() * total);
            Entry<C, R> picked = null;
            for (int index = 0; index < eligible.size(); index++) {
                Entry<C, R> candidate = eligible.get(index);
                if (roll < candidate.recipe.weight()) {
                    picked = candidate;
                    eligible.remove(index);
                    break;
                }
                roll -= candidate.recipe.weight();
            }
            if (picked == null) {
                // Only reachable through floating point rounding; take the last candidate.
                picked = eligible.remove(eligible.size() - 1);
            }
            try {
                R result = picked.recipe.resolve(context);
                if (result != null) {
                    return Lookup.single(result, matched, rejections);
                }
            } catch (DynamicRecipeRejection rejection) {
                rejections.add(label(picked, rejection));
            }
        }
        return new Lookup<>(Collections.emptyList(), matched, rejections);
    }

    @NotNull
    private Lookup<R> collect(List<Entry<C, R>> entries, C context) {
        int matched = 0;
        List<R> results = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        for (Entry<C, R> entry : entries) {
            if (!entry.recipe.matches(context)) {
                continue;
            }
            matched++;
            try {
                R result = entry.recipe.resolve(context);
                if (result != null) {
                    results.add(result);
                }
            } catch (DynamicRecipeRejection rejection) {
                rejections.add(label(entry, rejection));
            }
        }
        return new Lookup<>(results, matched, rejections);
    }

    @NotNull
    private static String label(Entry<?, ?> entry, DynamicRecipeRejection rejection) {
        return entry.recipe.id() + ": " + rejection.reason();
    }

    // ==================== lookup result ====================

    /** Immutable outcome of a lookup. */
    public static final class Lookup<R> {

        private final List<R> results;
        private final int matched;
        private final List<String> rejections;

        private Lookup(List<R> results, int matched, List<String> rejections) {
            this.results = results.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(results));
            this.matched = matched;
            this.rejections = rejections.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(rejections));
        }

        private static <R> Lookup<R> single(R result, int matched, List<String> rejections) {
            return new Lookup<>(Collections.singletonList(result), matched, rejections);
        }

        private static <R> Lookup<R> empty() {
            return new Lookup<>(Collections.emptyList(), 0, Collections.emptyList());
        }

        /** First result, or {@code null} when none was produced. */
        @Nullable
        public R result() {
            return results.isEmpty() ? null : results.get(0);
        }

        @NotNull
        public List<R> results() {
            return results;
        }

        public boolean isPresent() {
            return !results.isEmpty();
        }

        /** Number of candidates whose condition passed and which were actually considered. */
        public int matched() {
            return matched;
        }

        /**
         * Reasons from candidates that matched but rejected themselves, in consideration order.
         */
        @NotNull
        public List<String> rejections() {
            return rejections;
        }

        @NotNull
        public String rejectionSummary() {
            return String.join("; ", rejections);
        }
    }

    // ==================== internals ====================

    private static final class Bucket<C, R> {

        private volatile List<Entry<C, R>> entries = Collections.emptyList();
    }

    private static final class Entry<C, R> implements Comparable<Entry<C, R>> {

        private final DynamicRecipe<C, R> recipe;
        private final long sequence;

        private Entry(DynamicRecipe<C, R> recipe, long sequence) {
            this.recipe = recipe;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(Entry<C, R> other) {
            int priority = Integer.compare(other.recipe.priority(), recipe.priority());
            if (priority != 0) {
                return priority;
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
