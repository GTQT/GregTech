package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.AutoPlaceEnvironment;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.GTLog;
import gregtech.api.util.BlockInfo;
import gregtech.common.ConfigHolder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wrapper element that adds lazy initialization, callback, and channel behaviors
 * to an underlying element.
 */
public class WrapperElement implements IStructureElement<Object> {

    private final IStructureElement delegate;
    @Nullable
    private final Supplier<IStructureElement> lazySupplier;
    @Nullable
    private final Consumer<PatternMatchContext> callback;
    @Nullable
    private final String channelName;

    // Lazy-resolved delegate
    private volatile IStructureElement resolved;

    public WrapperElement(IStructureElement delegate,
                          @Nullable Supplier<IStructureElement> lazySupplier,
                          @Nullable Consumer<PatternMatchContext> callback,
                          @Nullable String channelName) {
        this.delegate = delegate;
        this.lazySupplier = lazySupplier;
        this.callback = callback;
        this.channelName = channelName;
    }

    private IStructureElement getDelegate() {
        if (lazySupplier != null && delegate == null) {
            if (resolved == null) {
                resolved = lazySupplier.get();
                if (ConfigHolder.machines.debugStructureCheck) {
                    GTLog.logger.debug("[StructureElement] resolved lazy element delegate={}",
                            resolved == null ? "null" : resolved.getClass().getName());
                }
                if (resolved == null) {
                    throw new IllegalStateException("Lazy structure element supplier returned null");
                }
            }
            return resolved;
        }
        return delegate;
    }

    @NotNull
    @Override
    public Set<StructureElementCapability> getCapabilities() {
        Set<StructureElementCapability> delegateCapabilities =
                getDelegate().getCapabilities();
        if (callback == null && lazySupplier == null) {
            return delegateCapabilities;
        }
        if (delegateCapabilities.isEmpty()) {
            return delegateCapabilities;
        }
        EnumSet<StructureElementCapability> capabilities =
                EnumSet.copyOf(delegateCapabilities);
        capabilities.remove(StructureElementCapability.SNAPSHOT_MATCH);
        return Collections.unmodifiableSet(capabilities);
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        boolean result = getDelegate().check(world, pos, context);
        if (result && callback != null) {
            runCallback(context);
        }
        return result;
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        boolean result = getDelegate().check(context);
        if (result && callback != null) {
            runCallback(context);
        }
        return result;
    }

    @Override
    public boolean match(@NotNull StructureEvaluationContext<Object> context) {
        boolean result = getDelegate().match(context);
        if (result && callback != null) {
            runCallback(context);
        }
        return result;
    }

    @Override
    public boolean couldBeValid(World world, BlockPos pos, PatternMatchContext context,
                                @NotNull ItemStack trigger) {
        return context.probe(legacyContext ->
                getDelegate().couldBeValid(world, pos, legacyContext, trigger));
    }

    @Override
    public BlockInfo[] getCandidates() {
        return getDelegate().getCandidates();
    }

