package gregtech.common.wireless;

import gregtech.api.util.GTLog;
import gregtech.common.misc.GlobalVariableStorage;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

import gtqt.api.util.wireless.NetworkDatabase;
import gtqt.api.util.wireless.NetworkNode;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified WorldSavedData for the wireless energy network system.
 * Replaces both {@code GregTech_WirelessEUWorldSavedData} and {@code gtqt_network_data}.
 * <p>
 * On first load, migrates data from the legacy sources if the new data file does not yet exist.
 */
public class WirelessEnergySavedData extends WorldSavedData {

    public static final String DATA_NAME = "GregTech_WirelessEnergyNetworks";
    private static final String NBT_NETWORKS = "networks";
    private static final String NBT_MIGRATED = "migrated";

    private static WirelessEnergySavedData instance;

    private final Map<UUID, WirelessEnergyNetwork> networks = new ConcurrentHashMap<>();
    private boolean migrated = false;

    public WirelessEnergySavedData() {
        super(DATA_NAME);
    }

    public WirelessEnergySavedData(String name) {
        super(name);
    }

    // ==================== Instance Management ====================

    public static WirelessEnergySavedData getInstance() {
        return instance;
    }

    /**
     * Loads or creates the saved data instance for the given overworld.
     * Should be called once during world load (dimension 0).
     */
    public static WirelessEnergySavedData loadOrCreate(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            GTLog.logger.error("WirelessEnergySavedData: MapStorage is null, cannot load data.");
            instance = new WirelessEnergySavedData();
            return instance;
        }

        WirelessEnergySavedData data = (WirelessEnergySavedData) storage
                .getOrLoadData(WirelessEnergySavedData.class, DATA_NAME);
        if (data == null) {
            data = new WirelessEnergySavedData();
            storage.setData(DATA_NAME, data);
            // First time: attempt migration from legacy data sources
            data.migrateFromLegacy(world);
        }
        instance = data;
        return data;
    }

    public static void clearInstance() {
        instance = null;
    }

    // ==================== Network Access ====================

    public WirelessEnergyNetwork getNetwork(UUID networkId) {
        return networks.get(networkId);
    }

    public WirelessEnergyNetwork getOrCreateNetwork(UUID networkId, String defaultName) {
        return networks.computeIfAbsent(networkId, id -> new WirelessEnergyNetwork(id, defaultName));
    }

    public Map<UUID, WirelessEnergyNetwork> getAllNetworks() {
        return Collections.unmodifiableMap(networks);
    }

    // ==================== Persistence ====================

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        networks.clear();
        migrated = nbt.getBoolean(NBT_MIGRATED);

        NBTTagList list = nbt.getTagList(NBT_NETWORKS, 10);
        for (NBTBase tag : list) {
            try {
                NBTTagCompound networkTag = (NBTTagCompound) tag;
                WirelessEnergyNetwork network = WirelessEnergyNetwork.readFromNBT(networkTag);
                networks.put(network.getNetworkId(), network);
            } catch (RuntimeException e) {
                GTLog.logger.warn("WirelessEnergySavedData: Skipping malformed network entry", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean(NBT_MIGRATED, migrated);

        NBTTagList list = new NBTTagList();
        for (WirelessEnergyNetwork network : networks.values()) {
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

    /**
     * Called by the service layer each tick to batch dirty marking.
     * Only marks the WorldSavedData as dirty if any network has pending changes.
     */
    public void flushDirtyNetworks() {
        boolean anyDirty = false;
        for (WirelessEnergyNetwork network : networks.values()) {
            if (network.checkAndClearDirty()) {
                anyDirty = true;
            }
        }
        if (anyDirty) {
            markDirty();
        }
    }

    // ==================== Legacy Migration ====================

    /**
     * Migrates data from old GlobalEnergyWorldSavedData (GT5-style global balance)
     * and NetworkDatabase (PSS node positions) into the unified format.
     */
    private void migrateFromLegacy(World world) {
        GTLog.logger.info("WirelessEnergySavedData: Performing first-time migration from legacy data sources.");

        migrateFromGlobalEnergy();
        migrateFromNetworkDatabase(world);

        migrated = true;
        markDirty();
        GTLog.logger.info("WirelessEnergySavedData: Migration complete. {} networks created.", networks.size());
    }

    /**
     * Migrates global energy balances from the old GT5-style WirelessNetworkManager.
     * Each UUID->BigInteger entry becomes a network with that stored value and unlimited capacity
     * (since the old system had no capacity concept, we set capacity = stored to preserve balance,
     * and it will be properly set once PSS nodes are registered in P3).
     */
    private void migrateFromGlobalEnergy() {
        Map<UUID, BigInteger> globalEnergy = GlobalVariableStorage.GlobalEnergy;
        if (globalEnergy.isEmpty()) {
            GTLog.logger.info("WirelessEnergySavedData: No legacy global energy data to migrate.");
            return;
        }

        GTLog.logger.info("WirelessEnergySavedData: Migrating {} entries from GlobalEnergyWorldSavedData.",
                globalEnergy.size());

        for (Map.Entry<UUID, BigInteger> entry : globalEnergy.entrySet()) {
            UUID networkId = entry.getKey();
            BigInteger energy = entry.getValue();
            if (energy.signum() <= 0) continue;

            WirelessEnergyNetwork network = getOrCreateNetwork(networkId, "Migrated Network");
            // Set stored from legacy balance; capacity set to stored as placeholder
            // (real capacity comes from PSS nodes in P3)
            network.setStored(network.getStored().add(energy));
            network.setCapacity(network.getCapacity().add(energy));
        }
    }

    /**
     * Migrates PSS node positions from the old NetworkDatabase.
     * Only migrates the network registration (owner -> name + node locations).
     * Actual stored/capacity will be populated when PSS tiles load and register with the service.
     */
    private void migrateFromNetworkDatabase(World world) {
        try {
            MapStorage storage = world.getMapStorage();
            if (storage == null) return;

            NetworkDatabase legacyDb = (NetworkDatabase) storage
                    .getOrLoadData(NetworkDatabase.class, "gtqt_network_data");
            if (legacyDb == null) {
                GTLog.logger.info("WirelessEnergySavedData: No legacy NetworkDatabase to migrate.");
                return;
            }

            Map<UUID, NetworkNode> legacyNetworks = legacyDb.getNetworks();
            GTLog.logger.info("WirelessEnergySavedData: Migrating {} entries from NetworkDatabase.",
                    legacyNetworks.size());

            for (Map.Entry<UUID, NetworkNode> entry : legacyNetworks.entrySet()) {
                UUID ownerId = entry.getKey();
                NetworkNode legacyNode = entry.getValue();

                // Resolve to canonical network ID
                UUID networkId = WirelessTeamResolver.resolveNetworkId(ownerId);
                WirelessEnergyNetwork network = getOrCreateNetwork(networkId,
                        legacyNode.getNetworkName() != null ? legacyNode.getNetworkName() : "Wireless Network");

                // Node positions are migrated but actual capacity/stored will be populated
                // when PSS tiles load and call registerStorageNode in P3.
                // We don't create WirelessStorageNodeSnapshot here because we don't have
                // the tile entity data (capacity, stored, etc.) - that comes from the live PSS.
            }
        } catch (RuntimeException e) {
            GTLog.logger.warn("WirelessEnergySavedData: Error migrating from NetworkDatabase", e);
        }
    }
}
