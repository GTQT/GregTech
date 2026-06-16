package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Typed structure-formation payload delivered to new controller callbacks.
 *
 * <p>The legacy {@link PatternMatchContext} callback view is exposed only as a
 * compatibility projection and is materialized lazily while bridging an old
 * callback that actually requests it.
 */
public final class FormedStructureView {

    private static final ThreadLocal<LegacyProjectionScope> ACTIVE_LEGACY_PROJECTION = new ThreadLocal<>();

    @Nullable
    private final FormedStructureMetadata metadata;
    @NotNull
    private final StructureChannelValues channelValues;
    @NotNull
    private final StructureOperationState operationState;
    @Nullable
    private final Supplier<PatternMatchContext> legacyCallbackProjection;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> abilityCounts;
    @NotNull
    private final Map<String, Object> aggregateValues;
    private final boolean flipped;

    private FormedStructureView(@Nullable FormedStructureMetadata metadata,
                                @NotNull StructureChannelValues channelValues,
                                @NotNull StructureOperationState operationState,
                                @Nullable Supplier<PatternMatchContext> legacyCallbackProjection,
                                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                @NotNull Map<String, Object> aggregateValues,
                                boolean flipped) {
        this.metadata = metadata;
        this.channelValues = channelValues.copy();
        this.operationState = operationState.copy();
        this.legacyCallbackProjection = legacyCallbackProjection;
        this.abilityCounts = Collections.unmodifiableMap(new LinkedHashMap<>(abilityCounts));
        this.aggregateValues = Collections.unmodifiableMap(new LinkedHashMap<>(aggregateValues));
        this.flipped = flipped;
    }

    @ApiStatus.Internal
    @NotNull
    public static FormedStructureView fromCheckResult(@NotNull StructureCheckResult result) {
        StructureAggregateFolder.Result aggregate = result.getContributionAggregate();
        return new FormedStructureView(
                result.getMetadata(),
                result.copyChannelValues(),
                result.copyOperationState(),
                null,
                result.getAbilityCounts(),
                aggregate == null ? Collections.emptyMap() : aggregate.getAggregateValues(),
                result.isFlipped());
    }

