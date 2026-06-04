package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Element that wraps an existing TraceabilityPredicate for backward compatibility.
 * Used by pieceFromFactory() to convert old-style predicates to the new element system.
 */
public class LegacyElement implements IStructureElement {

    private final TraceabilityPredicate predicate;

    public LegacyElement(TraceabilityPredicate predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        // Direct check is not typically used for legacy predicates;
        // matching goes through toPredicate() in the compiled BlockPatternTemplate path
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        // Aggregate candidates from all simple predicates
        List<BlockInfo> result = new ArrayList<>();
        for (TraceabilityPredicate.SimplePredicate sp : predicate.common) {
            if (sp.candidates != null) {
                BlockInfo[] infos = sp.candidates.get();
                if (infos != null) {
                    result.addAll(Arrays.asList(infos));
                }
            }
        }
        for (TraceabilityPredicate.SimplePredicate sp : predicate.limited) {
            if (sp.candidates != null) {
                BlockInfo[] infos = sp.candidates.get();
                if (infos != null) {
                    result.addAll(Arrays.asList(infos));
                }
            }
        }
        return result.toArray(new BlockInfo[0]);
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
    public boolean isCenter() {
        return predicate.isCenter;
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return predicate;
    }
}
