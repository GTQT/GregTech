package gregtech.common.wireless;

import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;

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
    private static final String NBT_OVERRIDES = "overrides";

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

        // Load team resolver overrides
        Map<UUID, UUID> overrides = new ConcurrentHashMap<>();
        NBTTagList overrideList = nbt.getTagList(NBT_OVERRIDES, 10);
        for (int i = 0; i < overrideList.tagCount(); i++) {
            NBTTagCompound entry = overrideList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(entry.getString("player"));
                UUID network = UUID.fromString(entry.getString("network"));
                overrides.put(player, network);
            } catch (RuntimeException ignored) {}
        }
        WirelessTeamResolver.loadOverrides(overrides);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean(NBT_MIGRATED, migrated);

        NBTTagList list = new NBTTagList();
        for (WirelessEnergyNetwork network : networks.values()) {
            list.appendTag(network.writeToNBT());
        }
        nbt.setTag(NBT_NETWORKS, list);

        // Save team resolver overrides
        NBTTagList overrideList = new NBTTagList();
        for (Map.Entry<UUID, UUID> entry : WirelessTeamResolver.getOverrides().entrySet()) {
            NBTTagCompound overrideTag = new NBTTagCompound();
            overrideTag.setString("player", entry.getKey().toString());
            overrideTag.setString("network", entry.getValue().toString());
            overrideList.appendTag(overrideTag);
        }
        nbt.setTag(NBT_OVERRIDES, overrideList);

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

    private static final String LEGACY_GLOBAL_ENERGY_DATA_NAME = "GregTech_WirelessEUWorldSavedData";
    private static final String LEGACY_GLOBAL_ENERGY_NBT_LIST = "GlobalEnergyList";
    private static final String LEGACY_GLOBAL_ENERGY_NBT_UUID = "uuid";
    private static final String LEGACY_GLOBAL_ENERGY_NBT_ENERGY = "energy";
    private static final String LEGACY_NETWORK_DB_DATA_NAME = "gtqt_network_data";

    /**
     * Migrates data from the old GregTech_WirelessEUWorldSavedData (GT5-style global balance)
     * and NetworkDatabase (PSS node positions) into the unified format.
     */
    private void migrateFromLegacy(World world) {
        GTLog.logger.info("WirelessEnergySavedData: Performing first-time migration from legacy data sources.");

        migrateFromGlobalEnergy(world);
        migrateFromNetworkDatabase(world);

        migrated = true;
        markDirty();
        GTLog.logger.info("WirelessEnergySavedData: Migration complete. {} networks created.", networks.size());
    }

    /**
     * Migrates global energy balances by directly reading the old WorldSavedData NBT.
     * Each UUID->BigInteger entry becomes a network with that stored value.
     * Capacity is set equal to stored as a placeholder until PSS nodes register in P3.
     */
    private void migrateFromGlobalEnergy(World world) {
        try {
            MapStorage storage = world.getMapStorage();
            if (storage == null) return;

            WorldSavedData legacyData = storage.getOrLoadData(LegacyGlobalEnergySavedData.class,
                    LEGACY_GLOBAL_ENERGY_DATA_NAME);
            if (legacyData == null) {
                GTLog.logger.info("WirelessEnergySavedData: No legacy global energy data to migrate.");
                return;
            }

            LegacyGlobalEnergySavedData legacy = (LegacyGlobalEnergySavedData) legacyData;
            Map<UUID, BigInteger> globalEnergy = legacy.getEnergyMap();

            if (globalEnergy.isEmpty()) {
                GTLog.logger.info("WirelessEnergySavedData: Legacy global energy data is empty.");
                return;
            }

            GTLog.logger.info("WirelessEnergySavedData: Migrating {} entries from GregTech_WirelessEUWorldSavedData.",
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
        } catch (RuntimeException e) {
            GTLog.logger.warn("WirelessEnergySavedData: Error migrating from legacy global energy data", e);
        }
    }

    /**
     * Migrates PSS node positions from the old gtqt_network_data WorldSavedData.
     * Only migrates the network registration (owner -> name).
     * Actual stored/capacity will be populated when PSS tiles load and register with the service.
     */
    private void migrateFromNetworkDatabase(World world) {
        try {
            MapStorage storage = world.getMapStorage();
            if (storage == null) return;

            WorldSavedData legacyData = storage.getOrLoadData(
                    LegacyNetworkDatabaseSavedData.class, LEGACY_NETWORK_DB_DATA_NAME);
            if (legacyData == null) {
                GTLog.logger.info("WirelessEnergySavedData: No legacy NetworkDatabase to migrate.");
                return;
            }

            LegacyNetworkDatabaseSavedData legacy = (LegacyNetworkDatabaseSavedData) legacyData;
            Map<UUID, String> legacyNetworks = legacy.getNetworkNames();
            GTLog.logger.info("WirelessEnergySavedData: Migrating {} entries from NetworkDatabase.",
                    legacyNetworks.size());

            for (Map.Entry<UUID, String> entry : legacyNetworks.entrySet()) {
                UUID ownerId = entry.getKey();
                String networkName = entry.getValue();

                // Resolve to canonical network ID
                UUID networkId = WirelessTeamResolver.resolveNetworkId(ownerId);
                getOrCreateNetwork(networkId,
                        networkName != null && !networkName.isEmpty() ? networkName : "Wireless Network");

                GTLog.logger.info("WirelessEnergySavedData: Migrated network for owner {} -> network {}",
                        ownerId, networkId);
            }
        } catch (RuntimeException e) {
            GTLog.logger.warn("WirelessEnergySavedData: Error migrating from NetworkDatabase", e);
        }
    }

    // ==================== Legacy Data Reader ====================

    /**
     * Minimal WorldSavedData subclass used solely to read the old GregTech_WirelessEUWorldSavedData
     * NBT format during migration. Not used for any active storage.
     */
    public static class LegacyGlobalEnergySavedData extends WorldSavedData {

        private final Map<UUID, BigInteger> energyMap = new ConcurrentHashMap<>();

        public LegacyGlobalEnergySavedData() {
            super(LEGACY_GLOBAL_ENERGY_DATA_NAME);
        }

        public LegacyGlobalEnergySavedData(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            energyMap.clear();
            NBTTagList energyList = nbt.getTagList(LEGACY_GLOBAL_ENERGY_NBT_LIST, 10);
            for (int i = 0; i < energyList.tagCount(); i++) {
                NBTTagCompound entry = energyList.getCompoundTagAt(i);
                try {
                    UUID uuid = UUID.fromString(entry.getString(LEGACY_GLOBAL_ENERGY_NBT_UUID));
                    byte[] energyBytes = entry.getByteArray(LEGACY_GLOBAL_ENERGY_NBT_ENERGY);
                    BigInteger energy = new BigInteger(energyBytes);
                    energyMap.put(uuid, energy);
                } catch (RuntimeException ignored) {
                    // malformed uuid or energy data, skip this entry
                }
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            // Read-only for migration, no need to write
            return nbt;
        }

        public Map<UUID, BigInteger> getEnergyMap() {
            return Collections.unmodifiableMap(energyMap);
        }
    }

    /**
     * Minimal WorldSavedData subclass used solely to read the old gtqt_network_data
     * NBT format during migration. Not used for any active storage.
     */
    public static class LegacyNetworkDatabaseSavedData extends WorldSavedData {

        private final Map<UUID, String> networkNames = new ConcurrentHashMap<>();

        public LegacyNetworkDatabaseSavedData() {
            super(LEGACY_NETWORK_DB_DATA_NAME);
        }

        public LegacyNetworkDatabaseSavedData(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            networkNames.clear();
            NBTTagList list = nbt.getTagList("networks", 10);
            for (NBTBase tag : list) {
                NBTTagCompound nodeTag = (NBTTagCompound) tag;
                try {
                    UUID owner = UUID.fromString(nodeTag.getString("owner"));
                    String networkName = nodeTag.getString("name");
                    networkNames.put(owner, networkName);
                } catch (RuntimeException ignored) {
                    // malformed entry, skip
                }
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            // Read-only for migration, no need to write
            return nbt;
        }

        public Map<UUID, String> getNetworkNames() {
            return Collections.unmodifiableMap(networkNames);
        }
    }
}
