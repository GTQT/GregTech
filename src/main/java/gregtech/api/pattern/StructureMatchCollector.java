package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Transitional collector for V3 element-side match effects.
 *
 * <p>The backing data intentionally lives in {@link PatternMatchContext} so
 * existing session checkpoint/restore logic stays transactional while elements
 * move away from executing {@link TraceabilityPredicate} directly.
 */
public final class StructureMatchCollector {

    private static final String MULTIBLOCK_PARTS_KEY = "MultiblockParts";
    private static final String ABILITY_REQUIREMENTS_KEY = "__StructureAbilityRequirements";
    private static final String ABILITY_COUNTS_KEY = "__StructureAbilityCounts";

    private final PatternMatchContext context;

    public StructureMatchCollector(@NotNull PatternMatchContext context) {
        this.context = context;
    }

    public void declareAbility(@NotNull Object key, @NotNull MultiblockAbility<?> ability, int min, int max) {
        declareAbility(key, ability, min, max, null, null);
    }

    public void declareAbility(@NotNull Object key, @NotNull MultiblockAbility<?> ability, int min, int max,
                               @Nullable Supplier<PatternError> minErrorFactory,
                               @Nullable Supplier<PatternError> maxErrorFactory) {
        requirements(context).putIfAbsent(
                key, new AbilityRequirement(ability, min, max, minErrorFactory, maxErrorFactory));
    }

    public boolean canRecordAbility(@NotNull Object key) {
        AbilityRequirement requirement = requirements(context).get(key);
        if (requirement == null || requirement.max < 0) {
            return true;
        }
        return getAbilityCount(key) < requirement.max;
    }

    public boolean recordAbility(@NotNull Object key, @NotNull IMultiblockPart part) {
        addPart(part);
        Map<Object, int[]> counts = counts(context);
        int[] count = counts.get(key);
        if (count == null) {
            count = new int[]{0};
            counts.put(key, count);
        }
        count[0]++;
        AbilityRequirement requirement = requirements(context).get(key);
        return requirement == null || requirement.max < 0 || count[0] <= requirement.max;
    }

    public void addPart(@NotNull IMultiblockPart part) {
        Set<IMultiblockPart> parts = context.getOrCreate(MULTIBLOCK_PARTS_KEY, HashSet::new);
        parts.add(part);
    }

    public int getAbilityCount(@NotNull Object key) {
        int[] count = counts(context).get(key);
        return count == null ? 0 : count[0];
    }

    @NotNull
    public Validation validate() {
        return validate(context);
    }

    @NotNull
    public static Validation validate(@NotNull PatternMatchContext context) {
        Map<MultiblockAbility<?>, Integer> missingAbilities = new LinkedHashMap<>();
        AbilityRequirement firstMissing = null;
        AbilityRequirement firstExceeded = null;
        int exceededCount = 0;

        Map<Object, AbilityRequirement> requirements = requirements(context);
        Map<Object, int[]> counts = counts(context);
        for (Map.Entry<Object, AbilityRequirement> entry : requirements.entrySet()) {
            AbilityRequirement requirement = entry.getValue();
            int count = countValue(counts.get(entry.getKey()));
            if (count < requirement.min) {
                if (firstMissing == null) {
                    firstMissing = requirement;
                }
                missingAbilities.merge(requirement.ability, requirement.min - count, Integer::sum);
            } else if (requirement.max >= 0 && count > requirement.max) {
                if (firstExceeded == null) {
                    firstExceeded = requirement;
                    exceededCount = count;
                }
            }
        }

        if (firstExceeded != null) {
            return Validation.failure("Ability '" + firstExceeded.ability + "' count " + exceededCount
                    + " exceeds maximum " + firstExceeded.max, firstExceeded.createMaxError());
        }
        if (!missingAbilities.isEmpty()) {
            return Validation.missingAbilities(missingAbilities,
                    firstMissing == null ? null : firstMissing.createMinError());
        }
        if (firstMissing != null) {
            return Validation.failure(
                    "A structure ability requirement did not reach its minimum count",
                    firstMissing.createMinError());
        }
        return Validation.success();
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static Map<Object, AbilityRequirement> requirements(@NotNull PatternMatchContext context) {
        return context.getOrCreate(ABILITY_REQUIREMENTS_KEY, HashMap::new);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static Map<Object, int[]> counts(@NotNull PatternMatchContext context) {
        return context.getOrCreate(ABILITY_COUNTS_KEY, HashMap::new);
    }

    private static int countValue(@Nullable int[] count) {
        return count == null || count.length == 0 ? 0 : count[0];
    }

    private static final class AbilityRequirement {

        @NotNull
        private final MultiblockAbility<?> ability;
        private final int min;
        private final int max;
        @Nullable
        private final Supplier<PatternError> minErrorFactory;
        @Nullable
        private final Supplier<PatternError> maxErrorFactory;

        private AbilityRequirement(@NotNull MultiblockAbility<?> ability, int min, int max,
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
