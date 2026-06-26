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

/**
 * Typed structure-formation payload delivered to new controller callbacks.
 *
 * <p>The legacy {@link PatternMatchContext} callback projection has been
 * removed; controllers read typed state directly from this view.
 */
public final class FormedStructureView {

    @Nullable
    private final FormedStructureMetadata metadata;
    @NotNull
    private final StructureChannelValues channelValues;
    @NotNull
    private final StructureOperationState operationState;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> abilityCounts;
    @NotNull
    private final Map<String, Object> aggregateValues;
    private final boolean flipped;

    private FormedStructureView(@Nullable FormedStructureMetadata metadata,
                                @NotNull StructureChannelValues channelValues,
                                @NotNull StructureOperationState operationState,
                                @NotNull Map<MultiblockAbility<?>, Integer> abilityCounts,
                                @NotNull Map<String, Object> aggregateValues,
                                boolean flipped) {
        this.metadata = metadata;
        this.channelValues = channelValues.copy();
        this.operationState = operationState.copy();
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
                result.getAbilityCounts(),
                aggregate == null ? Collections.emptyMap() : aggregate.getAggregateValues(),
                result.isFlipped());
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

    public boolean isFlipped() {
        return flipped;
    }

    @NotNull
    private static String channelAggregateId(@NotNull StructureChannel channel) {
        return "gregtech:legacy/channel/" + channel.getName();
    }
}
