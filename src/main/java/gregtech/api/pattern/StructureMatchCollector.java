package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Collector for V3 element-side match effects.
 *
 * <p>Direct element state lives in {@link StructureOperationState}. The
 * context-only constructor remains as a compatibility path for legacy
 * traversals that do not yet create a {@link StructureMatchSession}.
 */
public final class StructureMatchCollector {

    private static final String REQUIREMENTS_KEY = "__StructureRequirements";
    private static final String COUNTS_KEY = "__StructureCounts";

    @Nullable
    private final StructureOperationState operationState;
    @Nullable
    private final StructureContribution.Builder contributionBuilder;
    private final PatternMatchContext context;
    private final boolean collectsFormationState;

    public StructureMatchCollector(@NotNull PatternMatchContext context) {
        this(context, true);
    }

    StructureMatchCollector(@NotNull PatternMatchContext context,
                            boolean collectsFormationState) {
        this.operationState = null;
        this.contributionBuilder = null;
        this.context = context;
        this.collectsFormationState = collectsFormationState;
    }

    StructureMatchCollector(@NotNull StructureOperationState operationState,
                            @NotNull PatternMatchContext context) {
        this(operationState, null, context, true);
    }

    StructureMatchCollector(@NotNull StructureOperationState operationState,
                            @NotNull PatternMatchContext context,
                            boolean collectsFormationState) {
        this(operationState, null, context, collectsFormationState);
    }

    StructureMatchCollector(@NotNull StructureOperationState operationState,
                            @Nullable StructureContribution.Builder contributionBuilder,
                            @NotNull PatternMatchContext context,
                            boolean collectsFormationState) {
        this.operationState = operationState;
        this.contributionBuilder = contributionBuilder;
        this.context = context;
        this.collectsFormationState = collectsFormationState;
    }

    public void declareAbility(@NotNull Object key, @NotNull MultiblockAbility<?> ability, int min, int max) {
        declareAbility(key, ability, min, max, null, null);
    }

    public void declareAbility(@NotNull Object key, @NotNull MultiblockAbility<?> ability, int min, int max,
                               @Nullable Supplier<PatternError> minErrorFactory,
                               @Nullable Supplier<PatternError> maxErrorFactory) {
        declareCount(key, min, max, ability, minErrorFactory, maxErrorFactory);
    }

    public void declareCount(@NotNull Object key, int min, int max,
                             @Nullable Supplier<PatternError> minErrorFactory,
                             @Nullable Supplier<PatternError> maxErrorFactory) {
        declareCount(key, min, max, null, minErrorFactory, maxErrorFactory);
    }

    public boolean canRecordAbility(@NotNull Object key) {
        return canRecordCount(key);
    }

    public boolean canRecordCount(@NotNull Object key) {
        if (!collectsFormationState) return true;

        CountRequirement requirement = requirements().get(key);
        if (requirement == null || requirement.max < 0) {
            return true;
        }
        return getCount(key) < requirement.max;
    }

    public boolean recordAbility(@NotNull Object key, @NotNull IMultiblockPart part) {
        if (!collectsFormationState) return true;

        CountRequirement requirement = operationState == null
                ? requirements().get(key)
                : operationState.requirements.get(key);
        if (operationState != null && requirement != null && requirement.ability != null) {
            Set<IMultiblockPart> countedParts = operationState.countedAbilityParts
                    .computeIfAbsent(key, ignored -> new HashSet<>());
            if (countedParts.contains(part)) {
                addPart(part);
                return true;
            }
        }
        boolean recorded = recordCount(key);
        if (recorded && operationState != null) {
            addPart(part);
            if (requirement != null && requirement.ability != null) {
                operationState.countedAbilityParts.get(key).add(part);
                Set<IMultiblockPart> abilityParts = operationState.abilityParts
                        .computeIfAbsent(requirement.ability, ignored -> new HashSet<>());
                abilityParts.add(part);
                operationState.abilityCounts.put(requirement.ability, abilityParts.size());
                if (contributionBuilder != null) {
                    contributionBuilder.addAbility(key, requirement.ability, part);
                }
            }
        } else if (recorded) {
            addPart(part);
        }
        return recorded;
    }

    public boolean recordCount(@NotNull Object key) {
        if (!collectsFormationState) return true;

        if (operationState != null) {
            CountRequirement requirement = operationState.requirements.get(key);
            int count = operationState.counts.getOrDefault(key, 0) + 1;
            if (requirement != null && requirement.max >= 0 && count > requirement.max) {
                return false;
            }
            operationState.counts.put(key, count);
            if (contributionBuilder != null) {
                contributionBuilder.increment(key);
            }
            return true;
        }

        Map<Object, int[]> legacyCounts = legacyCounts(context);
        int[] count = legacyCounts.computeIfAbsent(key, ignored -> new int[]{0});
        CountRequirement requirement = legacyRequirements(context).get(key);
        int nextCount = count[0] + 1;
        if (requirement != null && requirement.max >= 0 && nextCount > requirement.max) {
            return false;
        }
        count[0] = nextCount;
        return true;
    }

