package gregtech.common.command.wireless;

import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.UUID;

/**
 * /gt wireless info [player]
 * Displays wireless network information for the sender or specified player.
 */
public class CommandWirelessInfo extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "info";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless info [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) {
            throw new CommandException("Wireless energy service is not available.");
        }

        UUID targetUuid;
        String targetName;

        if (args.length > 0) {
            if (getRequiredPermissionLevel() < 2 && !sender.canUseCommand(2, getName())) {
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

        WirelessNetworkView view = service.getView(targetUuid);
        if (view.isEmpty()) {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + targetName + " does not have a wireless network."));
            return;
        }

        sender.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "=== Wireless Network: " + view.getNetworkName() + " ==="));
        sender.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "Owner: " + TextFormatting.GREEN + targetName));
        sender.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "Stored: " + TextFormatting.AQUA + formatBigInteger(view.getStored()) + " EU"));
        sender.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "Capacity: " + TextFormatting.AQUA + formatBigInteger(view.getCapacity()) + " EU"));
        sender.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "Input/s: " + TextFormatting.GREEN + formatBigInteger(view.getInputPerSecond()) + " EU/s"));
        sender.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "Output/s: " + TextFormatting.RED + formatBigInteger(view.getOutputPerSecond()) + " EU/s"));
        sender.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "Nodes: " + TextFormatting.YELLOW + view.getOnlineNodeCount() +
                        "/" + view.getNodeCount() + " online"));
    }

    private static String formatBigInteger(BigInteger value) {
        if (value.compareTo(BigInteger.valueOf(1_000_000_000L)) >= 0) {
            return String.format("%.3E", value.doubleValue());
        }
        return value.toString();
    }
}
