package gregtech.common.wireless;

import gregtech.api.util.GTLog;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the unified wireless energy service.
 * Singleton instance that manages all wireless energy network operations.
 * <p>
 * The wireless pool is a truly unbounded "bank account" per team —
 * no capacity limits, no per-node tracking. Physical PSS units
 * periodically rebalance against the pool.
 * <p>
 * Lifecycle:
 * <ul>
 *   <li>Initialized on overworld load (dimension 0).</li>
 *   <li>Ticks statistics and flushes dirty data each server tick.</li>
 *   <li>Cleared on world unload.</li>
 * </ul>
 */
public class WirelessEnergyServiceImpl implements WirelessEnergyService {

    private static WirelessEnergyServiceImpl INSTANCE;

    private WirelessEnergySavedData savedData;

    public WirelessEnergyServiceImpl() {}

    // ==================== Instance Management ====================

    public static WirelessEnergyServiceImpl getInstance() {
        return INSTANCE;
    }

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

        // Single pass: advance stats + flush dirty in one iteration
        boolean anyDirty = false;
        Iterator<Map.Entry<UUID, WirelessEnergyNetwork>> it = savedData.getAllNetworks().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WirelessEnergyNetwork> entry = it.next();
            WirelessEnergyNetwork network = entry.getValue();
            network.tickStats();
            if (network.checkAndClearDirty()) {
                anyDirty = true;
            }
            // Clean up empty stale networks
            if (network.getStored().signum() == 0 && network.getInputPerSecond().signum() == 0
                    && network.getOutputPerSecond().signum() == 0) {
                // Keep networks with nodes (they will have activity when PSS reconnects)
                // Only remove networks that have been idle for very long periods
                // For now: skip cleanup, the map is small
            }
        }
        if (anyDirty) {
            savedData.markDirty();
        }
    }

    // ==================== WirelessEnergyService Implementation ====================

    @Override
    public WirelessNetworkView getView(UUID actor) {
        if (savedData == null) return WirelessNetworkView.EMPTY;

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return WirelessNetworkView.EMPTY;

        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return WirelessNetworkView.EMPTY;

        return new WirelessNetworkView(
                network.getNetworkId(), network.getNetworkName(),
                network.getStored(),
                network.getInputPerSecond(), network.getOutputPerSecond());
    }

    @Override
    public TransferResult insert(UUID actor, long amount, TransferContext context) {
        if (amount <= 0) return TransferResult.success(0L);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = savedData.getOrCreateNetwork(networkId, "Wireless Network");
        long accepted = network.insert(amount);
        return TransferResult.success(accepted);
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
    public TransferResult extractUpTo(UUID actor, long amount, TransferContext context) {
        if (amount <= 0) return TransferResult.success(0L);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return TransferResult.noNetwork();

        long extracted = network.extractUpTo(amount);
        if (extracted == amount) {
            return TransferResult.success(extracted);
        } else if (extracted > 0) {
            return TransferResult.partial(extracted);
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

        WirelessEnergyNetwork network = savedData.getOrCreateNetwork(networkId, "Wireless Network");
        BigInteger accepted = network.insert(amount);
        return TransferResult.success(accepted);
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
    public TransferResult extractUpTo(UUID actor, BigInteger amount, TransferContext context) {
        if (amount.signum() <= 0) return TransferResult.success(BigInteger.ZERO);
        if (savedData == null) return TransferResult.noNetwork();

        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return TransferResult.noNetwork();

        WirelessEnergyNetwork network = savedData.getNetwork(networkId);
        if (network == null) return TransferResult.noNetwork();

        BigInteger extracted = network.extractUpTo(amount);
        if (extracted.compareTo(amount) == 0) {
            return TransferResult.success(extracted);
        } else if (extracted.signum() > 0) {
            return TransferResult.partial(extracted);
        } else {
            return TransferResult.insufficientEnergy();
        }
    }
}
