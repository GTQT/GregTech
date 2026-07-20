package gregtech.integration.tic.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for custom TiC harvest-level display names above Cobalt (level 4).
 *
 * <p>
 * TiC natively names levels 0–4 (Wood, Stone, Iron, Diamond, Cobalt). GTMT automatically assigns names for levels 5 and
 * above based on the first GT material encountered at each level. Registrations made here take priority over those
 * auto-assigned names.
 *
 * <p>
 * All registrations must occur before GTMT's {@code registerBlocks} phase (your mod's {@code preInit} or
 * {@code init}).
 */
public final class HarvestLevels {

    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

    private HarvestLevels() {}

    /**
     * Returns an unmodifiable view of the harvest-level name registry. Used internally by {@code TiCMaterials} when
     * applying names to TiC.
     */
    public static Map<Integer, String> getNames() {
        return Collections.unmodifiableMap(NAMES);
    }

    /**
     * Registers a display name for a TiC harvest level above Cobalt (4).
     *
     * <p>
     * Call this before GTMT's {@code registerBlocks} phase to override the name GTMT would otherwise auto-assign from
     * the first GT material seen at that level.
     *
     * <p>
     * Example — name harvest level 5 after a custom material tier:
     *
     * <pre>
     * {@code
     * HarvestLevels.register(5, "Vibranium");
     * }
     * </pre>
     *
     * @param level harvest level to name (must be &gt; 4; TiC natively handles 0–4)
     * @param name  the display name (must not be blank)
     * @throws IllegalArgumentException if level ≤ 4 or name is blank
     */
    public static void register(int level, String name) {
        if (level <= 4) throw new IllegalArgumentException(
                "TiC natively handles harvest levels 0–4; register level > 4 only");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException(
                "name must not be blank");
        NAMES.put(level, name);
    }

    /**
     * Fills a harvest level name only if not already registered externally. Called internally by {@code TiCMaterials}
     * during GT material processing.
     */
    public static void registerIfAbsent(int level, String name) {
        NAMES.putIfAbsent(level, name);
    }
}
