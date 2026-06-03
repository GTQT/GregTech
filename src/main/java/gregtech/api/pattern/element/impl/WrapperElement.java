package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wrapper element that adds lazy initialization, callback, and channel behaviors
 * to an underlying element.
 */
public class WrapperElement implements IStructureElement {

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
            }
            return resolved;
        }
        return delegate;
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        boolean result = getDelegate().check(world, pos, context);
        if (result && callback != null) {
            callback.accept(context);
        }
        return result;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return getDelegate().getCandidates();
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return getDelegate().placeBlock(world, pos, context, player, skipHatches);
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        getDelegate().spawnHint(world, pos);
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
    public TraceabilityPredicate toPredicate() {
        // Defensive copy to avoid mutating the original predicate
        TraceabilityPredicate pred = new TraceabilityPredicate(getDelegate().toPredicate());

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
                                callback.accept(bws.getMatchContext());
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
        return wrapped;
    }
}