    public void addPart(@NotNull IMultiblockPart part) {
        if (!collectsFormationState) return;

        if (operationState != null) {
            operationState.parts.add(part);
            if (contributionBuilder != null) {
                contributionBuilder.addPart(part);
            }
            return;
        }
        Set<IMultiblockPart> parts =
                context.getOrCreate(StructureOperationState.MULTIBLOCK_PARTS_KEY, HashSet::new);
        parts.add(part);
    }

    public int getAbilityCount(@NotNull Object key) {
        return getCount(key);
    }

    public int getCount(@NotNull Object key) {
        if (operationState != null) {
            return operationState.counts.getOrDefault(key, 0);
        }
        int[] count = legacyCounts(context).get(key);
        return countValue(count);
    }

    public void recordVariantActiveBlock(@NotNull BlockPos pos) {
        if (!collectsFormationState) return;

        if (operationState != null) {
            if (!operationState.variantActiveBlocks.contains(pos)) {
                operationState.variantActiveBlocks.add(pos);
            }
            if (contributionBuilder != null) {
                contributionBuilder.addVariantActiveBlock(pos);
            }
            return;
        }
        List<BlockPos> positions =
                context.getOrCreate(StructureOperationState.VARIANT_ACTIVE_BLOCKS_KEY, LinkedList::new);
        positions.add(pos);
    }

    public boolean recordChannelValue(@NotNull String channelName,
                                      @NotNull Object value,
                                      boolean requiresUniformValue) {
        if (!collectsFormationState) return true;

        StructureContributionKey<Object, Object> key = requiresUniformValue
                ? StructureContributionKey.uniform(
                        contributionId("channel", channelName),
                        (legacyContext, aggregate) -> legacyContext.set(channelName, aggregate))
                : StructureContributionKey.create(
                        contributionId("channel", channelName),
                        "first-non-null",
                        () -> null,
                        (current, emitted) -> current == null ? emitted : current,
                        ignored -> StructureContributionKey.Validation.success(),
                        (legacyContext, aggregate) -> legacyContext.set(channelName, aggregate),
                        java.util.function.UnaryOperator.identity(),
                        java.util.function.UnaryOperator.identity());
        emit(key, value);
        Object existing = context.get(channelName);
        if (existing == null) {
            context.set(channelName, value);
            return true;
        }
        return !requiresUniformValue || existing.equals(value);
    }

    public void setValue(@NotNull String key, @NotNull Object value) {
        if (!collectsFormationState) return;

        emit(StructureContributionKey.create(
                contributionId("value", key),
                "last-non-null",
                () -> null,
                (current, emitted) -> emitted == null ? current : emitted,
                ignored -> StructureContributionKey.Validation.success(),
                (legacyContext, aggregate) -> legacyContext.set(key, aggregate),
                java.util.function.UnaryOperator.identity(),
                java.util.function.UnaryOperator.identity()), value);
        context.set(key, value);
    }

    public <E, A> void emit(@NotNull StructureContributionKey<E, A> key,
                            @Nullable E value) {
        if (!collectsFormationState || contributionBuilder == null) {
            return;
        }
        contributionBuilder.emit(key, value);
    }

    @NotNull
    public Validation validate() {
        return operationState == null ? validate(context) : validate(operationState);
    }

    @NotNull
    public static Validation validate(@NotNull PatternMatchContext context) {
        return validate(legacyRequirements(context), legacyCounts(context));
    }

    @NotNull
    static Validation validate(@NotNull StructureOperationState operationState) {
        return validate(operationState.requirements, operationState.counts);
    }

