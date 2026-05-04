package gregtech.common.misc;

import static gregtech.common.misc.GlobalVariableStorage.GlobalEnergy;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class GlobalEnergyWorldSavedData extends WorldSavedData {

    public static GlobalEnergyWorldSavedData INSTANCE;

    private static final String DATA_NAME = "GregTech_WirelessEUWorldSavedData";
    private static final String NBT_ENERGY_LIST = "GlobalEnergyList";
    private static final String NBT_UUID = "uuid";
    private static final String NBT_ENERGY = "energy";

    private static void loadInstance(World world) {
        GlobalEnergy.clear();

        MapStorage storage = world.getMapStorage();
        if (storage == null) return;

        INSTANCE = (GlobalEnergyWorldSavedData) storage.getOrLoadData(GlobalEnergyWorldSavedData.class, DATA_NAME);
        if (INSTANCE == null) {
            INSTANCE = new GlobalEnergyWorldSavedData();
            storage.setData(DATA_NAME, INSTANCE);
        }
        INSTANCE.markDirty();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            loadInstance(event.getWorld());
        }
    }

    public GlobalEnergyWorldSavedData() {
        super(DATA_NAME);
    }

    public GlobalEnergyWorldSavedData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        GlobalEnergy.clear();

        NBTTagList energyList = nbt.getTagList(NBT_ENERGY_LIST, 10);
        for (int i = 0; i < energyList.tagCount(); i++) {
            NBTTagCompound entry = energyList.getCompoundTagAt(i);
            try {
                UUID uuid = UUID.fromString(entry.getString(NBT_UUID));
                byte[] energyBytes = entry.getByteArray(NBT_ENERGY);
                BigInteger energy = new BigInteger(energyBytes);
                GlobalEnergy.put(uuid, energy);
            } catch (RuntimeException ignored) {
                // malformed uuid or energy data, skip this entry
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList energyList = new NBTTagList();

        for (Map.Entry<UUID, BigInteger> entry : GlobalEnergy.entrySet()) {
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString(NBT_UUID, entry.getKey().toString());
            entryTag.setByteArray(NBT_ENERGY, entry.getValue().toByteArray());
            energyList.appendTag(entryTag);
        }

        nbt.setTag(NBT_ENERGY_LIST, energyList);
        return nbt;
    }
}
