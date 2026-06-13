package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Transactional state shared by every piece and repeat slice participating in
 * one structure match.
 */
public final class StructureMatchSession {

    @Nullable
    private final StructureMatchSession parent;
    private final Map<MultiblockAbility<?>, int[]> abilityLimits;
    private final List<AbilityGroupLimit> abilityGroupLimits;
    private final PatternMatchContext context;
    private final StructureOperationState operationState;
    private final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
    private final Map<StructureSessionKey<?>, Object> typedData;
    @Nullable
    private Object controllerContext;

    public StructureMatchSession() {
        this(Collections.emptyMap(), Collections.emptyList(), null);
    }

    public StructureMatchSession(@NotNull Map<MultiblockAbility<?>, int[]> abilityLimits,
                                 @NotNull List<AbilityGroupLimit> abilityGroupLimits,
                                 @Nullable PatternMatchContext initialContext) {
        this.parent = null;
        this.abilityLimits = copyLimits(abilityLimits);
        this.abilityGroupLimits = Collections.unmodifiableList(new ArrayList<>(abilityGroupLimits));
        this.context = initialContext == null ? new PatternMatchContext() : initialContext.copy();
        this.operationState = new StructureOperationState();
        this.globalCount = new HashMap<>();
        this.typedData = new HashMap<>();
        this.controllerContext = null;
    }

    private StructureMatchSession(@NotNull StructureMatchSession parent) {
        this.parent = parent;
        this.abilityLimits = parent.abilityLimits;
        this.abilityGroupLimits = parent.abilityGroupLimits;
        this.context = parent.context.copy();
        this.operationState = parent.operationState.copy();
        this.globalCount = new HashMap<>(parent.globalCount);
        this.typedData = copyTypedData(parent.typedData);
        this.controllerContext = parent.controllerContext;
    }

    @NotNull
    public StructureMatchSession fork() {
        return new StructureMatchSession(this);
    }

    public boolean tryFork(@NotNull Predicate<StructureMatchSession> action) {
        StructureMatchSession candidate = fork();
        boolean matched = action.test(candidate);
        if (matched) {
            candidate.commit();
        }
        return matched;
    }

    public boolean transaction(@NotNull Supplier<Boolean> action) {
        return transactionValue(ignored -> action.get(), Boolean.TRUE::equals);
    }

    public boolean transaction(@NotNull Predicate<StructureMatchSession> action) {
        return transactionValue(action::test, Boolean.TRUE::equals);
    }

    public void transactionAction(@NotNull Consumer<StructureMatchSession> action) {
        transactionValue(session -> {
            action.accept(session);
            return Boolean.TRUE;
        }, Boolean.TRUE::equals);
    }

    public <T> T transactionValue(@NotNull Function<StructureMatchSession, T> action,
                                  @NotNull Predicate<T> commitPredicate) {
        Checkpoint checkpoint = checkpoint();
        try {
            T result = action.apply(this);
            if (!commitPredicate.test(result)) {
                restoreTo(checkpoint);
            }
            return result;
        } catch (RuntimeException | Error e) {
            restoreTo(checkpoint);
            throw e;
        }
    }

    public boolean probe(@NotNull Supplier<Boolean> action) {
        return probeValue(ignored -> action.get());
    }

    public boolean probe(@NotNull Predicate<StructureMatchSession> action) {
        return probeValue(action::test);
    }

    public void probeAction(@NotNull Consumer<StructureMatchSession> action) {
        probeValue(session -> {
            action.accept(session);
            return null;
        });
    }

    public <T> T probeValue(@NotNull Function<StructureMatchSession, T> action) {
        Checkpoint checkpoint = checkpoint();
        try {
            return action.apply(this);
        } finally {
            restoreTo(checkpoint);
        }
    }

    public void commit() {
        if (parent == null) {
            throw new IllegalStateException("Root structure match session cannot be committed");
        }
        parent.context.replaceWith(context);
        parent.operationState.replaceWith(operationState);
        parent.globalCount.clear();
        parent.globalCount.putAll(globalCount);
        parent.typedData.clear();
        parent.typedData.putAll(copyTypedData(typedData));
    }

    @NotNull
    public Checkpoint checkpoint() {
        return new Checkpoint(
                context.copy(), operationState.copy(),
                new HashMap<>(globalCount), copyTypedData(typedData));
    }

    private void restore(@NotNull Checkpoint checkpoint) {
        context.replaceWith(checkpoint.context);
        operationState.replaceWith(checkpoint.operationState);
        globalCount.clear();
        globalCount.putAll(checkpoint.globalCount);
        typedData.clear();
        typedData.putAll(copyTypedData(checkpoint.typedData));
    }

    public void restoreTo(@NotNull Checkpoint checkpoint) {
        restore(checkpoint);
    }

    @NotNull
    public PatternMatchContext getContext() {
        return context;
    }

    @NotNull
    StructureOperationState getOperationState() {
        return operationState;
    }