    @NotNull
    private static Validation validate(
            @NotNull Map<Object, CountRequirement> requirements,
            @NotNull Map<Object, ?> counts) {
        Map<MultiblockAbility<?>, Integer> missingAbilities = new LinkedHashMap<>();
        CountRequirement firstMissing = null;
        CountRequirement firstMissingAbility = null;
        CountRequirement firstExceeded = null;
        int exceededCount = 0;

        for (Map.Entry<Object, CountRequirement> entry : requirements.entrySet()) {
            CountRequirement requirement = entry.getValue();
            int count = countValue(counts.get(entry.getKey()));
            if (count < requirement.min) {
                if (firstMissing == null) {
                    firstMissing = requirement;
                }
                if (requirement.ability != null) {
                    if (firstMissingAbility == null) {
                        firstMissingAbility = requirement;
                    }
                    missingAbilities.merge(requirement.ability, requirement.min - count, Integer::sum);
                }
            } else if (requirement.max >= 0 && count > requirement.max) {
                if (firstExceeded == null) {
                    firstExceeded = requirement;
                    exceededCount = count;
                }
            }
        }

        if (firstExceeded != null) {
            String subject = firstExceeded.ability == null
                    ? "Structure element"
                    : "Ability '" + firstExceeded.ability + "'";
            return Validation.failure(subject + " count " + exceededCount
                    + " exceeds maximum " + firstExceeded.max,
                    firstExceeded.createMaxError());
        }
        if (!missingAbilities.isEmpty()) {
            return Validation.missingAbilities(missingAbilities,
                    firstMissingAbility == null ? null : firstMissingAbility.createMinError());
        }
        if (firstMissing != null) {
            return Validation.failure(
                    "A structure element requirement did not reach its minimum count",
                    firstMissing.createMinError());
        }
        return Validation.success();
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static Map<Object, CountRequirement> legacyRequirements(@NotNull PatternMatchContext context) {
        return context.getOrCreate(REQUIREMENTS_KEY, HashMap::new);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static Map<Object, int[]> legacyCounts(@NotNull PatternMatchContext context) {
        return context.getOrCreate(COUNTS_KEY, HashMap::new);
    }

    @NotNull
    private Map<Object, CountRequirement> requirements() {
        return operationState == null
                ? legacyRequirements(context)
                : operationState.requirements;
    }

    private static int countValue(@Nullable Object count) {
        if (count instanceof Integer) {
            return (Integer) count;
        }
        if (count instanceof int[]) {
            int[] values = (int[]) count;
            return values.length == 0 ? 0 : values[0];
        }
        return 0;
    }

    private void declareCount(@NotNull Object key, int min, int max,
                              @Nullable MultiblockAbility<?> ability,
                              @Nullable Supplier<PatternError> minErrorFactory,
                              @Nullable Supplier<PatternError> maxErrorFactory) {
        if (!collectsFormationState) return;

        CountRequirement requirement =
                new CountRequirement(ability, min, max, minErrorFactory, maxErrorFactory);
        if (operationState != null) {
            operationState.requirements.putIfAbsent(key, requirement);
            if (contributionBuilder != null) {
                contributionBuilder.declare(key, requirement);
            }
        } else {
            legacyRequirements(context).putIfAbsent(key, requirement);
        }
    }

    static final class CountRequirement {

        @Nullable
        private final MultiblockAbility<?> ability;
        private final int min;
        private final int max;
        @Nullable
        private final Supplier<PatternError> minErrorFactory;
        @Nullable
        private final Supplier<PatternError> maxErrorFactory;

        CountRequirement(@Nullable MultiblockAbility<?> ability, int min, int max,
                         @Nullable Supplier<PatternError> minErrorFactory,
                         @Nullable Supplier<PatternError> maxErrorFactory) {
            this.ability = ability;
            this.min = Math.max(0, min);
            this.max = max;
            this.minErrorFactory = minErrorFactory;
            this.maxErrorFactory = maxErrorFactory;
        }

        @Nullable
        private PatternError createMinError() {
            return minErrorFactory == null ? null : minErrorFactory.get();
        }

        @Nullable
        private PatternError createMaxError() {
            return maxErrorFactory == null ? null : maxErrorFactory.get();
        }

        @Nullable
        MultiblockAbility<?> getAbility() {
            return ability;
        }

        int getMin() {
            return min;
        }

        int getMax() {
            return max;
        }

        @Nullable
        Supplier<PatternError> getMinErrorFactory() {
            return minErrorFactory;
        }

        @Nullable
        Supplier<PatternError> getMaxErrorFactory() {
            return maxErrorFactory;
        }

        boolean isCompatibleWith(@NotNull CountRequirement other) {
            return ability == other.ability && min == other.min && max == other.max;
        }
    }

    @NotNull
    private static String contributionId(@NotNull String kind, @NotNull String legacyKey) {
        return "gregtech:legacy/" + kind + "/" + legacyKey;
    }

    public static final class Validation {

        public final boolean success;
        @Nullable
        public final String errorMessage;
        @Nullable
        public final PatternError error;
        @NotNull
        public final Map<MultiblockAbility<?>, Integer> missingAbilities;

        private Validation(boolean success, @Nullable String errorMessage,
                           @Nullable PatternError error,
                           @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.error = error;
            this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
        }

        @NotNull
        private static Validation success() {
            return new Validation(true, null, null, Collections.emptyMap());
        }

        @NotNull
        private static Validation failure(@NotNull String errorMessage, @Nullable PatternError error) {
            return new Validation(false, errorMessage, error, Collections.emptyMap());
        }

        @NotNull
        private static Validation missingAbilities(
                @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                @Nullable PatternError error) {
            return new Validation(false, "Missing required multiblock abilities", error, missingAbilities);
        }
    }
}
