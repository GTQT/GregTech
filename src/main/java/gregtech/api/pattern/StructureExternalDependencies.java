package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Standard external dependency keys for non-block state that may influence a
 * structure definition.
 */
public final class StructureExternalDependencies {

    public static final StructureExternalDependencyKey<ControllerModeSnapshot> CONTROLLER_MODE =
            StructureExternalDependencyKey.create(
                    "gregtech:controller_mode",
                    controller -> controller == null
                            ? ControllerModeSnapshot.empty()
                            : controller.getStructureControllerModeSnapshot(),
                    Objects::equals);

    public static final StructureExternalDependencyKey<VersionedSnapshot> CHANNEL_VALUES =
            StructureExternalDependencyKey.create(
                    "gregtech:channel_values",
                    controller -> controller == null
                            ? VersionedSnapshot.empty()
                            : controller.getStructureChannelDependencySnapshot(),
                    Objects::equals);

    public static final StructureExternalDependencyKey<VersionedSnapshot> CONFIGURATION =
            StructureExternalDependencyKey.create(
                    "gregtech:configuration",
                    controller -> controller == null
                            ? VersionedSnapshot.empty()
                            : controller.getStructureConfigDependencySnapshot(),
                    Objects::equals);

    public static final StructureExternalDependencyKey<VersionedSnapshot> UPGRADES =
            StructureExternalDependencyKey.create(
                    "gregtech:upgrades",
                    controller -> controller == null
                            ? VersionedSnapshot.empty()
                            : controller.getStructureUpgradeDependencySnapshot(),
                    Objects::equals);

    private StructureExternalDependencies() {}

    @NotNull
    public static StructureDependency controllerMode() {
        return StructureDependency.external(
                CONTROLLER_MODE, PieceDependencyAspect.CONTROLLER_STATE);
    }

    @NotNull
    public static StructureDependency channelValues() {
        return StructureDependency.external(
                CHANNEL_VALUES, PieceDependencyAspect.CONTROLLER_STATE);
    }

    @NotNull
    public static StructureDependency configuration() {
        return StructureDependency.external(
                CONFIGURATION, PieceDependencyAspect.CONTROLLER_STATE);
    }

    @NotNull
    public static StructureDependency upgrades() {
        return StructureDependency.external(
                UPGRADES, PieceDependencyAspect.CONTROLLER_STATE);
    }

    public static final class ControllerModeSnapshot {

        @Nullable
        private final Object modeValue;
        private final boolean controllable;
        private final boolean workingEnabled;
        private final long generation;

        @NotNull
        public static ControllerModeSnapshot empty() {
            return new ControllerModeSnapshot(null, false, false, 0);
        }

        public ControllerModeSnapshot(@Nullable Object modeValue,
                                      boolean controllable,
                                      boolean workingEnabled,
                                      long generation) {
            this.modeValue = VersionedSnapshot.normalize(modeValue);
            this.controllable = controllable;
            this.workingEnabled = workingEnabled;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ControllerModeSnapshot)) return false;
            ControllerModeSnapshot other = (ControllerModeSnapshot) obj;
            return controllable == other.controllable
                    && workingEnabled == other.workingEnabled
                    && generation == other.generation
                    && Objects.equals(modeValue, other.modeValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modeValue, controllable, workingEnabled, generation);
        }

        @Override
        public String toString() {
            return "ControllerModeSnapshot{"
                    + "modeValue=" + modeValue
                    + ", controllable=" + controllable
                    + ", workingEnabled=" + workingEnabled
                    + ", generation=" + generation
                    + '}';
        }
    }

    public static final class VersionedSnapshot {

        private final long generation;
        @Nullable
        private final Object value;

        @NotNull
        public static VersionedSnapshot empty() {
            return new VersionedSnapshot(0, null);
        }

        @NotNull
        public static VersionedSnapshot of(long generation, @Nullable Object value) {
            return new VersionedSnapshot(generation, normalize(value));
        }

        private VersionedSnapshot(long generation, @Nullable Object value) {
            this.generation = generation;
            this.value = value;
        }

        @Nullable
        static Object normalize(@Nullable Object value) {
            if (value instanceof Map<?, ?>) {
                Map<Object, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    copy.put(normalize(entry.getKey()), normalize(entry.getValue()));
                }
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof Set<?>) {
                Set<Object> copy = new LinkedHashSet<>();
                for (Object element : (Set<?>) value) {
                    copy.add(normalize(element));
                }
                return Collections.unmodifiableSet(copy);
            }
            if (value instanceof Collection<?>) {
                List<Object> copy = new ArrayList<>();
                for (Object element : (Collection<?>) value) {
                    copy.add(normalize(element));
                }
                return Collections.unmodifiableList(copy);
            }
            if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> copy = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    copy.add(normalize(Array.get(value, i)));
                }
                return Collections.unmodifiableList(copy);
            }
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof VersionedSnapshot)) return false;
            VersionedSnapshot other = (VersionedSnapshot) obj;
            return generation == other.generation
                    && Objects.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, value);
        }

        @Override
        public String toString() {
            return "VersionedSnapshot{generation=" + generation
                    + ", value=" + value + '}';
        }
    }
}
