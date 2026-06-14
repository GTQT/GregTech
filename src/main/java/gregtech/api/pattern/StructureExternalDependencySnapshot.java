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

    @NotNull
    private final Map<StructureExternalDependencyKey<?>, Object> values;

    private StructureExternalDependencySnapshot(
            @NotNull Map<StructureExternalDependencyKey<?>, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @NotNull
    public static StructureExternalDependencySnapshot capture(
            @NotNull Collection<StructureExternalDependencyKey<?>> keys,
            @Nullable MultiblockControllerBase controller) {
        Map<StructureExternalDependencyKey<?>, Object> values = new LinkedHashMap<>();
        for (StructureExternalDependencyKey<?> key : keys) {
            values.put(key, key.snapshot(controller));
        }
        return new StructureExternalDependencySnapshot(values);
    }

    @NotNull
    public Map<StructureExternalDependencyKey<?>, Object> getValues() {
        return values;
    }

    @Nullable
    public Object get(@NotNull StructureExternalDependencyKey<?> key) {
        return values.get(key);
    }

    public boolean isEquivalentTo(@NotNull StructureExternalDependencySnapshot previous) {
        return changedKeys(previous).isEmpty();
    }

    @NotNull
    public Set<StructureExternalDependencyKey<?>> changedKeys(
            @NotNull StructureExternalDependencySnapshot previous) {
        LinkedHashSet<StructureExternalDependencyKey<?>> keys = new LinkedHashSet<>();
        keys.addAll(previous.values.keySet());
        keys.addAll(values.keySet());

        LinkedHashSet<StructureExternalDependencyKey<?>> changed = new LinkedHashSet<>();
        for (StructureExternalDependencyKey<?> key : keys) {
            if (!previous.values.containsKey(key) || !values.containsKey(key)) {
                changed.add(key);
                continue;
            }
            Object left = previous.values.get(key);
            Object right = values.get(key);
            if (!key.equivalentObjects(left, right)) {
                changed.add(key);
            }
        }
        return Collections.unmodifiableSet(changed);
    }

    @NotNull
    public String describe() {
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<StructureExternalDependencyKey<?>, Object> entry : values.entrySet()) {
            parts.add(entry.getKey().getId() + "=" + entry.getValue());
        }
        return String.join(", ", parts);
    }
}
