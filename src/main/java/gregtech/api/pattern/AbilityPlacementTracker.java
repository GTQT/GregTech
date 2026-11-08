package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks globally limited multiblock abilities while assembling a multi-piece
 * preview or auto-building several pieces and repeat slices.
 */
public final class AbilityPlacementTracker {

    /**
     * Prefix used to carry per-request ability count caps through the existing
     * channel-value map. The keys are deliberately outside the structure channel
     * namespace so they cannot collide with a real {@code GTStructureChannel}.
     */
    public static final String ABILITY_COUNT_PREFIX = "@ability_count:";

    private final Map<MultiblockAbility<?>, int[]> limits;
    private final List<AbilityGroupLimit> groupLimits;
    private final Map<MultiblockAbility<?>, Integer> abilityMaxOverrides = new HashMap<>();
    private final Map<AbilityGroupLimit, Integer> groupMaxOverrides = new HashMap<>();
    private final Map<MultiblockAbility<?>, Integer> counts = new HashMap<>();
    private final Map<AbilityGroupLimit, Integer> groupCounts = new HashMap<>();
    private final LongSet recordedWorldPositions = new LongOpenHashSet();

    @NotNull
    public static String channelKey(@NotNull MultiblockAbility<?> ability) {
        return ABILITY_COUNT_PREFIX + ability;
    }

    AbilityPlacementTracker(@NotNull Map<MultiblockAbility<?>, int[]> limits,
                            @NotNull List<AbilityGroupLimit> groupLimits) {
        this(limits, groupLimits, Collections.emptyMap());
    }

    AbilityPlacementTracker(@NotNull Map<MultiblockAbility<?>, int[]> limits,
                            @NotNull List<AbilityGroupLimit> groupLimits,
                            @NotNull Map<String, Integer> config) {
        Map<MultiblockAbility<?>, int[]> copied = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : limits.entrySet()) {
            copied.put(entry.getKey(), entry.getValue().clone());
        }
        this.limits = Collections.unmodifiableMap(copied);
        this.groupLimits = Collections.unmodifiableList(new ArrayList<>(groupLimits));

        for (Map.Entry<MultiblockAbility<?>, int[]> entry : this.limits.entrySet()) {
            Integer override = config.get(channelKey(entry.getKey()));
            if (override != null) {
                abilityMaxOverrides.put(entry.getKey(), clampMax(entry.getValue(), override));
            }
        }
        for (AbilityGroupLimit groupLimit : this.groupLimits) {
            Integer override = config.get(channelKey(groupLimit.getDisplayAbility()));
            if (override != null) {
                groupMaxOverrides.put(groupLimit, clampMax(groupLimit.getMin(), groupLimit.getMax(), override));
            }
        }
    }

    private static int clampMax(@NotNull int[] range, int requested) {
        return clampMax(range[0], range[1], requested);
    }

    private static int clampMax(int min, int max, int requested) {
        if (max >= 0 && requested > max) {
            requested = max;
        }
        return Math.max(min, requested);
    }

    private int effectiveMax(@NotNull MultiblockAbility<?> ability, int[] range) {
        Integer override = abilityMaxOverrides.get(ability);
        return override == null ? range[1] : override;
    }

    private int effectiveMax(@NotNull AbilityGroupLimit groupLimit) {
        Integer override = groupMaxOverrides.get(groupLimit);
        return override == null ? groupLimit.getMax() : override;
    }

    public boolean canPlace(@NotNull BlockInfo info) {
        List<MultiblockAbility<?>> abilities = getAbilities(info.getTileEntity());
        for (MultiblockAbility<?> ability : abilities) {
            int[] range = limits.get(ability);
            if (range != null && effectiveMax(ability, range) >= 0
                    && counts.getOrDefault(ability, 0) >= effectiveMax(ability, range)) {
                return false;
            }
        }
        for (AbilityGroupLimit groupLimit : groupLimits) {
            int max = effectiveMax(groupLimit);
            if (max >= 0 && groupLimit.matchesAny(abilities)
                    && groupCounts.getOrDefault(groupLimit, 0) >= max) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return an immutable snapshot of the per-ability placement counts.
     */
    @NotNull
    public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
        return Collections.unmodifiableMap(new HashMap<>(counts));
    }

    /**
     * @return an immutable snapshot of the per-group placement counts.
     */
    @NotNull
    public Map<AbilityGroupLimit, Integer> getGroupCounts() {
        return Collections.unmodifiableMap(new HashMap<>(groupCounts));
    }

    /**
     * Returns the declared ability ranges with the per-request caps applied. Used by
     * {@link StructureMatchSession}, which counts placed parts rather than placing them.
     */
    @NotNull
    public Map<MultiblockAbility<?>, int[]> getEffectiveAbilityLimits() {
        Map<MultiblockAbility<?>, int[]> effective = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : limits.entrySet()) {
            int[] range = entry.getValue();
            effective.put(entry.getKey(), new int[] { range[0], effectiveMax(entry.getKey(), range) });
        }
        return Collections.unmodifiableMap(effective);
    }

    /**
     * Returns the declared group limits with the per-request caps applied.
     */
    @NotNull
    public List<AbilityGroupLimit> getEffectiveGroupLimits() {
        List<AbilityGroupLimit> effective = new ArrayList<>(groupLimits.size());
        for (AbilityGroupLimit groupLimit : groupLimits) {
            effective.add(new AbilityGroupLimit(groupLimit.getDisplayAbility(), groupLimit.getMin(),
                    effectiveMax(groupLimit), groupLimit.getAbilities()));
        }
        return Collections.unmodifiableList(effective);
    }

    public boolean isStillRequired(@NotNull BlockInfo info) {
        List<MultiblockAbility<?>> abilities = getAbilities(info.getTileEntity());
        for (MultiblockAbility<?> ability : abilities) {
            int[] range = limits.get(ability);
            if (range != null && counts.getOrDefault(ability, 0) < range[0]) {
                return true;
            }
        }
        for (AbilityGroupLimit groupLimit : groupLimits) {
            if (groupLimit.matchesAny(abilities)
                    && groupCounts.getOrDefault(groupLimit, 0) < groupLimit.getMin()) {
                return true;
            }
        }
        return false;
    }

    public void record(@NotNull BlockInfo info) {
        recordAbilities(getAbilities(info.getTileEntity()));
    }

    public void recordWorldTile(@NotNull BlockPos pos, TileEntity tileEntity) {
        if (tileEntity == null || !recordedWorldPositions.add(pos.toLong())) return;
        recordAbilities(getAbilities(tileEntity));
    }

    private void recordAbilities(@NotNull List<MultiblockAbility<?>> abilities) {
        for (MultiblockAbility<?> ability : abilities) {
            if (limits.containsKey(ability)) {
                counts.merge(ability, 1, Integer::sum);
            }
        }
        for (AbilityGroupLimit groupLimit : groupLimits) {
            if (groupLimit.matchesAny(abilities)) {
                groupCounts.merge(groupLimit, 1, Integer::sum);
            }
        }
    }

    @NotNull
    private static List<MultiblockAbility<?>> getAbilities(TileEntity tileEntity) {
        if (!(tileEntity instanceof IGregTechTileEntity gregTechTile)) {
            return Collections.emptyList();
        }
        MetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
        if (!(metaTileEntity instanceof IMultiblockAbilityPart<?> abilityPart)) {
            return Collections.emptyList();
        }
        return abilityPart.getAbilities();
    }
}
