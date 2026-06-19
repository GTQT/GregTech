package gregtech.api.pattern.element;

import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;

import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * Explicit contract for direct elements whose runtime effects are represented
 * by typed structure contributions and declared dependencies.
 */
public interface ITypedStructureElement<T> extends IStructureElement<T> {

    @Override
    default boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                               @NotNull EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @NotNull
    @Override
    default PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<T> context,
                                           @NotNull net.minecraft.item.ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env,
                                           boolean skipHatches) {
        if (context.probe(this::check)) {
            return PlaceResult.SKIP;
        }

        BlocksToPlace blocksToPlace = context.probeValue(probeContext ->
                getBlocksToPlace(probeContext, trigger, env));
        if (blocksToPlace == null) {
            return PlaceResult.REJECT_CONTINUE;
        }

        IItemSource source = env.getSource();
        EntityPlayer actor = env.getActor();
        if (source == null || actor == null) {
            return PlaceResult.REJECT_CONTINUE;
        }

        if (blocksToPlace.getStacks() == null) {
            net.minecraft.item.ItemStack taken = source.takeOne(blocksToPlace.getPredicate(), true);
            if (taken.isEmpty()) {
                return PlaceResult.REJECT;
            }
            if (!placeBlock(context, actor, skipHatches)) {
                return PlaceResult.REJECT;
            }
            source.takeOne(blocksToPlace.getPredicate(), false);
            return PlaceResult.ACCEPT;
        }

        for (net.minecraft.item.ItemStack stack : blocksToPlace.getStacks()) {
            if (stack.isEmpty()) continue;
            net.minecraft.item.ItemStack one = stack.copy();
            one.setCount(1);
            if (!source.takeOne(one, true)) continue;
            if (!placeBlock(context, actor, skipHatches)) {
                return PlaceResult.REJECT;
            }
            source.takeOne(one, false);
            return PlaceResult.ACCEPT;
        }
        return PlaceResult.REJECT;
    }

    @Override
    default BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
        return getCandidates();
    }

    @NotNull
    @Override
    default StructureElementPreview getPreview(@NotNull StructureEvaluationContext<T> context) {
        return getPreview();
    }

    @Override
    default void spawnHint(@NotNull StructureEvaluationContext<T> context) {
        spawnHintWithResult(context);
    }

    @NotNull
    @Override
    default StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context) {
        return StructureHintRenderResult.skipped(StructureHintRenderResult.Source.CONTEXT);
    }

    @NotNull
    @Override
    default StructureIncrementalSupport getIncrementalSupport() {
        return StructureIncrementalSupport.TYPED_CONTRIBUTION;
    }

    @NotNull
    @Override
    default Set<StructureDependency> getDependencies() {
        return Collections.emptySet();
    }

    @Override
    default boolean hasExplicitIncrementalContract() {
        return true;
    }
}
