package gregtech.common.wireless;

import gregtech.api.capability.IOpticalComputationProvider;

/**
 * A runtime registration of an uplink cloud computation hatch inside a
 * wireless computation channel. Holds a live reference to the provider
 * (the hatch, which forwards to its multiblock controller).
 * <p>
 * Not persisted: after a world reload the uplink hatch re-registers itself.
 */
public final class ComputationNode {

    private final String key;
    private final int tier;
    private final int dimension;
    private final long position;
    private final IOpticalComputationProvider provider;
    private long lastSeen;

    public ComputationNode(String key, int tier, int dimension, long position,
                           IOpticalComputationProvider provider, long gameTime) {
        this.key = key;
        this.tier = tier;
        this.dimension = dimension;
        this.position = position;
        this.provider = provider;
        this.lastSeen = gameTime;
    }

    public void touch(long gameTime) {
        this.lastSeen = gameTime;
    }

    public String getKey() { return key; }
    public int getTier() { return tier; }
    public int getDimension() { return dimension; }
    public long getPosition() { return position; }
    public IOpticalComputationProvider getProvider() { return provider; }
    public long getLastSeen() { return lastSeen; }
}
