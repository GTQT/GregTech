package gregtech.api.wireless;

/**
 * Minimal channel information contract shared by the energy and computation
 * network views, consumed by the Flux channel selection widgets.
 */
public interface ChannelInfo {

    int getChannelId();

    String getNetworkName();
}
