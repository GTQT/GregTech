package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Contains an context used for storing temporary data
 * related to current check and shared between all predicates doing it
 */
public class PatternMatchContext {

    private final Map<String, Object> data = new HashMap<>();

    private boolean neededFlip = false;

    public void reset() {
        this.data.clear();
        this.neededFlip = false;
    }

    public void set(String key, Object value) {
        this.data.put(key, value);
    }

    public void remove(String key) {
        this.data.remove(key);
    }

    public int getInt(String key) {
        return data.containsKey(key) ? (int) data.get(key) : 0;
    }

    public void increment(String key, int value) {
        set(key, getOrDefault(key, 0) + value);
    }

    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) data.getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public <T> T getOrCreate(String key, Supplier<T> creator) {
        T result = get(key);
        if (result == null) {
            result = creator.get();
            set(key, result);
        }
        return result;
    }

    public <T> T getOrPut(String key, T initialValue) {
        T result = get(key);
        if (result == null) {
            result = initialValue;
            set(key, result);
        }
        return result;
    }

    @NotNull
    public Set<Map.Entry<String, Object>> entrySet() {
        return data.entrySet();
    }

    @NotNull
    public Checkpoint checkpoint() {
        return new Checkpoint(copy());
    }

    public void restore(@NotNull Checkpoint checkpoint) {
        replaceWith(checkpoint.context);
    }

    public boolean neededFlip() {
        return neededFlip;
    }

    public void setNeededFlip(boolean neededFlip) {
        this.neededFlip = neededFlip;
    }

    /**
     * Create an isolated copy suitable for speculative structure matching.
     * Common mutable container values are copied so a failed branch cannot
     * mutate the parent context through a shared collection.
     */
    @NotNull
    public PatternMatchContext copy() {
        PatternMatchContext copy = new PatternMatchContext();
        copy.replaceWith(this);
        return copy;
    }

    /**
     * Replace this context with an isolated copy of another context.
     */
    public void replaceWith(@NotNull PatternMatchContext other) {
        this.data.clear();
        for (Map.Entry<String, Object> entry : other.data.entrySet()) {
            this.data.put(entry.getKey(), copyValue(entry.getValue()));
        }
        this.neededFlip = other.neededFlip;
    }

    private static Object copyValue(Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = newContainer(value.getClass(), new HashMap<>());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(copyValue(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = newContainer(value.getClass(), new HashSet<>());
            for (Object element : set) {
                copy.add(copyValue(element));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = newContainer(value.getClass(), new ArrayList<>(list.size()));
            for (Object element : list) {
                copy.add(copyValue(element));
            }
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            Collection<Object> copy = newContainer(
                    value.getClass(), new ArrayList<>(collection.size()));
            for (Object element : collection) {
                copy.add(copyValue(element));
            }
            return copy;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(valueClass.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, copyValue(Array.get(value, i)));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T newContainer(Class<?> containerClass, T fallback) {
        try {
            return (T) containerClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return fallback;
        }
    }

    public static final class Checkpoint {

        private final PatternMatchContext context;

        private Checkpoint(@NotNull PatternMatchContext context) {
            this.context = context;
        }
    }
}
