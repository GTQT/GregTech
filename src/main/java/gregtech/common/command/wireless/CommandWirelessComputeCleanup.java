package gregtech.common.command.wireless;

import gregtech.common.wireless.WirelessComputationNetwork;
import gregtech.common.wireless.WirelessComputationSavedData;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

public class CommandWirelessComputeCleanup extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "cleanup";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless compute cleanup";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        WirelessComputationSavedData savedData = WirelessComputationSavedData.getInstance();
        if (savedData == null) {
            throw new CommandException("Wireless computation service is not available.");
        }

        int removed = 0;
        var iterator = savedData.getAllNetworks().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            WirelessComputationNetwork network = entry.getValue();
            // Only remove pristine networks: default channel only and no registered uplink nodes
            if (network.getChannels().size() == 1) {
                var channel = network.getChannel(WirelessComputationNetwork.DEFAULT_CHANNEL_ID);
                if (channel != null && channel.getNodeCount() == 0) {
                    savedData.removeNetwork(entry.getKey());
                    removed++;
                }
            }
        }
        if (removed > 0) savedData.markDirty();

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Removed " + removed + " empty wireless computation network(s)."));
    }
}
