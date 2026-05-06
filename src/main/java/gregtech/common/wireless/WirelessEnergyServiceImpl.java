package gregtech.common.wireless;

import gregtech.api.util.GTLog;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.UnregisterMode;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.api.wireless.WirelessNodeId;
import gregtech.api.wireless.WirelessStorageNodeSnapshot;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Implementation of the unified wireless energy service.
 * Singleton instance that manages all wireless energy network operations.
 * <p>
 * Lifecycle:
 * <ul>
 *   <li>Initialized on overworld load (dimension 0).</li>
 *   <li>Ticks statistics and flushes dirty data each server tick.</li>
 *   <li>Cleared on world unload.</li>
 * </ul>
 * <p>
 * Thread safety: All mutation is expected on the server main thread only.
 */
public class WirelessEnergyServiceImpl implements WirelessEnergyService {

    private static WirelessEnergyServiceImpl INSTANCE;

    private WirelessEnergySavedData savedData;

    public WirelessEnergyServiceImpl() {}

    // ==================== Instance Management ====================

    public static WirelessEnergyServiceImpl getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the global service instance, creating it if needed.
     * Returns null if the service has not been initialized yet (world not loaded).
     */
    public static WirelessEnergyService getService() {
        return INSTANCE;
    }

    // ==================== Event Handlers ====================

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;

        GTLog.logger.info("WirelessEnergyService: Initializing on overworld load.");
        INSTANCE = this;
        savedData = WirelessEnergySavedData.loadOrCreate(world);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;

        GTLog.logger.info("WirelessEnergyService: Shutting down on overworld unload.");
        if (savedData != null) {
            savedData.flushDirtyNetworks();
        }
        savedData = null;
        WirelessEnergySavedData.clearInstance();
        WirelessTeamResolver.clearOverrides();
        INSTANCE = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (savedData == null) return;

        // Advance statistics windows for all networks
        for (WirelessEnergyNetwork network : savedData.getAllNetworks().values()) {
            network.tickStats();
        }

        // Batch flush dirty data to WorldSavedData
        savedData.flushDirtyNetworks();
    }

    // ==================== WirelessEnergyService Implementation ====================

    @Override
    public WirelessNetworkView getView(UUID actor) {
        if (savedData == null) return WirelessNetworkView.EMPTY;

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return WirelessNetworkView.EMPTY;

        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return WirelessNetworkView.EMPTY;

        return network.createView();
    }

    @Override
    public TransferResult insert(UUID actor, long amount, TransferContext context) {
        if (amount <= 0) return TransferResult.success(0L);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = getOrCreateNetworkForTransfer(networkId);

        long accepted = network.insert(amount, context.isAllowOverflow());
        if (accepted == amount) {
            return TransferResult.success(accepted);
        } else if (accepted > 0) {
            return TransferResult.partial(accepted);
        } else {
            return TransferResult.networkFull();
        }
    }

    @Override
    public TransferResult extract(UUID actor, long amount, TransferContext context) {
        if (amount <= 0) return TransferResult.success(0L);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return TransferResult.noNetwork();

        long extracted = network.extract(amount);
        if (extracted == amount) {
            return TransferResult.success(extracted);
        } else {
            return TransferResult.insufficientEnergy();
        }
    }

    @Override
    public TransferResult insert(UUID actor, BigInteger amount, TransferContext context) {
        if (amount.signum() <= 0) return TransferResult.success(BigInteger.ZERO);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = getOrCreateNetworkForTransfer(networkId);

        BigInteger accepted = network.insert(amount, context.isAllowOverflow());
        if (accepted.compareTo(amount) == 0) {
            return TransferResult.success(accepted);
        } else if (accepted.signum() > 0) {
            return TransferResult.partial(accepted);
        } else {
            return TransferResult.networkFull();
        }
    }

    @Override
    public TransferResult extract(UUID actor, BigInteger amount, TransferContext context) {
        if (amount.signum() <= 0) return TransferResult.success(BigInteger.ZERO);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return TransferResult.noNetwork();

        BigInteger extracted = network.extract(amount);
        if (extracted.compareTo(amount) == 0) {
            return TransferResult.success(extracted);
        } else {
            return TransferResult.insufficientEnergy();
        }
    }

    @Override
    public void registerStorageNode(WirelessStorageNodeSnapshot node) {
        if (savedData == null) return;

        UUID networkId = node.getOwnerNetworkId();
        WirelessEnergyNetwork network = savedData.getOrCreateNetwork(networkId, "Wireless Network");
        network.registerNode(node);
    }

    @Override
    public void updateStorageNode(WirelessStorageNodeSnapshot node) {
        if (savedData == null) return;

        UUID networkId = node.getOwnerNetworkId();
        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return;

        network.updateNode(node);
    }

    @Override
    public void unregisterStorageNode(WirelessNodeId nodeId, UnregisterMode mode) {
        if (savedData == null) return;

        // Find which network owns this node
        for (WirelessEnergyNetwork network : savedData.getAllNetworks().values()) {
            if (network.getNodes().containsKey(nodeId)) {
                network.unregisterNode(nodeId);
                return;
            }
        }
    }

    // ==================== Internal Helpers ====================

    private WirelessEnergyNetwork getOrCreateNetworkForTransfer(UUID networkId) {
        return savedData.getOrCreateNetwork(networkId, "Wireless Network");
    }
}
