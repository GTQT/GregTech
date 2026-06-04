package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Element that matches one or more specific block states.
 *
 * <p>Depth-optimized: the {@link TraceabilityPredicate} is built once in
 * the constructor and cached. {@link #applyTo} bypasses {@link #toPredicate}
 * to skip the per-call method-indirection cost.
 */
public class BlockElement implements IStructureElement {

    private final IBlockState[] states;
    private final TraceabilityPredicate cachedPredicate;

    public BlockElement(IBlockState... states) {
        this.states = states;
        this.cachedPredicate = buildPredicate();
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        IBlockState worldState = world.getBlockState(pos);
        for (IBlockState state : states) {
            if (worldState == state) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        BlockInfo[] infos = new BlockInfo[states.length];
        for (int i = 0; i < states.length; i++) {
            infos[i] = new BlockInfo(states[i], null);
        }
        return infos;
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        if (states.length == 0) {
            return false;
        }
        world.setBlockState(pos, states[0]);
        return true;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        // Hints are handled at a higher level
    }

    @Override
    public void applyTo(@NotNull String symbol, @NotNull PieceTemplateCompiler compiler) {
        // Depth-optimized: register the cached predicate directly, skipping
        // the default-method indirection through toPredicate().
        compiler.where(symbol, cachedPredicate);
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }

    private TraceabilityPredicate buildPredicate() {
        if (states.length == 1) {
            return new TraceabilityPredicate(
                    bws -> bws.getBlockState() == states[0],
                    this::getCandidates);
        }
        return new TraceabilityPredicate(
                bws -> {
                    IBlockState ws = bws.getBlockState();
                    for (IBlockState s : states) {
                        if (ws == s) return true;
                    }
                    return false;
                },
                this::getCandidates);
    }
}
