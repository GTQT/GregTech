package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Element that matches air blocks.
 */
public class AirElement implements IStructureElement<Object> {

    public static final AirElement INSTANCE = new AirElement();

    private final TraceabilityPredicate cachedPredicate = TraceabilityPredicate.AIR;

    private AirElement() {}

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        return world.getBlockState(pos).getBlock().isAir(world.getBlockState(pos), world, pos);
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[]{new BlockInfo(Blocks.AIR.getDefaultState(), null)};
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        world.setBlockToAir(pos);
        return true;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        // No hint for air
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }
}
