package gregtech.api.pattern;

import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Global registry of external item sources consulted by survival placement.
 *
 * <p>Integrations (an ME network, a storage addon, ...) register one {@link StructureItemSource}; the build never
 * talks to a concrete storage mod directly. Sources are only consulted when the player inventory is short and the
 * request allows network stock.
 */
public final class StructureItemSourceRegistry {

    private static final List<StructureItemSource> SOURCES = new ArrayList<>();

    private StructureItemSourceRegistry() {}

    /**
     * Registers an external item source.
     *
     * @param source source consulted for every survival placement
     */
    public static void register(@NotNull StructureItemSource source) {
        SOURCES.add(Objects.requireNonNull(source));
    }

    static List<StructureItemSource> getSources() {
        return Collections.unmodifiableList(SOURCES);
    }

    /**
     * @param player player the build belongs to
     * @return whether at least one registered source reports the player as wired up to an external stock
     */
    public static boolean hasLinkedSource(@NotNull EntityPlayer player) {
        for (StructureItemSource source : SOURCES) {
            try {
                if (source.isLinked(player)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // A misbehaving integration must not break structure previews or builds.
            }
        }
        return false;
    }

    /** @return whether any source is registered, so the UI can hide network-stock controls */
    public static boolean hasSources() {
        return !SOURCES.isEmpty();
    }
}
