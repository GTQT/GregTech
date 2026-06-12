package gregtech.api.pattern.casing;

import gregtech.api.pattern.PatternMatchContext;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A typed container for structure channel tier values.
 * Wraps a {@code Map<StructureChannel, Integer>} and provides conversion utilities
 * to/from raw string-keyed maps, NBT, and {@link PatternMatchContext}.
 *
 * <p>This class is mutable — values can be set/removed at any time.
 * Use {@link #unmodifiableView()} if you need a read-only view.
 *
 * <p>Typical usage flow:
 * <ol>
 *   <li>JEI/builder creates a StructureChannelValues from user selections</li>
 *   <li>Values are passed to {@code getMatchingShapes(channelValues.toMap())}</li>
 *   <li>After structure formation, values can be read back from PatternMatchContext</li>
 * </ol>
 *
 * @see StructureChannelRegistry for resolving channel names
 * @see StructureChannel for individual channel definitions
 */
public final class StructureChannelValues {

    private final Map<StructureChannel, Integer> values;

    public StructureChannelValues() {
        this.values = new LinkedHashMap<>();
    }

    private StructureChannelValues(@NotNull Map<StructureChannel, Integer> values) {
        this.values = values;
    }

    /**
     * Create from a raw string-keyed map (e.g. from JEI or builder GUI).
     * Keys are resolved via {@link StructureChannelRegistry#resolve(String)},
     * which handles both canonical names and legacy aliases.
     * Unknown keys are silently skipped.
     *
     * @param map the raw map (channel name/alias -> tier value)
     * @return a new StructureChannelValues instance
     */
    @NotNull
    public static StructureChannelValues fromMap(@Nullable Map<String, Integer> map) {
        StructureChannelValues result = new StructureChannelValues();
        if (map == null || map.isEmpty()) return result;

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            StructureChannel channel = StructureChannelRegistry.resolve(entry.getKey());
            if (channel != null) {
                result.values.put(channel, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Convert back to a raw string-keyed map using canonical channel names.
     *
     * @return a new map with channel canonical names as keys
     */
    @NotNull
    public Map<String, Integer> toMap() {
        Map<String, Integer> map = new HashMap<>();
        for (Map.Entry<StructureChannel, Integer> entry : values.entrySet()) {
            map.put(entry.getKey().getName(), entry.getValue());
        }
        return map;
    }

    /**
     * Deserialize from an NBT compound. Each key in the compound is a channel name,
     * each value is an integer tag.
     *
     * @param nbt the NBT data
     * @return a new StructureChannelValues instance
     */
    @NotNull
    public static StructureChannelValues fromNBT(@Nullable NBTTagCompound nbt) {
        StructureChannelValues result = new StructureChannelValues();
        if (nbt == null || nbt.isEmpty()) return result;

        for (String key : nbt.getKeySet()) {
            StructureChannel channel = StructureChannelRegistry.resolve(key);
            if (channel != null) {
                result.values.put(channel, nbt.getInteger(key));
            }
        }
        return result;
    }

    /**
     * Serialize to an NBT compound using canonical channel names as keys.
     *
     * @return the NBT representation
     */
    @NotNull
    public NBTTagCompound toNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        for (Map.Entry<StructureChannel, Integer> entry : values.entrySet()) {
            nbt.setInteger(entry.getKey().getName(), entry.getValue());
        }
        return nbt;
    }

    /**
     * Read channel values from a PatternMatchContext (after structure formation).
     * Reads all registered channels and collects those with non-zero tier values.
     *
     * @param context the pattern match context
     * @return a new StructureChannelValues with values from context
     */
    @NotNull
    public static StructureChannelValues fromContext(@NotNull PatternMatchContext context) {
        StructureChannelValues result = new StructureChannelValues();
        for (StructureChannel channel : StructureChannelRegistry.getAll()) {
            int value = channel.getValue(context);
            if (value != 0) {
                result.values.put(channel, value);
            }
        }
        return result;
    }

    /**
     * Write all channel values into a PatternMatchContext.
     * Useful for setting up context before auto-build or preview generation.
     *
     * @param context the target context
     */
    public void applyToContext(@NotNull PatternMatchContext context) {
        for (Map.Entry<StructureChannel, Integer> entry : values.entrySet()) {
            entry.getKey().setValue(context, entry.getValue());
        }
    }

    /**
     * Get the value for a specific channel.
     *
     * @param channel the channel
     * @return the tier value, or 0 if not set
     */
    public int getValue(@NotNull StructureChannel channel) {
        return values.getOrDefault(channel, 0);
    }

    /**
     * Set the value for a specific channel.
     *
     * @param channel the channel
     * @param value   the tier value (0 removes the entry)
     */
    public void setValue(@NotNull StructureChannel channel, int value) {
        if (value == 0) {
            values.remove(channel);
        } else {
            values.put(channel, value);
        }
    }

    /**
     * Check if a value is set for the given channel.
     */
    public boolean hasValue(@NotNull StructureChannel channel) {
        return values.containsKey(channel);
    }

    /**
     * @return true if no channel values are set
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * @return number of channels with values set
     */
    public int size() {
        return values.size();
    }

    /**
     * Clear all values.
     */
    public void clear() {
        values.clear();
    }

    /**
     * @return an unmodifiable view of the internal channel-value map
     */
    @NotNull
    public Map<StructureChannel, Integer> getEntries() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Create an independent mutable copy.
     */
    @NotNull
    public StructureChannelValues copy() {
        return new StructureChannelValues(new LinkedHashMap<>(values));
    }

    /**
     * @return an unmodifiable view of this StructureChannelValues
     */
    @NotNull
    public StructureChannelValues unmodifiableView() {
        return new StructureChannelValues(Collections.unmodifiableMap(values));
    }

    // --- Semantic helpers ---

    /**
     * Check if NO_HATCH mode is enabled.
     * When NO_HATCH=1, autoBuild should skip hatch placement and only use casing blocks.
     *
     * @return true if NO_HATCH channel value is 1
     */
    public boolean isNoHatch() {
        return getValue(GTStructureChannels.NO_HATCH) == 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StructureChannelValues{");
        boolean first = true;
        for (Map.Entry<StructureChannel, Integer> entry : values.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey().getName()).append('=').append(entry.getValue());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructureChannelValues)) return false;
        return values.equals(((StructureChannelValues) o).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
