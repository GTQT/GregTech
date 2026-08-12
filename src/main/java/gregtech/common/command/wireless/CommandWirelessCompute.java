package gregtech.common.command.wireless;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

/**
 * Parent command: /gt wireless compute
 * Subcommands: info, cleanup
 */
public class CommandWirelessCompute extends CommandTreeBase {

    public CommandWirelessCompute() {
        addSubcommand(new CommandWirelessComputeInfo());
        addSubcommand(new CommandWirelessComputeCleanup());
    }

    @NotNull
    @Override
    public String getName() {
        return "compute";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless compute <info|cleanup>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
