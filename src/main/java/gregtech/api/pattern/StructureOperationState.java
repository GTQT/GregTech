package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed, transactional state collected by direct structure elements.
 */
public final class StructureOperationState {

    final Map<Object, StructureMatchCollector.CountRequirement> requirements = new HashMap<>();
    final Map<Object, Integer> counts = new HashMap<>();
    final Map<MultiblockAbility<?>, Integer> abilityCounts = new HashMap<>();
    final Map<MultiblockAbility<?>, Set<IMultiblockPart>> abilityParts = new HashMap<>();
    final Map<Object, Set<IMultiblockPart>> countedAbilityParts = new HashMap<>();
    final Set<IMultiblockPart> parts = new HashSet<>();
    final List<BlockPos> variantActiveBlocks = new ArrayList<>();

    public StructureOperationState() {}

    private StructureOperationState(@NotNull StructureOperationState source) {
        replaceWith(source);
    }

    @NotNull
    public StructureOperationState copy() {
        return new StructureOperationState(this);
    }

    void replaceWith(@NotNull StructureOperationState source) {
        requirements.clear();
        requirements.putAll(source.requirements);
        counts.clear();
        counts.putAll(source.counts);
        abilityCounts.clear();
        abilityCounts.putAll(source.abilityCounts);
        abilityParts.clear();
        for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry : source.abilityParts.entrySet()) {
            abilityParts.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        countedAbilityParts.clear();
        for (Map.Entry<Object, Set<IMultiblockPart>> entry : source.countedAbilityParts.entrySet()) {
            countedAbilityParts.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        parts.clear();
        parts.addAll(source.parts);
        variantActiveBlocks.clear();
        variantActiveBlocks.addAll(source.variantActiveBlocks);
    }

    @NotNull
    public Set<IMultiblockPart> getParts() {
        return Collections.unmodifiableSet(parts);
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
        return Collections.unmodifiableMap(abilityCounts);
    }

    @NotNull
    Set<IMultiblockPart> getExplicitAbilityParts(@NotNull MultiblockAbility<?> ability) {
        Set<IMultiblockPart> explicitParts = abilityParts.get(ability);
        return explicitParts == null ? Collections.emptySet() : Collections.unmodifiableSet(explicitParts);
    }

    @NotNull
    public List<BlockPos> getVariantActiveBlocks() {
        return Collections.unmodifiableList(variantActiveBlocks);
    }
}
