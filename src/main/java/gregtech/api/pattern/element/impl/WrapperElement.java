package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.element.AutoPlaceEnvironment;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.GTLog;
import gregtech.api.util.BlockInfo;
import gregtech.common.ConfigHolder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
    private final Consumer<StructureEvaluationContext<?>> callback;
    @Nullable
    private final String channelName;
    @NotNull
    private final List<String> tooltips;
    @Nullable
    private final Supplier<? extends MetaTileEntity> defaultCandidate;

    // Lazy-resolved delegate
    private volatile IStructureElement resolved;

    public WrapperElement(IStructureElement delegate,
                          @Nullable Supplier<IStructureElement> lazySupplier,
                          @Nullable Consumer<StructureEvaluationContext<?>> callback,
                          @Nullable String channelName) {
        this.delegate = delegate;
        this.lazySupplier = lazySupplier;
        this.callback = callback;
        this.channelName = channelName;
        this.tooltips = Collections.emptyList();
        this.defaultCandidate = null;
    }

    private WrapperElement(IStructureElement delegate,
                           @Nullable Supplier<IStructureElement> lazySupplier,
                           @Nullable Consumer<StructureEvaluationContext<?>> callback,
                           @Nullable String channelName,
                           @NotNull List<String> tooltips,
                           @Nullable Supplier<? extends MetaTileEntity> defaultCandidate) {
        this.delegate = delegate;
        this.lazySupplier = lazySupplier;
        this.callback = callback;
        this.channelName = channelName;
        this.tooltips = Collections.unmodifiableList(new ArrayList<>(tooltips));
        this.defaultCandidate = defaultCandidate;
    }

    public static IStructureElement withTooltips(@NotNull IStructureElement delegate, String... tips) {
        return new WrapperElement(delegate, null, null, null, Arrays.asList(tips), null);
    }

    public static IStructureElement withDefaultCandidate(
            @NotNull IStructureElement delegate,
            @NotNull Supplier<? extends MetaTileEntity> defaultCandidate) {
        return new WrapperElement(delegate, null, null, null, Collections.emptyList(), defaultCandidate);
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
        return applyPreviewMetadata(preview);
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview(@NotNull StructureEvaluationContext<Object> context) {
        return context.probeValue(probeContext -> {
            StructureElementPreview preview = getDelegate().getPreview(probeContext);
            return applyPreviewMetadata(preview);
        });
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
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player, boolean skipHatches) {
        return context.probe(probeContext ->
                getDelegate().placeBlock(probeContext, player, skipHatches));
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

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<Object> context,
                                                         @NotNull ItemStack trigger) {
        return context.probeValue(probeContext ->
                getDelegate().spawnHintWithResult(probeContext, trigger));
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
        tooltip.addAll(tooltips);
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

    @NotNull
    @Override
    public StructureIncrementalSupport getIncrementalSupport() {
        if (callback != null || lazySupplier != null) {
            return StructureIncrementalSupport.OPAQUE;
        }
        return getDelegate().getIncrementalSupport();
    }

    @NotNull
    @Override
    public Set<StructureDependency> getDependencies() {
        if (callback != null || lazySupplier != null) {
            return Collections.emptySet();
        }
        return getDelegate().getDependencies();
    }

    @Override
    public boolean hasExplicitIncrementalContract() {
        return callback != null || lazySupplier != null
                || getDelegate().hasExplicitIncrementalContract();
    }

    @NotNull
    private StructureElementPreview applyPreviewMetadata(@NotNull StructureElementPreview preview) {
        if (channelName == null && defaultCandidate == null && tooltips.isEmpty()) {
            return preview;
        }
        StructureElementPreview.Builder builder = StructureElementPreview.builder();
        for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
            builder.limited(applyPreviewMetadata(group));
        }
        for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
            builder.common(applyPreviewMetadata(group));
        }
        return builder.build();
    }

    @NotNull
    private StructureElementPreview.CandidateGroup applyPreviewMetadata(
            @NotNull StructureElementPreview.CandidateGroup group) {
        StructureElementPreview.CandidateGroup result = group;
        if (channelName != null) {
            result = result.withChannel(channelName);
        }
        if (defaultCandidate != null) {
            result = result.withDefaultCandidate(defaultCandidate);
        }
        if (!tooltips.isEmpty()) {
            result = result.withAdditionalTooltip(tooltips);
        }
        return result;
    }

    private void runCallback(@NotNull StructureEvaluationContext<Object> context) {
        if (callback == null) {
            return;
        }
        context.transactionAction(transactionContext ->
                callback.accept(transactionContext));
    }
}
