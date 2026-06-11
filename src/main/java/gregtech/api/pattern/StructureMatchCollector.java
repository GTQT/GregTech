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
 * Transitional collector for V3 element-side match effects.
 *
 * <p>The backing data intentionally lives in {@link PatternMatchContext} so
 * existing session checkpoint/restore logic stays transactional while elements
 * move away from executing {@link TraceabilityPredicate} directly.
 */
public final class StructureMatchCollector {

    private static final String MULTIBLOCK_PARTS_KEY = "MultiblockParts";
    private static final String REQUIREMENTS_KEY = "__StructureRequirements";
    private static final String COUNTS_KEY = "__StructureCounts";
    private static final String VARIANT_ACTIVE_BLOCKS_KEY = "VABlock";

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
        CountRequirement requirement = requirements(context).get(key);
        if (requirement == null || requirement.max < 0) {
            return true;
        }
        return getCount(key) < requirement.max;
    }

    public boolean recordAbility(@NotNull Object key, @NotNull IMultiblockPart part) {
        addPart(part);
        return recordCount(key);
    }

    public boolean recordCount(@NotNull Object key) {
        Map<Object, int[]> counts = counts(context);
        int[] count = counts.get(key);
        if (count == null) {
            count = new int[]{0};
            counts.put(key, count);
        }
        count[0]++;
        CountRequirement requirement = requirements(context).get(key);
        return requirement == null || requirement.max < 0 || count[0] <= requirement.max;
    }

    public void addPart(@NotNull IMultiblockPart part) {
        Set<IMultiblockPart> parts = context.getOrCreate(MULTIBLOCK_PARTS_KEY, HashSet::new);
        parts.add(part);
    }

    public int getAbilityCount(@NotNull Object key) {
        return getCount(key);
    }

    public int getCount(@NotNull Object key) {
        int[] count = counts(context).get(key);
        return count == null ? 0 : count[0];
    }

    public void recordVariantActiveBlock(@NotNull BlockPos pos) {
        List<BlockPos> positions = context.getOrCreate(VARIANT_ACTIVE_BLOCKS_KEY, LinkedList::new);
        positions.add(pos);
    }

    public boolean recordChannelValue(@NotNull String channelName,
                                      @NotNull Object value,
                                      boolean requiresUniformValue) {
        Object existing = context.get(channelName);
        if (existing == null) {
            context.set(channelName, value);
            return true;
        }
        return !requiresUniformValue || existing.equals(value);
    }

    public void setValue(@NotNull String key, @NotNull Object value) {
        context.set(key, value);
    }

    @NotNull
    public Validation validate() {
        return validate(context);
    }

    @NotNull
    public static Validation validate(@NotNull PatternMatchContext context) {
        Map<MultiblockAbility<?>, Integer> missingAbilities = new LinkedHashMap<>();
        CountRequirement firstMissing = null;
        CountRequirement firstMissingAbility = null;
        CountRequirement firstExceeded = null;
        int exceededCount = 0;

        Map<Object, CountRequirement> requirements = requirements(context);
        Map<Object, int[]> counts = counts(context);
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
    private static Map<Object, CountRequirement> requirements(@NotNull PatternMatchContext context) {
        return context.getOrCreate(REQUIREMENTS_KEY, HashMap::new);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static Map<Object, int[]> counts(@NotNull PatternMatchContext context) {
        return context.getOrCreate(COUNTS_KEY, HashMap::new);
    }

    private static int countValue(@Nullable int[] count) {
        return count == null || count.length == 0 ? 0 : count[0];
    }

    private void declareCount(@NotNull Object key, int min, int max,
                              @Nullable MultiblockAbility<?> ability,
                              @Nullable Supplier<PatternError> minErrorFactory,
                              @Nullable Supplier<PatternError> maxErrorFactory) {
        requirements(context).putIfAbsent(
                key, new CountRequirement(ability, min, max, minErrorFactory, maxErrorFactory));
    }

    private static final class CountRequirement {

        @Nullable
        private final MultiblockAbility<?> ability;
        private final int min;
        private final int max;
        @Nullable
        private final Supplier<PatternError> minErrorFactory;
        @Nullable
        private final Supplier<PatternError> maxErrorFactory;

        private CountRequirement(@Nullable MultiblockAbility<?> ability, int min, int max,
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
