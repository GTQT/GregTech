package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Immutable contribution emitted by one matched structure piece.
 */
public final class StructureContribution {

    public enum RequirementScope {
        PIECE,
        STRUCTURE
    }

    public static final class Requirement {

        @Nullable
        private final MultiblockAbility<?> ability;
        private final int min;
        private final int max;
        @NotNull
        private final RequirementScope scope;
        @Nullable
        private final Supplier<PatternError> minErrorFactory;
        @Nullable
        private final Supplier<PatternError> maxErrorFactory;

        Requirement(@NotNull StructureMatchCollector.CountRequirement source,
                    @NotNull RequirementScope scope) {
            this(source.getAbility(), source.getMin(), source.getMax(), scope,
                    source.getMinErrorFactory(), source.getMaxErrorFactory());
        }

        public Requirement(@Nullable MultiblockAbility<?> ability,
                           int min,
                           int max,
                           @NotNull RequirementScope scope,
                           @Nullable Supplier<PatternError> minErrorFactory,
                           @Nullable Supplier<PatternError> maxErrorFactory) {
            this.ability = ability;
            this.min = Math.max(0, min);
            this.max = max;
            this.scope = scope;
            this.minErrorFactory = minErrorFactory;
            this.maxErrorFactory = maxErrorFactory;
        }

        @Nullable
        public MultiblockAbility<?> getAbility() {
            return ability;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        @NotNull
        public RequirementScope getScope() {
            return scope;
        }

        boolean isCompatibleWith(@NotNull Requirement other) {
            return ability == other.ability
                    && min == other.min
                    && max == other.max
                    && scope == other.scope;
        }

        @NotNull
        StructureMatchCollector.CountRequirement toCollectorRequirement() {
            return new StructureMatchCollector.CountRequirement(
                    ability, min, max, minErrorFactory, maxErrorFactory);
        }
    }

    private static final StructureContribution EMPTY = new Builder().build();

    @NotNull
    private final Map<Object, Requirement> requirements;
    @NotNull
    private final Map<Object, Integer> counts;
    @NotNull
    private final Set<IMultiblockPart> parts;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> abilityCounts;
    @NotNull
    private final Map<MultiblockAbility<?>, Set<IMultiblockPart>> abilityParts;
    @NotNull
    private final Map<Object, Set<IMultiblockPart>> countedAbilityParts;
    @NotNull
    private final List<BlockPos> variantActiveBlocks;
    @NotNull
    private final Map<StructureContributionKey<?, ?>, List<?>> typedEmissions;

