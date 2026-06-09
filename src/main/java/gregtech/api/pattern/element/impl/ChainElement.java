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
 * Element that chains multiple alternative elements together (any may match).
 */
public class ChainElement implements IStructureElement {

    private final IStructureElement[] elements;

    public ChainElement(IStructureElement... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("ChainElement requires at least one element");
        }
        this.elements = elements;
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        for (IStructureElement e : elements) {
            if (e.check(world, pos, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        List<BlockInfo> all = new ArrayList<>();
        for (IStructureElement e : elements) {
            BlockInfo[] c = e.getCandidates();
            if (c != null) {
                all.addAll(Arrays.asList(c));
            }
        }
        return all.toArray(new BlockInfo[0]);
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        for (IStructureElement e : elements) {
            if (e.placeBlock(world, pos, context, player, skipHatches)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        for (IStructureElement e : elements) {
            e.spawnHint(world, pos);
        }
    }

    @Override
    public int getMinGlobalCount() {
        int max = 0;
        for (IStructureElement e : elements) {
            max = Math.max(max, e.getMinGlobalCount());
        }
        return max;
    }

    @Override
    public int getMaxGlobalCount() {
        for (IStructureElement e : elements) {
            if (e.getMaxGlobalCount() == -1) {
                return -1;
            }
        }
        int max = 0;
        for (IStructureElement e : elements) {
            max = Math.max(max, e.getMaxGlobalCount());
        }
        return max;
    }

    @Override
    public boolean isCenter() {
        for (IStructureElement e : elements) {
            if (e.isCenter()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        for (IStructureElement e : elements) {
            e.addTooltip(tooltip);
        }
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        TraceabilityPredicate result = elements[0].toPredicate();
        for (int i = 1; i < elements.length; i++) {
            result = result.or(elements[i].toPredicate());
        }
        return result;
    }
}
