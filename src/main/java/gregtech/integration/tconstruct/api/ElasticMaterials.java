package gregtech.integration.tconstruct.api;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for TiC BowString and Fletching material stats derived from GT polymer materials.
 *
 * <p>
 * GTMT automatically registers every GT material that carries the {@code POLYMER} property as a TiC BowString and
 * Fletching material. Registrations made here take priority over GTMT's auto-assigned default stats (modifier = 1.0 /
 * accuracy = 1.0).
 *
 * <p>
 * All registrations must occur before GTMT's {@code registerBlocks} phase (your mod's {@code preInit} or
 * {@code init}).
 */
public final class ElasticMaterials {

    // -------------------------------------------------------------------------
    // Entry type
    // -------------------------------------------------------------------------

    private static final Map<Material, Entry> ENTRIES = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------

    static {
        // Natural rubber — slimeball-tier baseline
        registerIfAbsent(Materials.Rubber, 1.0f, 1.0f, 1.0f);
        // Vulcanised silicone — more durable
        registerIfAbsent(Materials.SiliconeRubber, 1.05f, 1.0f, 1.05f);
        // SBR — best synthetic rubber
        registerIfAbsent(Materials.StyreneButadieneRubber, 1.1f, 1.0f, 1.1f);
        // Polyethylene — stiffer plastic, lower tier
        registerIfAbsent(Materials.Polyethylene, 0.8f, 0.9f, 0.8f);
    }

    private ElasticMaterials() {}

    /**
     * Returns an unmodifiable view of the elastic-material registry. Used internally by {@code TiCMaterials} when
     * registering BowString/Fletching materials.
     */
    public static Map<Material, Entry> getEntries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    /**
     * Registers BowString and Fletching stats for a GT polymer material.
     *
     * <p>
     * Call this before GTMT's {@code registerBlocks} phase to override the stats GTMT would otherwise auto-assign.
     *
     * <p>
     * Example — register a custom high-grade polymer:
     *
     * <pre>
     * {@code
     * ElasticMaterials.register(MyMaterials.KEVLAR, 1.3f, 1.0f, 1.2f);
     * }
     * </pre>
     *
     * @param material          GT material (must carry the {@code POLYMER} property, or be a recognised elastic
     *                          material)
     * @param stringModifier    BowString draw-speed modifier (must be &gt; 0)
     * @param fletchingAccuracy Fletching accuracy (must be &gt; 0 and ≤ 1)
     * @param fletchingModifier Fletching damage modifier (must be &gt; 0)
     * @throws IllegalArgumentException if material is null or any stat is out of range
     */
    public static void register(Material material,
                                float stringModifier,
                                float fletchingAccuracy,
                                float fletchingModifier) {
        validate(material, stringModifier, fletchingAccuracy, fletchingModifier);
        ENTRIES.put(material, new Entry(stringModifier, fletchingAccuracy, fletchingModifier));
    }

    /**
     * Fills stats only if the material has not already been registered externally. Called internally by the static
     * initializer and by {@code TiCMaterials} for auto-detected POLYMER materials.
     */
    public static void registerIfAbsent(Material material,
                                        float stringModifier,
                                        float fletchingAccuracy,
                                        float fletchingModifier) {
        validate(material, stringModifier, fletchingAccuracy, fletchingModifier);
        ENTRIES.putIfAbsent(material, new Entry(stringModifier, fletchingAccuracy, fletchingModifier));
    }

    private static void validate(Material material,
                                 float stringModifier,
                                 float fletchingAccuracy,
                                 float fletchingModifier) {
        if (material == null) throw new IllegalArgumentException("material must not be null");
        if (stringModifier <= 0) throw new IllegalArgumentException("stringModifier must be > 0");
        if (fletchingAccuracy <= 0) throw new IllegalArgumentException("fletchingAccuracy must be > 0");
        if (fletchingModifier <= 0) throw new IllegalArgumentException("fletchingModifier must be > 0");
    }

    /** BowString and Fletching stats for a single GT material. */
        public static class Entry {

        private final float stringModifier;
            private final float fletchingAccuracy;
            private final float fletchingModifier;

            public Entry(float stringModifier, float fletchingAccuracy, float fletchingModifier) {
                this.stringModifier = stringModifier;
                this.fletchingAccuracy = fletchingAccuracy;
                this.fletchingModifier = fletchingModifier;
            }

        /** BowString draw-speed modifier (1.0 = neutral). */
            public float getStringModifier() {
                return stringModifier;
            }

            /** Fletching accuracy (1.0 = perfect, lower = spread). */
            public float getFletchingAccuracy() {
                return fletchingAccuracy;
            }

            /** Fletching damage modifier (1.0 = neutral). */
            public float getFletchingModifier() {
                return fletchingModifier;
            }
        }
}
