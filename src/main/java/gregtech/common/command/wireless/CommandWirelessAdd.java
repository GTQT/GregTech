package gregtech.common.command.wireless;

import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
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
 * /gt wireless add <amount> [player]
 * Adds energy to the wireless network. Admin only (permission level 2).
 */
public class CommandWirelessAdd extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "add";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless add <amount> [player]";
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

        if (amount.signum() <= 0) {
            throw new CommandException("Amount must be positive.");
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

        TransferResult result = service.insert(targetUuid, amount, TransferContext.ADMIN);
        if (result.isSuccess()) {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "Added " + amount + " EU to " + targetName + "'s network. " +
                            "Actually inserted: " + result.getAmount() + " EU"));
        } else {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.RED + "Failed to add energy: " + result.getStatus()));
        }
    }
}
