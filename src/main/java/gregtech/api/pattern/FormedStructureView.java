package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed structure-formation payload delivered to new controller callbacks.
 *
 * <p>The legacy {@link PatternMatchContext} callback view is materialized only
 * at the result boundary and exposed here as a compatibility projection.
 */
public final class FormedStructureView {

    @Nullable
    private final FormedStructureMetadata metadata;
    @NotNull
    private final StructureChannelValues channelValues;
    @NotNull
    private final StructureOperationState operationState;
    @NotNull
    private final PatternMatchContext legacyCallbackView;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> abilityCounts;
    @NotNull
    private final Map<String, Object> aggregateValues;
    private final boolean flipped;

    private FormedStructureView(@Nullable FormedStructureMetadata metadata,
                                @NotNull StructureChannelValues channelValues,
                                @NotNull StructureOperationState operationState,
                                @NotNull PatternMatchContext legacyCallbackView,
                                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                @NotNull Map<String, Object> aggregateValues,
                                boolean flipped) {
        this.metadata = metadata;
        this.channelValues = channelValues.copy();
        this.operationState = operationState.copy();
        this.legacyCallbackView = legacyCallbackView.copy();
        this.abilityCounts = Collections.unmodifiableMap(new LinkedHashMap<>(abilityCounts));
        this.aggregateValues = Collections.unmodifiableMap(new LinkedHashMap<>(aggregateValues));
        this.flipped = flipped;
    }

    @NotNull
    public static FormedStructureView fromCheckResult(
            @NotNull StructureCheckResult result,
            @NotNull PatternMatchContext legacyCallbackView) {
        StructureAggregateFolder.Result aggregate = result.getContributionAggregate();
        return new FormedStructureView(
                result.getMetadata(),
                result.copyChannelValues(),
                result.copyOperationState(),
                legacyCallbackView,
                result.getAbilityCounts(),
                aggregate == null ? Collections.emptyMap() : aggregate.getAggregateValues(),
                result.isFlipped());
    }

    @NotNull
    public static FormedStructureView legacy(
            @Nullable FormedStructureMetadata metadata,
            @NotNull StructureChannelValues channelValues,
            @NotNull StructureOperationState operationState,
            @NotNull PatternMatchContext legacyCallbackView,
            boolean flipped) {
        return new FormedStructureView(
                metadata, channelValues, operationState, legacyCallbackView,
                Collections.emptyMap(), Collections.emptyMap(), flipped);
    }

    @Nullable
    public FormedStructureMetadata getMetadata() {
        return metadata;
    }

    @NotNull
    public StructureChannelValues copyChannelValues() {
        return channelValues.copy();
    }

    @NotNull
    public StructureOperationState copyOperationState() {
        return operationState.copy();
    }

    @NotNull
    public PatternMatchContext copyLegacyCallbackContext() {
        return legacyCallbackView.copy();
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
        return abilityCounts;
    }

    @NotNull
    public Map<String, Object> getAggregateValues() {
        return aggregateValues;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <A> A getAggregate(@NotNull StructureContributionKey<?, A> key) {
        return (A) aggregateValues.get(key.getId());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <A> A getAggregate(@NotNull String key) {
        return (A) aggregateValues.get(key);
    }

    public boolean isFlipped() {
        return flipped;
    }
}
