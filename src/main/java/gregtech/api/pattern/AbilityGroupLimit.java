package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structure-wide count limit for a group of interchangeable multiblock abilities.
 *
 * <p>The display ability is intentionally separate from the matched abilities so
 * different domains (energy, items, fluids, etc.) can report distinct missing
 * requirement names even when each domain supports multiple concrete hatch types.
 */
public final class AbilityGroupLimit {

    private final MultiblockAbility<?> displayAbility;
    private final List<MultiblockAbility<?>> abilities;
    private final int min;
    private final int max;

    public AbilityGroupLimit(@NotNull MultiblockAbility<?> displayAbility,
                             int min,
                             int max,
                             @NotNull List<MultiblockAbility<?>> abilities) {
        if (min < 0 || (max >= 0 && max < min)) {
            throw new IllegalArgumentException("Invalid ability group range [" + min + ", " + max + "]");
        }
        if (abilities.isEmpty()) {
            throw new IllegalArgumentException("Ability group must contain at least one ability");
        }
        this.displayAbility = displayAbility;
        this.min = min;
        this.max = max;
        this.abilities = Collections.unmodifiableList(new ArrayList<>(abilities));
    }

    @NotNull
    public MultiblockAbility<?> getDisplayAbility() {
        return displayAbility;
    }

    @NotNull
    public List<MultiblockAbility<?>> getAbilities() {
        return abilities;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public boolean matchesAny(@NotNull List<MultiblockAbility<?>> partAbilities) {
        for (MultiblockAbility<?> ability : abilities) {
            if (partAbilities.contains(ability)) {
                return true;
            }
        }
        return false;
    }
}
