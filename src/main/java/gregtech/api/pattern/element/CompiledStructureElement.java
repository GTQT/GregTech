package gregtech.api.pattern.element;

import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Immutable execution form of one structure element.
 *
 * <p>Both new elements and legacy predicates are normalized to this type while
 * compiling a piece. Runtime code therefore executes one element contract for
 * matching, candidates, hints and placement.
 */
public final class CompiledStructureElement<T> implements IStructureElement<T> {

    private final IStructureElement<T> source;
    @Nullable
    private TraceabilityPredicate predicateView;
    private boolean predicateViewResolved;
    private final Set<StructureElementCapability> capabilities;

    private CompiledStructureElement(@NotNull IStructureElement<T> source,
                                     @Nullable TraceabilityPredicate predicateView,
                                     boolean predicateViewResolved) {
        this.source = source;
        this.predicateView = predicateView;
        this.predicateViewResolved = predicateViewResolved;
        this.capabilities = StructureElementCapability.copyOf(source.getCapabilities());
    }

    @NotNull
    public static <T> CompiledStructureElement<T> compile(@NotNull IStructureElement<T> source) {
        if (source instanceof CompiledStructureElement) {
            return (CompiledStructureElement<T>) source;
        }
        if (source.usesLegacyPredicateRuntime()) {
            TraceabilityPredicate predicate = source.toPredicate();
            if (predicate == null) {
                throw new IllegalStateException(
                        source.getClass().getName() + " requested legacy predicate runtime without a predicate");
            }
            return (CompiledStructureElement<T>) legacy(predicate);
        }
        return new CompiledStructureElement<>(source, null, false);
    }

    @NotNull
    public static CompiledStructureElement<Object> legacy(@NotNull TraceabilityPredicate predicate) {
        TraceabilityPredicate sorted = new TraceabilityPredicate(predicate).sort();
        return new CompiledStructureElement<>(
                new gregtech.api.pattern.element.impl.LegacyElement(sorted),
                sorted,
                true);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<T> context) {
        return source.check(context);
    }

    @Override
    public boolean match(@NotNull StructureEvaluationContext<T> context) {
        return source.match(context);
    }

    @NotNull
    @Override
    public StructureIncrementalSupport getIncrementalSupport() {
        return source.getIncrementalSupport();
    }

    @NotNull
    @Override
    public Set<StructureDependency> getDependencies() {
        return source.getDependencies();
    }

    @Override
    public boolean hasExplicitIncrementalContract() {
        return source.hasExplicitIncrementalContract();
    }

    @NotNull
    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return capabilities;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return source.getCandidates();
    }

    @Override
    public BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
        return context.probeValue(source::getCandidates);
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview() {
        return source.getPreview();
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview(@NotNull StructureEvaluationContext<T> context) {
        return context.probeValue(source::getPreview);
    }

    @Nullable
    @Override
    public BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<T> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return context.probeValue(probeContext ->
                source.getBlocksToPlace(probeContext, trigger, env));
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                              @NotNull EntityPlayer player, boolean skipHatches) {
        return context.probe(probeContext ->
                source.placeBlock(probeContext, player, skipHatches));
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<T> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        return context.probeValue(probeContext ->
                source.survivalPlaceBlock(probeContext, trigger, env, skipHatches));
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context,
                                                         @NotNull ItemStack trigger) {
        return context.probeValue(probeContext ->
                source.spawnHintWithResult(probeContext, trigger));
    }

    @Override
    public void spawnHint(@NotNull StructureEvaluationContext<T> context) {
        spawnHintWithResult(context);
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context) {
        return context.probeValue(source::spawnHintWithResult);
    }

    @Override
    public int getMinGlobalCount() {
        return source.getMinGlobalCount();
    }

    @Override
    public int getMaxGlobalCount() {
        return source.getMaxGlobalCount();
    }

    @Override
    public int getMinLayerCount() {
        return source.getMinLayerCount();
    }

    @Override
    public int getMaxLayerCount() {
        return source.getMaxLayerCount();
    }

    @Override
    public boolean isCenter() {
        return source.isCenter();
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        source.addTooltip(tooltip);
    }

    @Override
    public void addPreviewTooltip(@NotNull List<String> tooltip) {
        source.addPreviewTooltip(tooltip);
    }

    @Nullable
    @Override
    public List<String> getDescription(@Nullable T context) {
        return source.getDescription(context);
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<T> context) {
        source.collectRequirements(context);
    }

    @Override
    public CompiledStructureElement<T> compile() {
        return this;
    }

    @Override
    public boolean usesLegacyPredicateRuntime() {
        return source.usesLegacyPredicateRuntime();
    }

    @Nullable
    @Override
    public TraceabilityPredicate toPredicate() {
        if (!predicateViewResolved) {
            TraceabilityPredicate predicate = source.toPredicate();
            predicateView = predicate == null ? null : copyPredicateView(predicate, source.isCenter());
            predicateViewResolved = true;
        }
        return predicateView;
    }

    @NotNull
    private static TraceabilityPredicate copyPredicateView(@NotNull TraceabilityPredicate predicate,
                                                           boolean center) {
        if (predicate == TraceabilityPredicate.ANY && !center) {
            return TraceabilityPredicate.ANY;
        }
        TraceabilityPredicate result = new TraceabilityPredicate(predicate);
        if (center) {
            result.setCenter();
        }
        return result.sort();
    }
}
