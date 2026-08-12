package gregtech.common.wireless;

import gregtech.api.wireless.IWirelessComputationService;
import gregtech.api.wireless.WirelessComputationView;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Small server-safe helpers for the ModularUI channel selectors of the cloud computation hatches. */
public final class WirelessComputationChannelUi {

    private WirelessComputationChannelUi() {}

    public static List<WirelessComputationView> getChannels(UUID owner) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        return service == null ? Collections.emptyList() : service.getChannels(owner);
    }

    public static int indexOf(UUID owner, int channelId) {
        List<WirelessComputationView> channels = getChannels(owner);
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getChannelId() == channelId) return i;
        }
        return 0;
    }

    public static int channelIdAt(UUID owner, int index) {
        List<WirelessComputationView> channels = getChannels(owner);
        return channels.isEmpty() || index < 0 || index >= channels.size() ? 0 : channels.get(index).getChannelId();
    }

    public static int nextChannelId(UUID owner, int channelId) {
        List<WirelessComputationView> channels = getChannels(owner);
        if (channels.size() < 2) return channelId;
        return channels.get((indexOf(owner, channelId) + 1) % channels.size()).getChannelId();
    }
}