    @Override
    public BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<Object> context) {
        return context.probeValue(probeContext ->
                getDelegate().getCandidates(probeContext));
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview() {
        StructureElementPreview preview = getDelegate().getPreview();
        return channelName == null ? preview : applyChannel(preview, channelName);
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview(@NotNull StructureEvaluationContext<Object> context) {
        return context.probeValue(probeContext -> {
            StructureElementPreview preview = getDelegate().getPreview(probeContext);
            return channelName == null ? preview : applyChannel(preview, channelName);
        });
    }

    @Nullable
    @Override
    public BlocksToPlace getBlocksToPlace(World world, BlockPos pos, PatternMatchContext context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return context.probeValue(legacyContext ->
                getDelegate().getBlocksToPlace(world, pos, legacyContext, trigger, env));
    }

    @Nullable
    @Override
    public BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<Object> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return context.probeValue(probeContext ->
                getDelegate().getBlocksToPlace(probeContext, trigger, env));
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return getDelegate().placeBlock(world, pos, context, player, skipHatches);
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player, boolean skipHatches) {
        return context.probe(probeContext ->
                getDelegate().placeBlock(probeContext, player, skipHatches));
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(World world, BlockPos pos, PatternMatchContext context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        return getDelegate().survivalPlaceBlock(world, pos, context, trigger, env, skipHatches);
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<Object> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        return context.probeValue(probeContext ->
                getDelegate().survivalPlaceBlock(probeContext, trigger, env, skipHatches));
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        getDelegate().spawnHint(world, pos);
    }

    @Override
    public boolean spawnHint(World world, BlockPos pos, @NotNull ItemStack trigger) {
        return getDelegate().spawnHint(world, pos, trigger);
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(
            World world, BlockPos pos, @NotNull ItemStack trigger) {
        return getDelegate().spawnHintWithResult(world, pos, trigger);
    }

    @Override
    public void spawnHint(@NotNull StructureEvaluationContext<Object> context) {
        spawnHintWithResult(context);
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<Object> context) {
        return context.probeValue(probeContext -> getDelegate().spawnHintWithResult(probeContext));
    }

    @Override
    public int getMinGlobalCount() {
        return getDelegate().getMinGlobalCount();
    }

    @Override
    public int getMaxGlobalCount() {
        return getDelegate().getMaxGlobalCount();
    }

    @Override
    public int getMinLayerCount() {
        return getDelegate().getMinLayerCount();
    }

    @Override
    public int getMaxLayerCount() {
        return getDelegate().getMaxLayerCount();
    }

    @Override
    public boolean isCenter() {
        return getDelegate().isCenter();
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        getDelegate().addTooltip(tooltip);
    }

    @Override
    public void addPreviewTooltip(@NotNull List<String> tooltip) {
        getDelegate().addPreviewTooltip(tooltip);
    }

    @Nullable
    @Override
    public List<String> getDescription(@Nullable Object context) {
        return getDelegate().getDescription(context);
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        getDelegate().collectRequirements(context);
    }

    @Override
    public boolean usesLegacyPredicateRuntime() {
        return false;
    }

    @NotNull
    @Override
    public StructureIncrementalSupport getIncrementalSupport() {
        if (callback != null || lazySupplier != null) {
            return StructureIncrementalSupport.OPAQUE;
        }
        return getDelegate().getIncrementalSupport();
    }

    @Nullable
    @Override
    public TraceabilityPredicate toPredicate() {
        // Defensive copy to avoid mutating the original predicate
        TraceabilityPredicate delegatePredicate = getDelegate().toPredicate();
        if (delegatePredicate == null) {
            return null;
        }
        TraceabilityPredicate pred = new TraceabilityPredicate(delegatePredicate);

        if (callback != null) {
            // Wrap each SimplePredicate with the callback invocation
            List<TraceabilityPredicate.SimplePredicate> oldCommon =
                    new ArrayList<>(pred.common);
            List<TraceabilityPredicate.SimplePredicate> oldLimited =
                    new ArrayList<>(pred.limited);

            pred.common.clear();
            pred.limited.clear();

            for (TraceabilityPredicate.SimplePredicate sp : oldCommon) {
                pred.common.add(wrapWithCallback(sp));
            }
            for (TraceabilityPredicate.SimplePredicate sp : oldLimited) {
                pred.limited.add(wrapWithCallback(sp));
            }
        }

        if (channelName != null && !pred.common.isEmpty()) {
            pred.common.get(0).channelName = channelName;
        }

        return pred;
    }

    /**
     * Wrap a SimplePredicate so that the callback is invoked on successful match.
     */
    private TraceabilityPredicate.SimplePredicate wrapWithCallback(
            TraceabilityPredicate.SimplePredicate sp) {
        TraceabilityPredicate.SimplePredicate wrapped =
                new TraceabilityPredicate.SimplePredicate(
                        bws -> {
                            boolean match = sp.test(bws);
                            if (match && callback != null) {
                                runCallback(bws.getMatchContext());
                            }
                            return match;
                        },
                        sp.candidates);
        // Copy metadata from the original predicate
        wrapped.channelName = sp.channelName;
        wrapped.minGlobalCount = sp.minGlobalCount;
        wrapped.maxGlobalCount = sp.maxGlobalCount;
        wrapped.minLayerCount = sp.minLayerCount;
        wrapped.maxLayerCount = sp.maxLayerCount;
        wrapped.previewCount = sp.previewCount;
        wrapped.ability = sp.ability;
        wrapped.defaultCandidate = sp.defaultCandidate;
        return wrapped;
    }

    @NotNull
    private static StructureElementPreview applyChannel(@NotNull StructureElementPreview preview,
                                                        @NotNull String channelName) {
        StructureElementPreview.Builder builder = StructureElementPreview.builder();
        for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
            builder.limited(group.withChannel(channelName));
        }
        for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
            builder.common(group.withChannel(channelName));
        }
        return builder.build();
    }

    private void runCallback(@NotNull PatternMatchContext context) {
        if (callback == null) {
            return;
        }
        context.transactionAction(callback);
    }

    private void runCallback(@NotNull StructureEvaluationContext<Object> context) {
        if (callback == null) {
            return;
        }
        context.transactionAction(transactionContext ->
                callback.accept(transactionContext.getLegacyContext()));
    }
}
