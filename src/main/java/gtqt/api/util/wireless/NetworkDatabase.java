package gtqt.api.util.wireless;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.integration.ftb.utility.FTBTeamHelper;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessController;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkDatabase extends WorldSavedData {

    private static final String DATA_NAME = "gtqt_network_data";
    private final Map<UUID, NetworkNode> networks = new ConcurrentHashMap<>(); // 线程安全

    public NetworkDatabase() {super(DATA_NAME);}

    public NetworkDatabase(String name) {super(name);}

    public static NetworkDatabase get(World world) {
        MapStorage storage = world.getMapStorage();
        NetworkDatabase instance = (NetworkDatabase) storage.getOrLoadData(NetworkDatabase.class, DATA_NAME);
        if (instance == null) {
            instance = new NetworkDatabase();
            storage.setData(DATA_NAME, instance);
        }
        return instance;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        networks.clear();
        NBTTagList list = nbt.getTagList("networks", 10);
        for (NBTBase tag : list) {
            NBTTagCompound nodeTag = (NBTTagCompound) tag;
            NetworkNode node = new NetworkNode(
                    UUID.fromString(nodeTag.getString("owner")),
                    nodeTag.getString("name")
            );
            NBTTagList listMtes = nodeTag.getTagList("Mtes", 10);
            // 恢复hatches
            for (int i = 0; i < listMtes.tagCount(); i++) {
                NBTTagCompound entry = listMtes.getCompoundTagAt(i);
                int dim = entry.getInteger("dim");
                BlockPos pos = new BlockPos(entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"));
                World world = NetworkManager.getWorldByDimension(dim);
                if (world != null) {
                    MetaTileEntity mte = GTUtility.getMetaTileEntity(world, pos);
                    if (mte instanceof MetaTileEntityWirelessController) {
                        node.addNewHatch((MetaTileEntityWirelessController) mte);
                    }
                }
            }
            networks.put(node.getOwnerUUID(), node);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (NetworkNode node : networks.values()) {
            NBTTagCompound nodeTag = new NBTTagCompound();
            nodeTag.setString("owner", node.getOwnerUUID().toString());
            nodeTag.setString("name", node.getNetworkName());

            NBTTagList listMte = new NBTTagList();
            for (MetaTileEntityWirelessController hatch : node.getHatches()) {
                if (hatch.getWorld() == null) continue;
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger("dim", hatch.getWorld().provider.getDimension());
                entry.setInteger("x", hatch.getPos().getX());
                entry.setInteger("y", hatch.getPos().getY());
                entry.setInteger("z", hatch.getPos().getZ());
                listMte.appendTag(entry);
            }
            nodeTag.setTag("Mtes", listMte);
            list.appendTag(nodeTag);
        }
        nbt.setTag("networks", list);
        return nbt;
    }

    // 获取玩家所属的网络（队伍共享或单人）
    public NetworkNode getNetworkForPlayer(UUID playerUUID) {
        // 1. 直接以玩家UUID为键查找
        NetworkNode node = networks.get(playerUUID);
        if (node != null) return node;

        // 2. 查找玩家所在队伍
        ForgeTeam team = FTBTeamHelper.getTeam(playerUUID);
        if (team != null) {
            for (ForgePlayer member : team.players.keySet()) {
                UUID memberId = member.getId();
                node = networks.get(memberId);
                if (node != null) return node;
            }
        }
        return null; // 没有网络
    }

    // 添加网络（线程安全，自动标记dirty）
    public void addNetwork(NetworkNode node) {
        networks.put(node.getOwnerUUID(), node);
        markDirty();
    }

    @Override
    public void markDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer() &&
                FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            super.markDirty();
        }
    }

    // 获取所有网络（不可修改视图）
    public Map<UUID, NetworkNode> getNetworks() {
        return Collections.unmodifiableMap(networks);
    }
}
