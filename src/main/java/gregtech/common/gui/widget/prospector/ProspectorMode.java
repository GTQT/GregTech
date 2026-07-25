package gregtech.common.gui.widget.prospector;

import org.jetbrains.annotations.NotNull;

public enum ProspectorMode {

    ORE("ore_prospector", "metaitem.prospector.mode.ores"),
    BEDROCK_FLUID("fluid_prospector", "metaitem.prospector.mode.bedrock_fluid"),
    BEDROCK_ORE("bedrock_ore_prospector", "metaitem.prospector.mode.bedrock_ore");

    public static final ProspectorMode[] VALUES = values();

    public final String terminalName;
    public final String unlocalizedName;

    ProspectorMode(@NotNull String terminalName, @NotNull String unlocalizedName) {
        this.terminalName = terminalName;
        this.unlocalizedName = unlocalizedName;
    }

    @NotNull
    public ProspectorMode next() {
        int next = ordinal() + 1;
        if (next >= VALUES.length) {
            return ProspectorMode.VALUES[0];
        }
        return VALUES[next];
    }
}
