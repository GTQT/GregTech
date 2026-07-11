package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.CountLimitError;
import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.element.AutoPlaceEnvironment;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * V3 wrapper that applies count and preview limits to any structure element.
 */
public final class CountedElement implements IStructureElement<Object> {

    private final IStructureElement delegate;
    private final int minGlobalCount;
    private final int maxGlobalCount;
    private final int minLayerCount;
    private final int maxLayerCount;
    private final int previewCount;

    public CountedElement(@NotNull IStructureElement delegate,
                          int minGlobalCount, int maxGlobalCount,
                          int minLayerCount, int maxLayerCount,
                          int previewCount) {
        this.delegate = delegate;
        this.minGlobalCount = Math.max(0, minGlobalCount);
        this.maxGlobalCount = maxGlobalCount;
        this.minLayerCount = Math.max(0, minLayerCount);
        this.maxLayerCount = maxLayerCount;
        this.previewCount = previewCount;
    }

    @NotNull
    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return delegate.getCapabilities();
    }

    @NotNull
    @Override
    public StructureIncrementalSupport getIncrementalSupport() {
        return delegate.getIncrementalSupport();
    }

    @NotNull
    @Override
    public Set<StructureDependency> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public boolean hasExplicitIncrementalContract() {
        return delegate.hasExplicitIncrementalContract();
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        return context.transaction(transactionContext ->
                delegate.check(transactionContext) && recordCount(transactionContext));
    }

    @Override
    public boolean match(@NotNull StructureEvaluationContext<Object> context) {
        collectRequirements(context);
        return context.transaction(transactionContext ->
                delegate.check(transactionContext) && recordCount(transactionContext));
    }

    private boolean recordCount(@NotNull StructureEvaluationContext<Object> context) {
        if (!hasGlobalLimit()) {
            return true;
        }
        if (!context.getCollector().recordCount(this)) {
            context.setError(new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxGlobalCount));
            return false;
        }
        return true;
    }

    private boolean hasGlobalLimit() {
        return minGlobalCount > 0 || maxGlobalCount >= 0;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return delegate.getCandidates();
    }

    @Override
    public BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<Object> context) {
        return delegate.getCandidates(context);
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview() {
        StructureElementPreview preview = delegate.getPreview();
        if (!hasAnyLimit()) {
            return preview;
        }
        return limitedPreview();
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview(@NotNull StructureEvaluationContext<Object> context) {
        return context.probeValue(ignored -> getPreview());
    }

    @NotNull
    private StructureElementPreview limitedPreview() {
        StructureElementPreview.Builder builder = StructureElementPreview.builder();
        StructureElementPreview.CandidateGroup.Builder group =
                StructureElementPreview.CandidateGroup.builder(this::getCandidates);
        if (hasGlobalLimit()) {
            group.global(minGlobalCount, maxGlobalCount);
        }
        if (hasLayerLimit()) {
            group.layer(minLayerCount, maxLayerCount);
        }
        if (previewCount >= 0) {
            group.previewCount(previewCount);
        }
        builder.limited(group.build());
        return builder.build();
    }

    private boolean hasAnyLimit() {
        return hasGlobalLimit() || hasLayerLimit() || previewCount >= 0;
    }

    private boolean hasLayerLimit() {
        return minLayerCount > 0 || maxLayerCount >= 0;
    }

    @Nullable
    @Override
    public BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<Object> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return delegate.getBlocksToPlace(context, trigger, env);
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player) {
        return delegate.placeBlock(context, player);
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<Object> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return delegate.survivalPlaceBlock(context, trigger, env);
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<Object> context,
                                                         @NotNull ItemStack trigger) {
        return delegate.spawnHintWithResult(context, trigger);
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<Object> context) {
        return delegate.spawnHintWithResult(context);
    }

    @Override
    public int getMinGlobalCount() {
        return minGlobalCount;
    }

    @Override
    public int getMaxGlobalCount() {
        return maxGlobalCount;
    }

    @Override
    public int getMinLayerCount() {
        return minLayerCount;
    }

    @Override
    public int getMaxLayerCount() {
        return maxLayerCount;
    }

    @Override
    public boolean isCenter() {
        return delegate.isCenter();
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        delegate.addTooltip(tooltip);
    }

    @Override
    public void addPreviewTooltip(@NotNull List<String> tooltip) {
        delegate.addPreviewTooltip(tooltip);
    }

    @Nullable
    @Override
    public List<String> getDescription(@Nullable Object context) {
        return delegate.getDescription(context);
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        delegate.collectRequirements(context);
        if (hasGlobalLimit()) {
            context.getCollector().declareCount(
                    this,
                    minGlobalCount,
                    maxGlobalCount,
                    () -> new CountLimitError(CountLimitError.Kind.MIN_GLOBAL, minGlobalCount),
                    () -> new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxGlobalCount));
        }
    }
}
