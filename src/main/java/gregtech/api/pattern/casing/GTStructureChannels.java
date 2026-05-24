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
    HEATING_COIL("heating_coil"),
    WIRE_COIL("wire_coil"),

    // --- Glass ---
    BOROSILICATE_GLASS("borosilicate_glass"),

    // --- Machine Casings ---
    MACHINE_CASING("machine_casing"),
    SOLID_CASING("solid_casing"),
    PIPE_CASING("pipe_casing"),
    ITEM_PIPE_CASING("item_pipe_casing"),

    // --- Structure Dimensions ---
    STRUCTURE_HEIGHT("structure_height"),
    STRUCTURE_LENGTH("structure_length"),

    // --- Hatch Tier ---
    HATCH_TIER("hatch_tier"),

    // --- Energy ---
    ENERGY_CASING("energy_casing"),

    // --- Solenoid ---
    SOLENOID("solenoid"),

    // --- Battery/Capacitor ---
    BATTERY("battery"),

    // --- Hatch Placement Control ---
    // When no_hatch=1 in channelValues, autoBuild skips hatch candidates
    // and only places casing blocks. When absent or 0, hatches are placed normally.
    NO_HATCH("no_hatch"),

    // --- Multi-Piece Structure Piece Selection ---
    // Controls which piece of a MultiPiecePattern to build.
    // 0 = main pattern only (default), 1+ = piece index (1-based) from MultiPiecePattern.
    STRUCTURE_PIECE("structure_piece"),
    ;

    private static final String TOOLTIP_PREFIX = "gregtech.structure_channel.";

    private final String name;
    private final String tooltip;

    GTStructureChannels(@NotNull String name) {
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