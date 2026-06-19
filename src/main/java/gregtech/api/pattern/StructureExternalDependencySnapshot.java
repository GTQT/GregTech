package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captured values for a plan's external dependencies.
 */
public final class StructureExternalDependencySnapshot {

    public static final class ChangeSet {

        @NotNull
        private final Set<StructureExternalDependencyKey<?>> changedKeys;
        @NotNull
        private final Map<StructureExternalDependencyKey<?>, String> failures;

        private ChangeSet(
                @NotNull Set<StructureExternalDependencyKey<?>> changedKeys,
                @NotNull Map<StructureExternalDependencyKey<?>, String> failures) {
            this.changedKeys = Collections.unmodifiableSet(new LinkedHashSet<>(changedKeys));
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
        }

        @NotNull
        public Set<StructureExternalDependencyKey<?>> getChangedKeys() {
            return changedKeys;
        }

        @NotNull
        public Map<StructureExternalDependencyKey<?>, String> getFailures() {
            return failures;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }

        @NotNull
        public String describeFailures() {
            return describeFailureMap(failures);
        }
    }

    @NotNull
    private final Map<StructureExternalDependencyKey<?>, Object> values;
    @NotNull
    private final Map<StructureExternalDependencyKey<?>, String> failures;

    private StructureExternalDependencySnapshot(
            @NotNull Map<StructureExternalDependencyKey<?>, Object> values,
            @NotNull Map<StructureExternalDependencyKey<?>, String> failures) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
    }

    @NotNull
    public static StructureExternalDependencySnapshot capture(
            @NotNull Collection<StructureExternalDependencyKey<?>> keys,
            @Nullable MultiblockControllerBase controller) {
        Map<StructureExternalDependencyKey<?>, Object> values = new LinkedHashMap<>();
        Map<StructureExternalDependencyKey<?>, String> failures = new LinkedHashMap<>();
        for (StructureExternalDependencyKey<?> key : keys) {
            try {
                values.put(key, key.snapshot(controller));
            } catch (RuntimeException e) {
                failures.put(key, "snapshot failed: " + describeThrowable(e));
            }
        }
        return new StructureExternalDependencySnapshot(values, failures);
    }

    @NotNull
    public Map<StructureExternalDependencyKey<?>, Object> getValues() {
        return values;
    }

    @NotNull
    public Map<StructureExternalDependencyKey<?>, String> getFailures() {
        return failures;
    }

    @Nullable
    public Object get(@NotNull StructureExternalDependencyKey<?> key) {
        return values.get(key);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    @NotNull
    public String describeFailures() {
        return describeFailureMap(failures);
    }

    public boolean isEquivalentTo(@NotNull StructureExternalDependencySnapshot previous) {
        ChangeSet changes = changesFrom(previous);
        return !changes.hasFailures() && changes.getChangedKeys().isEmpty();
    }

    @NotNull
    public Set<StructureExternalDependencyKey<?>> changedKeys(
            @NotNull StructureExternalDependencySnapshot previous) {
        return changesFrom(previous).getChangedKeys();
    }

    @NotNull
    public ChangeSet changesFrom(
            @NotNull StructureExternalDependencySnapshot previous) {
        LinkedHashSet<StructureExternalDependencyKey<?>> keys = new LinkedHashSet<>();
        keys.addAll(previous.values.keySet());
        keys.addAll(values.keySet());
        keys.addAll(previous.failures.keySet());
        keys.addAll(failures.keySet());

        LinkedHashSet<StructureExternalDependencyKey<?>> changed = new LinkedHashSet<>();
        Map<StructureExternalDependencyKey<?>, String> changeFailures = new LinkedHashMap<>();
        for (StructureExternalDependencyKey<?> key : keys) {
            if (previous.failures.containsKey(key)) {
                changed.add(key);
                putFailure(changeFailures, key,
                        "previous " + previous.failures.get(key));
            }
            if (failures.containsKey(key)) {
                changed.add(key);
                putFailure(changeFailures, key,
                        "current " + failures.get(key));
            }
            if (previous.failures.containsKey(key) || failures.containsKey(key)) {
                continue;
            }
            if (!previous.values.containsKey(key) || !values.containsKey(key)) {
                changed.add(key);
                continue;
            }
            Object left = previous.values.get(key);
            Object right = values.get(key);
            try {
                if (!key.equivalentObjects(left, right)) {
                    changed.add(key);
                }
            } catch (RuntimeException e) {
                changed.add(key);
                putFailure(changeFailures, key,
                        "comparison failed: " + describeThrowable(e));
            }
        }
        return new ChangeSet(changed, changeFailures);
    }

    @NotNull
    public String describe() {
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<StructureExternalDependencyKey<?>, Object> entry : values.entrySet()) {
            parts.add(entry.getKey().getId() + "=" + entry.getValue());
        }
        for (Map.Entry<StructureExternalDependencyKey<?>, String> entry : failures.entrySet()) {
            parts.add(entry.getKey().getId() + "=(" + entry.getValue() + ")");
        }
        return String.join(", ", parts);
    }

    private static void putFailure(
            @NotNull Map<StructureExternalDependencyKey<?>, String> target,
            @NotNull StructureExternalDependencyKey<?> key,
            @NotNull String detail) {
        String existing = target.get(key);
        target.put(key, existing == null ? detail : existing + "; " + detail);
    }

    @NotNull
    private static String describeFailureMap(
            @NotNull Map<StructureExternalDependencyKey<?>, String> source) {
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<StructureExternalDependencyKey<?>, String> entry : source.entrySet()) {
            parts.add(entry.getKey().getId() + ": " + entry.getValue());
        }
        return String.join(", ", parts);
    }

    @NotNull
    private static String describeThrowable(@NotNull RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : "(" + message + ")");
    }
}
