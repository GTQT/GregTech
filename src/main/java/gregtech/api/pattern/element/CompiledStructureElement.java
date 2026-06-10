package gregtech.api.pattern.element;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

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
    private final TraceabilityPredicate predicate;

    private CompiledStructureElement(@NotNull IStructureElement<T> source,
                                     @NotNull TraceabilityPredicate predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    @NotNull
    public static <T> CompiledStructureElement<T> compile(@NotNull IStructureElement<T> source) {
        if (source instanceof CompiledStructureElement) {
            return (CompiledStructureElement<T>) source;
        }
        return new CompiledStructureElement<>(source,
                new TraceabilityPredicate(source.toPredicate()).sort());
    }

    @NotNull
    public static CompiledStructureElement<Object> legacy(@NotNull TraceabilityPredicate predicate) {
        return compile(new gregtech.api.pattern.element.impl.LegacyElement(predicate));
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<T> context) {
        return source.check(context.bindElementPredicate(predicate));
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        return source.check(world, pos, context);
    }

    @Override
    public BlockInfo[] getCandidates() {
        return source.getCandidates();
    }

    @Override
    public BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
        return source.getCandidates(context);
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

    @Override
    public void spawnHint(World world, BlockPos pos) {
        source.spawnHint(world, pos);
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
        return predicate.isCenter();
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        source.addTooltip(tooltip);
    }

    @Override
    public CompiledStructureElement<T> compile() {
        return this;
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return predicate;
    }
}
