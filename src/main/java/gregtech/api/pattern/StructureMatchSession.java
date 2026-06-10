package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Transactional state shared by every piece and repeat slice participating in
 * one structure match.
 */
public final class StructureMatchSession {

    @Nullable
    private final StructureMatchSession parent;
    private final Map<MultiblockAbility<?>, int[]> abilityLimits;
    private final PatternMatchContext context;
    private final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
    private final Map<StructureSessionKey<?>, Object> typedData;
    @Nullable
    private Object controllerContext;

    public StructureMatchSession() {
        this(Collections.emptyMap(), null);
    }

    public StructureMatchSession(@NotNull Map<MultiblockAbility<?>, int[]> abilityLimits,
                                 @Nullable PatternMatchContext initialContext) {
        this.parent = null;
        this.abilityLimits = copyLimits(abilityLimits);
        this.context = initialContext == null ? new PatternMatchContext() : initialContext.copy();
        this.globalCount = new HashMap<>();
        this.typedData = new HashMap<>();
        this.controllerContext = null;
    }

    private StructureMatchSession(@NotNull StructureMatchSession parent) {
        this.parent = parent;
        this.abilityLimits = parent.abilityLimits;
        this.context = parent.context.copy();
        this.globalCount = new HashMap<>(parent.globalCount);
        this.typedData = copyTypedData(parent.typedData);
        this.controllerContext = parent.controllerContext;
    }

    @NotNull
    public StructureMatchSession fork() {
        return new StructureMatchSession(this);
    }

    public void commit() {
        if (parent == null) {
            throw new IllegalStateException("Root structure match session cannot be committed");
        }
        parent.context.replaceWith(context);
        parent.globalCount.clear();
        parent.globalCount.putAll(globalCount);
        parent.typedData.clear();
        parent.typedData.putAll(copyTypedData(typedData));
    }

    @NotNull
    public Checkpoint checkpoint() {
        return new Checkpoint(context.copy(), new HashMap<>(globalCount), copyTypedData(typedData));
    }

    public void restore(@NotNull Checkpoint checkpoint) {
        context.replaceWith(checkpoint.context);
        globalCount.clear();
        globalCount.putAll(checkpoint.globalCount);
        typedData.clear();
        typedData.putAll(copyTypedData(checkpoint.typedData));
    }

    @NotNull
    public PatternMatchContext getContext() {
        return context;
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

        if (includeAbilityLimits) {
            Set<IMultiblockPart> parts = context.getOrDefault("MultiblockParts", Collections.emptySet());
            for (Map.Entry<MultiblockAbility<?>, int[]> entry : abilityLimits.entrySet()) {
                int count = 0;
                for (IMultiblockPart part : parts) {
                    if (part instanceof IMultiblockAbilityPart<?> abilityPart
                            && abilityPart.getAbilities().contains(entry.getKey())) {
                        count++;
                    }
                }
                int[] range = entry.getValue();
                if (count < range[0]) {
                    missingAbilities.merge(entry.getKey(), range[0] - count, Math::max);
                } else if (range[1] >= 0 && count > range[1]) {
                    return Validation.failure("Ability '" + entry.getKey() + "' count " + count
                            + " is outside [" + range[0] + ", " + range[1] + "]");
                }
            }
        }

        if (!missingAbilities.isEmpty()) {
            return Validation.missingAbilities(missingAbilities);
        }
        if (firstMissingPredicate != null) {
            return Validation.failure("A global structure predicate did not reach its minimum count");
        }
        return Validation.success();
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

    private static <T> T copyTypedValue(@NotNull StructureSessionKey<T> key,
                                        @NotNull Object value) {
        return key.copy((T) value);
    }

    public static final class Checkpoint {

        private final PatternMatchContext context;
        private final Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount;
        private final Map<StructureSessionKey<?>, Object> typedData;

        private Checkpoint(@NotNull PatternMatchContext context,
                           @NotNull Map<TraceabilityPredicate.SimplePredicate, Integer> globalCount,
                           @NotNull Map<StructureSessionKey<?>, Object> typedData) {
            this.context = context;
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