    /**
     * Snapshot collector-owned state and merge any compatibility data produced
     * by legacy predicates during the same operation.
     */
    @NotNull
    public StructureOperationState copyOperationState() {
        return operationState.copyIncludingLegacy(context);
    }

    @NotNull
    Map<TraceabilityPredicate.SimplePredicate, Integer> getGlobalCount() {
        return globalCount;
    }

    @Nullable
    public <T> T get(@NotNull StructureSessionKey<T> key) {
        return (T) typedData.get(key);
    }

    @NotNull
    public <T> T getOrCreate(@NotNull StructureSessionKey<T> key,
                             @NotNull Supplier<? extends T> factory) {
        T value = get(key);
        if (value == null) {
            value = factory.get();
            set(key, value);
        }
        return value;
    }

    public <T> void set(@NotNull StructureSessionKey<T> key, @NotNull T value) {
        typedData.put(key, value);
    }

    public void remove(@NotNull StructureSessionKey<?> key) {
        typedData.remove(key);
    }

    public <T> void setControllerContext(@Nullable T controllerContext) {
        this.controllerContext = controllerContext;
    }

    @Nullable
    public <T> T getControllerContext(@NotNull Class<T> controllerType) {
        return controllerType.isInstance(controllerContext)
                ? controllerType.cast(controllerContext)
                : null;
    }

    @Nullable
    Object getControllerContext() {
        return controllerContext;
    }

    /**
     * Validate constraints that are intentionally deferred until every piece
     * and repeat slice has committed to the session.
     */
    @NotNull
    public Validation validate(boolean includeAbilityLimits) {
        Map<MultiblockAbility<?>, Integer> missingAbilities = new LinkedHashMap<>();
        TraceabilityPredicate.SimplePredicate firstMissingPredicate = null;

        for (Map.Entry<TraceabilityPredicate.SimplePredicate, Integer> entry : globalCount.entrySet()) {
            TraceabilityPredicate.SimplePredicate predicate = entry.getKey();
            int deficit = predicate.minGlobalCount - entry.getValue();
            if (deficit <= 0) continue;
            if (firstMissingPredicate == null) {
                firstMissingPredicate = predicate;
            }
            if (predicate.ability != null) {
                missingAbilities.merge(predicate.ability, deficit, Integer::sum);
            }
        }

        String collectorFailure = mergeCollectorValidation(
                StructureMatchCollector.validate(operationState), missingAbilities);
        if (collectorFailure == null) {
            collectorFailure = mergeCollectorValidation(
                    StructureMatchCollector.validate(context), missingAbilities);
        }
        if (collectorFailure != null) {
            return Validation.failure(collectorFailure);
        }

        if (includeAbilityLimits) {
            Set<IMultiblockPart> parts = copyOperationState().getParts();
            Map<MultiblockAbility<?>, Integer> explicitAbilityCounts = operationState.getAbilityCounts();
            Map<MultiblockAbility<?>, Integer> collectedAbilities = ConfigHolder.machines.debugStructureCheck
                    ? new LinkedHashMap<>() : null;
            for (Map.Entry<MultiblockAbility<?>, int[]> entry : abilityLimits.entrySet()) {
                int explicitCount = explicitAbilityCounts.getOrDefault(entry.getKey(), 0);
                int count = explicitCount + countAbilityParts(
                        parts, entry.getKey(), operationState.getExplicitAbilityParts(entry.getKey()));
                if (collectedAbilities != null) {
                    collectedAbilities.put(entry.getKey(), count);
                }
                int[] range = entry.getValue();
                if (count < range[0]) {
                    missingAbilities.merge(entry.getKey(), range[0] - count, Math::max);
                } else if (range[1] >= 0 && count > range[1]) {
                    debugAbilityValidation(parts.size(), collectedAbilities, missingAbilities);
                    return Validation.failure("Ability '" + entry.getKey() + "' count " + count
                            + " is outside [" + range[0] + ", " + range[1] + "]");
                }
            }
            for (AbilityGroupLimit groupLimit : abilityGroupLimits) {
                int count = countAbilityGroup(parts, operationState, groupLimit);
                if (count < groupLimit.getMin()) {
                    missingAbilities.merge(
                            groupLimit.getDisplayAbility(), groupLimit.getMin() - count, Math::max);
                } else if (groupLimit.getMax() >= 0 && count > groupLimit.getMax()) {
                    debugAbilityValidation(parts.size(), collectedAbilities, missingAbilities);
                    return Validation.failure("Ability group '" + groupLimit.getDisplayAbility() + "' count "
                            + count + " is outside [" + groupLimit.getMin() + ", " + groupLimit.getMax() + "]");
                }
            }
            debugAbilityValidation(parts.size(), collectedAbilities, missingAbilities);
        }

        if (!missingAbilities.isEmpty()) {
            return Validation.missingAbilities(missingAbilities);
        }
        if (firstMissingPredicate != null) {
            return Validation.failure("A global structure predicate did not reach its minimum count");
        }
        return Validation.success();
    }

