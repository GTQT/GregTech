package gregtech.common.wireless;

import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Small server-safe helpers for the legacy ModularUI channel selectors. */
public final class WirelessChannelUi {

    private WirelessChannelUi() {}

    public static List<WirelessNetworkView> getChannels(UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        return service == null ? Collections.emptyList() : service.getChannels(owner);
    }

    public static String[] getChannelNames(UUID owner) {
        List<WirelessNetworkView> channels = getChannels(owner);
        if (channels.isEmpty()) return new String[] { "Main" };
        String[] names = new String[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            WirelessNetworkView channel = channels.get(i);
            names[i] = channel.getChannelId() + ": " + channel.getNetworkName();
        }
        return names;
    }

    public static int indexOf(UUID owner, int channelId) {
        List<WirelessNetworkView> channels = getChannels(owner);
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getChannelId() == channelId) return i;
        }
        return 0;
    }

    public static int channelIdAt(UUID owner, int index) {
        List<WirelessNetworkView> channels = getChannels(owner);
        return channels.isEmpty() || index < 0 || index >= channels.size() ? 0 : channels.get(index).getChannelId();
    }

    public static int nextChannelId(UUID owner, int channelId) {
        List<WirelessNetworkView> channels = getChannels(owner);
        if (channels.size() < 2) return channelId;
        return channels.get((indexOf(owner, channelId) + 1) % channels.size()).getChannelId();
    }
}
