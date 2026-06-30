package gregtech.api.pattern.casing;

import gregtech.api.pattern.FormedStructureView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named channel for tracking tiered structure data during pattern matching.
 * Channels are used to coordinate tiered casing selection during structure
 * checking and auto-building.
 *
 * <p>In GT5's structurelib, channels are used with trigger items (e.g. circuit #1 = tier 1).
 * Here, channel values can be set via the builder GUI for auto-building, or
 * detected automatically during structure checking.
 *
 * <p>Usage in pattern predicates:
 * <pre>{@code
 * // In DeclarativePatternBuilder:
 * .tieredCasing('C', coilGroup)
 *
 * // In formStructure(FormedStructureView formed):
 * ICasing matched = channel.getMatchedCasing(formed);
 * }</pre>
 *
 * @see GTCasingGroups for pre-defined casing group registrations (which auto-create channels)
 */
public interface StructureChannel {

    /**
     * @return the unique channel name
     */
    @NotNull
    String getName();

    /**
     * @return human-readable description for tooltips and GUI display
     */
    @NotNull
    String getDefaultTooltip();

    /**
     * Get the matched ICasing object from a typed formation view.
     *
     * @param formed the typed formation view
     * @return the matched ICasing, or null if not set
     */
    @Nullable
    default ICasing getMatchedCasing(@NotNull FormedStructureView formed) {
        return formed.getChannelAggregate(this, ICasing.class);
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
