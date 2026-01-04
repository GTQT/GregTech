package gregtech.integration.ftb.utility;

import gregtech.api.util.Mods;

import com.feed_the_beast.ftblib.lib.data.FTBLibAPI;
import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.Universe;

import java.util.UUID;

public class FTBTeamHelper {

    public static boolean isSameTeam(UUID first, UUID second) {
        return Mods.FTB_UTILITIES.isModLoaded() && FTBLibAPI.arePlayersInSameTeam(first, second);
    }

    public static ForgeTeam getTeam(UUID uuid) {
        if(!Mods.FTB_UTILITIES.isModLoaded())return null;
        ForgePlayer p1 = Universe.get().getPlayer(uuid);
        if (p1 != null && p1.hasTeam()) {
            return p1.team;
        }
        return null;
    }
    

    public Short getTeamUID(UUID uuid) {
        if(!Mods.FTB_UTILITIES.isModLoaded())return null;
        ForgePlayer p1 = Universe.get().getPlayer(uuid);
        if (p1 != null && p1.hasTeam()) {
            return p1.team.getUID();
        }
        return null;
    }
}
