package gregtech.common.command.wireless;

import gregtech.common.wireless.WirelessEnergyNetwork;
import gregtech.common.wireless.WirelessEnergySavedData;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /gt wireless cleanup
 * Removes empty wireless networks (stored = 0 and no activity).
 * Admin only (permission level 2).
 */
public class CommandWirelessCleanup extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "cleanup";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless cleanup";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        WirelessEnergySavedData savedData = WirelessEnergySavedData.getInstance();
        if (savedData == null) {
            throw new CommandException("Wireless energy data is not loaded.");
        }

        int totalRemoved = 0;
        List<UUID> emptyNetworks = new ArrayList<>();

        for (Map.Entry<UUID, WirelessEnergyNetwork> entry : savedData.getAllNetworks().entrySet()) {
            WirelessEnergyNetwork network = entry.getValue();
            if (network.getStored().signum() == 0) {
                emptyNetworks.add(entry.getKey());
            }
        }

        for (UUID id : emptyNetworks) {
            // Only remove if still empty (race condition safety)
            WirelessEnergyNetwork network = savedData.getNetwork(id);
            if (network != null && network.getStored().signum() == 0) {
                savedData.removeNetwork(id);
                totalRemoved++;
            }
        }

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Cleanup complete. Removed " + totalRemoved + " empty network(s)."));
    }
}
