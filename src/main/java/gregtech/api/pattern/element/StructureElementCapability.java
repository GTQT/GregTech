package gregtech.api.pattern.element;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Operations that a compiled structure element can execute safely.
 */
public enum StructureElementCapability {

    LIVE_MATCH,
    SNAPSHOT_MATCH,
    PREVIEW,
    HINTS,
    CREATIVE_PLACEMENT,
    SURVIVAL_PLACEMENT;

    private static final Set<StructureElementCapability> STANDARD =
            immutable(EnumSet.of(
                    LIVE_MATCH, PREVIEW, HINTS,
                    CREATIVE_PLACEMENT, SURVIVAL_PLACEMENT));
    private static final Set<StructureElementCapability> SNAPSHOT_SAFE =
            immutable(EnumSet.allOf(StructureElementCapability.class));

    @NotNull
    public static Set<StructureElementCapability> standard() {
        return STANDARD;
    }

    @NotNull
    public static Set<StructureElementCapability> snapshotSafe() {
        return SNAPSHOT_SAFE;
    }

    @NotNull
    public static Set<StructureElementCapability> copyOf(
            @NotNull Set<StructureElementCapability> capabilities) {
        if (capabilities.isEmpty()) {
            return Collections.emptySet();
        }
        return immutable(EnumSet.copyOf(capabilities));
    }

    @NotNull
    private static Set<StructureElementCapability> immutable(
            @NotNull EnumSet<StructureElementCapability> capabilities) {
        return Collections.unmodifiableSet(capabilities);
    }
}
