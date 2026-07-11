package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Direct element backed by a pure block-state predicate.
 */
public class BlockPredicateElement implements ITypedStructureElement<Object> {

    @NotNull
    private final Predicate<IBlockState> predicate;
    @Nullable
    private final Supplier<BlockInfo[]> candidates;
    @NotNull
    private final StructureElementPreview preview;

    public BlockPredicateElement(@NotNull Predicate<IBlockState> predicate,
                                 @Nullable Supplier<BlockInfo[]> candidates) {
        this.predicate = predicate;
        this.candidates = candidates;
        this.preview = candidates == null
                ? StructureElementPreview.empty()
                : StructureElementPreview.of(candidates);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        return predicate.test(context.getBlockState());
    }

    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.snapshotSafe();
    }

    @Override
    public BlockInfo[] getCandidates() {
        return candidates == null ? new BlockInfo[0] : candidates.get();
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player) {
        World world = context.getWorld();
        if (world == null) {
            return false;
        }
        BlockInfo[] infos = getCandidates();
        if (infos.length == 0 || infos[0] == null || infos[0].getBlockState() == null) {
            return false;
        }
        world.setBlockState(context.getPos(), infos[0].getBlockState());
        return true;
    }
}
