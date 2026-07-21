package gregtech.common.wireless;

import gregtech.api.util.GTLog;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;

import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server lifecycle owner and API implementation for team wireless channels. */
public class WirelessEnergyServiceImpl implements WirelessEnergyService {

    private static final long WIRELESS_CHARGING_RATE = 1_000_000L;
    private static WirelessEnergyServiceImpl INSTANCE;
    private WirelessEnergySavedData savedData;

    public static WirelessEnergyServiceImpl getInstance() { return INSTANCE; }
    public static WirelessEnergyService getService() { return INSTANCE; }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        INSTANCE = this;
        savedData = WirelessEnergySavedData.loadOrCreate(world);
        GTLog.logger.info("WirelessEnergyService: Initialized team channel service.");
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        if (savedData != null) savedData.flushDirtyNetworks();
        savedData = null;
        WirelessEnergySavedData.clearInstance();
        WirelessTeamResolver.clearOverrides();
        INSTANCE = null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || savedData == null) return;
        boolean anyDirty = false;
        for (Map.Entry<UUID, WirelessEnergyNetwork> entry : savedData.getAllNetworks().entrySet()) {
            WirelessEnergyNetwork network = entry.getValue();
            chargeWirelessItems(network);
            network.tickStats();
            anyDirty |= network.checkAndClearDirty();
        }
        if (anyDirty) savedData.markDirty();
    }

    @Override
    public WirelessNetworkView getView(UUID actor) {
        return getView(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID);
    }

    @Override
    public WirelessNetworkView getView(UUID actor, int channelId) {
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return WirelessNetworkView.EMPTY;
        WirelessEnergyChannel channel = network.getChannel(channelId);
        return channel == null ? WirelessNetworkView.EMPTY : toView(network, channel);
    }

    @Override
    public List<WirelessNetworkView> getChannels(UUID actor) {
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return new ArrayList<>();
        List<WirelessNetworkView> views = new ArrayList<>();
        for (WirelessEnergyChannel channel : network.getChannels()) views.add(toView(network, channel));
        return views;
    }

    @Override
    public int createChannel(UUID actor, String name) {
        WirelessEnergyNetwork network = getNetwork(actor, true);
        if (network == null) return -1;
        WirelessEnergyChannel channel = network.createChannel(name);
        savedData.markDirty();
        return channel.getChannelId();
    }

    @Override
    public boolean renameChannel(UUID actor, int channelId, String name) {
        WirelessEnergyNetwork network = getNetwork(actor, false);
        return network != null && network.renameChannel(channelId, name);
    }

    @Override
    public boolean deleteChannel(UUID actor, int channelId) {
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null || !network.deleteChannel(channelId)) return false;
        savedData.markDirty();
        return true;
    }

    @Override
    public boolean setWirelessCharging(UUID actor, int channelId, boolean enabled, int slotMask) {
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return false;
        WirelessEnergyChannel channel = network.getChannel(channelId);
        if (channel == null) return false;
        channel.setWirelessChargingEnabled(enabled);
        channel.setWirelessChargingSlots(slotMask);
        return true;
    }

    @Override
    public void updateEndpoint(UUID actor, int channelId, String key, String type, int dimension, long position,
                               boolean chunkLoaded, boolean forceLoaded, long gameTime) {
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return;
        WirelessEnergyChannel channel = network.getChannel(channelId);
        if (channel != null) channel.touchEndpoint(key, type, dimension, position, chunkLoaded, forceLoaded, gameTime);
    }

    @Override
    public TransferResult transfer(UUID actor, int sourceChannelId, int targetChannelId, BigInteger amount,
                                   TransferContext context) {
        if (amount.signum() <= 0 || sourceChannelId == targetChannelId) return TransferResult.success(BigInteger.ZERO);
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return TransferResult.noNetwork();
        WirelessEnergyChannel source = network.getChannel(sourceChannelId);
        WirelessEnergyChannel target = network.getChannel(targetChannelId);
        if (source == null || target == null) return TransferResult.noNetwork();
        if (source.extract(amount).signum() == 0) return TransferResult.insufficientEnergy();
        target.insert(amount);
        return TransferResult.success(amount);
    }

    @Override
    public TransferResult insert(UUID actor, long amount, TransferContext context) {
        return insert(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID, amount, context);
    }

    @Override
    public TransferResult insert(UUID actor, int channelId, long amount, TransferContext context) {
        return insert(actor, channelId, BigInteger.valueOf(Math.max(amount, 0)), context);
    }

    @Override
    public TransferResult extract(UUID actor, long amount, TransferContext context) {
        return extract(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID, amount, context);
    }

    @Override
    public TransferResult extract(UUID actor, int channelId, long amount, TransferContext context) {
        return extract(actor, channelId, BigInteger.valueOf(Math.max(amount, 0)), context);
    }

    @Override
    public TransferResult extractUpTo(UUID actor, long amount, TransferContext context) {
        return extractUpTo(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID, amount, context);
    }

    @Override
    public TransferResult extractUpTo(UUID actor, int channelId, long amount, TransferContext context) {
        return extractUpTo(actor, channelId, BigInteger.valueOf(Math.max(amount, 0)), context);
    }

    @Override
    public TransferResult insert(UUID actor, BigInteger amount, TransferContext context) {
        return insert(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID, amount, context);
    }

    @Override
    public TransferResult insert(UUID actor, int channelId, BigInteger amount, TransferContext context) {
        if (amount.signum() <= 0) return TransferResult.success(BigInteger.ZERO);
        WirelessEnergyNetwork network = getNetwork(actor, true);
        if (network == null) return TransferResult.noNetwork();
        WirelessEnergyChannel channel = network.getChannel(channelId);
        if (channel == null) return TransferResult.noNetwork();
        return TransferResult.success(channel.insert(amount));
    }

    @Override
    public TransferResult extract(UUID actor, BigInteger amount, TransferContext context) {
        return extract(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID, amount, context);
    }

    @Override
    public TransferResult extract(UUID actor, int channelId, BigInteger amount, TransferContext context) {
        if (amount.signum() <= 0) return TransferResult.success(BigInteger.ZERO);
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return TransferResult.noNetwork();
        WirelessEnergyChannel channel = network.getChannel(channelId);
        if (channel == null) return TransferResult.noNetwork();
        BigInteger extracted = channel.extract(amount);
        return extracted.signum() > 0 ? TransferResult.success(extracted) : TransferResult.insufficientEnergy();
    }

    @Override
    public TransferResult extractUpTo(UUID actor, BigInteger amount, TransferContext context) {
        return extractUpTo(actor, WirelessEnergyNetwork.DEFAULT_CHANNEL_ID, amount, context);
    }

    @Override
    public TransferResult extractUpTo(UUID actor, int channelId, BigInteger amount, TransferContext context) {
        if (amount.signum() <= 0) return TransferResult.success(BigInteger.ZERO);
        WirelessEnergyNetwork network = getNetwork(actor, false);
        if (network == null) return TransferResult.noNetwork();
        WirelessEnergyChannel channel = network.getChannel(channelId);
        if (channel == null) return TransferResult.noNetwork();
        BigInteger extracted = channel.extractUpTo(amount);
        if (extracted.signum() == 0) return TransferResult.insufficientEnergy();
        return extracted.equals(amount) ? TransferResult.success(extracted) : TransferResult.partial(extracted);
    }

    private WirelessEnergyNetwork getNetwork(UUID actor, boolean create) {
        if (savedData == null) return null;
        UUID networkId = WirelessTeamResolver.resolveNetworkId(actor);
        if (networkId == null) return null;
        return create ? savedData.getOrCreateNetwork(networkId, "Wireless Network") : savedData.getNetwork(networkId);
    }

    private static WirelessNetworkView toView(WirelessEnergyNetwork network, WirelessEnergyChannel channel) {
        return new WirelessNetworkView(network.getNetworkId(), channel.getChannelId(), channel.getName(),
                channel.getStored(), channel.getInputPerSecond(), channel.getOutputPerSecond(),
                channel.isWirelessChargingEnabled(), channel.getWirelessChargingSlots());
    }

    private void chargeWirelessItems(WirelessEnergyNetwork network) {
        boolean anyEnabled = false;
        for (WirelessEnergyChannel channel : network.getChannels()) {
            if (channel.isWirelessChargingEnabled()) {
                anyEnabled = true;
                break;
            }
        }
        if (!anyEnabled) return;

        for (EntityPlayerMP player : net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getPlayerList().getPlayers()) {
            if (!network.getNetworkId().equals(WirelessTeamResolver.resolveNetworkId(player.getUniqueID()))) continue;
            for (WirelessEnergyChannel channel : network.getChannels()) {
                if (!channel.isWirelessChargingEnabled() || channel.getStored().signum() <= 0) continue;
                chargePlayerItems(channel, player);
            }
        }
    }

    private static void chargePlayerItems(WirelessEnergyChannel channel, EntityPlayerMP player) {
        long remaining = channel.getStored().min(BigInteger.valueOf(WIRELESS_CHARGING_RATE)).longValue();
        int slotMask = Math.min(2, Math.max(0, channel.getWirelessChargingSlots()));

        remaining = chargeStack(channel, player.getHeldItemMainhand(), remaining);
        if (remaining > 0) remaining = chargeStack(channel, player.getHeldItemOffhand(), remaining);
        if (slotMask >= 1) {
            for (ItemStack stack : player.inventory.armorInventory) {
                if (remaining <= 0) break;
                remaining = chargeStack(channel, stack, remaining);
            }
        }
        if (slotMask >= 2) {
            for (ItemStack stack : player.inventory.mainInventory) {
                if (remaining <= 0) break;
                remaining = chargeStack(channel, stack, remaining);
            }
        }
    }

    private static long chargeStack(WirelessEnergyChannel channel, ItemStack stack, long remaining) {
        if (remaining <= 0 || stack.isEmpty()) return remaining;
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null || !electricItem.chargeable()) return remaining;
        long accepted = electricItem.charge(remaining, Integer.MAX_VALUE, true, true);
        if (accepted <= 0) return remaining;
        long charged = electricItem.charge(accepted, Integer.MAX_VALUE, true, false);
        if (charged > 0) channel.extract(charged);
        return remaining - charged;
    }
}
