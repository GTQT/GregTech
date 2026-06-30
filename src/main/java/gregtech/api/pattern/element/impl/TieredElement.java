package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;

import java.util.function.Supplier;

/**
 * Element that matches tiered blocks with a channel name for tier selection.
 * Used for blocks like heating coils where the tier determines the block variant.
 */
public class TieredElement implements ITypedStructureElement<Object> {

    private final Supplier<BlockInfo[]> candidates;
    private final String channelName;
    private final StructureElementPreview preview;

    public TieredElement(Supplier<BlockInfo[]> candidates, String channelName) {
        this.candidates = candidates;
        this.channelName = channelName;
        this.preview = StructureElementPreview.builder()
                .common(StructureElementPreview.CandidateGroup.builder(candidates)
                        .channel(channelName)
                        .build())
                .build();
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
    public BlockInfo[] getCandidates() {
        return candidates.get();
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }
}
