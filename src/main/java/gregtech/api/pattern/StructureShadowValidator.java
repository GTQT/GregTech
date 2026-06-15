package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Debug-only oracle for incremental checks.
 */
final class StructureShadowValidator {

    private static final long SAMPLE_MASK = 63L;
    private static final AtomicLong SAMPLE_COUNTER = new AtomicLong();

    private StructureShadowValidator() {}

    static void maybeValidateIncremental(
            @NotNull StructureCheckOperationService evaluator,
            @NotNull StructureOperationRequest request,
            @NotNull StructureCheckResult incrementalResult,
            @NotNull StructureWorldReadTracker.Metrics incrementalReads) {
        if (!ConfigHolder.machines.debugStructureCheck
                || !incrementalResult.usedIncrementalEvaluator()
                || (SAMPLE_COUNTER.incrementAndGet() & SAMPLE_MASK) != 0L) {
            return;
        }

        StructureCheckResult fullResult;
        StructureWorldReadTracker.Metrics fullReads;
        StructureWorldReadTracker.Scope fullReadScope =
                StructureWorldReadTracker.begin();
        try {
            fullResult = evaluator.check(request);
        } catch (RuntimeException e) {
            fullReads = fullReadScope.finish();
            GTLog.logger.warn("[StructureIncrementalShadow] Full shadow check threw for {}: {}",
                    controllerName(request.getController()), e.toString());
            GTLog.logger.warn(
                    "[StructureIncrementalShadow] Read metrics before exception for {}: incremental={}, full={}",
                    controllerName(request.getController()), incrementalReads, fullReads);
            return;
        }
        fullReads = fullReadScope.finish();

        Mismatch mismatch = compare(incrementalResult, fullResult);
        if (mismatch != null) {
            GTLog.logger.warn(
                    "[StructureIncrementalShadow] Mismatch for {}: {}; reads incremental={}, full={}",
                    controllerName(request.getController()), mismatch.describe(),
                    incrementalReads, fullReads);
            return;
        }
        StructureIncrementalCheckResult diagnostic =
                incrementalResult.getIncrementalCheckResult();
        GTLog.logger.debug(
                "[StructureIncrementalShadow] Validated {}: {}, reads incremental={}, full={}",
                controllerName(request.getController()),
                diagnostic == null ? "no incremental diagnostics" : diagnostic.describe(),
                incrementalReads, fullReads);
    }

    @Nullable
    static Mismatch compare(@NotNull StructureCheckResult incrementalResult,
                            @NotNull StructureCheckResult fullResult) {
        Snapshot incremental = Snapshot.from(incrementalResult);
        Snapshot full = Snapshot.from(fullResult);
        return incremental.equals(full) ? null : new Mismatch(incremental, full);
    }

    @NotNull
    private static String controllerName(@Nullable MultiblockControllerBase controller) {
        return controller == null ? "unknown" : controller.getMetaName();
    }

    static final class Mismatch {

        @NotNull
        private final Snapshot incremental;
        @NotNull
        private final Snapshot full;

        private Mismatch(@NotNull Snapshot incremental, @NotNull Snapshot full) {
            this.incremental = incremental;
            this.full = full;
        }

        @NotNull
        String describe() {
            return "incremental=" + incremental + ", full=" + full;
        }
    }

    private static final class Snapshot {

        private final boolean matched;
        @Nullable
        private final Long resultTableFingerprint;
        @Nullable
        private final Map<String, Object> aggregateValues;
        @Nullable
        private final Set<IMultiblockPart> parts;
        @Nullable
        private final Map<MultiblockAbility<?>, Integer> resultAbilityCounts;
        @Nullable
        private final Map<MultiblockAbility<?>, Integer> stateAbilityCounts;
        @Nullable
        private final Map<MultiblockAbility<?>, Set<IMultiblockPart>> abilityParts;
        @Nullable
        private final List<BlockPos> variantActiveBlocks;
        @Nullable
        private final MetadataSnapshot metadata;
        @NotNull
        private final StructureChannelValues channelValues;

        @NotNull
        private static Snapshot from(@NotNull StructureCheckResult result) {
            StructureResultTable table = result.getResultTable();
            StructureAggregateFolder.Result aggregate = result.getContributionAggregate();
            StructureOperationState state = result.copyOperationState();
            return new Snapshot(
                    result.isMatched(),
                    table == null ? null : table.getSemanticFingerprint(),
                    aggregate == null ? null : aggregate.getAggregateValues(),
                    state.getParts(),
                    result.getAbilityCounts(),
                    state.getAbilityCounts(),
                    copyAbilityParts(state),
                    state.getVariantActiveBlocks(),
                    MetadataSnapshot.from(result.getMetadata(), table),
                    result.copyChannelValues());
        }

