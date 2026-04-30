package gregtech.api.pattern.casing;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import net.minecraft.client.resources.I18n;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for automatically generating structure tooltip lines from declarative definitions.
 * Given a set of casing slots and hatch declarations, produces formatted tooltip strings
 * describing what the structure requires.
 *
 * <p>Example output:
 * <pre>
 * "16x Steel Solid Casing (at least 12)"
 * "1-4x Item Input Bus"
 * "1-4x Item Output Bus"
 * "Heating Coils (all same tier)"
 * </pre>
 *
 * @see DeclarativePatternBuilder for the builder that uses this
 */
public class StructureTooltipBuilder {

    private final List<String> lines = new ArrayList<>();

    /**
     * Add a casing requirement line.
     *
     * @param casing   the casing definition
     * @param minCount minimum required count
     * @param maxCount maximum possible count (from aisle definition)
     */
    public StructureTooltipBuilder addCasing(@NotNull ICasing casing, int minCount, int maxCount) {
        String name = I18n.format(casing.getTranslationKey());
        if (minCount == maxCount) {
            lines.add(String.format("%dx %s", maxCount, name));
        } else {
            lines.add(String.format("%dx %s (%s %d)",
                    maxCount, name,
                    I18n.format("gregtech.multiblock.tooltip.at_least"),
                    minCount));
        }
        return this;
    }

    /**
     * Add a hatch requirement line.
     *
     * @param ability  the multiblock ability
     * @param name     the localized name of the hatch
     * @param minCount minimum required
     * @param maxCount maximum allowed
     */
    public StructureTooltipBuilder addHatch(@NotNull MultiblockAbility<?> ability,
                                            @NotNull String name,
                                            int minCount, int maxCount) {
        if (minCount == 0) {
            lines.add(String.format("0-%dx %s (%s)",
                    maxCount, name,
                    I18n.format("gregtech.multiblock.tooltip.optional")));
        } else if (minCount == maxCount) {
            lines.add(String.format("%dx %s", maxCount, name));
        } else {
            lines.add(String.format("%d-%dx %s", minCount, maxCount, name));
        }
        return this;
    }

    /**
     * Add a tiered casing group requirement line.
     *
     * @param group the casing group
     */
    public StructureTooltipBuilder addTieredGroup(@NotNull ICasingGroup group) {
        String name = I18n.format(group.getTranslationKey());
        if (group.requiresUniformTier()) {
            lines.add(String.format("%s (%s)",
                    name,
                    I18n.format("gregtech.multiblock.tooltip.same_tier")));
        } else {
            lines.add(name);
        }
        return this;
    }

    /**
     * Add a custom tooltip line.
     */
    public StructureTooltipBuilder addCustom(@NotNull String line) {
        lines.add(line);
        return this;
    }

    /**
     * @return the built tooltip lines
     */
    public List<String> build() {
        return new ArrayList<>(lines);
    }

    /**
     * @return the lines as a formatted array for use in tooltip methods
     */
    public String[] toArray() {
        return lines.toArray(new String[0]);
    }
}
