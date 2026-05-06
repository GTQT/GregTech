package gregtech.common.wireless;

import gregtech.api.util.Mods;
import gregtech.integration.ftb.utility.FTBTeamHelper;

import com.feed_the_beast.ftblib.lib.data.ForgeTeam;

import java.util.UUID;

/**
 * Resolves a player UUID to a canonical network UUID.
 * The canonical network UUID determines which wireless energy network a player belongs to.
 * <p>
 * Resolution priority:
 * <ol>
 *   <li>If FTB Utilities is loaded and player has a team, use team owner UUID.</li>
 *   <li>Otherwise, use the player's own UUID.</li>
 * </ol>
 */
public final class WirelessTeamResolver {

    private WirelessTeamResolver() {}

    /**
     * Resolves the given actor UUID to the canonical network UUID.
     *
     * @param actorUuid the player UUID to resolve
     * @return the canonical network UUID (team owner or player self)
     */
    public static UUID resolveNetworkId(UUID actorUuid) {
        if (actorUuid == null) return null;
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
}