    @Nullable
    private static String mergeCollectorValidation(
            @NotNull StructureMatchCollector.Validation validation,
            @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (validation.success) {
            return null;
        }
        if (!validation.missingAbilities.isEmpty()) {
            validation.missingAbilities.forEach(
                    (ability, deficit) -> missingAbilities.merge(ability, deficit, Integer::sum));
            return null;
        }
        return validation.errorMessage == null
                ? "A structure element requirement failed"
                : validation.errorMessage;
    }

    @NotNull
    private static Map<MultiblockAbility<?>, int[]> copyLimits(
            @NotNull Map<MultiblockAbility<?>, int[]> limits) {
        Map<MultiblockAbility<?>, int[]> copied = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : limits.entrySet()) {
            copied.put(entry.getKey(), entry.getValue().clone());
        }
        return Collections.unmodifiableMap(copied);
    }

    @NotNull
    private static Map<StructureSessionKey<?>, Object> copyTypedData(
            @NotNull Map<StructureSessionKey<?>, Object> source) {
        Map<StructureSessionKey<?>, Object> copied = new HashMap<>();
        for (Map.Entry<StructureSessionKey<?>, Object> entry : source.entrySet()) {
            copied.put(entry.getKey(), copyTypedValue(entry.getKey(), entry.getValue()));
        }
        return copied;
    }

    private static void debugAbilityValidation(int partCount,
                                               @Nullable Map<MultiblockAbility<?>, Integer> collectedAbilities,
                                               @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (!ConfigHolder.machines.debugStructureCheck || collectedAbilities == null) {
            return;
        }
        GTLog.logger.debug("[StructureDefinition] ability validation parts={} collected={} missing={}",
                partCount, collectedAbilities, missingAbilities);
    }

    private static int countAbilityParts(@NotNull Set<IMultiblockPart> parts,
                                         @NotNull MultiblockAbility<?> ability,
                                         @NotNull Set<IMultiblockPart> ignoredParts) {
        int count = 0;
        for (IMultiblockPart part : parts) {
            if (ignoredParts.contains(part)) {
                continue;
            }
            if (part instanceof IMultiblockAbilityPart<?> abilityPart
                    && abilityPart.getAbilities().contains(ability)) {
                count++;
            }
        }
        return count;
    }

    private static int countAbilityGroup(
            @NotNull Set<IMultiblockPart> parts,
            @NotNull StructureOperationState operationState,
            @NotNull AbilityGroupLimit groupLimit) {
        Map<MultiblockAbility<?>, Integer> explicitAbilityCounts = operationState.getAbilityCounts();
        Set<IMultiblockPart> explicitParts = new HashSet<>();
        boolean hasExplicitCount = false;
        int count = 0;
        for (MultiblockAbility<?> ability : groupLimit.getAbilities()) {
            Integer abilityCount = explicitAbilityCounts.get(ability);
            if (abilityCount != null) {
                hasExplicitCount = true;
                count += abilityCount;
                explicitParts.addAll(operationState.getExplicitAbilityParts(ability));
            }
        }

        for (IMultiblockPart part : parts) {
            if (!(part instanceof IMultiblockAbilityPart<?> abilityPart)) {
                continue;
            }
            if (hasExplicitCount && explicitParts.contains(part)) {
                continue;
            }
            if (groupLimit.matchesAny(abilityPart.getAbilities())) {
                count++;
            }
        }
        return count;
    }

    private static <T> T copyTypedValue(@NotNull StructureSessionKey<T> key,
                                        @NotNull Object value) {
        return key.copy((T) value);
    }

    public static final class Checkpoint {

        private final PatternMatchContext context;
        private final StructureOperationState operationState;
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
        private final Map<StructureSessionKey<?>, Object> typedData;

        private Checkpoint(@NotNull PatternMatchContext context,
                           @NotNull StructureOperationState operationState,
                           @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                           @NotNull Map<StructureSessionKey<?>, Object> typedData) {
            this.context = context;
            this.operationState = operationState;
            this.globalCount = globalCount;
            this.typedData = typedData;
        }
    }

    public static final class Validation {

        public final boolean success;
        @Nullable
        public final String errorMessage;
        @NotNull
        public final Map<MultiblockAbility<?>, Integer> missingAbilities;

        private Validation(boolean success, @Nullable String errorMessage,
                           @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
        }

        @NotNull
        private static Validation success() {
            return new Validation(true, null, Collections.emptyMap());
        }

        @NotNull
        private static Validation failure(@NotNull String errorMessage) {
            return new Validation(false, errorMessage, Collections.emptyMap());
        }

        @NotNull
        private static Validation missingAbilities(
                @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
            return new Validation(false, "Missing required multiblock abilities", missingAbilities);
        }
    }
}
