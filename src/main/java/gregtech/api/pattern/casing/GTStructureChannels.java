package gregtech.api.pattern.casing;

import org.jetbrains.annotations.NotNull;

/**
 * Pre-defined structure channels for GregTech multiblocks.
 * Each channel corresponds to a type of tiered component that can vary between
 * different tiers of the same multiblock structure.
 *
 * <p>Channels are used for:
 * <ul>
 *   <li>Tracking detected tier during structure checking (stored in PatternMatchContext)</li>
 *   <li>Selecting which tier to build during auto-building (set via builder GUI)</li>
 *   <li>Displaying tier information in multiblock tooltips</li>
 * </ul>
 *
 * <p>To add a new channel for an addon, simply create a new enum or implement
 * {@link StructureChannel} directly.
 *
 * @see StructureChannel for the interface contract
 * @see DeclarativePatternBuilder for usage in pattern definitions
 */
public enum GTStructureChannels implements StructureChannel {

    // --- Heating/Processing ---
    HEATING_COIL("heating_coil", "gregtech.structure_channel.heating_coil"),
    WIRE_COIL("wire_coil", "gregtech.structure_channel.wire_coil"),

    // --- Glass ---
    BOROSILICATE_GLASS("borosilicate_glass", "gregtech.structure_channel.borosilicate_glass"),

    // --- Machine Casings ---
    MACHINE_CASING("machine_casing", "gregtech.structure_channel.machine_casing"),
    SOLID_CASING("solid_casing", "gregtech.structure_channel.solid_casing"),
    PIPE_CASING("pipe_casing", "gregtech.structure_channel.pipe_casing"),
    ITEM_PIPE_CASING("item_pipe_casing", "gregtech.structure_channel.item_pipe_casing"),

    // --- Structure Dimensions ---
    STRUCTURE_HEIGHT("structure_height", "gregtech.structure_channel.structure_height"),
    STRUCTURE_LENGTH("structure_length", "gregtech.structure_channel.structure_length"),

    // --- Hatch Tier ---
    HATCH_TIER("hatch_tier", "gregtech.structure_channel.hatch_tier"),

    // --- Energy ---
    ENERGY_CASING("energy_casing", "gregtech.structure_channel.energy_casing"),

    // --- Solenoid ---
    SOLENOID("solenoid", "gregtech.structure_channel.solenoid"),

    // --- Battery/Capacitor ---
    BATTERY("battery", "gregtech.structure_channel.battery"),

    // --- Hatch Placement Control ---
    // When gt_no_hatch=1 in channelValues, autoBuild skips hatch candidates
    // and only places casing blocks. When absent or 0, hatches are placed normally.
    NO_HATCH("gt_no_hatch", "gregtech.structure_channel.no_hatch"),
    ;

    private final String name;
    private final String tooltip;

    GTStructureChannels(@NotNull String name, @NotNull String tooltip) {
        this.name = name;
        this.tooltip = tooltip;
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
