package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Element that matches air blocks.
 *
 * <p>Depth-optimized: the cached predicate field is the
 * {@link TraceabilityPredicate#AIR} singleton, and {@link #applyTo}
 * bypasses {@link #toPredicate} to skip the per-call method-indirection.
 */
public class AirElement implements IStructureElement {

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
    public void applyTo(@NotNull String symbol, @NotNull PieceTemplateCompiler compiler) {
        // Depth-optimized: register the cached predicate (singleton) directly.
        compiler.where(symbol, cachedPredicate);
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }
}
