package gregtech.common.wireless;

import net.minecraft.nbt.NBTTagCompound;

/** Persisted light-weight record for a wireless endpoint, including its last loaded state. */
public final class WirelessEndpointRecord {

    private final String key;
    private final String type;
    private final int dimension;
    private final long position;
    private boolean chunkLoaded;
    private boolean forceLoaded;
    private long lastSeen;

    public WirelessEndpointRecord(String key, String type, int dimension, long position) {
        this.key = key;
        this.type = type;
        this.dimension = dimension;
        this.position = position;
    }

    public void touch(boolean chunkLoaded, boolean forceLoaded, long gameTime) {
        this.chunkLoaded = chunkLoaded;
        this.forceLoaded = forceLoaded;
        this.lastSeen = gameTime;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("key", key);
        tag.setString("type", type);
        tag.setInteger("dim", dimension);
        tag.setLong("pos", position);
        tag.setBoolean("loaded", chunkLoaded);
        tag.setBoolean("forced", forceLoaded);
        tag.setLong("seen", lastSeen);
        return tag;
    }

    public static WirelessEndpointRecord readFromNBT(NBTTagCompound tag) {
        WirelessEndpointRecord record = new WirelessEndpointRecord(tag.getString("key"), tag.getString("type"),
                tag.getInteger("dim"), tag.getLong("pos"));
        record.touch(tag.getBoolean("loaded"), tag.getBoolean("forced"), tag.getLong("seen"));
        return record;
    }
}