        private Snapshot(boolean matched,
                         @Nullable Long resultTableFingerprint,
                         @Nullable Map<String, Object> aggregateValues,
                         @Nullable Set<IMultiblockPart> parts,
                         @Nullable Map<MultiblockAbility<?>, Integer> resultAbilityCounts,
                         @Nullable Map<MultiblockAbility<?>, Integer> stateAbilityCounts,
                         @Nullable Map<MultiblockAbility<?>, Set<IMultiblockPart>> abilityParts,
                         @Nullable List<BlockPos> variantActiveBlocks,
                         @Nullable MetadataSnapshot metadata,
                         @NotNull StructureChannelValues channelValues) {
            this.matched = matched;
            this.resultTableFingerprint = resultTableFingerprint;
            this.aggregateValues = aggregateValues;
            this.parts = parts;
            this.resultAbilityCounts = resultAbilityCounts;
            this.stateAbilityCounts = stateAbilityCounts;
            this.abilityParts = abilityParts;
            this.variantActiveBlocks = variantActiveBlocks;
            this.metadata = metadata;
            this.channelValues = channelValues;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Snapshot)) return false;
            Snapshot other = (Snapshot) obj;
            return matched == other.matched
                    && Objects.equals(resultTableFingerprint, other.resultTableFingerprint)
                    && Objects.equals(aggregateValues, other.aggregateValues)
                    && Objects.equals(parts, other.parts)
                    && Objects.equals(resultAbilityCounts, other.resultAbilityCounts)
                    && Objects.equals(stateAbilityCounts, other.stateAbilityCounts)
                    && Objects.equals(abilityParts, other.abilityParts)
                    && Objects.equals(variantActiveBlocks, other.variantActiveBlocks)
                    && Objects.equals(metadata, other.metadata)
                    && channelValues.equals(other.channelValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matched, resultTableFingerprint, aggregateValues,
                    parts, resultAbilityCounts, stateAbilityCounts, abilityParts,
                    variantActiveBlocks, metadata, channelValues);
        }

        @Override
        public String toString() {
            return "Snapshot{"
                    + "matched=" + matched
                    + ", resultTableFingerprint=" + resultTableFingerprint
                    + ", aggregateValues=" + aggregateValues
                    + ", parts=" + (parts == null ? null : parts.size())
                    + ", resultAbilityCounts=" + resultAbilityCounts
                    + ", stateAbilityCounts=" + stateAbilityCounts
                    + ", abilityParts=" + describeAbilityParts(abilityParts)
                    + ", variantActiveBlocks=" + variantActiveBlocks
                    + ", metadata=" + metadata
                    + ", channelValues=" + channelValues
                    + '}';
        }

        @NotNull
        private static Map<MultiblockAbility<?>, Set<IMultiblockPart>> copyAbilityParts(
                @NotNull StructureOperationState state) {
            Map<MultiblockAbility<?>, Set<IMultiblockPart>> copy = new LinkedHashMap<>();
            for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry :
                    state.abilityParts.entrySet()) {
                copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            return copy;
        }

        @Nullable
        private static Map<MultiblockAbility<?>, Integer> describeAbilityParts(
                @Nullable Map<MultiblockAbility<?>, Set<IMultiblockPart>> abilityParts) {
            if (abilityParts == null) {
                return null;
            }
            Map<MultiblockAbility<?>, Integer> counts = new LinkedHashMap<>();
            for (Map.Entry<MultiblockAbility<?>, Set<IMultiblockPart>> entry :
                    abilityParts.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().size());
            }
            return counts;
        }
    }

    private static final class MetadataSnapshot {

        @NotNull
        private final Map<String, int[]> repeats;
        @NotNull
        private final Map<String, BlockPos> centers;
        @NotNull
        private final Map<String, Integer> channelValues;

        @Nullable
        private static MetadataSnapshot from(@Nullable FormedStructureMetadata metadata,
                                             @Nullable StructureResultTable table) {
            if (metadata == null) {
                return null;
            }
            Map<String, int[]> repeats = new LinkedHashMap<>();
            Map<String, BlockPos> centers = new LinkedHashMap<>();
            if (table != null) {
                for (PieceEvaluationResult result : table.getResults()) {
                    String pieceName = result.getPiece().getName();
                    repeats.put(pieceName, metadata.getPieceRepeats(pieceName));
                    centers.put(pieceName, metadata.getPieceCenter(pieceName));
                }
            }
            return new MetadataSnapshot(repeats, centers, metadata.getChannelValues());
        }

        private MetadataSnapshot(@NotNull Map<String, int[]> repeats,
                                 @NotNull Map<String, BlockPos> centers,
                                 @NotNull Map<String, Integer> channelValues) {
            this.repeats = repeats;
            this.centers = centers;
            this.channelValues = channelValues;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MetadataSnapshot)) return false;
            MetadataSnapshot other = (MetadataSnapshot) obj;
            return repeatsEqual(repeats, other.repeats)
                    && Objects.equals(centers, other.centers)
                    && Objects.equals(channelValues, other.channelValues);
        }

        @Override
        public int hashCode() {
            int result = centers.hashCode();
            result = 31 * result + channelValues.hashCode();
            for (Map.Entry<String, int[]> entry : repeats.entrySet()) {
                result = 31 * result + entry.getKey().hashCode();
                result = 31 * result + Arrays.hashCode(entry.getValue());
            }
            return result;
        }

        @Override
        public String toString() {
            Map<String, String> repeatStrings = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> entry : repeats.entrySet()) {
                repeatStrings.put(entry.getKey(), Arrays.toString(entry.getValue()));
            }
            return "MetadataSnapshot{"
                    + "repeats=" + repeatStrings
                    + ", centers=" + centers
                    + ", channelValues=" + channelValues
                    + '}';
        }

        private static boolean repeatsEqual(@NotNull Map<String, int[]> left,
                                            @NotNull Map<String, int[]> right) {
            if (!left.keySet().equals(right.keySet())) {
                return false;
            }
            for (String key : left.keySet()) {
                if (!Arrays.equals(left.get(key), right.get(key))) {
                    return false;
                }
            }
            return true;
        }
    }
}
