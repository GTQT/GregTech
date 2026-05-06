package gregtech.common.metatileentities.multi.electric.godforge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Auto-generated from GT5-Unofficial ForgeOfGodsStructureString.java
// Character mapping (used in controller's where() clauses):
// ' ' (space) -> any (automatically handled by FactoryBlockPattern)
// 'S' -> selfPredicate() (controller)
// 'A' -> Hatches (InputBus, InputHatch, OutputBus) or TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING
// 'B' -> SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING
// 'C' -> CELESTIAL_MATTER_GUIDANCE_CASING
// 'D' -> BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING
// 'E' -> TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING
// 'F' -> STELLAR_ENERGY_SIPHON_CASING
// 'G' -> REMOTE_GRAVITON_FLOW_MODULATOR
// 'H' -> SPATIALLY_TRANSCENDENT_GRAVITATIONAL_LENS (Glass)
// 'J' -> Module Hatches or SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING
public final class ForgeOfGodsStructureString {

    private static final String STRUCTURE_ROOT = "/assets/gregtech/godforge/structures/";

    private ForgeOfGodsStructureString() {}

    public static final String[][] BEAM_SHAFT = loadStructure("beam_shaft.txt");
    public static final String[][] FIRST_RING = loadStructure("first_ring.txt");
    public static final String[][] FIRST_RING_AIR = replaceLetters(FIRST_RING, "L");
    public static final String[][] SECOND_RING = loadStructure("second_ring.txt");
    public static final String[][] THIRD_RING = loadStructure("third_ring.txt");
    public static final String[][] SECOND_RING_AIR = replaceLetters(SECOND_RING, "L");
    public static final String[][] THIRD_RING_AIR = replaceLetters(THIRD_RING, "L");

    private static String[][] loadStructure(String fileName) {
        String path = STRUCTURE_ROOT + fileName;
        InputStream stream = ForgeOfGodsStructureString.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing Forge of Gods structure resource: " + path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String[]> layers = new ArrayList<>();
            List<String> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (layers.isEmpty() && rows.isEmpty() && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                if ("---".equals(line)) {
                    layers.add(rows.toArray(new String[0]));
                    rows.clear();
                } else {
                    rows.add(line);
                }
            }
            if (!rows.isEmpty()) {
                layers.add(rows.toArray(new String[0]));
            }
            validateStructure(fileName, layers);
            return layers.toArray(new String[0][]);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Forge of Gods structure resource: " + path, e);
        }
    }

    private static void validateStructure(String fileName, List<String[]> layers) {
        if (layers.isEmpty()) {
            throw new IllegalStateException("Forge of Gods structure resource is empty: " + fileName);
        }

        int expectedHeight = layers.get(0).length;
        int expectedWidth = expectedHeight == 0 ? 0 : layers.get(0)[0].length();
        for (int z = 0; z < layers.size(); z++) {
            String[] layer = layers.get(z);
            if (layer.length != expectedHeight) {
                throw new IllegalStateException("Invalid Forge of Gods structure resource " + fileName +
                        ": layer " + z + " has height " + layer.length + ", expected " + expectedHeight);
            }
            for (int y = 0; y < layer.length; y++) {
                if (layer[y].length() != expectedWidth) {
                    throw new IllegalStateException("Invalid Forge of Gods structure resource " + fileName +
                            ": row " + y + " in layer " + z + " has width " + layer[y].length() +
                            ", expected " + expectedWidth);
                }
            }
        }
    }

    private static String[][] replaceLetters(String[][] array, String replacement) {
        String[][] out = new String[array.length][];
        for (int i = 0; i < array.length; i++) {
            out[i] = new String[array[i].length];
            for (int j = 0; j < array[i].length; j++) {
                out[i][j] = array[i][j].replaceAll("[A-Z]", replacement);
            }
        }
        return out;
    }
}
