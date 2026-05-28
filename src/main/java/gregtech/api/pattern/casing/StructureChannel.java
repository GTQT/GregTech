package gregtech.api.pattern.casing;

import gregtech.api.pattern.PatternMatchContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named channel for tracking tiered structure data during pattern matching.
 * A channel is a keyed slot in {@link PatternMatchContext} that stores an integer value
 * (typically a tier level). Channels are used to coordinate tiered casing selection
 * during structure checking and auto-building.
 *
 * <p>In GT5's structurelib, channels are used with trigger items (e.g. circuit #1 = tier 1).
 * Here, channels integrate with the existing PatternMatchContext system, and their values
 * can be set via the builder GUI for auto-building, or detected automatically during
 * structure checking.
 *
 * <p>Usage in pattern predicates:
 * <pre>{@code
 * // In DeclarativePatternBuilder:
 * .tieredCasing('C', coilGroup)
 *
 * // In formStructure():
 * ICasing matched = channel.getMatchedCasing(context);
 * }</pre>
 *
 * @see GTCasingGroups for pre-defined casing group registrations (which auto-create channels)
 * @see PatternMatchContext for the storage mechanism
 */
public interface StructureChannel {

    /**
     * @return the unique channel name, used as key in PatternMatchContext
     */
    @NotNull
    String getName();

    /**
     * @return human-readable description for tooltips and GUI display
     */
    @NotNull
    String getDefaultTooltip();

    /**
     * Get the channel value from a PatternMatchContext.
     * Reads the integer tier value stored as "{channelName}.tier".
     *
     * @param context the match context
     * @return the stored channel tier value, or 0 if not set
     */
    default int getValue(@NotNull PatternMatchContext context) {
        return context.getInt(getName() + ".tier");
    }

    /**
     * Set the channel value in a PatternMatchContext.
     * Stores the integer tier value as "{channelName}.tier".
     *
     * @param context the match context
     * @param value   the value to store
     */
    default void setValue(@NotNull PatternMatchContext context, int value) {
        context.set(getName() + ".tier", value);
    }

    /**
     * Get the matched ICasing object from a PatternMatchContext (if available).
     * During structure checking, the detected casing is stored under the channel name.
     *
     * @param context the match context
     * @return the matched ICasing, or null if not set
     */
    @Nullable
    @SuppressWarnings("unchecked")
    default ICasing getMatchedCasing(@NotNull PatternMatchContext context) {
        Object obj = context.get(getName());
        return obj instanceof ICasing ? (ICasing) obj : null;
    }

    /**
     * Get channel value clamped to a range.
     *
     * @param context the match context
     * @param min     minimum value (inclusive)
     * @param max     maximum value (inclusive)
     * @return the clamped value
     */
    default int getValueClamped(@NotNull PatternMatchContext context, int min, int max) {
        int raw = getValue(context);
        return Math.min(max, Math.max(min, raw));
    }

    /**
     * Check whether this channel has been set in the context.
     *
     * @param context the match context
     * @return true if the channel has a non-null value
     */
    default boolean hasValue(@NotNull PatternMatchContext context) {
        return context.get(getName()) != null;
    }

    /**
     * Get the indicator item representing a specific tier of this channel.
     * Indicator items are used in GUIs and tooltips to visually represent tier levels
     * (e.g. a coil block's ItemStack for the HEATING_COIL channel tier).
     *
     * <p>Default implementation delegates to {@link StructureChannelRegistry#getIndicator(StructureChannel, int)}.
     *
     * @param tier the tier value
     * @return the indicator item, or {@link net.minecraft.item.ItemStack#EMPTY} if not registered
     */
    @NotNull
    default net.minecraft.item.ItemStack getIndicatorItem(int tier) {
        return StructureChannelRegistry.getIndicator(this, tier);
    }
}
