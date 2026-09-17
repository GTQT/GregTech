package gregtech.common.command.material;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

/**
 * 父命令:/gt material
 * 子命令:info, component
 */
public class CommandMaterial extends CommandTreeBase {

    public CommandMaterial() {
        addSubcommand(new CommandMaterialInfo());
        addSubcommand(new CommandMaterialComponent());
    }

    @NotNull
    @Override
    public String getName() {
        return "material";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.material.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
