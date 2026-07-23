package gregtech.datafix.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

public final class CommandDataFix extends CommandTreeBase {

    public CommandDataFix() {
    }

    @Override
    public @NotNull String getName() {
        return "datafix";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.datafix.usage";
    }
}
