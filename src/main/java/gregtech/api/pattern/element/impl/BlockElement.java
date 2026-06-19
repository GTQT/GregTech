package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.Set;

/**
 * Element that matches one or more specific block states.
 */
public class BlockElement implements ITypedStructureElement<Object> {

    private final IBlockState[] states;
    private final TraceabilityPredicate cachedPredicate;
    private final StructureElementPreview preview;

    public BlockElement(IBlockState... states) {
        this.states = states;
        this.cachedPredicate = buildPredicate();
        this.preview = StructureElementPreview.of(this::getCandidates);
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        IBlockState worldState = context.getBlockState();
        for (IBlockState state : states) {
            if (worldState == state) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.snapshotSafe();
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
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean placeBlock(StructureEvaluationContext<Object> context,
                              EntityPlayer player, boolean skipHatches) {
        World world = context.getWorld();
        if (world == null) {
            return false;
        }
        if (states.length == 0) {
            return false;
        }
        world.setBlockState(context.getPos(), states[0]);
        return true;
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
