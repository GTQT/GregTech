package gregtech.common.wireless;

import gregtech.api.util.Mods;
import gregtech.integration.ftb.utility.FTBTeamHelper;

import com.feed_the_beast.ftblib.lib.data.ForgeTeam;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a player UUID to a canonical network UUID.
 * The canonical network UUID determines which wireless energy network a player belongs to.
 * <p>
 * Resolution priority:
 * <ol>
 *   <li>Admin override map (set via /gt wireless join).</li>
 *   <li>If FTB Utilities is loaded and player has a team, use team owner UUID.</li>
 *   <li>Otherwise, use the player's own UUID.</li>
 * </ol>
 */
public final class WirelessTeamResolver {

    /**
     * Admin override map: playerUUID -> forced networkUUID.
     * Persisted in WirelessEnergySavedData.
     */
    private static final Map<UUID, UUID> overrideMap = new ConcurrentHashMap<>();

    private WirelessTeamResolver() {}

    /**
     * Resolves the given actor UUID to the canonical network UUID.
     *
     * @param actorUuid the player UUID to resolve
     * @return the canonical network UUID (override > team owner > player self)
     */
    public static UUID resolveNetworkId(UUID actorUuid) {
        if (actorUuid == null) return null;

        // Priority 1: admin override
        UUID override = overrideMap.get(actorUuid);
        if (override != null) return override;

        // Priority 2: FTB team
        if (!Mods.FTB_UTILITIES.isModLoaded()) {
            return actorUuid;
        }
        return resolveWithFTB(actorUuid);
    }

    private static UUID resolveWithFTB(UUID actorUuid) {
        ForgeTeam team = FTBTeamHelper.getTeam(actorUuid);
        if (team != null && team.owner != null) {
            return team.owner.getId();
        }
        return actorUuid;
    }

    // ==================== Override Management ====================

    /**
     * Forces a player to join a specific network (admin override).
     *
     * @param playerUuid the player to override
     * @param networkOwnerUuid the network owner UUID to join
     */
    public static void setOverride(UUID playerUuid, UUID networkOwnerUuid) {
        overrideMap.put(playerUuid, networkOwnerUuid);
    }

    /**
     * Removes the admin override for a player, reverting to default resolution.
     *
     * @param playerUuid the player to clear override for
     * @return true if an override was removed
     */
    public static boolean removeOverride(UUID playerUuid) {
        return overrideMap.remove(playerUuid) != null;
    }

    /**
     * Gets an unmodifiable view of the current override map.
     */
    public static Map<UUID, UUID> getOverrides() {
        return Collections.unmodifiableMap(overrideMap);
    }

    /**
     * Loads override map from saved data (called during world load).
     */
    public static void loadOverrides(Map<UUID, UUID> overrides) {
        overrideMap.clear();
        overrideMap.putAll(overrides);
    }

    /**
     * Clears all overrides (called during world unload).
     */
    public static void clearOverrides() {
        overrideMap.clear();
    }
}
