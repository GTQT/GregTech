package gregtech.api.pattern.element;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-instance formed structure metadata.
 * Stores the actual repeat counts and channel values when a multiblock structure
 * is successfully formed. This metadata is persisted to NBT and can be read by
 * JEI, recipe logic, and async structure checking.
 *
 * <p>Key contents:
 * <ul>
 *   <li>{@code pieceRepeats} — piece name → actual repeat counts along each repeat axis</li>
 *   <li>{@code pieceChannelNames} — piece name → channel names for each repeat axis</li>
 *   <li>{@code channelValues} — channel name → actual tier/value</li>
 * </ul>
 */
public final class FormedStructureMetadata {

    /** piece name → actual repeat counts along each repeat axis (empty array = fixed piece) */
    private final Map<String, int[]> pieceRepeats;

    /** piece name → channel names for each repeat axis (nullable entries) */
    private final Map<String, String[]> pieceChannelNames;

    /** channel name → actual tier/value */
    private final Map<String, Integer> channelValues;

    public FormedStructureMetadata(@NotNull Map<String, int[]> pieceRepeats,
                                   @Nullable Map<String, String[]> pieceChannelNames,
                                   @NotNull Map<String, Integer> channelValues) {
        this.pieceRepeats = Collections.unmodifiableMap(new HashMap<>(pieceRepeats));
        this.pieceChannelNames = pieceChannelNames == null
                ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(pieceChannelNames));
        this.channelValues = Collections.unmodifiableMap(new HashMap<>(channelValues));
    }

    /**
     * Get the repeat count for a specific piece and axis.
     *
     * @param pieceName the piece name
     * @param axisIndex the axis index within the piece's repeat axes
     * @return the repeat count, or 0 if not found
     */
    public int getPieceRepeat(@NotNull String pieceName, int axisIndex) {
        int[] reps = pieceRepeats.get(pieceName);
        if (reps == null || axisIndex < 0 || axisIndex >= reps.length) return 0;
        return reps[axisIndex];
    }

    /**
     * Get all repeat counts for a specific piece.
     *
     * @param pieceName the piece name
     * @return the repeat counts array, or empty array if not found
     */
    @NotNull
    public int[] getPieceRepeats(@NotNull String pieceName) {
        int[] reps = pieceRepeats.get(pieceName);
        return reps != null ? reps : new int[0];
    }

    /**
     * Get the channel names for a specific piece's repeat axes.
     *
     * @param pieceName the piece name
     * @return the channel names array, or null if not found
     */
    @Nullable
    public String[] getPieceChannelNames(@NotNull String pieceName) {
        return pieceChannelNames.get(pieceName);
    }

    /**
     * Get the channel value (tier) for a specific channel.
     *
     * @param channelName the channel name
     * @return the channel value, or 0 if not found
     */
    public int getChannelValue(@NotNull String channelName) {
        return channelValues.getOrDefault(channelName, 0);
    }

    /**
     * Get all channel values as an unmodifiable map.
     */
    @NotNull
    public Map<String, Integer> getChannelValues() {
        return channelValues;
    }

    // --- NBT serialization ---

    private static final String KEY_PIECE_REPEATS = "PieceRepeats";
    private static final String KEY_PIECE_CHANNELS = "PieceChannels";
    private static final String KEY_CHANNEL_VALUES = "ChannelValues";
    private static final String KEY_NAME = "Name";
    private static final String KEY_DATA = "Data";

    /**
     * Serialize this metadata to NBT.
     */
    @NotNull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        // Piece repeats
        NBTTagCompound repeatsTag = new NBTTagCompound();
        for (Map.Entry<String, int[]> entry : pieceRepeats.entrySet()) {
            repeatsTag.setIntArray(entry.getKey(), entry.getValue());
        }
        tag.setTag(KEY_PIECE_REPEATS, repeatsTag);

        // Piece channel names
        NBTTagCompound channelsTag = new NBTTagCompound();
        for (Map.Entry<String, String[]> entry : pieceChannelNames.entrySet()) {
            NBTTagList list = new NBTTagList();
            for (String name : entry.getValue()) {
                list.appendTag(new NBTTagString(name != null ? name : ""));
            }
            channelsTag.setTag(entry.getKey(), list);
        }
        tag.setTag(KEY_PIECE_CHANNELS, channelsTag);

        // Channel values
        NBTTagCompound valuesTag = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : channelValues.entrySet()) {
            valuesTag.setInteger(entry.getKey(), entry.getValue());
        }
        tag.setTag(KEY_CHANNEL_VALUES, valuesTag);

        return tag;
    }

    /**
     * Deserialize metadata from NBT.
     */
    @NotNull
    public static FormedStructureMetadata readFromNBT(@NotNull NBTTagCompound tag) {
        // Piece repeats
        Map<String, int[]> repeats = new HashMap<>();
        NBTTagCompound repeatsTag = tag.getCompoundTag(KEY_PIECE_REPEATS);
        for (String key : repeatsTag.getKeySet()) {
            repeats.put(key, repeatsTag.getIntArray(key));
        }

        // Piece channel names
        Map<String, String[]> channels = new HashMap<>();
        NBTTagCompound channelsTag = tag.getCompoundTag(KEY_PIECE_CHANNELS);
        for (String key : channelsTag.getKeySet()) {
            NBTTagList list = channelsTag.getTagList(key, 8); // 8 = STRING
            String[] names = new String[list.tagCount()];
            for (int i = 0; i < list.tagCount(); i++) {
                String s = list.getStringTagAt(i);
                names[i] = s.isEmpty() ? null : s;
            }
            channels.put(key, names);
        }

        // Channel values
        Map<String, Integer> values = new HashMap<>();
        NBTTagCompound valuesTag = tag.getCompoundTag(KEY_CHANNEL_VALUES);
        for (String key : valuesTag.getKeySet()) {
            values.put(key, valuesTag.getInteger(key));
        }

        return new FormedStructureMetadata(repeats, channels, values);
    }

    /**
     * Construct from check result data.
     *
     * @param pieceRepeats   piece name → actual repeat counts
     * @param channelValues  channel name → actual tier values
     * @return a new FormedStructureMetadata instance
     */
    @NotNull
    public static FormedStructureMetadata fromCheckResult(
            @NotNull Map<String, int[]> pieceRepeats,
            @NotNull Map<String, Integer> channelValues) {
        return new FormedStructureMetadata(pieceRepeats, null, channelValues);
    }
}
