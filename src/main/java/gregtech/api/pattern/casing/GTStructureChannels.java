package gregtech.api.pattern.casing;

import org.jetbrains.annotations.NotNull;

/**
 * Pre-defined structure channels for GregTech multiblocks.
 *
 * <p>Casing-related channels (heating coils, machine casings, borosilicate glass)
 * are now auto-created by {@link GTCasingGroups} and registered in
 * {@link StructureChannelRegistry}. Access them via:
 * <ul>
 *   <li>{@link GTCasingGroups#heatingCoils()} + {@code .channel()}</li>
 *   <li>{@link GTCasingGroups#machineCasings()} + {@code .channel()}</li>
 *   <li>{@link GTCasingGroups#borosilicateGlasses()} + {@code .channel()}</li>
 * </ul>
 *
 * <p>To add a new channel for an addon, either:
 * <ul>
 *   <li>Use {@link CasingDefinition#fromMap} / {@link CasingDefinition#fromIterable} which auto-creates the channel</li>
 *   <li>Create a {@link SimpleStructureChannel} and register it via {@link StructureChannelRegistry#register}</li>
 *   <li>Add an enum constant here (for channels without casing groups)</li>
 * </ul>
 *
 * @see StructureChannel for the interface contract
 * @see DeclarativePatternBuilder for usage in pattern definitions
 */
public enum GTStructureChannels implements StructureChannel {

    // --- Wire Coil ---
    WIRE_COIL("wire_coil"),

    // --- Structure Dimensions ---
    STRUCTURE_WIDTH("structure_width"),
    STRUCTURE_HEIGHT("structure_height"),
    STRUCTURE_LENGTH("structure_length"),
    STRUCTURE_TIER("structure_tier"),

    // --- Hatch Tier ---
    HATCH_TIER("hatch_tier"),

    // --- Energy ---
    ENERGY_CASING("energy_casing"),

    // --- Solenoid ---
    SOLENOID("solenoid"),

    // --- Battery/Capacitor ---
    BATTERY("battery"),

    // --- Solid/Pipe/Item-Pipe Casings ---
    SOLID_CASING("solid_casing"),
    PIPE_CASING("pipe_casing"),
    ITEM_PIPE_CASING("item_pipe_casing"),

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
