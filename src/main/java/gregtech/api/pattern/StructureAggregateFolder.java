package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure declaration-ordered fold from piece results to formation state.
 */
public final class StructureAggregateFolder {

    private StructureAggregateFolder() {}

    @NotNull
    public static Result fold(@NotNull MultiPiecePattern pattern,
                              @NotNull StructureResultTable table) {
        if (table.size() != pattern.getPieceCount()) {
            return Result.failure("Result table does not match the compiled piece count");
        }

        StructureOperationState operationState = new StructureOperationState();
        Map<String, KeyAccumulator> accumulators = new LinkedHashMap<>();
        Map<Object, Set<IMultiblockPart>> countedAbilityParts = new LinkedHashMap<>();
        Set<BlockPos> variantActiveBlocks = new LinkedHashSet<>();
        Map<String, int[]> repetitions = new LinkedHashMap<>();
        Map<String, BlockPos> centers = new LinkedHashMap<>();

        for (int ordinal = 0; ordinal < pattern.getPieceList().size(); ordinal++) {
            StructurePiece expectedPiece = pattern.getPieceList().get(ordinal);
            PieceEvaluationResult pieceResult = table.getResults().get(ordinal);
            if (pieceResult.getPiece() != expectedPiece) {
                return Result.failure("Result table piece order does not match the compiled pattern");
            }
            if (!pieceResult.isActive()) {
                continue;
            }

            BlockPos center = pieceResult.getResolvedCenter();
            if (center == null) {
                return Result.failure(
                        "Active piece '" + expectedPiece.getName() + "' has no resolved center");
            }
            centers.put(expectedPiece.getName(), center);
            int[] pieceRepetitions = pieceResult.getRepetitions();
            if (pieceRepetitions.length > 0) {
                repetitions.put(expectedPiece.getName(), pieceRepetitions);
            }

            StructureContribution contribution = pieceResult.getContribution();
            for (Map.Entry<Object, StructureContribution.Requirement> entry :
                    contribution.getRequirements().entrySet()) {
                StructureMatchCollector.CountRequirement candidate =
                        entry.getValue().toCollectorRequirement();
                StructureMatchCollector.CountRequirement existing =
                        operationState.requirements.get(entry.getKey());
                if (existing != null && !existing.isCompatibleWith(candidate)) {
                    return Result.failure(
                            "Conflicting requirement declaration for key " + entry.getKey());
                }
                operationState.requirements.putIfAbsent(entry.getKey(), candidate);
            }
            for (Map.Entry<Object, Integer> entry : contribution.getCounts().entrySet()) {
                StructureContribution.Requirement requirement =
                        contribution.getRequirements().get(entry.getKey());
                if (requirement == null || requirement.getAbility() == null) {
                    operationState.counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
            operationState.parts.addAll(contribution.getParts());
            for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry :
                    contribution.getAbilityParts().entrySet()) {
                operationState.abilityParts
                        .computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>())
                        .addAll(entry.getValue());
            }
            for (Map.Entry<Object, Set<IMultiblockPart>> entry :
                    contribution.getCountedAbilityParts().entrySet()) {
                countedAbilityParts
                        .computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>())
                        .addAll(entry.getValue());
            }
            variantActiveBlocks.addAll(contribution.getVariantActiveBlocks());

            for (Map.Entry<StructureContributionKey<?, ?>, List<?>> entry :
                    contribution.getTypedEmissions().entrySet()) {
                StructureContributionKey<?, ?> key = entry.getKey();
                KeyAccumulator accumulator = accumulators.get(key.getId());
                if (accumulator == null) {
                    accumulator = new KeyAccumulator(key);
                    accumulators.put(key.getId(), accumulator);
                } else if (!accumulator.key.isCompatibleWith(key)) {
                    return Result.failure(
                            "Contribution key id '" + key.getId() + "' has conflicting schemas");
                }
                try {
                    for (Object emission : entry.getValue()) {
                        accumulator.reduce(emission);
                    }
                } catch (StructureContributionKey.ReductionException e) {
                    return Result.failure(e.getMessage());
                }
            }
        }

        for (Map.Entry<Object, Set<IMultiblockPart>> entry : countedAbilityParts.entrySet()) {
            operationState.countedAbilityParts.put(
                    entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            operationState.counts.put(entry.getKey(), entry.getValue().size());
        }
        for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry :
                operationState.abilityParts.entrySet()) {
            operationState.abilityCounts.put(entry.getKey(), entry.getValue().size());
        }
        operationState.variantActiveBlocks.addAll(variantActiveBlocks);
        PatternMatchContext compatibilityContext = new PatternMatchContext();
        Map<String, Object> aggregateValues = new LinkedHashMap<>();
        for (KeyAccumulator accumulator : accumulators.values()) {
            StructureContributionKey.Validation validation = accumulator.validate();
            if (!validation.isSuccess()) {
                return Result.failure(
                        validation.getErrorMessage() == null
                                ? "Contribution validation failed for " + accumulator.key.getId()
                                : validation.getErrorMessage());
            }
            Object value = accumulator.copyValue();
            aggregateValues.put(accumulator.key.getId(), value);
            accumulator.project(compatibilityContext);
        }
        operationState.applyCompatibilityView(compatibilityContext);

