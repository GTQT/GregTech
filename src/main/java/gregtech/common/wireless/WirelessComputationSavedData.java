package gregtech.common.wireless;

import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldSavedData for the wireless computation network system.
 * Persists channel definitions only — uplink nodes are runtime registrations
 * re-established by the hatches after every world reload.
 */
public class WirelessComputationSavedData extends WorldSavedData {

    public static final String DATA_NAME = "GregTech_WirelessComputationNetworks";
    private static final String NBT_NETWORKS = "networks";

    private static WirelessComputationSavedData instance;

    private final Map<UUID, WirelessComputationNetwork> networks = new ConcurrentHashMap<>();

    public WirelessComputationSavedData() {
        super(DATA_NAME);
    }

    public WirelessComputationSavedData(String name) {
        super(name);
    }

    // ==================== Instance Management ====================

    public static WirelessComputationSavedData getInstance() {
        return instance;
    }

    public static WirelessComputationSavedData loadOrCreate(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            GTLog.logger.error("WirelessComputationSavedData: MapStorage is null, cannot load data.");
            instance = new WirelessComputationSavedData();
            return instance;
        }

        WirelessComputationSavedData data = (WirelessComputationSavedData) storage
                .getOrLoadData(WirelessComputationSavedData.class, DATA_NAME);
        if (data == null) {
            data = new WirelessComputationSavedData();
            storage.setData(DATA_NAME, data);
        }
        instance = data;
        return data;
    }

    public static void clearInstance() {
        instance = null;
    }

    // ==================== Network Access ====================

    public WirelessComputationNetwork getNetwork(UUID networkId) {
        return networks.get(networkId);
    }

    public WirelessComputationNetwork getOrCreateNetwork(UUID networkId, String defaultName) {
        return networks.computeIfAbsent(networkId, id -> new WirelessComputationNetwork(id, defaultName));
    }

    public WirelessComputationNetwork removeNetwork(UUID networkId) {
        return networks.remove(networkId);
    }

    public Map<UUID, WirelessComputationNetwork> getAllNetworks() {
        return Collections.unmodifiableMap(networks);
    }

    // ==================== Persistence ====================

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        networks.clear();

        NBTTagList list = nbt.getTagList(NBT_NETWORKS, 10);
        for (NBTBase tag : list) {
            try {
                NBTTagCompound networkTag = (NBTTagCompound) tag;
                WirelessComputationNetwork network = WirelessComputationNetwork.readFromNBT(networkTag);
                networks.put(network.getNetworkId(), network);
            } catch (RuntimeException e) {
                GTLog.logger.warn("WirelessComputationSavedData: Skipping malformed network entry", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (WirelessComputationNetwork network : networks.values()) {
            list.appendTag(network.writeToNBT());
        }
        nbt.setTag(NBT_NETWORKS, list);
        return nbt;
    }

    @Override
    public void markDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer() &&
                FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            super.markDirty();
        }
    }

    public void flushDirtyNetworks() {
        boolean anyDirty = false;
        for (WirelessComputationNetwork network : networks.values()) {
            if (network.checkAndClearDirty()) {
                anyDirty = true;
            }
        }
        if (anyDirty) {
            markDirty();
        }
    }
}
