package gregtech.api.pattern.casing;

import org.jetbrains.annotations.NotNull;

/**
 * A simple immutable implementation of {@link StructureChannel}.
 * <p>
 * The tooltip key is automatically derived as {@code "gregtech.structure_channel." + name}.
 * <p>
 * Use this when creating custom channels that don't need to be enum constants.
 * Channels created this way are typically registered automatically by
 * {@link CasingDefinition#fromMap} and similar factory methods.
 *
 * @see StructureChannel
 * @see CasingRegistration
 */
public class SimpleStructureChannel implements StructureChannel {

    private static final String TOOLTIP_PREFIX = "gregtech.structure_channel.";

    private final String name;
    private final String tooltip;

    /**
     * Create a channel with auto-derived tooltip key.
     *
     * @param name the unique channel name (e.g. "heating_coil")
     */
    public SimpleStructureChannel(@NotNull String name) {
        this.name = name;
        this.tooltip = TOOLTIP_PREFIX + name;
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    public String getDefaultTooltip() {
        return tooltip;
    }
}