package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Supplier;

/**
 * Element that matches tiered blocks with a channel name for tier selection.
 * Used for blocks like heating coils where the tier determines the block variant.
 */
public class TieredElement implements IStructureElement<Object> {

    private final Supplier<BlockInfo[]> candidates;
    private final String channelName;

    public TieredElement(Supplier<BlockInfo[]> candidates, String channelName) {
        this.candidates = candidates;
        this.channelName = channelName;
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        IBlockState worldState = context.getBlockState();
        BlockInfo[] cand = candidates.get();
        for (BlockInfo info : cand) {
            if (info.getBlockState() == worldState) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        IBlockState worldState = world.getBlockState(pos);
        BlockInfo[] cand = candidates.get();
        for (BlockInfo info : cand) {
            if (info.getBlockState() == worldState) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return candidates.get();
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        // Hints are handled at a higher level
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        TraceabilityPredicate pred = new TraceabilityPredicate(
                bws -> {
                    IBlockState worldState = bws.getBlockState();
                    BlockInfo[] cand = candidates.get();
                    for (BlockInfo info : cand) {
                        if (info.getBlockState() == worldState) return true;
                    }
                    return false;
                },
                candidates);
        // Set channel name on the first common predicate for tier selection
        if (!pred.common.isEmpty()) {
            pred.common.get(0).channelName = channelName;
        }
        return pred;
    }
}
