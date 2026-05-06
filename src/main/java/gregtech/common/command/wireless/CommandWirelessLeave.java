package gregtech.common.command.wireless;

import gregtech.common.wireless.WirelessTeamResolver;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * /gt wireless leave <player>
 * Removes the admin override for a player, reverting to default team/player resolution.
 * Admin only (permission level 2).
 */
public class CommandWirelessLeave extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "leave";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless leave <player>";
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

        EntityPlayerMP player = getPlayer(server, sender, args[0]);
        UUID playerUuid = player.getUniqueID();

        boolean removed = WirelessTeamResolver.removeOverride(playerUuid);
        if (removed) {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + player.getName() + "'s network override has been removed. " +
                            "Now using default team/player resolution."));
        } else {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + player.getName() + " did not have a network override."));
        }
    }
}
