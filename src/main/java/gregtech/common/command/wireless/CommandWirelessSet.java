package gregtech.common.command.wireless;

import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.common.wireless.WirelessEnergyNetwork;
import gregtech.common.wireless.WirelessEnergySavedData;
import gregtech.common.wireless.WirelessEnergyServiceImpl;
import gregtech.common.wireless.WirelessTeamResolver;

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
 * /gt wireless set <amount> [player]
 * Sets the stored energy of a wireless network to an exact value. Admin only (permission level 2).
 */
public class CommandWirelessSet extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "set";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless set <amount> [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        if (args.length < 1) {
            throw new CommandException("Usage: " + getUsage(sender));
        }

        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) {
            throw new CommandException("Wireless energy service is not available.");
        }

        BigInteger amount;
        try {
            amount = new BigInteger(args[0]);
        } catch (NumberFormatException e) {
            throw new CommandException("Invalid amount: " + args[0]);
        }

        if (amount.signum() < 0) {
            throw new CommandException("Amount cannot be negative.");
        }

        UUID targetUuid;
        String targetName;

        if (args.length > 1) {
            EntityPlayerMP target = getPlayer(server, sender, args[1]);
            targetUuid = target.getUniqueID();
            targetName = target.getName();
        } else {
            if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP player)) {
                throw new CommandException("Must specify a player when running from console.");
            }
            targetUuid = player.getUniqueID();
            targetName = player.getName();
        }

        UUID networkId = WirelessTeamResolver.resolveNetworkId(targetUuid);
        WirelessEnergySavedData savedData = WirelessEnergySavedData.getInstance();
        if (savedData == null) {
            throw new CommandException("Wireless energy data is not loaded.");
        }

        WirelessEnergyNetwork network = savedData.getOrCreateNetwork(networkId, "Wireless Network");
        BigInteger oldStored = network.getStored();
        network.setStored(amount);

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Set " + targetName + "'s network stored energy: " +
                        oldStored + " -> " + amount + " EU"));
    }
}
