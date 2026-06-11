package gregtech.api.pattern.element;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable execution form of one structure element.
 *
 * <p>Both new elements and legacy predicates are normalized to this type while
 * compiling a piece. Runtime code therefore executes one element contract for
 * matching, candidates, hints and placement.
 */
public final class CompiledStructureElement<T> implements IStructureElement<T> {

    private final IStructureElement<T> source;
    private final TraceabilityPredicate predicateView;

    private CompiledStructureElement(@NotNull IStructureElement<T> source,
                                     @NotNull TraceabilityPredicate predicateView) {
        this.source = source;
        this.predicateView = predicateView;
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
        return new CompiledStructureElement<>(source, createPredicateView(source));
    }

    @NotNull
    public static CompiledStructureElement<Object> legacy(@NotNull TraceabilityPredicate predicate) {
        TraceabilityPredicate sorted = new TraceabilityPredicate(predicate).sort();
        return new CompiledStructureElement<>(
                new gregtech.api.pattern.element.impl.LegacyElement(sorted),
                sorted);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<T> context) {
        return source.check(context);
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        return source.check(world, pos, context);
    }

    @Override
    public boolean couldBeValid(World world, BlockPos pos, PatternMatchContext context,
                                @NotNull ItemStack trigger) {
        return source.couldBeValid(world, pos, context, trigger);
    }

    @Override
    public BlockInfo[] getCandidates() {
        return source.getCandidates();
    }

    @Override
    public BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
        return source.getCandidates(context);
    }

    @Nullable
    @Override
    public BlocksToPlace getBlocksToPlace(World world, BlockPos pos, PatternMatchContext context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return source.getBlocksToPlace(world, pos, context, trigger, env);
    }

    @Nullable
    @Override
    public BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<T> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env) {
        return source.getBlocksToPlace(context, trigger, env);
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return source.placeBlock(world, pos, context, player, skipHatches);
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                              @NotNull EntityPlayer player, boolean skipHatches) {
        return source.placeBlock(context, player, skipHatches);
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(World world, BlockPos pos, PatternMatchContext context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        return source.survivalPlaceBlock(world, pos, context, trigger, env, skipHatches);
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<T> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        return source.survivalPlaceBlock(context, trigger, env, skipHatches);
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        source.spawnHint(world, pos);
    }

    @Override
    public boolean spawnHint(World world, BlockPos pos, @NotNull ItemStack trigger) {
        return source.spawnHint(world, pos, trigger);
    }

    @Override
    public void spawnHint(@NotNull StructureEvaluationContext<T> context) {
        source.spawnHint(context);
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
        return source.isCenter() || predicateView.isCenter();
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        source.addTooltip(tooltip);
    }

    @Nullable
    @Override
    public List<String> getDescription(@Nullable T context) {
        return source.getDescription(context);
    }

    @Override
    public CompiledStructureElement<T> compile() {
        return this;
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return predicateView;
    }

    @NotNull
    private static TraceabilityPredicate createPredicateView(@NotNull IStructureElement<?> source) {
        TraceabilityPredicate predicate = source.toPredicate();
        TraceabilityPredicate result = predicate == null
                ? new TraceabilityPredicate(state -> true, source::getCandidates)
                : new TraceabilityPredicate(predicate);
        if (source.isCenter()) {
            result.setCenter();
        }
        return result.sort();
    }
}
