package gregtech.common.command.wireless;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

/**
 * Parent command: /gt wireless
 * Subcommands: info, add, set, cleanup
 */
public class CommandWireless extends CommandTreeBase {

    public CommandWireless() {
        addSubcommand(new CommandWirelessInfo());
        addSubcommand(new CommandWirelessAdd());
        addSubcommand(new CommandWirelessSet());
        addSubcommand(new CommandWirelessJoin());
        addSubcommand(new CommandWirelessLeave());
        addSubcommand(new CommandWirelessCleanup());
    }

    @NotNull
    @Override
    public String getName() {
        return "wireless";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless <info|add|set|join|leave|cleanup>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