    /**
     * Compatibility constructor for callers that already materialized a legacy
     * callback context. Prefer {@link #fromCheckResult(StructureCheckResult)}
     * so normal typed formation does not eagerly copy legacy state.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    @ApiStatus.Internal
    @NotNull
    public static FormedStructureView fromCheckResult(
            @NotNull StructureCheckResult result,
            @NotNull PatternMatchContext legacyCallbackView) {
        StructureAggregateFolder.Result aggregate = result.getContributionAggregate();
        return new FormedStructureView(
                result.getMetadata(),
                result.copyChannelValues(),
                result.copyOperationState(),
                snapshotProjection(legacyCallbackView),
                result.getAbilityCounts(),
                aggregate == null ? Collections.emptyMap() : aggregate.getAggregateValues(),
                result.isFlipped());
    }

    @ApiStatus.Internal
    @NotNull
    public static FormedStructureView legacy(
            @Nullable FormedStructureMetadata metadata,
            @NotNull StructureChannelValues channelValues,
            @NotNull StructureOperationState operationState,
            @NotNull PatternMatchContext legacyCallbackView,
            boolean flipped) {
        return new FormedStructureView(
                metadata, channelValues, operationState, snapshotProjection(legacyCallbackView),
                operationState.getAbilityCounts(), Collections.emptyMap(), flipped);
    }

    @ApiStatus.Internal
    public static void runWithLegacyCallbackProjection(@NotNull FormedStructureView view,
                                                       @NotNull StructureCheckResult result,
                                                       @NotNull Runnable action) {
        runWithLegacyCallbackProjection(view, lazyResultProjection(result), action);
    }

    @ApiStatus.Internal
    public static void runWithLegacyCallbackProjection(@NotNull FormedStructureView view,
                                                       @NotNull PatternMatchContext context,
                                                       @NotNull Runnable action) {
        runWithLegacyCallbackProjection(view, snapshotProjection(context), action);
    }

    private static void runWithLegacyCallbackProjection(@NotNull FormedStructureView view,
                                                        @NotNull Supplier<PatternMatchContext> projection,
                                                        @NotNull Runnable action) {
        LegacyProjectionScope previous = ACTIVE_LEGACY_PROJECTION.get();
        ACTIVE_LEGACY_PROJECTION.set(new LegacyProjectionScope(view, projection));
        try {
            action.run();
        } finally {
            if (previous == null) {
                ACTIVE_LEGACY_PROJECTION.remove();
            } else {
                ACTIVE_LEGACY_PROJECTION.set(previous);
            }
        }
    }

    public int getChannelValue(@NotNull StructureChannel channel) {
        return channelValues.getValue(channel);
    }

    public boolean hasChannelValue(@NotNull StructureChannel channel) {
        return channelValues.hasValue(channel);
    }

    public int getMetadataChannelValue(@NotNull String channelName) {
        return metadata == null ? 0 : metadata.getChannelValue(channelName);
    }

    public int getMetadataChannelValue(@NotNull StructureChannel channel) {
        return getMetadataChannelValue(channel.getName());
    }

    public int getPieceRepeat(@NotNull String pieceName, int axisIndex) {
        return metadata == null ? 0 : metadata.getPieceRepeat(pieceName, axisIndex);
    }

    public int getPieceRepeat(@NotNull StructurePieceKey pieceKey, int axisIndex) {
        return metadata == null ? 0 : metadata.getPieceRepeat(pieceKey, axisIndex);
    }

    @NotNull
    public int[] getPieceRepeats(@NotNull String pieceName) {
        return metadata == null ? new int[0] : metadata.getPieceRepeats(pieceName);
    }

    @NotNull
    public int[] getPieceRepeats(@NotNull StructurePieceKey pieceKey) {
        return metadata == null ? new int[0] : metadata.getPieceRepeats(pieceKey);
    }

    @Nullable
    public BlockPos getPieceCenter(@NotNull String pieceName) {
        return metadata == null ? null : metadata.getPieceCenter(pieceName);
    }

    @Nullable
    public BlockPos getPieceCenter(@NotNull StructurePieceKey pieceKey) {
        return metadata == null ? null : metadata.getPieceCenter(pieceKey);
    }

    @NotNull
    public Set<IMultiblockPart> getParts() {
        return operationState.getParts();
    }

    @NotNull
    public List<BlockPos> getVariantActiveBlocks() {
        return operationState.getVariantActiveBlocks();
    }

    public int getAbilityCount(@NotNull MultiblockAbility<?> ability) {
        return abilityCounts.getOrDefault(ability, 0);
    }

    public boolean hasAbility(@NotNull MultiblockAbility<?> ability) {
        return getAbilityCount(ability) > 0;
    }

    @Nullable
    public <A> A getChannelAggregate(@NotNull StructureChannel channel,
                                     @NotNull Class<A> type) {
        Object value = aggregateValues.get(channelAggregateId(channel));
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /**
     * Compatibility projection for old {@code formStructure(PatternMatchContext)}
     * overrides. New code should read typed state directly from this view.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    @NotNull
    public PatternMatchContext copyLegacyCallbackContext() {
        Supplier<PatternMatchContext> projection = legacyCallbackProjection;
        if (projection == null) {
            LegacyProjectionScope scope = ACTIVE_LEGACY_PROJECTION.get();
            if (scope != null && scope.view == this) {
                projection = scope.projection;
            }
        }
        if (projection == null) {
            throw new IllegalStateException(
                    "Legacy callback context is only available while bridging formStructure(PatternMatchContext)");
        }
        return projection.get();
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getAbilityCounts() {
        return abilityCounts;
    }

    @NotNull
    @ApiStatus.Internal
    public Map<String, Object> getAggregateValues() {
        return aggregateValues;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <A> A getAggregate(@NotNull StructureContributionKey<?, A> key) {
        return (A) aggregateValues.get(key.getId());
    }

    /**
     * Compatibility lookup for legacy aggregate ids. Prefer
     * {@link #getAggregate(StructureContributionKey)} or a domain-specific typed
     * helper.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    @Nullable
    @SuppressWarnings("unchecked")
    public <A> A getAggregate(@NotNull String key) {
        return (A) aggregateValues.get(key);
    }

    public boolean isFlipped() {
        return flipped;
    }

    @NotNull
    private static String channelAggregateId(@NotNull StructureChannel channel) {
        return "gregtech:legacy/channel/" + channel.getName();
    }

    @NotNull
    private static Supplier<PatternMatchContext> lazyResultProjection(
            @NotNull StructureCheckResult result) {
        return () -> {
            PatternMatchContext context = result.copyContext();
            if (context == null) {
                throw new IllegalStateException(
                        "Cannot materialize a legacy callback context from a result without match context");
            }
            return context;
        };
    }

    @NotNull
    private static Supplier<PatternMatchContext> snapshotProjection(
            @NotNull PatternMatchContext context) {
        PatternMatchContext snapshot = context.copy();
        return snapshot::copy;
    }

    private static final class LegacyProjectionScope {

        @NotNull
        private final FormedStructureView view;
        @NotNull
        private final Supplier<PatternMatchContext> projection;

        private LegacyProjectionScope(@NotNull FormedStructureView view,
                                      @NotNull Supplier<PatternMatchContext> projection) {
            this.view = view;
            this.projection = projection;
        }
    }
}
