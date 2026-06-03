package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Element that matches any block (wildcard).
 */
public class AnyElement implements IStructureElement {

    public static final AnyElement INSTANCE = new AnyElement();

    private AnyElement() {}

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        return true;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[0];
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        // No hint for wildcard
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return TraceabilityPredicate.ANY;
    }
}
