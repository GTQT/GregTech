package gregtech.common.command;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureRuntime;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class CommandStructureTrace extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "structure_trace";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt structure_trace <x> <y> <z>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server,
                        @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        if (args.length != 3) {
            throw new CommandException("Usage: " + getUsage(sender));
        }

        BlockPos pos = parseBlockPos(sender, args, 0, false);
        World world = sender.getEntityWorld();
        if (!world.isBlockLoaded(pos)) {
            throw new CommandException("Position is not loaded: " + pos);
        }

        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            throw new CommandException("No GregTech tile entity at " + pos);
        }

        MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (!(metaTileEntity instanceof MultiblockControllerBase)) {
            throw new CommandException("GregTech tile at " + pos + " is not a multiblock controller");
        }

        MultiblockControllerBase controller = (MultiblockControllerBase) metaTileEntity;
        StructureRuntime runtime = controller.getStructureRuntime();
        if (runtime == null) {
            sender.sendMessage(new TextComponentString("Structure runtime is not initialized for "
                    + controller.getMetaName() + " at " + pos));
            return;
        }

        StructureFailureTrace failure = runtime.getLastFailure();
        sender.sendMessage(new TextComponentString("Structure trace for "
                + controller.getMetaName() + " at " + pos));
        sender.sendMessage(new TextComponentString("formed=" + controller.isStructureFormed()
                + ", shape=" + runtime.describeShape()));
        if (failure == null) {
            sender.sendMessage(new TextComponentString("lastFailure=<none>"));
            return;
        }
        sender.sendMessage(new TextComponentString(failure.describeForCommand()));
    }
}
