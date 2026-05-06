package gregtech.common.misc;

import static gregtech.common.misc.GlobalVariableStorage.GlobalEnergy;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.integration.ftb.utility.FTBTeamHelper;

import com.feed_the_beast.ftblib.lib.data.ForgeTeam;

public final class WirelessNetworkManager {

    private WirelessNetworkManager() {}

    public static void strongCheckOrAddUser(UUID user_uuid) {
        if (!GlobalEnergy.containsKey(user_uuid)) {
            GlobalEnergy.put(user_uuid, BigInteger.ZERO);
        }
    }

    public static UUID getLeaderUUID(UUID user_uuid) {
        ForgeTeam team = FTBTeamHelper.getTeam(user_uuid);
        if (team != null) {
            return team.owner.getId();
        }
        return user_uuid;
    }

    public static boolean addEUToGlobalEnergyMap(UUID user_uuid, BigInteger EU) {
        try {
            GlobalEnergyWorldSavedData.INSTANCE.markDirty();
        } catch (Exception exception) {
            System.out.println("COULD NOT MARK GLOBAL ENERGY AS DIRTY IN ADD EU");
            exception.printStackTrace();
        }

        UUID teamUUID = getLeaderUUID(user_uuid);

        BigInteger totalEU = GlobalEnergy.getOrDefault(teamUUID, BigInteger.ZERO);
        totalEU = totalEU.add(EU);

        if (totalEU.signum() >= 0) {
            GlobalEnergy.put(teamUUID, totalEU);
            return true;
        }

        return false;
    }

    public static boolean addEUToGlobalEnergyMap(UUID user_uuid, long EU) {
        return addEUToGlobalEnergyMap(user_uuid, BigInteger.valueOf(EU));
    }

    public static boolean addEUToGlobalEnergyMap(UUID user_uuid, int EU) {
        return addEUToGlobalEnergyMap(user_uuid, BigInteger.valueOf(EU));
    }

    public static long ticks_between_energy_addition = 100L * 20L;

    public static long number_of_energy_additions = 4L;

    public static long totalStorage(long tier_eu_per_tick) {
        return tier_eu_per_tick * ticks_between_energy_addition * number_of_energy_additions;
    }

    public static BigInteger getUserEU(UUID user_uuid) {
        return GlobalEnergy.getOrDefault(getLeaderUUID(user_uuid), BigInteger.ZERO);
    }

    public static void setUserEU(UUID user_uuid, BigInteger EU) {
        try {
            GlobalEnergyWorldSavedData.INSTANCE.markDirty();
        } catch (Exception exception) {
            System.out.println("COULD NOT MARK GLOBAL ENERGY AS DIRTY IN SET EU");
            exception.printStackTrace();
        }

        GlobalEnergy.put(getLeaderUUID(user_uuid), EU);
    }

    public static void clearGlobalEnergyInformationMaps() {
        GlobalEnergy.clear();
    }

    public static UUID processInitialSettings(final MetaTileEntity machine) {
        final UUID UUID = machine.getOwnerGT();
        strongCheckOrAddUser(UUID);
        return UUID;
    }

    public static World getOverworld() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
    }
}
