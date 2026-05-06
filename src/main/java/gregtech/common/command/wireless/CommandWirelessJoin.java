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
 * /gt wireless join <player> <target_player>
 * Forces a player to join another player's wireless network (admin override).
 * Admin only (permission level 2).
 */
public class CommandWirelessJoin extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "join";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless join <player> <target_network_owner>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        if (args.length < 2) {
            throw new CommandException("Usage: " + getUsage(sender));
        }

        EntityPlayerMP player = getPlayer(server, sender, args[0]);
        EntityPlayerMP targetOwner = getPlayer(server, sender, args[1]);

        UUID playerUuid = player.getUniqueID();
        UUID targetNetworkUuid = WirelessTeamResolver.resolveNetworkId(targetOwner.getUniqueID());

        WirelessTeamResolver.setOverride(playerUuid, targetNetworkUuid);

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + player.getName() + " has been forced to join " +
                        targetOwner.getName() + "'s wireless network."));
    }
}