        StructureMatchSession aggregateSession = pattern.createMatchSession(compatibilityContext);
        aggregateSession.getOperationState().replaceWith(operationState);
        StructureMatchSession.Validation validation = aggregateSession.validate(true);

        Map<String, Integer> channelValues = new HashMap<>();
        for (Map.Entry<String, Object> entry : compatibilityContext.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                channelValues.put(entry.getKey(), (Integer) entry.getValue());
            }
        }
        FormedStructureMetadata metadata =
                FormedStructureMetadata.fromCheckResult(repetitions, channelValues, centers);
        if (!validation.success) {
            return Result.validationFailure(
                    validation.errorMessage == null
                            ? "Contribution aggregate validation failed"
                            : validation.errorMessage,
                    operationState, compatibilityContext, metadata, aggregateValues,
                    validation.missingAbilities, validation.abilityCounts);
        }
        return Result.success(
                operationState, compatibilityContext, metadata, aggregateValues,
                validation.abilityCounts);
    }

    private static final class KeyAccumulator {

        @NotNull
        private final StructureContributionKey key;
        @Nullable
        private Object value;

        private KeyAccumulator(@NotNull StructureContributionKey<?, ?> key) {
            this.key = key;
            this.value = key.identity();
        }

        @SuppressWarnings("unchecked")
        private void reduce(@Nullable Object emission) {
            value = key.reduce(value, emission);
        }

        @NotNull
        @SuppressWarnings("unchecked")
        private StructureContributionKey.Validation validate() {
            return key.validate(value);
        }

        @Nullable
        @SuppressWarnings("unchecked")
        private Object copyValue() {
            return key.copyAggregate(value);
        }

        @SuppressWarnings("unchecked")
        private void project(@NotNull PatternMatchContext context) {
            key.project(context, value);
        }
    }

    public static final class Result {

        private final boolean matched;
        @Nullable
        private final String errorMessage;
        @NotNull
        private final StructureOperationState operationState;
        @NotNull
        private final PatternMatchContext compatibilityContext;
        @Nullable
        private final FormedStructureMetadata metadata;
        @NotNull
        private final Map<String, Object> aggregateValues;
        @NotNull
        private final Map<MultiblockAbility<?>, Integer> missingAbilities;
        @NotNull
        private final Map<MultiblockAbility<?>, Integer> abilityCounts;

        private Result(boolean matched,
                       @Nullable String errorMessage,
                       @NotNull StructureOperationState operationState,
                       @NotNull PatternMatchContext compatibilityContext,
                       @Nullable FormedStructureMetadata metadata,
                       @NotNull Map<String, Object> aggregateValues,
                       @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                       @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts) {
            this.matched = matched;
            this.errorMessage = errorMessage;
            this.operationState = operationState.copy();
            this.compatibilityContext = compatibilityContext.copy();
            this.metadata = metadata;
            this.aggregateValues =
                    Collections.unmodifiableMap(new LinkedHashMap<>(aggregateValues));
            this.missingAbilities =
                    Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
            this.abilityCounts =
                    Collections.unmodifiableMap(new LinkedHashMap<>(abilityCounts));
        }

        @NotNull
        private static Result success(
                @NotNull StructureOperationState operationState,
                @NotNull PatternMatchContext compatibilityContext,
                @NotNull FormedStructureMetadata metadata,
                @NotNull Map<String, Object> aggregateValues,
                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts) {
            return new Result(
                    true, null, operationState, compatibilityContext, metadata,
                    aggregateValues, Collections.emptyMap(), abilityCounts);
        }

        @NotNull
        private static Result validationFailure(
                @NotNull String errorMessage,
                @NotNull StructureOperationState operationState,
                @NotNull PatternMatchContext compatibilityContext,
                @NotNull FormedStructureMetadata metadata,
                @NotNull Map<String, Object> aggregateValues,
                @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts) {
            return new Result(
                    false, errorMessage, operationState, compatibilityContext, metadata,
                    aggregateValues, missingAbilities, abilityCounts);
        }

        @NotNull
        private static Result failure(@NotNull String errorMessage) {
            return new Result(
                    false, errorMessage, new StructureOperationState(), new PatternMatchContext(),
                    null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        public boolean isMatched() {
            return matched;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        @NotNull
        public StructureOperationState copyOperationState() {
            return operationState.copy();
        }

        @NotNull
        public PatternMatchContext copyCompatibilityContext() {
            return compatibilityContext.copy();
        }

        @Nullable
        public FormedStructureMetadata getMetadata() {
            return metadata;
        }

        @NotNull
        public Map<String, Object> getAggregateValues() {
            return aggregateValues;
        }

        @Nullable
        @SuppressWarnings("unchecked")
        public <A> A get(@NotNull StructureContributionKey<?, A> key) {
            return (A) aggregateValues.get(key.getId());
        }

        @NotNull
        public Map<MultiblockAbility<?>, Integer> getMissingAbilities() {
            return missingAbilities;
        }

        @NotNull
        public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
            return abilityCounts;
        }
    }
}
