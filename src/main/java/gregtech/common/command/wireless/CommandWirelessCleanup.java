package gregtech.common.command.wireless;

import gregtech.api.wireless.WirelessNodeId;
import gregtech.api.wireless.WirelessStorageNodeSnapshot;
import gregtech.common.wireless.WirelessEnergyNetwork;
import gregtech.common.wireless.WirelessEnergySavedData;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import gregtech.api.util.GTUtility;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /gt wireless cleanup
 * Scans all networks for stale nodes (chunks loaded but tile entity missing/invalid)
 * and removes them. Admin only (permission level 2).
 */
public class CommandWirelessCleanup extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "cleanup";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "/gt wireless cleanup";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        WirelessEnergySavedData savedData = WirelessEnergySavedData.getInstance();
        if (savedData == null) {
            throw new CommandException("Wireless energy data is not loaded.");
        }

        int totalRemoved = 0;

        for (Map.Entry<UUID, WirelessEnergyNetwork> entry : savedData.getAllNetworks().entrySet()) {
            WirelessEnergyNetwork network = entry.getValue();
            List<WirelessNodeId> staleNodes = new ArrayList<>();

            for (Map.Entry<WirelessNodeId, WirelessStorageNodeSnapshot> nodeEntry : network.getNodes().entrySet()) {
                WirelessNodeId nodeId = nodeEntry.getKey();
                if (isNodeStale(server, nodeId)) {
                    staleNodes.add(nodeId);
                }
            }

            for (WirelessNodeId staleId : staleNodes) {
                network.unregisterNode(staleId);
                totalRemoved++;
            }
        }

        sender.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Cleanup complete. Removed " + totalRemoved + " stale node(s)."));
    }

    /**
     * Checks if a node is stale: the chunk is loaded but the expected tile entity is not present.
     */
    private boolean isNodeStale(MinecraftServer server, WirelessNodeId nodeId) {
        World world = server.getWorld(nodeId.getDimension());
        if (world == null) return false;

        if (!world.isBlockLoaded(nodeId.getPos())) {
            // Chunk not loaded, cannot verify - not considered stale
            return false;
        }

        // Chunk loaded but no valid MTE at position = stale
        var mte = GTUtility.getMetaTileEntity(world, nodeId.getPos());
        return mte == null;
    }
}
