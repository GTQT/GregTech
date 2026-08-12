package gregtech.common.command.wireless;

import gregtech.api.wireless.IWirelessComputationService;
import gregtech.api.wireless.WirelessComputationView;
import gregtech.common.wireless.WirelessComputationServiceImpl;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class CommandWirelessComputeInfo extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "info";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless compute info [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) {
            throw new CommandException("Wireless computation service is not available.");
        }

        UUID targetUuid;
        String targetName;

        if (args.length > 0) {
            if (!sender.canUseCommand(2, getName())) {
                throw new CommandException("You do not have permission to view other players' networks.");
            }
            EntityPlayerMP target = getPlayer(server, sender, args[0]);
            targetUuid = target.getUniqueID();
            targetName = target.getName();
        } else {
            if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP player)) {
                throw new CommandException("Must specify a player when running from console.");
            }
            targetUuid = player.getUniqueID();
            targetName = player.getName();
        }

        List<WirelessComputationView> channels = service.getChannels(targetUuid);
        if (channels.isEmpty()) {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + targetName + " does not have a wireless computation network."));
            return;
        }

        sender.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "=== Wireless Computation Network: " + targetName + " ==="));
        for (WirelessComputationView channel : channels) {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.WHITE + "Channel " + TextFormatting.AQUA + channel.getChannelId() + " ("
                            + channel.getNetworkName() + ")" +
                            TextFormatting.WHITE + " | Nodes: " + TextFormatting.GREEN + channel.getNodeCount() +
                            TextFormatting.WHITE + " | Capacity: " + TextFormatting.GREEN + channel.getMaxCWUt() +
                            " CWU/t" +
                            TextFormatting.WHITE + " | Allocated: " + TextFormatting.GREEN + channel.getAllocatedCWUt() +
                            " CWU/t" +
                            TextFormatting.WHITE + " | Rate: " + TextFormatting.GREEN + channel.getAllocatedPerSecond() +
                            " CWU/s"));
        }
    }
}