    private StructureContribution(@NotNull Builder builder) {
        this.requirements = Collections.unmodifiableMap(new LinkedHashMap<>(builder.requirements));
        this.counts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.counts));
        this.parts = Collections.unmodifiableSet(new LinkedHashSet<>(builder.parts));
        this.abilityCounts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.abilityCounts));

        Map<MultiblockAbility<?>, Set<IMultiblockPart>> copiedAbilityParts = new LinkedHashMap<>();
        for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry : builder.abilityParts.entrySet()) {
            copiedAbilityParts.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        this.abilityParts = Collections.unmodifiableMap(copiedAbilityParts);
        Map<Object, Set<IMultiblockPart>> copiedCountedParts = new LinkedHashMap<>();
        for (Map.Entry<Object, Set<IMultiblockPart>> entry :
                builder.countedAbilityParts.entrySet()) {
            copiedCountedParts.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        this.countedAbilityParts = Collections.unmodifiableMap(copiedCountedParts);
        this.variantActiveBlocks =
                Collections.unmodifiableList(new ArrayList<>(builder.variantActiveBlocks));

        Map<StructureContributionKey<?, ?>, List<?>> copiedEmissions = new LinkedHashMap<>();
        for (Map.Entry<StructureContributionKey<?, ?>, List<Object>> entry :
                builder.typedEmissions.entrySet()) {
            copiedEmissions.put(
                    entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.typedEmissions = Collections.unmodifiableMap(copiedEmissions);
    }

    @NotNull
    public static StructureContribution empty() {
        return EMPTY;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public Map<Object, Requirement> getRequirements() {
        return requirements;
    }

    @NotNull
    public Map<Object, Integer> getCounts() {
        return counts;
    }

    @NotNull
    public Set<IMultiblockPart> getParts() {
        return parts;
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
        return abilityCounts;
    }

    @NotNull
    public Map<MultiblockAbility<?>, Set<IMultiblockPart>> getAbilityParts() {
        return abilityParts;
    }

    @NotNull
    public Map<Object, Set<IMultiblockPart>> getCountedAbilityParts() {
        return countedAbilityParts;
    }

    @NotNull
    public List<BlockPos> getVariantActiveBlocks() {
        return variantActiveBlocks;
    }

    @NotNull
    public Map<StructureContributionKey<?, ?>, List<?>> getTypedEmissions() {
        return typedEmissions;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public <E> List<E> getEmissions(@NotNull StructureContributionKey<E, ?> key) {
        List<?> values = typedEmissions.get(key);
        return values == null ? Collections.emptyList() : (List<E>) values;
    }

    public boolean isEmpty() {
        return requirements.isEmpty()
                && counts.isEmpty()
                && parts.isEmpty()
                && abilityCounts.isEmpty()
                && variantActiveBlocks.isEmpty()
                && typedEmissions.isEmpty();
    }

    public static final class Builder {

        private final Map<Object, Requirement> requirements = new LinkedHashMap<>();
        private final Map<Object, Integer> counts = new LinkedHashMap<>();
        private final Set<IMultiblockPart> parts = new LinkedHashSet<>();
        private final Map<MultiblockAbility<?>, Integer> abilityCounts = new LinkedHashMap<>();
        private final Map<MultiblockAbility<?>, Set<IMultiblockPart>> abilityParts = new LinkedHashMap<>();
        private final Map<Object, Set<IMultiblockPart>> countedAbilityParts = new LinkedHashMap<>();
        private final List<BlockPos> variantActiveBlocks = new ArrayList<>();
        private final Map<StructureContributionKey<?, ?>, List<Object>> typedEmissions =
                new LinkedHashMap<>();

        public Builder() {}

        private Builder(@NotNull Builder source) {
            requirements.putAll(source.requirements);
            counts.putAll(source.counts);
            parts.addAll(source.parts);
            abilityCounts.putAll(source.abilityCounts);
            for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry :
                    source.abilityParts.entrySet()) {
                abilityParts.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            for (Map.Entry<Object, Set<IMultiblockPart>> entry :
                    source.countedAbilityParts.entrySet()) {
                countedAbilityParts.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            variantActiveBlocks.addAll(source.variantActiveBlocks);
            for (Map.Entry<StructureContributionKey<?, ?>, List<Object>> entry :
                    source.typedEmissions.entrySet()) {
                typedEmissions.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        @NotNull
        Builder copy() {
            return new Builder(this);
        }

        void replaceWith(@NotNull Builder source) {
            requirements.clear();
            counts.clear();
            parts.clear();
            abilityCounts.clear();
            abilityParts.clear();
            countedAbilityParts.clear();
            variantActiveBlocks.clear();
            typedEmissions.clear();

            Builder copy = new Builder(source);
            requirements.putAll(copy.requirements);
            counts.putAll(copy.counts);
            parts.addAll(copy.parts);
            abilityCounts.putAll(copy.abilityCounts);
            abilityParts.putAll(copy.abilityParts);
            countedAbilityParts.putAll(copy.countedAbilityParts);
            variantActiveBlocks.addAll(copy.variantActiveBlocks);
            typedEmissions.putAll(copy.typedEmissions);
        }

        void declare(@NotNull Object key,
                     @NotNull StructureMatchCollector.CountRequirement requirement) {
            requirements.putIfAbsent(
                    key, new Requirement(requirement, RequirementScope.STRUCTURE));
        }

        public void declare(@NotNull Object key,
                            @NotNull Requirement requirement) {
            Requirement existing = requirements.putIfAbsent(key, requirement);
            if (existing != null && !existing.isCompatibleWith(requirement)) {
                throw new IllegalArgumentException(
                        "Conflicting requirement declaration for key " + key);
            }
        }

        public void increment(@NotNull Object key) {
            counts.merge(key, 1, Integer::sum);
        }

        public void addPart(@NotNull IMultiblockPart part) {
            parts.add(part);
        }

        public void addAbility(@NotNull Object key,
                               @NotNull MultiblockAbility<?> ability,
                               @NotNull IMultiblockPart part) {
            boolean newForRequirement = countedAbilityParts
                    .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(part);
            if (newForRequirement) {
                abilityParts.computeIfAbsent(ability, ignored -> new LinkedHashSet<>()).add(part);
                abilityCounts.put(ability, abilityParts.get(ability).size());
            }
        }

        public void addVariantActiveBlock(@NotNull BlockPos pos) {
            if (!variantActiveBlocks.contains(pos)) {
                variantActiveBlocks.add(pos);
            }
        }

        public <E, A> void emit(@NotNull StructureContributionKey<E, A> key,
                                @Nullable E value) {
            typedEmissions.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(key.copyEmission(value));
        }

        @NotNull
        public StructureContribution build() {
            return new StructureContribution(this);
        }
    }
}
