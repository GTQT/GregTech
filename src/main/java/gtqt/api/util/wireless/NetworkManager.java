package gtqt.api.util.wireless;

import gregtech.integration.ftb.utility.FTBTeamHelper;

import net.minecraft.world.World;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class NetworkManager {

    public static final NetworkManager INSTANCE = new NetworkManager();

    // 获取队伍成员UUID列表（可能为null）
    public static List<UUID> getPartList(UUID owner) {
        ForgeTeam team = FTBTeamHelper.getTeam(owner);
        if (team != null) {
            return team.players.keySet().stream()
                    .map(ForgePlayer::getId)
                    .collect(Collectors.toList());
        }
        return null;
    }

    private NetworkNode getNetwork(World world, UUID playerUUID) {
        NetworkDatabase db = NetworkDatabase.get(world);
        return db.getNetworkForPlayer(playerUUID);
    }

    public NetworkNode getOrCreateNetwork(World world, UUID playerUUID, String defaultName) {
        NetworkDatabase db = NetworkDatabase.get(world);
        NetworkNode node = db.getNetworkForPlayer(playerUUID);
        if (node == null) {
            node = new NetworkNode(playerUUID, defaultName);
            db.addNetwork(node);
        }
        return node;
    }

    public NetworkNode getNetworkForPlayer(World world, UUID playerUUID) {
        return NetworkDatabase.get(world).getNetworkForPlayer(playerUUID);
    }
}
