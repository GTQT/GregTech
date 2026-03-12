package gtqt.api.util.wireless;

import gregtech.integration.ftb.utility.FTBTeamHelper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.server.FMLServerHandler;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NetworkManager {

    public static final NetworkManager INSTANCE = new NetworkManager();
    private final ConcurrentHashMap<UUID, Object> networkLocks = new ConcurrentHashMap<>();

    // 获取指定维度的World（服务端）
    public static World getWorldByDimension(int dimension) {
        MinecraftServer server = FMLServerHandler.instance().getServer();
        return server != null ? server.getWorld(dimension) : null;
    }

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

    // 核心方法：根据玩家UUID获取其所属的网络（队伍共享或个人）
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

    // 创建网络（如果队伍已有网络则返回现有网络，否则新建）
    public NetworkNode createNetwork(World world, UUID owner, String name) {
        NetworkDatabase db = NetworkDatabase.get(world);
        NetworkNode existing = db.getNetworkForPlayer(owner);
        if (existing != null) {
            return existing; // 队伍已有网络，直接返回
        }
        // 新建网络，以owner的UUID为键
        NetworkNode node = new NetworkNode(owner, name);
        db.addNetwork(node);
        return node;
    }

    // 向网络填充能量（返回实际填充量）
    public long fill(World world, UUID playerUUID, long amount) {
        if (amount <= 0) return 0;
        NetworkNode node = getNetwork(world, playerUUID);
        if (node == null) return 0;
        synchronized (getLock(node.getOwnerUUID())) {
            return node.fill(amount);
        }
    }

    public long fill(World world, UUID playerUUID, BigInteger amount) {
        return fill(world, playerUUID, amount.longValue());
    }

    // 从网络抽取能量（返回实际抽取量）
    public long drain(World world, UUID playerUUID, long amount) {
        if (amount <= 0) return 0;
        NetworkNode node = getNetwork(world, playerUUID);
        if (node == null) return 0;
        synchronized (getLock(node.getOwnerUUID())) {
            return node.drain(amount);
        }
    }

    public long drain(World world, UUID playerUUID, BigInteger amount) {
        return drain(world, playerUUID, amount.longValue());
    }

    // 获取网络总容量
    public BigInteger getCapacity(World world, UUID playerUUID) {
        NetworkNode node = getNetwork(world, playerUUID);
        return node != null ? node.getTotalCapacity() : BigInteger.ZERO;
    }

    // 获取网络当前存储
    public BigInteger getStored(World world, UUID playerUUID) {
        NetworkNode node = getNetwork(world, playerUUID);
        return node != null ? node.getTotalStored() : BigInteger.ZERO;
    }

    // 旧版transferEnergy保留，但推荐使用新方法
    public long transferEnergy(World world, UUID playerUUID, BigInteger amount) {
        if (amount.equals(BigInteger.ZERO)) return 0L;
        NetworkNode node = getNetwork(world, playerUUID);
        if (node == null) return 0L;
        synchronized (getLock(node.getOwnerUUID())) {
            BigInteger actual = node.modifyEnergy(amount);
            if (!actual.equals(BigInteger.ZERO)) {
                NetworkDatabase.get(world).markDirty();
            }
            return actual.longValue();
        }
    }

    // 获取或创建锁对象（基于网络所有者UUID）
    private Object getLock(UUID ownerUUID) {
        return networkLocks.computeIfAbsent(ownerUUID, k -> new Object());
    }

    public NetworkNode getNetworkForPlayer(World world, UUID playerUUID) {
        return NetworkDatabase.get(world).getNetworkForPlayer(playerUUID);
    }
}
